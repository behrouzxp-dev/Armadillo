package com.dour.game.game

import java.util.UUID

/** Every timed value is stored in nanoseconds. UI rendering never drives the game clock. */
enum class GamePhase {
    PREPARATION, PLAYING, MANUAL_PAUSED, SYSTEM_PAUSED, EXPLODING, RESULTS
}

object WordCategories {
    /** Retained only to safely migrate saved Dour games; it is not a selectable or playable category. */
    const val GENERAL = "general"
    const val JOBS = "jobs"
    const val ANIMALS = "animals"
    const val FOODS = "foods"
    const val PLACES = "places"
    const val TECHNOLOGY = "technology"

    val all: Set<String> = linkedSetOf(JOBS, ANIMALS, FOODS, PLACES, TECHNOLOGY)
}

data class SavedCategoryMigration(val selected: Set<String>, val refreshCurrentWord: Boolean)

/** Drop retired/unknown categories and choose a safe non-general fallback for legacy saves. */
fun migrateSavedCategories(saved: Set<String>?): SavedCategoryMigration {
    val selected = saved.orEmpty()
        .filter { it in WordCategories.all }
        .toCollection(linkedSetOf())
        .ifEmpty { linkedSetOf(WordCategories.JOBS) }
    return SavedCategoryMigration(selected, selected != saved)
}

enum class WordOutcome { PENDING, CORRECT, SWAPPED, EXPIRED, ENDED }

data class WordHistoryEntry(
    val word: String,
    /** Zero-based seat index of the player who received this word. */
    val playerSeat: Int,
    val round: Int,
    val shownAtEpochMillis: Long = 0L,
    var outcome: WordOutcome = WordOutcome.PENDING
)

data class Player(
    val seat: Int,
    val name: String,
    /** Zero-based team index. */
    val teamIndex: Int
)

data class TeamStats(
    var realTimeNanos: Long = 0L,
    var penaltyNanos: Long = 0L,
    var correctWords: Int = 0,
    var swaps: Int = 0,
    var explosions: Int = 0
) {
    val finalTimeNanos: Long get() = realTimeNanos + penaltyNanos
}

data class GameState(
    val id: String = UUID.randomUUID().toString(),
    val players: MutableList<Player>,
    var difficulty: Int,
    var durationSeconds: Int,
    var totalRounds: Int,
    var currentRound: Int = 1,
    var phase: GamePhase = GamePhase.PREPARATION,
    var pausedFrom: GamePhase? = null,
    var currentPlayerIndex: Int = 0,
    var nextRoundStarterIndex: Int = 0,
    var roundRemainingNanos: Long,
    var swapCooldownNanos: Long = GameConstants.SWAP_LOCK_NANOS,
    var currentWord: String? = null,
    val usedWords: MutableSet<String> = linkedSetOf(),
    val wordHistory: MutableList<WordHistoryEntry> = mutableListOf(),
    val teamStats: MutableList<TeamStats>,
    var selectedCategories: Set<String> = setOf(WordCategories.JOBS),
    /** Retained while decoding legacy saves; the general bank is no longer used for new words. */
    var legacyGeneralDifficulty: Int? = null
) {
    init {
        require(players.size in GameConstants.allowedPlayerCounts.toSet())
        require(difficulty in 1..3)
        require(selectedCategories.isNotEmpty() && selectedCategories.all { it in WordCategories.all })
        require(teamStats.size == players.size / 2)
        require(currentPlayerIndex in players.indices)
    }

    val teamCount: Int get() = players.size / 2
    val currentPlayer: Player get() = players[currentPlayerIndex]
    val nextPlayer: Player get() = players[SeatPlan.nextSeat(currentPlayerIndex, players.size)]
    val currentTeamIndex: Int get() = currentPlayer.teamIndex

    fun isFinished(): Boolean = phase == GamePhase.RESULTS
}

data class SoundSettings(
    var beepEnabled: Boolean = true,
    var beepVolume: Int = 55,
    var explosionEnabled: Boolean = true,
    var explosionVolume: Int = 75,
    var effectsEnabled: Boolean = true,
    var effectsVolume: Int = 45,
    var muteAll: Boolean = false,
    var vibrateOnExplosion: Boolean = true
)

data class RankedTeam(
    val teamIndex: Int,
    val rank: Int,
    val stats: TeamStats
)

object GameConstants {
    const val SWAP_LOCK_NANOS: Long = 7_000_000_000L
    const val EXPLOSION_PENALTY_NANOS: Long = 10_000_000_000L
    const val EXPLOSION_ANIMATION_MS: Long = 2_000L
    const val CHECKPOINT_INTERVAL_MS: Long = 1_000L

    // Stable team identity order: blue, red, green, purple, yellow.
    val teamColors: IntArray = intArrayOf(
        0xFF1683D8.toInt(), // blue
        0xFFE53935.toInt(), // red
        0xFF278A50.toInt(), // green
        0xFF7A4DB8.toInt(), // purple
        0xFFD39F00.toInt()  // yellow / dark gold to separate it from the yellow app background
    )

    val allowedPlayerCounts = intArrayOf(4, 6, 8, 10)
    val allowedDurations = intArrayOf(120, 210, 300)
    val allowedRounds = intArrayOf(3, 5, 7)
}

/** Seat 0 starts at six o'clock. Teammates occupy seats opposite each other. */
object SeatPlan {
    fun isAllowedCount(count: Int): Boolean = GameConstants.allowedPlayerCounts.contains(count)

    fun createPlayers(count: Int, rawNames: List<String>): MutableList<Player> {
        require(isAllowedCount(count)) { "Player count must be 4, 6, 8, or 10" }
        val reservedNames = rawNames.take(count).map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        val result = mutableListOf<Player>()
        val teamCount = count / 2
        var generated = 1
        for (seat in 0 until count) {
            var name = rawNames.getOrNull(seat)?.trim().orEmpty()
            if (name.isEmpty()) {
                var candidate: String
                do {
                    candidate = "بازیکن $generated"
                    generated++
                } while (reservedNames.contains(candidate) || result.any { it.name == candidate })
                name = candidate
            }
            val team = if (seat < teamCount) seat else seat - teamCount
            result += Player(seat = seat, name = name, teamIndex = team)
        }
        return result
    }

    /** Increasing seat index follows the counterclockwise order around the table. */
    fun nextSeat(currentSeat: Int, playerCount: Int): Int {
        require(playerCount > 0)
        return (currentSeat + 1) % playerCount
    }

    fun teamForSeat(seat: Int, playerCount: Int): Int {
        val teamCount = playerCount / 2
        return if (seat < teamCount) seat else seat - teamCount
    }

    fun areOppositeTeamMates(a: Int, b: Int, playerCount: Int): Boolean {
        if (a !in 0 until playerCount || b !in 0 until playerCount) return false
        return (a + playerCount / 2) % playerCount == b && teamForSeat(a, playerCount) == teamForSeat(b, playerCount)
    }
}

fun normalizeWord(word: String): String = word
    .trim()
    .replace('ك', 'ک')
    .replace('ي', 'ی')
    .replace('ى', 'ی')
    .replace('\u200c', ' ')
    .replace(Regex("\\s+"), " ")
    .lowercase()

fun rankTeams(stats: List<TeamStats>): List<RankedTeam> {
    val ordered = stats.indices.sortedWith { a, b ->
        val sa = stats[a]
        val sb = stats[b]
        when {
            sa.finalTimeNanos != sb.finalTimeNanos -> sa.finalTimeNanos.compareTo(sb.finalTimeNanos)
            sa.explosions != sb.explosions -> sa.explosions.compareTo(sb.explosions)
            sa.correctWords != sb.correctWords -> sb.correctWords.compareTo(sa.correctWords)
            sa.swaps != sb.swaps -> sa.swaps.compareTo(sb.swaps)
            else -> 0
        }
    }
    var rank = 1
    val result = mutableListOf<RankedTeam>()
    for (position in ordered.indices) {
        if (position > 0 && !sameRanking(stats[ordered[position - 1]], stats[ordered[position]])) {
            rank = position + 1
        }
        val team = ordered[position]
        result += RankedTeam(teamIndex = team, rank = rank, stats = stats[team])
    }
    return result
}

private fun sameRanking(a: TeamStats, b: TeamStats): Boolean =
    a.finalTimeNanos == b.finalTimeNanos &&
        a.explosions == b.explosions &&
        a.correctWords == b.correctWords &&
        a.swaps == b.swaps
