package com.eetimeline.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Shared persistent cache/database used by streamed playback and Media3 downloads. */
@UnstableApi
internal object PlaybackDownloadRuntime {
    private const val CACHE_DIRECTORY = "episode_media_cache"

    @Volatile
    private var runtime: Runtime? = null

    private fun getRuntime(context: Context): Runtime {
        runtime?.let { return it }
        return synchronized(this) {
            runtime ?: createRuntime(context.applicationContext).also { runtime = it }
        }
    }

    private fun createRuntime(context: Context): Runtime {
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(
            File(context.filesDir, CACHE_DIRECTORY),
            NoOpCacheEvictor(),
            databaseProvider,
        )
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val cachedFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val downloadManager = DownloadManager(
            context,
            databaseProvider,
            cache,
            upstreamFactory,
            Executors.newFixedThreadPool(DOWNLOAD_WORKER_COUNT),
        ).apply {
            setMaxParallelDownloads(DOWNLOAD_WORKER_COUNT)
        }
        return Runtime(cachedFactory, upstreamFactory, downloadManager)
    }

    fun downloadManager(context: Context): DownloadManager = getRuntime(context).downloadManager

    fun mediaSourceFactory(context: Context, preferDownloadedAudio: AtomicBoolean): DefaultMediaSourceFactory {
        val runtime = getRuntime(context)
        val sourceFactory = PreferredCacheDataSourceFactory(
            cachedFactory = runtime.cachedFactory,
            upstreamFactory = runtime.upstreamFactory,
            preferCache = preferDownloadedAudio,
        )
        return DefaultMediaSourceFactory(sourceFactory)
    }

    private data class Runtime(
        val cachedFactory: CacheDataSource.Factory,
        val upstreamFactory: DataSource.Factory,
        val downloadManager: DownloadManager,
    )

    private class PreferredCacheDataSourceFactory(
        private val cachedFactory: DataSource.Factory,
        private val upstreamFactory: DataSource.Factory,
        private val preferCache: AtomicBoolean,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SwitchingDataSource(
            cachedFactory = cachedFactory,
            upstreamFactory = upstreamFactory,
            preferCache = preferCache,
        )
    }

    private class SwitchingDataSource(
        private val cachedFactory: DataSource.Factory,
        private val upstreamFactory: DataSource.Factory,
        private val preferCache: AtomicBoolean,
    ) : DataSource {
        private val transferListeners = mutableListOf<TransferListener>()
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            transferListeners += transferListener
            delegate?.addTransferListener(transferListener)
        }

        @Throws(IOException::class)
        override fun open(dataSpec: DataSpec): Long {
            close()
            val source = if (preferCache.get()) cachedFactory.createDataSource() else upstreamFactory.createDataSource()
            transferListeners.forEach(source::addTransferListener)
            delegate = source
            return source.open(dataSpec)
        }

        @Throws(IOException::class)
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            checkNotNull(delegate) { "DataSource has not been opened" }.read(buffer, offset, length)

        override fun getUri(): Uri? = delegate?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            delegate?.responseHeaders ?: emptyMap()

        @Throws(IOException::class)
        override fun close() {
            val source = delegate ?: return
            delegate = null
            source.close()
        }
    }

    private const val DOWNLOAD_WORKER_COUNT = 2
}
