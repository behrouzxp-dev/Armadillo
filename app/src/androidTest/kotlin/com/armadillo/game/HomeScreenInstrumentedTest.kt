package com.armadillo.game

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.armadillo.game.ui.theme.BrandPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsAppNameAndStartAction() {
        val activity = composeTestRule.activity
        composeTestRule.onNodeWithText(activity.getString(R.string.app_name)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.home_start)).assertIsDisplayed()
    }

    @Test
    fun brandCanvasMatchesThePlatformThemeColour() {
        val activity = composeTestRule.activity
        assertEquals(activity.getColor(R.color.design_canvas), BrandPalette.Canvas.toArgb())
    }
}
