package com.eetimeline.ui

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

data class PlaybackUiState(
    val currentEpisodeId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val sleepRemainingMs: Long? = null,
    val lastSleepTimerChoice: Int = 0,
    val journeyMode: Boolean = false,
)
