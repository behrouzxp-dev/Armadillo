package com.armadillo.game

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.armadillo.game.audio.GameAudio
import com.armadillo.game.data.LocalStore
import com.armadillo.game.data.LocalWordBank
import com.armadillo.game.game.ActionResult
import com.armadillo.game.game.EngineEvent
import com.armadillo.game.game.GameConstants
import com.armadillo.game.game.GameEngine
import com.armadillo.game.game.GamePhase
import com.armadillo.game.game.GameState
import com.armadillo.game.game.RankedTeam
import com.armadillo.game.game.SeatPlan
import com.armadillo.game.game.SoundSettings
import com.armadillo.game.game.TeamStats
import com.armadillo.game.game.WordCategories
import com.armadillo.game.game.WordOutcome
import com.armadillo.game.game.rankTeams
import com.armadillo.game.ui.DesignColors
import com.armadillo.game.ui.DesignSystem
import com.armadillo.game.ui.SeatingCircleView
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppScreen {
    HOME, PLAYER_COUNT, PLAYERS, CATEGORIES, OPTIONS, SOUND_SETTINGS, RULES, ABOUT, GAME, SYSTEM_PAUSED, RESULTS
}

private data class SetupDraft(
    val playerCount: Int,
    val names: MutableList<String>,
    var difficulty: Int = 1,
    val categories: MutableSet<String> = linkedSetOf(WordCategories.JOBS),
    var durationSeconds: Int = 120,
    var rounds: Int = 5,
    val isReplay: Boolean = false,
    val returnToPreparation: Boolean = false
)

class MainActivity : Activity() {
    private lateinit var store: LocalStore
    private lateinit var wordBank: LocalWordBank
    private lateinit var audio: GameAudio
    private lateinit var designColors: DesignColors
    private lateinit var palette: PaletteTokens
    private var soundSettings = SoundSettings()
    private var gameState: GameState? = null
    private var engine: GameEngine? = null
    private var draft: SetupDraft? = null
    private var selectedPlayerCount: Int? = null
    private val lalezarTypeface by lazy { DesignSystem.lalezar(this) }
    private val lalezarBoldTypeface by lazy { DesignSystem.lalezar(this, bold = true) }
    private var screen = AppScreen.HOME
    private var settingsReturnScreen = AppScreen.HOME
    private var rootFrame: FrameLayout? = null
    private var dialogOpen = false
    private var needsRenderOnResume = false
    private var startAnimator: ObjectAnimator? = null
    private var explosionAnimator: AnimatorSet? = null
    private var explosionTapHandled = false
    private var lastCheckpointMs = 0L
    private var nextBeepAtNanos = 0L

    private var teamTimeViews: List<TextView> = emptyList()
    private var roundLabelView: TextView? = null
    private var currentPlayerView: TextView? = null
    private var currentCardView: LinearLayout? = null
    private var nextPlayerView: TextView? = null
    private var nextCardView: LinearLayout? = null
    private var wordView: TextView? = null
    private var swapButton: TextView? = null
    private var correctButton: TextView? = null
    private var pauseButton: TextView? = null
    private var startButton: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val activeEngine = engine ?: return
            if (activeEngine.state.phase != GamePhase.PLAYING) return
            val event = activeEngine.tick()
            if (event == EngineEvent.EXPLOSION_STARTED) {
                onExplosionStarted()
                return
            }
            updateLiveGameUi()
            playScheduledBeep()
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastCheckpointMs >= GameConstants.CHECKPOINT_INTERVAL_MS) {
                store.saveGame(activeEngine.state, durable = false)
                lastCheckpointMs = nowMs
            }
            if (activeEngine.state.phase == GamePhase.PLAYING) {
                mainHandler.postDelayed(this, 100L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        designColors = DesignSystem.colors(this)
        palette = PaletteTokens(designColors)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = palette.CANVAS
        window.navigationBarColor = palette.CANVAS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        window.decorView.systemUiVisibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        store = LocalStore(this)
        wordBank = LocalWordBank(this)
        soundSettings = store.loadSoundSettings()
        audio = GameAudio(this, soundSettings)
        gameState = store.loadGame()
        gameState?.let { game -> engine = GameEngine(game, wordBank, clockNanos = { SystemClock.elapsedRealtimeNanos() }) }
        renderRestoredState()
    }

    override fun onResume() {
        super.onResume()
        if (needsRenderOnResume) {
            needsRenderOnResume = false
            renderCurrentGameState()
        }
    }

    override fun onPause() {
        if (::store.isInitialized && engine != null) {
            val state = engine!!.state
            if (state.phase != GamePhase.RESULTS) {
                engine!!.pauseForSystem()
                audio.stopAll()
                stopActiveLoops()
                store.saveGame(state, durable = true)
                needsRenderOnResume = true
            }
        }
        super.onPause()
    }

    override fun onStop() {
        if (::store.isInitialized && engine != null && engine!!.state.phase != GamePhase.RESULTS) {
            engine!!.pauseForSystem()
            audio.stopAll()
            stopActiveLoops()
            store.saveGame(engine!!.state, durable = true)
            needsRenderOnResume = true
        }
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && !dialogOpen && ::store.isInitialized && engine != null) {
            val phase = engine!!.state.phase
            if (phase == GamePhase.PLAYING || phase == GamePhase.MANUAL_PAUSED || phase == GamePhase.PREPARATION || phase == GamePhase.EXPLODING) {
                engine!!.pauseForSystem()
                audio.stopAll()
                stopActiveLoops()
                store.saveGame(engine!!.state, durable = true)
                needsRenderOnResume = true
            }
        } else if (hasFocus && needsRenderOnResume && engine != null) {
            needsRenderOnResume = false
            renderCurrentGameState()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when (screen) {
            AppScreen.HOME -> showHomeExitDialog()
            AppScreen.PLAYER_COUNT -> renderHome()
            AppScreen.PLAYERS -> backFromPlayers()
            AppScreen.CATEGORIES -> backFromCategories()
            AppScreen.OPTIONS -> renderCategories()
            AppScreen.SOUND_SETTINGS -> returnFromSoundSettings()
            AppScreen.RULES, AppScreen.ABOUT -> renderHome()
            AppScreen.GAME, AppScreen.SYSTEM_PAUSED -> confirmExitGame()
            AppScreen.RESULTS -> returnToHome()
        }
    }

    private fun renderRestoredState() {
        if (gameState == null) {
            renderHome()
        } else {
            renderCurrentGameState()
        }
    }

    private fun renderCurrentGameState() {
        val state = engine?.state ?: run {
            renderHome()
            return
        }
        when (state.phase) {
            GamePhase.PREPARATION, GamePhase.PLAYING, GamePhase.MANUAL_PAUSED -> renderGamePage()
            GamePhase.SYSTEM_PAUSED -> renderSystemPaused()
            GamePhase.EXPLODING -> renderExplosionPage(playSound = false)
            GamePhase.RESULTS -> renderResults()
        }
    }

    private fun showPage(view: View) {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(palette.CANVAS)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setOnApplyWindowInsetsListener { target, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    target.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rootFrame = frame
        setContentView(frame)
    }

    private fun createPage(
        title: String,
        backAction: () -> Unit,
        footerText: String? = null,
        footerEnabled: () -> Boolean = { true },
        footerAction: (() -> Unit)? = null
    ): Pair<LinearLayout, LinearLayout> {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(DesignSystem.Spacing.PAGE_SIDE), dp(DesignSystem.Spacing.PAGE_TOP), dp(DesignSystem.Spacing.PAGE_SIDE), dp(DesignSystem.Spacing.PAGE_BOTTOM))
            setBackgroundColor(palette.CANVAS)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = label(title, 23f, palette.INK, true).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START }
        header.addView(heading, LinearLayout.LayoutParams(0, dp(DesignSystem.Metrics.PAGE_HEADER_CONTENT), 1f))
        val back = uiButton("بازگشت", outlined = true, height = DesignSystem.Metrics.MIN_TOUCH_TARGET, playEffect = true, onClick = backAction)
        header.addView(back, LinearLayout.LayoutParams(dp(82), dp(DesignSystem.Metrics.MIN_TOUCH_TARGET)))
        page.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(DesignSystem.Metrics.PAGE_HEADER)))

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, dp(DesignSystem.Spacing.INSET_6), 0, dp(DesignSystem.Spacing.MD))
        }
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (footerText != null && footerAction != null) {
            val enabled = footerEnabled()
            val confirm = uiButton(footerText, height = 58) {
                if (footerEnabled()) footerAction()
            }.apply {
                isEnabled = enabled
            }
            page.addView(confirm, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
                topMargin = dp(DesignSystem.Spacing.XS)
                bottomMargin = dp(DesignSystem.Spacing.XXS)
            })
        }
        return page to body
    }

    private fun renderHome() {
        screen = AppScreen.HOME
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(palette.CANVAS) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(DesignSystem.Spacing.HOME_SIDE), dp(DesignSystem.Spacing.HOME_TOP), dp(DesignSystem.Spacing.HOME_SIDE), dp(DesignSystem.Spacing.HOME_BOTTOM))
        }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(palette.SURFACE, DesignSystem.Radius.HERO, palette.LINE, DesignSystem.Interaction.DEFAULT_STROKE_DP.toFloat())
            setPadding(dp(DesignSystem.Spacing.MD), dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.MD), dp(DesignSystem.Spacing.INSET_14))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.armadillo_icon_art)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "نشان آرمادیلو"
        }
        hero.addView(logo, LinearLayout.LayoutParams(dp(DesignSystem.Metrics.HOME_LOGO_SIZE_DP), dp(DesignSystem.Metrics.HOME_LOGO_SIZE_DP)))
        hero.addView(label("آرمادیلو", 36f, palette.INK, true).apply { gravity = Gravity.CENTER }, matchParams(top = 4, bottom = 1))
        hero.addView(label("بازی گروهی حدس واژه", 16f, palette.INK).apply { gravity = Gravity.CENTER }, matchParams())
        column.addView(hero, matchParams(bottom = 18))
        column.addView(uiButton("شروع بازی", height = 62, onClick = {
            selectedPlayerCount = store.loadPlayerRoster()?.playerCount ?: 4
            renderPlayerCount()
        }), matchParams(bottom = 10))
        val canContinue = gameState != null && gameState?.phase != GamePhase.RESULTS && store.hasUnfinishedGame()
        if (canContinue) {
            column.addView(uiButton("ادامه بازی قبلی", height = 58, onClick = { continueSavedGame() }), matchParams(bottom = 10))
        }
        column.addView(uiButton("تنظیمات صدا", height = 54, onClick = { openSoundSettings(AppScreen.HOME) }), matchParams(bottom = 10))
        column.addView(uiButton("قوانین بازی", outlined = true, height = 54, onClick = { renderRules() }), matchParams(bottom = 10))
        column.addView(uiButton("درباره بازی", outlined = true, height = 54, onClick = { renderAbout() }), matchParams(bottom = 8))
        column.addView(space(8))
        column.addView(label("آفلاین · رایگان · بدون تبلیغ", 13f, palette.MUTED).apply { gravity = Gravity.CENTER }, matchParams())
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        showPage(scroll)
    }

    private fun continueSavedGame() {
        val restored = store.loadGame()
        if (restored == null) {
            gameState = null
            engine = null
            renderHome()
            return
        }
        gameState = restored
        engine = GameEngine(restored, wordBank, clockNanos = { SystemClock.elapsedRealtimeNanos() })
        renderCurrentGameState()
    }

    private fun renderPlayerCount() {
        screen = AppScreen.PLAYER_COUNT
        val countSelection = selectedPlayerCount
        if (countSelection == null || countSelection !in GameConstants.allowedPlayerCounts) {
            selectedPlayerCount = store.loadPlayerRoster()?.playerCount ?: 4
        }
        val (page, body) = createPage(
            title = "تعداد بازیکنان",
            backAction = { renderHome() },
            footerText = "ادامه به نام بازیکنان",
            footerEnabled = { selectedPlayerCount?.let { it in GameConstants.allowedPlayerCounts } == true },
            footerAction = {
                val count = selectedPlayerCount
                if (count != null && count in GameConstants.allowedPlayerCounts) {
                    val names = store.playerNamesForCount(count)
                    store.savePlayerRoster(names, count)
                    draft = SetupDraft(count, names.toMutableList())
                    renderPlayers()
                }
            }
        )
        body.addView(label("تعداد بازیکنان را انتخاب کنید؛ هر تیم دو نفر دارد و چیدمان از صندلی پایین آغاز می‌شود.", 15f, palette.MUTED), matchParams(bottom = 12))
        GameConstants.allowedPlayerCounts.forEach { count ->
            val selected = count == selectedPlayerCount
            val card = choiceCard(selected)
            card.addView(label("$count بازیکن", 20f, palette.INK, true), matchParams(bottom = 5))
            card.addView(label("${count / 2} تیم · دو نفر در هر تیم", 14f, palette.MUTED), matchParams())
            card.contentDescription = "$count بازیکن، ${if (selected) "انتخاب‌شده" else "انتخاب‌نشده"}"
            card.setOnClickListener {
                audio.playEffect()
                selectedPlayerCount = count
                renderPlayerCount()
            }
            body.addView(card, matchParams(bottom = 9))
        }
        showPage(page)
    }

    private fun backFromPlayers() {
        if (draft?.returnToPreparation == true) renderGamePage() else renderPlayerCount()
    }

    private fun backFromCategories() {
        val current = draft
        if (current?.isReplay == true) renderResultsIfFromReplayOrHome() else renderPlayers()
    }

    private fun renderPlayers() {
        screen = AppScreen.PLAYERS
        val current = draft ?: return renderPlayerCount()
        val (page, body) = createPage(
            title = "نام بازیکنان",
            backAction = { backFromPlayers() },
            footerText = "ادامه به انتخاب دسته‌ها",
            footerAction = { renderCategories() }
        )
        body.addView(label("صندلی پایین دایره شروع‌کنندهٔ دست اول است. هم‌تیمی‌ها روبه‌روی یکدیگر می‌نشینند.", 14f, palette.MUTED), matchParams(bottom = 6))
        val seats = SeatingCircleView(this, current.playerCount, current.names) { seat -> editPlayerName(seat) }
        body.addView(seats, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(365)))
        body.addView(label("برای واردکردن یا ویرایش نام، صندلی را لمس کنید. نام خالی خودکار تکمیل می‌شود.", 13f, palette.MUTED), matchParams(top = 2, bottom = 12))
        showPage(page)
    }

    private fun editPlayerName(seat: Int) {
        val current = draft ?: return
        val input = EditText(this).apply {
            setText(current.names.getOrNull(seat).orEmpty())
            hint = "نام بازیکن ${seat + 1}"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            textDirection = View.TEXT_DIRECTION_RTL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            typeface = lalezarTypeface
            setTextColor(palette.INK)
            setHintTextColor(palette.MUTED)
            backgroundTintList = ColorStateList.valueOf(palette.BLUE)
            minHeight = dp(DesignSystem.Metrics.MIN_TOUCH_TARGET)
            setPadding(dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.INSET_10), dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.INSET_10))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("نام بازیکن ${seat + 1}")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                current.names[seat] = input.text.toString().trim()
                store.savePlayerRoster(current.names, current.playerCount, durable = true)
                renderPlayers()
            }
            .setNegativeButton("انصراف", null)
            .create()
        showDesignDialog(dialog, titleText = "نام بازیکن ${seat + 1}")
    }

    private fun renderCategories() {
        screen = AppScreen.CATEGORIES
        val current = draft ?: return renderPlayerCount()
        val (page, body) = createPage(
            title = "دسته‌بندی واژه‌ها",
            backAction = { backFromCategories() },
            footerText = "ادامه به تنظیمات بازی",
            footerEnabled = { current.categories.isNotEmpty() },
            footerAction = { if (current.categories.isNotEmpty()) renderOptions() }
        )
        body.addView(label("یک یا چند دسته را انتخاب کنید. واژه‌ها از بانک آفلاین و در سه سطح دشواری انتخاب می‌شوند.", 14f, palette.MUTED), matchParams(bottom = 12))
        val categories = listOf(
            Triple(WordCategories.JOBS, "مشاغل", "۱۰۰ واژه در هر سطح"),
            Triple(WordCategories.ANIMALS, "حیوانات", "۱۰۰ واژه در هر سطح"),
            Triple(WordCategories.FOODS, "خوراکی‌ها", "۱۰۰ واژه در هر سطح"),
            Triple(WordCategories.PLACES, "مکان‌ها", "۱۰۰ واژه در هر سطح"),
            Triple(WordCategories.TECHNOLOGY, "تکنولوژی", "۱۰۰ واژه در هر سطح")
        )
        categories.chunked(3).forEach { categoryRow ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            categoryRow.forEach { (id, title, description) ->
                val selected = id in current.categories
                val card = choiceCard(selected).apply { gravity = Gravity.CENTER }
                card.addView(label(if (selected) "✓ $title" else title, 14f, if (selected) palette.BLUE else palette.INK, true).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    maxLines = 2
                }, matchParams(bottom = 5))
                card.addView(label(description, 11f, palette.MUTED).apply {
                    gravity = Gravity.CENTER
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    maxLines = 2
                }, matchParams())
                card.contentDescription = "$title، ${if (selected) "انتخاب‌شده" else "انتخاب‌نشده"}"
                card.setOnClickListener {
                    audio.playEffect()
                    if (id in current.categories) current.categories.remove(id) else current.categories.add(id)
                    renderCategories()
                }
                row.addView(card, LinearLayout.LayoutParams(0, dp(132), 1f).apply {
                    marginStart = dp(DesignSystem.Spacing.INSET_3)
                    marginEnd = dp(DesignSystem.Spacing.INSET_3)
                    bottomMargin = dp(7)
                })
            }
            repeat(3 - categoryRow.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(132), 1f).apply {
                    marginStart = dp(DesignSystem.Spacing.INSET_3)
                    marginEnd = dp(DesignSystem.Spacing.INSET_3)
                    bottomMargin = dp(7)
                })
            }
            body.addView(row, matchParams())
        }
        if (current.categories.isEmpty()) {
            body.addView(label("برای ادامه دست‌کم یک دسته را انتخاب کنید.", 13f, palette.RED, true), matchParams(top = 2, bottom = 8))
        }
        showPage(page)
    }

    private fun renderOptions() {
        screen = AppScreen.OPTIONS
        val current = draft ?: return renderPlayerCount()
        val (page, body) = createPage(
            title = "تنظیمات بازی",
            backAction = { renderCategories() },
            footerText = "رفتن به آماده‌سازی",
            footerAction = { beginGame(current) }
        )

        body.addView(label("سطح دشواری", 18f, palette.INK, true), matchParams(bottom = 8))
        val difficulties = listOf(
            "آسان" to "واژه‌های روزمره و آشنا.",
            "متوسط" to "واژه‌های کم‌آشناتر و موضوعی.",
            "سخت" to "واژه‌های تخصصی‌تر و دشوارتر."
        )
        difficulties.forEachIndexed { index, pair ->
            val level = index + 1
            val card = choiceCard(current.difficulty == level)
            card.addView(label(pair.first, 15f, palette.INK, true), matchParams(bottom = 4))
            card.addView(label(pair.second, 12f, palette.MUTED), matchParams())
            card.setOnClickListener { audio.playEffect(); current.difficulty = level; renderOptions() }
            body.addView(card, matchParams(bottom = 7))
        }

        body.addView(space(10))
        body.addView(label("مدت هر دست", 18f, palette.INK, true), matchParams(bottom = 8))
        val durationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        GameConstants.allowedDurations.forEach { seconds ->
            val selected = current.durationSeconds == seconds
            val option = uiButton(
                "$seconds ثانیه",
                outlined = !selected,
                height = 48
            ) {
                current.durationSeconds = seconds
                renderOptions()
            }
            durationRow.addView(option, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(DesignSystem.Spacing.INSET_3)
                marginEnd = dp(DesignSystem.Spacing.INSET_3)
            })
        }
        body.addView(durationRow, matchParams(bottom = 16))

        body.addView(label("تعداد دست‌ها", 18f, palette.INK, true), matchParams(bottom = 8))
        val roundRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        GameConstants.allowedRounds.forEach { rounds ->
            val selected = current.rounds == rounds
            val option = uiButton(
                "$rounds دست",
                outlined = !selected,
                height = 48
            ) {
                current.rounds = rounds
                renderOptions()
            }
            roundRow.addView(option, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(DesignSystem.Spacing.INSET_3)
                marginEnd = dp(DesignSystem.Spacing.INSET_3)
            })
        }
        body.addView(roundRow, matchParams(bottom = 12))
        body.addView(label("زمان هر دست و تعداد دست‌ها در طول یک بازی ثابت می‌ماند.", 12f, palette.MUTED), matchParams(bottom = 4))
        showPage(page)
    }

    private fun beginGame(setup: SetupDraft) {
        val valid = SeatPlan.isAllowedCount(setup.playerCount) &&
            setup.categories.isNotEmpty() && setup.categories.all { it in WordCategories.all } &&
            setup.difficulty in 1..3 && setup.durationSeconds in GameConstants.allowedDurations &&
            setup.rounds in GameConstants.allowedRounds && setup.names.size == setup.playerCount
        if (!valid) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("تنظیمات ناقص است")
                .setMessage("تعداد بازیکنان، دست‌کم یک دسته، سطح دشواری، مدت و تعداد دست‌ها را بررسی کنید.")
                .setPositiveButton("باشه", null)
                .create()
            showDesignDialog(dialog, titleText = "تنظیمات ناقص است")
            return
        }
        val players = SeatPlan.createPlayers(setup.playerCount, setup.names)
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
        // One atomic preference write replaces the previous incomplete game only after setup is complete.
        store.saveGame(state, durable = true)
        gameState = state
        engine = GameEngine(state, wordBank, clockNanos = { SystemClock.elapsedRealtimeNanos() })
        audio.playEffect()
        renderGamePage()
    }

    private fun renderGamePage() {
        val activeEngine = engine ?: return renderHome()
        val state = activeEngine.state
        screen = AppScreen.GAME
        stopActiveLoops()
        startAnimator?.cancel()
        startAnimator = null
        teamTimeViews = emptyList()
        roundLabelView = null
        currentPlayerView = null
        currentCardView = null
        nextPlayerView = null
        nextCardView = null
        wordView = null
        swapButton = null
        correctButton = null
        pauseButton = null
        startButton = null

        val frame = FrameLayout(this).apply { setBackgroundColor(palette.CANVAS); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(DesignSystem.Spacing.GAME_HORIZONTAL), dp(DesignSystem.Spacing.GAME_TOP), dp(DesignSystem.Spacing.GAME_HORIZONTAL), dp(DesignSystem.Spacing.GAME_BOTTOM))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        val teamRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        val timeViews = mutableListOf<TextView>()
        for (team in 0 until state.teamCount) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                gravity = Gravity.CENTER
                background = rounded(palette.PALE, DesignSystem.Radius.TAG, palette.LINE, 1f)
                setPadding(dp(DesignSystem.Spacing.XXXS), dp(DesignSystem.Spacing.INSET_3), dp(DesignSystem.Spacing.XXXS), dp(DesignSystem.Spacing.INSET_3))
            }
            val swatch = View(this).apply { background = teamSwatch(team) }
            item.addView(swatch, LinearLayout.LayoutParams(dp(DesignSystem.Spacing.XXS), dp(DesignSystem.Spacing.XL)).apply { marginEnd = dp(DesignSystem.Spacing.INSET_3) })
            val time = label("0s", if (state.teamCount >= 5) 9f else if (state.teamCount == 4) 10f else 11f, palette.INK, true)
                .apply { gravity = Gravity.CENTER; maxLines = 1 }
            item.addView(time, LinearLayout.LayoutParams(0, dp(28), 1f))
            teamRow.addView(item, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(1); marginStart = dp(1) })
            timeViews += time
        }
        teamTimeViews = timeViews
        top.addView(teamRow, LinearLayout.LayoutParams(0, dp(44), 1f))
        // One control remains usable in preparation and while manually paused; ending the game
        // remains a separate, explicit action inside the pause overlay/dialog.
        val pauseLabel = if (state.phase == GamePhase.MANUAL_PAUSED) "ادامه بازی" else "توقف موقت"
        val pause = uiButton(pauseLabel, height = DesignSystem.Metrics.MIN_TOUCH_TARGET, playEffect = false) {
            handlePauseControl()
        }.apply {
            pauseButton = this
            isEnabled = true
            contentDescription = if (state.phase == GamePhase.MANUAL_PAUSED) "ادامه بازی" else "توقف موقت بازی"
        }
        top.addView(pause, LinearLayout.LayoutParams(dp(88), dp(DesignSystem.Metrics.MIN_TOUCH_TARGET)).apply { marginStart = dp(DesignSystem.Spacing.XXXS) })
        content.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

        val roundLabel = label("دست ${state.currentRound} از ${state.totalRounds}", 13f, palette.MUTED, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(1), 0, dp(1))
        }
        roundLabelView = roundLabel
        content.addView(roundLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(23)))

        val playersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER
        }
        val currentCard = playerCard(state.currentPlayer.name, state.currentPlayer.teamIndex, secondary = false)
        val arrow = label("→", 26f, palette.MUTED, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "حرکت پادساعت‌گرد به بازیکن بعدی"
        }
        val nextCard = playerCard(state.nextPlayer.name, state.nextPlayer.teamIndex, secondary = true)
        currentCardView = currentCard.first
        currentPlayerView = currentCard.second
        nextCardView = nextCard.first
        nextPlayerView = nextCard.second
        playersRow.addView(currentCard.first, LinearLayout.LayoutParams(0, dp(72), 1f))
        playersRow.addView(arrow, LinearLayout.LayoutParams(dp(34), dp(70)))
        playersRow.addView(nextCard.first, LinearLayout.LayoutParams(0, dp(72), 1f))
        content.addView(playersRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)))

        val card = FrameLayout(this).apply {
            background = rounded(palette.SURFACE, DesignSystem.Radius.GAME_CARD, palette.LINE, DesignSystem.Interaction.DEFAULT_STROKE_DP.toFloat())
            elevation = dp(DesignSystem.Spacing.XXXS).toFloat()
        }
        if (state.phase == GamePhase.PREPARATION) {
            val prepare = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(DesignSystem.Spacing.INSET_18), dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.INSET_18), dp(DesignSystem.Spacing.SM))
            }
            prepare.addView(label("کلمه پس از شروع نمایش داده می‌شود", 16f, palette.MUTED).apply { gravity = Gravity.CENTER }, matchParams(bottom = 16))
            val start = uiButton("شروع", height = 62, playEffect = false) {
                startAnimator?.cancel()
                if (activeEngine.startRound()) {
                    store.saveGame(state, durable = true)
                    audio.playEffect()
                    renderGamePage()
                    startGameLoop()
                }
            }
            startButton = start
            prepare.addView(start, LinearLayout.LayoutParams(dp(220), dp(62)))
            val beforeFirstHand = state.currentRound == 1 && state.usedWords.isEmpty() && state.teamStats.all {
                it.realTimeNanos == 0L && it.penaltyNanos == 0L && it.correctWords == 0 && it.swaps == 0
            }
            if (beforeFirstHand) {
                prepare.addView(uiButton("ویرایش نام بازیکنان", outlined = true, height = DesignSystem.Metrics.MIN_TOUCH_TARGET, playEffect = false) {
                    draft = SetupDraft(
                        playerCount = state.players.size,
                        names = state.players.sortedBy { it.seat }.map { it.name }.toMutableList(),
                        difficulty = state.difficulty,
                        categories = state.selectedCategories.toMutableSet(),
                        durationSeconds = nearestAllowedDuration(state.durationSeconds),
                        rounds = nearestAllowedRounds(state.totalRounds),
                        returnToPreparation = true
                    )
                    renderPlayers()
                }, LinearLayout.LayoutParams(dp(220), dp(DesignSystem.Metrics.MIN_TOUCH_TARGET)).apply { topMargin = dp(DesignSystem.Spacing.SM) })
            }
            card.addView(prepare, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            startAnimator = ObjectAnimator.ofFloat(start, View.ALPHA, 0.62f, 1f).apply {
                duration = 650L
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        } else {
            val word = label(state.currentWord.orEmpty(), 40f, palette.INK, true).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                maxLines = 3
                includeFontPadding = false
                setPadding(dp(DesignSystem.Spacing.INSET_18), dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.INSET_18), dp(DesignSystem.Spacing.SM))
            }
            wordView = word
            fitWordSize(word, state.currentWord.orEmpty())
            card.addView(word, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(DesignSystem.Spacing.INSET_10); bottomMargin = dp(DesignSystem.Spacing.INSET_10) })

        if (state.phase != GamePhase.PREPARATION) {
            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
            }
            val swap = uiButton("تعویض کلمه", height = 68, playEffect = false) {
                val result = activeEngine.swapWord()
                handleActionResult(result)
                if (result.accepted) {
                    store.saveGame(state, durable = true)
                    updateLiveGameUi()
                }
            }
            swapButton = swap
            val correct = uiButton("حدس زد", height = 68, playEffect = false) {
                val result = activeEngine.markCorrect()
                handleActionResult(result)
                if (result.accepted) {
                    audio.playEffect()
                    store.saveGame(state, durable = true)
                    updateLiveGameUi()
                }
            }
            correctButton = correct
            controls.addView(swap, LinearLayout.LayoutParams(0, dp(68), 1f).apply { marginEnd = dp(7) })
            controls.addView(correct, LinearLayout.LayoutParams(0, dp(68), 1f).apply { marginStart = dp(7) })
            content.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)))
        }

        frame.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (state.phase == GamePhase.MANUAL_PAUSED) addManualPauseOverlay(frame)
        updateLiveGameUi()
        showPage(frame)
    }

    private fun addManualPauseOverlay(frame: FrameLayout) {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(palette.SCRIM)
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "بازی متوقف است"
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(palette.SURFACE, DesignSystem.Radius.PAUSE_CARD, palette.LINE, DesignSystem.Interaction.DEFAULT_STROKE_DP.toFloat())
            elevation = dp(DesignSystem.Spacing.MD).toFloat()
            setPadding(dp(DesignSystem.Spacing.XL), dp(DesignSystem.Spacing.MD), dp(DesignSystem.Spacing.XL), dp(DesignSystem.Spacing.MD))
        }
        card.addView(label("توقف موقت", 23f, palette.INK, true).apply { gravity = Gravity.CENTER }, matchParams(bottom = DesignSystem.Spacing.XS))
        card.addView(label("زمان‌ها و بوق‌ها متوقف شده‌اند.", 14f, palette.MUTED).apply { gravity = Gravity.CENTER }, matchParams(bottom = DesignSystem.Spacing.MD))
        val resume = uiButton("ادامه بازی", height = 56, onClick = {
            handlePauseControl()
        }).apply {
            pauseButton = this
            isEnabled = true
            contentDescription = "ادامه بازی"
        }
        card.addView(resume, matchParams(bottom = DesignSystem.Spacing.XS))
        card.addView(uiButton("پایان بازی", outlined = true, height = 54, onClick = { confirmEndGame() }), matchParams(bottom = DesignSystem.Spacing.XS))
        card.addView(uiButton("تنظیمات صدا", height = DesignSystem.Metrics.MIN_TOUCH_TARGET, playEffect = false, onClick = { openSoundSettings(AppScreen.GAME) }), matchParams())
        val cardParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = dp(DesignSystem.Spacing.PAUSE_MARGIN)
            rightMargin = dp(DesignSystem.Spacing.PAUSE_MARGIN)
        }
        overlay.addView(card, cardParams)
        frame.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun playerCard(name: String, team: Int, secondary: Boolean): Pair<LinearLayout, TextView> {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(tint(colorOf(team), if (secondary) 0.93f else 0.86f), DesignSystem.Radius.CONTROL,
                teamEdgeColor(team), if (secondary) 1f else 1.7f)
            setPadding(dp(DesignSystem.Spacing.INSET_6), dp(5), dp(DesignSystem.Spacing.INSET_6), dp(5))
        }
        val nameView = label(name, if (secondary) 14f else 16f, if (secondary) palette.MUTED else palette.INK, true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        container.addView(nameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return container to nameView
    }

    private fun renderSystemPaused() {
        screen = AppScreen.SYSTEM_PAUSED
        pauseButton = null
        stopActiveLoops()
        audio.stopAll()
        val page = FrameLayout(this).apply { setBackgroundColor(palette.CANVAS) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(DesignSystem.Spacing.PAUSE_PANEL), dp(DesignSystem.Spacing.PAUSE_PANEL), dp(DesignSystem.Spacing.PAUSE_PANEL), dp(DesignSystem.Spacing.PAUSE_PANEL))
            background = rounded(palette.SURFACE, DesignSystem.Radius.CARD, palette.LINE, DesignSystem.Interaction.DEFAULT_STROKE_DP.toFloat())
            elevation = dp(DesignSystem.Spacing.XS).toFloat()
        }
        column.addView(label("بازی متوقف است", 28f, palette.INK, true).apply { gravity = Gravity.CENTER }, matchParams(bottom = 12))
        column.addView(label("وضعیت بازی ذخیره شده است. برای ادامه، دکمه را بزنید.", 15f, palette.MUTED).apply { gravity = Gravity.CENTER }, matchParams(bottom = 24))
        val resume = uiButton("ادامه بازی", height = 62, onClick = {
            handlePauseControl()
        }).apply {
            pauseButton = this
            isEnabled = true
            contentDescription = "ادامه بازی"
        }
        column.addView(resume, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))
        page.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = dp(DesignSystem.Spacing.LG)
            rightMargin = dp(DesignSystem.Spacing.LG)
        })
        showPage(page)
    }

    private fun renderExplosionPage(playSound: Boolean) {
        val active = engine ?: return renderHome()
        screen = AppScreen.GAME
        pauseButton = null
        stopActiveLoops()
        explosionTapHandled = false
        val state = active.state
        if (state.phase != GamePhase.EXPLODING) return renderCurrentGameState()
        val team = state.nextRoundStarterIndex.let { state.players.getOrNull(it)?.teamIndex ?: state.currentTeamIndex }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(DesignSystem.Spacing.EXPLOSION_PAGE), dp(DesignSystem.Spacing.EXPLOSION_PAGE), dp(DesignSystem.Spacing.EXPLOSION_PAGE), dp(DesignSystem.Spacing.EXPLOSION_PAGE))
            setBackgroundColor(palette.CANVAS)
            isClickable = true
            isFocusable = true
            contentDescription = "انفجار؛ برای ادامه صفحه را لمس کنید یا توقف موقت را بزنید"
        }
        val blast = label("بوم!", 58f, palette.RED, true).apply { gravity = Gravity.CENTER }
        column.addView(blast, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)))
        val penaltyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
        }
        val penaltySwatch = View(this).apply { background = teamSwatch(team, 6f) }
        penaltyRow.addView(penaltySwatch, LinearLayout.LayoutParams(dp(DesignSystem.Spacing.INSET_14), dp(DesignSystem.Spacing.INSET_22)).apply { marginStart = dp(DesignSystem.Spacing.INSET_6) })
        penaltyRow.addView(label("تیم ${team + 1} جریمهٔ ۱۰ ثانیه‌ای گرفت", 19f, teamLabelColor(team), true).apply { gravity = Gravity.CENTER })
        column.addView(penaltyRow, matchParams(top = 6, bottom = 6))
        column.addView(label("دست ${state.currentRound} پایان یافت", 14f, palette.MUTED).apply { gravity = Gravity.CENTER }, matchParams(bottom = 18))
        column.addView(label("برای ادامه، صفحه را لمس کنید", 15f, palette.BLUE, true).apply { gravity = Gravity.CENTER }, matchParams())
        val pause = uiButton("توقف موقت", height = DesignSystem.Metrics.MIN_TOUCH_TARGET, playEffect = false) {
            handlePauseControl()
        }.apply {
            pauseButton = this
            isEnabled = true
            contentDescription = "توقف موقت بازی"
        }
        column.addView(pause, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(DesignSystem.Metrics.MIN_TOUCH_TARGET)).apply {
            topMargin = dp(DesignSystem.Spacing.XL)
        })

        val scaleX = ObjectAnimator.ofFloat(blast, View.SCALE_X, 0.75f, 1.1f, 0.95f, 1f).apply {
            duration = GameConstants.EXPLOSION_ANIMATION_MS
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(blast, View.SCALE_Y, 0.75f, 1.1f, 0.95f, 1f).apply {
            duration = GameConstants.EXPLOSION_ANIMATION_MS
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        explosionAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
        column.setOnClickListener {
            if (explosionTapHandled || active.state.phase != GamePhase.EXPLODING) return@setOnClickListener
            explosionTapHandled = true
            explosionAnimator?.cancel()
            explosionAnimator = null
            audio.stopAll()
            val event = active.finishExplosion()
            if (event == EngineEvent.GAME_FINISHED || active.state.phase == GamePhase.RESULTS) {
                store.clearGame()
                renderResults()
            } else {
                store.saveGame(active.state, durable = true)
                renderGamePage()
            }
        }
        showPage(column)
        if (playSound) {
            audio.playExplosion()
            vibrateOnExplosion()
        }
    }

    private fun onExplosionStarted() {
        stopActiveLoops()
        audio.stopAll()
        val active = engine ?: return
        store.saveGame(active.state, durable = true)
        renderExplosionPage(playSound = true)
    }

    private fun handleActionResult(result: ActionResult) {
        if (result.event == EngineEvent.EXPLOSION_STARTED) onExplosionStarted()
    }

    private fun startGameLoop() {
        val active = engine ?: return
        if (active.state.phase != GamePhase.PLAYING) return
        mainHandler.removeCallbacks(timerRunnable)
        lastCheckpointMs = SystemClock.elapsedRealtime()
        nextBeepAtNanos = SystemClock.elapsedRealtimeNanos()
        playScheduledBeep()
        mainHandler.post(timerRunnable)
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
        mainHandler.removeCallbacks(timerRunnable)
        audio.stopBeep()
        startAnimator?.cancel()
        startAnimator = null
        explosionAnimator?.cancel()
        explosionAnimator = null
    }

    private fun updateLiveGameUi() {
        val active = engine ?: return
        val state = active.state
        for (team in teamTimeViews.indices) {
            val stats = state.teamStats[team]
            teamTimeViews[team].text = "${stats.finalTimeNanos / GameEngine.NANOS_PER_SECOND}s"
        }
        roundLabelView?.text = "دست ${state.currentRound} از ${state.totalRounds}"
        currentPlayerView?.text = state.currentPlayer.name
        val currentColor = colorOf(state.currentPlayer.teamIndex)
        currentCardView?.background = rounded(tint(currentColor, 0.86f), DesignSystem.Radius.CONTROL, teamEdgeColor(state.currentPlayer.teamIndex), 1.7f)
        currentPlayerView?.setTextColor(palette.INK)
        nextPlayerView?.text = state.nextPlayer.name
        val nextColor = colorOf(state.nextPlayer.teamIndex)
        nextCardView?.background = rounded(tint(nextColor, 0.93f), DesignSystem.Radius.CONTROL, teamEdgeColor(state.nextPlayer.teamIndex), 1f)
        nextPlayerView?.setTextColor(palette.MUTED)
        state.currentWord?.let { word ->
            wordView?.text = word
            wordView?.let { fitWordSize(it, word) }
        }
        val canSwap = active.canSwap()
        swapButton?.isEnabled = canSwap
        val isPlaying = state.phase == GamePhase.PLAYING
        correctButton?.isEnabled = isPlaying
        pauseButton?.text = if (state.phase == GamePhase.MANUAL_PAUSED) "ادامه بازی" else "توقف موقت"
        pauseButton?.contentDescription = if (state.phase == GamePhase.MANUAL_PAUSED) "ادامه بازی" else "توقف موقت بازی"
        pauseButton?.isEnabled = true
    }

    /** Toggle or suspend/resume the game without resetting its checkpoint or timer state. */
    private fun handlePauseControl() {
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
                    renderGamePage()
                }
            }
            GamePhase.MANUAL_PAUSED -> {
                if (active.resumeManually()) {
                    store.saveGame(active.state, durable = true)
                    renderGamePage()
                    startGameLoop()
                }
            }
            GamePhase.PREPARATION, GamePhase.EXPLODING -> {
                active.pauseForSystem()
                audio.stopAll()
                stopActiveLoops()
                store.saveGame(active.state, durable = true)
                renderSystemPaused()
            }
            GamePhase.SYSTEM_PAUSED -> {
                active.resumeFromSystemPause()
                if (active.state.phase == GamePhase.RESULTS) {
                    store.clearGame()
                    renderResults()
                } else {
                    store.saveGame(active.state, durable = true)
                    renderCurrentGameState()
                    if (active.state.phase == GamePhase.PLAYING) startGameLoop()
                }
            }
            GamePhase.RESULTS -> Unit
        }
    }

    private fun confirmEndGame() {
        val active = engine
        var wasPlaying = false
        if (active?.state?.phase == GamePhase.PLAYING) {
            active.pauseForSystem()
            wasPlaying = active.state.phase == GamePhase.SYSTEM_PAUSED
            audio.stopAll()
            stopActiveLoops()
            store.saveGame(active.state, durable = true)
        }
        dialogOpen = true
        val dialog = AlertDialog.Builder(this)
            .setTitle("پایان بازی؟")
            .setMessage("نتایج بر اساس اطلاعات ثبت‌شده تا همین لحظه محاسبه می‌شوند.")
            .setNegativeButton("انصراف") { _, _ ->
                val current = engine
                if (wasPlaying && current?.state?.phase == GamePhase.SYSTEM_PAUSED) {
                    current.resumeFromSystemPause()
                    store.saveGame(current.state, durable = true)
                    renderCurrentGameState()
                    if (current.state.phase == GamePhase.PLAYING) startGameLoop()
                } else renderCurrentGameState()
            }
            .setPositiveButton("پایان بازی") { _, _ ->
                val current = engine ?: return@setPositiveButton
                current.finishEarly()
                audio.stopAll()
                stopActiveLoops()
                store.clearGame()
                renderResults()
            }
            .create()
        showDesignDialog(dialog, titleText = "پایان بازی؟") { dialogOpen = false }
    }

    private fun confirmExitGame() {
        val active = engine
        var wasPlaying = false
        if (active?.state?.phase == GamePhase.PLAYING) {
            active.pauseForSystem()
            wasPlaying = active.state.phase == GamePhase.SYSTEM_PAUSED
            audio.stopAll()
            stopActiveLoops()
            store.saveGame(active.state, durable = true)
        }
        dialogOpen = true
        val dialog = AlertDialog.Builder(this)
            .setTitle("خروج از بازی")
            .setMessage("وضعیت بازی ذخیره می‌شود. با خروج از برنامه، زمان و صدا متوقف می‌مانند.")
            .setNegativeButton("بازگشت به بازی") { _, _ ->
                val current = engine
                if (wasPlaying && current?.state?.phase == GamePhase.SYSTEM_PAUSED) {
                    current.resumeFromSystemPause()
                    store.saveGame(current.state, durable = true)
                    renderCurrentGameState()
                    if (current.state.phase == GamePhase.PLAYING) startGameLoop()
                } else renderCurrentGameState()
            }
            .setPositiveButton("خروج") { _, _ ->
                engine?.let {
                    it.pauseForSystem()
                    store.saveGame(it.state, durable = true)
                }
                audio.stopAll()
                stopActiveLoops()
                finish()
            }
            .create()
        showDesignDialog(dialog, titleText = "خروج از بازی") { dialogOpen = false }
    }

    private fun showHomeExitDialog() {
        dialogOpen = true
        val dialog = AlertDialog.Builder(this)
            .setTitle("خروج از برنامه")
            .setMessage("آیا می‌خواهید از آرمادیلو خارج شوید؟")
            .setNegativeButton("لغو", null)
            .setPositiveButton("خروج") { _, _ -> finish() }
            .create()
        showDesignDialog(dialog, titleText = "خروج از برنامه") { dialogOpen = false }
    }

    private fun showDesignDialog(dialog: AlertDialog, titleText: String? = null, onDismiss: (() -> Unit)? = null) {
        if (onDismiss != null) dialog.setOnDismissListener { onDismiss() }
        titleText?.takeIf { it.isNotBlank() }?.let { title ->
            dialog.setCustomTitle(label(title, DesignSystem.TypeScale.SUBTITLE, palette.INK, bold = true).apply {
                setPadding(dp(DesignSystem.Spacing.MD), dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.MD), dp(DesignSystem.Spacing.XXS))
            })
        }
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
                window.setBackgroundDrawable(rounded(palette.WHITE, DesignSystem.Radius.DIALOG, palette.LINE, DesignSystem.Interaction.DEFAULT_STROKE_DP.toFloat()))
            }
            dialog.findViewById<TextView>(android.R.id.message)?.apply {
                setTextColor(palette.MUTED)
                typeface = lalezarTypeface
                textSize = DesignSystem.TypeScale.BODY_SMALL
                textDirection = View.TEXT_DIRECTION_RTL
            }
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { which ->
                dialog.getButton(which)?.apply {
                    typeface = lalezarBoldTypeface
                    setTextColor(buttonTextColorStateList())
                    background = rippleBackground(
                        palette.BUTTON,
                        palette.BUTTON_OUTLINE,
                        DesignSystem.Interaction.DEFAULT_STROKE_DP.toFloat(),
                        DesignSystem.Radius.BUTTON,
                        palette.BUTTON_RIPPLE
                    )
                    minHeight = dp(DesignSystem.Metrics.MIN_TOUCH_TARGET)
                    setPadding(dp(DesignSystem.Spacing.BUTTON_HORIZONTAL), dp(DesignSystem.Spacing.XXS), dp(DesignSystem.Spacing.BUTTON_HORIZONTAL), dp(DesignSystem.Spacing.XXS))
                }
            }
        }
        dialog.show()
    }

    private fun renderResultsIfFromReplayOrHome() {
        if (gameState?.phase == GamePhase.RESULTS) renderResults() else renderHome()
    }

    private fun renderResults() {
        val state = engine?.state ?: gameState ?: return renderHome()
        screen = AppScreen.RESULTS
        stopActiveLoops()
        val ranking = rankTeams(state.teamStats)
        val winners = ranking.filter { it.rank == 1 }
        val (page, body) = createPage("نتایج بازی", { returnToHome() })
        val winnerCard = sectionCard(fillColor = palette.PALE_ACCENT, stroke = palette.BLUE)
        winnerCard.addView(label(if (winners.size == 1) "تیم برنده" else "تساوی در رتبهٔ اول", 17f, palette.INK, true), matchParams(bottom = 8))
        winners.forEach { winner ->
            val teamRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_VERTICAL }
            val swatch = View(this).apply { background = teamSwatch(winner.teamIndex, 5f) }
            teamRow.addView(swatch, LinearLayout.LayoutParams(dp(DesignSystem.Spacing.INSET_10), dp(32)).apply { marginEnd = dp(DesignSystem.Spacing.XXS) })
            val winnerLabel = label("تیم ${winner.teamIndex + 1}", 18f, teamLabelColor(winner.teamIndex), true)
            teamRow.addView(winnerLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            teamRow.addView(label(formatFinalTime(winner.stats.finalTimeNanos), 16f, palette.INK, true))
            winnerCard.addView(teamRow, matchParams(bottom = 3))
            val winnerNames = state.players.filter { it.teamIndex == winner.teamIndex }.sortedBy { it.seat }.joinToString(" و ") { it.name }
            winnerCard.addView(label(winnerNames, 13f, palette.MUTED), matchParams(bottom = 7))
        }
        body.addView(winnerCard, matchParams(bottom = 14))

        body.addView(label("رتبه‌بندی تیم‌ها", 18f, palette.INK, true), matchParams(bottom = 8))
        ranking.forEach { row ->
            val card = sectionCard()
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(label("رتبهٔ ${row.rank}", 13f, palette.MUTED, true), LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT))
            val colorBar = View(this).apply { background = teamSwatch(row.teamIndex, 4f) }
            top.addView(colorBar, LinearLayout.LayoutParams(dp(DesignSystem.Spacing.XS), dp(30)).apply { marginEnd = dp(5); marginStart = dp(5) })
            top.addView(label("تیم ${row.teamIndex + 1}", 16f, teamLabelColor(row.teamIndex), true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(label(formatFinalTime(row.stats.finalTimeNanos), 15f, palette.INK, true))
            card.addView(top, matchParams(bottom = 6))
            val teamNames = state.players.filter { it.teamIndex == row.teamIndex }.sortedBy { it.seat }.joinToString(" و ") { it.name }
            card.addView(label("بازیکنان: $teamNames", 13f, palette.INK, true), matchParams(bottom = 4))
            card.addView(label("کلمات صحیح: ${row.stats.correctWords}     جریمه‌های انفجار: ${row.stats.explosions}", 13f, palette.MUTED), matchParams())
            body.addView(card, matchParams(bottom = 8))
        }
        body.addView(label("زمان نهایی = زمان واقعی نگه‌داشتن گوشی + جریمه‌های انفجار", 12f, palette.MUTED), matchParams(top = 3, bottom = 12))
        body.addView(label("فهرست واژه‌ها · به‌ترتیب نمایش", 18f, palette.INK, true), matchParams(bottom = 7))
        if (state.wordHistory.isEmpty()) {
            body.addView(label("در این بازی هنوز واژه‌ای ثبت نشده است.", 13f, palette.MUTED), matchParams(bottom = 12))
        } else {
            val historyCard = sectionCard()
            state.wordHistory.forEachIndexed { index, entry ->
                val player = state.players.getOrNull(entry.playerSeat)
                val playerName = player?.name ?: "بازیکن نامشخص"
                val teamText = player?.let { "تیم ${it.teamIndex + 1}" } ?: "تیم نامشخص"
                historyCard.addView(label("${index + 1}. ${entry.word}", 15f, palette.INK, true), matchParams(bottom = 1))
                historyCard.addView(label(
                    "$playerName · $teamText · دست ${entry.round} · ${formatWordTimestamp(entry.shownAtEpochMillis)} · ${wordOutcomeLabel(entry.outcome)}",
                    12f,
                    palette.MUTED
                ), matchParams(bottom = 8))
            }
            body.addView(historyCard, matchParams(bottom = 12))
        }
        body.addView(uiButton("بازی دوباره با همین بازیکنان", height = 58, onClick = { replayWithSamePlayers(state) }), matchParams(bottom = 10))
        body.addView(uiButton("بازگشت به صفحه اصلی", outlined = true, height = 54, onClick = { returnToHome() }), matchParams())
        showPage(page)
    }

    private fun replayWithSamePlayers(state: GameState) {
        draft = SetupDraft(
            playerCount = state.players.size,
            names = state.players.sortedBy { it.seat }.map { it.name }.toMutableList(),
            difficulty = state.difficulty,
            categories = state.selectedCategories.toMutableSet(),
            durationSeconds = nearestAllowedDuration(state.durationSeconds),
            rounds = nearestAllowedRounds(state.totalRounds),
            isReplay = true
        )
        store.clearGame()
        renderCategories()
    }

    private fun returnToHome() {
        store.clearGame()
        gameState = null
        engine = null
        draft = null
        renderHome()
    }

    private fun renderRules() {
        screen = AppScreen.RULES
        val (page, body) = createPage("قوانین بازی", { renderHome() })
        val sections = listOf(
            "هدف بازی" to "کلمه را به هم‌تیمی‌ها برسانید تا آن را حدس بزنند؛ گفتن خود واژه یا بخشی از آن مجاز نیست. برنده تیمی است که در پایان کمترین زمان نهایی را داشته باشد.",
            "بازیکنان و تیم‌ها" to "برای ۴، ۶، ۸ یا ۱۰ نفر؛ از ۲ تا ۵ تیمِ دونفره. هم‌تیمی‌ها روبه‌روی هم می‌نشینند. صندلی پایین آغازگر است و نوبت‌ها پادساعت‌گرد می‌چرخند.",
            "دسته‌ها و سطح واژه‌ها" to "پیش از شروع، یک یا چند دسته از مشاغل، حیوانات، خوراکی‌ها، مکان‌ها و تکنولوژی انتخاب کنید. واژه‌های عمومی در چرخهٔ بازی نیستند. هر دسته ۱۰۰ واژه در هرکدام از سه سطح آسان، متوسط و سخت دارد.",
            "حدس درست و انتقال گوشی" to "به‌محض حدس درست، بازیکن «حدس زد» را می‌زند. کلمه با نام بازیکن، زمان نمایش و نتیجه ثبت می‌شود و گوشی به نفر بعدی در مسیر پادساعت‌گرد می‌رسد؛ زمان تیم جدید همان لحظه شروع می‌شود.",
            "تعویض کلمه" to "دکمهٔ «تعویض کلمه» در ۷ ثانیهٔ اول پس از نمایش هر کلمه غیرفعال است. بعد از آن می‌توان کلمه را عوض کرد؛ کلمهٔ قبلی مصرف‌شده می‌ماند، اما پاسخ صحیح نیست و تیم یک تعویض می‌گیرد.",
            "پایان زمان و انفجار" to "زمان دست در پس‌زمینهٔ بازی محاسبه می‌شود و نمایش داده نمی‌شود. با پایان زمان، تیمی که گوشی دستش است ۱۰ ثانیه جریمه می‌گیرد. انیمیشن انفجار تا لمس صفحه باقی می‌ماند؛ لمس، دست بعد را آغاز می‌کند یا در آخرین دست نتایج را نشان می‌دهد.",
            "وقفه‌ها و ادامه" to "با رفتن برنامه به پس‌زمینه، قفل صفحه یا وقفهٔ سیستمی، شمارش معکوس، زمان تیم‌ها و صدا متوقف می‌شوند. صفحهٔ ادامهٔ بازی، وضعیت ذخیره‌شده را از همان زمان باقی‌مانده بازمی‌گرداند؛ زمان بیرون از برنامه حساب نمی‌شود. تنظیمات صدا از صفحهٔ اصلی در دسترس است.",
            "پایان و رتبه‌بندی" to "زمان نهایی هر تیم برابر است با زمان واقعی گوشی‌داشتن به‌علاوهٔ جریمه‌ها. ابتدا زمان کمتر، سپس انفجار کمتر، پاسخ صحیح بیشتر و تعویض کمتر ملاک است. نتایج نام همهٔ اعضای تیم، رنگ تیم و امتیازهای همان بازی را نشان می‌دهد؛ تساوی کامل، رتبهٔ مشترک دارد. پایان زودهنگام نیز نتایج ثبت‌شده تا همان لحظه را محاسبه می‌کند."
        )
        sections.forEach { (title, description) ->
            body.addView(label(title, 17f, palette.INK, true), matchParams(top = 8, bottom = 4))
            body.addView(label(description, 14f, palette.MUTED), matchParams(bottom = 7))
        }
        showPage(page)
    }

    private fun renderAbout() {
        screen = AppScreen.ABOUT
        val (page, body) = createPage("درباره بازی", { renderHome() })
        val version = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
        body.addView(space(14))
        body.addView(label("آرمادیلو (Armadillo)", 28f, palette.BLUE, true), matchParams(bottom = 12))
        if (version != null) {
            body.addView(label("نسخهٔ $version", 16f, palette.INK, true), matchParams(bottom = 14))
        }
        body.addView(label("یک بازی گروهی، آفلاین و فارسی برای توضیح و حدس‌زدن واژه‌ها. نام بازیکنان، زمان‌سنج و وضعیت بازی روی همین دستگاه نگهداری می‌شوند.", 15f, palette.MUTED), matchParams(bottom = 16))
        body.addView(label("رایگان · بدون تبلیغ", 13f, palette.MUTED), matchParams())
        showPage(page)
    }

    private fun openSoundSettings(returnTo: AppScreen) {
        settingsReturnScreen = returnTo
        renderSoundSettings()
    }

    private fun renderSoundSettings() {
        screen = AppScreen.SOUND_SETTINGS
        val (page, body) = createPage("تنظیمات صدا", { returnFromSoundSettings() })
        body.addView(label("تغییرها بلافاصله ذخیره و اعمال می‌شوند. هنگام رفتن بازی به پس‌زمینه، شمارش معکوس و صدا متوقف می‌شوند.", 13f, palette.MUTED), matchParams(bottom = 12))
        addSoundControl(
            body, "بوق بمب", soundSettings.beepEnabled, soundSettings.beepVolume,
            enabledUpdate = { value -> soundSettings.beepEnabled = value; persistSoundSettings() },
            volumeUpdate = { value -> soundSettings.beepVolume = value; persistSoundSettings() },
            preview = { audio.previewBeep() }
        )
        addSoundControl(
            body, "صدای انفجار", soundSettings.explosionEnabled, soundSettings.explosionVolume,
            enabledUpdate = { value -> soundSettings.explosionEnabled = value; persistSoundSettings() },
            volumeUpdate = { value -> soundSettings.explosionVolume = value; persistSoundSettings() },
            preview = { audio.previewExplosion() }
        )
        addSoundControl(
            body, "جلوه‌های صوتی دیگر", soundSettings.effectsEnabled, soundSettings.effectsVolume,
            enabledUpdate = { value -> soundSettings.effectsEnabled = value; persistSoundSettings() },
            volumeUpdate = { value -> soundSettings.effectsVolume = value; persistSoundSettings() },
            preview = { audio.previewEffect() }
        )
        body.addView(toggleRow("قطع کامل صدای برنامه", soundSettings.muteAll) { value -> soundSettings.muteAll = value; persistSoundSettings() }, matchParams(top = 4, bottom = 10))
        body.addView(toggleRow("لرزش هنگام انفجار", soundSettings.vibrateOnExplosion) { value -> soundSettings.vibrateOnExplosion = value; persistSoundSettings() }, matchParams(bottom = 14))
        body.addView(label("در حالت بی‌صدا یا قطع بودن صدای رسانهٔ گوشی، صداهای بازی و نمونه‌ها نیز پخش نمی‌شوند.", 12f, palette.MUTED), matchParams(bottom = 8))
        showPage(page)
    }

    private fun addSoundControl(
        body: LinearLayout,
        title: String,
        enabled: Boolean,
        volume: Int,
        enabledUpdate: (Boolean) -> Unit,
        volumeUpdate: (Int) -> Unit,
        preview: () -> Unit
    ) {
        val card = sectionCard()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(title, 16f, palette.INK, true), LinearLayout.LayoutParams(0, dp(DesignSystem.Metrics.MIN_TOUCH_TARGET), 1f))
        header.addView(uiButton("پخش نمونه", height = DesignSystem.Metrics.MIN_TOUCH_TARGET, playEffect = false, onClick = preview), LinearLayout.LayoutParams(dp(104), dp(DesignSystem.Metrics.MIN_TOUCH_TARGET)))
        card.addView(header, matchParams(bottom = 4))
        val check = CheckBox(this).apply {
            text = "فعال باشد"
            textSize = DesignSystem.TypeScale.label(13f)
            typeface = lalezarTypeface
            setTextColor(palette.MUTED)
            isChecked = enabled
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            buttonTintList = ColorStateList.valueOf(palette.BLUE)
            setOnCheckedChangeListener { _, checked -> enabledUpdate(checked); audio.applySettings(soundSettings) }
        }
        card.addView(check, matchParams())
        val volumeLabel = label("بلندی صدا: $volume٪", 12f, palette.MUTED)
        card.addView(volumeLabel, matchParams(top = 2))
        val seek = SeekBar(this).apply {
            max = 100
            progress = volume
            progressTintList = ColorStateList.valueOf(palette.BLUE)
            thumbTintList = ColorStateList.valueOf(palette.BLUE)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val amount = progress.coerceIn(0, 100)
                volumeLabel.text = "بلندی صدا: $amount٪"
                volumeUpdate(amount)
                audio.applySettings(soundSettings)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        card.addView(seek, matchParams())
        body.addView(card, matchParams(bottom = 9))
    }

    private fun toggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(palette.PALE, DesignSystem.Radius.CONTROL, palette.LINE, 1f)
            setPadding(dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.INSET_6), dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.INSET_6))
        }
        box.addView(label(title, 14f, palette.INK), LinearLayout.LayoutParams(0, dp(46), 1f))
        val check = CheckBox(this).apply {
            isChecked = checked
            buttonTintList = ColorStateList.valueOf(palette.BLUE)
            setOnCheckedChangeListener { _, value -> onChange(value); audio.applySettings(soundSettings) }
        }
        box.addView(check, LinearLayout.LayoutParams(dp(48), dp(48)))
        return box
    }

    private fun persistSoundSettings() {
        store.saveSoundSettings(soundSettings)
        audio.applySettings(soundSettings)
    }

    private fun returnFromSoundSettings() {
        when (settingsReturnScreen) {
            AppScreen.GAME -> {
                renderCurrentGameState()
                if (engine?.state?.phase == GamePhase.PLAYING) startGameLoop()
            }
            AppScreen.HOME -> renderHome()
            else -> renderHome()
        }
    }

    private fun vibrateOnExplosion() {
        if (!soundSettings.vibrateOnExplosion) return
        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(180L)
        } catch (_: Exception) {
            // Devices without vibration support continue normally.
        }
    }

    private fun label(text: String, sizeSp: Float, color: Int = palette.INK, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        setTextColor(color)
        textSize = DesignSystem.TypeScale.label(sizeSp)
        typeface = if (bold) lalezarBoldTypeface else lalezarTypeface
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        textDirection = View.TEXT_DIRECTION_RTL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        includeFontPadding = false
        setLineSpacing(dp(DesignSystem.Metrics.LINE_SPACING_DP).toFloat(), 1f)
    }

    private fun uiButton(
        text: String,
        outlined: Boolean = false,
        height: Int = 54,
        playEffect: Boolean = true,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = DesignSystem.TypeScale.button(height)
        typeface = lalezarBoldTypeface
        setTextColor(buttonTextColorStateList())
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        textDirection = View.TEXT_DIRECTION_RTL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        minHeight = dp(maxOf(height, DesignSystem.Metrics.MIN_TOUCH_TARGET))
        isClickable = true
        isFocusable = true
        background = rippleBackground(
            palette.BUTTON,
            if (outlined) palette.BUTTON_OUTLINE else null,
            if (outlined) DesignSystem.Interaction.DEFAULT_STROKE_DP.toFloat() else 0f,
            DesignSystem.Radius.BUTTON,
            palette.BUTTON_RIPPLE
        )
        setPadding(dp(DesignSystem.Spacing.BUTTON_HORIZONTAL), dp(DesignSystem.Spacing.BUTTON_VERTICAL), dp(DesignSystem.Spacing.BUTTON_HORIZONTAL), dp(DesignSystem.Spacing.BUTTON_VERTICAL))
        setOnClickListener {
            if (playEffect) audio.playEffect()
            onClick()
        }
    }

    private fun buttonTextColorStateList() = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(palette.BUTTON_DISABLED_TEXT, palette.BUTTON_TEXT)
    )

    private fun sectionCard(fillColor: Int = palette.SURFACE, stroke: Int = palette.LINE): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        background = rounded(fillColor, DesignSystem.Radius.CARD, stroke, DesignSystem.Interaction.DEFAULT_STROKE_DP.toFloat())
        setPadding(dp(DesignSystem.Spacing.CARD_HORIZONTAL), dp(DesignSystem.Spacing.CARD_VERTICAL), dp(DesignSystem.Spacing.CARD_HORIZONTAL), dp(DesignSystem.Spacing.CARD_VERTICAL))
    }

    private fun choiceCard(selected: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        background = rippleBackground(
            if (selected) tint(palette.BLUE, 0.92f) else palette.WHITE,
            if (selected) palette.BLUE else palette.LINE,
            if (selected) DesignSystem.Interaction.SELECTED_STROKE_DP.toFloat() else DesignSystem.Interaction.DEFAULT_STROKE_DP.toFloat(),
            DesignSystem.Radius.CONTROL
        )
        setPadding(dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.INSET_10), dp(DesignSystem.Spacing.SM), dp(DesignSystem.Spacing.INSET_10))
        isClickable = true
        isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null, strokeDp: Float = 0f): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
        if (strokeColor != null && strokeDp > 0f) setStroke(dp(strokeDp.toInt()), strokeColor)
    }

    private fun rippleBackground(
        color: Int,
        strokeColor: Int?,
        strokeDp: Float,
        radiusDp: Float,
        rippleColor: Int = palette.RIPPLE
    ): RippleDrawable {
        val content = rounded(color, radiusDp, strokeColor, strokeDp)
        val mask = rounded(Color.WHITE, radiusDp)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    private fun tint(color: Int, fraction: Float): Int {
        val r = Color.red(color) + ((255 - Color.red(color)) * fraction).toInt()
        val g = Color.green(color) + ((255 - Color.green(color)) * fraction).toInt()
        val b = Color.blue(color) + ((255 - Color.blue(color)) * fraction).toInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun colorOf(team: Int): Int = GameConstants.teamColors[team.coerceIn(0, GameConstants.teamColors.lastIndex)]

    // Keep every team color byte-for-byte; the fifth team's gold marker gets a navy edge on the yellow canvas.
    private fun teamEdgeColor(team: Int): Int = if (team == DesignSystem.Metrics.TEAM_YELLOW_INDEX) palette.INK else colorOf(team)

    private fun teamLabelColor(@Suppress("UNUSED_PARAMETER") team: Int): Int = palette.INK

    private fun teamSwatch(team: Int, radiusDp: Float = DesignSystem.Radius.CHIP): GradientDrawable {
        val isYellowTeam = team == DesignSystem.Metrics.TEAM_YELLOW_INDEX
        return rounded(
            colorOf(team),
            radiusDp,
            if (isYellowTeam) palette.INK else null,
            if (isYellowTeam) DesignSystem.Interaction.TEAM_YELLOW_STROKE_DP else 0f
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun space(heightDp: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(heightDp)) }

    private fun matchParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun fitWordSize(view: TextView, word: String) {
        view.textSize = DesignSystem.TypeScale.word(word.length)
    }

    private fun formatFinalTime(nanos: Long): String {
        val seconds = nanos.coerceAtLeast(0L) / 1_000_000_000.0
        return DecimalFormat("0.00").format(seconds) + " ثانیه"
    }

    private fun formatWordTimestamp(epochMillis: Long): String =
        if (epochMillis <= 0L) "زمان نامشخص"
        else SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))

    private fun wordOutcomeLabel(outcome: WordOutcome): String = when (outcome) {
        WordOutcome.PENDING -> "در جریان"
        WordOutcome.CORRECT -> "حدس درست"
        WordOutcome.SWAPPED -> "تعویض‌شده"
        WordOutcome.EXPIRED -> "پایان زمان"
        WordOutcome.ENDED -> "پایان بازی"
    }

    private fun nearestAllowedDuration(value: Int): Int =
        GameConstants.allowedDurations.minByOrNull { kotlin.math.abs(it - value) } ?: 120

    private fun nearestAllowedRounds(value: Int): Int =
        GameConstants.allowedRounds.minByOrNull { kotlin.math.abs(it - value) } ?: 5

    override fun onDestroy() {
        stopActiveLoops()
        if (::audio.isInitialized) audio.release()
        super.onDestroy()
    }

    private data class PaletteTokens(private val colors: DesignColors) {
        val WHITE = colors.surfaceRaised
        val SURFACE = colors.surface
        val CANVAS = colors.canvas
        val YELLOW = colors.brandYellow
        val INK = colors.textPrimary
        val MUTED = colors.textSecondary
        val LINE = colors.outline
        val PALE = colors.surfaceAlt
        val PALE_ACCENT = colors.surfaceAccent
        val BLUE = colors.primary
        val BUTTON = colors.buttonBackground
        val BUTTON_TEXT = colors.buttonText
        val BUTTON_DISABLED_TEXT = colors.buttonDisabledText
        val BUTTON_OUTLINE = colors.buttonOutline
        val BUTTON_RIPPLE = colors.buttonRipple
        val GREEN = colors.success
        val RED = colors.danger
        val SCRIM = colors.scrim
        val RIPPLE = colors.ripple
    }
}
