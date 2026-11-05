package com.armadillo.game

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
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
        registerBackHandler()
        setContent {
            ArmadilloTheme {
                ArmadilloApp(controller)
            }
        }
    }

    /**
     * Targeting API 36 turns predictive back on by default, and the platform no
     * longer calls onBackPressed(). Registering an OnBackInvokedCallback keeps the
     * in-app back navigation working on Android 13 and newer.
     */
    private fun registerBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { controller.handleBack() }
        }
    }

    // API 33+ back gestures are served by the platform callback above; this override only
    // covers API 23-32.
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        controller.handleBack()
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
