package com.eetimeline.playback

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
)

/** DataStore-backed settings shared by the UI and [PlaybackService]. */
class PlaybackPreferences(context: Context) {
    private val store = context.applicationContext.playbackPreferencesDataStore

    val settings: Flow<PlaybackSettings> = store.data.map { preferences ->
        PlaybackSettings(
            speed = (preferences[PLAYBACK_SPEED] ?: DEFAULT_SPEED).coerceIn(MIN_SPEED, MAX_SPEED),
            sleepTimerChoice = SleepTimerChoice.fromStoredValue(preferences[SLEEP_TIMER_CHOICE] ?: 0),
            preferDownloadedAudio = preferences[PREFER_DOWNLOADED_AUDIO] ?: true,
        )
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        require(speed.isFinite()) { "speed must be finite" }
        store.edit { it[PLAYBACK_SPEED] = speed.coerceIn(MIN_SPEED, MAX_SPEED) }
    }

    suspend fun setSleepTimerChoice(choice: SleepTimerChoice) {
        store.edit { it[SLEEP_TIMER_CHOICE] = choice.storedValue }
    }

    suspend fun setPreferDownloadedAudio(prefer: Boolean) {
        store.edit { it[PREFER_DOWNLOADED_AUDIO] = prefer }
    }

    companion object {
        const val DEFAULT_SPEED = 1.0f
        const val MIN_SPEED = 0.8f
        const val MAX_SPEED = 2.0f

        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        private val SLEEP_TIMER_CHOICE = intPreferencesKey("sleep_timer_choice")
        private val PREFER_DOWNLOADED_AUDIO = booleanPreferencesKey("prefer_downloaded_audio")
    }
}
