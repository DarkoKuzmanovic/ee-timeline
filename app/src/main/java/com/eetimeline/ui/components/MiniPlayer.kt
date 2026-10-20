package com.eetimeline.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eetimeline.ui.PlaybackUiState
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
internal val MINI_PLAYER_HEIGHT = 67.dp

@Composable
fun MiniPlayer(
    title: String,
    imageUrl: String?,
    playback: PlaybackUiState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val fraction = if (playback.durationMs <= 0L) 0f else (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    val tint = if (playback.bedtimeMode) Color(0xFFB9AD9A).copy(alpha = 0.72f) else Apricot
    val surfaceColor = if (playback.bedtimeMode) Color.Black else MaterialTheme.colorScheme.surfaceVariant
    Surface(color = surfaceColor, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(MINI_PLAYER_HEIGHT).clickable(onClick = onOpen)) {
        androidx.compose.foundation.layout.Column {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = tint.copy(alpha = if (playback.bedtimeMode) 0.55f else 1f),
                trackColor = surfaceColor,
            )
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                if (playback.bedtimeMode) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
                } else {
                    EpisodeArtwork(imageUrl, Modifier.size(42.dp), cornerRadius = 12.dp)
                }
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (playback.bedtimeMode) tint else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (playback.journeyMode) "On your journey" else "Everything Everywhere Daily",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (playback.bedtimeMode) tint.copy(alpha = 0.66f) else MutedCream,
                    )
                }
                MorphingPlayPauseButton(
                    isPlaying = playback.isPlaying,
                    onToggle = onPlayPause,
                    modifier = Modifier.size(44.dp),
                    containerColor = if (playback.bedtimeMode) tint.copy(alpha = 0.16f) else Apricot,
                    contentColor = if (playback.bedtimeMode) tint else MaterialTheme.colorScheme.onPrimary,
                    iconSize = 22.dp,
                )
            }
        }
    }
}
