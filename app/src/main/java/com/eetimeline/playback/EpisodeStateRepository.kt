package com.eetimeline.playback

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import com.eetimeline.core.SleepSegment
import com.eetimeline.core.isPlayedByPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "episode_playback_state")
data class EpisodePlaybackEntity(
    @PrimaryKey val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlayed: Boolean,
    val updatedAtMs: Long,
)

@Entity(tableName = "sleep_session")
data class SleepSessionEntity(
    @PrimaryKey val id: Int = SLEEP_SESSION_ID,
    val timerStartedAtMs: Long,
    val fadeEndedAtMs: Long,
)

@Entity(
    tableName = "sleep_session_segment",
    primaryKeys = ["sessionId", "segmentIndex"],
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SleepSessionSegmentEntity(
    val sessionId: Int,
    val segmentIndex: Int,
    val episodeId: String,
    val startPositionMs: Long,
    val endPositionMs: Long,
    val wallClockStartMs: Long,
    val wallClockEndMs: Long,
)

data class SleepSessionWithSegments(
    @Embedded val session: SleepSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val segments: List<SleepSessionSegmentEntity>,
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

    @Transaction
    @Query("SELECT * FROM sleep_session WHERE id = $SLEEP_SESSION_ID")
    fun observeSleepSession(): Flow<SleepSessionWithSegments?>

    @Transaction
    @Query("SELECT * FROM sleep_session WHERE id = $SLEEP_SESSION_ID")
    suspend fun getSleepSession(): SleepSessionWithSegments?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepSession(session: SleepSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepSegments(segments: List<SleepSessionSegmentEntity>)

    @Query("DELETE FROM sleep_session WHERE id = $SLEEP_SESSION_ID")
    suspend fun deleteSleepSession()
}

private const val SLEEP_SESSION_ID = 1

@Database(
    entities = [EpisodePlaybackEntity::class, SleepSessionEntity::class, SleepSessionSegmentEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class EpisodeStateDatabase : RoomDatabase() {
    abstract fun episodePlaybackDao(): EpisodePlaybackDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS sleep_session (" +
                        "id INTEGER NOT NULL, timerStartedAtMs INTEGER NOT NULL, " +
                        "fadeEndedAtMs INTEGER NOT NULL, PRIMARY KEY(id))",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS sleep_session_segment (" +
                        "sessionId INTEGER NOT NULL, segmentIndex INTEGER NOT NULL, episodeId TEXT NOT NULL, " +
                        "startPositionMs INTEGER NOT NULL, endPositionMs INTEGER NOT NULL, " +
                        "wallClockStartMs INTEGER NOT NULL, wallClockEndMs INTEGER NOT NULL, " +
                        "PRIMARY KEY(sessionId, segmentIndex), " +
                        "FOREIGN KEY(sessionId) REFERENCES sleep_session(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sleep_session_segment_sessionId " +
                        "ON sleep_session_segment(sessionId)",
                )
            }
        }

        @Volatile
        private var instance: EpisodeStateDatabase? = null

        fun getInstance(context: Context): EpisodeStateDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EpisodeStateDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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

data class SleepSession(
    val timerStartedAtMs: Long,
    val fadeEndedAtMs: Long,
    val segments: List<SleepSegment>,
)

/** Durable per-episode resume position and manual/automatic played state. */
class EpisodeStateRepository private constructor(context: Context) {
    private val database = EpisodeStateDatabase.getInstance(context)
    private val dao = database.episodePlaybackDao()

    suspend fun getState(episodeId: String): EpisodePlaybackState? = dao.get(episodeId)?.toState()

    fun observeState(episodeId: String): Flow<EpisodePlaybackState?> =
        dao.observe(episodeId).map { it?.toState() }

    fun observeAll(): Flow<List<EpisodePlaybackState>> =
        dao.observeAll().map { states -> states.map { it.toState() } }

    fun observeSleepSession(): Flow<SleepSession?> =
        dao.observeSleepSession().map { it?.toSleepSession() }

    suspend fun getSleepSession(): SleepSession? = dao.getSleepSession()?.toSleepSession()

    suspend fun recordSleepSession(
        timerStartedAtMs: Long,
        fadeEndedAtMs: Long,
        segments: List<SleepSegment>,
    ) {
        database.withTransaction {
            dao.deleteSleepSession()
            dao.insertSleepSession(
                SleepSessionEntity(
                    timerStartedAtMs = timerStartedAtMs,
                    fadeEndedAtMs = fadeEndedAtMs,
                ),
            )
            dao.insertSleepSegments(
                segments.mapIndexed { index, segment ->
                    SleepSessionSegmentEntity(
                        sessionId = SLEEP_SESSION_ID,
                        segmentIndex = index,
                        episodeId = segment.episodeId,
                        startPositionMs = segment.startPositionMs,
                        endPositionMs = segment.endPositionMs,
                        wallClockStartMs = segment.wallClockStartMs,
                        wallClockEndMs = segment.wallClockEndMs,
                    )
                },
            )
        }
    }

    suspend fun resolveSleepSession() {
        database.withTransaction { dao.deleteSleepSession() }
    }

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

    private fun SleepSessionWithSegments.toSleepSession() = SleepSession(
        timerStartedAtMs = session.timerStartedAtMs,
        fadeEndedAtMs = session.fadeEndedAtMs,
        segments = segments.sortedBy { it.segmentIndex }.map { segment ->
            SleepSegment(
                episodeId = segment.episodeId,
                startPositionMs = segment.startPositionMs,
                endPositionMs = segment.endPositionMs,
                wallClockStartMs = segment.wallClockStartMs,
                wallClockEndMs = segment.wallClockEndMs,
            )
        },
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

