package com.eetimeline.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.eetimeline.ui.components.EraArtCollage
import com.eetimeline.ui.components.unitArtworkUrls
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eetimeline.core.CurriculumUnit
import com.eetimeline.core.Episode
import com.eetimeline.ui.EpisodeProgress
import com.eetimeline.ui.PlaybackUiState
import com.eetimeline.ui.components.MINI_PLAYER_HEIGHT
import com.eetimeline.ui.components.EpisodeRow
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.SurfaceRaised
import com.eetimeline.ui.theme.unitColor

private val HERO_HEIGHT = 280.dp
private val TOP_BAR_HEIGHT = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitDetailScreen(
    unit: CurriculumUnit,
    episodeLookup: Map<String, Episode>,
    progress: Map<String, EpisodeProgress>,
    playback: PlaybackUiState,
    onBack: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
) {
    val listState = rememberLazyListState()
    val ids = remember(unit) { unit.topics.flatMap { topic -> topic.groups.flatMap { it.episodeIds } } }
    val played = ids.count { progress[it]?.played == true }
    val artUrls = remember(unit, episodeLookup) { unitArtworkUrls(unit, episodeLookup, count = 12) }
    val collapseRangePx = with(LocalDensity.current) { (HERO_HEIGHT - TOP_BAR_HEIGHT).toPx() }
    // Read only in draw/graphics phases so scrolling never recomposes the screen.
    val collapse: () -> Float = {
        if (listState.firstVisibleItemIndex > 0) 1f
        else (listState.firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + MINI_PLAYER_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                UnitHero(
                    unit = unit,
                    artUrls = artUrls,
                    played = played,
                    total = ids.size,
                    collapse = collapse,
                )
            }
            item(key = "summary") {
                Text(
                    unit.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MutedCream,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            items(unit.topics, key = { it.id }) { topic ->
                Card(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(topic.id, style = MaterialTheme.typography.labelMedium, color = unitColor(unit.number), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(topic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        topic.detail?.takeIf(String::isNotBlank)?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MutedCream)
                        }
                        topic.groups.forEachIndexed { groupIndex, group ->
                            if (group.episodeIds.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    group.title?.takeIf(String::isNotBlank) ?: "Episodes",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = unitColor(unit.number),
                                    fontWeight = FontWeight.Bold,
                                )
                                group.episodeIds.forEach { id ->
                                    episodeLookup[id]?.let { episode ->
                                        EpisodeRow(
                                            episode = episode,
                                            isPlayed = progress[id]?.played == true,
                                            isCurrent = playback.currentEpisodeId == id,
                                            onClick = { onEpisodeClick(episode) },
                                            positionMs = progress[id]?.positionMs ?: 0L,
                                            downloaded = progress[id]?.downloaded == true,
                                            downloadInProgress = progress[id]?.downloadInProgress == true,
                                            downloadProgressPercent = progress[id]?.downloadProgressPercent,
                                            downloadFailed = progress[id]?.downloadFailed == true,
                                        )
                                    }
                                }
                            }
                        }
                        if (topic.groups.none { it.episodeIds.isNotEmpty() }) {
                            Spacer(Modifier.height(10.dp))
                            Text("No episodes in this topic yet.", style = MaterialTheme.typography.bodySmall, color = MutedCream)
                        }
                    }
                }
            }
        }

        val barColor = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .drawBehind { drawRect(barColor.copy(alpha = collapse())) },
        ) {
            TopAppBar(
                modifier = Modifier.fillMaxWidth(),
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                title = { Text("Unit ${unit.number}", color = unitColor(unit.number)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        }
    }
}

/**
 * Full-bleed header: a dimmed episode-art mosaic under a soft era-colour wash, fading into the page
 * background so the unit title, period and progress sit on near-solid night colour.
 */
@Composable
private fun UnitHero(
    unit: CurriculumUnit,
    artUrls: List<String>,
    played: Int,
    total: Int,
    collapse: () -> Float,
) {
    val color = unitColor(unit.number)
    val background = MaterialTheme.colorScheme.background
    val fraction = if (total == 0) 0f else played.toFloat() / total
    val animated by animateFloatAsState(fraction, label = "unit-hero-progress-${unit.number}")
    val completed = total > 0 && played == total

    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        EraArtCollage(
            imageUrls = artUrls,
            eraColor = color,
            eraIcon = eraIcon(unit.number),
            columns = 4,
            rows = 3,
            modifier = Modifier.fillMaxSize().alpha(0.32f),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(0f to color.copy(alpha = 0.26f), 1f to color.copy(alpha = 0f))),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to background.copy(alpha = 0.55f),
                        0.4f to background.copy(alpha = 0.55f),
                        0.85f to background.copy(alpha = 0.96f),
                        1.0f to background,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                .graphicsLayer { alpha = (1f - collapse() * 1.4f).coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(color = color.copy(alpha = 0.22f), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                    drawArc(color = color, startAngle = -90f, sweepAngle = 360f * animated, useCenter = false, style = stroke)
                }
                Icon(
                    imageVector = if (completed) Icons.Filled.CheckCircle else eraIcon(unit.number),
                    contentDescription = if (completed) "Completed" else null,
                    tint = color,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(unit.period, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(unit.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("$played of $total episodes", style = MaterialTheme.typography.bodyMedium, color = MutedCream)
            }
        }
    }
}
