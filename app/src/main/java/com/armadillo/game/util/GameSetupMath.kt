package com.armadillo.game.util

import com.armadillo.game.game.GameConstants
import kotlin.math.abs

/** Pure setup arithmetic, kept free of Android types so it runs in ordinary JVM tests. */
object GameSetupMath {
    fun nearestAllowedDuration(value: Int): Int =
        GameConstants.allowedDurations.minByOrNull { abs(it - value) } ?: 120

    fun nearestAllowedRounds(value: Int): Int =
        GameConstants.allowedRounds.minByOrNull { abs(it - value) } ?: 5
}
