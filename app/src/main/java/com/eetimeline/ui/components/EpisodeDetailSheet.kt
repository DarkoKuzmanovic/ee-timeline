package com.eetimeline.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eetimeline.core.Episode
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.SurfaceRaised

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailSheet(
    episode: Episode,
    badge: String,
    played: Boolean,
    downloaded: Boolean,
    downloadInProgress: Boolean = false,
    downloadProgressPercent: Int? = null,
    downloadFailed: Boolean = false,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onTogglePlayed: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EpisodeArtwork(episode.imageUrl, Modifier.size(92.dp), cornerRadius = 20.dp)
                Column(Modifier.weight(1f)) {
                    Text(badge, style = MaterialTheme.typography.labelMedium, color = Apricot, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(episode.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(listOfNotNull(episode.yearLabel, if (episode.durationSec > 0) formatDuration(episode.durationSec) else null).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MutedCream)
                }
            }
            if (episode.description.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised), shape = RoundedCornerShape(20.dp)) {
                    Text(episode.description, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MutedCream)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPlay, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Play", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onQueue, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Text("Add to queue")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        when {
                            downloaded -> "Delete download"
                            downloadInProgress -> downloadProgressPercent?.let { "Cancel · $it%" } ?: "Cancel download"
                            downloadFailed -> "Retry download"
                            else -> "Download"
                        },
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                OutlinedButton(onClick = onTogglePlayed, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Text(if (played) "Mark unplayed" else "Mark played", modifier = Modifier.padding(start = 6.dp))
                }
            }
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(episode.pageUrl)))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Text("Open on the Everything Everywhere website", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
