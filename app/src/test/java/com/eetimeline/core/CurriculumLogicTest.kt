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

    @Test
    fun selectsSleepRewindPointByWallClockTimeAndClampsToSession() {
        val segments = listOf(
            SleepSegment("first", 0L, 1_000L, 10_000L, 20_000L),
            SleepSegment("second", 500L, 2_500L, 20_000L, 30_000L),
        )

        assertEquals(
            SleepRewindPoint("first", 250L, 0),
            selectSleepRewindPoint(segments, 12_500L),
        )
        assertEquals(
            SleepRewindPoint("first", 0L, 0),
            selectSleepRewindPoint(segments, 5_000L),
        )
        assertEquals(
            SleepRewindPoint("second", 2_500L, 1),
            selectSleepRewindPoint(segments, 35_000L),
        )
        assertNull(selectSleepRewindPoint(emptyList(), 10_000L))
    }

    @Test
    fun rewindGapUsesNearestBoundaryAndHandlesZeroSpansAndInvalidPositions() {
        val segments = listOf(
            SleepSegment("before-gap", -10L, 100L, 0L, 10L),
            SleepSegment("zero-span", 50L, 20L, 30L, 30L),
            SleepSegment("after-gap", 20L, 120L, 40L, 50L),
        )

        assertEquals(
            SleepRewindPoint("before-gap", 100L, 0),
            selectSleepRewindPoint(segments, 20L),
        )
        assertEquals(
            SleepRewindPoint("zero-span", 50L, 1),
            selectSleepRewindPoint(segments, 30L),
        )
        assertEquals(
            SleepRewindPoint("before-gap", 0L, 0),
            selectSleepRewindPoint(segments, -5L),
        )
    }

    @Test
    fun duplicateSegmentsChooseFirstAndZeroPositionDurationStaysFixed() {
        val segments = listOf(
            SleepSegment("zero-duration", 100L, 100L, 100L, 200L),
            SleepSegment("duplicate", 0L, 1_000L, 100L, 200L),
        )

        assertEquals(
            SleepRewindPoint("zero-duration", 100L, 0),
            selectSleepRewindPoint(segments, 150L),
        )
    }

    @Test
    fun completedEpisodeIdsAfterRewindAreUniqueOrderedAndFiltered() {
        val segments = listOf(
            SleepSegment("a", 0L, 100L, 0L, 10L),
            SleepSegment("b", 0L, 100L, 10L, 20L),
            SleepSegment("a", 100L, 200L, 20L, 30L),
            SleepSegment("c", 0L, 100L, 30L, 40L),
        )
        val completedSegments = setOf(0, 1, 2, 3)

        assertEquals(
            listOf("a", "b", "c"),
            completedEpisodeIdsAfter(segments, SleepRewindPoint("a", 50L, 0), completedSegments),
        )
        assertEquals(
            listOf("b", "a", "c"),
            completedEpisodeIdsAfter(segments, SleepRewindPoint("a", 100L, 0), completedSegments),
        )
        assertEquals(
            listOf("b", "c"),
            completedEpisodeIdsAfter(
                segments,
                SleepRewindPoint("a", 50L, 0),
                setOf(1, 3),
            ),
        )
        assertTrue(
            completedEpisodeIdsAfter(segments, SleepRewindPoint("missing", 0L, 9), completedSegments).isEmpty(),
        )
    }

    @Test
    fun onlyCompletedSegmentsAfterTheSelectedPointAreUnmarked() {
        val segments = listOf(
            SleepSegment("already-completed", 0L, 100L, 0L, 10L),
            SleepSegment("already-completed", 100L, 150L, 10L, 20L),
            SleepSegment("completed-later", 0L, 100L, 20L, 30L),
        )

        assertEquals(
            listOf("completed-later"),
            completedEpisodeIdsAfter(
                segments,
                SleepRewindPoint("already-completed", 120L, 1),
                setOf(0, 2),
            ),
        )
    }

    @Test
    fun rewindPointInterpolatesBackwardSeekSegments() {
        val segment = SleepSegment("rewound", 1_000L, 0L, 0L, 100L)

        assertEquals(SleepRewindPoint("rewound", 500L, 0), selectSleepRewindPoint(listOf(segment), 50L))
    }

    @Test
    fun journeyPlansSelectWholeStoriesAndUseRemainingDurationBudgets() {
        val minute = 60_000L
        val ids = listOf("a", "b", "c", "d")
        val durations = ids.associateWith { 20L * minute }
        val resumes = mapOf("a" to 10L * minute)

        assertEquals(
            JourneySelection(ids, 70L * minute),
            selectJourneyPlan(ids, durations, resumes, JourneyPlan.JUST_KEEP_GOING),
        )
        assertEquals(
            JourneySelection(listOf("a"), 10L * minute),
            selectJourneyPlan(ids, durations, resumes, JourneyPlan.ONE_STORY),
        )
        assertEquals(
            JourneySelection(listOf("a", "b"), 30L * minute),
            selectJourneyPlan(ids, durations, resumes, JourneyPlan.TWO_STORIES),
        )
        assertEquals(
            JourneySelection(listOf("a", "b"), 30L * minute),
            selectJourneyPlan(ids, durations, resumes, JourneyPlan.THIRTY_MINUTES),
        )
        assertEquals(
            JourneySelection(listOf("a", "b", "c"), 50L * minute),
            selectJourneyPlan(ids, durations, resumes, JourneyPlan.FORTY_FIVE_MINUTES),
        )
        assertEquals(
            JourneySelection(listOf("a", "b", "c"), 30L * minute),
            selectJourneyPlan(
                ids,
                durations,
                emptyMap(),
                JourneyPlan.THIRTY_MINUTES,
                playbackSpeed = 2f,
            ),
        )
        assertEquals(
            JourneySelection(emptyList(), 0L),
            selectJourneyPlan(emptyList(), durations, resumes, JourneyPlan.ONE_STORY),
        )
    }

    @Test
    fun journeyPlansClampResumePositionsAndChooseAtLeastOneWholeEpisode() {
        assertEquals(
            JourneySelection(listOf("negative", "past-end", "unresumed"), 220L),
            selectJourneyPlan(
                orderedEpisodeIds = listOf("negative", "past-end", "unresumed"),
                durationsMs = mapOf("negative" to 120L, "past-end" to 100L, "unresumed" to 100L),
                resumePositionsMs = mapOf("negative" to -10L, "past-end" to 150L),
                plan = JourneyPlan.JUST_KEEP_GOING,
            ),
        )
        assertEquals(
            JourneySelection(listOf("long"), 60L * 60_000L),
            selectJourneyPlan(
                listOf("long", "next"),
                mapOf("long" to 60L * 60_000L, "next" to 1L),
                emptyMap(),
                JourneyPlan.THIRTY_MINUTES,
            ),
        )
    }

    @Test
    fun bedtimeWindowHandlesDayNightAndEqualBoundaries() {
        assertTrue(isInBedtimeWindow(21 * 60, 21 * 60, 6 * 60))
        assertTrue(isInBedtimeWindow(0, 21 * 60, 6 * 60))
        assertTrue(isInBedtimeWindow(5 * 60 + 59, 21 * 60, 6 * 60))
        assertFalse(isInBedtimeWindow(6 * 60, 21 * 60, 6 * 60))
        assertFalse(isInBedtimeWindow(12 * 60, 21 * 60, 6 * 60))

        assertTrue(isInBedtimeWindow(9 * 60, 9 * 60, 17 * 60))
        assertTrue(isInBedtimeWindow(16 * 60 + 59, 9 * 60, 17 * 60))
        assertFalse(isInBedtimeWindow(17 * 60, 9 * 60, 17 * 60))
        assertFalse(isInBedtimeWindow(8 * 60, 9 * 60, 17 * 60))

        assertFalse(isInBedtimeWindow(10, 300, 300))
        assertFalse(isInBedtimeWindow(1_440, 21 * 60, 6 * 60))
    }
}
