package com.eetimeline.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Queues and manages durable Media3 offline downloads for episode MediaItems. */
@UnstableApi
class EpisodeDownloadRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager = PlaybackDownloadRuntime.downloadManager(appContext)

    /** Adds one episode to Media3's persistent download queue. */
    fun enqueue(item: MediaItem, foreground: Boolean = true) {
        sendAddDownload(toDownloadRequest(item), foreground)
    }

    /** Adds all supplied episodes to the persistent download queue. */
    fun enqueueAll(items: List<MediaItem>, foreground: Boolean = true) {
        items.forEach { enqueue(it, foreground) }
    }

    /** Convenience for downloading the next N episodes selected by the UI, in queue order. */
    fun enqueueNextEpisodes(nextEpisodes: List<MediaItem>, foreground: Boolean = true) {
        enqueueAll(nextEpisodes, foreground)
    }

    /** Removes one episode's download and its cached media. */
    fun delete(episodeId: String, foreground: Boolean = true) {
        require(episodeId.isNotBlank()) { "episodeId must not be blank" }
        DownloadService.sendRemoveDownload(
            appContext,
            EpisodeDownloadService::class.java,
            episodeId,
            foreground,
        )
    }

    /** Removes all downloads and their cached media. */
    fun deleteAll(foreground: Boolean = true) {
        DownloadService.sendRemoveAllDownloads(appContext, EpisodeDownloadService::class.java, foreground)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDownloads(): Flow<List<Download>> = callbackFlow {
        val refresh = Channel<Unit>(Channel.CONFLATED)
        val listener = object : DownloadManager.Listener {
            override fun onInitialized(manager: DownloadManager) {
                refresh.trySend(Unit)
            }

            override fun onDownloadChanged(
                manager: DownloadManager,
                download: Download,
                finalException: Exception?,
            ) {
                refresh.trySend(Unit)
            }

            override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                refresh.trySend(Unit)
            }
        }
        downloadManager.addListener(listener)
        val poller = launch {
            while (isActive) {
                val downloads = readAllDownloads()
                trySend(downloads)
                if (downloads.any { it.state == Download.STATE_DOWNLOADING }) {
                    withTimeoutOrNull(DOWNLOAD_PROGRESS_POLL_MS) { refresh.receive() }
                } else {
                    refresh.receive()
                }
            }
        }
        awaitClose {
            downloadManager.removeListener(listener)
            poller.cancel()
            refresh.close()
        }
    }.conflate()

    private suspend fun readAllDownloads(): List<Download> = withContext(Dispatchers.IO) {
        val downloads = mutableListOf<Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        try {
            while (cursor.moveToNext()) downloads += cursor.download
        } finally {
            cursor.close()
        }
        downloads
    }

    /** Returns the persisted Media3 download record, including completed downloads. */
    suspend fun getDownload(episodeId: String): Download? = withContext(Dispatchers.IO) {
        require(episodeId.isNotBlank()) { "episodeId must not be blank" }
        downloadManager.downloadIndex.getDownload(episodeId)
    }

    suspend fun isDownloaded(episodeId: String): Boolean =
        getDownload(episodeId)?.state == Download.STATE_COMPLETED

    private fun sendAddDownload(request: DownloadRequest, foreground: Boolean) {
        DownloadService.sendAddDownload(
            appContext,
            EpisodeDownloadService::class.java,
            request,
            foreground,
        )
    }

    private fun toDownloadRequest(item: MediaItem): DownloadRequest {
        require(item.mediaId.isNotBlank()) { "MediaItem.mediaId must be the episode id" }
        val uri: Uri = item.localConfiguration?.uri
            ?: item.requestMetadata.mediaUri
            ?: throw IllegalArgumentException("MediaItem must have an audio URI")
        val builder = DownloadRequest.Builder(item.mediaId, uri)
        item.localConfiguration?.mimeType?.let(builder::setMimeType)
        return builder.build()
    }

    companion object {
        private const val DOWNLOAD_PROGRESS_POLL_MS = 1_000L
        @Volatile
        private var instance: EpisodeDownloadRepository? = null

        /** Returns the process-wide repository used by the app and download service. */
        fun getInstance(context: Context): EpisodeDownloadRepository =
            instance ?: synchronized(this) {
                instance ?: EpisodeDownloadRepository(context.applicationContext).also { instance = it }
            }
    }

}
