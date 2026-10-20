package com.eetimeline.core

private const val PRE_1200_FRACTION = 0.08
private const val MODERN_ERA_START = 1200
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

/** Maps [year] to [0, 1], compressing years before 1200 into the first 8% when bounds cross 1200. */
fun timelineX(year: Int, minYear: Int, maxYear: Int): Float {
    val low = minOf(minYear, maxYear)
    val high = maxOf(minYear, maxYear)
    if (low == high) return 0.5f

    val clampedYear = year.coerceIn(low, high).toDouble()
    val lowYear = low.toDouble()
    val highYear = high.toDouble()
    val epoch = MODERN_ERA_START.toDouble()

    val x = if (low < MODERN_ERA_START && high > MODERN_ERA_START) {
        if (clampedYear < epoch) {
            PRE_1200_FRACTION * ((clampedYear - lowYear) / (epoch - lowYear))
        } else {
            PRE_1200_FRACTION +
                (1.0 - PRE_1200_FRACTION) * ((clampedYear - epoch) / (highYear - epoch))
        }
    } else {
        (clampedYear - lowYear) / (highYear - lowYear)
    }
    return x.toFloat()
}

/** True once playback is within 30 seconds of the end of a positive-duration episode. */
fun isPlayedByPosition(durationMs: Long, positionMs: Long): Boolean =
    durationMs > 0L && positionMs >= durationMs - PLAYED_THRESHOLD_MS

/** Linear volume multiplier over the final 30 seconds of a sleep timer. */
fun sleepFadeVolume(remainingMs: Long): Float =
    remainingMs.coerceIn(0L, SLEEP_FADE_DURATION_MS).toFloat() / SLEEP_FADE_DURATION_MS.toFloat()
