package com.eetimeline.ui

import com.eetimeline.core.JourneyPlan

data class EpisodeProgress(
    val positionMs: Long = 0L,
    val played: Boolean = false,
    val downloaded: Boolean = false,
    val downloadInProgress: Boolean = false,
    val downloadProgressPercent: Int? = null,
    val downloadFailed: Boolean = false,
)

data class EpisodeDownloadStatus(
    val completed: Boolean = false,
    val inProgress: Boolean = false,
    val progressPercent: Int? = null,
    val failed: Boolean = false,
)

data class JourneyPlanPreview(
    val plan: JourneyPlan,
    val storyCount: Int,
    val estimatedDurationMs: Long,
    val finishTimeLabel: String,
)

data class PlaybackUiState(
    val currentEpisodeId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val sleepRemainingMs: Long? = null,
    val sleepTimerActive: Boolean = false,
    val lastSleepTimerChoice: Int = 0,
    val journeyMode: Boolean = false,
    val activeJourneyPlan: JourneyPlan? = null,
    val journeyStoryIndex: Int = 0,
    val journeyStoryCount: Int = 0,
    val bedtimeMode: Boolean = false,
    val voiceBoost: Boolean = false,
    val skipSilence: Boolean = false,
)

