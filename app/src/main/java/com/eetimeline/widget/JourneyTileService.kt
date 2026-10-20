package com.eetimeline.widget

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.common.util.UnstableApi
import com.eetimeline.playback.JourneyPlaybackCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class JourneyTileService : TileService() {
    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        tileScope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        JourneyPlaybackCommands.createPendingIntent(this, toggleActiveJourney = true).send()
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val data = JourneyWidgetData.load(this)
        tile.state = if (data.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tile.label = getString(com.eetimeline.R.string.continue_journey)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = data.title
        }
        tile.updateTile()
    }

    companion object {
        fun requestRefresh(context: Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, JourneyTileService::class.java),
            )
        }
    }
}
