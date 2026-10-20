package com.eetimeline.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.eetimeline.core.JourneyPlan
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

/** Session command and extras shared by the service, UI, widgets, and quick settings tiles. */
object JourneyPlaybackProtocol {
    const val ACTION_PLAY_JOURNEY = "com.eetimeline.playback.action.PLAY_JOURNEY"
    const val ACTION_EXTEND_SLEEP_TIMER = "com.eetimeline.playback.action.EXTEND_SLEEP_TIMER"
    const val KEY_PLAN = "journey_plan"
    const val KEY_ACTIVE_PLAN = "active_journey_plan"
    /** One-based index within the active queue, or zero when no journey is active. */
    const val KEY_STORY_INDEX = "journey_story_index"
    /** Number of selected episodes in the active journey, or zero when inactive. */
    const val KEY_STORY_COUNT = "journey_story_count"
}

/** Helpers for starting service-side journey planning without loading app state in the caller. */
@UnstableApi
object JourneyPlaybackCommands {
    /** Sends [JourneyPlaybackProtocol.ACTION_PLAY_JOURNEY] through an existing media controller. */
    fun playJourney(
        controller: MediaController,
        plan: JourneyPlan? = null,
    ): ListenableFuture<SessionResult> = controller.sendCustomCommand(
        SessionCommand(JourneyPlaybackProtocol.ACTION_PLAY_JOURNEY, Bundle.EMPTY),
        Bundle().apply { plan?.let { putString(JourneyPlaybackProtocol.KEY_PLAN, it.name) } },
    )

    /** Builds a controller for [PlaybackService], sends the journey command, then releases it. */
    fun playJourney(
        context: Context,
        plan: JourneyPlan? = null,
    ): ListenableFuture<SessionResult> {
        val appContext = context.applicationContext
        val controllerFuture = MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
        ).buildAsync()
        val result = SettableFuture.create<SessionResult>()
        val mainExecutor = ContextCompat.getMainExecutor(appContext)
        Futures.addCallback(
            controllerFuture,
            object : FutureCallback<MediaController> {
                override fun onSuccess(controller: MediaController?) {
                    if (controller == null) {
                        result.setException(IllegalStateException("MediaController creation returned null"))
                        return
                    }
                    val commandFuture = playJourney(controller, plan)
                    Futures.addCallback(
                        commandFuture,
                        object : FutureCallback<SessionResult> {
                            override fun onSuccess(commandResult: SessionResult?) {
                                MediaController.releaseFuture(controllerFuture)
                                if (commandResult == null) {
                                    result.setException(IllegalStateException("Journey command returned null"))
                                } else {
                                    result.set(commandResult)
                                }
                            }

                            override fun onFailure(error: Throwable) {
                                MediaController.releaseFuture(controllerFuture)
                                result.setException(error)
                            }
                        },
                        mainExecutor,
                    )
                }

                override fun onFailure(error: Throwable) {
                    result.setException(error)
                }
            },
            mainExecutor,
        )
        return result
    }

    /**
     * Returns a service-targeted PendingIntent suitable for a widget click or a tile click handler.
     * Widgets can pass it to RemoteViews.setOnClickPendingIntent; tile services can call send() from
     * their click callback. The service loads curriculum/progress and starts playback itself.
     */
    fun createPendingIntent(
        context: Context,
        plan: JourneyPlan? = null,
        requestCode: Int = DEFAULT_REQUEST_CODE + (plan?.ordinal ?: -1),
    ): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java)
            .setAction(JourneyPlaybackProtocol.ACTION_PLAY_JOURNEY)
            .putExtra(JourneyPlaybackProtocol.KEY_PLAN, plan?.name)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Sends a ten-minute extension through an existing controller, e.g. from a shake-settings UI. */
    fun extendSleepTimer(controller: MediaController): ListenableFuture<SessionResult> =
        controller.sendCustomCommand(
            SessionCommand(JourneyPlaybackProtocol.ACTION_EXTEND_SLEEP_TIMER, Bundle.EMPTY),
            Bundle.EMPTY,
        )

    private const val DEFAULT_REQUEST_CODE = 4_210
}
