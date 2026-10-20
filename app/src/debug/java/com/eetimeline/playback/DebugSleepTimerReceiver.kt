package com.eetimeline.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** ADB-only one-minute sleep timer for debug-build fade/session testing. */
class DebugSleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEBUG_START_SHORT_SLEEP_TIMER) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, PlaybackService::class.java)
                .setAction(ACTION_DEBUG_START_SHORT_SLEEP_TIMER),
        )
    }
}
