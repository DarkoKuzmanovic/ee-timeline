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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eetimeline.core.Curriculum
import com.eetimeline.core.CurriculumUnit
import com.eetimeline.ui.EpisodeProgress
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
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
                onClick = { onUnitClick(unit.number) },
            )
        }
    }
}

@Composable
private fun UnitCard(
    unit: CurriculumUnit,
    progress: Map<String, EpisodeProgress>,
    onClick: () -> Unit,
) {
    val ids = unit.topics.flatMap { topic -> topic.groups.flatMap { it.episodeIds } }
    val played = ids.count { progress[it]?.played == true }
    val fraction = if (ids.isEmpty()) 0f else played.toFloat() / ids.size
    val animated by animateFloatAsState(fraction, label = "unit-${unit.number}-progress")
    val color = unitColor(unit.number)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
    ) {
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
                Text("${unit.number}", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
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
