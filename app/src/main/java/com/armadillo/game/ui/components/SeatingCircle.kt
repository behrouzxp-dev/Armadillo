package com.armadillo.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armadillo.game.R
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Seat 1 starts at six o'clock and seats run counterclockwise. Armchairs face
 * the table and use their team's colour; seat numbers stay upright as they turn.
 * The list below the circle remains available to screen-reader and switch users.
 */
@Composable
fun SeatingCircle(
    count: Int,
    onSeatTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val circleDescription = stringResource(R.string.a11y_seating_circle)
    val table = ImageBitmap.imageResource(R.drawable.seating_table)
    val armchair = ImageBitmap.imageResource(R.drawable.seating_armchair)
    val seatNumbers = (0 until count).map { seat ->
        stringResource(R.string.seating_seat_number, seat + 1)
    }
    val maxHitRadiusPx = with(density) { 40.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(Dim.SeatingCircle)
            .semantics { contentDescription = circleDescription }
            .pointerInput(count, maxHitRadiusPx) {
                detectTapGestures { tap ->
                    val shortSide = min(size.width, size.height).toFloat()
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val seatRadius = shortSide * 0.36f
                    val hitRadius = min(maxHitRadiusPx, shortSide * 0.10f)
                    var bestSeat = -1
                    var bestDistance = Float.MAX_VALUE
                    for (seat in 0 until count) {
                        val angle = PI.toFloat() - 2f * PI.toFloat() * seat / count
                        val x = centerX + sin(angle) * seatRadius
                        val y = centerY - cos(angle) * seatRadius
                        val dx = tap.x - x
                        val dy = tap.y - y
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance <= hitRadius && distance < bestDistance) {
                            bestDistance = distance
                            bestSeat = seat
                        }
                    }
                    if (bestSeat >= 0) onSeatTap(bestSeat)
                }
            }
    ) {
        val shortSide = min(size.width, size.height)
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val seatRadius = shortSide * 0.36f
        val tableSize = (shortSide * 0.48f).roundToInt()
        val chairSize = (shortSide * 0.18f).roundToInt()

        drawImage(
            image = table,
            dstOffset = IntOffset((centerX - tableSize / 2f).roundToInt(), (centerY - tableSize / 2f).roundToInt()),
            dstSize = IntSize(tableSize, tableSize)
        )

        for (seat in 0 until count) {
            val angle = PI.toFloat() - 2f * PI.toFloat() * seat / count
            val x = centerX + sin(angle) * seatRadius
            val y = centerY - cos(angle) * seatRadius
            val team = seat % (count / 2)
            rotate(degrees = -360f * seat / count, pivot = Offset(x, y)) {
                drawImage(
                    image = armchair,
                    dstOffset = IntOffset((x - chairSize / 2f).roundToInt(), (y - chairSize / 2f).roundToInt()),
                    dstSize = IntSize(chairSize, chairSize),
                    colorFilter = ColorFilter.tint(teamColor(team), BlendMode.Modulate)
                )
            }
            // Numbering, not colour alone, identifies each seat and remains legible.
            drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(x, y))
            val label = measurer.measure(
                text = AnnotatedString(seatNumbers[seat]),
                style = typography.labelSmall.copy(
                    color = BrandPalette.Navy,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                layoutDirection = layoutDirection
            )
            drawText(label, topLeft = Offset(x - label.size.width / 2f, y - label.size.height / 2f))
        }
    }
}
