package com.armadillo.game.ui

import com.armadillo.game.game.GamePhase
import com.armadillo.game.game.Player
import com.armadillo.game.game.TeamStats
import com.armadillo.game.game.WordHistoryEntry

/** One destination of the app. Dialogs and in-game overlays are modelled separately. */
enum class Screen {
    HOME,
    PLAYER_COUNT,
    PLAYERS,
    CATEGORIES,
    OPTIONS,
    SOUND_SETTINGS,
    RULES,
    ABOUT,
    GAME,
    EXPLOSION,
    SYSTEM_PAUSED,
    RESULTS
}

/** The mutable setup draft. Immutable so a copy always triggers recomposition. */
data class SetupDraft(
    val playerCount: Int,
    val names: List<String>,
    val difficulty: Int = 1,
    val categories: Set<String> = linkedSetOf(com.armadillo.game.game.WordCategories.JOBS),
    val durationSeconds: Int = 120,
    val rounds: Int = 5,
    val isReplay: Boolean = false,
    val returnToPreparation: Boolean = false
)

/** Everything a screen needs to draw the current game without touching the engine. */
data class GameSnapshot(
    val phase: GamePhase,
    val round: Int,
    val totalRounds: Int,
    val teamCount: Int,
    val currentPlayerName: String,
    val currentPlayerTeam: Int,
    val nextPlayerName: String,
    val nextPlayerTeam: Int,
    val word: String?,
    val teamSeconds: List<Long>,
    val canSwap: Boolean,
    val showEditNames: Boolean,
    val players: List<Player>,
    val teamStats: List<TeamStats>,
    val wordHistory: List<WordHistoryEntry>,
    val penaltyTeamIndex: Int
)

/** The eight modal dialogs of the app, as data instead of imperative builder calls. */
sealed interface DialogState {
    data object WordsUnavailable : DialogState
    data object WordsNotReady : DialogState
    data object DiscardedCheckpoint : DialogState
    data object SetupIncomplete : DialogState
    data object ExitApp : DialogState
    data class EditName(val seat: Int, val initial: String) : DialogState
    data class ConfirmEndGame(val wasPlaying: Boolean) : DialogState
    data class ConfirmExitGame(val wasPlaying: Boolean) : DialogState
}
