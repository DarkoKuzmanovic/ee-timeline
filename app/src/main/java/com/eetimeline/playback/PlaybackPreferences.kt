package com.eetimeline.playback

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eetimeline.core.JourneyPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playbackPreferencesDataStore by preferencesDataStore(name = "playback_preferences")

enum class SleepTimerChoice(val storedValue: Int) {
    OFF(0),
    MINUTES_15(15),
    MINUTES_30(30),
    MINUTES_45(45),
    MINUTES_60(60),
    END_OF_CURRENT_ITEM(-1);

    companion object {
        fun fromStoredValue(value: Int): SleepTimerChoice =
            values().firstOrNull { it.storedValue == value } ?: OFF
    }
}

data class PlaybackSettings(
    val speed: Float = PlaybackPreferences.DEFAULT_SPEED,
    /** Last selected sleep-timer choice, retained after the timer is canceled or expires. */
    val sleepTimerChoice: SleepTimerChoice = SleepTimerChoice.OFF,
    /** Prefer cached/downloaded media when it is available; falls back to network on a cache miss. */
    val preferDownloadedAudio: Boolean = true,
    val bedtimeStartMinute: Int = PlaybackPreferences.DEFAULT_BEDTIME_START_MINUTE,
    val bedtimeEndMinute: Int = PlaybackPreferences.DEFAULT_BEDTIME_END_MINUTE,
    val autoSleepTimerInBedtime: Boolean = true,
    val shakeToExtend: Boolean = true,
    val rewindOffsetMinutes: Int = PlaybackPreferences.DEFAULT_REWIND_OFFSET_MINUTES,
    val voiceBoost: Boolean = false,
    val skipSilence: Boolean = false,
    val lastJourneyPlan: JourneyPlan = JourneyPlan.JUST_KEEP_GOING,
)

/** DataStore-backed settings shared by the UI and [PlaybackService]. */
class PlaybackPreferences(context: Context) {
    private val store = context.applicationContext.playbackPreferencesDataStore

    val settings: Flow<PlaybackSettings> = store.data.map { preferences ->
        val storedRewindOffset = preferences[REWIND_OFFSET_MINUTES] ?: DEFAULT_REWIND_OFFSET_MINUTES
        PlaybackSettings(
            speed = (preferences[PLAYBACK_SPEED] ?: DEFAULT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED),
            sleepTimerChoice = SleepTimerChoice.fromStoredValue(preferences[SLEEP_TIMER_CHOICE] ?: 0),
            preferDownloadedAudio = preferences[PREFER_DOWNLOADED_AUDIO] ?: true,
            bedtimeStartMinute = (preferences[BEDTIME_START_MINUTE] ?: DEFAULT_BEDTIME_START_MINUTE)
                .coerceIn(0, MINUTES_PER_DAY - 1),
            bedtimeEndMinute = (preferences[BEDTIME_END_MINUTE] ?: DEFAULT_BEDTIME_END_MINUTE)
                .coerceIn(0, MINUTES_PER_DAY - 1),
            autoSleepTimerInBedtime = preferences[AUTO_SLEEP_TIMER_IN_BEDTIME] ?: true,
            shakeToExtend = preferences[SHAKE_TO_EXTEND] ?: true,
            rewindOffsetMinutes = when (storedRewindOffset) {
                2, 5, 10 -> storedRewindOffset
                else -> DEFAULT_REWIND_OFFSET_MINUTES
            },
            voiceBoost = preferences[VOICE_BOOST] ?: false,
            skipSilence = preferences[SKIP_SILENCE] ?: false,
            lastJourneyPlan = JourneyPlan.values().firstOrNull {
                it.name == preferences[LAST_JOURNEY_PLAN]
            } ?: JourneyPlan.JUST_KEEP_GOING,
        )
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        require(speed.isFinite()) { "speed must be finite" }
        store.edit { it[PLAYBACK_SPEED] = speed.coerceIn(MIN_SPEED, MAX_SPEED) }
    }

    suspend fun setSleepTimerChoice(choice: SleepTimerChoice) {
        require(choice != SleepTimerChoice.OFF) { "sleep timer choice must be selectable" }
        store.edit { it[SLEEP_TIMER_CHOICE] = choice.storedValue }
    }

    suspend fun setPreferDownloadedAudio(prefer: Boolean) {
        store.edit { it[PREFER_DOWNLOADED_AUDIO] = prefer }
    }

    suspend fun setBedtimeStartMinute(minuteOfDay: Int) {
        store.edit { it[BEDTIME_START_MINUTE] = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1) }
    }

    suspend fun setBedtimeEndMinute(minuteOfDay: Int) {
        store.edit { it[BEDTIME_END_MINUTE] = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1) }
    }

    suspend fun setAutoSleepTimerInBedtime(enabled: Boolean) {
        store.edit { it[AUTO_SLEEP_TIMER_IN_BEDTIME] = enabled }
    }

    suspend fun setShakeToExtend(enabled: Boolean) {
        store.edit { it[SHAKE_TO_EXTEND] = enabled }
    }

    suspend fun setRewindOffsetMinutes(minutes: Int) {
        require(minutes == 2 || minutes == 5 || minutes == 10) {
            "rewind offset must be 2, 5, or 10 minutes"
        }
        store.edit { it[REWIND_OFFSET_MINUTES] = minutes }
    }

    suspend fun setVoiceBoost(enabled: Boolean) {
        store.edit { it[VOICE_BOOST] = enabled }
    }

    suspend fun setSkipSilence(enabled: Boolean) {
        store.edit { it[SKIP_SILENCE] = enabled }
    }

    suspend fun setLastJourneyPlan(plan: JourneyPlan) {
        store.edit { it[LAST_JOURNEY_PLAN] = plan.name }
    }

    companion object {
        const val DEFAULT_SPEED = 1.0f
        const val MIN_SPEED = 0.8f
        const val MAX_SPEED = 2.0f
        const val DEFAULT_BEDTIME_START_MINUTE = 21 * 60 + 30
        const val DEFAULT_BEDTIME_END_MINUTE = 6 * 60
        const val DEFAULT_REWIND_OFFSET_MINUTES = 5
        private const val MINUTES_PER_DAY = 24 * 60

        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val SLEEP_TIMER_CHOICE = intPreferencesKey("sleep_timer_choice")
        private val PREFER_DOWNLOADED_AUDIO = booleanPreferencesKey("prefer_downloaded_audio")
        private val BEDTIME_START_MINUTE = intPreferencesKey("bedtime_start_minute")
        private val BEDTIME_END_MINUTE = intPreferencesKey("bedtime_end_minute")
        private val AUTO_SLEEP_TIMER_IN_BEDTIME = booleanPreferencesKey("auto_sleep_timer_in_bedtime")
        private val SHAKE_TO_EXTEND = booleanPreferencesKey("shake_to_extend")
        private val REWIND_OFFSET_MINUTES = intPreferencesKey("rewind_offset_minutes")
        private val VOICE_BOOST = booleanPreferencesKey("voice_boost")
        private val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        private val LAST_JOURNEY_PLAN = stringPreferencesKey("last_journey_plan")
    }
}

