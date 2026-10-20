package com.eetimeline.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eetimeline.core.Curriculum
import com.eetimeline.core.CurriculumUnit
import com.eetimeline.ui.EpisodeProgress
import com.eetimeline.core.Episode
import com.eetimeline.ui.components.EraArtCollage
import com.eetimeline.ui.components.MINI_PLAYER_HEIGHT
import com.eetimeline.ui.components.unitArtworkUrls
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.SurfaceRaised
import com.eetimeline.ui.theme.unitColor

@Composable
fun UnitsScreen(
    curriculum: Curriculum,
    progress: Map<String, EpisodeProgress>,
    onUnitClick: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp + MINI_PLAYER_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("The eras", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Nine chapters of the world, in AP order.", color = MutedCream, style = MaterialTheme.typography.bodyLarge)
            }
        }
        items(curriculum.units, key = { it.number }) { unit ->
            UnitCard(
                unit = unit,
                progress = progress,
                episodes = curriculum.episodes,
                onClick = { onUnitClick(unit.number) },
            )
        }
    }
}

@Composable
private fun UnitCard(
    unit: CurriculumUnit,
    progress: Map<String, EpisodeProgress>,
    episodes: Map<String, Episode>,
    onClick: () -> Unit,
) {
    val ids = unit.topics.flatMap { topic -> topic.groups.flatMap { it.episodeIds } }
    val played = ids.count { progress[it]?.played == true }
    val fraction = if (ids.isEmpty()) 0f else played.toFloat() / ids.size
    val animated by animateFloatAsState(fraction, label = "unit-progress-${unit.number}")
    val completed = ids.isNotEmpty() && played == ids.size
    val color = unitColor(unit.number)

    val artUrls = remember(unit, episodes) { unitArtworkUrls(unit, episodes, count = 6) }
    val cardColor = SurfaceRaised

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // Artwork layer: dimmed mosaic on the trailing edge, faded into the card toward the text.
            Box(Modifier.matchParentSize()) {
                EraArtCollage(
                    imageUrls = artUrls,
                    eraColor = color,
                    eraIcon = eraIcon(unit.number),
                    columns = 3,
                    rows = 2,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(132.dp).alpha(0.34f),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to color.copy(alpha = 0.14f),
                                0.6f to color.copy(alpha = 0.0f),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(132.dp)
                        .background(
                            Brush.horizontalGradient(
                                0.0f to cardColor,
                                0.55f to cardColor.copy(alpha = 0.35f),
                                1.0f to cardColor.copy(alpha = 0.0f),
                            ),
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(62.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawArc(
                            color = color.copy(alpha = 0.18f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = color,
                            startAngle = -90f,
                            sweepAngle = 360f * animated,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    Icon(
                        imageVector = eraIcon(unit.number),
                        contentDescription = unit.title,
                        tint = color,
                        modifier = Modifier.size(24.dp),
                    )
                    if (completed) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Completed",
                            tint = color,
                            modifier = Modifier.align(Alignment.TopEnd).size(19.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                    Text("UNIT ${unit.number}  ·  ${unit.period}", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(unit.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("$played of ${ids.size} episodes", style = MaterialTheme.typography.bodySmall, color = MutedCream)
                }
                androidx.compose.material3.Icon(Icons.Filled.ArrowForward, contentDescription = "Open ${unit.title}", tint = MutedCream)
            }
        }
    }
}

internal fun eraIcon(unitNumber: Int): ImageVector = when (unitNumber) {
    1 -> Icons.Filled.AccountBalance
    2 -> Icons.Filled.Route
    3 -> Icons.Filled.Castle
    4 -> Icons.Filled.Sailing
    5 -> Icons.Filled.Flag
    6 -> Icons.Filled.Factory
    7 -> Icons.Filled.Shield
    8 -> Icons.Filled.RocketLaunch
    9 -> Icons.Filled.Public
    else -> Icons.Filled.MenuBook
}
