package com.armadillo.game.data

import com.armadillo.game.game.GameConstants
import com.armadillo.game.game.GameEngine
import com.armadillo.game.game.GamePhase
import com.armadillo.game.game.GameState
import com.armadillo.game.game.Player
import com.armadillo.game.game.TeamStats
import com.armadillo.game.game.WordCategories
import com.armadillo.game.game.WordHistoryEntry
import com.armadillo.game.game.WordOutcome
import com.armadillo.game.game.WordSource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the checkpoint format that protects a player's unfinished game.
 * These rules previously had no automated coverage at all.
 */
class GameCodecTest {

    private fun state(): GameState = GameState(
        players = MutableList(4) { index -> Player(seat = index, name = "بازیکن ${index + 1}", teamIndex = index % 2) },
        difficulty = 2,
        durationSeconds = 210,
        totalRounds = 5,
        currentRound = 3,
        phase = GamePhase.PLAYING,
        currentPlayerIndex = 2,
        nextRoundStarterIndex = 1,
        roundRemainingNanos = 90 * GameEngine.NANOS_PER_SECOND,
        swapCooldownNanos = 0L,
        currentWord = "چراغ",
        usedWords = linkedSetOf("چراغ", "میز"),
        wordHistory = mutableListOf(
            WordHistoryEntry("میز", playerSeat = 1, round = 2, shownAtEpochMillis = 1_700_000_000_000L, outcome = WordOutcome.CORRECT),
            WordHistoryEntry("چراغ", playerSeat = 2, round = 3, shownAtEpochMillis = 0L, outcome = WordOutcome.PENDING)
        ),
        teamStats = MutableList(2) { TeamStats(realTimeNanos = 100L, penaltyNanos = 10L, correctWords = 4, swaps = 1, explosions = 1) },
        selectedCategories = linkedSetOf(WordCategories.ANIMALS, WordCategories.PLACES)
    )

    @Test
    fun roundTripKeepsEveryPlayedField() {
        val original = state()
        val decoded = GameCodec.decode(GameCodec.encode(original))

        assertEquals(original.id, decoded.id)
        assertEquals(original.difficulty, decoded.difficulty)
        assertEquals(original.durationSeconds, decoded.durationSeconds)
        assertEquals(original.totalRounds, decoded.totalRounds)
        assertEquals(original.currentRound, decoded.currentRound)
        assertEquals(GamePhase.PLAYING, decoded.phase)
        assertEquals(original.currentPlayerIndex, decoded.currentPlayerIndex)
        assertEquals(original.nextRoundStarterIndex, decoded.nextRoundStarterIndex)
        assertEquals(original.roundRemainingNanos, decoded.roundRemainingNanos)
        assertEquals(original.currentWord, decoded.currentWord)
        assertEquals(original.usedWords, decoded.usedWords)
        assertEquals(original.selectedCategories, decoded.selectedCategories)
        assertEquals(original.players.map { it.name }, decoded.players.map { it.name })
        assertEquals(original.wordHistory.map { it.word }, decoded.wordHistory.map { it.word })
        assertEquals(original.wordHistory.map { it.outcome }, decoded.wordHistory.map { it.outcome })
        assertEquals(original.teamStats.map { it.realTimeNanos }, decoded.teamStats.map { it.realTimeNanos })
        assertEquals(original.teamStats.map { it.explosions }, decoded.teamStats.map { it.explosions })
    }

    @Test
    fun legacyGeneralSaveWithoutCategoriesMigratesToJobsAndRetiresItsWord() {
        val legacy = JSONObject()
            .put("id", "legacy")
            .put("difficulty", 3)
            .put("durationSeconds", 180)
            .put("totalRounds", 5)
            .put("currentRound", 2)
            .put("phase", GamePhase.PLAYING.name)
            .put("currentPlayerIndex", 0)
            .put("currentWord", "واژهٔ عمومی")
            .put("players", playersJson(4))
            .put("teamStats", teamStatsJson(2))

        val decoded = GameCodec.decode(legacy)

        assertEquals(setOf(WordCategories.JOBS), decoded.selectedCategories)
        // A five-level legacy difficulty of 3 maps onto the middle topic tier.
        assertEquals(2, decoded.difficulty)
        // The retired word cannot stay on screen, but it must not vanish from the results.
        assertNull(decoded.currentWord)
        assertEquals(1, decoded.wordHistory.count { it.word == "واژهٔ عمومی" && it.outcome == WordOutcome.ENDED })
        assertTrue(decoded.usedWords.isEmpty())
    }

    @Test
    fun unknownCategoryIsDroppedAndWordIsRefreshedInsteadOfCrashing() {
        val saved = JSONObject()
            .put("schema", GameCodec.SCHEMA)
            .put("difficulty", 1)
            .put("durationSeconds", 120)
            .put("totalRounds", 3)
            .put("currentRound", 1)
            .put("phase", GamePhase.PLAYING.name)
            .put("currentWord", "هندبال")
            .put("selectedCategories", org.json.JSONArray().put("sports"))
            .put("players", playersJson(4))
            .put("teamStats", teamStatsJson(2))

        val decoded = GameCodec.decode(saved)

        assertEquals(setOf(WordCategories.JOBS), decoded.selectedCategories)
        assertNull(decoded.currentWord)
        assertEquals(WordOutcome.ENDED, decoded.wordHistory.single().outcome)
    }

    @Test
    fun outOfRangeValuesAreClampedInsteadOfPropagating() {
        val saved = JSONObject()
            .put("difficulty", 9)
            .put("durationSeconds", 9999)
            .put("totalRounds", 42)
            .put("currentRound", 99)
            .put("phase", GamePhase.PLAYING.name)
            .put("currentPlayerIndex", 17)
            .put("roundRemainingNanos", 999L)
            .put("swapCooldownNanos", 999_999_999_999L)
            .put("selectedCategories", org.json.JSONArray().put(WordCategories.FOODS))
            .put("players", playersJson(4))
            .put("teamStats", teamStatsJson(2))

        val decoded = GameCodec.decode(saved)

        assertTrue(decoded.difficulty in 1..3)
        assertTrue(decoded.currentPlayerIndex in 0..3)
        assertTrue(decoded.currentRound in 1..(decoded.totalRounds + 1))
        assertTrue(decoded.roundRemainingNanos <= decoded.durationSeconds * GameEngine.NANOS_PER_SECOND)
        assertTrue(decoded.swapCooldownNanos <= GameConstants.SWAP_LOCK_NANOS)
        assertTrue(decoded.totalRounds >= 3)
    }

    @Test
    fun schemaThreeCooldownMigratesFromSevenSecondsWithoutLosingElapsedTime() {
        val saved = GameCodec.encode(state().copy(phase = GamePhase.SYSTEM_PAUSED))
            .put("schema", 3)
        for ((oldRemaining, newRemaining) in listOf(7L to 5L, 6L to 4L, 2L to 0L, 0L to 0L)) {
            saved.put("swapCooldownNanos", oldRemaining * GameEngine.NANOS_PER_SECOND)
            val decoded = GameCodec.decode(saved)
            assertEquals(newRemaining * GameEngine.NANOS_PER_SECOND, decoded.swapCooldownNanos)
            assertEquals(GamePhase.SYSTEM_PAUSED, decoded.phase)
        }
    }

    @Test
    fun currentSchemaPreservesFiveSecondCooldownWithoutRemigration() {
        val original = state().copy(swapCooldownNanos = 4L * GameEngine.NANOS_PER_SECOND)
        val decoded = GameCodec.decode(GameCodec.encode(original))
        assertEquals(original.swapCooldownNanos, decoded.swapCooldownNanos)
    }

    @Test
    fun encodedPayloadCarriesTheCurrentSchemaVersion() {
        val encoded = GameCodec.encode(state())
        assertEquals(GameCodec.SCHEMA, encoded.getInt("schema"))
    }

    @Test(expected = org.json.JSONException::class)
    fun corruptPayloadFailsFastSoTheStoreCanDiscardItSafely() {
        GameCodec.decode(JSONObject("{\"players\":\"broken\"}"))
    }

    @Test
    fun storedPlayersWithoutNamesUseTheInjectedLabeller() {
        val json = GameCodec.encode(state())
        val players = json.getJSONArray("players")
        repeat(players.length()) { players.getJSONObject(it).remove("name") }
        val decoded = GameCodec.decode(json) { "بازیکن $it" }
        assertEquals((1..players.length()).map { "بازیکن $it" }, decoded.players.map { it.name })
    }

    @Test
    fun finishedGameFlagsAreResetByExplicitPhaseOnly() {
        val results = state().copy(phase = GamePhase.RESULTS)
        assertFalse(GameCodec.decode(GameCodec.encode(results)).phase == GamePhase.PLAYING)
    }

    @Test
    fun malformedSeatAndTeamIndicesAreClampedInsteadOfCrashingTheEngine() {
        val saved = JSONObject()
            .put("difficulty", 1)
            .put("durationSeconds", 120)
            .put("totalRounds", 3)
            .put("currentRound", 1)
            .put("phase", GamePhase.PLAYING.name)
            .put("currentWord", "چراغ")
            .put("selectedCategories", org.json.JSONArray().put(WordCategories.JOBS))
            .put("players", playersJson(4).apply {
                getJSONObject(0).put("seat", 99).put("team", 99)
            })
            .put("teamStats", teamStatsJson(2))

        val decoded = GameCodec.decode(saved)

        assertTrue(decoded.players.all { it.seat in decoded.players.indices })
        assertTrue(decoded.players.all { it.teamIndex in 0 until decoded.teamStats.size })
        assertEquals(1, decoded.players[0].teamIndex)
        assertEquals(3, decoded.players[0].seat)
        assertTrue(decoded.currentTeamIndex in 0 until decoded.teamStats.size)

        var now = 0L
        val words = object : WordSource {
            override fun nextWord(
                categories: Set<String>, difficulty: Int,
                used: MutableSet<String>, legacyGeneralDifficulty: Int?
            ): String = "واژه"
        }
        val engine = GameEngine(decoded, words, clockNanos = { now })
        now = 1_000_000_000L
        engine.tick()
        assertEquals(1_000_000_000L, decoded.teamStats[decoded.currentTeamIndex].realTimeNanos)
    }

    private fun playersJson(count: Int) = org.json.JSONArray().apply {
        for (index in 0 until count) {
            put(JSONObject().put("seat", index).put("name", "بازیکن ${index + 1}").put("team", index % (count / 2)))
        }
    }

    private fun teamStatsJson(teamCount: Int) = org.json.JSONArray().apply {
        for (team in 0 until teamCount) put(JSONObject().put("realTimeNanos", 0L).put("penaltyNanos", 0L))
    }
}
