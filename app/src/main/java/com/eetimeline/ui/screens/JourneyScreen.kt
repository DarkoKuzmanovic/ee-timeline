package com.eetimeline.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import com.eetimeline.core.Curriculum
import com.eetimeline.core.Episode
import com.eetimeline.core.JourneyPlan
import com.eetimeline.core.SleepRewindPoint
import com.eetimeline.core.orderedEpisodeIds
import com.eetimeline.playback.SleepSession
import com.eetimeline.ui.EpisodeProgress
import com.eetimeline.ui.JourneyPlanPreview
import com.eetimeline.ui.PlaybackUiState
import com.eetimeline.ui.components.MINI_PLAYER_HEIGHT
import com.eetimeline.ui.components.EpisodeRow
import com.eetimeline.ui.components.SleepSessionSheet
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.Mint
import com.eetimeline.ui.theme.unitColor
import com.eetimeline.ui.theme.SurfaceRaised
import kotlin.math.roundToInt

private fun JourneyPlan.title(): String = when (this) {
    JourneyPlan.JUST_KEEP_GOING -> "Just keep going"
    JourneyPlan.ONE_STORY -> "1 story"
    JourneyPlan.TWO_STORIES -> "2 stories"
    JourneyPlan.THIRTY_MINUTES -> "~30 min"
    JourneyPlan.FORTY_FIVE_MINUTES -> "~45 min"
}

private fun formatPlanDuration(durationMs: Long): String {
    val minutes = ((durationMs.coerceAtLeast(0L) + 30_000L) / 60_000L).toInt()
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}

private fun formatPosition(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
fun JourneyScreen(
    curriculum: Curriculum,
    progress: Map<String, EpisodeProgress>,
    playback: PlaybackUiState,
    upcoming: List<Episode>,
    planPreviews: List<JourneyPlanPreview>,
    selectedPlan: JourneyPlan,
    sleepSession: SleepSession?,
    suggestedRewindPoint: SleepRewindPoint?,
    onContinue: () -> Unit,
    onPlayPlan: (JourneyPlan) -> Unit,
    onRewind: (SleepRewindPoint) -> Unit,
    onKeepMyPlace: () -> Unit,
    onSettings: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onDownloadNext: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var showSleepSpotSheet by remember { mutableStateOf(false) }
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
        if (sleepSession != null && suggestedRewindPoint != null) {
            item {
                val title = curriculum.episodes[suggestedRewindPoint.episodeId]?.title ?: suggestedRewindPoint.episodeId
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("A gentle place to pick up", style = MaterialTheme.typography.labelMedium, color = Mint, fontWeight = FontWeight.Bold)
                        Text(
                            buildAnnotatedString {
                                append("You drifted off during ")
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)) {
                                    append(title)
                                }
                                append(" — pick up from ${formatPosition(suggestedRewindPoint.positionMs)}?")
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRewind(suggestedRewindPoint)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Rewind there", fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { showSleepSpotSheet = true }) {
                                Text("Choose the spot", color = Apricot)
                            }
                            TextButton(onClick = onKeepMyPlace) {
                                Text("Keep my place", color = MutedCream)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("A little further in time", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Good evening. Let’s take one more story with us.", style = MaterialTheme.typography.bodyLarge, color = MutedCream)
                }
                IconButton(onClick = onSettings) {
                    androidx.compose.material3.Icon(Icons.Filled.Settings, contentDescription = "Listening settings", tint = MutedCream)
                }
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
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (sleepSession != null) onKeepMyPlace()
                            onContinue()
                        },
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
                    if (planPreviews.isNotEmpty()) {
                        Text("Tonight’s plan", style = MaterialTheme.typography.labelMedium, color = MutedCream, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            planPreviews.forEach { preview ->
                                val storyLabel = if (preview.storyCount == 1) "1 story" else "${preview.storyCount} stories"
                                FilterChip(
                                    selected = selectedPlan == preview.plan,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlayPlan(preview.plan)
                                    },
                                    label = {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(preview.plan.title(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (preview.plan == JourneyPlan.JUST_KEEP_GOING) {
                                                    "Until the timer or you stop"
                                                } else {
                                                    "$storyLabel · ~${formatPlanDuration(preview.estimatedDurationMs)} · ends ~${preview.finishTimeLabel}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    },
                                )
                            }
                        }
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
    if (showSleepSpotSheet && sleepSession != null && suggestedRewindPoint != null) {
        SleepSessionSheet(
            session = sleepSession,
            episodes = curriculum.episodes,
            suggestedPoint = suggestedRewindPoint,
            onDismiss = { showSleepSpotSheet = false },
            onRewind = { point ->
                showSleepSpotSheet = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onRewind(point)
            },
        )
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
