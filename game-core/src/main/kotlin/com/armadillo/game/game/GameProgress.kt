package com.armadillo.game.game

/** Presentation state for a round, without changing the game engine's phase. */
enum class RoundIndicator { COMPLETED, ACTIVE, UPCOMING }

fun roundIndicator(round: Int, currentRound: Int, phase: GamePhase): RoundIndicator = when {
    round < currentRound -> RoundIndicator.COMPLETED
    round == currentRound && (phase == GamePhase.PLAYING || phase == GamePhase.MANUAL_PAUSED) -> RoundIndicator.ACTIVE
    else -> RoundIndicator.UPCOMING
}

/** Fraction of the maximum possible team time, including one penalty per round. */
fun teamTimeProgress(finalTimeNanos: Long, durationSeconds: Int, totalRounds: Int): Float {
    if (durationSeconds <= 0 || totalRounds <= 0) return 0f
    val maxNanos = (durationSeconds.toDouble() * GameEngine.NANOS_PER_SECOND +
        GameConstants.EXPLOSION_PENALTY_NANOS) * totalRounds
    return (finalTimeNanos.toDouble() / maxNanos).coerceIn(0.0, 1.0).toFloat()
}
