package com.eetimeline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eetimeline.core.Episode
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.Mint

@Composable
fun EpisodeRow(
    episode: Episode,
    isPlayed: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadInProgress: Boolean = false,
    downloadProgressPercent: Int? = null,
    downloadFailed: Boolean = false,
    positionMs: Long = 0L,
    downloaded: Boolean = false,
) {
    var observedPlayed by remember(episode.id) { mutableStateOf(isPlayed) }
    val stampScale = remember(episode.id) { Animatable(1f) }
    LaunchedEffect(isPlayed) {
        if (isPlayed && !observedPlayed) {
            stampScale.snapTo(0.72f)
            stampScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            )
        }
        observedPlayed = isPlayed
    }
    val partialProgress = if (positionMs <= 0L || episode.durationSec <= 0) {
        0f
    } else {
        (positionMs.toFloat() / (episode.durationSec.toLong() * 1000f)).coerceIn(0f, 0.99f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .height(76.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = if (isCurrent) Apricot.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            BoxCenterIcon(
                icon = if (isCurrent) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
                tint = if (isCurrent) Apricot else MutedCream,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val downloadLabel = when {
                downloadInProgress -> downloadProgressPercent?.let { "Downloading · $it%" } ?: "Downloading…"
                downloadFailed -> "Download failed"
                else -> null
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = downloadLabel
                    ?: listOfNotNull(
                        episode.yearLabel.takeIf(String::isNotBlank),
                        episode.durationSec.takeIf { it > 0 }?.let(::formatDuration),
                    ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = if (downloadInProgress) Apricot else MutedCream,
            )
            if (!isPlayed && partialProgress > 0f) {
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { partialProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Apricot,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
        if (downloaded) {
            Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = "Downloaded",
                tint = Mint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (isPlayed) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Played",
                tint = Mint,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    scaleX = stampScale.value
                    scaleY = stampScale.value
                },
            )
        } else if (isCurrent) {
            Surface(color = Apricot, shape = MaterialTheme.shapes.small) {
                Text("NOW", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF322016), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BoxCenterIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
) {
    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

fun formatDuration(durationSec: Int): String {
    val hours = durationSec / 3600
    val minutes = durationSec % 3600 / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
}
