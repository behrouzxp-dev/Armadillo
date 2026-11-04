package com.armadillo.game.data

import com.armadillo.game.game.GameConstants

/** Player names are persisted separately from the optional unfinished-game checkpoint. */
data class PlayerRoster(val playerCount: Int, val names: List<String>)

/** Pure seat-index merge logic; keeping it separate makes roster migration/unit tests deterministic. */
fun mergePlayerRosterNames(existing: List<String>, updated: List<String>, count: Int): List<String> {
    require(count in GameConstants.allowedPlayerCounts)
    require(updated.size >= count)
    val merged = existing.toMutableList()
    while (merged.size < count) merged += ""
    for (index in 0 until count) merged[index] = updated[index].trim()
    return merged
}
