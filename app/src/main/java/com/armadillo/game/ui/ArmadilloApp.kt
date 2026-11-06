package com.armadillo.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.armadillo.game.GameController
import com.armadillo.game.ui.components.LocalSoundEffect
import com.armadillo.game.ui.screens.CategoriesScreen
import com.armadillo.game.ui.screens.ExplosionScreen
import com.armadillo.game.ui.screens.GameScreen
import com.armadillo.game.ui.screens.HomeScreen
import com.armadillo.game.ui.screens.OptionsScreen
import com.armadillo.game.ui.screens.PlayerCountScreen
import com.armadillo.game.ui.screens.PlayersScreen
import com.armadillo.game.ui.screens.ResultsScreen
import com.armadillo.game.ui.screens.RulesScreen
import com.armadillo.game.ui.screens.SoundSettingsScreen
import com.armadillo.game.ui.screens.SystemPausedScreen
import com.armadillo.game.ui.theme.BrandPalette

/**
 * Single Compose host for every screen and dialog. Insets are applied once, here,
 * which also replaces the old per-page WindowInsets listener.
 */
@Composable
fun ArmadilloApp(controller: GameController) {
    // The app is Persian-only, so do not inherit LTR from the device language.
    // Scoped exceptions preserve intentional physical ordering in game controls.
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalSoundEffect provides { controller.playEffect() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandPalette.Canvas)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (controller.screen) {
                Screen.HOME -> HomeScreen(controller)
                Screen.PLAYER_COUNT -> PlayerCountScreen(controller)
                Screen.PLAYERS -> PlayersScreen(controller)
                Screen.CATEGORIES -> CategoriesScreen(controller)
                Screen.OPTIONS -> OptionsScreen(controller)
                Screen.SOUND_SETTINGS -> SoundSettingsScreen(controller)
                Screen.RULES -> RulesScreen(controller)
                Screen.GAME -> GameScreen(controller)
                Screen.EXPLOSION -> ExplosionScreen(controller)
                Screen.SYSTEM_PAUSED -> SystemPausedScreen(controller)
                Screen.RESULTS -> ResultsScreen(controller)
            }
        }
        controller.dialog?.let { open -> GameDialog(controller, open) }
    }
}
