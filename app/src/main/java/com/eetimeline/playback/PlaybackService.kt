package com.eetimeline.playback

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.eetimeline.BuildConfig
import com.eetimeline.core.JourneyPlan
import com.eetimeline.core.SleepSegment
import com.eetimeline.core.isInBedtimeWindow
import com.eetimeline.core.orderedEpisodeIds
import com.eetimeline.core.selectJourneyPlan
import com.eetimeline.core.sleepFadeVolume
import com.eetimeline.data.CurriculumRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

internal const val ACTION_DEBUG_START_SHORT_SLEEP_TIMER =
    "com.eetimeline.debug.action.START_SHORT_SLEEP_TIMER"

/** MediaSessionService for notification, lock-screen, Bluetooth and headset playback controls. */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferDownloadedAudio = AtomicBoolean(true)
    private val voiceBoostAudioEffect = VoiceBoostAudioEffect()
    private lateinit var stateRepository: EpisodeStateRepository
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var lastSnapshot: EpisodeProgress? = null
    private lateinit var playbackPreferences: PlaybackPreferences
    private var playbackSettings = PlaybackSettings()
    private var hasLoadedPlaybackSettings = false
    private var rememberedSleepTimerChoice = SleepTimerChoice.OFF
    private var activeSleepTimer: ActiveSleepTimer? = null
    private var sleepSessionCapture: SleepSessionCapture? = null
    private var sleepSessionPersistenceJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var pendingResumeJob: Job? = null
    private var sleepTimerBaseVolume = 1f
    private var lastPublishedRemainingSecond: Long? = null
    private var activeJourneyPlan: JourneyPlan? = null
    private var journeyEpisodeIds: List<String> = emptyList()
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var shakeSensorRegistered = false
    private var lastShakeElapsedMs = -SHAKE_DEBOUNCE_MS

    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val magnitude = sqrt(
                event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2],
            )
            val shakeAcceleration = abs(magnitude - SensorManager.GRAVITY_EARTH)
            val now = SystemClock.elapsedRealtime()
            if (
                shakeAcceleration >= SHAKE_THRESHOLD_METERS_PER_SECOND_SQUARED &&
                now - lastShakeElapsedMs >= SHAKE_DEBOUNCE_MS &&
                extendActiveSleepTimer()
            ) {
                lastShakeElapsedMs = now
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                closeSleepSegment()
                persistCurrentPosition()
            } else {
                beginSleepSegment()
                maybeStartBedtimeTimer()
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            voiceBoostAudioEffect.onAudioSessionIdChanged(audioSessionId)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                closeSleepSegment()
                persistCurrentPosition(completed = true)
            } else if (playbackState == Player.STATE_IDLE) {
                closeSleepSegment()
                persistCurrentPosition()
            }
            val timer = activeSleepTimer
            if (
                playbackState == Player.STATE_ENDED &&
                timer != null &&
                timer.endsAtCurrentItem
            ) {
                completeEndOfItemTimer(timer)
            }
            if (playbackState == Player.STATE_ENDED && activeJourneyPlan != null) {
                if (activeSleepTimer != null) cancelSleepTimer()
                player.pause()
                clearActiveJourneyPlan()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val currentEpisodeId = mediaItem?.mediaId
            sleepSessionCapture?.openSegment?.let { openSegment ->
                if (openSegment.episodeId != currentEpisodeId) {
                    val lastPosition = lastSnapshot
                        ?.takeIf { it.episodeId == openSegment.episodeId }
                        ?.positionMs
                        ?: openSegment.startPositionMs
                    closeSleepSegment(openSegment.episodeId, lastPosition)
                }
            }
            val timer = activeSleepTimer
            val endedTimedItem = timer != null &&
                timer.endsAtCurrentItem &&
                timer.deadlineElapsedMs == null &&
                (currentEpisodeId != timer.episodeId ||
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && player.currentPosition < 1_000L))
            if (endedTimedItem && timer != null) completeEndOfItemTimer(timer)
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                lastSnapshot?.let { persist(it) }
            }
            lastSnapshot = captureCurrentPosition()
            currentEpisodeId?.takeIf(String::isNotBlank)?.let(::resumeSavedPosition)
            if (activeJourneyPlan != null) {
                if (currentEpisodeId == null || currentEpisodeId !in journeyEpisodeIds) {
                    clearActiveJourneyPlan()
                } else {
                    publishSleepTimerState(force = true)
                }
            }
            beginSleepSegment()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                closeSleepSegment(
                    oldPosition.mediaItem?.mediaId,
                    oldPosition.positionMs,
                )
                persistCompletedOldPosition(oldPosition)
                val timer = activeSleepTimer
                if (
                    timer != null &&
                    timer.endsAtCurrentItem &&
                    timer.deadlineElapsedMs == null &&
                    oldPosition.mediaItem?.mediaId == timer.episodeId &&
                    newPosition.mediaItem?.mediaId != timer.episodeId
                ) {
                    completeEndOfItemTimer(timer)
                }
                beginSleepSegment()
            } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                closeSleepSegment(
                    oldPosition.mediaItem?.mediaId,
                    oldPosition.positionMs,
                )
                pendingResumeJob?.cancel()
                if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                    persistCurrentPosition()
                } else {
                    val episodeId = oldPosition.mediaItem?.mediaId?.takeIf(String::isNotBlank)
                    if (episodeId != null) {
                        val durationMs = lastSnapshot
                            ?.takeIf { it.episodeId == episodeId }
                            ?.durationMs
                            ?.coerceAtLeast(oldPosition.positionMs)
                            ?: oldPosition.positionMs
                        persist(EpisodeProgress(episodeId, oldPosition.positionMs, durationMs))
                    }
                    lastSnapshot = captureCurrentPosition()
                }
                beginSleepSegment()
            }
            if (activeSleepTimer?.endsAtCurrentItem == true) {
                updateSleepTimer()
            }
        }
    }

    private val sleepTimerSessionCallback = object : MediaSession.Callback {
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(SleepTimerProtocol.ACTION_START, Bundle.EMPTY))
                .add(SessionCommand(SleepTimerProtocol.ACTION_CANCEL, Bundle.EMPTY))
                .add(SessionCommand(JourneyPlaybackProtocol.ACTION_EXTEND_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(JourneyPlaybackProtocol.ACTION_PLAY_JOURNEY, Bundle.EMPTY))
                .build()
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .build()
            return Futures.immediateFuture(result)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
            SleepTimerProtocol.ACTION_START -> Futures.immediateFuture(
                SessionResult(startSleepTimerFromCommand(args)),
            )
            SleepTimerProtocol.ACTION_CANCEL -> {
                cancelSleepTimer()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            JourneyPlaybackProtocol.ACTION_EXTEND_SLEEP_TIMER -> Futures.immediateFuture(
                SessionResult(
                    if (extendActiveSleepTimer()) SessionResult.RESULT_SUCCESS
                    else SessionResult.RESULT_ERROR_BAD_VALUE,
                ),
            )
            JourneyPlaybackProtocol.ACTION_PLAY_JOURNEY -> playJourneyFromCommand(args)
            else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateRepository = EpisodeStateRepository.getInstance(this)
        playbackPreferences = PlaybackPreferences(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(PlaybackDownloadRuntime.mediaSourceFactory(this, preferDownloadedAudio))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    /* handleAudioFocus= */ true,
                )
                setWakeMode(C.WAKE_MODE_LOCAL)
                setHandleAudioBecomingNoisy(true)
                addListener(playerListener)
            }
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sleepTimerSessionCallback)
            .build()
        publishSleepTimerState(force = true)

        serviceScope.launch {
            playbackPreferences.settings.collect { settings ->
                val wasLoaded = hasLoadedPlaybackSettings
                hasLoadedPlaybackSettings = true
                playbackSettings = settings
                preferDownloadedAudio.set(settings.preferDownloadedAudio)
                rememberedSleepTimerChoice = settings.sleepTimerChoice
                player.playbackParameters = PlaybackParameters(settings.speed)
                player.skipSilenceEnabled = settings.skipSilence
                voiceBoostAudioEffect.setEnabled(settings.voiceBoost, player.audioSessionId)
                updateShakeSensorRegistration()
                publishSleepTimerState(force = true)
                if (!wasLoaded && player.isPlaying) maybeStartBedtimeTimer()
            }
        }
        serviceScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                persistCurrentPosition()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == JourneyPlaybackProtocol.ACTION_PLAY_JOURNEY) {
            val rawPlan = intent.getStringExtra(JourneyPlaybackProtocol.KEY_PLAN)
            val plan = if (rawPlan == null) {
                JourneyPlan.JUST_KEEP_GOING
            } else {
                JourneyPlan.values().firstOrNull { it.name == rawPlan }
            }
            if (plan != null) {
                serviceScope.launch { runCatching { playJourney(plan) } }
            }
        } else if (BuildConfig.DEBUG && intent?.action == ACTION_DEBUG_START_SHORT_SLEEP_TIMER) {
            startDebugShortSleepTimer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistCurrentPosition()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        cancelSleepTimer(publish = false)
        unregisterShakeSensor()
        voiceBoostAudioEffect.release()
        val finalSnapshot = captureCurrentPosition() ?: lastSnapshot
        if (::stateRepository.isInitialized && finalSnapshot != null) {
            runBlocking(Dispatchers.IO) {
                stateRepository.saveProgress(
                    finalSnapshot.episodeId,
                    finalSnapshot.positionMs,
                    finalSnapshot.durationMs,
                )
            }
        }
        sleepSessionPersistenceJob?.let { persistenceJob ->
            runBlocking(Dispatchers.IO) { persistenceJob.join() }
        }
        mediaSession?.release()
        mediaSession = null
        if (::player.isInitialized) {
            player.removeListener(playerListener)
            player.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun playJourneyFromCommand(args: Bundle): ListenableFuture<SessionResult> {
        val rawPlan = args.getString(JourneyPlaybackProtocol.KEY_PLAN)
        val plan = if (rawPlan == null) {
            JourneyPlan.JUST_KEEP_GOING
        } else {
            JourneyPlan.values().firstOrNull { it.name == rawPlan }
        } ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))

        val result = SettableFuture.create<SessionResult>()
        serviceScope.launch {
            val resultCode = try {
                playJourney(plan)
                SessionResult.RESULT_SUCCESS
            } catch (_: Exception) {
                SessionResult.RESULT_ERROR_UNKNOWN
            }
            result.set(SessionResult(resultCode))
        }
        return result
    }

    private suspend fun playJourney(plan: JourneyPlan) {
        val curriculum = CurriculumRepository(this).load()
        val statesById = stateRepository.observeAll().first().associateBy { it.episodeId }
        val candidates = curriculum.orderedEpisodeIds().filter { episodeId ->
            curriculum.episodes.containsKey(episodeId) && statesById[episodeId]?.isPlayed != true
        }
        val durationsMs = candidates.associateWith { episodeId ->
            curriculum.episodes.getValue(episodeId).durationSec.coerceAtLeast(0).toLong() * MILLIS_PER_SECOND
        }
        val resumePositionsMs = candidates.associateWith { episodeId ->
            statesById[episodeId]?.positionMs?.coerceAtLeast(0L) ?: 0L
        }
        val selection = selectJourneyPlan(candidates, durationsMs, resumePositionsMs, plan, player.playbackParameters.speed)
        if (selection.episodeIds.isEmpty()) return

        val mediaItems = selection.episodeIds.map { episodeId ->
            val episode = curriculum.episodes.getValue(episodeId)
            MediaItem.Builder()
                .setMediaId(episode.id)
                .setUri(episode.audioUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(episode.title).build())
                .build()
        }
        val firstEpisodeId = selection.episodeIds.first()
        val firstDurationMs = durationsMs[firstEpisodeId] ?: 0L
        val startPositionMs = (resumePositionsMs[firstEpisodeId] ?: 0L).let { position ->
            if (firstDurationMs > 0L) position.coerceAtMost(firstDurationMs) else position
        }
        activeJourneyPlan = plan
        journeyEpisodeIds = selection.episodeIds
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        player.setMediaItems(mediaItems, 0, startPositionMs)
        player.prepare()
        player.play()
        publishSleepTimerState(force = true)
    }

    private fun clearActiveJourneyPlan() {
        if (activeJourneyPlan == null && journeyEpisodeIds.isEmpty()) return
        activeJourneyPlan = null
        journeyEpisodeIds = emptyList()
        publishSleepTimerState(force = true)
    }

    private fun captureCurrentPosition(): EpisodeProgress? {
        if (!::player.isInitialized) return null
        val item = player.currentMediaItem ?: return null
        val episodeId = item.mediaId.takeIf(String::isNotBlank) ?: return null
        val rawDuration = player.duration
        val duration = if (rawDuration == C.TIME_UNSET || rawDuration < 0L) 0L else rawDuration
        return EpisodeProgress(
            episodeId = episodeId,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
        )
    }

    private fun persistCurrentPosition(completed: Boolean = false) {
        val snapshot = captureCurrentPosition() ?: return
        lastSnapshot = snapshot
        persist(snapshot, completed)
    }

    private fun persistCompletedOldPosition(oldPosition: Player.PositionInfo) {
        val episodeId = oldPosition.mediaItem?.mediaId?.takeIf(String::isNotBlank) ?: return
        val durationMs = lastSnapshot
            ?.takeIf { it.episodeId == episodeId }
            ?.durationMs
            ?.coerceAtLeast(oldPosition.positionMs)
            ?: oldPosition.positionMs
        persist(
            EpisodeProgress(
                episodeId = episodeId,
                positionMs = oldPosition.positionMs,
                durationMs = durationMs,
            ),
            completed = true,
        )
    }

    private fun resumeSavedPosition(episodeId: String) {
        pendingResumeJob?.cancel()
        pendingResumeJob = serviceScope.launch {
            val saved = stateRepository.getState(episodeId) ?: return@launch
            if (saved.isPlayed || saved.positionMs <= MIN_RESUME_POSITION_MS) return@launch
            if (
                player.currentMediaItem?.mediaId != episodeId ||
                player.currentPosition > MAX_RESTORE_START_POSITION_MS
            ) return@launch
            val durationMs = player.duration
            val positionMs = if (durationMs != C.TIME_UNSET && durationMs > 0L) {
                saved.positionMs.coerceAtMost(durationMs)
            } else {
                saved.positionMs
            }
            player.seekTo(positionMs)
            lastSnapshot = captureCurrentPosition()
        }
    }

    private fun persist(snapshot: EpisodeProgress, completed: Boolean = false) {
        if (!::stateRepository.isInitialized) return
        serviceScope.launch {
            stateRepository.saveProgress(
                snapshot.episodeId,
                snapshot.positionMs,
                snapshot.durationMs,
                forceCompleted = completed,
            )
        }
    }

    private fun startSleepTimerFromCommand(args: Bundle): Int {
        val rawChoice = args.getInt(SleepTimerProtocol.KEY_CHOICE, Int.MIN_VALUE)
        val choice = SleepTimerChoice.values().firstOrNull { it.storedValue == rawChoice }
            ?: return SessionResult.RESULT_ERROR_BAD_VALUE
        if (choice == SleepTimerChoice.OFF) {
            cancelSleepTimer()
            return SessionResult.RESULT_SUCCESS
        }
        return if (startSleepTimer(choice)) {
            SessionResult.RESULT_SUCCESS
        } else {
            SessionResult.RESULT_ERROR_BAD_VALUE
        }
    }

    private fun startSleepTimer(choice: SleepTimerChoice): Boolean {
        val timer = if (choice == SleepTimerChoice.END_OF_CURRENT_ITEM) {
            val item = player.currentMediaItem ?: return false
            val episodeId = item.mediaId.takeIf(String::isNotBlank) ?: return false
            ActiveSleepTimer(endsAtCurrentItem = true, episodeId = episodeId)
        } else {
            val durationMs = choice.storedValue.toLong() * MILLIS_PER_MINUTE
            ActiveSleepTimer(
                endsAtCurrentItem = false,
                deadlineElapsedMs = saturatingAdd(SystemClock.elapsedRealtime(), durationMs),
            )
        }
        return activateSleepTimer(timer, rememberedChoice = choice)
    }

    private fun startDebugShortSleepTimer() {
        val timer = ActiveSleepTimer(
            endsAtCurrentItem = false,
            deadlineElapsedMs = saturatingAdd(SystemClock.elapsedRealtime(), MILLIS_PER_MINUTE),
        )
        activateSleepTimer(timer, rememberedChoice = null)
    }

    private fun activateSleepTimer(
        timer: ActiveSleepTimer,
        rememberedChoice: SleepTimerChoice?,
    ): Boolean {
        cancelSleepTimer(publish = false)
        sleepTimerBaseVolume = player.volume
        activeSleepTimer = timer
        sleepSessionCapture = SleepSessionCapture(System.currentTimeMillis())
        if (rememberedChoice != null) {
            rememberedSleepTimerChoice = rememberedChoice
            serviceScope.launch { playbackPreferences.setSleepTimerChoice(rememberedChoice) }
        }
        lastPublishedRemainingSecond = null
        updateShakeSensorRegistration()
        beginSleepSegment()
        publishSleepTimerState(force = true)
        sleepTimerJob = serviceScope.launch {
            while (isActive && activeSleepTimer != null) {
                updateSleepTimer()
                if (activeSleepTimer != null) delay(SLEEP_TIMER_UPDATE_INTERVAL_MS)
            }
        }
        return true
    }

    private fun cancelSleepTimer(publish: Boolean = true) {
        val hadTimer = activeSleepTimer != null
        activeSleepTimer = null
        sleepSessionCapture = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (hadTimer && ::player.isInitialized) player.volume = sleepTimerBaseVolume
        updateShakeSensorRegistration()
        lastPublishedRemainingSecond = null
        if (publish) publishSleepTimerState(force = true)
    }

    private fun finishSleepTimer() {
        if (activeSleepTimer == null) return
        closeSleepSegment()
        val capture = sleepSessionCapture
        val segments = capture?.segments?.toList().orEmpty()
        val fadeEndedAtMs = System.currentTimeMillis()
        activeSleepTimer = null
        sleepSessionCapture = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        player.volume = sleepTimerBaseVolume
        updateShakeSensorRegistration()
        if (capture != null && segments.isNotEmpty()) {
            val previousPersistenceJob = sleepSessionPersistenceJob
            sleepSessionPersistenceJob = serviceScope.launch(Dispatchers.IO) {
                previousPersistenceJob?.join()
                stateRepository.recordSleepSession(
                    timerStartedAtMs = capture.timerStartedAtMs,
                    fadeEndedAtMs = fadeEndedAtMs,
                    segments = segments,
                )
            }
        }
        player.pause()
        lastPublishedRemainingSecond = null
        publishSleepTimerState(force = true)
    }

    private fun updateSleepTimer(force: Boolean = false) {
        val timer = activeSleepTimer ?: return
        val remainingMs = getRemainingMs(timer)
        if (remainingMs == null) {
            player.volume = sleepTimerBaseVolume
            publishSleepTimerState(force)
            return
        }
        val safeRemainingMs = remainingMs.coerceAtLeast(0L)
        player.volume = sleepTimerBaseVolume * sleepFadeVolume(safeRemainingMs)
        publishSleepTimerState(force)
        if (safeRemainingMs == 0L) {
            if (timer.endsAtCurrentItem && timer.extensionAfterItemMs > 0L) {
                completeEndOfItemTimer(timer)
            } else {
                finishSleepTimer()
            }
        }
    }

    private fun getRemainingMs(timer: ActiveSleepTimer): Long? =
        if (timer.endsAtCurrentItem && timer.deadlineElapsedMs == null) {
            val itemRemainingMs = if (player.currentMediaItem?.mediaId != timer.episodeId) {
                0L
            } else {
                val durationMs = player.duration
                if (durationMs == C.TIME_UNSET || durationMs <= 0L) {
                    null
                } else {
                    (durationMs - player.currentPosition).coerceAtLeast(0L)
                }
            }
            itemRemainingMs?.let { saturatingAdd(it, timer.extensionAfterItemMs) }
        } else {
            (checkNotNull(timer.deadlineElapsedMs) - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }

    private fun completeEndOfItemTimer(timer: ActiveSleepTimer) {
        if (activeSleepTimer != timer || !timer.endsAtCurrentItem) return
        if (timer.extensionAfterItemMs > 0L) {
            activeSleepTimer = timer.copy(
                deadlineElapsedMs = saturatingAdd(SystemClock.elapsedRealtime(), timer.extensionAfterItemMs),
                extensionAfterItemMs = 0L,
            )
            updateSleepTimer(force = true)
        } else {
            finishSleepTimer()
        }
    }

    private fun extendActiveSleepTimer(): Boolean {
        val timer = activeSleepTimer ?: return false
        if (timer.endsAtCurrentItem && timer.deadlineElapsedMs == null) {
            activeSleepTimer = timer.copy(
                extensionAfterItemMs = saturatingAdd(timer.extensionAfterItemMs, TEN_MINUTES_MS),
            )
        } else {
            val now = SystemClock.elapsedRealtime()
            val deadline = checkNotNull(timer.deadlineElapsedMs).coerceAtLeast(now)
            activeSleepTimer = timer.copy(deadlineElapsedMs = saturatingAdd(deadline, TEN_MINUTES_MS))
        }
        updateSleepTimer(force = true)
        performShortHaptic()
        return true
    }

    private fun maybeStartBedtimeTimer() {
        if (
            !hasLoadedPlaybackSettings ||
            !playbackSettings.autoSleepTimerInBedtime ||
            activeSleepTimer != null
        ) {
            return
        }
        val choice = rememberedSleepTimerChoice.takeIf { it != SleepTimerChoice.OFF }
            ?: SleepTimerChoice.MINUTES_15
        val calendar = Calendar.getInstance()
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR +
            calendar.get(Calendar.MINUTE)
        if (
            isInBedtimeWindow(
                minuteOfDay,
                playbackSettings.bedtimeStartMinute,
                playbackSettings.bedtimeEndMinute,
            )
        ) {
            startSleepTimer(choice)
        }
    }

    private fun beginSleepSegment() {
        val capture = sleepSessionCapture ?: return
        if (!player.isPlaying || capture.openSegment != null) return
        val episodeId = player.currentMediaItem?.mediaId?.takeIf(String::isNotBlank) ?: return
        capture.openSegment = OpenSleepSegment(
            episodeId = episodeId,
            startPositionMs = player.currentPosition.coerceAtLeast(0L),
            wallClockStartMs = System.currentTimeMillis(),
        )
    }

    private fun closeSleepSegment(episodeId: String? = null, endPositionMs: Long? = null) {
        val capture = sleepSessionCapture ?: return
        val openSegment = capture.openSegment ?: return
        if (episodeId != null && episodeId != openSegment.episodeId) return
        val wallClockEndMs = System.currentTimeMillis().coerceAtLeast(openSegment.wallClockStartMs)
        val endPosition = (endPositionMs ?: player.currentPosition).coerceAtLeast(0L)
        if (endPosition != openSegment.startPositionMs) {
            capture.segments += SleepSegment(
                episodeId = openSegment.episodeId,
                startPositionMs = openSegment.startPositionMs,
                endPositionMs = endPosition,
                wallClockStartMs = openSegment.wallClockStartMs,
                wallClockEndMs = wallClockEndMs,
            )
        }
        capture.openSegment = null
    }

    private fun updateShakeSensorRegistration() {
        val manager = sensorManager ?: return
        val shouldRegister = activeSleepTimer != null &&
            playbackSettings.shakeToExtend &&
            accelerometer != null
        if (shouldRegister && !shakeSensorRegistered) {
            shakeSensorRegistered = manager.registerListener(
                shakeListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
            )
        } else if (!shouldRegister) {
            unregisterShakeSensor()
        }
    }

    private fun unregisterShakeSensor() {
        if (!shakeSensorRegistered) return
        sensorManager?.unregisterListener(shakeListener)
        shakeSensorRegistered = false
    }

    private fun performShortHaptic() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(HAPTIC_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(HAPTIC_DURATION_MS)
        }
    }

    private fun publishSleepTimerState(force: Boolean = false) {
        val session = mediaSession ?: return
        val remainingMs = activeSleepTimer?.let(::getRemainingMs)
        val remainingSecond = remainingMs?.div(MILLIS_PER_SECOND)
        if (!force && remainingSecond == lastPublishedRemainingSecond) return
        lastPublishedRemainingSecond = remainingSecond
        session.setSessionExtras(
            Bundle().apply {
                putLong(SleepTimerProtocol.KEY_REMAINING_MS, remainingMs ?: NO_TIMER)
                putInt(SleepTimerProtocol.KEY_CHOICE, rememberedSleepTimerChoice.storedValue)
                putBoolean(SleepTimerProtocol.KEY_ACTIVE, activeSleepTimer != null)
                putString(JourneyPlaybackProtocol.KEY_ACTIVE_PLAN, activeJourneyPlan?.name)
                val currentIndex = player.currentMediaItem?.mediaId?.let(journeyEpisodeIds::indexOf) ?: -1
                putInt(
                    JourneyPlaybackProtocol.KEY_STORY_INDEX,
                    if (activeJourneyPlan != null && currentIndex >= 0) currentIndex + 1 else 0,
                )
                putInt(
                    JourneyPlaybackProtocol.KEY_STORY_COUNT,
                    if (activeJourneyPlan != null) journeyEpisodeIds.size else 0,
                )
            },
        )
    }

    private data class ActiveSleepTimer(
        val endsAtCurrentItem: Boolean,
        val deadlineElapsedMs: Long? = null,
        val episodeId: String? = null,
        val extensionAfterItemMs: Long = 0L,
    )

    private data class SleepSessionCapture(
        val timerStartedAtMs: Long,
        val segments: MutableList<SleepSegment> = mutableListOf(),
        var openSegment: OpenSleepSegment? = null,
    )

    private data class OpenSleepSegment(
        val episodeId: String,
        val startPositionMs: Long,
        val wallClockStartMs: Long,
    )

    private data class EpisodeProgress(
        val episodeId: String,
        val positionMs: Long,
        val durationMs: Long,
    )

    private companion object {
        const val MIN_RESUME_POSITION_MS = 5_000L
        const val POSITION_SAVE_INTERVAL_MS = 15_000L
        const val SLEEP_TIMER_UPDATE_INTERVAL_MS = 250L
        const val MAX_RESTORE_START_POSITION_MS = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val TEN_MINUTES_MS = 10L * MILLIS_PER_MINUTE
        const val NO_TIMER = -1L
        const val MINUTES_PER_HOUR = 60
        const val SHAKE_THRESHOLD_METERS_PER_SECOND_SQUARED = 4.0f
        const val SHAKE_DEBOUNCE_MS = 2_000L
        const val HAPTIC_DURATION_MS = 60L

        fun saturatingAdd(left: Long, right: Long): Long =
            if (right > 0L && Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}
