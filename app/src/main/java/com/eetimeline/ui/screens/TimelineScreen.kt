package com.eetimeline.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eetimeline.core.Curriculum
import com.eetimeline.core.Episode
import com.eetimeline.core.timelineX
import com.eetimeline.core.orderedEpisodeIds
import com.eetimeline.ui.EpisodeProgress
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.unitColor
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private data class TimelineDot(val episode: Episode, val x: Float, val y: Float)

@Composable
fun TimelineScreen(
    curriculum: Curriculum,
    progress: Map<String, EpisodeProgress>,
    currentEpisodeId: String?,
    onEpisodeClick: (Episode) -> Unit,
) {
    val allEpisodes = remember(curriculum) {
        curriculum.orderedEpisodeIds().mapNotNull(curriculum.episodes::get)
    }
    val episodeUnits = remember(curriculum) {
        buildMap {
            curriculum.units.forEach { unit ->
                unit.topics.forEach { topic ->
                    topic.groups.forEach { group -> group.episodeIds.forEach { put(it, unit.number) } }
                }
            }
        }
    }
    val minYear = min(1200, allEpisodes.minOfOrNull { it.approxYear } ?: 1200)
    val maxYear = max(Calendar.getInstance().get(Calendar.YEAR), allEpisodes.maxOfOrNull { it.approxYear } ?: 2026)
    val timelineWidth = 1500.dp
    val scroll = rememberScrollState()
    var canvasPixelSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val dots = remember(allEpisodes, minYear, maxYear, canvasPixelSize) {
        if (canvasPixelSize.width == 0 || canvasPixelSize.height == 0) {
            emptyList()
        } else {
            timelineDots(allEpisodes, minYear, maxYear, canvasPixelSize.width.toFloat(), canvasPixelSize.height.toFloat())
        }
    }
    val tickLabels = remember(minYear, maxYear) {
        listOf(minYear, 1200, 1450, 1750, maxYear).distinct().map { year ->
            year to if (year < 0) "${-year} BCE" else year.toString()
        }
    }
    val unitLabels = remember(curriculum) { curriculum.units.associate { it.number to it.number.toString() } }
    val textPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    val pulse by rememberInfiniteTransition(label = "timeline-current-episode").animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "timeline-current-pulse",
    )

    Column(Modifier.fillMaxSize().padding(top = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("The long view", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Stories placed across the centuries. Earlier years have a little less room.", style = MaterialTheme.typography.bodyMedium, color = MutedCream)
        }
        androidx.compose.foundation.layout.Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Canvas(
                modifier = Modifier.width(timelineWidth).fillMaxSize()
                    .onSizeChanged { canvasPixelSize = it }
                    .pointerInput(dots) {
                        detectTapGestures { tap ->
                            dots.minByOrNull { dot -> distanceSquared(tap, Offset(dot.x, dot.y)) }
                                ?.takeIf { distanceSquared(tap, Offset(it.x, it.y)) < 42f * 42f }
                                ?.let { onEpisodeClick(it.episode) }
                        }
                    },
            ) {
                val left = 38.dp.toPx()
                val right = size.width - 36.dp.toPx()
                val width = right - left
                val baseline = size.height * 0.56f
                val pxHeight = size.height
                textPaint.textSize = 13.dp.toPx()

                if (minYear < 1200) {
                    textPaint.color = android.graphics.Color.rgb(185, 178, 168)
                    drawContext.canvas.nativeCanvas.drawText("BEFORE 1200", left, 18.dp.toPx(), textPaint)
                }
                // Era spans follow the curriculum's unit ranges, overlaid on one continuous chronology.
                curriculum.units.forEach { unit ->
                    val x1 = left + timelineX(unit.startYear, minYear, maxYear) * width
                    val x2 = left + timelineX(unit.endYear, minYear, maxYear) * width
                    val eraTop = 28.dp.toPx()
                    drawRoundRect(
                        color = unitColor(unit.number).copy(alpha = 0.15f),
                        topLeft = Offset(min(x1, x2), eraTop),
                        size = androidx.compose.ui.geometry.Size(max(9.dp.toPx(), abs(x2 - x1)), pxHeight * 0.74f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                    )
                    drawContext.canvas.nativeCanvas.drawText(unitLabels[unit.number] ?: unit.number.toString(), min(x1, x2) + 8.dp.toPx(), eraTop + 22.dp.toPx(), textPaint.apply { color = unitColor(unit.number).toArgb() })
                }

                drawLine(
                    color = Color(0xFF656A75),
                    start = Offset(left, baseline),
                    end = Offset(right, baseline),
                    strokeWidth = 2.dp.toPx(),
                )
                tickLabels.forEach { (year, label) ->
                    val x = left + timelineX(year, minYear, maxYear) * width
                    drawLine(Color(0xFF777C86), Offset(x, baseline - 7.dp.toPx()), Offset(x, baseline + 7.dp.toPx()), 1.dp.toPx())
                    textPaint.color = android.graphics.Color.rgb(185, 178, 168)
                    drawContext.canvas.nativeCanvas.drawText(label, x + 4.dp.toPx(), baseline + 28.dp.toPx(), textPaint)
                }

                dots.forEach { dot ->
                    val eraColor = unitColor(episodeUnits[dot.episode.id] ?: 1)
                    val played = progress[dot.episode.id]?.played == true
                    val isCurrent = currentEpisodeId == dot.episode.id
                    if (isCurrent) {
                        drawCircle(Apricot.copy(alpha = 0.2f), radius = 14.dp.toPx() * pulse, center = Offset(dot.x, dot.y))
                    }
                    val radius = if (isCurrent) 6.dp.toPx() else 4.3.dp.toPx()
                    if (played) {
                        drawCircle(eraColor, radius = radius, center = Offset(dot.x, dot.y))
                    } else {
                        drawCircle(eraColor.copy(alpha = 0.8f), radius = radius, center = Offset(dot.x, dot.y), style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
        }
        Text("Swipe sideways to wander through time · tap a story to learn more", modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = MutedCream)
    }
}

private fun timelineDots(episodes: List<Episode>, minYear: Int, maxYear: Int, width: Float, height: Float): List<TimelineDot> {
    val left = 38.dp.value * (width / 1500f)
    val right = width - 36.dp.value * (width / 1500f)
    val baseline = height * 0.56f
    val plotWidth = right - left
    return episodes.map { episode ->
        val x = left + timelineX(episode.approxYear, minYear, maxYear) * plotWidth
        val row = (episode.id.hashCode().and(Int.MAX_VALUE) % 5)
        val y = baseline + (row - 2) * 17.dp.value * (height / 520f)
        TimelineDot(episode, x, y)
    }
}

private fun distanceSquared(first: Offset, second: Offset): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return dx * dx + dy * dy
}

