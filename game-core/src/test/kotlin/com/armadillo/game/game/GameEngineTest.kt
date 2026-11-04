package com.armadillo.game.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private class FakeClock(var now: Long = 0L) {
        fun nanos(): Long = now
        fun seconds(value: Long) { now = value * GameEngine.NANOS_PER_SECOND }
    }

    private class TestWords(private val entries: List<String> = listOf("سیب", "کتاب", "باران")) : WordSource {
        override fun nextWord(
            categories: Set<String>,
            difficulty: Int,
            used: MutableSet<String>,
            legacyGeneralDifficulty: Int?
        ): String {
            var available = entries.filter { normalizeWord(it) !in used }
            if (available.isEmpty()) {
                used.clear()
                available = entries
            }
            return available.first()
        }
        override fun count(categories: Set<String>, difficulty: Int, legacyGeneralDifficulty: Int?): Int = entries.size
    }

    private fun newGame(clock: FakeClock, count: Int = 6, duration: Int = 90, rounds: Int = 3): GameEngine {
        val players = SeatPlan.createPlayers(count, List(count) { "بازیکن ${it + 1}" })
        val state = GameState(
            players = players,
            difficulty = 3,
            durationSeconds = duration,
            totalRounds = rounds,
            currentPlayerIndex = 0,
            nextRoundStarterIndex = 0,
            roundRemainingNanos = duration * GameEngine.NANOS_PER_SECOND,
            teamStats = MutableList(count / 2) { TeamStats() }
        )
        return GameEngine(state, TestWords(), clock::nanos)
    }

    @Test fun armadilloHasOnlyTopicCategoriesAndRequestedSetupValues() {
        assertFalse(WordCategories.GENERAL in WordCategories.all)
        assertEquals(listOf(120, 210, 300), GameConstants.allowedDurations.toList())
        assertEquals(listOf(3, 5, 7), GameConstants.allowedRounds.toList())
    }

    @Test fun legacyGeneralSaveMigratesToPlayableCategoryAndRefreshesCurrentWord() {
        val legacy = migrateSavedCategories(setOf(WordCategories.GENERAL))
        assertEquals(setOf(WordCategories.JOBS), legacy.selected)
        assertTrue(legacy.refreshCurrentWord)
        assertFalse(WordCategories.GENERAL in legacy.selected)

        val current = migrateSavedCategories(setOf(WordCategories.PLACES, WordCategories.TECHNOLOGY))
        assertEquals(setOf(WordCategories.PLACES, WordCategories.TECHNOLOGY), current.selected)
        assertFalse(current.refreshCurrentWord)
    }

    @Test fun categorySelectionIsCopiedIntoTheGameAndForwardedToItsWordSource() {
        val clock = FakeClock()
        val draftCategories = linkedSetOf(WordCategories.JOBS, WordCategories.FOODS)
        val players = SeatPlan.createPlayers(4, listOf("A", "B", "C", "D"))
        val state = GameState(
            players = players,
            difficulty = 2,
            durationSeconds = 90,
            totalRounds = 3,
            roundRemainingNanos = 90L * GameEngine.NANOS_PER_SECOND,
            teamStats = MutableList(2) { TeamStats() },
            selectedCategories = draftCategories.toSet()
        )
        draftCategories.clear()
        var received: Set<String>? = null
        val source = object : WordSource {
            override fun nextWord(categories: Set<String>, difficulty: Int, used: MutableSet<String>, legacyGeneralDifficulty: Int?): String {
                received = categories.toSet()
                return "واژه"
            }
            override fun count(categories: Set<String>, difficulty: Int, legacyGeneralDifficulty: Int?): Int = 100
        }
        assertTrue(GameEngine(state, source, clock::nanos).startRound())
        assertEquals(setOf(WordCategories.JOBS, WordCategories.FOODS), received)
        assertEquals(2, state.difficulty)
    }

    @Test fun teamLayoutsHaveOppositePartnersForAllSupportedPlayerCounts() {
        for (count in listOf(4, 6, 8, 10)) {
            val players = SeatPlan.createPlayers(count, List(count) { "P$it" })
            assertEquals(count / 2, players.map { it.teamIndex }.toSet().size)
            for (seat in 0 until count) {
                val opposite = (seat + count / 2) % count
                assertTrue(SeatPlan.areOppositeTeamMates(seat, opposite, count))
                assertEquals(players[seat].teamIndex, players[opposite].teamIndex)
                assertEquals((seat + 1) % count, SeatPlan.nextSeat(seat, count))
            }
        }
    }

    @Test fun missingNamesAreUniqueAndUserDuplicatesRemainAllowed() {
        val players = SeatPlan.createPlayers(4, listOf("", "بازیکن 1", "ندا", "ندا"))
        assertEquals("بازیکن 2", players[0].name)
        assertEquals("بازیکن 1", players[1].name)
        assertEquals("ندا", players[2].name)
        assertEquals("ندا", players[3].name)
    }

    @Test fun wordHistoryStoresDisplayTimePlayerRoundAndOutcome() {
        val clock = FakeClock()
        val players = SeatPlan.createPlayers(4, listOf("آوا", "آرمان", "نیما", "رها"))
        val state = GameState(
            players = players,
            difficulty = 1,
            durationSeconds = 120,
            totalRounds = 3,
            roundRemainingNanos = 120L * GameEngine.NANOS_PER_SECOND,
            teamStats = MutableList(2) { TeamStats() }
        )
        var wallTime = 1_800_000_000_000L
        val game = GameEngine(state, TestWords(), clock::nanos, wallClockMillis = { wallTime.also { wallTime += 1_000L } })
        assertTrue(game.startRound())
        assertEquals(1_800_000_000_000L, game.state.wordHistory.single().shownAtEpochMillis)
        assertEquals(0, game.state.wordHistory.single().playerSeat)
        assertEquals(1, game.state.wordHistory.single().round)

        clock.seconds(5)
        assertTrue(game.markCorrect().accepted)
        assertEquals(WordOutcome.CORRECT, game.state.wordHistory[0].outcome)
        assertEquals(1, game.state.wordHistory[1].playerSeat)
        assertEquals(1_800_000_001_000L, game.state.wordHistory[1].shownAtEpochMillis)

        game.finishEarly()
        assertEquals(WordOutcome.ENDED, game.state.wordHistory[1].outcome)
    }

    @Test fun correctAnswerTransfersTheClockAtOneMonotonicInstant() {
        val clock = FakeClock()
        val game = newGame(clock)
        assertTrue(game.startRound())
        clock.seconds(5)
        assertTrue(game.markCorrect().accepted)
        assertEquals(5L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        assertEquals(1, game.state.teamStats[0].correctWords)
        assertEquals(1, game.state.currentPlayerIndex)
        clock.seconds(8)
        game.tick()
        assertEquals(3L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[1].realTimeNanos)
        assertEquals(82L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
    }

    @Test fun pauseFreezesCountdownAndTeamTimeAndResumeUsesRemainingTime() {
        val clock = FakeClock()
        val game = newGame(clock)
        game.startRound()
        clock.seconds(7)
        assertTrue(game.pauseManually().accepted)
        assertEquals(7L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        assertEquals(83L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
        clock.seconds(100)
        game.resumeManually()
        clock.seconds(103)
        game.tick()
        assertEquals(10L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        assertEquals(80L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
    }

    @Test fun manualPausePreservesTheCurrentWordPlayerAndUsedWordHistory() {
        val clock = FakeClock()
        val game = newGame(clock, duration = 120)
        assertTrue(game.startRound())
        val word = game.state.currentWord
        val player = game.state.currentPlayerIndex
        val usedWords = game.state.usedWords.toSet()
        val history = game.state.wordHistory.toList()
        clock.seconds(9)

        assertTrue(game.pauseManually().accepted)
        assertEquals(GamePhase.MANUAL_PAUSED, game.state.phase)
        assertEquals(word, game.state.currentWord)
        assertEquals(player, game.state.currentPlayerIndex)
        assertEquals(usedWords, game.state.usedWords)
        assertEquals(history, game.state.wordHistory)
        assertEquals(111L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)

        clock.seconds(200)
        assertEquals(EngineEvent.NONE, game.tick())
        assertEquals(111L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
        assertTrue(game.resumeManually())
        assertFalse(game.resumeManually())
        assertEquals(GamePhase.PLAYING, game.state.phase)
        assertEquals(word, game.state.currentWord)
        assertEquals(player, game.state.currentPlayerIndex)
        clock.seconds(202)
        game.tick()
        assertEquals(11L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        assertEquals(109L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
        assertEquals(word, game.state.currentWord)
    }

    @Test fun repeatedManualPauseResumePreservesPlayerWordHistoryAndRemainingTime() {
        val clock = FakeClock()
        val game = newGame(clock, duration = 120)
        assertTrue(game.startRound())
        val word = game.state.currentWord
        val player = game.state.currentPlayerIndex
        val usedWords = game.state.usedWords.toSet()
        val history = game.state.wordHistory.toList()

        clock.seconds(3)
        assertTrue(game.pauseManually().accepted)
        assertEquals(117L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
        clock.seconds(80)
        assertEquals(EngineEvent.NONE, game.tick())
        assertEquals(117L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)

        assertTrue(game.resumeManually())
        clock.seconds(84)
        game.tick()
        clock.seconds(86)
        assertTrue(game.pauseManually().accepted)
        assertEquals(111L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
        clock.seconds(120)
        assertEquals(EngineEvent.NONE, game.tick())
        assertEquals(111L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)

        assertTrue(game.resumeManually())
        clock.seconds(124)
        game.tick()
        assertEquals(GamePhase.PLAYING, game.state.phase)
        assertEquals(107L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
        assertEquals(13L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        assertEquals(word, game.state.currentWord)
        assertEquals(player, game.state.currentPlayerIndex)
        assertEquals(usedWords, game.state.usedWords)
        assertEquals(history, game.state.wordHistory)
    }

    @Test fun manuallyPausedGameReturnsToManualPauseAfterSystemInterruption() {
        val clock = FakeClock()
        val game = newGame(clock, duration = 120)
        assertTrue(game.startRound())
        val word = game.state.currentWord
        val player = game.state.currentPlayerIndex
        val history = game.state.wordHistory.toList()

        clock.seconds(5)
        assertTrue(game.pauseManually().accepted)
        assertEquals(EngineEvent.NONE, game.pauseForSystem())
        assertEquals(GamePhase.SYSTEM_PAUSED, game.state.phase)
        assertEquals(GamePhase.MANUAL_PAUSED, game.state.pausedFrom)

        clock.seconds(200)
        assertTrue(game.resumeFromSystemPause())
        assertEquals(GamePhase.MANUAL_PAUSED, game.state.phase)
        assertEquals(word, game.state.currentWord)
        assertEquals(player, game.state.currentPlayerIndex)
        assertEquals(history, game.state.wordHistory)
        assertEquals(115L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)

        clock.seconds(205)
        assertEquals(EngineEvent.NONE, game.tick())
        assertEquals(115L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
        assertTrue(game.resumeManually())
        clock.seconds(208)
        game.tick()
        assertEquals(8L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        assertEquals(112L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
        assertEquals(word, game.state.currentWord)
        assertEquals(player, game.state.currentPlayerIndex)
        assertEquals(history, game.state.wordHistory)
    }

    @Test fun preparationPauseDoesNotStartCountdownOrLoseSetup() {
        val clock = FakeClock()
        val game = newGame(clock, duration = 120)
        val players = game.state.players.toList()
        val round = game.state.currentRound
        clock.seconds(12)

        assertEquals(EngineEvent.NONE, game.pauseForSystem())
        assertEquals(GamePhase.SYSTEM_PAUSED, game.state.phase)
        assertEquals(GamePhase.PREPARATION, game.state.pausedFrom)
        assertEquals(120L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)

        clock.seconds(250)
        assertTrue(game.resumeFromSystemPause())
        assertEquals(GamePhase.PREPARATION, game.state.phase)
        assertEquals(players, game.state.players)
        assertEquals(round, game.state.currentRound)
        assertEquals(120L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
        assertEquals(null, game.state.currentWord)
        assertTrue(game.startRound())
        clock.seconds(252)
        game.tick()
        assertEquals(118L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
    }

    @Test fun systemPauseDoesNotCountTimeOutsideTheApp() {
        val clock = FakeClock()
        val game = newGame(clock)
        game.startRound()
        clock.seconds(4)
        game.pauseForSystem()
        assertEquals(GamePhase.SYSTEM_PAUSED, game.state.phase)
        clock.seconds(200)
        game.resumeFromSystemPause()
        assertEquals(GamePhase.PLAYING, game.state.phase)
        clock.seconds(202)
        game.tick()
        assertEquals(6L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        assertEquals(84L * GameEngine.NANOS_PER_SECOND, game.state.roundRemainingNanos)
    }

    @Test fun swappingRequiresSevenActiveSecondsAndCountsAsUsedNotCorrect() {
        val clock = FakeClock()
        val game = newGame(clock)
        game.startRound()
        val first = game.state.currentWord
        clock.now = 6_999_000_000L
        assertFalse(game.swapWord().accepted)
        assertEquals(0, game.state.teamStats[0].swaps)
        clock.now = 7_000_000_000L
        assertTrue(game.swapWord().accepted)
        assertEquals(WordOutcome.SWAPPED, game.state.wordHistory[0].outcome)
        assertEquals(WordOutcome.PENDING, game.state.wordHistory[1].outcome)
        assertEquals(1, game.state.teamStats[0].swaps)
        assertEquals(0, game.state.teamStats[0].correctWords)
        assertEquals(2, game.state.usedWords.size)
        assertTrue(game.state.currentWord != first)
        assertFalse(game.canSwap())
    }

    @Test fun swapCooldownFreezesDuringPauseAndContinuesAfterResume() {
        val clock = FakeClock()
        val game = newGame(clock)
        game.startRound()
        clock.seconds(3)
        game.tick()
        assertFalse(game.canSwap())
        game.pauseManually()
        clock.seconds(100)
        game.resumeManually()
        clock.seconds(103)
        game.tick()
        assertFalse(game.canSwap())
        clock.seconds(104)
        assertTrue(game.swapWord().accepted)
        assertEquals(7L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
    }

    @Test fun expirationAddsTenSecondsAndNextHandStartsWithTheHolder() {
        val clock = FakeClock()
        val game = newGame(clock, count = 4, duration = 1, rounds = 2)
        game.startRound()
        clock.now = 1_400_000_000L // delayed UI tick must not charge the extra 400 ms
        assertEquals(EngineEvent.EXPLOSION_STARTED, game.tick())
        assertEquals(GamePhase.EXPLODING, game.state.phase)
        assertEquals(WordOutcome.EXPIRED, game.state.wordHistory.single().outcome)
        assertEquals(1L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        assertEquals(10L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].penaltyNanos)
        assertEquals(1, game.state.teamStats[0].explosions)
        clock.seconds(5)
        assertEquals(EngineEvent.NONE, game.tick())
        assertEquals(1L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        game.finishExplosion()
        assertEquals(GamePhase.PREPARATION, game.state.phase)
        assertEquals(2, game.state.currentRound)
        assertEquals(0, game.state.currentPlayerIndex)
        assertEquals(1L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
    }

    @Test fun interruptionDuringExplosionKeepsItPendingUntilAcknowledged() {
        val clock = FakeClock()
        val game = newGame(clock, count = 4, duration = 1, rounds = 2)
        game.startRound()
        clock.seconds(1)
        assertEquals(EngineEvent.EXPLOSION_STARTED, game.tick())
        val penaltyBeforePause = game.state.teamStats[0].penaltyNanos
        game.pauseForSystem()
        assertEquals(GamePhase.SYSTEM_PAUSED, game.state.phase)
        clock.seconds(100)
        game.resumeFromSystemPause()
        assertEquals(GamePhase.EXPLODING, game.state.phase)
        assertEquals(1, game.state.currentRound)
        assertEquals(penaltyBeforePause, game.state.teamStats[0].penaltyNanos)
        assertEquals(EngineEvent.NONE, game.finishExplosion())
        assertEquals(GamePhase.PREPARATION, game.state.phase)
        assertEquals(2, game.state.currentRound)
        assertEquals(1L * GameEngine.NANOS_PER_SECOND, game.state.teamStats[0].realTimeNanos)
        assertEquals(EngineEvent.NONE, game.finishExplosion())
        assertEquals(2, game.state.currentRound)
    }

    @Test fun expiryDuringSystemPauseStillRequiresExplosionAcknowledgement() {
        val clock = FakeClock()
        val game = newGame(clock, count = 4, duration = 1, rounds = 2)
        game.startRound()
        clock.seconds(1)
        assertEquals(EngineEvent.EXPLOSION_STARTED, game.pauseForSystem())
        assertEquals(GamePhase.SYSTEM_PAUSED, game.state.phase)
        assertEquals(GamePhase.EXPLODING, game.state.pausedFrom)
        clock.seconds(100)
        game.resumeFromSystemPause()
        assertEquals(GamePhase.EXPLODING, game.state.phase)
        assertEquals(1, game.state.currentRound)
        assertEquals(EngineEvent.NONE, game.finishExplosion())
        assertEquals(GamePhase.PREPARATION, game.state.phase)
    }

    @Test fun wordPoolDoesNotRepeatUntilExhaustedThenStartsANewCycle() {
        val clock = FakeClock()
        val source = TestWords(listOf("یک", "دو"))
        val players = SeatPlan.createPlayers(4, listOf("A", "B", "C", "D"))
        val state = GameState(
            players = players, difficulty = 1,
            durationSeconds = 90, totalRounds = 3, roundRemainingNanos = 90L * GameEngine.NANOS_PER_SECOND,
            teamStats = MutableList(2) { TeamStats() }
        )
        val game = GameEngine(state, source, clock::nanos)
        game.startRound()
        val first = game.state.currentWord
        clock.seconds(1)
        game.markCorrect()
        val second = game.state.currentWord
        assertTrue(first != second)
        clock.seconds(2)
        game.markCorrect()
        assertEquals(1, game.state.usedWords.size) // a new cycle begins only after both entries are used
        assertTrue(game.state.currentWord == first || game.state.currentWord == second)
    }

    @Test fun rankingUsesExactTimeThenSpecifiedTiebreakersAndSharesFullTies() {
        val a = TeamStats(realTimeNanos = 10_000_000_001L, correctWords = 4)
        val b = TeamStats(realTimeNanos = 10_000_000_002L, correctWords = 99)
        val c = TeamStats(realTimeNanos = 12_000_000_000L, explosions = 1, correctWords = 1)
        val ranked = rankTeams(listOf(a, b, c))
        assertEquals(0, ranked[0].teamIndex) // exact nanoseconds win; rounded seconds would tie
        assertEquals(1, ranked[1].teamIndex)
        assertEquals(2, ranked[2].teamIndex)

        val tied = rankTeams(listOf(TeamStats(realTimeNanos = 5), TeamStats(realTimeNanos = 5)))
        assertEquals(1, tied[0].rank)
        assertEquals(1, tied[1].rank)

        val byRules = rankTeams(listOf(
            TeamStats(realTimeNanos = 1_000_000_000L, correctWords = 2, swaps = 4),
            TeamStats(realTimeNanos = 1_000_000_000L, explosions = 1, correctWords = 99),
            TeamStats(realTimeNanos = 1_000_000_000L, correctWords = 3, swaps = 99),
            TeamStats(realTimeNanos = 1_000_000_000L, correctWords = 3, swaps = 1)
        ))
        assertEquals(listOf(3, 2, 0, 1), byRules.map { it.teamIndex })
    }
}
