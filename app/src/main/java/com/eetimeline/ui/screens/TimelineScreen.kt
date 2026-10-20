package com.eetimeline.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eetimeline.core.Curriculum
import com.eetimeline.core.CurriculumUnit
import com.eetimeline.core.Episode
import com.eetimeline.core.orderedEpisodeIds
import com.eetimeline.core.timelineScale
import com.eetimeline.core.timelineX
import com.eetimeline.ui.EpisodeProgress
import com.eetimeline.ui.theme.Apricot
import com.eetimeline.ui.theme.MutedCream
import com.eetimeline.ui.theme.unitColor
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val UNIT_LABEL_WIDTH = 116.dp
private val TIMELINE_MIN_WIDTH = 1_120.dp
private val PLOT_HORIZONTAL_PADDING = 18.dp
private val DOT_STACK_SPACING = 11.dp
private val DOT_RADIUS = 4.3.dp
private val DOT_HIT_RADIUS = 24.dp
private val AXIS_HEIGHT = 50.dp
private const val MIN_TIMELINE_ZOOM = 1f
private const val MAX_TIMELINE_ZOOM = 4f
private val OVERVIEW_HEIGHT = 58.dp

internal data class TimelineZoomTransform(val zoom: Float, val scrollPx: Float)

internal fun timelineZoomTransform(
    currentZoom: Float,
    requestedZoom: Float,
    minimumZoom: Float,
    maximumZoom: Float,
    contentWidthPx: Float,
    viewportWidthPx: Float,
    scrollPx: Float,
    focalPointPx: Float,
    horizontalPaddingPx: Float,
): TimelineZoomTransform {
    val zoom = requestedZoom.coerceIn(minimumZoom, maximumZoom)
    val scaledContentWidth = contentWidthPx * (zoom / currentZoom)
    val oldPlotWidth = (contentWidthPx - horizontalPaddingPx * 2f).coerceAtLeast(1f)
    val newPlotWidth = (scaledContentWidth - horizontalPaddingPx * 2f).coerceAtLeast(1f)
    val focalProgress = (
        (scrollPx + focalPointPx - horizontalPaddingPx) / oldPlotWidth
    ).coerceIn(0f, 1f)
    val maximumScroll = (scaledContentWidth - viewportWidthPx).coerceAtLeast(0f)
    val transformedScroll = (
        horizontalPaddingPx + focalProgress * newPlotWidth - focalPointPx
    ).coerceIn(0f, maximumScroll)
    return TimelineZoomTransform(zoom, transformedScroll)
}

private data class TimelineDot(val episode: Episode, val x: Float, val track: Int)

private data class TimelineLane(
    val unit: CurriculumUnit,
    val dots: List<TimelineDot>,
    val height: Dp,
)

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
    val laneSources = remember(curriculum) {
        curriculum.units.map { unit ->
            val episodes = unit.topics
                .flatMap { topic -> topic.groups.flatMap { it.episodeIds } }
                .mapNotNull(curriculum.episodes::get)
            unit to episodes
        }
    }
    val presentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val scale = remember(allEpisodes, presentYear) {
        timelineScale(allEpisodes.map(Episode::approxYear), presentYear)
    }
    val targetEpisode = remember(allEpisodes, currentEpisodeId, progress) {
        allEpisodes.firstOrNull { it.id == currentEpisodeId }
            ?: allEpisodes.firstOrNull { progress[it.id]?.played != true }
    }
    val scroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    var canvasPixelSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportWidthPx by remember { mutableStateOf(0) }
    var zoomFactor by remember { mutableFloatStateOf(MIN_TIMELINE_ZOOM) }
    var pendingZoomScrollPx by remember { mutableStateOf<Float?>(null) }
    var pendingCanvasWidthPx by remember { mutableIntStateOf(0) }
    val dotAppearances = remember { mutableStateMapOf<String, Float>() }
    val previousVisibleDotIds = remember { HashSet<String>() }
    val currentVisibleDotIds = remember { HashSet<String>() }
    val screenScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val density = LocalDensity.current
    val plotPaddingPx = with(density) { PLOT_HORIZONTAL_PADDING.toPx() }
    val jitterWindowPx = with(density) { 120.dp.toPx() }

    val dotSpacingPx = with(density) { (DOT_RADIUS * 2 + 2.dp).toPx() }
    val laneLayouts = remember(laneSources, scale, canvasPixelSize.width, density) {
        laneSources.map { (unit, episodes) ->
            val dots = if (canvasPixelSize.width == 0) {
                emptyList()
            } else {
                timelineDots(
                    episodes = episodes,
                    scale = scale,
                    width = canvasPixelSize.width.toFloat(),
                    horizontalPadding = plotPaddingPx,
                    minimumSpacing = dotSpacingPx,
                    jitterWindow = jitterWindowPx,
                )
            }
            val trackCount = dots.maxOfOrNull { it.track + 1 } ?: 1
            TimelineLane(unit, dots, laneHeight(trackCount))
        }
    }
    LaunchedEffect(laneLayouts, viewportWidthPx) {
        currentVisibleDotIds.clear()
        snapshotFlow { scroll.value }.collect { scrollPx ->
            currentVisibleDotIds.clear()
            val visibleRight = scrollPx + viewportWidthPx
            laneLayouts.forEach { lane ->
                lane.dots.forEach { dot ->
                    if (dot.x >= scrollPx && dot.x <= visibleRight) {
                        currentVisibleDotIds.add(dot.episode.id)
                    }
                }
            }
            val previousIterator = previousVisibleDotIds.iterator()
            while (previousIterator.hasNext()) {
                if (previousIterator.next() !in currentVisibleDotIds) {
                    previousIterator.remove()
                }
            }
            currentVisibleDotIds.forEach { episodeId ->
                if (previousVisibleDotIds.add(episodeId)) {
                    dotAppearances[episodeId] = 0f
                    screenScope.launch {
                        animate(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 260),
                        ) { value, _ ->
                            dotAppearances[episodeId] = value
                        }
                    }
                }
            }
        }
    }
    val axisPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }
    val pulse by rememberInfiniteTransition(label = "timeline-current-item").animateFloat(
        initialValue = 0.88f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "timeline-current-pulse",
    )

    Column(
        Modifier.fillMaxSize().padding(top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("The long view", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Nine eras, laid out across the centuries. Swipe or pinch to wander; double-tap to toggle zoom.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedCream,
            )
        }

        BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            val baseTimelineWidth = maxOf(TIMELINE_MIN_WIDTH, maxWidth - UNIT_LABEL_WIDTH)
            val timelineWidth = baseTimelineWidth * zoomFactor
            val baseTimelineWidthPx = with(density) { baseTimelineWidth.toPx() }
            val timelineWidthPx = with(density) { timelineWidth.toPx() }
            val plotWidthPx = (timelineWidthPx - plotPaddingPx * 2).coerceAtLeast(0f)
            val latestZoomFactor = rememberUpdatedState(zoomFactor)
            val latestViewportWidthPx = rememberUpdatedState(viewportWidthPx)
            val latestBaseTimelineWidthPx = rememberUpdatedState(baseTimelineWidthPx)
            val latestPlotPaddingPx = rememberUpdatedState(plotPaddingPx)
            val latestLabelWidthPx = rememberUpdatedState(
                with(density) { UNIT_LABEL_WIDTH.toPx() },
            )
            val axisTicks = remember(scale, timelineWidth, density) {
                adaptiveTimelineTicks(
                    scale = scale,
                    plotWidth = plotWidthPx,
                    minimumSpacing = with(density) { 64.dp.toPx() },
                ).map { year -> year to yearLabel(year) }
            }

            LaunchedEffect(zoomFactor) {
                val targetScroll = pendingZoomScrollPx ?: return@LaunchedEffect
                val expectedWidth = pendingCanvasWidthPx
                if (abs(canvasPixelSize.width - expectedWidth) > 1) {
                    snapshotFlow { canvasPixelSize.width }
                        .first { abs(it - expectedWidth) <= 1 }
                }
                scroll.scrollTo(targetScroll.roundToInt().coerceIn(0, scroll.maxValue))
                pendingZoomScrollPx = null
                pendingCanvasWidthPx = 0
            }

            LaunchedEffect(targetEpisode?.id, canvasPixelSize.width > 0, viewportWidthPx) {
                if (targetEpisode != null && canvasPixelSize.width > 0 && viewportWidthPx > 0) {
                    val targetX = plotPaddingPx +
                        timelineX(targetEpisode.approxYear, scale) * plotWidthPx
                    val targetScroll = (targetX - viewportWidthPx / 2f)
                        .roundToInt()
                        .coerceIn(0, scroll.maxValue)
                    scroll.scrollTo(targetScroll)
                }
            }
            fun zoomAround(focalPoint: Float, requestedZoom: Float) {
                val currentZoom = zoomFactor
                val transform = timelineZoomTransform(
                    currentZoom = currentZoom,
                    requestedZoom = requestedZoom,
                    minimumZoom = MIN_TIMELINE_ZOOM,
                    maximumZoom = MAX_TIMELINE_ZOOM,
                    contentWidthPx = baseTimelineWidthPx * currentZoom,
                    viewportWidthPx = viewportWidthPx.toFloat(),
                    scrollPx = scroll.value.toFloat(),
                    focalPointPx = focalPoint,
                    horizontalPaddingPx = plotPaddingPx,
                )
                if (transform.zoom != currentZoom) {
                    pendingCanvasWidthPx =
                        (baseTimelineWidthPx * transform.zoom).roundToInt()
                    pendingZoomScrollPx = transform.scrollPx
                    zoomFactor = transform.zoom
                }
            }
            fun scrollTimelineBy(deltaPx: Float): Boolean {
                val currentScroll = scroll.value
                val targetScroll = (currentScroll + deltaPx)
                    .roundToInt()
                    .coerceIn(0, scroll.maxValue)
                if (targetScroll == currentScroll) return false
                screenScope.launch { scroll.scrollTo(targetScroll) }
                return true
            }

            Column(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            var activeZoom = latestZoomFactor.value
                            awaitFirstDown(requireUnconsumed = false)
                            var previousSpan = 0f
                            do {
                                val event = awaitPointerEvent()
                                var firstIndex = -1
                                var secondIndex = -1
                                event.changes.forEachIndexed { index, change ->
                                    if (change.pressed) {
                                        if (firstIndex < 0) firstIndex = index
                                        else if (secondIndex < 0) secondIndex = index
                                    }
                                }
                                if (secondIndex >= 0) {
                                    val first = event.changes[firstIndex].position
                                    val second = event.changes[secondIndex].position
                                    val span = (first - second).getDistance()
                                    val currentViewportWidthPx = latestViewportWidthPx.value
                                    if (
                                        previousSpan > 0f &&
                                        span > 0f &&
                                        currentViewportWidthPx > 0
                                    ) {
                                        val centroidX = (first.x + second.x) / 2f
                                        val focalPoint = (centroidX - latestLabelWidthPx.value)
                                            .coerceIn(0f, currentViewportWidthPx.toFloat())
                                        val previousZoom = activeZoom
                                        val currentBaseTimelineWidthPx =
                                            latestBaseTimelineWidthPx.value
                                        val transform = timelineZoomTransform(
                                            currentZoom = previousZoom,
                                            requestedZoom = previousZoom * span / previousSpan,
                                            minimumZoom = MIN_TIMELINE_ZOOM,
                                            maximumZoom = MAX_TIMELINE_ZOOM,
                                            contentWidthPx = currentBaseTimelineWidthPx * previousZoom,
                                            viewportWidthPx = currentViewportWidthPx.toFloat(),
                                            scrollPx = scroll.value.toFloat(),
                                            focalPointPx = focalPoint,
                                            horizontalPaddingPx = latestPlotPaddingPx.value,
                                        )
                                        if (transform.zoom != previousZoom) {
                                            pendingCanvasWidthPx =
                                                (currentBaseTimelineWidthPx * transform.zoom)
                                                    .roundToInt()
                                            pendingZoomScrollPx = transform.scrollPx
                                            activeZoom = transform.zoom
                                            zoomFactor = transform.zoom
                                        }
                                    }
                                    previousSpan = span
                                    event.changes.forEach { it.consume() }
                                } else {
                                    previousSpan = 0f
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .semantics {
                        contentDescription = "Timeline zoom and scroll controls"
                        stateDescription = "Zoom ${(zoomFactor * 100f).roundToInt()} percent"
                        customActions = buildList {
                            if (zoomFactor < MAX_TIMELINE_ZOOM) {
                                add(CustomAccessibilityAction("Zoom in") {
                                    zoomAround(
                                        viewportWidthPx / 2f,
                                        minOf(MAX_TIMELINE_ZOOM, zoomFactor * 1.5f),
                                    )
                                    true
                                })
                            }
                            if (zoomFactor > MIN_TIMELINE_ZOOM) {
                                add(CustomAccessibilityAction("Zoom out") {
                                    zoomAround(
                                        viewportWidthPx / 2f,
                                        maxOf(MIN_TIMELINE_ZOOM, zoomFactor / 1.5f),
                                    )
                                    true
                                })
                                add(CustomAccessibilityAction("Reset zoom") {
                                    zoomAround(viewportWidthPx / 2f, MIN_TIMELINE_ZOOM)
                                    true
                                })
                            }
                            add(CustomAccessibilityAction("Scroll earlier") {
                                scrollTimelineBy(-viewportWidthPx * 0.8f)
                            })
                            add(CustomAccessibilityAction("Scroll later") {
                                scrollTimelineBy(viewportWidthPx * 0.8f)
                            })
                        }
                    },
            ) {
                Row(
                    Modifier.fillMaxWidth().height(AXIS_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.width(UNIT_LABEL_WIDTH).padding(end = 8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "YEAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedCream,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("then → now", style = MaterialTheme.typography.labelSmall, color = MutedCream)
                    }
                    Box(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .onSizeChanged { viewportWidthPx = it.width }
                            .horizontalScroll(scroll),
                    ) {
                        Canvas(
                            Modifier.width(timelineWidth)
                                .height(AXIS_HEIGHT)
                                .onSizeChanged { canvasPixelSize = it },
                        ) {
                            val left = PLOT_HORIZONTAL_PADDING.toPx()
                            val right = size.width - left
                            val plotWidth = right - left
                            val ticks = axisTicks
                            val axisY = 10.dp.toPx()

                            drawLine(
                                color = Color(0xFF656A75),
                                start = Offset(left, axisY),
                                end = Offset(right, axisY),
                                strokeWidth = 1.dp.toPx(),
                            )
                            axisPaint.textSize = 11.sp.toPx()
                            axisPaint.color = android.graphics.Color.rgb(185, 178, 168)
                            ticks.forEach { (year, label) ->
                                val x = left + timelineX(year, scale) * plotWidth
                                drawLine(
                                    color = Color(0xFF777C86),
                                    start = Offset(x, axisY - 4.dp.toPx()),
                                    end = Offset(x, axisY + 5.dp.toPx()),
                                    strokeWidth = 1.dp.toPx(),
                                )
                                val labelWidth = axisPaint.measureText(label)
                                val labelX = (x - labelWidth / 2f).coerceIn(left, right - labelWidth)
                                drawContext.canvas.nativeCanvas.drawText(
                                    label,
                                    labelX,
                                    size.height - 3.dp.toPx(),
                                    axisPaint,
                                )
                            }
                        }
                    }
                }

                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(verticalScroll),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    laneLayouts.forEach { lane ->
                        Row(
                            Modifier.fillMaxWidth().height(lane.height),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.width(UNIT_LABEL_WIDTH)
                                    .height(lane.height)
                                    .padding(end = 8.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Surface(
                                    color = unitColor(lane.unit.number).copy(alpha = 0.19f),
                                    contentColor = unitColor(lane.unit.number),
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = timelineEraIcon(lane.unit.number),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            "UNIT ${lane.unit.number}",
                                            modifier = Modifier.padding(start = 5.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    lane.unit.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                    ),
                                    color = MutedCream,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .onSizeChanged { viewportWidthPx = it.width }
                                    .horizontalScroll(scroll),
                            ) {
                                Canvas(
                                    Modifier.width(timelineWidth)
                                        .height(lane.height)
                                        .pointerInput(lane.dots, zoomFactor) {
                                            val centerY = laneCenterY(lane, density)
                                            val stackSpacingPx = with(density) { DOT_STACK_SPACING.toPx() }
                                            val hitRadiusPx = with(density) { DOT_HIT_RADIUS.toPx() }
                                            val hitRadiusSquared = hitRadiusPx * hitRadiusPx
                                            detectTapGestures(
                                                onDoubleTap = { tap ->
                                                    val focalPoint = (tap.x - scroll.value)
                                                        .coerceIn(0f, viewportWidthPx.toFloat())
                                                    val nextZoom = if (
                                                        zoomFactor > MIN_TIMELINE_ZOOM + 0.05f
                                                    ) {
                                                        MIN_TIMELINE_ZOOM
                                                    } else {
                                                        2f
                                                    }
                                                    zoomAround(focalPoint, nextZoom)
                                                },
                                                onTap = { tap ->
                                                    var nearestDot: TimelineDot? = null
                                                    var nearestDistance = Float.MAX_VALUE
                                                    lane.dots.forEach { dot ->
                                                        val center = Offset(
                                                            dot.x,
                                                            centerY + stackOffset(dot.track) * stackSpacingPx,
                                                        )
                                                        val distance = distanceSquared(tap, center)
                                                        if (distance < nearestDistance) {
                                                            nearestDot = dot
                                                            nearestDistance = distance
                                                        }
                                                    }
                                                    if (nearestDistance < hitRadiusSquared) {
                                                        nearestDot?.let {
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            onEpisodeClick(it.episode)
                                                        }
                                                    }
                                                },
                                            )
                                        },
                                ) {
                                    val left = PLOT_HORIZONTAL_PADDING.toPx()
                                    val right = size.width - left
                                    val plotWidth = right - left
                                    val firstX = left + timelineX(lane.unit.startYear, scale) * plotWidth
                                    val lastX = left + timelineX(lane.unit.endYear, scale) * plotWidth
                                    val spanLeft = minOf(firstX, lastX)
                                    val spanWidth = maxOf(8.dp.toPx(), abs(lastX - firstX))
                                    val spanHeight = (size.height - 10.dp.toPx()).coerceAtLeast(1.dp.toPx())

                                    drawRoundRect(
                                        color = unitColor(lane.unit.number).copy(alpha = 0.18f),
                                        topLeft = Offset(spanLeft, (size.height - spanHeight) / 2f),
                                        size = Size(spanWidth, spanHeight),
                                        cornerRadius = CornerRadius(13.dp.toPx()),
                                    )
                                    drawLine(
                                        color = Color(0xFF656A75).copy(alpha = 0.7f),
                                        start = Offset(left, size.height / 2f),
                                        end = Offset(right, size.height / 2f),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                    targetEpisode?.let { nextStop ->
                                        val markerX = left + timelineX(nextStop.approxYear, scale) * plotWidth
                                        drawLine(
                                            color = Apricot.copy(alpha = 0.42f),
                                            start = Offset(markerX, 2.dp.toPx()),
                                            end = Offset(markerX, size.height - 2.dp.toPx()),
                                            strokeWidth = 1.dp.toPx(),
                                        )
                                    }

                                    lane.dots.forEach { dot ->
                                        val appearance = dotAppearances[dot.episode.id] ?: 0f
                                        if (appearance > 0f) {
                                            val dotY = size.height / 2f +
                                                stackOffset(dot.track) * DOT_STACK_SPACING.toPx()
                                            val played = progress[dot.episode.id]?.played == true
                                            val isCurrent = currentEpisodeId == dot.episode.id
                                            val color = unitColor(lane.unit.number)
                                            val entranceScale = 0.65f + 0.35f * appearance
                                            if (isCurrent) {
                                                drawCircle(
                                                    color = Apricot.copy(alpha = 0.2f * appearance),
                                                    radius = 13.dp.toPx() * pulse * entranceScale,
                                                    center = Offset(dot.x, dotY),
                                                )
                                            }
                                            val radius =
                                                (if (isCurrent) 6.dp.toPx() else DOT_RADIUS.toPx()) * entranceScale
                                            if (played) {
                                                drawCircle(
                                                    color.copy(alpha = appearance),
                                                    radius,
                                                    Offset(dot.x, dotY),
                                                )
                                            } else {
                                                drawCircle(
                                                    color.copy(alpha = 0.9f * appearance),
                                                    radius,
                                                    Offset(dot.x, dotY),
                                                    style = Stroke(width = 1.5.dp.toPx() * entranceScale),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(OVERVIEW_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.width(UNIT_LABEL_WIDTH).padding(end = 8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "OVERVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedCream,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "drag to scrub",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedCream,
                        )
                    }
                    Canvas(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 5.dp)
                            .pointerInput(timelineWidthPx, viewportWidthPx, scroll.maxValue) {
                                detectTapGestures { tap ->
                                    val left = with(density) { PLOT_HORIZONTAL_PADDING.toPx() }
                                    val plotWidth = (size.width - left * 2f).coerceAtLeast(1f)
                                    val fraction = ((tap.x - left) / plotWidth).coerceIn(0f, 1f)
                                    val target = (
                                        fraction * timelineWidthPx - viewportWidthPx / 2f
                                    ).roundToInt().coerceIn(0, scroll.maxValue)
                                    scroll.dispatchRawDelta(target.toFloat() - scroll.value.toFloat())
                                }
                            }
                            .pointerInput(timelineWidthPx, viewportWidthPx, scroll.maxValue) {
                                var dragStartX = 0f
                                var dragStartScroll = 0f
                                detectDragGestures(
                                    onDragStart = { point ->
                                        dragStartX = point.x
                                        dragStartScroll = scroll.value.toFloat()
                                    },
                                    onDrag = { change, _ ->
                                        val left = with(density) { PLOT_HORIZONTAL_PADDING.toPx() }
                                        val plotWidth = (size.width - left * 2f).coerceAtLeast(1f)
                                        val target = dragStartScroll +
                                            (change.position.x - dragStartX) * timelineWidthPx / plotWidth
                                        scroll.dispatchRawDelta(target - scroll.value)
                                        change.consume()
                                    },
                                )
                            }
                            .semantics {
                                contentDescription =
                                    "Timeline overview. Use horizontal scrolling or scrub actions to move through time."
                                horizontalScrollAxisRange = ScrollAxisRange(
                                    value = { scroll.value.toFloat() },
                                    maxValue = { scroll.maxValue.toFloat() },
                                )
                                scrollBy("Scrub timeline") { x, _ ->
                                    scrollTimelineBy(x)
                                }
                                customActions = listOf(
                                    CustomAccessibilityAction("Scrub earlier") {
                                        scrollTimelineBy(-viewportWidthPx * 0.2f)
                                    },
                                    CustomAccessibilityAction("Scrub later") {
                                        scrollTimelineBy(viewportWidthPx * 0.2f)
                                    },
                                )
                            },
                    ) {
                        val left = PLOT_HORIZONTAL_PADDING.toPx()
                        val plotWidth = (size.width - left * 2f).coerceAtLeast(1f)
                        val rowHeight = size.height / laneLayouts.size.coerceAtLeast(1)
                        drawRoundRect(
                            color = Color(0xFF171A20),
                            cornerRadius = CornerRadius(5.dp.toPx()),
                        )
                        laneLayouts.forEachIndexed { laneIndex, lane ->
                            val y = rowHeight * (laneIndex + 0.5f)
                            val color = unitColor(lane.unit.number)
                            drawLine(
                                color = color.copy(alpha = 0.28f),
                                start = Offset(left, y),
                                end = Offset(left + plotWidth, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                            lane.dots.forEach { dot ->
                                val x = left + timelineX(dot.episode.approxYear, scale) * plotWidth
                                drawCircle(color, 1.1.dp.toPx(), Offset(x, y))
                            }
                        }
                        if (timelineWidthPx > 0f && viewportWidthPx > 0) {
                            val viewportStart = left +
                                (scroll.value / timelineWidthPx).coerceIn(0f, 1f) * plotWidth
                            val viewportWidth = (
                                viewportWidthPx / timelineWidthPx * plotWidth
                            ).coerceAtLeast(minOf(4.dp.toPx(), plotWidth)).coerceAtMost(plotWidth)
                            val indicator = Size(viewportWidth, size.height)
                            val cornerRadius = CornerRadius(5.dp.toPx())
                            drawRoundRect(
                                color = Apricot.copy(alpha = 0.12f),
                                topLeft = Offset(viewportStart, 0f),
                                size = indicator,
                                cornerRadius = cornerRadius,
                            )
                            drawRoundRect(
                                color = Apricot.copy(alpha = 0.78f),
                                topLeft = Offset(viewportStart, 0f),
                                size = indicator,
                                cornerRadius = cornerRadius,
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }

        Text(
            "Tap a story for details · filled dots are played · the pulse marks what’s playing · the line marks your next stop",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MutedCream,
        )
    }
}

private fun timelineDots(
    episodes: List<Episode>,
    scale: com.eetimeline.core.TimelineScale,
    width: Float,
    horizontalPadding: Float,
    minimumSpacing: Float,
    jitterWindow: Float,
): List<TimelineDot> {
    val orderedEpisodes = episodes.sortedBy { timelineX(it.approxYear, scale) }
    val plotWidth = width - horizontalPadding * 2
    val baseXs = FloatArray(orderedEpisodes.size) { index ->
        horizontalPadding + timelineX(orderedEpisodes[index].approxYear, scale) * plotWidth
    }
    val spreadXs = FloatArray(orderedEpisodes.size)
    var clusterStart = 0
    while (clusterStart < orderedEpisodes.size) {
        var clusterEnd = clusterStart + 1
        while (
            clusterEnd < orderedEpisodes.size &&
            baseXs[clusterEnd] - baseXs[clusterEnd - 1] < minimumSpacing * 1.5f &&
            baseXs[clusterEnd] - baseXs[clusterStart] < jitterWindow
        ) {
            clusterEnd++
        }
        val clusterSize = clusterEnd - clusterStart
        if (clusterSize == 1) {
            spreadXs[clusterStart] = baseXs[clusterStart]
        } else {
            val clusterFirstX = baseXs[clusterStart]
            val clusterLastX = baseXs[clusterEnd - 1]
            val actualSpread = clusterLastX - clusterFirstX
            val desiredSpread = minOf(jitterWindow, minimumSpacing * (clusterSize - 1))
            val spread = maxOf(actualSpread, desiredSpread).coerceAtMost(plotWidth)
            val center = (clusterFirstX + clusterLastX) / 2f
            val spreadStart = (center - spread / 2f)
                .coerceIn(horizontalPadding, width - horizontalPadding - spread)
            val spreadIntervals = (clusterSize - 1).toFloat()
            for (index in clusterStart until clusterEnd) {
                spreadXs[index] = spreadStart + spread * (index - clusterStart) / spreadIntervals
            }
        }
        clusterStart = clusterEnd
    }

    val orderedIndexes = orderedEpisodes.indices.sortedBy { spreadXs[it] }
    val trackLastX = FloatArray(orderedEpisodes.size)
    var trackCount = 0
    val dots = ArrayList<TimelineDot>(orderedEpisodes.size)
    for (index in orderedIndexes) {
        val x = spreadXs[index]
        var track = 0
        while (track < trackCount && x - trackLastX[track] < minimumSpacing) {
            track++
        }
        if (track == trackCount) trackCount++
        trackLastX[track] = x
        dots.add(TimelineDot(orderedEpisodes[index], x, track))
    }
    return dots
}

private fun laneHeight(trackCount: Int): Dp {
    val outerStack = trackCount / 2
    return (56 + outerStack * DOT_STACK_SPACING.value * 2).dp
}

private fun laneCenterY(lane: TimelineLane, density: androidx.compose.ui.unit.Density): Float =
    with(density) { lane.height.toPx() / 2f }

private fun stackOffset(track: Int): Int = when {
    track == 0 -> 0
    track % 2 == 1 -> -(track + 1) / 2
    else -> track / 2
}

private fun adaptiveTimelineTicks(
    scale: com.eetimeline.core.TimelineScale,
    plotWidth: Float,
    minimumSpacing: Float,
): List<Int> {
    val anchors = intArrayOf(
        scale.earliestYear,
        -3_000,
        1,
        1_000,
        1_200,
        1_450,
        1_750,
        1_900,
        scale.presentYear,
    )
    val niceSteps = intArrayOf(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1_000, 2_000, 5_000)
    val candidates = ArrayList<Int>(anchors.size + 16)
    for (anchor in anchors) candidates.add(anchor)

    for (segment in 0 until anchors.lastIndex) {
        val start = anchors[segment]
        val end = anchors[segment + 1]
        val segmentWidth = (timelineX(end, scale) - timelineX(start, scale)) * plotWidth
        if (segmentWidth < minimumSpacing * 1.4f) continue
        val targetYearStep = ((end - start) * minimumSpacing / segmentWidth).toInt().coerceAtLeast(1)
        val step = niceSteps.firstOrNull { it >= targetYearStep } ?: niceSteps.last()
        var year = ceil(start.toDouble() / step).toInt() * step
        while (year < end) {
            if (year != 0) candidates.add(year)
            year += step
        }
    }

    val selected = ArrayList<Int>()
    for (year in candidates.distinct().sorted()) {
        val x = timelineX(year, scale) * plotWidth
        if (selected.none { abs(timelineX(it, scale) * plotWidth - x) < minimumSpacing }) {
            selected.add(year)
        }
    }
    return selected
}

private fun yearLabel(year: Int): String = when {
    year < 0 -> "${-year} BCE"
    year == 0 -> "1 BCE"
    year == 1 -> "1 CE"
    else -> year.toString()
}

private fun timelineEraIcon(unitNumber: Int): ImageVector = when (unitNumber) {
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

private fun distanceSquared(first: Offset, second: Offset): Float {
    val dx = first.x - second.x
    val dy = first.y - second.y
    return dx * dx + dy * dy
}
