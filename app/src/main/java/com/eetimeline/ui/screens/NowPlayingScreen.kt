package com.eetimeline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eetimeline.core.Episode
import com.eetimeline.ui.PlaybackUiState
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.SurfaceRaised
import kotlin.math.max
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.eetimeline.ui.components.MorphingPlayPauseButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NowPlayingScreen(
    episode: Episode?,
    playback: PlaybackUiState,
    badge: String,
    unitAccent: Color,
    played: Boolean,
    downloaded: Boolean,
    downloadInProgress: Boolean = false,
    downloadProgressPercent: Int? = null,
    downloadFailed: Boolean = false,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onTimer: (Long?) -> Unit,
    onEndOfEpisodeTimer: () -> Unit,
    onExtendSleepTimer: () -> Unit,
    onToggleBedtime: () -> Unit,
    onVoiceBoostChange: (Boolean) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onMarkPlayed: () -> Unit,
    onDownload: () -> Unit,
    onOpenWebsite: () -> Unit,
) {
    if (episode == null) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nothing playing just yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Choose a story from your journey and settle in.", color = MutedCream, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onBack) { Text("Back to the journey") }
        }
        return
    }
    val haptics = LocalHapticFeedback.current
    val setSleepTimer: (Long?) -> Unit = { duration ->
        if (duration != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onTimer(duration)
    }
    val setEndOfEpisodeTimer = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onEndOfEpisodeTimer()
    }
    val extendSleepTimer = {
        onExtendSleepTimer()
    }
    val markPlayed = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onMarkPlayed()
    }
    val download = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onDownload()
    }
    if (playback.bedtimeMode) {
        BedtimeNowPlaying(
            episode = episode,
            playback = playback,
            onBack = onBack,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            onTimer = setSleepTimer,
            onEndOfEpisodeTimer = setEndOfEpisodeTimer,
            onExtendSleepTimer = extendSleepTimer,
            onToggleBedtime = onToggleBedtime,
        )
        return
    }
    var paletteEpisodeId by remember { mutableStateOf<String?>(null) }
    var paletteAccent by remember { mutableStateOf<Color?>(null) }
    val accentTarget = if (paletteEpisodeId == episode.id) paletteAccent ?: unitAccent else unitAccent
    val accent by animateColorAsState(
        targetValue = accentTarget,
        animationSpec = tween(durationMillis = 650),
        label = "episode-accent",
    )
    val accentContentColor = if (accent.luminance() > 0.18f) Color(0xFF201A16) else Color(0xFFFFF4E8)
    val rememberedTimerLabel = when (playback.lastSleepTimerChoice) {
        15 -> "15 minutes"
        30 -> "30 minutes"
        45 -> "45 minutes"
        60 -> "60 minutes"
        -1 -> "end of episode"
        else -> null
    }

    var sleepMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.23f), MaterialTheme.colorScheme.background)))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Text(badge, style = MaterialTheme.typography.labelSmall, color = MutedCream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                TextButtonSpeed(playback.speed) { speedMenu = true }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.8f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}×") },
                            onClick = { onSpeed(speed); speedMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = onToggleBedtime) {
                Icon(Icons.Filled.Bedtime, contentDescription = "Toggle bedtime mode", tint = MutedCream)
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { overflowMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More playback options", tint = MutedCream)
                }
                DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (playback.voiceBoost) "Voice boost · on" else "Voice boost") },
                        onClick = { onVoiceBoostChange(!playback.voiceBoost); overflowMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (playback.skipSilence) "Skip silence · on" else "Skip silence") },
                        onClick = { onSkipSilenceChange(!playback.skipSilence); overflowMenu = false },
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        NowPlayingPaletteArtwork(
            episodeId = episode.id,
            imageUrl = episode.imageUrl,
            modifier = Modifier.fillMaxWidth().weight(0.92f),
            fallbackColor = unitAccent,
            onPaletteColor = { color ->
                paletteEpisodeId = episode.id
                paletteAccent = color
            },
        )
        Spacer(Modifier.height(24.dp))
        Text(episode.yearLabel, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(episode.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text("Everything Everywhere Daily", style = MaterialTheme.typography.bodyMedium, color = MutedCream)
        if (playback.journeyMode && playback.activeJourneyPlan != null && playback.journeyStoryCount > 0) {
            Text(
                "Tonight: story ${playback.journeyStoryIndex.coerceAtLeast(1)} of ${playback.journeyStoryCount}",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(15.dp))

        Slider(
            value = playback.positionMs.coerceAtMost(max(1L, playback.durationMs)).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..max(1L, playback.durationMs).toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.34f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatClock(playback.positionMs), style = MaterialTheme.typography.labelSmall, color = MutedCream)
            Text("−${formatClock((playback.durationMs - playback.positionMs).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall, color = MutedCream)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            IconButton(onClick = { onSeek((playback.positionMs - 15_000L).coerceAtLeast(0)) }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.FastRewind, contentDescription = "Back 15 seconds", tint = MutedCream)
            }
            MorphingPlayPauseButton(
                isPlaying = playback.isPlaying,
                onToggle = onPlayPause,
                modifier = Modifier.size(72.dp),
                containerColor = accent,
                contentColor = accentContentColor,
                iconSize = 34.dp,
            )
            IconButton(onClick = { onSeek((playback.positionMs + 30_000L).coerceAtMost(playback.durationMs)) }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.FastForward, contentDescription = "Forward 30 seconds", tint = MutedCream)
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Sleep timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            playback.sleepRemainingMs != null -> "Fades out in ${formatClock(playback.sleepRemainingMs)}"
                            playback.sleepTimerActive -> "Waiting for this story to end"
                            rememberedTimerLabel != null -> "Last time, you chose $rememberedTimerLabel"
                            else -> "Let the story fade when you’re ready"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedCream,
                    )
                }
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (playback.sleepTimerActive) {
                            sleepMenu = true
                        } else {
                            when (playback.lastSleepTimerChoice) {
                                15 -> setSleepTimer(15L * 60_000L)
                                30 -> setSleepTimer(30L * 60_000L)
                                45 -> setSleepTimer(45L * 60_000L)
                                60 -> setSleepTimer(60L * 60_000L)
                                -1 -> setEndOfEpisodeTimer()
                                else -> sleepMenu = true
                            }
                        }
                    },
                ) {
                    Text(if (playback.sleepTimerActive) "Change" else if (rememberedTimerLabel != null) "Again" else "Set", color = accent)
                }
                DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        DropdownMenuItem(text = { Text("$minutes minutes") }, onClick = { setSleepTimer(minutes * 60_000L); sleepMenu = false })
                    }
                    DropdownMenuItem(text = { Text("End of episode") }, onClick = { setEndOfEpisodeTimer(); sleepMenu = false })
                    if (playback.sleepTimerActive) {
                        DropdownMenuItem(text = { Text("Turn off") }, onClick = { onTimer(null); sleepMenu = false })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = markPlayed,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    if (played) "Mark unplayed" else "Mark played",
                    modifier = Modifier.padding(start = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = download,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    when {
                        downloaded -> "Delete download"
                        downloadInProgress -> downloadProgressPercent?.let { "Cancel · $it%" } ?: "Cancel download"
                        downloadFailed -> "Retry download"
                        else -> "Download"
                    },
                    modifier = Modifier.padding(start = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOpenWebsite) { Icon(Icons.Filled.OpenInNew, contentDescription = "Open episode website", tint = MutedCream) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun NowPlayingPaletteArtwork(
    episodeId: String,
    imageUrl: String?,
    modifier: Modifier,
    fallbackColor: Color,
    onPaletteColor: (Color) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paletteRequested by remember(episodeId, imageUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF70584A), Color(0xFF333B4B)))),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = fallbackColor,
                modifier = Modifier.fillMaxSize(0.34f),
            )
        } else {
            val request = remember(context, episodeId, imageUrl) {
                ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { result ->
                    if (!paletteRequested) {
                        paletteRequested = true
                        val image = result.result.image
                        scope.launch(Dispatchers.Default) {
                            val palette = Palette.from(image.toBitmap(width = 96, height = 96)).generate()
                            val extractedColor = palette.lightVibrantSwatch?.rgb
                                ?: palette.vibrantSwatch?.rgb
                                ?: palette.lightMutedSwatch?.rgb
                                ?: palette.mutedSwatch?.rgb
                                ?: palette.dominantSwatch?.rgb
                                ?: fallbackColor.toArgb()
                            val extractedAccent = Color(extractedColor)
                            val accent = if (extractedAccent.luminance() >= 0.28f) extractedAccent else fallbackColor
                            withContext(Dispatchers.Main.immediate) {
                                onPaletteColor(accent)
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun BedtimeNowPlaying(
    episode: Episode,
    playback: PlaybackUiState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onTimer: (Long?) -> Unit,
    onEndOfEpisodeTimer: () -> Unit,
    onExtendSleepTimer: () -> Unit,
    onToggleBedtime: () -> Unit,
) {
    val isEndOfEpisodeChoice = playback.lastSleepTimerChoice == -1
    val startTimerMinutes = when (playback.lastSleepTimerChoice) {
        15 -> 15
        30 -> 30
        45 -> 45
        60 -> 60
        else -> 15
    }
    val timerButtonLabel = when {
        playback.sleepTimerActive -> "+10 min"
        isEndOfEpisodeChoice -> "Start end-of-episode timer"
        else -> "Start $startTimerMinutes min timer"
    }
    val warm = Color(0xFFB9AD9A)
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = warm.copy(alpha = 0.72f))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleBedtime) {
                    Icon(Icons.Filled.Bedtime, contentDescription = "Turn bedtime mode off", tint = warm.copy(alpha = 0.72f))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(episode.yearLabel, style = MaterialTheme.typography.labelLarge, color = warm.copy(alpha = 0.66f))
                Text(
                    episode.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = warm.copy(alpha = 0.88f),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playback.journeyMode && playback.activeJourneyPlan != null && playback.journeyStoryCount > 0) {
                    Text(
                        "Tonight: story ${playback.journeyStoryIndex.coerceAtLeast(1)} of ${playback.journeyStoryCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = warm.copy(alpha = 0.62f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSeek((playback.positionMs - 15_000L).coerceAtLeast(0L)) }, modifier = Modifier.size(68.dp)) {
                    Icon(Icons.Filled.FastRewind, contentDescription = "Back 15 seconds", tint = warm.copy(alpha = 0.68f), modifier = Modifier.size(32.dp))
                }
                MorphingPlayPauseButton(
                    isPlaying = playback.isPlaying,
                    onToggle = onPlayPause,
                    modifier = Modifier.size(100.dp),
                    containerColor = warm.copy(alpha = 0.18f),
                    contentColor = warm,
                    iconSize = 52.dp,
                )
                IconButton(
                    onClick = { onSeek((playback.positionMs + 30_000L).coerceAtMost(playback.durationMs)) },
                    modifier = Modifier.size(68.dp),
                ) {
                    Icon(Icons.Filled.FastForward, contentDescription = "Forward 30 seconds", tint = warm.copy(alpha = 0.68f), modifier = Modifier.size(32.dp))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF080706)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("SLEEP TIMER", style = MaterialTheme.typography.labelMedium, color = warm.copy(alpha = 0.62f), fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !playback.sleepTimerActive -> "No timer set"
                            playback.sleepRemainingMs == null -> "Waiting for this story to end"
                            else -> "Fades out in ${formatClock(playback.sleepRemainingMs)}"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = warm.copy(alpha = 0.88f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(
                        onClick = {
                            if (playback.sleepTimerActive) {
                                onExtendSleepTimer()
                            } else if (isEndOfEpisodeChoice) {
                                onEndOfEpisodeTimer()
                            } else {
                                onTimer(startTimerMinutes * 60_000L)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = warm.copy(alpha = 0.15f),
                            contentColor = warm.copy(alpha = 0.94f),
                        ),
                    ) {
                        Text(timerButtonLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButtonSpeed(speed: Float, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text("${speed}×", color = Apricot, fontWeight = FontWeight.Bold)
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes % 60, seconds) else "%d:%02d".format(minutes, seconds)
}
