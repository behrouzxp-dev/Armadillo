package com.armadillo.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armadillo.game.R
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim
import com.armadillo.game.ui.theme.TeamStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Circular seat picker, now drawn with Compose Canvas. Seat 1 starts at six o'clock
 * and the seats run counterclockwise. The circled names are decorative: the players
 * screen also lists every seat as a real button for screen-reader and switch access.
 */
@Composable
fun SeatingCircle(
    count: Int,
    names: List<String>,
    onSeatTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val circleDescription = stringResource(R.string.a11y_seating_circle)
    val tableLabel = stringResource(R.string.seating_table)
    val defaultName = stringResource(R.string.players_default_name)
    // Resolved as resources so a locale or configuration change re-reads them.
    val details = buildList {
        for (seat in 0 until count) {
            add(stringResource(R.string.seating_team_seat, seat % (count / 2) + 1, seat + 1))
        }
    }
    val hitRadiusPx = with(density) { 40.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(Dim.SeatingCircle)
            .semantics { contentDescription = circleDescription }
            .pointerInput(count) {
                detectTapGestures { tap ->
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val centerX = width / 2f
                    val centerY = height / 2f
                    val shortSide = min(width, height)
                    val tableRadius = shortSide * 0.315f
                    val hitRadius = min(hitRadiusPx, shortSide * if (count >= 8) 0.095f else 0.115f)
                    var bestSeat = -1
                    var bestDistance = Float.MAX_VALUE
                    for (seat in 0 until count) {
                        val angle = PI.toFloat() - 2f * PI.toFloat() * seat / count
                        val x = centerX + sin(angle) * tableRadius
                        val y = centerY - cos(angle) * tableRadius
                        val dx = tap.x - x
                        val dy = tap.y - y
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance <= hitRadius * 1.35f && distance < bestDistance) {
                            bestDistance = distance
                            bestSeat = seat
                        }
                    }
                    if (bestSeat >= 0) onSeatTap(bestSeat)
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        val shortSide = min(width, height)
        val tableRadius = shortSide * 0.315f
        val hitRadius = min(hitRadiusPx, shortSide * if (count >= 8) 0.095f else 0.115f)

        drawCircle(
            color = BrandPalette.Outline,
            radius = tableRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )

        val tableLayout = measurer.measure(
            text = AnnotatedString(tableLabel),
            style = typography.bodySmall.copy(color = BrandPalette.TextSecondary, textAlign = TextAlign.Center),
            layoutDirection = layoutDirection
        )
        drawText(
            textLayoutResult = tableLayout,
            topLeft = Offset(
                centerX - tableLayout.size.width / 2f,
                centerY - tableLayout.size.height / 2f
            )
        )

        for (seat in 0 until count) {
            val angle = PI.toFloat() - 2f * PI.toFloat() * seat / count
            val x = centerX + sin(angle) * tableRadius
            val y = centerY - cos(angle) * tableRadius
            val team = seat % (count / 2)
            val markerY = y - 21.dp.toPx()

            drawCircle(color = teamColor(team), radius = 4.dp.toPx(), center = Offset(x, markerY))
            if (team == TeamStyle.YELLOW_INDEX) {
                drawCircle(
                    color = BrandPalette.Navy,
                    radius = 4.8.dp.toPx(),
                    center = Offset(x, markerY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            val shownName = names.getOrNull(seat)?.trim().orEmpty().ifEmpty { defaultName }
            val nameSize = when {
                shownName.length > 11 -> 10.sp
                shownName.length > 7 -> 11.sp
                else -> 12.sp
            }
            val nameStyle: TextStyle = typography.bodySmall.copy(
                color = BrandPalette.Navy,
                fontWeight = FontWeight.Normal,
                fontSize = nameSize,
                lineHeight = nameSize * 1.2f,
                textAlign = TextAlign.Center
            )
            val nameLayout = measureName(
                measurer = measurer,
                text = shownName,
                style = nameStyle,
                maxWidth = (hitRadius * 1.78f).toInt().coerceAtLeast(1),
                layoutDirection = layoutDirection
            )
            drawText(
                textLayoutResult = nameLayout,
                topLeft = Offset(
                    x - nameLayout.size.width / 2f,
                    y - 1.dp.toPx() - nameLayout.size.height
                )
            )

            val detailLayout = measurer.measure(
                text = AnnotatedString(details.getOrElse(seat) { "" }),
                style = typography.bodySmall.copy(
                    color = BrandPalette.TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center
                ),
                layoutDirection = layoutDirection
            )
            drawText(
                textLayoutResult = detailLayout,
                topLeft = Offset(
                    x - detailLayout.size.width / 2f,
                    y + 6.dp.toPx()
                )
            )
        }
    }
}

private fun measureName(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidth: Int,
    layoutDirection: LayoutDirection
) = measurer.measure(
    text = AnnotatedString(text),
    style = style,
    overflow = TextOverflow.Ellipsis,
    maxLines = 1,
    constraints = Constraints(maxWidth = maxWidth),
    layoutDirection = layoutDirection
)
