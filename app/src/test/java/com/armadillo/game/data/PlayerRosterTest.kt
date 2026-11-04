package com.armadillo.game.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRosterTest {
    @Test fun editingNamesRetainsSeatOrderAndKeepsNamesForAdditionalSeats() {
        val existing = listOf("آوا", "آرمان", "نیما", "رها", "سام")
        val edited = mergePlayerRosterNames(existing, listOf("ندا", "", "نیما", "رها"), 4)
        assertEquals(listOf("ندا", "", "نیما", "رها", "سام"), edited)

        val expanded = mergePlayerRosterNames(edited, listOf("ندا", "", "نیما", "رها", "سام", ""), 6)
        assertEquals(listOf("ندا", "", "نیما", "رها", "سام", ""), expanded)
    }
}
