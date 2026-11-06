package com.armadillo.game.util

import com.armadillo.game.game.GameConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSetupMathTest {
    @Test fun configuredOptionsAreReturnedUnchanged() {
        for (seconds in GameConstants.allowedDurations) assertEquals(seconds, GameSetupMath.nearestAllowedDuration(seconds))
        for (rounds in GameConstants.allowedRounds) assertEquals(rounds, GameSetupMath.nearestAllowedRounds(rounds))
    }

    @Test fun legacyOrOutOfRangeValuesSnapToTheNearestAllowedOption() {
        assertEquals(120, GameSetupMath.nearestAllowedDuration(90))
        assertEquals(120, GameSetupMath.nearestAllowedDuration(-500))
        assertEquals(300, GameSetupMath.nearestAllowedDuration(9999))
        assertEquals(210, GameSetupMath.nearestAllowedDuration(200))
        assertEquals(3, GameSetupMath.nearestAllowedRounds(1))
        assertEquals(7, GameSetupMath.nearestAllowedRounds(42))
        assertTrue(GameSetupMath.nearestAllowedDuration(Int.MAX_VALUE) in GameConstants.allowedDurations)
    }
}
