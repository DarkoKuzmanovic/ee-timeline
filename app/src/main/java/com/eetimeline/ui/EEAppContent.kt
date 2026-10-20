package com.eetimeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eetimeline.core.Curriculum
import com.eetimeline.core.Episode
import com.eetimeline.core.JourneyPlan
import com.eetimeline.core.SleepRewindPoint
import com.eetimeline.core.orderedEpisodeIds
import com.eetimeline.playback.PlaybackSettings
import com.eetimeline.playback.SleepSession
import com.eetimeline.ui.components.BottomNavigation
import com.eetimeline.ui.components.EpisodeDetailSheet
import com.eetimeline.ui.components.MiniPlayer
import com.eetimeline.ui.screens.JourneyScreen
import com.eetimeline.ui.screens.NowPlayingScreen
import com.eetimeline.ui.screens.SettingsScreen
import com.eetimeline.ui.screens.TimelineScreen
import com.eetimeline.ui.screens.UnitDetailScreen
import com.eetimeline.ui.screens.UnitsScreen
import com.eetimeline.ui.theme.MutedCream
private const val ROUTE_JOURNEY = "journey"
private const val ROUTE_TIMELINE = "timeline"
private const val ROUTE_UNITS = "units"
private const val ROUTE_UNIT = "unit/{number}"
private const val ROUTE_NOW_PLAYING = "now-playing"
private const val ROUTE_SETTINGS = "settings"

data class EEAppState(
    val curriculum: Curriculum = Curriculum(),
    val progress: Map<String, EpisodeProgress> = emptyMap(),
    val playback: PlaybackUiState = PlaybackUiState(),
    val settings: PlaybackSettings = PlaybackSettings(),
    val planPreviews: List<JourneyPlanPreview> = emptyList(),
    val sleepSession: SleepSession? = null,
    val suggestedRewindPoint: SleepRewindPoint? = null,
)

data class EEAppActions(
    val playJourney: () -> Unit,
    val playJourneyPlan: (JourneyPlan) -> Unit,
    val rewindSleepSession: (SleepRewindPoint) -> Unit,
    val keepSleepSessionPlace: () -> Unit,
    val playEpisode: (Episode) -> Unit,
    val queueEpisode: (Episode) -> Unit,
    val togglePlayed: (Episode) -> Unit,
    val toggleDownload: (Episode) -> Unit,
    val downloadNext: (Int) -> Unit,
    val togglePlayback: () -> Unit,
    val seek: (Long) -> Unit,
    val setSpeed: (Float) -> Unit,
    val setSleepTimer: (Long?) -> Unit,
    val sleepUntilEpisodeEnds: () -> Unit,
    val extendSleepTimer: () -> Unit,
    val toggleBedtimeMode: () -> Unit,
    val setBedtimeStartMinute: (Int) -> Unit,
    val setBedtimeEndMinute: (Int) -> Unit,
    val setAutoSleepTimerInBedtime: (Boolean) -> Unit,
    val setShakeToExtend: (Boolean) -> Unit,
    val setRewindOffsetMinutes: (Int) -> Unit,
    val setVoiceBoost: (Boolean) -> Unit,
    val setSkipSilence: (Boolean) -> Unit,
    val openWebsite: (Episode) -> Unit,
)

@Composable
fun EEAppContent(state: EEAppState, actions: EEAppActions) {
    val haptics = LocalHapticFeedback.current
    val navController = rememberNavController()
    val navEntry by navController.currentBackStackEntryAsState()
    val route = navEntry?.destination?.route
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    val episode = state.playback.currentEpisodeId?.let(state.curriculum.episodes::get)
    val ordered = state.curriculum.orderedEpisodeIds()
    val upcoming = ordered.asSequence()
        .filter { state.progress[it]?.played != true }
        .mapNotNull(state.curriculum.episodes::get)
        .take(8)
        .toList()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = if (route == ROUTE_NOW_PLAYING && state.playback.bedtimeMode) Color.Black else MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
                if (route != ROUTE_NOW_PLAYING && episode != null) {
                    MiniPlayer(
                        title = episode.title,
                        imageUrl = episode.imageUrl,
                        playback = state.playback,
                        onOpen = { navController.navigate(ROUTE_NOW_PLAYING) },
                        onPlayPause = actions.togglePlayback,
                    )
                }
                if (route in setOf(ROUTE_JOURNEY, ROUTE_TIMELINE, ROUTE_UNITS)) {
                    BottomNavigation(
                        currentRoute = route,
                        onNavigate = { destination ->
                            navController.navigate(destination) {
                                popUpTo(ROUTE_JOURNEY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_JOURNEY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_JOURNEY) {
                JourneyScreen(
                    curriculum = state.curriculum,
                    progress = state.progress,
                    playback = state.playback,
                    upcoming = upcoming,
                    planPreviews = state.planPreviews,
                    selectedPlan = state.settings.lastJourneyPlan,
                    sleepSession = state.sleepSession,
                    suggestedRewindPoint = state.suggestedRewindPoint,
                    onContinue = actions.playJourney,
                    onPlayPlan = actions.playJourneyPlan,
                    onRewind = actions.rewindSleepSession,
                    onKeepMyPlace = actions.keepSleepSessionPlace,
                    onSettings = { navController.navigate(ROUTE_SETTINGS) },
                    onEpisodeClick = { selectedEpisode = it },
                    onDownloadNext = actions.downloadNext,
                )
            }
            composable(ROUTE_TIMELINE) {
                TimelineScreen(
                    curriculum = state.curriculum,
                    progress = state.progress,
                    currentEpisodeId = state.playback.currentEpisodeId,
                    onEpisodeClick = { selectedEpisode = it },
                )
            }
            composable(ROUTE_UNITS) {
                UnitsScreen(
                    curriculum = state.curriculum,
                    progress = state.progress,
                    onUnitClick = { number -> navController.navigate("unit/$number") },
                )
            }
            composable(
                route = ROUTE_UNIT,
                arguments = listOf(navArgument("number") { type = NavType.IntType }),
            ) { entry ->
                val number = entry.arguments?.getInt("number")
                val unit = state.curriculum.units.firstOrNull { it.number == number }
                if (unit != null) {
                    UnitDetailScreen(
                        unit = unit,
                        episodeLookup = state.curriculum.episodes,
                        progress = state.progress,
                        playback = state.playback,
                        onBack = { navController.popBackStack() },
                        onEpisodeClick = { selectedEpisode = it },
                    )
                } else {
                    EmptyCurriculumScreen()
                }
            }
            composable(ROUTE_SETTINGS) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back to journey")
                        }
                        Text("Back to the journey", color = MutedCream)
                    }
                    Box(Modifier.weight(1f)) {
                        SettingsScreen(
                            settings = state.settings,
                            onBedtimeStartMinuteChange = actions.setBedtimeStartMinute,
                            onBedtimeEndMinuteChange = actions.setBedtimeEndMinute,
                            onAutoSleepTimerInBedtimeChange = actions.setAutoSleepTimerInBedtime,
                            onShakeToExtendChange = actions.setShakeToExtend,
                            onRewindOffsetMinutesChange = actions.setRewindOffsetMinutes,
                            onDefaultPlaybackSpeedChange = actions.setSpeed,
                            onVoiceBoostChange = actions.setVoiceBoost,
                            onSkipSilenceChange = actions.setSkipSilence,
                        )
                    }
                }
            }
            composable(ROUTE_NOW_PLAYING) {
                NowPlayingScreen(
                    episode = episode,
                    playback = state.playback,
                    badge = episode?.let { episodeBadge(state.curriculum, it.id) } ?: "Everything Everywhere Daily",
                    played = episode?.let { state.progress[it.id]?.played } == true,
                    downloaded = episode?.let { state.progress[it.id]?.downloaded } == true,
                    downloadInProgress = episode?.let { state.progress[it.id]?.downloadInProgress } == true,
                    downloadProgressPercent = episode?.let { state.progress[it.id]?.downloadProgressPercent },
                    downloadFailed = episode?.let { state.progress[it.id]?.downloadFailed } == true,
                    onBack = { navController.popBackStack() },
                    onPlayPause = actions.togglePlayback,
                    onSeek = actions.seek,
                    onSpeed = actions.setSpeed,
                    onTimer = actions.setSleepTimer,
                    onEndOfEpisodeTimer = actions.sleepUntilEpisodeEnds,
                    onExtendSleepTimer = actions.extendSleepTimer,
                    onToggleBedtime = actions.toggleBedtimeMode,
                    onVoiceBoostChange = actions.setVoiceBoost,
                    onSkipSilenceChange = actions.setSkipSilence,
                    onMarkPlayed = { episode?.let(actions.togglePlayed) },
                    onDownload = { episode?.let(actions.toggleDownload) },
                    onOpenWebsite = { episode?.let(actions.openWebsite) },
                )
            }
        }
    }

    selectedEpisode?.let { selected ->
        EpisodeDetailSheet(
            episode = selected,
            badge = episodeBadge(state.curriculum, selected.id),
            played = state.progress[selected.id]?.played == true,
            downloaded = state.progress[selected.id]?.downloaded == true,
            downloadInProgress = state.progress[selected.id]?.downloadInProgress == true,
            downloadProgressPercent = state.progress[selected.id]?.downloadProgressPercent,
            downloadFailed = state.progress[selected.id]?.downloadFailed == true,
            onDismiss = { selectedEpisode = null },
            onPlay = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.playEpisode(selected)
                selectedEpisode = null
                navController.navigate(ROUTE_NOW_PLAYING)
            },
            onQueue = { actions.queueEpisode(selected); selectedEpisode = null },
            onDownload = { actions.toggleDownload(selected) },
            onTogglePlayed = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.togglePlayed(selected)
            },
        )
    }
}

@Composable
private fun EmptyCurriculumScreen() {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraLarge) {
                androidx.compose.material3.Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp))
            }
            Text("Your next stop is being mapped", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("The curriculum is not available yet. Your listening space is ready when it arrives.", color = MutedCream)
        }
    }
}

private fun episodeBadge(curriculum: Curriculum, episodeId: String): String {
    curriculum.units.forEach { unit ->
        unit.topics.forEach { topic ->
            if (topic.groups.any { episodeId in it.episodeIds }) return "UNIT ${unit.number} · ${topic.id} ${topic.title}"
        }
    }
    return "Your curriculum journey"
}
