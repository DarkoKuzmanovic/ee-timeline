package com.eetimeline.ui.screens

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eetimeline.playback.PlaybackSettings
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.SurfaceRaised
import java.util.Calendar

/** Standalone playback and bedtime preferences screen; navigation is owned by the host. */
@Composable
fun SettingsScreen(
    settings: PlaybackSettings,
    onBedtimeStartMinuteChange: (Int) -> Unit,
    onBedtimeEndMinuteChange: (Int) -> Unit,
    onAutoSleepTimerInBedtimeChange: (Boolean) -> Unit,
    onShakeToExtendChange: (Boolean) -> Unit,
    onRewindOffsetMinutesChange: (Int) -> Unit,
    onDefaultPlaybackSpeedChange: (Float) -> Unit,
    onVoiceBoostChange: (Boolean) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("Listening settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Set a gentle bedtime routine and make each story sound right for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedCream,
            )
        }

        SettingsCard {
            Text("Bedtime window", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Choose when bedtime listening begins and ends.", style = MaterialTheme.typography.bodySmall, color = MutedCream)
            BedtimeTimeRow("Start", settings.bedtimeStartMinute, onBedtimeStartMinuteChange)
            BedtimeTimeRow("End", settings.bedtimeEndMinute, onBedtimeEndMinuteChange)
        }

        SettingsCard {
            Text("Sleep cues", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SettingsSwitchRow(
                title = "Start a sleep timer at bedtime",
                description = "Automatically wind down playback during your bedtime window.",
                checked = settings.autoSleepTimerInBedtime,
                onCheckedChange = onAutoSleepTimerInBedtimeChange,
            )
            SettingsSwitchRow(
                title = "Shake to extend",
                description = "Shake your phone to keep listening a little longer.",
                checked = settings.shakeToExtend,
                onCheckedChange = onShakeToExtendChange,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("If you fall asleep, rewind by", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10).forEach { minutes ->
                        if (settings.rewindOffsetMinutes == minutes) {
                            Button(
                                onClick = { onRewindOffsetMinutesChange(minutes) },
                                modifier = Modifier.weight(1f),
                            ) { Text("$minutes min") }
                        } else {
                            OutlinedButton(
                                onClick = { onRewindOffsetMinutesChange(minutes) },
                                modifier = Modifier.weight(1f),
                            ) { Text("$minutes min") }
                        }
                    }
                }
            }
        }

        SettingsCard {
            Text("Default playback speed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Used when you start a story.", style = MaterialTheme.typography.bodySmall, color = MutedCream)
            PlaybackSpeedControl(settings.speed, onDefaultPlaybackSpeedChange)
        }

        SettingsCard {
            Text("Audio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SettingsSwitchRow(
                title = "Voice boost",
                description = "Bring spoken narration forward.",
                checked = settings.voiceBoost,
                onCheckedChange = onVoiceBoostChange,
            )
            SettingsSwitchRow(
                title = "Skip silence",
                description = "Move through longer pauses in the recording.",
                checked = settings.skipSilence,
                onCheckedChange = onSkipSilenceChange,
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun BedtimeTimeRow(label: String, minuteOfDay: Int, onTimeChange: (Int) -> Unit) {
    val context = LocalContext.current
    val safeMinute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    val calendar = remember(safeMinute) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, safeMinute / 60)
            set(Calendar.MINUTE, safeMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute -> onTimeChange(hourOfDay * 60 + minute) },
                    safeMinute / 60,
                    safeMinute % 60,
                    DateFormat.is24HourFormat(context),
                ).show()
            },
        ) {
            Text(DateFormat.getTimeFormat(context).format(calendar.time), color = Apricot)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MutedCream)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PlaybackSpeedControl(speed: Float, onSpeedChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val choices = listOf(0.8f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)
    val label = if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        androidx.compose.foundation.layout.Box {
            TextButton(onClick = { expanded = true }) { Text(label, color = Apricot) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(if (choice % 1f == 0f) "${choice.toInt()}×" else "${choice}×") },
                        onClick = {
                            onSpeedChange(choice)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
