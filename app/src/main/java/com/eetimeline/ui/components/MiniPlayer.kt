package com.eetimeline.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eetimeline.ui.PlaybackUiState
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream

@Composable
fun MiniPlayer(
    title: String,
    imageUrl: String?,
    playback: PlaybackUiState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val fraction = if (playback.durationMs <= 0L) 0f else (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        androidx.compose.foundation.layout.Column {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Apricot,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                EpisodeArtwork(imageUrl, Modifier.size(42.dp), cornerRadius = 12.dp)
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (playback.journeyMode) "On your journey" else "Everything Everywhere Daily", style = MaterialTheme.typography.labelSmall, color = MutedCream)
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                    Icon(if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playback.isPlaying) "Pause" else "Play", tint = Apricot)
                }
            }
        }
    }
}
