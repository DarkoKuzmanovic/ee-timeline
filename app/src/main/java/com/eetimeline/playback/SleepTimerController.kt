package com.eetimeline.playback

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity-facing sleep timer commands and observable service timer state. */
@UnstableApi
class SleepTimerController(private val mediaController: MediaController) {
    private val _remainingMs = MutableStateFlow(readRemaining(mediaController.sessionExtras))
    private val _isActive = MutableStateFlow(readIsActive(mediaController.sessionExtras))
    private val _rememberedChoice = MutableStateFlow(readChoice(mediaController.sessionExtras))

    /** Null means remaining time is unavailable (no timer or unresolved end-of-item duration). */
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /** Last selected choice, even after its timer expires or is canceled. */
    val rememberedChoice: StateFlow<SleepTimerChoice> = _rememberedChoice.asStateFlow()

    fun refresh() {
        val extras = mediaController.sessionExtras
        _remainingMs.value = readRemaining(extras)
        _isActive.value = readIsActive(extras)
        _rememberedChoice.value = readChoice(extras)
    }

    fun start15Minutes(): ListenableFuture<SessionResult> = start(SleepTimerChoice.MINUTES_15)

    fun start30Minutes(): ListenableFuture<SessionResult> = start(SleepTimerChoice.MINUTES_30)

    fun start45Minutes(): ListenableFuture<SessionResult> = start(SleepTimerChoice.MINUTES_45)

    fun start60Minutes(): ListenableFuture<SessionResult> = start(SleepTimerChoice.MINUTES_60)

    fun endOfCurrentItem(): ListenableFuture<SessionResult> = start(SleepTimerChoice.END_OF_CURRENT_ITEM)

    /** Starts the selected timer; passing OFF is equivalent to [cancel]. */
    fun start(choice: SleepTimerChoice): ListenableFuture<SessionResult> {
        if (choice == SleepTimerChoice.OFF) return cancel()
        val args = Bundle().apply {
            putInt(SleepTimerProtocol.KEY_CHOICE, choice.storedValue)
        }
        return send(SleepTimerProtocol.ACTION_START, args)
    }

    fun cancel(): ListenableFuture<SessionResult> = send(SleepTimerProtocol.ACTION_CANCEL)


    private fun send(action: String, args: Bundle = Bundle.EMPTY): ListenableFuture<SessionResult> =
        mediaController.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)

    private fun readRemaining(extras: Bundle): Long? =
        extras.getLong(SleepTimerProtocol.KEY_REMAINING_MS, NO_TIMER).takeIf { it >= 0L }

    private fun readIsActive(extras: Bundle): Boolean =
        extras.getBoolean(SleepTimerProtocol.KEY_ACTIVE, false)
    private fun readChoice(extras: Bundle): SleepTimerChoice = SleepTimerChoice.fromStoredValue(
        extras.getInt(SleepTimerProtocol.KEY_CHOICE, SleepTimerChoice.OFF.storedValue),
    )

    private companion object {
        const val NO_TIMER = -1L
    }
}

internal object SleepTimerProtocol {
    const val ACTION_START = "com.eetimeline.playback.action.START_SLEEP_TIMER"
    const val ACTION_CANCEL = "com.eetimeline.playback.action.CANCEL_SLEEP_TIMER"
    const val KEY_CHOICE = "sleep_timer_choice"
    const val KEY_REMAINING_MS = "sleep_timer_remaining_ms"
    const val KEY_ACTIVE = "sleep_timer_active"
}
