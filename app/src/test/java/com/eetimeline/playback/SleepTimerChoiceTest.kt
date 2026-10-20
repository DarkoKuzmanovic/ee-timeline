package com.eetimeline.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerChoiceTest {
    @Test
    fun onlyUserSelectableDurationsAreDecodedAsRememberedChoices() {
        assertEquals(
            listOf(
                SleepTimerChoice.OFF,
                SleepTimerChoice.MINUTES_15,
                SleepTimerChoice.MINUTES_30,
                SleepTimerChoice.MINUTES_45,
                SleepTimerChoice.MINUTES_60,
                SleepTimerChoice.END_OF_CURRENT_ITEM,
            ),
            SleepTimerChoice.values().toList(),
        )
        assertEquals(SleepTimerChoice.OFF, SleepTimerChoice.fromStoredValue(1))
        assertEquals(SleepTimerChoice.OFF, SleepTimerChoice.fromStoredValue(10))
    }
}
