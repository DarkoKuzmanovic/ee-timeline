package com.eetimeline.playback

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import com.eetimeline.core.isPlayedByPosition
import kotlinx.coroutines.flow.map

@Entity(tableName = "episode_playback_state")
data class EpisodePlaybackEntity(
    @PrimaryKey val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlayed: Boolean,
    val updatedAtMs: Long,
)

@Dao
interface EpisodePlaybackDao {
    @Query("SELECT * FROM episode_playback_state WHERE episodeId = :episodeId")
    suspend fun get(episodeId: String): EpisodePlaybackEntity?

    @Query("SELECT * FROM episode_playback_state WHERE episodeId = :episodeId")
    fun observe(episodeId: String): Flow<EpisodePlaybackEntity?>

    @Query("SELECT * FROM episode_playback_state ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<EpisodePlaybackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(state: EpisodePlaybackEntity)

    @Query(
        "UPDATE episode_playback_state " +
            "SET positionMs = :positionMs, durationMs = :durationMs, " +
            "isPlayed = CASE WHEN isPlayed = 1 OR :completed = 1 THEN 1 ELSE 0 END, " +
            "updatedAtMs = :updatedAtMs WHERE episodeId = :episodeId",
    )
    suspend fun updateProgress(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
        updatedAtMs: Long,
    ): Int

    @Query(
        "UPDATE episode_playback_state SET isPlayed = :played, updatedAtMs = :updatedAtMs " +
            "WHERE episodeId = :episodeId",
    )
    suspend fun updatePlayed(episodeId: String, played: Boolean, updatedAtMs: Long): Int

    @Query(
        "UPDATE episode_playback_state SET positionMs = 0, durationMs = 0, updatedAtMs = :updatedAtMs " +
            "WHERE episodeId = :episodeId",
    )
    suspend fun clearProgress(episodeId: String, updatedAtMs: Long): Int
}

@Database(entities = [EpisodePlaybackEntity::class], version = 1, exportSchema = false)
abstract class EpisodeStateDatabase : RoomDatabase() {
    abstract fun episodePlaybackDao(): EpisodePlaybackDao

    companion object {
        @Volatile
        private var instance: EpisodeStateDatabase? = null

        fun getInstance(context: Context): EpisodeStateDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EpisodeStateDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }

        private const val DATABASE_NAME = "episode_playback_state.db"
    }
}

data class EpisodePlaybackState(
    val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlayed: Boolean,
    val updatedAtMs: Long,
)

/** Durable per-episode resume position and manual/automatic played state. */
class EpisodeStateRepository private constructor(context: Context) {
    private val dao = EpisodeStateDatabase.getInstance(context).episodePlaybackDao()

    suspend fun getState(episodeId: String): EpisodePlaybackState? = dao.get(episodeId)?.toState()

    fun observeState(episodeId: String): Flow<EpisodePlaybackState?> =
        dao.observe(episodeId).map { it?.toState() }

    fun observeAll(): Flow<List<EpisodePlaybackState>> =
        dao.observeAll().map { states -> states.map { it.toState() } }

    suspend fun getResumePositionMs(episodeId: String): Long =
        dao.get(episodeId)?.positionMs ?: 0L

    /** Saves resume progress, preserving a manually played state and marking near-completion played. */
    suspend fun saveProgress(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        forceCompleted: Boolean = false,
    ) {
        require(episodeId.isNotBlank()) { "episodeId must not be blank" }
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceAtLeast(0L).let { position ->
            if (safeDuration > 0L) position.coerceAtMost(safeDuration) else position
        }
        val completed = forceCompleted || (safePosition > 0L && isPlayedByPosition(safeDuration, safePosition))
        val updatedAt = System.currentTimeMillis()
        val updated = dao.updateProgress(
            episodeId = episodeId,
            positionMs = safePosition,
            durationMs = safeDuration,
            completed = completed,
            updatedAtMs = updatedAt,
        )
        if (updated == 0) {
            dao.insertOrReplace(
                EpisodePlaybackEntity(
                    episodeId = episodeId,
                    positionMs = safePosition,
                    durationMs = safeDuration,
                    isPlayed = completed,
                    updatedAtMs = updatedAt,
                ),
            )
        }
    }

    /** Allows the UI to mark an episode played or unplayed independently of playback. */
    suspend fun setPlayed(episodeId: String, played: Boolean) {
        require(episodeId.isNotBlank()) { "episodeId must not be blank" }
        val updatedAt = System.currentTimeMillis()
        if (dao.updatePlayed(episodeId, played, updatedAt) == 0) {
            dao.insertOrReplace(
                EpisodePlaybackEntity(
                    episodeId = episodeId,
                    positionMs = 0L,
                    durationMs = 0L,
                    isPlayed = played,
                    updatedAtMs = updatedAt,
                ),
            )
        }
    }

    suspend fun clearProgress(episodeId: String) {
        require(episodeId.isNotBlank()) { "episodeId must not be blank" }
        if (dao.clearProgress(episodeId, System.currentTimeMillis()) == 0) {
            dao.insertOrReplace(
                EpisodePlaybackEntity(
                    episodeId = episodeId,
                    positionMs = 0L,
                    durationMs = 0L,
                    isPlayed = false,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun EpisodePlaybackEntity.toState() = EpisodePlaybackState(
        episodeId = episodeId,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlayed = isPlayed,
        updatedAtMs = updatedAtMs,
    )

    companion object {
        @Volatile
        private var instance: EpisodeStateRepository? = null

        /** Returns the process-wide repository used by both the app and playback service. */
        fun getInstance(context: Context): EpisodeStateRepository =
            instance ?: synchronized(this) {
                instance ?: EpisodeStateRepository(context.applicationContext).also { instance = it }
            }
    }
}
