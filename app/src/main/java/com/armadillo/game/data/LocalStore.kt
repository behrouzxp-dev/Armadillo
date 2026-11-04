package com.armadillo.game.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import com.armadillo.game.game.GameConstants
import com.armadillo.game.game.GamePhase
import com.armadillo.game.game.GameState
import com.armadillo.game.game.Player
import com.armadillo.game.game.SoundSettings
import com.armadillo.game.game.TeamStats
import com.armadillo.game.game.WordCategories
import com.armadillo.game.game.WordHistoryEntry
import com.armadillo.game.game.WordOutcome
import com.armadillo.game.game.migrateSavedCategories

/** Player names are persisted separately from the optional unfinished-game checkpoint. */
data class PlayerRoster(val playerCount: Int, val names: List<String>)

/** Pure seat-index merge logic; keeping it separate makes roster migration/unit tests deterministic. */
internal fun mergePlayerRosterNames(existing: List<String>, updated: List<String>, count: Int): List<String> {
    require(count in GameConstants.allowedPlayerCounts)
    require(updated.size >= count)
    val merged = existing.toMutableList()
    while (merged.size < count) merged += ""
    for (index in 0 until count) merged[index] = updated[index].trim()
    return merged
}

/** Local durable state. Keep the preference-file and existing game keys stable for upgrades. */
class LocalStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("armadillo_local_v1", Context.MODE_PRIVATE)

    fun hasUnfinishedGame(): Boolean = loadGame() != null

    fun saveGame(state: GameState, durable: Boolean = true) {
        val editor = preferences.edit()
        if (state.phase == GamePhase.RESULTS) {
            editor.remove(KEY_GAME)
        } else {
            editor.putString(KEY_GAME, encodeGame(state).toString())
        }
        // Roster survives result display and is not coupled to the game checkpoint/history.
        putRoster(editor, state.players.sortedBy { it.seat }.map { it.name }, state.players.size)
        if (durable) editor.commit() else editor.apply()
    }

    fun clearGame() {
        preferences.edit().remove(KEY_GAME).commit()
    }

    /** Reads and migrates any existing checkpoint without deleting player or team data. */
    fun loadGame(): GameState? {
        val raw = preferences.getString(KEY_GAME, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val state = decodeGame(json)
            if (loadPlayerRoster() == null) {
                // v1.0/v1.1 did not have a standalone roster. An unfinished save is the
                // trustworthy source for a one-time name migration.
                savePlayerRoster(state.players.sortedBy { it.seat }.map { it.name }, state.players.size, durable = true)
            }
            when (state.phase) {
                GamePhase.PLAYING -> {
                    state.pausedFrom = GamePhase.PLAYING
                    state.phase = GamePhase.SYSTEM_PAUSED
                }
                GamePhase.EXPLODING -> {
                    state.pausedFrom = GamePhase.EXPLODING
                    state.phase = GamePhase.SYSTEM_PAUSED
                }
                GamePhase.PREPARATION -> {
                    state.pausedFrom = GamePhase.PREPARATION
                    state.phase = GamePhase.SYSTEM_PAUSED
                }
                GamePhase.MANUAL_PAUSED -> {
                    // Preserve the user's explicit pause intent across process recreation.
                    // Continue first restores the manual-paused screen; the user then resumes deliberately.
                    state.pausedFrom = GamePhase.MANUAL_PAUSED
                    state.phase = GamePhase.SYSTEM_PAUSED
                }
                GamePhase.SYSTEM_PAUSED -> Unit
                GamePhase.RESULTS -> {
                    clearGame()
                    return null
                }
                else -> Unit
            }
            // Persist schema/category/history migration and the system-paused checkpoint.
            saveGame(state, durable = true)
            state
        } catch (_: Exception) {
            // Do not clear the separate player roster if a corrupted/unsupported checkpoint is found.
            clearGame()
            null
        }
    }

    fun loadPlayerRoster(): PlayerRoster? {
        val raw = preferences.getString(KEY_ROSTER, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val count = json.optInt("playerCount", 0)
            val array = json.optJSONArray("names") ?: return null
            if (count !in GameConstants.allowedPlayerCounts || array.length() < count) return null
            val names = List(array.length()) { index -> array.optString(index, "") }
            PlayerRoster(count, names)
        } catch (_: Exception) {
            null
        }
    }

    /** Existing names retain their seat positions; new seats start blank. */
    fun playerNamesForCount(count: Int): List<String> {
        require(count in GameConstants.allowedPlayerCounts)
        val existing = loadPlayerRoster()?.names.orEmpty()
        return List(count) { index -> existing.getOrNull(index).orEmpty() }
    }

    fun savePlayerRoster(names: List<String>, count: Int, durable: Boolean = true) {
        require(count in GameConstants.allowedPlayerCounts)
        require(names.size >= count)
        val editor = preferences.edit()
        putRoster(editor, names, count)
        if (durable) editor.commit() else editor.apply()
    }

    private fun putRoster(editor: SharedPreferences.Editor, names: List<String>, count: Int) {
        val oldNames = loadPlayerRoster()?.names.orEmpty()
        // Update only the selected seats; any saved names beyond the chosen count remain available
        // if the player-count selection is increased later.
        val allNames = mergePlayerRosterNames(oldNames, names, count)
        editor.putString(KEY_ROSTER, JSONObject().apply {
            put("playerCount", count)
            put("names", JSONArray().apply { allNames.forEach { put(it) } })
        }.toString())
    }

    fun loadSoundSettings(): SoundSettings = SoundSettings(
        beepEnabled = preferences.getBoolean("beep_enabled", true),
        beepVolume = preferences.getInt("beep_volume", 55).coerceIn(0, 100),
        explosionEnabled = preferences.getBoolean("explosion_enabled", true),
        explosionVolume = preferences.getInt("explosion_volume", 75).coerceIn(0, 100),
        effectsEnabled = preferences.getBoolean("effects_enabled", true),
        effectsVolume = preferences.getInt("effects_volume", 45).coerceIn(0, 100),
        muteAll = preferences.getBoolean("mute_all", false),
        vibrateOnExplosion = preferences.getBoolean("vibrate_explosion", true)
    )

    fun saveSoundSettings(settings: SoundSettings) {
        preferences.edit()
            .putBoolean("beep_enabled", settings.beepEnabled)
            .putInt("beep_volume", settings.beepVolume.coerceIn(0, 100))
            .putBoolean("explosion_enabled", settings.explosionEnabled)
            .putInt("explosion_volume", settings.explosionVolume.coerceIn(0, 100))
            .putBoolean("effects_enabled", settings.effectsEnabled)
            .putInt("effects_volume", settings.effectsVolume.coerceIn(0, 100))
            .putBoolean("mute_all", settings.muteAll)
            .putBoolean("vibrate_explosion", settings.vibrateOnExplosion)
            .apply()
    }

    private fun encodeGame(game: GameState): JSONObject = JSONObject().apply {
        put("schema", 3)
        put("id", game.id)
        put("players", JSONArray().apply {
            game.players.forEach { player ->
                put(JSONObject().put("seat", player.seat).put("name", player.name).put("team", player.teamIndex))
            }
        })
        put("difficulty", game.difficulty)
        put("selectedCategories", JSONArray().apply { game.selectedCategories.forEach { put(it) } })
        put("legacyGeneralDifficulty", JSONObject.NULL)
        put("durationSeconds", game.durationSeconds)
        put("totalRounds", game.totalRounds)
        put("currentRound", game.currentRound)
        put("phase", game.phase.name)
        if (game.pausedFrom == null) put("pausedFrom", JSONObject.NULL) else put("pausedFrom", game.pausedFrom!!.name)
        put("currentPlayerIndex", game.currentPlayerIndex)
        put("nextRoundStarterIndex", game.nextRoundStarterIndex)
        put("roundRemainingNanos", game.roundRemainingNanos)
        put("swapCooldownNanos", game.swapCooldownNanos)
        if (game.currentWord == null) put("currentWord", JSONObject.NULL) else put("currentWord", game.currentWord)
        put("usedWords", JSONArray().apply { game.usedWords.forEach { put(it) } })
        put("wordHistory", JSONArray().apply {
            game.wordHistory.forEach { entry ->
                put(JSONObject()
                    .put("word", entry.word)
                    .put("playerSeat", entry.playerSeat)
                    .put("round", entry.round)
                    .put("shownAtEpochMillis", entry.shownAtEpochMillis)
                    .put("outcome", entry.outcome.name))
            }
        })
        put("teamStats", JSONArray().apply {
            game.teamStats.forEach { stats ->
                put(JSONObject()
                    .put("realTimeNanos", stats.realTimeNanos)
                    .put("penaltyNanos", stats.penaltyNanos)
                    .put("correctWords", stats.correctWords)
                    .put("swaps", stats.swaps)
                    .put("explosions", stats.explosions))
            }
        })
    }

    private fun decodeGame(json: JSONObject): GameState {
        val playersArray = json.getJSONArray("players")
        require(playersArray.length() in GameConstants.allowedPlayerCounts.toSet())
        val players = MutableList(playersArray.length()) { index ->
            val item = playersArray.getJSONObject(index)
            Player(
                seat = item.optInt("seat", index),
                name = item.optString("name", "بازیکن ${index + 1}"),
                teamIndex = item.optInt("team", index % (playersArray.length() / 2))
            )
        }
        val statsArray = json.getJSONArray("teamStats")
        val stats = MutableList(statsArray.length()) { index ->
            val item = statsArray.getJSONObject(index)
            TeamStats(
                realTimeNanos = item.optLong("realTimeNanos", 0L).coerceAtLeast(0L),
                penaltyNanos = item.optLong("penaltyNanos", 0L).coerceAtLeast(0L),
                correctWords = item.optInt("correctWords", 0).coerceAtLeast(0),
                swaps = item.optInt("swaps", 0).coerceAtLeast(0),
                explosions = item.optInt("explosions", 0).coerceAtLeast(0)
            )
        }
        require(stats.size == players.size / 2)
        val phase = runCatching { GamePhase.valueOf(json.optString("phase", GamePhase.PREPARATION.name)) }
            .getOrDefault(GamePhase.PREPARATION)
        val pausedFrom = if (json.isNull("pausedFrom")) null else {
            runCatching { GamePhase.valueOf(json.optString("pausedFrom")) }.getOrNull()
        }
        // Preserve the actual timer in an already-running legacy game; these values are not
        // offered for new games, but converting them here would discard elapsed-time semantics.
        val duration = json.optInt("durationSeconds", 120)
            .takeIf { it in setOf(90, 120, 180, 210, 300) } ?: 120
        // Preserve a legacy in-progress round plan; new setup only accepts 3, 5, or 7.
        val totalRounds = json.optInt("totalRounds", 5).coerceIn(3, 10)
        val currentRound = json.optInt("currentRound", 1).coerceIn(1, totalRounds + 1)
        val used = linkedSetOf<String>().apply {
            val array = json.optJSONArray("usedWords") ?: JSONArray()
            for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }

        val rawCategories = json.optJSONArray("selectedCategories")
        val selectedFromSave = linkedSetOf<String>().apply {
            if (rawCategories != null) {
                for (i in 0 until rawCategories.length()) {
                    rawCategories.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            } else {
                add(WordCategories.GENERAL)
            }
        }
        val categoryMigration = migrateSavedCategories(selectedFromSave)
        val categories = categoryMigration.selected
        val categorySelectionChanged = categoryMigration.refreshCurrentWord
        val storedDifficulty = json.optInt("difficulty", 1)
        val legacyGeneralDifficulty = if (rawCategories == null) storedDifficulty.coerceIn(1, 5) else null
        val mappedDifficulty = if (legacyGeneralDifficulty != null) {
            when (legacyGeneralDifficulty) {
                1, 2 -> 1
                3 -> 2
                else -> 3
            }
        } else storedDifficulty.coerceIn(1, 3)
        val currentPlayerIndex = json.optInt("currentPlayerIndex", 0).coerceIn(0, players.lastIndex)
        val savedWord = if (json.isNull("currentWord")) null else json.optString("currentWord").takeIf { it.isNotBlank() }
        val currentWord = if (categorySelectionChanged) null else savedWord
        val history = mutableListOf<WordHistoryEntry>()
        val historyArray = json.optJSONArray("wordHistory")
        if (historyArray != null) {
            for (index in 0 until historyArray.length()) {
                val item = historyArray.optJSONObject(index) ?: continue
                val word = item.optString("word").trim()
                val playerSeat = item.optInt("playerSeat", -1)
                val round = item.optInt("round", 0)
                val outcome = runCatching { WordOutcome.valueOf(item.optString("outcome", WordOutcome.PENDING.name)) }
                    .getOrDefault(WordOutcome.PENDING)
                if (word.isNotEmpty() && playerSeat in players.indices && round in 1..totalRounds) {
                    history += WordHistoryEntry(
                        word = word,
                        playerSeat = playerSeat,
                        round = round,
                        shownAtEpochMillis = item.optLong("shownAtEpochMillis", 0L).coerceAtLeast(0L),
                        outcome = outcome
                    )
                }
            }
        }
        if (categorySelectionChanged && savedWord != null) {
            val legacyCurrentEntry = history.lastOrNull {
                it.word == savedWord && it.playerSeat == currentPlayerIndex &&
                    it.round == currentRound && it.outcome == WordOutcome.PENDING
            }
            if (legacyCurrentEntry != null) {
                legacyCurrentEntry.outcome = WordOutcome.ENDED
            } else {
                history += WordHistoryEntry(
                    word = savedWord,
                    playerSeat = currentPlayerIndex,
                    round = currentRound,
                    shownAtEpochMillis = 0L,
                    outcome = WordOutcome.ENDED
                )
            }
        } else if (currentWord != null && history.none {
                it.word == currentWord && it.playerSeat == currentPlayerIndex && it.round == currentRound && it.outcome == WordOutcome.PENDING
            }) {
            // Older saves had no word-history field. Only the actual current word can be
            // associated confidently; usedWords alone has no player/round provenance.
            history += WordHistoryEntry(
                word = currentWord,
                playerSeat = currentPlayerIndex,
                round = currentRound,
                shownAtEpochMillis = 0L,
                outcome = WordOutcome.PENDING
            )
        }

        return GameState(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            players = players,
            difficulty = mappedDifficulty,
            durationSeconds = duration,
            totalRounds = totalRounds,
            currentRound = currentRound,
            phase = phase,
            pausedFrom = pausedFrom,
            currentPlayerIndex = currentPlayerIndex,
            nextRoundStarterIndex = json.optInt("nextRoundStarterIndex", currentPlayerIndex).coerceIn(0, players.lastIndex),
            roundRemainingNanos = json.optLong("roundRemainingNanos", duration * 1_000_000_000L).coerceIn(0L, duration * 1_000_000_000L),
            swapCooldownNanos = json.optLong("swapCooldownNanos", 0L).coerceIn(0L, GameConstants.SWAP_LOCK_NANOS),
            currentWord = currentWord,
            usedWords = used,
            wordHistory = history,
            teamStats = stats,
            selectedCategories = categories,
            legacyGeneralDifficulty = null
        )
    }

    private companion object {
        const val KEY_GAME = "current_game"
        const val KEY_ROSTER = "player_roster_v1"
    }
}
