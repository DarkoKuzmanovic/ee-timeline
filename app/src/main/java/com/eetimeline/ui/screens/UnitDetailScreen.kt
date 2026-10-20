package com.eetimeline.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.eetimeline.ui.components.EpisodeRow
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.SurfaceRaised
import com.eetimeline.ui.theme.unitColor

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
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = { Text("Unit ${unit.number}", color = unitColor(unit.number)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(unit.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(unit.period, style = MaterialTheme.typography.titleMedium, color = unitColor(unit.number))
                    Spacer(Modifier.height(12.dp))
                    Text(unit.summary, style = MaterialTheme.typography.bodyLarge, color = MutedCream)
                }
            }
        items(unit.topics, key = { it.id }) { topic ->
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = SurfaceRaised)) {
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
    }
}
