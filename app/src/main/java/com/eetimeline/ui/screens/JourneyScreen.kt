package com.eetimeline.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eetimeline.core.Curriculum
import com.eetimeline.core.Episode
import com.eetimeline.core.orderedEpisodeIds
import com.eetimeline.ui.EpisodeProgress
import com.eetimeline.ui.PlaybackUiState
import com.eetimeline.ui.components.MINI_PLAYER_HEIGHT
import com.eetimeline.ui.components.EpisodeRow
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.Mint
import com.eetimeline.ui.theme.unitColor
import com.eetimeline.ui.theme.SurfaceRaised
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun JourneyScreen(
    curriculum: Curriculum,
    progress: Map<String, EpisodeProgress>,
    playback: PlaybackUiState,
    upcoming: List<Episode>,
    onContinue: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onDownloadNext: (Int) -> Unit,
) {
    val ordered = curriculum.orderedEpisodeIds()
    val playedCount = ordered.count { progress[it]?.played == true }
    val ratio = if (ordered.isEmpty()) 0f else playedCount.toFloat() / ordered.size
    val animatedRatio by animateFloatAsState(ratio, label = "journey-progress")
    val journeyCounter = when {
        ordered.isEmpty() -> "No stories yet"
        playedCount == ordered.size -> "${ordered.size} stories finished"
        else -> "Story ${playedCount + 1} of ${ordered.size} · $playedCount finished"
    }
    val remainingHours = ordered.sumOf { id ->
        if (progress[id]?.played == true) 0L else (curriculum.episodes[id]?.durationSec ?: 0).toLong()
    } / 3600f
    val unit = curriculum.units.firstOrNull { unit ->
        unit.topics.any { topic -> topic.groups.any { group -> group.episodeIds.any { it !in progress || progress[it]?.played != true } } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp + MINI_PLAYER_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                Text("A little further in time", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Good evening. Let’s take one more story with us.", style = MaterialTheme.typography.bodyLarge, color = MutedCream)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("YOUR JOURNEY", style = MaterialTheme.typography.labelMedium, color = Mint, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text(journeyCounter, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text("${(ratio * 100).roundToInt()}%", style = MaterialTheme.typography.headlineSmall, color = Apricot, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { animatedRatio },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = Apricot,
                        trackColor = MaterialTheme.colorScheme.surface,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("ERA PASSPORT", style = MaterialTheme.typography.labelSmall, color = MutedCream, fontWeight = FontWeight.Bold)
                            val completedEras = curriculum.units.count { era ->
                                var episodeCount = 0
                                var playedEpisodes = 0
                                era.topics.forEach { topic ->
                                    topic.groups.forEach { group ->
                                        group.episodeIds.forEach { id ->
                                            episodeCount++
                                            if (progress[id]?.played == true) playedEpisodes++
                                        }
                                    }
                                }
                                episodeCount > 0 && playedEpisodes == episodeCount
                            }
                            Text(
                                "$completedEras/${curriculum.units.size} stamped",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedCream,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            curriculum.units.forEach { era ->
                                var episodeCount = 0
                                var playedEpisodes = 0
                                era.topics.forEach { topic ->
                                    topic.groups.forEach { group ->
                                        group.episodeIds.forEach { id ->
                                            episodeCount++
                                            if (progress[id]?.played == true) playedEpisodes++
                                        }
                                    }
                                }
                                EraPassportStamp(era.number, playedEpisodes, episodeCount)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("About ${remainingHours.roundToInt()} hours left", style = MaterialTheme.typography.bodyMedium, color = MutedCream)
                        Text("·", color = MutedCream)
                        Text("${curriculum.units.size} eras", style = MaterialTheme.typography.bodyMedium, color = MutedCream)
                    }
                    unit?.let {
                        Text("Next stop: ${it.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Button(
                        onClick = onContinue,
                        enabled = ordered.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Apricot, contentColor = MaterialTheme.colorScheme.onPrimary),
                    ) {
                        androidx.compose.material3.Icon(
                            if (playback.journeyMode && playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Text(
                            if (playback.journeyMode && playback.isPlaying) "Pause your journey" else "Continue the journey",
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("In curriculum order, one story at a time.", style = MaterialTheme.typography.bodySmall, color = MutedCream)
                }
                OutlinedButton(onClick = { onDownloadNext(3) }, shape = RoundedCornerShape(15.dp)) {
                    androidx.compose.material3.Icon(Icons.Filled.Download, contentDescription = null, tint = Apricot)
                    Text("Next 3", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (upcoming.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceRaised), shape = RoundedCornerShape(22.dp)) {
                    Text(
                        if (ordered.isEmpty()) "Your timeline is being put together. Check back soon." else "You’ve travelled the whole timeline. What a lovely collection of stories.",
                        modifier = Modifier.padding(18.dp),
                        color = MutedCream,
                    )
                }
            }
        } else {
            items(upcoming.take(8), key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    isPlayed = progress[episode.id]?.played == true,
                    isCurrent = playback.currentEpisodeId == episode.id,
                    onClick = { onEpisodeClick(episode) },
                    positionMs = progress[episode.id]?.positionMs ?: 0L,
                    downloaded = progress[episode.id]?.downloaded == true,
                    downloadInProgress = progress[episode.id]?.downloadInProgress == true,
                    downloadProgressPercent = progress[episode.id]?.downloadProgressPercent,
                    downloadFailed = progress[episode.id]?.downloadFailed == true,
                )
            }
        }
    }
}
@Composable
private fun EraPassportStamp(unitNumber: Int, played: Int, total: Int) {
    val fraction = if (total == 0) 0f else played.toFloat() / total
    val color = unitColor(unitNumber)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(if (fraction >= 1f) color.copy(alpha = 0.2f) else trackColor.copy(alpha = 0.7f))
            drawArc(
                color = if (fraction > 0f) color else MutedCream.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Text(
            unitNumber.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = if (fraction > 0f) color else MutedCream,
            fontWeight = FontWeight.Bold,
        )
    }
}
