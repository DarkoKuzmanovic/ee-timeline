package com.eetimeline.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.eetimeline.AppGraph
import com.eetimeline.core.Curriculum
import com.eetimeline.core.Episode
import com.eetimeline.core.firstUnplayed
import com.eetimeline.core.orderedEpisodeIds
import com.eetimeline.playback.PlaybackService
import com.eetimeline.playback.SleepTimerChoice
import com.eetimeline.playback.SleepTimerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun EEApp(appGraph: AppGraph) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var curriculum by remember { mutableStateOf(Curriculum()) }
    var savedProgress by remember { mutableStateOf(emptyMap<String, com.eetimeline.playback.EpisodePlaybackState>()) }
    var downloadStatuses by remember { mutableStateOf(emptyMap<String, EpisodeDownloadStatus>()) }
    var preferredSpeed by remember { mutableStateOf(1f) }
    var journeyMode by remember { mutableStateOf(false) }
    var playerSnapshot by remember { mutableStateOf(PlaybackUiState()) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var timerController by remember { mutableStateOf<SleepTimerController?>(null) }

    val pendingPermissionAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingPermissionAction.value?.let { action ->
            pendingPermissionAction.value = null
            action()
        }
    }
    val runWithNotificationPermission: (() -> Unit) -> Unit = { action ->
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPermissionAction.value = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    DisposableEffect(context) {
        var active = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                if (active) {
                    runCatching { future.get() }.onSuccess { controller = it }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            active = false
            MediaController.releaseFuture(future)
        }
    }

    DisposableEffect(controller) {
        val timer = controller?.let(::SleepTimerController)
        timerController = timer
        onDispose {
            if (timerController === timer) timerController = null
        }
    }

    LaunchedEffect(appGraph) {
        curriculum = appGraph.curriculumRepository.load()
        launch {
            appGraph.episodeStates.observeAll().collect { states ->
                savedProgress = states.associateBy { it.episodeId }
            }
        }
        launch {
            appGraph.playbackPreferences.settings.collect { settings ->
                preferredSpeed = settings.speed
            }
        }
        launch {
            appGraph.downloads.observeDownloads().collect { downloads ->
                downloadStatuses = downloads.associate { download ->
                    val status = when (download.state) {
                        Download.STATE_COMPLETED -> EpisodeDownloadStatus(completed = true)
                        Download.STATE_QUEUED,
                        Download.STATE_DOWNLOADING,
                        Download.STATE_RESTARTING,
                        Download.STATE_STOPPED -> EpisodeDownloadStatus(
                            inProgress = true,
                            progressPercent = download.percentDownloaded
                                .takeIf { it >= 0f && it.isFinite() }
                                ?.toInt(),
                        )
                        Download.STATE_FAILED -> EpisodeDownloadStatus(failed = true)
                        else -> EpisodeDownloadStatus()
                    }
                    download.request.id to status
                }
            }
        }
    }

    val timer = timerController
    val sleepRemainingState = if (timer == null) {
        remember { mutableStateOf<Long?>(null) }
    } else {
        timer.remainingMs.collectAsState()
    }
    val sleepRemainingMs by sleepRemainingState
    val rememberedChoiceState = if (timer == null) {
        remember { mutableStateOf(SleepTimerChoice.OFF) }
    } else {
        timer.rememberedChoice.collectAsState()
    }
    val lastTimerChoice by rememberedChoiceState

    LaunchedEffect(controller, curriculum, timerController) {
        while (isActive) {
            val player = controller
            if (player != null) {
                val item = player.currentMediaItem
                val currentId = item?.mediaId?.takeIf(String::isNotBlank)
                val metadataDuration = currentId?.let { curriculum.episodes[it]?.durationSec?.times(1_000L) } ?: 0L
                val playerDuration = player.duration
                val duration = if (playerDuration == C.TIME_UNSET || playerDuration <= 0L) metadataDuration else playerDuration
                playerSnapshot = PlaybackUiState(
                    currentEpisodeId = currentId,
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = duration,
                    speed = player.playbackParameters.speed,
                )
            }
            timerController?.refresh()
            delay(350L)
        }
    }

    val uiProgress = remember(savedProgress, downloadStatuses, curriculum) {
        (curriculum.episodes.keys + savedProgress.keys + downloadStatuses.keys).associateWith { id ->
            val saved = savedProgress[id]
            val download = downloadStatuses[id] ?: EpisodeDownloadStatus()
            EpisodeProgress(
                positionMs = saved?.positionMs ?: 0L,
                played = saved?.isPlayed == true,
                downloaded = download.completed,
                downloadInProgress = download.inProgress,
                downloadProgressPercent = download.progressPercent,
                downloadFailed = download.failed,
            )
        }
    }
    val playback = playerSnapshot.copy(
        speed = controller?.playbackParameters?.speed ?: preferredSpeed,
        sleepRemainingMs = sleepRemainingMs,
        lastSleepTimerChoice = lastTimerChoice.storedValue,
        journeyMode = journeyMode,
    )
    val state = EEAppState(curriculum = curriculum, progress = uiProgress, playback = playback)

    fun playQueue(queue: List<Episode>, isJourney: Boolean) {
        if (queue.isEmpty()) return
        val player = controller ?: return
        coroutineScope.launch {
            val startPosition = appGraph.episodeStates.getResumePositionMs(queue.first().id)
            journeyMode = isJourney
            player.setMediaItems(queue.map(Episode::toMediaItem), 0, startPosition)
            player.prepare()
            player.play()
        }
    }

    val actions = EEAppActions(
        playJourney = {
            val activePlayer = controller
            if (journeyMode && activePlayer?.isPlaying == true) {
                activePlayer.pause()
            } else {
                runWithNotificationPermission {
                    val playedIds = savedProgress.values.asSequence().filter { it.isPlayed }.map { it.episodeId }.toSet()
                    val firstId = curriculum.firstUnplayed(playedIds)
                    if (firstId != null) {
                        val orderedIds = curriculum.orderedEpisodeIds()
                        val queue = orderedIds.asSequence()
                            .dropWhile { it != firstId }
                            .filter { it !in playedIds }
                            .mapNotNull(curriculum.episodes::get)
                            .toList()
                        playQueue(queue, isJourney = true)
                    }
                }
            }
        },
        playEpisode = { episode ->
            runWithNotificationPermission {
                val orderedIds = curriculum.orderedEpisodeIds()
                val currentIndex = orderedIds.indexOf(episode.id)
                val queue = if (currentIndex < 0) {
                    listOf(episode)
                } else {
                    buildList {
                        add(episode)
                        orderedIds.drop(currentIndex + 1)
                            .filter { savedProgress[it]?.isPlayed != true }
                            .mapNotNullTo(this, curriculum.episodes::get)
                    }
                }
                playQueue(queue, isJourney = false)
            }
        },
        queueEpisode = { episode ->
            runWithNotificationPermission {
                val player = controller
                if (player != null) {
                    val item = episode.toMediaItem()
                    if (player.mediaItemCount == 0) {
                        playQueue(listOf(episode), isJourney = false)
                    } else if ((0 until player.mediaItemCount).none { player.getMediaItemAt(it).mediaId == episode.id }) {
                        player.addMediaItem(item)
                    }
                }
            }
        },
        togglePlayed = { episode ->
            coroutineScope.launch {
                val isPlayed = savedProgress[episode.id]?.isPlayed == true
                appGraph.episodeStates.setPlayed(episode.id, !isPlayed)
            }
        },
        toggleDownload = { episode ->
            if (downloadStatuses[episode.id]?.let { it.completed || it.inProgress } == true) {
                appGraph.downloads.delete(episode.id)
            } else {
                appGraph.downloads.enqueue(episode.toMediaItem())
            }
        },
        downloadNext = { count ->
            val nextItems = curriculum.orderedEpisodeIds()
                .asSequence()
                .filter { savedProgress[it]?.isPlayed != true }
                .mapNotNull(curriculum.episodes::get)
                .take(count.coerceAtLeast(0))
                .map(Episode::toMediaItem)
                .toList()
            appGraph.downloads.enqueueNextEpisodes(nextItems)
        },
        togglePlayback = {
            controller?.let { player ->
                if (player.isPlaying) player.pause() else if (player.currentMediaItem != null) player.play()
            }
        },
        seek = { position -> controller?.seekTo(position.coerceAtLeast(0L)) },
        setSpeed = { speed ->
            val boundedSpeed = speed.coerceIn(0.8f, 2f)
            controller?.setPlaybackParameters(PlaybackParameters(boundedSpeed))
            coroutineScope.launch { appGraph.playbackPreferences.setPlaybackSpeed(boundedSpeed) }
        },
        setSleepTimer = { durationMs ->
            val sleepTimer = timerController
            when (durationMs) {
                null -> sleepTimer?.cancel()
                15L * 60_000L -> sleepTimer?.start(SleepTimerChoice.MINUTES_15)
                30L * 60_000L -> sleepTimer?.start(SleepTimerChoice.MINUTES_30)
                45L * 60_000L -> sleepTimer?.start(SleepTimerChoice.MINUTES_45)
                60L * 60_000L -> sleepTimer?.start(SleepTimerChoice.MINUTES_60)
            }
        },
        sleepUntilEpisodeEnds = { timerController?.endOfCurrentItem() },
        openWebsite = { episode ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(episode.pageUrl)))
            }
        },
    )

    EEAppContent(state = state, actions = actions)
}

private fun Episode.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist("Everything Everywhere Daily")
        .setArtworkUri(imageUrl?.takeIf(String::isNotBlank)?.let(Uri::parse))
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(audioUrl)
        .setMediaMetadata(metadata)
        .build()
}
