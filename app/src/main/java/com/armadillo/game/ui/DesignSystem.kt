package com.armadillo.game.ui

import android.content.Context
import android.graphics.Typeface
import com.armadillo.game.R

/** Shared visual tokens for every Armadillo screen and custom view. */
data class DesignColors(
    val canvas: Int,
    val brandYellow: Int,
    val brandNavy: Int,
    val surface: Int,
    val surfaceRaised: Int,
    val surfaceAlt: Int,
    val surfaceAccent: Int,
    val primary: Int,
    val buttonBackground: Int,
    val buttonOutlinedFill: Int,
    val buttonText: Int,
    val buttonDisabledText: Int,
    val buttonOutline: Int,
    val buttonRipple: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val outline: Int,
    val success: Int,
    val danger: Int,
    val dangerSurface: Int,
    val scrim: Int,
    val ripple: Int
)

object DesignSystem {
    /** The palette is backed by named Android color resources; the icon artwork is never edited. */
    fun colors(context: Context): DesignColors = DesignColors(
        canvas = context.getColor(R.color.design_canvas),
        brandYellow = context.getColor(R.color.design_brand_yellow),
        brandNavy = context.getColor(R.color.design_brand_navy),
        surface = context.getColor(R.color.design_surface),
        surfaceRaised = context.getColor(R.color.design_surface_raised),
        surfaceAlt = context.getColor(R.color.design_surface_alt),
        surfaceAccent = context.getColor(R.color.design_surface_accent),
        primary = context.getColor(R.color.design_primary),
        buttonBackground = context.getColor(R.color.design_button_background),
        buttonOutlinedFill = context.getColor(R.color.design_button_outlined_fill),
        buttonText = context.getColor(R.color.design_button_text),
        buttonDisabledText = context.getColor(R.color.design_button_disabled_text),
        buttonOutline = context.getColor(R.color.design_button_outline),
        buttonRipple = context.getColor(R.color.design_button_ripple),
        textPrimary = context.getColor(R.color.design_text_primary),
        textSecondary = context.getColor(R.color.design_text_secondary),
        outline = context.getColor(R.color.design_outline),
        success = context.getColor(R.color.design_success),
        danger = context.getColor(R.color.design_danger),
        dangerSurface = context.getColor(R.color.design_danger_surface),
        scrim = context.getColor(R.color.design_scrim),
        ripple = context.getColor(R.color.design_ripple)
    )

    /**
     * The bundled Mikhak typeface (SIL OFL 1.1) in two real weights. Mikhak ships
     * static masters, so bold text uses the actual Bold file instead of a
     * synthesized weight. Both files are cached per process by [fontCache].
     */
    fun typeface(context: Context, bold: Boolean = false): Typeface =
        fontCache.getOrPut(bold) {
            Typeface.createFromAsset(
                context.applicationContext.assets,
                if (bold) "fonts/Mikhak-Bold.ttf" else "fonts/Mikhak-Regular.ttf"
            )
        }

    private val fontCache = HashMap<Boolean, Typeface>()

    object Spacing {
        const val XXXS = 2
        const val XXS = 4
        const val XS = 8
        const val SM = 12
        const val MD = 16
        const val LG = 20
        const val XL = 24
        const val XXL = 32
        const val PAGE_SIDE = 20
        const val PAGE_TOP = 8
        const val PAGE_BOTTOM = 12
        const val CARD_HORIZONTAL = 14
        const val CARD_VERTICAL = 12
        const val BUTTON_HORIZONTAL = 14
        const val BUTTON_VERTICAL = 8
        const val PAUSE_MARGIN = 30
        const val HOME_SIDE = 24
        const val HOME_TOP = 22
        const val HOME_BOTTOM = 24
        const val INSET_3 = 3
        const val INSET_6 = 6
        const val INSET_10 = 10
        const val INSET_14 = 14
        const val INSET_18 = 18
        const val INSET_22 = 22
        const val GAME_HORIZONTAL = 13
        const val GAME_TOP = 8
        const val GAME_BOTTOM = 12
        const val PAUSE_PANEL = 28
        const val EXPLOSION_PAGE = 26
    }

    object Radius {
        const val CHIP = 8f
        const val CONTROL = 14f
        const val TAG = 12f
        const val BUTTON = 16f
        const val CARD = 18f
        const val HERO = 24f
        const val GAME_CARD = 22f
        const val DIALOG = 22f
        const val PAUSE_CARD = 22f
    }

    object Metrics {
        const val MIN_TOUCH_TARGET = 48
        const val BUTTON_MIN_HEIGHT = 48
        const val PAGE_HEADER = 56
        const val PAGE_HEADER_CONTENT = 52
        const val HOME_LOGO_SIZE_DP = 124
        const val SEAT_HIT_RADIUS_DP = 40
        const val SEAT_MARKER_OFFSET_DP = 21
        const val SEAT_MARKER_RADIUS_DP = 4f
        const val SEAT_MARKER_OUTLINE_RADIUS_DP = 4.8f
        const val SEAT_DETAIL_BASELINE_DP = 15
        const val LINE_SPACING_DP = 2
        const val DISABLED_ALPHA = 0.46f
        const val UNAVAILABLE_ALPHA = 0.52f
        const val GAME_HORIZONTAL_PADDING = 13
        const val TEAM_YELLOW_INDEX = 4
    }

    object TypeScale {
        const val TINY = 10f
        const val CAPTION = 12f
        const val BODY_SMALL = 14f
        const val BODY = 16f
        const val SECTION = 18f
        const val SUBTITLE = 20f
        const val TITLE = 24f
        const val DISPLAY = 28f
        const val HERO = 36f
        const val EXPLOSION = 58f
        const val WORD_XL = 44f
        const val WORD_LARGE = 38f
        const val WORD_MEDIUM = 32f
        const val WORD_SMALL = 27f
        const val WORD_XS = 23f
        const val BUTTON = 14f
        const val BUTTON_LARGE = 17f
        const val BUTTON_COMPACT = 12f
        const val SEAT_NAME_SMALL = 10f
        const val SEAT_NAME_MEDIUM = 11f
        const val SEAT_NAME_LARGE = 12f
        const val SEAT_DETAIL = 10f
        const val SEAT_CENTER = 14f

        /** Convert old per-call labels to a small, centrally managed type scale. */
        fun label(requestedSp: Float): Float = when {
            requestedSp >= 56f -> EXPLOSION
            requestedSp >= 40f -> WORD_XL
            requestedSp >= 34f -> HERO
            requestedSp >= 26f -> DISPLAY
            requestedSp >= 23f -> TITLE
            requestedSp >= 19f -> SUBTITLE
            requestedSp >= 17f -> SECTION
            requestedSp >= 15f -> BODY
            requestedSp >= 13f -> BODY_SMALL
            requestedSp >= 11f -> CAPTION
            else -> TINY
        }

        fun button(heightDp: Int): Float = when {
            heightDp < 44 -> BUTTON_COMPACT
            heightDp >= 60 -> BUTTON_LARGE
            else -> BUTTON
        }

        fun word(length: Int): Float = when {
            length <= 8 -> WORD_XL
            length <= 13 -> WORD_LARGE
            length <= 18 -> WORD_MEDIUM
            length <= 25 -> WORD_SMALL
            else -> WORD_XS
        }
    }

    object Interaction {
        const val RIPPLE_ALPHA = 0.16f
        const val DISABLED_ALPHA = Metrics.DISABLED_ALPHA
        const val SELECTED_STROKE_DP = 2
        const val OUTLINED_STROKE_DP = 1.5f
        const val DEFAULT_STROKE_DP = 1
        const val TEAM_YELLOW_STROKE_DP = 1.5f
        const val RING_STROKE_DP = 2f
    }
}
