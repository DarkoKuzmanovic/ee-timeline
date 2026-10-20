package com.eetimeline.playback

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.eetimeline.core.sleepFadeVolume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.collect
import java.util.concurrent.atomic.AtomicBoolean

/** MediaSessionService for notification, lock-screen, Bluetooth and headset playback controls. */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferDownloadedAudio = AtomicBoolean(true)
    private lateinit var stateRepository: EpisodeStateRepository
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var lastSnapshot: EpisodeProgress? = null
    private lateinit var playbackPreferences: PlaybackPreferences
    private var rememberedSleepTimerChoice = SleepTimerChoice.OFF
    private var activeSleepTimer: ActiveSleepTimer? = null
    private var sleepTimerJob: Job? = null
    private var pendingResumeJob: Job? = null
    private var sleepTimerBaseVolume = 1f
    private var lastPublishedRemainingSecond: Long? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) persistCurrentPosition()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                persistCurrentPosition(completed = true)
            } else if (playbackState == Player.STATE_IDLE) {
                persistCurrentPosition()
            }
            if (playbackState == Player.STATE_ENDED &&
                activeSleepTimer?.choice == SleepTimerChoice.END_OF_CURRENT_ITEM
            ) {
                finishSleepTimer()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val timer = activeSleepTimer
            val endedTimedItem = timer?.choice == SleepTimerChoice.END_OF_CURRENT_ITEM &&
                (mediaItem?.mediaId != timer.episodeId ||
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && player.currentPosition < 1_000L))
            if (endedTimedItem) finishSleepTimer()
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                lastSnapshot?.let { persist(it) }
            }
            lastSnapshot = captureCurrentPosition()
            mediaItem?.mediaId?.takeIf(String::isNotBlank)?.let(::resumeSavedPosition)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                persistCompletedOldPosition(oldPosition)
            } else if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                oldPosition.mediaItemIndex == newPosition.mediaItemIndex
            ) {
                pendingResumeJob?.cancel()
                persistCurrentPosition()
            }
            if (activeSleepTimer?.choice == SleepTimerChoice.END_OF_CURRENT_ITEM) {
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
        ): ListenableFuture<SessionResult> {
            val resultCode = when (customCommand.customAction) {
                SleepTimerProtocol.ACTION_START -> startSleepTimerFromCommand(args)
                SleepTimerProtocol.ACTION_CANCEL -> {
                    cancelSleepTimer()
                    SessionResult.RESULT_SUCCESS
                }
                else -> SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
            return Futures.immediateFuture(SessionResult(resultCode))
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateRepository = EpisodeStateRepository.getInstance(this)
        playbackPreferences = PlaybackPreferences(this)
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
                preferDownloadedAudio.set(settings.preferDownloadedAudio)
                rememberedSleepTimerChoice = settings.sleepTimerChoice
                player.playbackParameters = PlaybackParameters(settings.speed)
                publishSleepTimerState(force = true)
            }

        }
        serviceScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                persistCurrentPosition()
            }
        }
    }


    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistCurrentPosition()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        cancelSleepTimer(publish = false)
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
        mediaSession?.release()
        mediaSession = null
        if (::player.isInitialized) {
            player.removeListener(playerListener)
            player.release()
        }
        serviceScope.cancel()
        super.onDestroy()
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
            ActiveSleepTimer(choice = choice, episodeId = episodeId)
        } else {
            val durationMs = choice.storedValue.toLong() * MILLIS_PER_MINUTE
            ActiveSleepTimer(
                choice = choice,
                deadlineElapsedMs = SystemClock.elapsedRealtime() + durationMs,
            )
        }
        cancelSleepTimer(publish = false)
        sleepTimerBaseVolume = player.volume
        activeSleepTimer = timer
        rememberedSleepTimerChoice = choice
        lastPublishedRemainingSecond = null
        serviceScope.launch { playbackPreferences.setSleepTimerChoice(choice) }
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
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (hadTimer && ::player.isInitialized) player.volume = sleepTimerBaseVolume
        lastPublishedRemainingSecond = null
        if (publish) publishSleepTimerState(force = true)
    }

    private fun finishSleepTimer() {
        if (activeSleepTimer == null) return
        activeSleepTimer = null
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        player.volume = sleepTimerBaseVolume
        player.pause()
        lastPublishedRemainingSecond = null
        publishSleepTimerState(force = true)
    }

    private fun updateSleepTimer() {
        val timer = activeSleepTimer ?: return
        val remainingMs = getRemainingMs(timer)
        if (remainingMs == null) {
            player.volume = sleepTimerBaseVolume
            publishSleepTimerState()
            return
        }
        val safeRemainingMs = remainingMs.coerceAtLeast(0L)
        player.volume = sleepTimerBaseVolume * sleepFadeVolume(safeRemainingMs)
        publishSleepTimerState()
        if (safeRemainingMs == 0L) finishSleepTimer()
    }

    private fun getRemainingMs(timer: ActiveSleepTimer): Long? =
        if (timer.choice == SleepTimerChoice.END_OF_CURRENT_ITEM) {
            if (player.currentMediaItem?.mediaId != timer.episodeId) {
                0L
            } else {
                val durationMs = player.duration
                if (durationMs == C.TIME_UNSET || durationMs <= 0L) {
                    null
                } else {
                    (durationMs - player.currentPosition).coerceAtLeast(0L)
                }
            }
        } else {
            (checkNotNull(timer.deadlineElapsedMs) - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }

    private fun publishSleepTimerState(force: Boolean = false) {
        val session = mediaSession ?: return
        val remainingMs = activeSleepTimer?.let(::getRemainingMs)
        val remainingSecond = remainingMs?.div(1_000L)
        if (!force && remainingSecond == lastPublishedRemainingSecond) return
        lastPublishedRemainingSecond = remainingSecond
        session.setSessionExtras(
            Bundle().apply {
                putLong(SleepTimerProtocol.KEY_REMAINING_MS, remainingMs ?: NO_TIMER)
                putInt(SleepTimerProtocol.KEY_CHOICE, rememberedSleepTimerChoice.storedValue)
                putBoolean(SleepTimerProtocol.KEY_ACTIVE, activeSleepTimer != null)
            },
        )
    }

    private data class ActiveSleepTimer(
        val choice: SleepTimerChoice,
        val deadlineElapsedMs: Long? = null,
        val episodeId: String? = null,
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
        const val NO_TIMER = -1L
}
}
