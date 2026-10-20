package com.eetimeline.widget

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceComposable
import androidx.media3.common.util.UnstableApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.eetimeline.MainActivity
import com.eetimeline.core.Curriculum
import com.eetimeline.core.orderedEpisodeIds
import com.eetimeline.data.CurriculumRepository
import com.eetimeline.playback.EpisodePlaybackState
import com.eetimeline.playback.EpisodeStateRepository
import com.eetimeline.playback.JourneyPlaybackCommands
import com.eetimeline.ui.theme.unitColor
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first


private val WidgetNight = Color(0xFF15171D)
private val WidgetSurface = Color(0xFF292D37)
private val WidgetCream = Color(0xFFF5EBDD)
private val WidgetMutedCream = Color(0xFFB9B2A8)
private val WidgetApricot = Color(0xFFF6B27F)
private val WidgetMint = Color(0xFFA5D2B9)
/** Glance widget showing the current or next curriculum story and one-tap journey controls. */
class JourneyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val curriculum = CurriculumRepository(appContext).load()
        val progressFlow = EpisodeStateRepository.getInstance(appContext).observeAll()
        val initialProgress = progressFlow.first()
        val snapshotFlow = JourneySurfaceSnapshotStore.observe(appContext)
        val initialSnapshot = JourneySurfaceSnapshotStore.read(appContext)
        provideContent {
            val progress by progressFlow.collectAsState(initial = initialProgress)
            val snapshot by snapshotFlow.collectAsState(initial = initialSnapshot)
            JourneyWidgetContent(
                JourneyWidgetData.from(curriculum, progress, snapshot),
                appContext,
            )
        }
    }
}

@Composable
@GlanceComposable
private fun JourneyWidgetContent(data: JourneyWidgetData, context: Context) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetNight)
            .cornerRadius(22.dp)
            .appWidgetBackground()
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(16.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = data.titleContext,
                    style = TextStyle(
                        color = ColorProvider(WidgetMint),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = data.title,
                    style = TextStyle(
                        color = ColorProvider(WidgetCream),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
            if (data.unitNumber != null) {
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = "UNIT ${data.unitNumber}",
                    modifier = GlanceModifier
                        .background(ColorProvider(Color(data.unitColorArgb)))
                        .cornerRadius(12.dp)
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    style = TextStyle(
                        color = ColorProvider(WidgetNight),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.height(7.dp))
        if (data.nextTitle != null) {
            Text(
                text = "THEN · ${data.nextTitle}",
                style = TextStyle(color = ColorProvider(WidgetMutedCream), fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(5.dp))
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = data.progressText,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(WidgetMutedCream), fontSize = 12.sp),
                maxLines = 1,
            )
            Row(
                modifier = GlanceModifier
                    .background(WidgetSurface)
                    .cornerRadius(14.dp)
                    .clickable(actionRunCallback<ToggleJourneyAction>())
                    .padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text(
                    text = if (data.isPlaying) "Ⅱ" else "▶",
                    style = TextStyle(
                        color = ColorProvider(WidgetApricot),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

/** Sends the service-only toggle command without opening the app. */
@OptIn(UnstableApi::class)
class ToggleJourneyAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        JourneyPlaybackCommands.createPendingIntent(context, toggleActiveJourney = true).send()
    }
}

internal data class JourneySurfaceSnapshot(
    val currentEpisodeId: String? = null,
    val isPlaying: Boolean = false,
    val activeJourney: Boolean = false,
)

/** Minimal process-independent playback snapshot shared by the service, widget, and tile. */
internal object JourneySurfaceSnapshotStore {
    private const val PREFERENCES = "journey_surface_state"
    private const val KEY_PROCESS_INSTANCE_ID = "process_instance_id"
    private const val KEY_CURRENT_EPISODE_ID = "current_episode_id"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_ACTIVE_JOURNEY = "active_journey"
    private val processInstanceId = UUID.randomUUID().toString()

    fun read(context: Context): JourneySurfaceSnapshot {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getString(KEY_PROCESS_INSTANCE_ID, null) != processInstanceId) {
            return JourneySurfaceSnapshot()
        }
        return JourneySurfaceSnapshot(
            currentEpisodeId = preferences.getString(KEY_CURRENT_EPISODE_ID, null),
            isPlaying = preferences.getBoolean(KEY_IS_PLAYING, false),
            activeJourney = preferences.getBoolean(KEY_ACTIVE_JOURNEY, false),
        )
    }

    fun observe(context: Context): Flow<JourneySurfaceSnapshot> = callbackFlow {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == KEY_PROCESS_INSTANCE_ID ||
                key == KEY_CURRENT_EPISODE_ID ||
                key == KEY_IS_PLAYING ||
                key == KEY_ACTIVE_JOURNEY
            ) {
                trySend(read(appContext))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(appContext))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun publish(context: Context, snapshot: JourneySurfaceSnapshot) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROCESS_INSTANCE_ID, processInstanceId)
            .putString(KEY_CURRENT_EPISODE_ID, snapshot.currentEpisodeId)
            .putBoolean(KEY_IS_PLAYING, snapshot.isPlaying)
            .putBoolean(KEY_ACTIVE_JOURNEY, snapshot.activeJourney)
            .apply()
    }
}

internal data class JourneyWidgetData(
    val title: String,
    val titleContext: String,
    val nextTitle: String?,
    val progressText: String,
    val isPlaying: Boolean,
    val isJourneyPlaying: Boolean,
    val unitNumber: Int?,
    val unitColorArgb: Int,
) {
    companion object {
        suspend fun load(context: Context): JourneyWidgetData {
            val appContext = context.applicationContext
            val curriculum = CurriculumRepository(appContext).load()
            val progress = EpisodeStateRepository.getInstance(appContext).observeAll().first()
            return from(curriculum, progress, JourneySurfaceSnapshotStore.read(appContext))
        }

        fun from(
            curriculum: Curriculum,
            progress: List<EpisodePlaybackState>,
            surface: JourneySurfaceSnapshot,
        ): JourneyWidgetData {
            val statesById = progress.associateBy { it.episodeId }
            val ordered = curriculum.orderedEpisodeIds().filter(curriculum.episodes::containsKey)
            val playedCount = ordered.count { statesById[it]?.isPlayed == true }
            val currentId = surface.currentEpisodeId?.takeIf(ordered::contains)
            val currentIsActive = currentId != null && (surface.isPlaying || surface.activeJourney)
            val displayId = currentId ?: ordered.firstOrNull { statesById[it]?.isPlayed != true }
            val nextId = if (currentIsActive && surface.activeJourney) {
                currentId?.let { id ->
                    ordered.drop(ordered.indexOf(id) + 1).firstOrNull { statesById[it]?.isPlayed != true }
                }
            } else {
                null
            }
            val displayEpisode = displayId?.let(curriculum.episodes::get)
            val unit = displayId?.let { id ->
                curriculum.units.firstOrNull { era ->
                    era.topics.any { topic -> topic.groups.any { group -> id in group.episodeIds } }
                }
            }
            val total = ordered.size
            val progressText = when {
                total == 0 -> "No stories available"
                playedCount >= total -> "$total stories finished"
                else -> "Story ${playedCount + 1} of $total"
            }
            val titleContext = when {
                currentId != null -> if (surface.isPlaying) "NOW PLAYING" else "PAUSED"
                total > 0 && playedCount >= total -> "JOURNEY COMPLETE"
                total > 0 -> "NEXT UP"
                else -> "YOUR JOURNEY"
            }
            return JourneyWidgetData(
                titleContext = titleContext,
                title = displayEpisode?.title ?: if (total > 0 && playedCount >= total) {
                    "Journey complete"
                } else {
                    "The journey begins"
                },
                nextTitle = nextId?.let { curriculum.episodes[it]?.title },
                progressText = progressText,
                isPlaying = surface.isPlaying,
                isJourneyPlaying = surface.activeJourney && surface.isPlaying,
                unitNumber = unit?.number,
                unitColorArgb = unit?.number?.let { unitColor(it).toArgb() } ?: WidgetApricot.toArgb(),
            )
        }
}
}
