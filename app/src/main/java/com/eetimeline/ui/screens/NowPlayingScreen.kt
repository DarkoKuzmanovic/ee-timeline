package com.eetimeline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eetimeline.core.Episode
import com.eetimeline.ui.PlaybackUiState
import com.eetimeline.ui.components.EpisodeArtwork
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.SurfaceRaised
import kotlin.math.max

@Composable
fun NowPlayingScreen(
    episode: Episode?,
    playback: PlaybackUiState,
    badge: String,
    played: Boolean,
    downloaded: Boolean,
    downloadInProgress: Boolean = false,
    downloadProgressPercent: Int? = null,
    downloadFailed: Boolean = false,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onTimer: (Long?) -> Unit,
    onEndOfEpisodeTimer: () -> Unit,
    onMarkPlayed: () -> Unit,
    onDownload: () -> Unit,
    onOpenWebsite: () -> Unit,
) {
    if (episode == null) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing playing just yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Choose a story from your journey and settle in.", color = MutedCream, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onBack) { Text("Back to the journey") }
        }
        return
    }
    val rememberedTimerLabel = when (playback.lastSleepTimerChoice) {
        15 -> "15 minutes"
        30 -> "30 minutes"
        45 -> "45 minutes"
        60 -> "60 minutes"
        -1 -> "end of episode"
        else -> null
    }

    var sleepMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = Apricot, fontWeight = FontWeight.Bold)
                Text(badge, style = MaterialTheme.typography.labelSmall, color = MutedCream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                TextButtonSpeed(playback.speed) { speedMenu = true }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.8f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}×") },
                            onClick = { onSpeed(speed); speedMenu = false },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        EpisodeArtwork(
            imageUrl = episode.imageUrl,
            modifier = Modifier.fillMaxWidth().weight(0.92f),
            cornerRadius = 28.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text(episode.yearLabel, style = MaterialTheme.typography.labelLarge, color = Apricot, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(episode.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text("Everything Everywhere Daily", style = MaterialTheme.typography.bodyMedium, color = MutedCream)
        Spacer(Modifier.height(15.dp))

        Slider(
            value = playback.positionMs.coerceAtMost(max(1L, playback.durationMs)).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..max(1L, playback.durationMs).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(playback.positionMs), style = MaterialTheme.typography.labelSmall, color = MutedCream)
            Text("−${formatClock((playback.durationMs - playback.positionMs).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall, color = MutedCream)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            IconButton(onClick = { onSeek((playback.positionMs - 15_000L).coerceAtLeast(0)) }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.FastRewind, contentDescription = "Back 15 seconds", tint = MutedCream)
            }
            Button(onClick = onPlayPause, shape = RoundedCornerShape(26.dp), modifier = Modifier.size(72.dp)) {
                Icon(if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playback.isPlaying) "Pause" else "Play", modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = { onSeek((playback.positionMs + 30_000L).coerceAtMost(playback.durationMs)) }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.FastForward, contentDescription = "Forward 30 seconds", tint = MutedCream)
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = Apricot, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Sleep timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        playback.sleepRemainingMs?.let { "Fades out in ${formatClock(it)}" }
                            ?: rememberedTimerLabel?.let { "Last time, you chose $it" }
                            ?: "Let the story fade when you’re ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedCream,
                    )
                }
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (playback.sleepRemainingMs != null) {
                            sleepMenu = true
                        } else {
                            when (playback.lastSleepTimerChoice) {
                                15 -> onTimer(15L * 60_000L)
                                30 -> onTimer(30L * 60_000L)
                                45 -> onTimer(45L * 60_000L)
                                60 -> onTimer(60L * 60_000L)
                                -1 -> onEndOfEpisodeTimer()
                                else -> sleepMenu = true
                            }
                        }
                    },
                ) {
                    Text(if (playback.sleepRemainingMs != null) "Change" else if (rememberedTimerLabel != null) "Again" else "Set", color = Apricot)
                }
                DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        DropdownMenuItem(text = { Text("$minutes minutes") }, onClick = { onTimer(minutes * 60_000L); sleepMenu = false })
                    }
                    DropdownMenuItem(text = { Text("End of episode") }, onClick = { onEndOfEpisodeTimer(); sleepMenu = false })
                    if (playback.sleepRemainingMs != null) {
                        DropdownMenuItem(text = { Text("Turn off") }, onClick = { onTimer(null); sleepMenu = false })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onMarkPlayed, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (played) "Mark unplayed" else "Mark played", modifier = Modifier.padding(start = 7.dp))
            }
            OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    when {
                        downloaded -> "Delete download"
                        downloadInProgress -> downloadProgressPercent?.let { "Cancel · $it%" } ?: "Cancel download"
                        downloadFailed -> "Retry download"
                        else -> "Download"
                    },
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
            IconButton(onClick = onOpenWebsite) { Icon(Icons.Filled.OpenInNew, contentDescription = "Open episode website", tint = MutedCream) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TextButtonSpeed(speed: Float, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text("${speed}×", color = Apricot, fontWeight = FontWeight.Bold)
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes % 60, seconds) else "%d:%02d".format(minutes, seconds)
}
