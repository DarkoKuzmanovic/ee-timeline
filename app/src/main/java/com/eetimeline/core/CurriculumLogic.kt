package com.eetimeline.core

import kotlin.math.abs
import kotlin.math.roundToLong

private const val FIRST_TIMELINE_YEAR = -8_000
private const val PREHISTORY_BOUNDARY = -3_000
private const val COMMON_ERA_START = 1
private const val FIRST_MILLENNIUM_BOUNDARY = 1_000
private const val FIRST_ERA_BOUNDARY = 1_200
private const val SECOND_ERA_BOUNDARY = 1_450
private const val THIRD_ERA_BOUNDARY = 1_750
private const val FOURTH_ERA_BOUNDARY = 1_900
private const val TIMELINE_SEGMENT_COUNT = 8
private const val MIN_TIMELINE_SEGMENT_WIDTH = 0.07f
private const val PLAYED_THRESHOLD_MS = 30_000L
private const val SLEEP_FADE_DURATION_MS = 30_000L

/** Returns the episode ids in curriculum playback order: units, topics, groups, then ids. */
fun Curriculum.orderedEpisodeIds(): List<String> {
    val ids = ArrayList<String>()
    for (unit in units) {
        for (topic in unit.topics) {
            for (group in topic.groups) {
                ids.addAll(group.episodeIds)
            }
        }
    }
    return ids
}

/** The first episode in curriculum order that is not in [playedIds]. */
fun Curriculum.firstUnplayed(playedIds: Set<String>): String? {
    for (unit in units) {
        for (topic in unit.topics) {
            for (group in topic.groups) {
                for (episodeId in group.episodeIds) {
                    if (episodeId !in playedIds) return episodeId
                }
            }
        }
    }
    return null
}

/** The next unplayed episode after [currentId], without wrapping to the beginning. */
fun Curriculum.nextUnplayedAfter(currentId: String, playedIds: Set<String>): String? {
    var currentFound = false
    for (unit in units) {
        for (topic in unit.topics) {
            for (group in topic.groups) {
                for (episodeId in group.episodeIds) {
                    if (!currentFound) {
                        if (episodeId == currentId) currentFound = true
                    } else if (episodeId !in playedIds) {
                        return episodeId
                    }
                }
            }
        }
    }
    return null
}

/**
 * Every timeline interval gets at least 7% of the width; the remainder is shared by episode count.
 */
class TimelineScale private constructor(
    val earliestYear: Int,
    val presentYear: Int,
    private val knotYears: IntArray,
    private val knotPositions: FloatArray,
) {
    internal fun position(year: Int): Float {
        val boundedYear = year.coerceIn(earliestYear, presentYear)
        for (segment in 0 until knotYears.lastIndex) {
            val segmentStart = knotYears[segment]
            val segmentEnd = knotYears[segment + 1]
            if (boundedYear <= segmentEnd) {
                val fraction = (boundedYear - segmentStart).toFloat() / (segmentEnd - segmentStart)
                return knotPositions[segment] +
                    fraction * (knotPositions[segment + 1] - knotPositions[segment])
            }
        }
        return 1f
    }

    companion object {
        internal fun create(
            earliestYear: Int,
            presentYear: Int,
            knotYears: IntArray,
            knotPositions: FloatArray,
        ) = TimelineScale(earliestYear, presentYear, knotYears, knotPositions)
    }
}

/** Builds a density-balanced scale from episode years and the current year. */
fun timelineScale(episodeYears: List<Int>, presentYear: Int): TimelineScale {
    val earliestYear = minOf(FIRST_TIMELINE_YEAR, episodeYears.minOrNull() ?: FIRST_TIMELINE_YEAR)
    val boundedPresentYear = maxOf(FOURTH_ERA_BOUNDARY + 1, presentYear)
    val knotYears = intArrayOf(
        earliestYear,
        PREHISTORY_BOUNDARY,
        COMMON_ERA_START,
        FIRST_MILLENNIUM_BOUNDARY,
        FIRST_ERA_BOUNDARY,
        SECOND_ERA_BOUNDARY,
        THIRD_ERA_BOUNDARY,
        FOURTH_ERA_BOUNDARY,
        boundedPresentYear,
    )
    val episodeCounts = IntArray(TIMELINE_SEGMENT_COUNT)
    for (year in episodeYears) {
        val segment = when {
            year < PREHISTORY_BOUNDARY -> 0
            year < COMMON_ERA_START -> 1
            year < FIRST_MILLENNIUM_BOUNDARY -> 2
            year < FIRST_ERA_BOUNDARY -> 3
            year < SECOND_ERA_BOUNDARY -> 4
            year < THIRD_ERA_BOUNDARY -> 5
            year < FOURTH_ERA_BOUNDARY -> 6
            else -> 7
        }
        episodeCounts[segment]++
    }

    val totalEpisodes = episodeCounts.sum()
    val distributableWidth = 1f - MIN_TIMELINE_SEGMENT_WIDTH * TIMELINE_SEGMENT_COUNT
    val knotPositions = FloatArray(knotYears.size)
    for (segment in episodeCounts.indices) {
        val proportionalWidth = if (totalEpisodes == 0) {
            distributableWidth / TIMELINE_SEGMENT_COUNT
        } else {
            distributableWidth * episodeCounts[segment] / totalEpisodes
        }
        knotPositions[segment + 1] =
            knotPositions[segment] + MIN_TIMELINE_SEGMENT_WIDTH + proportionalWidth
    }
    knotPositions[knotPositions.lastIndex] = 1f
    return TimelineScale.create(earliestYear, boundedPresentYear, knotYears, knotPositions)
}

/** Maps [year] to a clamped [0, 1] coordinate on [scale]. */
fun timelineX(year: Int, scale: TimelineScale): Float = scale.position(year)

/** True once playback is within 30 seconds of the end of a positive-duration episode. */
fun isPlayedByPosition(durationMs: Long, positionMs: Long): Boolean =
    durationMs > 0L && positionMs >= durationMs - PLAYED_THRESHOLD_MS

/** Linear volume multiplier over the final 30 seconds of a sleep timer. */
fun sleepFadeVolume(remainingMs: Long): Float =
    remainingMs.coerceIn(0L, SLEEP_FADE_DURATION_MS).toFloat() / SLEEP_FADE_DURATION_MS.toFloat()
data class SleepSegment(
    val episodeId: String,
    val startPositionMs: Long,
    val endPositionMs: Long,
    val wallClockStartMs: Long,
    val wallClockEndMs: Long,
)

data class SleepRewindPoint(
    val episodeId: String,
    val positionMs: Long,
    val segmentIndex: Int,
)

/**
 * Finds the closest recorded point to [targetWallClockMs]. Gaps choose the nearest segment boundary;
 * ties choose the earlier boundary, and overlapping segments choose the first list entry.
 */
fun selectSleepRewindPoint(
    segments: List<SleepSegment>,
    targetWallClockMs: Long,
): SleepRewindPoint? {
    if (segments.isEmpty()) return null

    for (index in segments.indices) {
        val segment = segments[index]
        val wallStart = segment.wallClockStartMs
        val wallEnd = maxOf(wallStart, segment.wallClockEndMs)
        if (targetWallClockMs in wallStart..wallEnd) {
            return SleepRewindPoint(
                episodeId = segment.episodeId,
                positionMs = positionAtWallClockTime(segment, targetWallClockMs, wallStart, wallEnd),
                segmentIndex = index,
            )
        }
    }

    var bestIndex = 0
    var bestPositionMs = 0L
    var bestWallClockMs = 0L
    var bestDistance = Double.POSITIVE_INFINITY
    for (index in segments.indices) {
        val segment = segments[index]
        val wallStart = segment.wallClockStartMs
        val wallEnd = maxOf(wallStart, segment.wallClockEndMs)
        val startDistance = abs(targetWallClockMs.toDouble() - wallStart.toDouble())
        if (startDistance < bestDistance || (startDistance == bestDistance && wallStart < bestWallClockMs)) {
            bestIndex = index
            bestPositionMs = boundedStartPosition(segment)
            bestWallClockMs = wallStart
            bestDistance = startDistance
        }
        val endDistance = abs(targetWallClockMs.toDouble() - wallEnd.toDouble())
        if (endDistance < bestDistance || (endDistance == bestDistance && wallEnd < bestWallClockMs)) {
            bestIndex = index
            bestPositionMs = boundedEndPosition(segment)
            bestWallClockMs = wallEnd
            bestDistance = endDistance
        }
    }
    return SleepRewindPoint(
        episodeId = segments[bestIndex].episodeId,
        positionMs = bestPositionMs,
        segmentIndex = bestIndex,
    )
}

/** Returns completed segment episode IDs after [point], in segment order and without duplicates. */
fun completedEpisodeIdsAfter(
    segments: List<SleepSegment>,
    point: SleepRewindPoint,
    completedSegmentIndexes: Set<Int>,
): List<String> {
    if (point.segmentIndex !in segments.indices) return emptyList()

    val result = ArrayList<String>()
    val seen = HashSet<String>()
    val currentSegment = segments[point.segmentIndex]
    val currentStart = boundedStartPosition(currentSegment)
    val currentEnd = boundedEndPosition(currentSegment)
    val currentPosition = point.positionMs.coerceIn(minOf(currentStart, currentEnd), maxOf(currentStart, currentEnd))
    for (index in point.segmentIndex..segments.lastIndex) {
        val segment = segments[index]
        if (index !in completedSegmentIndexes) continue
        if (index == point.segmentIndex && currentPosition == boundedEndPosition(segment)) continue
        if (seen.add(segment.episodeId)) result.add(segment.episodeId)
    }
    return result
}

enum class JourneyPlan {
    JUST_KEEP_GOING,
    ONE_STORY,
    TWO_STORIES,
    THIRTY_MINUTES,
    FORTY_FIVE_MINUTES,
}

data class JourneySelection(
    val episodeIds: List<String>,
    val estimatedRemainingDurationMs: Long,
)

/** Selects whole episodes in input order, using clamped, speed-adjusted remaining duration. */
fun selectJourneyPlan(
    orderedEpisodeIds: List<String>,
    durationsMs: Map<String, Long>,
    resumePositionsMs: Map<String, Long>,
    plan: JourneyPlan,
    playbackSpeed: Float = 1f,
): JourneySelection {
    if (orderedEpisodeIds.isEmpty()) return JourneySelection(emptyList(), 0L)

    val episodeLimit = when (plan) {
        JourneyPlan.ONE_STORY -> 1
        JourneyPlan.TWO_STORIES -> 2
        else -> Int.MAX_VALUE
    }
    val durationBudgetMs: Long? = when (plan) {
        JourneyPlan.THIRTY_MINUTES -> 30L * 60_000L
        JourneyPlan.FORTY_FIVE_MINUTES -> 45L * 60_000L
        else -> null
    }
    val selected = ArrayList<String>()
    val speed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
    var estimatedDurationMs = 0L
    for (episodeId in orderedEpisodeIds) {
        if (selected.size >= episodeLimit ||
            (durationBudgetMs != null && estimatedDurationMs >= durationBudgetMs)
        ) {
            break
        }
        selected.add(episodeId)
        val durationMs = durationsMs[episodeId] ?: 0L
        val remainingMs = if (durationMs > 0L) {
            durationMs - (resumePositionsMs[episodeId] ?: 0L).coerceIn(0L, durationMs)
        } else {
            0L
        }
        val listeningRemainingMs = (remainingMs / speed).toLong().coerceAtLeast(0L)
        estimatedDurationMs = if (Long.MAX_VALUE - estimatedDurationMs < listeningRemainingMs) {
            Long.MAX_VALUE
        } else {
            estimatedDurationMs + listeningRemainingMs
        }
    }
    return JourneySelection(selected, estimatedDurationMs)
}

/**
 * Start is inclusive and end exclusive; equal boundaries are an empty bedtime window.
 */
fun isInBedtimeWindow(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
    if (minuteOfDay !in 0 until 1_440 ||
        startMinute !in 0 until 1_440 ||
        endMinute !in 0 until 1_440
    ) {
        return false
    }
    if (startMinute == endMinute) return false
    return if (startMinute < endMinute) {
        minuteOfDay >= startMinute && minuteOfDay < endMinute
    } else {
        minuteOfDay >= startMinute || minuteOfDay < endMinute
    }
}

private fun boundedStartPosition(segment: SleepSegment): Long =
    segment.startPositionMs.coerceAtLeast(0L)

private fun boundedEndPosition(segment: SleepSegment): Long =
    segment.endPositionMs.coerceAtLeast(0L)

private fun positionAtWallClockTime(
    segment: SleepSegment,
    targetWallClockMs: Long,
    wallStartMs: Long,
    wallEndMs: Long,
): Long {
    val positionStartMs = boundedStartPosition(segment)
    val positionEndMs = boundedEndPosition(segment)
    if (wallStartMs == wallEndMs || positionStartMs == positionEndMs) return positionStartMs
    if (targetWallClockMs <= wallStartMs) return positionStartMs
    if (targetWallClockMs >= wallEndMs) return positionEndMs

    val fraction = (targetWallClockMs.toDouble() - wallStartMs.toDouble()) /
        (wallEndMs.toDouble() - wallStartMs.toDouble())
    val interpolatedPositionMs = (
        positionStartMs.toDouble() +
            (positionEndMs - positionStartMs).toDouble() * fraction
        ).roundToLong()
    return interpolatedPositionMs.coerceIn(minOf(positionStartMs, positionEndMs), maxOf(positionStartMs, positionEndMs))
}
