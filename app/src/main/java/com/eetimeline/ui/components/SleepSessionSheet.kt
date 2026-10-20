package com.eetimeline.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eetimeline.core.Episode
import com.eetimeline.core.SleepRewindPoint
import com.eetimeline.playback.SleepSession
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.SurfaceRaised
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SleepSessionSheet(
    session: SleepSession,
    episodes: Map<String, Episode>,
    suggestedPoint: SleepRewindPoint,
    onDismiss: () -> Unit,
    onRewind: (SleepRewindPoint) -> Unit,
) {
    if (session.segments.isEmpty()) return
    var selectedPoint by remember(session, suggestedPoint) {
        mutableStateOf(suggestedPoint.copy(segmentIndex = suggestedPoint.segmentIndex.coerceIn(session.segments.indices)))
    }
    val selectedIndex = selectedPoint.segmentIndex
    val selectedSegment = session.segments[selectedIndex]
    val sliderFraction = fractionWithin(
        selectedSegment.startPositionMs,
        selectedSegment.endPositionMs,
        selectedPoint.positionMs,
    )
    val selectedPosition = selectedPoint.positionMs

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Choose the spot", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Pick a moment from the stories you heard before the fade.", style = MaterialTheme.typography.bodyMedium, color = MutedCream)
            }

            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                session.segments.forEachIndexed { index, segment ->
                    val active = selectedIndex == index
                    val time = formatSessionClock(segment.wallClockStartMs)
                    val title = episodes[segment.episodeId]?.title ?: segment.episodeId
                    val detail = if (index == session.segments.lastIndex) {
                        "Faded out ${formatPosition(segment.endPositionMs)}"
                    } else {
                        "${formatPosition(segment.startPositionMs)}–${formatPosition(segment.endPositionMs)}"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedPoint = SleepRewindPoint(segment.episodeId, segment.startPositionMs, index)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) MaterialTheme.colorScheme.surfaceVariant else SurfaceRaised,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Text(time, style = MaterialTheme.typography.labelMedium, color = Apricot, fontWeight = FontWeight.Bold)
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(detail, style = MaterialTheme.typography.labelSmall, color = MutedCream)
                            }
                            if (active) Text("●", color = Apricot)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Slider(
                    value = sliderFraction,
                    onValueChange = { fraction ->
                        selectedPoint = selectedPoint.copy(
                            positionMs = interpolate(selectedSegment.startPositionMs, selectedSegment.endPositionMs, fraction),
                        )
                    },
                    valueRange = 0f..1f,
                    enabled = selectedSegment.startPositionMs != selectedSegment.endPositionMs,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPosition(selectedSegment.startPositionMs), style = MaterialTheme.typography.labelSmall, color = MutedCream)
                    Text("Rewind to ${formatPosition(selectedPosition)}", style = MaterialTheme.typography.labelMedium, color = Apricot, fontWeight = FontWeight.SemiBold)
                    Text(formatPosition(selectedSegment.endPositionMs), style = MaterialTheme.typography.labelSmall, color = MutedCream)
                }
            }

            Button(
                onClick = {
                    onRewind(selectedPoint)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                Text("Rewind to this spot", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}


private fun fractionWithin(startPositionMs: Long, endPositionMs: Long, positionMs: Long): Float {
    val span = endPositionMs - startPositionMs
    if (span == 0L) return 0f
    return ((positionMs - startPositionMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
}

private fun interpolate(startPositionMs: Long, endPositionMs: Long, fraction: Float): Long =
    (startPositionMs + ((endPositionMs - startPositionMs).toDouble() * fraction).roundToLong())
        .coerceAtLeast(0L)

private fun formatPosition(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatSessionClock(timeMs: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timeMs))
