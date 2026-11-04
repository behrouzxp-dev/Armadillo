package com.armadillo.game.data

import android.content.Context
import com.armadillo.game.R
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

/** Local durable state. Keep the preference-file and existing game keys stable for upgrades. */
class LocalStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("armadillo_local_v1", Context.MODE_PRIVATE)
    private var rosterCache: PlayerRoster? = null
    private val defaultPlayerName: (Int) -> String = { context.getString(R.string.players_generated_name, it) }
    private var discardedCheckpoint = false

    /**
     * Cheap existence check. It must not decode or rewrite the checkpoint: this is
     * called while rendering the home screen, and the previous implementation
     * migrated the save and committed to disk on every visit.
     */
    fun hasSavedGame(): Boolean = preferences.contains(KEY_GAME)

    /** True once when a corrupt or unsupported checkpoint had to be discarded. */
    fun consumeDiscardedCheckpointNotice(): Boolean =
        discardedCheckpoint.also { discardedCheckpoint = false }

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
            // Do not clear the separate player roster if a corrupted/unsupported checkpoint is found,
            // but remember to tell the player that the unfinished game could not be restored.
            discardedCheckpoint = true
            clearGame()
            null
        }
    }

    fun loadPlayerRoster(): PlayerRoster? {
        rosterCache?.let { return it }
        val raw = preferences.getString(KEY_ROSTER, null) ?: return null
        val parsed = try {
            val json = JSONObject(raw)
            val count = json.optInt("playerCount", 0)
            val array = json.optJSONArray("names") ?: return null
            if (count !in GameConstants.allowedPlayerCounts || array.length() < count) return null
            val names = List(array.length()) { index -> array.optString(index, "") }
            PlayerRoster(count, names)
        } catch (_: Exception) {
            null
        }
        rosterCache = parsed
        return parsed
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
        // Use the in-memory roster: reading preferences on every checkpoint was wasteful.
        val oldNames = loadPlayerRoster()?.names.orEmpty()
        // Update only the selected seats; any saved names beyond the chosen count remain available
        // if the player-count selection is increased later.
        val allNames = mergePlayerRosterNames(oldNames, names, count)
        editor.putString(KEY_ROSTER, JSONObject().apply {
            put("playerCount", count)
            put("names", JSONArray().apply { allNames.forEach { put(it) } })
        }.toString())
        rosterCache = PlayerRoster(count, allNames)
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

    private fun encodeGame(game: GameState): JSONObject = GameCodec.encode(game)

    private fun decodeGame(json: JSONObject): GameState = GameCodec.decode(json, defaultPlayerName)

    private companion object {
        const val KEY_GAME = "current_game"
        const val KEY_ROSTER = "player_roster_v1"
    }
}
