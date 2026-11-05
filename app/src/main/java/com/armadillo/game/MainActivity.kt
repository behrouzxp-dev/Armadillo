package com.armadillo.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.armadillo.game.audio.GameAudio
import com.armadillo.game.data.LocalStore
import com.armadillo.game.ui.ArmadilloApp
import com.armadillo.game.ui.theme.ArmadilloTheme

/**
 * Thin host: the activity only wires the platform lifecycle to [GameController] and
 * hosts the Compose tree. All screens, dialogs and the timer live elsewhere.
 */
class MainActivity : ComponentActivity() {
    private lateinit var controller: GameController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is enforced from Android 15 and mandatory when targeting API 36.
        // The light system-bar style keeps the yellow canvas readable on every version.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(CANVAS_COLOR, CANVAS_COLOR),
            navigationBarStyle = SystemBarStyle.light(CANVAS_COLOR, CANVAS_COLOR)
        )
        val store = LocalStore(this)
        val audio = GameAudio(this, store.loadSoundSettings())
        controller = GameController(this, store, audio)
        controller.start(savedInstanceState)
        // Predictive back (API 33+) and the legacy back button both arrive here, so a
        // single OnBackPressedCallback covers every supported version.
        onBackPressedDispatcher.addCallback(this) { controller.handleBack() }
        setContent {
            ArmadilloTheme {
                ArmadilloApp(controller)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
    }

    override fun onPause() {
        controller.onPause()
        super.onPause()
    }

    override fun onStop() {
        controller.onStop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        controller.onWindowFocusChanged(hasFocus)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        controller.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        controller.dispose()
        super.onDestroy()
    }

    private companion object {
        /** The canvas colour sampled from the launcher icon (design_brand_yellow). */
        const val CANVAS_COLOR = 0xFFF9CB03.toInt()
    }
}
