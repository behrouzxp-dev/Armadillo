package com.armadillo.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import com.armadillo.game.game.GameConstants
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Circular seat picker: seat 1 begins at six o'clock and follows the counterclockwise order. */
class SeatingCircleView(
    context: Context,
    private val count: Int,
    private val names: List<String>,
    private val onSeatTap: (Int) -> Unit
) : View(context) {
    private val density = resources.displayMetrics.density
    private val designColors = DesignSystem.colors(context)
    private val lalezar = DesignSystem.lalezar(context)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = designColors.outline
        style = Paint.Style.STROKE
        strokeWidth = DesignSystem.Interaction.RING_STROKE_DP * density
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nodeOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = designColors.textPrimary
        style = Paint.Style.STROKE
        strokeWidth = DesignSystem.Interaction.TEAM_YELLOW_STROKE_DP * density
    }
    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = designColors.textPrimary
        textAlign = Paint.Align.CENTER
        typeface = lalezar
    }
    private val detailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = lalezar
    }
    private val centerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = designColors.textSecondary
        textAlign = Paint.Align.CENTER
        textSize = DesignSystem.TypeScale.SEAT_CENTER * density
        typeface = lalezar
    }
    private var centerX = 0f
    private var centerY = 0f
    private var tableRadius = 0f
    private var hitRadius = 0f

    init {
        contentDescription = "چیدمان دایره‌ای صندلی‌ها؛ لمس هر نام برای واردکردن یا ویرایش آن"
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        centerX = width / 2f
        centerY = height / 2f
        val minDimension = min(width, height).toFloat()
        tableRadius = minDimension * 0.315f
        hitRadius = min(DesignSystem.Metrics.SEAT_HIT_RADIUS_DP * density, minDimension * if (count >= 8) 0.095f else 0.115f)

        canvas.drawCircle(centerX, centerY, tableRadius, ringPaint)
        canvas.drawText("میز بازی", centerX, centerY + centerTextPaint.textSize / 3f, centerTextPaint)

        for (seat in 0 until count) {
            val point = pointForSeat(seat)
            val team = seat % (count / 2)
            val color = GameConstants.teamColors[team]

            // Use a small team-color marker instead of a frame around the player's name.
            nodePaint.color = color
            val markerX = point.first
            val markerY = point.second - DesignSystem.Metrics.SEAT_MARKER_OFFSET_DP * density
            canvas.drawCircle(markerX, markerY, DesignSystem.Metrics.SEAT_MARKER_RADIUS_DP * density, nodePaint)
            if (team == DesignSystem.Metrics.TEAM_YELLOW_INDEX) {
                // Gold remains the team's identity; a navy ring separates it from the yellow canvas.
                canvas.drawCircle(markerX, markerY, DesignSystem.Metrics.SEAT_MARKER_OUTLINE_RADIUS_DP * density, nodeOutlinePaint)
            }

            val shownName = names.getOrNull(seat)?.trim().orEmpty().ifEmpty { "نام بازیکن" }
            val maxTextWidth = hitRadius * 1.78f
            val nameSize = when {
                shownName.length > 11 -> DesignSystem.TypeScale.SEAT_NAME_SMALL
                shownName.length > 7 -> DesignSystem.TypeScale.SEAT_NAME_MEDIUM
                else -> DesignSystem.TypeScale.SEAT_NAME_LARGE
            }
            namePaint.textSize = nameSize * density
            val ellipsized = TextUtils.ellipsize(shownName, namePaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
            canvas.drawText(ellipsized, point.first, point.second - 1f * density, namePaint)

            detailPaint.textSize = DesignSystem.TypeScale.SEAT_DETAIL * density
            detailPaint.color = designColors.textSecondary
            canvas.drawText("تیم ${team + 1} · ${seat + 1}", point.first, point.second + DesignSystem.Metrics.SEAT_DETAIL_BASELINE_DP * density, detailPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            var bestSeat = -1
            var bestDistance = Float.MAX_VALUE
            for (seat in 0 until count) {
                val point = pointForSeat(seat)
                val dx = event.x - point.first
                val dy = event.y - point.second
                val distance = sqrt(dx * dx + dy * dy)
                if (distance <= hitRadius * 1.35f && distance < bestDistance) {
                    bestDistance = distance
                    bestSeat = seat
                }
            }
            if (bestSeat >= 0) {
                performClick()
                onSeatTap(bestSeat)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun pointForSeat(seat: Int): Pair<Float, Float> {
        // In screen coordinates, decreasing angle from the bottom moves counterclockwise to the right.
        val angle = PI.toFloat() - 2f * PI.toFloat() * seat / count
        val x = centerX + sin(angle) * tableRadius
        val y = centerY - cos(angle) * tableRadius
        return x to y
    }
}
