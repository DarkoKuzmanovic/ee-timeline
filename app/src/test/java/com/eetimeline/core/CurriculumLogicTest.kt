package com.eetimeline.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumLogicTest {
    private val curriculum: Curriculum by lazy {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("curriculum-fixture.json"))
        CurriculumParser.parse(stream.bufferedReader().use { it.readText() })
    }

    @Test
    fun parsesRealCurriculumAssetWithAllJourneyEpisodesAndAudioUrls() {
        val asset = java.io.File("src/main/assets/curriculum.json")
        val parsed = CurriculumParser.parse(asset.readText())
        val journey = parsed.orderedEpisodeIds()

        assertEquals(235, journey.size)
        assertEquals(235, journey.toSet().size)
        assertTrue(journey.all { id -> !parsed.episodes[id]?.audioUrl.isNullOrBlank() })
    }

    @Test
    fun parsesFixtureAndPreservesNullableAndHistoricalFields() {
        assertEquals("https://everything-everywhere.com/", curriculum.source)
        assertEquals(listOf("ancient-event", "pre-1200-event", "modern-event"), curriculum.orderedEpisodeIds())
        assertEquals(-500, curriculum.episodes.getValue("ancient-event").approxYear)
        assertEquals(800, curriculum.episodes.getValue("pre-1200-event").approxYear)
        assertNull(curriculum.units.single().topics.first().detail)
        assertTrue(curriculum.units.single().topics[1].groups.isEmpty())
    }

    @Test
    fun missingBlankAndMalformedAssetsReturnEmptyWithoutHidingValidData() {
        assertEquals(Curriculum(), CurriculumParser.parse(null))
        assertEquals(Curriculum(), CurriculumParser.parse("  \n\t"))
        assertEquals(Curriculum(), CurriculumParser.parse("{ malformed"))
        assertEquals("https://everything-everywhere.com/", curriculum.source)
        assertEquals(3, curriculum.orderedEpisodeIds().size)
    }

    @Test
    fun selectsFirstAndNextUnplayedInJourneyOrderWithoutWrapping() {
        assertEquals("ancient-event", curriculum.firstUnplayed(emptySet()))
        assertEquals(
            "modern-event",
            curriculum.firstUnplayed(setOf("ancient-event", "pre-1200-event")),
        )
        assertNull(curriculum.firstUnplayed(setOf("ancient-event", "pre-1200-event", "modern-event")))

        assertEquals(
            "modern-event",
            curriculum.nextUnplayedAfter("ancient-event", setOf("ancient-event", "pre-1200-event")),
        )
        assertNull(curriculum.nextUnplayedAfter("modern-event", emptySet()))
        assertNull(curriculum.nextUnplayedAfter("not-in-journey", emptySet()))
    }

    @Test
    fun densityBalancedTimelineIsMonotonicAndClampsOutsideItsDomain() {
        val scale = timelineScale(listOf(-9_000, -500, 800, 1_250, 1_500, 1_800, 1_950), 2_025)
        var previous = timelineX(-9_000, scale)

        for (year in -8_999..2_025) {
            val x = timelineX(year, scale)
            assertTrue("Timeline must be monotonic at year $year", x >= previous)
            previous = x
        }

        assertEquals(0f, timelineX(-12_000, scale), 0.0001f)
        assertEquals(1f, timelineX(3_000, scale), 0.0001f)
        assertEquals(0f, timelineX(-9_000, scale), 0.0001f)
        assertEquals(1f, timelineX(2_025, scale), 0.0001f)
    }

    @Test
    fun densityBalancedTimelinePlacesKnotsByEpisodeCounts() {
        val episodeYears = listOf(
            -8_000, -4_000, -1_000, 1, 500, 1_000, 1_199, 1_199,
            1_200, 1_449,
            1_450, 1_600, 1_749,
            1_750,
            1_900, 1_950, 2_000, 2_025, 2_025,
        )
        val knotYears = listOf(-8_000, -3_000, 1, 1_000, 1_200, 1_450, 1_750, 1_900, 2_025)
        val segmentCounts = intArrayOf(2, 1, 2, 3, 2, 3, 1, 5)
        val scale = timelineScale(episodeYears, 2_025)
        val distributableWidth = 1f - 0.07f * segmentCounts.size
        var expectedPosition = 0f

        knotYears.forEachIndexed { index, year ->
            assertEquals(expectedPosition, timelineX(year, scale), 0.0001f)
            if (index < segmentCounts.size) {
                expectedPosition += 0.07f + distributableWidth * segmentCounts[index] / episodeYears.size
            }
        }
    }

    @Test
    fun densityBalancedTimelineHasMinimumWidthAndContinuousKnotBoundaries() {
        val scale = timelineScale(listOf(-8_000, 2_025), 2_025)
        val knotYears = listOf(-8_000, -3_000, 1, 1_000, 1_200, 1_450, 1_750, 1_900, 2_025)
        val knotPositions = knotYears.map { timelineX(it, scale) }

        for (index in 0 until knotPositions.lastIndex) {
            assertTrue(knotPositions[index + 1] - knotPositions[index] >= 0.0699f)
        }
        for (year in knotYears.drop(1).dropLast(1)) {
            assertTrue(timelineX(year - 1, scale) < timelineX(year, scale))
            assertTrue(timelineX(year, scale) < timelineX(year + 1, scale))
        }
        assertTrue(timelineX(1_200, scale) - timelineX(1_000, scale) >= 0.0699f)
    }

    @Test
    fun appliesPlayedThresholdAndSleepTimerFade() {
        assertFalse(isPlayedByPosition(durationMs = 100_000L, positionMs = 69_999L))
        assertTrue(isPlayedByPosition(durationMs = 100_000L, positionMs = 70_000L))
        assertTrue(isPlayedByPosition(durationMs = 100_000L, positionMs = 100_000L))
        assertTrue(isPlayedByPosition(durationMs = 20_000L, positionMs = 0L))
        assertFalse(isPlayedByPosition(durationMs = 0L, positionMs = 0L))
        assertFalse(isPlayedByPosition(durationMs = -1L, positionMs = 0L))

        assertEquals(1f, sleepFadeVolume(30_000L), 0.0001f)
        assertEquals(0.5f, sleepFadeVolume(15_000L), 0.0001f)
        assertEquals(0f, sleepFadeVolume(0L), 0.0001f)
        assertEquals(0f, sleepFadeVolume(-1L), 0.0001f)
        assertEquals(1f, sleepFadeVolume(60_000L), 0.0001f)
    }
}
