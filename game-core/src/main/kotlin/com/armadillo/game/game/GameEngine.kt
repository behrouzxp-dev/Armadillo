package com.armadillo.game.game

import kotlin.math.max
import kotlin.math.min

interface WordSource {
    /** Returns an unused word from the frozen category selection and selected difficulty tier. */
    fun nextWord(
        categories: Set<String>,
        difficulty: Int,
        used: MutableSet<String>,
        legacyGeneralDifficulty: Int? = null
    ): String
}

enum class EngineEvent { NONE, EXPLOSION_STARTED, GAME_FINISHED }

data class ActionResult(val accepted: Boolean, val event: EngineEvent = EngineEvent.NONE)

/**
 * Deterministic game state transitions. Callers provide a monotonic nanosecond clock. The class
 * never uses timer tick counts as time; each update accrues the elapsed monotonic interval.
 */
class GameEngine(
    val state: GameState,
    private val words: WordSource,
    private val clockNanos: () -> Long,
    private val wallClockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private var lastSyncNanos: Long? = if (state.phase == GamePhase.PLAYING) clockNanos() else null

    fun startRound(): Boolean {
        if (state.phase != GamePhase.PREPARATION) return false
        val now = clockNanos()
        state.currentWord = drawWord()
        state.roundRemainingNanos = state.durationSeconds * NANOS_PER_SECOND
        state.swapCooldownNanos = GameConstants.SWAP_LOCK_NANOS
        state.phase = GamePhase.PLAYING
        state.pausedFrom = null
        lastSyncNanos = now
        return true
    }

    /** Accrue real time and return an explosion event exactly once when the countdown expires. */
    fun tick(): EngineEvent {
        if (state.phase != GamePhase.PLAYING) return EngineEvent.NONE
        return accrue(clockNanos())
    }

    fun markCorrect(): ActionResult {
        if (state.phase != GamePhase.PLAYING) return ActionResult(false)
        val now = clockNanos()
        val event = accrue(now)
        if (event != EngineEvent.NONE || state.phase != GamePhase.PLAYING) return ActionResult(false, event)

        recordCurrentWord(WordOutcome.CORRECT)
        state.teamStats[state.currentTeamIndex].correctWords += 1
        state.currentPlayerIndex = SeatPlan.nextSeat(state.currentPlayerIndex, state.players.size)
        state.currentWord = drawWord()
        state.swapCooldownNanos = GameConstants.SWAP_LOCK_NANOS
        // accrue() set the segment anchor to now. The new player's timer starts at this same instant.
        lastSyncNanos = now
        return ActionResult(true)
    }

    fun swapWord(): ActionResult {
        if (state.phase != GamePhase.PLAYING) return ActionResult(false)
        val now = clockNanos()
        val event = accrue(now)
        if (event != EngineEvent.NONE || state.phase != GamePhase.PLAYING) return ActionResult(false, event)
        if (state.swapCooldownNanos > 0L) return ActionResult(false)

        recordCurrentWord(WordOutcome.SWAPPED)
        state.teamStats[state.currentTeamIndex].swaps += 1
        state.currentWord = drawWord()
        state.swapCooldownNanos = GameConstants.SWAP_LOCK_NANOS
        lastSyncNanos = now
        return ActionResult(true)
    }

    fun canSwap(): Boolean = state.phase == GamePhase.PLAYING && state.swapCooldownNanos <= 0L

    fun pauseManually(): ActionResult {
        if (state.phase != GamePhase.PLAYING) return ActionResult(false)
        val event = accrue(clockNanos())
        if (event != EngineEvent.NONE) return ActionResult(false, event)
        state.pausedFrom = GamePhase.PLAYING
        state.phase = GamePhase.MANUAL_PAUSED
        lastSyncNanos = null
        return ActionResult(true)
    }

    /** Freeze any active state when the app loses foreground. Time outside the app is not counted. */
    fun pauseForSystem(): EngineEvent {
        when (state.phase) {
            GamePhase.PLAYING -> {
                val event = accrue(clockNanos())
                if (event == EngineEvent.EXPLOSION_STARTED) {
                    // Keep the explosion transition pending; acknowledgement is a deliberate tap.
                    state.pausedFrom = GamePhase.EXPLODING
                    state.phase = GamePhase.SYSTEM_PAUSED
                } else if (state.phase != GamePhase.RESULTS) {
                    state.pausedFrom = state.phase
                    state.phase = GamePhase.SYSTEM_PAUSED
                }
                lastSyncNanos = null
                return event
            }
            GamePhase.EXPLODING -> {
                state.pausedFrom = GamePhase.EXPLODING
                state.phase = GamePhase.SYSTEM_PAUSED
                lastSyncNanos = null
            }
            GamePhase.PREPARATION -> {
                state.pausedFrom = GamePhase.PREPARATION
                state.phase = GamePhase.SYSTEM_PAUSED
                lastSyncNanos = null
            }
            GamePhase.MANUAL_PAUSED -> {
                state.pausedFrom = GamePhase.MANUAL_PAUSED
                state.phase = GamePhase.SYSTEM_PAUSED
                lastSyncNanos = null
            }
            GamePhase.SYSTEM_PAUSED, GamePhase.RESULTS -> Unit
        }
        return EngineEvent.NONE
    }

    fun resumeManually(): Boolean {
        if (state.phase != GamePhase.MANUAL_PAUSED) return false
        state.phase = GamePhase.PLAYING
        state.pausedFrom = null
        lastSyncNanos = clockNanos()
        return true
    }

    fun resumeFromSystemPause(): Boolean {
        if (state.phase != GamePhase.SYSTEM_PAUSED) return false
        val resume = state.pausedFrom ?: GamePhase.PREPARATION
        state.pausedFrom = null
        if (resume == GamePhase.EXPLODING) {
            state.phase = GamePhase.EXPLODING
            lastSyncNanos = null
        } else if (resume == GamePhase.PLAYING) {
            // Legacy saves may have carried a word from the retired general bank. Draw a new
            // word from the migrated non-general selection without resetting the saved clock.
            if (state.currentWord == null) state.currentWord = drawWord()
            state.phase = GamePhase.PLAYING
            lastSyncNanos = clockNanos()
        } else {
            state.phase = if (resume == GamePhase.MANUAL_PAUSED) GamePhase.MANUAL_PAUSED else GamePhase.PREPARATION
            lastSyncNanos = null
        }
        return true
    }

    /** Called only after the player acknowledges the completed explosion animation. */
    fun finishExplosion(): EngineEvent {
        if (state.phase != GamePhase.EXPLODING) return EngineEvent.NONE
        lastSyncNanos = null
        state.currentWord = null
        state.currentRound += 1
        if (state.currentRound > state.totalRounds) {
            state.phase = GamePhase.RESULTS
            state.pausedFrom = null
            return EngineEvent.GAME_FINISHED
        }
        state.currentPlayerIndex = state.nextRoundStarterIndex
        state.roundRemainingNanos = state.durationSeconds * NANOS_PER_SECOND
        state.swapCooldownNanos = GameConstants.SWAP_LOCK_NANOS
        state.phase = GamePhase.PREPARATION
        state.pausedFrom = null
        return EngineEvent.NONE
    }

    /** End a manually paused or actively running game using all data accrued up to this instant. */
    fun finishEarly(): EngineEvent {
        if (state.phase == GamePhase.PLAYING) {
            val event = accrue(clockNanos())
            if (event == EngineEvent.EXPLOSION_STARTED) finishExplosion()
        } else if (state.phase == GamePhase.EXPLODING) {
            finishExplosion()
        }
        if (state.currentWord != null) recordCurrentWord(WordOutcome.ENDED)
        state.phase = GamePhase.RESULTS
        state.pausedFrom = null
        state.currentWord = null
        lastSyncNanos = null
        return EngineEvent.GAME_FINISHED
    }

    private fun accrue(now: Long): EngineEvent {
        val previous = lastSyncNanos ?: now.also { lastSyncNanos = it }
        val delta = max(0L, now - previous)
        lastSyncNanos = now
        if (delta == 0L) return EngineEvent.NONE

        val remainingBefore = state.roundRemainingNanos.coerceAtLeast(0L)
        val counted = min(delta, remainingBefore)
        state.teamStats[state.currentTeamIndex].realTimeNanos += counted
        state.roundRemainingNanos = remainingBefore - counted
        state.swapCooldownNanos = (state.swapCooldownNanos - counted).coerceAtLeast(0L)

        if (state.roundRemainingNanos == 0L) {
            recordCurrentWord(WordOutcome.EXPIRED)
            state.phase = GamePhase.EXPLODING
            state.pausedFrom = null
            state.nextRoundStarterIndex = state.currentPlayerIndex
            val team = state.teamStats[state.currentTeamIndex]
            team.penaltyNanos += GameConstants.EXPLOSION_PENALTY_NANOS
            team.explosions += 1
            lastSyncNanos = null
            return EngineEvent.EXPLOSION_STARTED
        }
        return EngineEvent.NONE
    }

    private fun drawWord(): String {
        val categories = state.selectedCategories.filter { it in WordCategories.all }.toSet()
        require(categories.isNotEmpty()) { "At least one active word category is required" }
        val word = words.nextWord(
            categories = categories,
            difficulty = state.difficulty,
            used = state.usedWords,
            legacyGeneralDifficulty = null
        )
        state.usedWords += normalizeWord(word)
        state.wordHistory += WordHistoryEntry(
            word = word,
            playerSeat = state.currentPlayerIndex,
            round = state.currentRound,
            shownAtEpochMillis = wallClockMillis(),
            outcome = WordOutcome.PENDING
        )
        return word
    }

    private fun recordCurrentWord(outcome: WordOutcome) {
        val word = state.currentWord ?: return
        val entry = state.wordHistory.lastOrNull {
            it.word == word && it.playerSeat == state.currentPlayerIndex &&
                it.round == state.currentRound && it.outcome == WordOutcome.PENDING
        }
        if (entry != null) entry.outcome = outcome
    }

    companion object {
        const val NANOS_PER_SECOND: Long = 1_000_000_000L
    }
}
