package com.eetimeline

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.eetimeline.data.CurriculumRepository
import com.eetimeline.playback.EpisodeDownloadRepository
import com.eetimeline.playback.EpisodeStateRepository
import com.eetimeline.playback.PlaybackPreferences

@OptIn(UnstableApi::class)
class AppGraph(context: Context) {
    val curriculumRepository = CurriculumRepository(context.applicationContext)
    val episodeStates = EpisodeStateRepository.getInstance(context.applicationContext)
    val playbackPreferences = PlaybackPreferences(context.applicationContext)
    val downloads by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EpisodeDownloadRepository.getInstance(context.applicationContext)
    }
}
