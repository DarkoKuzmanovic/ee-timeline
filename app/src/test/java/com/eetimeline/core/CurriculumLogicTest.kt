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
    fun mapsHistoricalAndModernYearsWithCompressedLeadInAndSafeBounds() {
        val minYear = -500
        val maxYear = 2025
        assertEquals(0f, timelineX(-500, minYear, maxYear), 0.0001f)
        assertEquals(0.08f, timelineX(1200, minYear, maxYear), 0.0001f)
        assertEquals(0.08f * (1300f / 1700f), timelineX(800, minYear, maxYear), 0.0001f)
        assertEquals(0.08f + 0.92f * (300f / 825f), timelineX(1500, minYear, maxYear), 0.0001f)
        assertEquals(1f, timelineX(2025, minYear, maxYear), 0.0001f)
        assertEquals(0f, timelineX(-1000, minYear, maxYear), 0.0001f)
        assertEquals(1f, timelineX(3000, minYear, maxYear), 0.0001f)

        assertEquals(0f, timelineX(1200, 1200, 2025), 0.0001f)
        assertEquals(300f / 825f, timelineX(1500, 1200, 2025), 0.0001f)
        assertEquals(1f, timelineX(2025, 2025, -500), 0.0001f)
        assertEquals(0.5f, timelineX(50, 50, 50), 0.0001f)
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
