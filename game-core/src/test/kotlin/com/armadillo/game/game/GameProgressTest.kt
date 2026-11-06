package com.armadillo.game.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GameProgressTest {
    @Test
    fun teamTimeUsesAllRoundDurationsAndOnePenaltyPerRound() {
        val second = GameEngine.NANOS_PER_SECOND
        assertEquals(0f, teamTimeProgress(0, 120, 5), 0.0001f)
        assertEquals(0.5f, teamTimeProgress(325 * second, 120, 5), 0.0001f)
        assertEquals(1f, teamTimeProgress(650 * second, 120, 5), 0.0001f)
        assertEquals(1f, teamTimeProgress(700 * second, 120, 5), 0.0001f)
    }

    @Test
    fun teamTimeHandlesInvalidOrNegativeInputsWithoutOverflow() {
        assertEquals(0f, teamTimeProgress(-1, 120, 5), 0.0001f)
        assertEquals(0f, teamTimeProgress(1, 0, 5), 0.0001f)
        assertEquals(0f, teamTimeProgress(1, 120, 0), 0.0001f)
        assertEquals(1f, teamTimeProgress(Long.MAX_VALUE, 120, 5), 0.0001f)
    }

    @Test
    fun roundIndicatorsDistinguishPreparationPlayingAndPause() {
        assertEquals(RoundIndicator.UPCOMING, roundIndicator(1, 1, GamePhase.PREPARATION))
        assertEquals(RoundIndicator.ACTIVE, roundIndicator(1, 1, GamePhase.PLAYING))
        assertEquals(RoundIndicator.ACTIVE, roundIndicator(1, 1, GamePhase.MANUAL_PAUSED))
        assertEquals(RoundIndicator.COMPLETED, roundIndicator(1, 2, GamePhase.PREPARATION))
        assertEquals(RoundIndicator.UPCOMING, roundIndicator(2, 2, GamePhase.PREPARATION))
        assertEquals(RoundIndicator.ACTIVE, roundIndicator(2, 2, GamePhase.PLAYING))
        assertEquals(RoundIndicator.UPCOMING, roundIndicator(3, 2, GamePhase.PLAYING))
    }
}
