package com.armadillo.game

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.armadillo.game.audio.GameAudio
import com.armadillo.game.data.LocalStore
import com.armadillo.game.data.LocalWordBank
import com.armadillo.game.game.EngineEvent
import com.armadillo.game.game.GameConstants
import com.armadillo.game.game.GameEngine
import com.armadillo.game.game.GamePhase
import com.armadillo.game.game.GameState
import com.armadillo.game.game.SeatPlan
import com.armadillo.game.game.SoundSettings
import com.armadillo.game.game.TeamStats
import com.armadillo.game.game.WordCategories
import com.armadillo.game.ui.DialogState
import com.armadillo.game.ui.GameSnapshot
import com.armadillo.game.ui.Screen
import com.armadillo.game.ui.SetupDraft
import com.armadillo.game.util.GameSetupMath

/**
 * Owns the game engine, the timer, persistence and audio. The Compose layer only
 * reads the observable properties and calls these commands, so the game rules stay
 * in :game-core and the timing stays independent of recomposition.
 */
class GameController(
    private val activity: Activity,
    private val store: LocalStore,
    private val audio: GameAudio
) {
    var screen by mutableStateOf(Screen.HOME)
        private set
    var draft by mutableStateOf<SetupDraft?>(null)
        private set
    var selectedPlayerCount by mutableStateOf<Int?>(null)
        private set
    var canContinue by mutableStateOf(false)
        private set
    var dialog by mutableStateOf<DialogState?>(null)
        private set
    var game by mutableStateOf<GameSnapshot?>(null)
        private set
    var sound by mutableStateOf(SoundSettings())
        private set

    private var settingsReturnScreen = Screen.HOME
    private var wordBank: LocalWordBank? = null
    private var gameState: GameState? = null
    private var engine: GameEngine? = null
    private var needsSyncOnResume = false
    private var backgroundSuspensionDone = false
    private var explosionTapHandled = false
    private var lastCheckpointMs = 0L
    private var nextBeepAtNanos = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            val active = engine ?: return
            if (active.state.phase != GamePhase.PLAYING) return
            val event = active.tick()
            if (event == EngineEvent.EXPLOSION_STARTED) {
                onExplosionStarted()
                return
            }
            publishGame()
            playScheduledBeep()
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastCheckpointMs >= GameConstants.CHECKPOINT_INTERVAL_MS) {
                store.saveGame(active.state, durable = false)
                lastCheckpointMs = nowMs
            }
            if (active.state.phase == GamePhase.PLAYING) {
                // Three updates a second are enough for a countdown shown in whole seconds.
                handler.postDelayed(this, LIVE_UPDATE_INTERVAL_MS)
            }
        }
    }

    // ---------------------------------------------------------------- startup

    fun start(saved: Bundle?) {
        sound = store.loadSoundSettings()
        audio.applySettings(sound)
        loadWordBankAsync()
        gameState = store.loadGame()
        gameState?.let { restored -> engine = createEngine(restored) }
        if (saved != null) restoreDraft(saved)
        if (engine != null) {
            syncToPhase()
            return
        }
        when (saved?.getString(KEY_SCREEN)) {
            Screen.PLAYER_COUNT.name -> showPlayerCount()
            Screen.PLAYERS.name -> if (draft == null) showPlayerCount() else showPlayers()
            Screen.CATEGORIES.name -> if (draft == null) showPlayerCount() else showCategories()
            Screen.OPTIONS.name -> if (draft == null) showPlayerCount() else showOptions()
            else -> showHome()
        }
    }

    /**
     * The bank is read off the main thread. A checkpoint that was restored before it
     * finished used to lose its engine; that is repaired as soon as the bank lands.
     */
    private fun loadWordBankAsync() {
        Thread {
            val result = runCatching { LocalWordBank(activity.applicationContext) }
            handler.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                result.onSuccess { bank ->
                    wordBank = bank
                    if (engine == null) {
                        val restored = gameState
                        if (restored != null) {
                            engine = createEngine(restored)
                            syncToPhase()
                        }
                    }
                }.onFailure {
                    dialog = DialogState.WordsUnavailable
                }
            }
        }.apply { name = "word-bank-loader" }.start()
    }

    private fun createEngine(state: GameState): GameEngine? = wordBank?.let { bank ->
        GameEngine(state, bank, clockNanos = { SystemClock.elapsedRealtimeNanos() })
    }

    // ------------------------------------------------------------- lifecycle

    fun onResume() {
        backgroundSuspensionDone = false
        if (needsSyncOnResume) {
            needsSyncOnResume = false
            syncToPhase()
        }
    }

    fun onPause() = suspendForBackground()

    fun onStop() = suspendForBackground()

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus && dialog == null) {
            suspendForBackground()
        } else if (hasFocus) {
            backgroundSuspensionDone = false
            if (needsSyncOnResume && engine != null) {
                needsSyncOnResume = false
                syncToPhase()
            }
        }
    }

    /** One implementation for pause, stop and focus loss. */
    private fun suspendForBackground() {
        val active = engine ?: return
        if (active.state.phase == GamePhase.RESULTS) return
        if (backgroundSuspensionDone) return
        active.pauseForSystem()
        audio.stopAll()
        stopActiveLoops()
        store.saveGame(active.state, durable = true)
        needsSyncOnResume = true
        backgroundSuspensionDone = true
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SCREEN, screen.name)
        val current = draft ?: return
        outState.putInt(KEY_PLAYER_COUNT, current.playerCount)
        outState.putStringArrayList(KEY_NAMES, ArrayList(current.names))
        outState.putInt(KEY_DIFFICULTY, current.difficulty)
        outState.putStringArrayList(KEY_CATEGORIES, ArrayList(current.categories))
        outState.putInt(KEY_DURATION, current.durationSeconds)
        outState.putInt(KEY_ROUNDS, current.rounds)
        outState.putBoolean(KEY_REPLAY, current.isReplay)
        outState.putBoolean(KEY_RETURN_TO_PREPARATION, current.returnToPreparation)
    }

    private fun restoreDraft(saved: Bundle) {
        val count = saved.getInt(KEY_PLAYER_COUNT, 0)
        if (count !in GameConstants.allowedPlayerCounts) return
        val names = saved.getStringArrayList(KEY_NAMES)?.toMutableList() ?: mutableListOf()
        while (names.size < count) names += ""
        val categories = saved.getStringArrayList(KEY_CATEGORIES)
            ?.filter { it in WordCategories.all }
            ?.toCollection(linkedSetOf())
            ?.takeIf { it.isNotEmpty() }
            ?: linkedSetOf(WordCategories.JOBS)
        draft = SetupDraft(
            playerCount = count,
            names = names,
            difficulty = saved.getInt(KEY_DIFFICULTY, 1).coerceIn(1, 3),
            categories = categories,
            durationSeconds = GameSetupMath.nearestAllowedDuration(saved.getInt(KEY_DURATION, 120)),
            rounds = GameSetupMath.nearestAllowedRounds(saved.getInt(KEY_ROUNDS, 5)),
            isReplay = saved.getBoolean(KEY_REPLAY, false),
            returnToPreparation = saved.getBoolean(KEY_RETURN_TO_PREPARATION, false)
        )
    }

    fun dispose() {
        stopActiveLoops()
        audio.release()
    }

    // ------------------------------------------------------------ navigation

    fun showHome() {
        screen = Screen.HOME
        val state = gameState
        canContinue = state != null && state.phase != GamePhase.RESULTS && store.hasSavedGame()
        if (store.consumeDiscardedCheckpointNotice()) dialog = DialogState.DiscardedCheckpoint
    }

    fun startSetup() {
        selectedPlayerCount = store.loadPlayerRoster()?.playerCount ?: 4
        showPlayerCount()
    }

    fun showPlayerCount() {
        if (selectedPlayerCount?.let { it in GameConstants.allowedPlayerCounts } != true) {
            selectedPlayerCount = store.loadPlayerRoster()?.playerCount ?: 4
        }
        screen = Screen.PLAYER_COUNT
    }

    fun selectPlayerCount(count: Int) {
        selectedPlayerCount = count
        showPlayerCount()
    }

    fun continueToPlayers() {
        val count = selectedPlayerCount ?: return
        if (count !in GameConstants.allowedPlayerCounts) return
        val names = store.playerNamesForCount(count)
        store.savePlayerRoster(names, count)
        draft = SetupDraft(playerCount = count, names = names)
        showPlayers()
    }

    fun showPlayers() {
        if (draft == null) {
            showPlayerCount()
            return
        }
        screen = Screen.PLAYERS
    }

    fun backFromPlayers() {
        if (draft?.returnToPreparation == true) syncToPhase() else showPlayerCount()
    }

    fun editPlayerName(seat: Int) {
        val current = draft ?: return
        if (seat !in 0 until current.playerCount) return
        dialog = DialogState.EditName(seat = seat, initial = current.names.getOrNull(seat).orEmpty())
    }

    fun commitPlayerName(seat: Int, raw: String) {
        val current = draft ?: return
        val names = current.names.toMutableList()
        if (seat in names.indices) names[seat] = raw.trim()
        draft = current.copy(names = names)
        store.savePlayerRoster(names, current.playerCount, durable = true)
        dialog = null
    }

    fun showCategories() {
        if (draft == null) {
            showPlayerCount()
            return
        }
        screen = Screen.CATEGORIES
    }

    fun backFromCategories() {
        if (draft?.isReplay == true) {
            if (gameState?.phase == GamePhase.RESULTS) syncToPhase() else showHome()
        } else {
            showPlayers()
        }
    }

    fun toggleCategory(categoryId: String) {
        val current = draft ?: return
        if (categoryId !in WordCategories.all) return
        val categories = if (categoryId in current.categories) {
            current.categories - categoryId
        } else {
            current.categories + categoryId
        }
        draft = current.copy(categories = categories)
    }

    fun showOptions() {
        if (draft == null) {
            showPlayerCount()
            return
        }
        screen = Screen.OPTIONS
    }

    fun setDifficulty(level: Int) {
        draft = draft?.copy(difficulty = level)
    }

    fun setDuration(seconds: Int) {
        draft = draft?.copy(durationSeconds = seconds)
    }

    fun setRounds(rounds: Int) {
        draft = draft?.copy(rounds = rounds)
    }

    fun showRules() {
        screen = Screen.RULES
    }

    fun openSoundSettings(returnTo: Screen) {
        settingsReturnScreen = returnTo
        screen = Screen.SOUND_SETTINGS
    }

    fun returnFromSoundSettings() {
        if (settingsReturnScreen == Screen.GAME) {
            syncToPhase()
            if (engine?.state?.phase == GamePhase.PLAYING) startGameLoop()
        } else {
            showHome()
        }
    }

    fun returnToHome() {
        store.clearGame()
        gameState = null
        engine = null
        draft = null
        game = null
        showHome()
    }

    fun handleBack() {
        val open = dialog
        if (open != null) {
            dismissDialog()
            return
        }
        when (screen) {
            Screen.HOME -> dialog = DialogState.ExitApp
            Screen.PLAYER_COUNT -> showHome()
            Screen.PLAYERS -> backFromPlayers()
            Screen.CATEGORIES -> backFromCategories()
            Screen.OPTIONS -> showCategories()
            Screen.SOUND_SETTINGS -> returnFromSoundSettings()
            Screen.RULES -> showHome()
            Screen.GAME, Screen.SYSTEM_PAUSED, Screen.EXPLOSION -> requestExitGame()
            Screen.RESULTS -> returnToHome()
        }
    }

    // -------------------------------------------------------------- dialogs

    fun dismissDialog() {
        when (val open = dialog) {
            null -> Unit
            is DialogState.ConfirmEndGame -> cancelEndGame(open.wasPlaying)
            is DialogState.ConfirmExitGame -> cancelExitGame(open.wasPlaying)
            else -> dialog = null
        }
    }

    fun dismissWordsDialogs() {
        dialog = null
    }

    fun exitApp() {
        dialog = null
        activity.finish()
    }

    // ----------------------------------------------------------------- game

    fun beginGame() {
        val setup = draft ?: return
        val valid = SeatPlan.isAllowedCount(setup.playerCount) &&
            setup.categories.isNotEmpty() && setup.categories.all { it in WordCategories.all } &&
            setup.difficulty in 1..3 && setup.durationSeconds in GameConstants.allowedDurations &&
            setup.rounds in GameConstants.allowedRounds && setup.names.size == setup.playerCount
        if (!valid) {
            dialog = DialogState.SetupIncomplete
            return
        }
        val players = SeatPlan.createPlayers(setup.playerCount, setup.names) { index ->
            activity.getString(R.string.players_generated_name, index)
        }
        val state = GameState(
            players = players,
            difficulty = setup.difficulty,
            selectedCategories = setup.categories.toSet(),
            durationSeconds = setup.durationSeconds,
            totalRounds = setup.rounds,
            currentRound = 1,
            phase = GamePhase.PREPARATION,
            currentPlayerIndex = 0,
            nextRoundStarterIndex = 0,
            roundRemainingNanos = setup.durationSeconds * GameEngine.NANOS_PER_SECOND,
            teamStats = MutableList(setup.playerCount / 2) { TeamStats() }
        )
        val newEngine = createEngine(state)
        if (newEngine == null) {
            dialog = DialogState.WordsNotReady
            return
        }
        // One atomic preference write replaces the previous incomplete game only after setup is complete.
        store.saveGame(state, durable = true)
        gameState = state
        engine = newEngine
        audio.playEffect()
        syncToPhase()
    }

    fun continueSavedGame() {
        val restored = store.loadGame()
        if (restored == null) {
            gameState = null
            engine = null
            showHome()
            return
        }
        gameState = restored
        engine = createEngine(restored)
        if (engine == null) {
            dialog = DialogState.WordsNotReady
            return
        }
        syncToPhase()
        if (restored.phase == GamePhase.PLAYING) startGameLoop()
    }

    fun startRound() {
        val active = engine ?: return
        if (active.startRound()) {
            store.saveGame(active.state, durable = true)
            audio.playEffect()
            syncToPhase()
            startGameLoop()
        }
    }

    fun swapWord() {
        val active = engine ?: return
        val result = active.swapWord()
        handleActionResult(result.event)
        if (result.accepted) {
            store.saveGame(active.state, durable = true)
            publishGame()
        }
    }

    fun markCorrect() {
        val active = engine ?: return
        val result = active.markCorrect()
        handleActionResult(result.event)
        if (result.accepted) {
            audio.playEffect()
            store.saveGame(active.state, durable = true)
            publishGame()
        }
    }

    /** Toggle or suspend/resume the game without resetting its checkpoint or timer state. */
    fun togglePause() {
        val active = engine ?: return
        when (active.state.phase) {
            GamePhase.PLAYING -> {
                val result = active.pauseManually()
                audio.stopAll()
                stopActiveLoops()
                if (result.event == EngineEvent.EXPLOSION_STARTED) {
                    onExplosionStarted()
                } else if (result.accepted) {
                    store.saveGame(active.state, durable = true)
                    syncToPhase()
                }
            }
            GamePhase.MANUAL_PAUSED -> {
                if (active.resumeManually()) {
                    store.saveGame(active.state, durable = true)
                    syncToPhase()
                    startGameLoop()
                }
            }
            GamePhase.PREPARATION, GamePhase.EXPLODING -> {
                active.pauseForSystem()
                audio.stopAll()
                stopActiveLoops()
                store.saveGame(active.state, durable = true)
                syncToPhase()
            }
            GamePhase.SYSTEM_PAUSED -> {
                // Resuming never returns RESULTS: the phase is restored before the game can end.
                active.resumeFromSystemPause()
                store.saveGame(active.state, durable = true)
                syncToPhase()
                if (active.state.phase == GamePhase.PLAYING) startGameLoop()
            }
            GamePhase.RESULTS -> Unit
        }
    }

    fun acknowledgeExplosion() {
        val active = engine ?: return
        if (explosionTapHandled || active.state.phase != GamePhase.EXPLODING) return
        explosionTapHandled = true
        audio.stopAll()
        val event = active.finishExplosion()
        if (event == EngineEvent.GAME_FINISHED || active.state.phase == GamePhase.RESULTS) {
            store.clearGame()
            syncToPhase()
        } else {
            store.saveGame(active.state, durable = true)
            syncToPhase()
        }
    }

    fun requestEndGame() {
        val active = engine
        var wasPlaying = false
        if (active?.state?.phase == GamePhase.PLAYING) {
            active.pauseForSystem()
            wasPlaying = active.state.phase == GamePhase.SYSTEM_PAUSED
            audio.stopAll()
            stopActiveLoops()
            store.saveGame(active.state, durable = true)
        }
        dialog = DialogState.ConfirmEndGame(wasPlaying)
    }

    fun confirmEndGame() {
        dialog = null
        val current = engine ?: return
        current.finishEarly()
        audio.stopAll()
        stopActiveLoops()
        store.clearGame()
        syncToPhase()
    }

    fun cancelEndGame(wasPlaying: Boolean) {
        dialog = null
        resumeAfterInterruption(wasPlaying)
    }

    fun requestExitGame() {
        val active = engine
        var wasPlaying = false
        if (active?.state?.phase == GamePhase.PLAYING) {
            active.pauseForSystem()
            wasPlaying = active.state.phase == GamePhase.SYSTEM_PAUSED
            audio.stopAll()
            stopActiveLoops()
            store.saveGame(active.state, durable = true)
        }
        dialog = DialogState.ConfirmExitGame(wasPlaying)
    }

    fun confirmExitGame() {
        dialog = null
        engine?.let { active ->
            active.pauseForSystem()
            store.saveGame(active.state, durable = true)
        }
        audio.stopAll()
        stopActiveLoops()
        activity.finish()
    }

    fun cancelExitGame(wasPlaying: Boolean) {
        dialog = null
        resumeAfterInterruption(wasPlaying)
    }

    fun replayWithSamePlayers() {
        val state = engine?.state ?: gameState ?: return
        draft = SetupDraft(
            playerCount = state.players.size,
            names = state.players.sortedBy { it.seat }.map { it.name },
            difficulty = state.difficulty,
            categories = state.selectedCategories.toSet(),
            durationSeconds = GameSetupMath.nearestAllowedDuration(state.durationSeconds),
            rounds = GameSetupMath.nearestAllowedRounds(state.totalRounds),
            isReplay = true
        )
        store.clearGame()
        game = null
        showCategories()
    }

    // ---------------------------------------------------------------- sound

    fun playEffect() = audio.playEffect()

    fun previewBeep() = audio.previewBeep()

    fun previewExplosion() = audio.previewExplosion()

    fun previewEffect() = audio.previewEffect()

    fun updateSound(transform: (SoundSettings) -> SoundSettings) {
        sound = transform(sound)
        store.saveSoundSettings(sound)
        audio.applySettings(sound)
    }

    // -------------------------------------------------------------- internal

    private fun resumeAfterInterruption(wasPlaying: Boolean) {
        val current = engine
        if (wasPlaying && current?.state?.phase == GamePhase.SYSTEM_PAUSED) {
            current.resumeFromSystemPause()
            store.saveGame(current.state, durable = true)
            syncToPhase()
            if (current.state.phase == GamePhase.PLAYING) startGameLoop()
        } else {
            syncToPhase()
        }
    }

    private fun handleActionResult(event: EngineEvent) {
        if (event == EngineEvent.EXPLOSION_STARTED) onExplosionStarted()
    }

    private fun onExplosionStarted() {
        stopActiveLoops()
        audio.stopAll()
        val active = engine ?: return
        store.saveGame(active.state, durable = true)
        explosionTapHandled = false
        syncToPhase()
        audio.playExplosion()
        vibrateOnExplosion()
    }

    private fun startGameLoop() {
        val active = engine ?: return
        if (active.state.phase != GamePhase.PLAYING) return
        handler.removeCallbacks(timerRunnable)
        lastCheckpointMs = SystemClock.elapsedRealtime()
        nextBeepAtNanos = SystemClock.elapsedRealtimeNanos()
        playScheduledBeep()
        handler.post(timerRunnable)
    }

    private fun playScheduledBeep() {
        val active = engine ?: return
        if (active.state.phase != GamePhase.PLAYING) return
        val now = SystemClock.elapsedRealtimeNanos()
        if (now >= nextBeepAtNanos) {
            audio.playBeep()
            val remainingMs = active.state.roundRemainingNanos / 1_000_000L
            val intervalMs = if (remainingMs <= 30_000L) 430L else 1_000L
            nextBeepAtNanos = now + intervalMs * 1_000_000L
        }
    }

    private fun stopActiveLoops() {
        handler.removeCallbacks(timerRunnable)
        audio.stopBeep()
    }

    /** Mirrors the engine phase onto the visible screen and refreshes the snapshot. */
    private fun syncToPhase() {
        val state = engine?.state
        if (state == null) {
            showHome()
            return
        }
        screen = when (state.phase) {
            GamePhase.PREPARATION, GamePhase.PLAYING, GamePhase.MANUAL_PAUSED -> Screen.GAME
            GamePhase.SYSTEM_PAUSED -> Screen.SYSTEM_PAUSED
            GamePhase.EXPLODING -> Screen.EXPLOSION
            GamePhase.RESULTS -> Screen.RESULTS
        }
        publishGame()
    }

    private fun publishGame() {
        val active = engine
        val state = active?.state
        if (active == null || state == null) {
            game = null
            return
        }
        game = GameSnapshot(
            phase = state.phase,
            round = state.currentRound,
            totalRounds = state.totalRounds,
            durationSeconds = state.durationSeconds,
            currentPlayerName = state.currentPlayer.name,
            currentPlayerTeam = state.currentPlayer.teamIndex,
            word = state.currentWord,
            canSwap = active.canSwap(),
            players = state.players.toList(),
            teamStats = state.teamStats.map {
                TeamStats(
                    realTimeNanos = it.realTimeNanos,
                    penaltyNanos = it.penaltyNanos,
                    correctWords = it.correctWords,
                    swaps = it.swaps,
                    explosions = it.explosions
                )
            },
            wordHistory = state.wordHistory.toList(),
            penaltyTeamIndex = state.players.getOrNull(state.nextRoundStarterIndex)?.teamIndex
                ?: state.currentTeamIndex
        )
    }

    private fun vibrateOnExplosion() {
        if (!sound.vibrateOnExplosion) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(180L)
            }
        } catch (_: Exception) {
            // Devices without vibration support continue normally.
        }
    }

    private companion object {
        const val LIVE_UPDATE_INTERVAL_MS = 250L
        const val KEY_SCREEN = "screen"
        const val KEY_PLAYER_COUNT = "draft_player_count"
        const val KEY_NAMES = "draft_names"
        const val KEY_DIFFICULTY = "draft_difficulty"
        const val KEY_CATEGORIES = "draft_categories"
        const val KEY_DURATION = "draft_duration"
        const val KEY_ROUNDS = "draft_rounds"
        const val KEY_REPLAY = "draft_replay"
        const val KEY_RETURN_TO_PREPARATION = "draft_return_to_preparation"
    }
}
