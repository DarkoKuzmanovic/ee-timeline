package com.eetimeline.core

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
