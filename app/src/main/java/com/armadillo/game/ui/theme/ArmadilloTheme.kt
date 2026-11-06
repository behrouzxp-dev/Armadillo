package com.armadillo.game.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armadillo.game.R

/**
 * The brand palette is sampled from the owner's launcher icon. The app keeps one
 * static light theme: there is no dark scheme and no dynamic colour, on purpose.
 */
object BrandPalette {
    val Canvas = Color(0xFFF9CB03)
    val Navy = Color(0xFF07223C)
    val Surface = Color(0xFFFFFBEB)
    val SurfaceRaised = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFFFF2C4)
    val SurfaceAccent = Color(0xFFFFE79B)
    val Outline = Color(0xFF6F5500)
    val TextSecondary = Color(0xFF38434F)
    val ButtonOutlinedFill = Color(0xFFFFFBEF)
    val ButtonText = Color(0xFFFFFFFF)
    val ButtonDisabledText = Color(0xFFA9B4C2)
    val Success = Color(0xFF1B6440)
    val Danger = Color(0xFFA6190F)
    val DangerSurface = Color(0xFFFFF1EF)
    val Scrim = Color(0xB307223C)
    /** Electric neon blue used only for the home start-control glow. */
    val NeonBlue = Color(0xFF00C8FF)
}

/** Mikhak ships static Regular and Bold masters, so bold text uses a real font file. */
val MikhakFontFamily = FontFamily(
    Font(R.font.mikhak_regular, FontWeight.Normal),
    Font(R.font.mikhak_bold, FontWeight.Bold)
)

private val BrandColorScheme = lightColorScheme(
    primary = BrandPalette.Navy,
    onPrimary = BrandPalette.ButtonText,
    primaryContainer = BrandPalette.SurfaceAccent,
    onPrimaryContainer = BrandPalette.Navy,
    secondary = BrandPalette.SurfaceAccent,
    onSecondary = BrandPalette.Navy,
    tertiary = BrandPalette.Outline,
    onTertiary = BrandPalette.ButtonText,
    background = BrandPalette.Canvas,
    onBackground = BrandPalette.Navy,
    surface = BrandPalette.Surface,
    onSurface = BrandPalette.Navy,
    surfaceVariant = BrandPalette.SurfaceAlt,
    onSurfaceVariant = BrandPalette.TextSecondary,
    outline = BrandPalette.Outline,
    outlineVariant = BrandPalette.SurfaceAccent,
    error = BrandPalette.Danger,
    onError = BrandPalette.ButtonText,
    errorContainer = BrandPalette.DangerSurface,
    onErrorContainer = BrandPalette.Danger,
    scrim = BrandPalette.Scrim
)

private fun brandStyle(sizeSp: Int, weight: FontWeight, lineHeightSp: Int): TextStyle = TextStyle(
    fontFamily = MikhakFontFamily,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp
)

private val BrandTypography = Typography(
    displayLarge = brandStyle(58, FontWeight.Bold, 66),
    displayMedium = brandStyle(44, FontWeight.Bold, 54),
    displaySmall = brandStyle(36, FontWeight.Bold, 46),
    headlineLarge = brandStyle(28, FontWeight.Bold, 38),
    headlineMedium = brandStyle(24, FontWeight.Bold, 34),
    headlineSmall = brandStyle(20, FontWeight.Bold, 30),
    titleLarge = brandStyle(18, FontWeight.Bold, 28),
    titleMedium = brandStyle(16, FontWeight.Bold, 26),
    titleSmall = brandStyle(15, FontWeight.Bold, 24),
    bodyLarge = brandStyle(16, FontWeight.Normal, 26),
    bodyMedium = brandStyle(14, FontWeight.Normal, 24),
    bodySmall = brandStyle(13, FontWeight.Normal, 22),
    labelLarge = brandStyle(15, FontWeight.Bold, 22),
    labelMedium = brandStyle(13, FontWeight.Normal, 20),
    labelSmall = brandStyle(12, FontWeight.Normal, 18)
)

private val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ArmadilloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrandColorScheme,
        typography = BrandTypography,
        shapes = BrandShapes,
        content = content
    )
}
