package com.armadillo.game.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armadillo.game.GameController
import com.armadillo.game.R
import com.armadillo.game.game.GameConstants
import com.armadillo.game.game.WordCategories
import com.armadillo.game.ui.Screen
import com.armadillo.game.ui.components.BrandButton
import com.armadillo.game.ui.components.ChoiceCard
import com.armadillo.game.ui.components.PageScaffold
import com.armadillo.game.ui.components.SeatingCircle
import com.armadillo.game.ui.components.selectionLabel
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim
import com.armadillo.game.ui.theme.Radii

@Composable
fun HomeScreen(controller: GameController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = Dim.HomeSide,
                end = Dim.HomeSide,
                top = Dim.HomeTop,
                bottom = Dim.HomeBottom
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val heroShape = RoundedCornerShape(Radii.Hero)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(BrandPalette.Surface)
                .border(1.dp, BrandPalette.Outline, heroShape)
                .padding(start = Dim.MD, end = Dim.MD, top = Dim.SM, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.armadillo_icon_hero),
                contentDescription = stringResource(R.string.a11y_app_logo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(Dim.HomeLogo)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = BrandPalette.Navy,
                modifier = Modifier.padding(top = 4.dp, bottom = 1.dp)
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = BrandPalette.Navy
            )
        }
        Spacer(Modifier.height(18.dp))
        BrandButton(
            text = stringResource(R.string.home_start),
            height = 62.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { controller.startSetup() }
        )
        Spacer(Modifier.height(10.dp))
        if (controller.canContinue) {
            BrandButton(
                text = stringResource(R.string.home_continue_saved),
                height = 58.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { controller.continueSavedGame() }
            )
            Spacer(Modifier.height(10.dp))
        }
        BrandButton(
            text = stringResource(R.string.sound_settings_title),
            height = 54.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { controller.openSoundSettings(Screen.HOME) }
        )
        Spacer(Modifier.height(10.dp))
        BrandButton(
            text = stringResource(R.string.home_rules),
            outlined = true,
            height = 54.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { controller.showRules() }
        )
        Spacer(Modifier.height(10.dp))
        BrandButton(
            text = stringResource(R.string.home_about),
            outlined = true,
            height = 54.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { controller.showAbout() }
        )
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = BrandPalette.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PlayerCountScreen(controller: GameController) {
    PageScaffold(
        title = stringResource(R.string.count_title),
        onBack = { controller.showHome() },
        footer = {
            BrandButton(
                text = stringResource(R.string.count_continue),
                height = 58.dp,
                enabled = controller.selectedPlayerCount?.let { it in GameConstants.allowedPlayerCounts } == true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { controller.continueToPlayers() }
            )
        }
    ) {
        Text(
            text = stringResource(R.string.count_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = BrandPalette.TextSecondary,
            modifier = Modifier.padding(bottom = Dim.SM)
        )
        GameConstants.allowedPlayerCounts.forEach { count ->
            val selected = controller.selectedPlayerCount == count
            ChoiceCard(
                selected = selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 9.dp),
                contentDescription = stringResource(
                    R.string.count_option_description,
                    count,
                    selectionLabel(selected)
                ),
                onClick = { controller.selectPlayerCount(count) }
            ) {
                Text(
                    text = stringResource(R.string.count_option_label, count),
                    style = MaterialTheme.typography.headlineSmall,
                    color = BrandPalette.Navy,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                Text(
                    text = stringResource(R.string.count_option_teams, count / 2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandPalette.TextSecondary
                )
            }
        }
    }
}

@Composable
fun PlayersScreen(controller: GameController) {
    val draft = controller.draft ?: return
    PageScaffold(
        title = stringResource(R.string.players_title),
        onBack = { controller.backFromPlayers() },
        footer = {
            BrandButton(
                text = stringResource(R.string.players_continue),
                height = 58.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { controller.showCategories() }
            )
        }
    ) {
        Text(
            text = stringResource(R.string.players_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = BrandPalette.TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        SeatingCircle(
            count = draft.playerCount,
            names = draft.names,
            onSeatTap = { seat -> controller.editPlayerName(seat) },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.players_edit_hint),
            style = MaterialTheme.typography.labelSmall,
            color = BrandPalette.TextSecondary,
            modifier = Modifier.padding(top = Dim.XXXS, bottom = Dim.XS)
        )
        // The circle cannot expose one accessibility node per seat, so every seat is also
        // a real focusable button for screen-reader and switch-access users.
        for (seat in 0 until draft.playerCount) {
            val seatName = draft.names.getOrNull(seat)?.trim().orEmpty()
                .ifEmpty { stringResource(R.string.players_default_name) }
            val teamNumber = seat % (draft.playerCount / 2) + 1
            BrandButton(
                text = stringResource(R.string.players_seat_button, seat + 1, seatName, teamNumber),
                outlined = true,
                height = Dim.MinTouch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                onClick = { controller.editPlayerName(seat) }
            )
        }
    }
}

private data class CategoryOption(val id: String, val titleRes: Int)

@Composable
fun CategoriesScreen(controller: GameController) {
    val draft = controller.draft ?: return
    PageScaffold(
        title = stringResource(R.string.categories_title),
        onBack = { controller.backFromCategories() },
        footer = {
            BrandButton(
                text = stringResource(R.string.categories_continue),
                height = 58.dp,
                enabled = draft.categories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (draft.categories.isNotEmpty()) controller.showOptions() }
            )
        }
    ) {
        Text(
            text = stringResource(R.string.categories_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = BrandPalette.TextSecondary,
            modifier = Modifier.padding(bottom = Dim.SM)
        )
        val options = listOf(
            CategoryOption(WordCategories.JOBS, R.string.category_jobs),
            CategoryOption(WordCategories.ANIMALS, R.string.category_animals),
            CategoryOption(WordCategories.FOODS, R.string.category_foods),
            CategoryOption(WordCategories.PLACES, R.string.category_places),
            CategoryOption(WordCategories.TECHNOLOGY, R.string.category_technology)
        )
        options.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowOptions.forEach { option ->
                    val title = stringResource(option.titleRes)
                    val selected = option.id in draft.categories
                    ChoiceCard(
                        selected = selected,
                        modifier = Modifier
                            .weight(1f)
                            .height(132.dp),
                        contentDescription = stringResource(
                            R.string.category_card_description,
                            title,
                            selectionLabel(selected)
                        ),
                        onClick = { controller.toggleCategory(option.id) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (selected) {
                                    stringResource(R.string.category_selected_label, title)
                                } else {
                                    title
                                },
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPalette.Navy,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                            Text(
                                text = stringResource(R.string.category_word_count),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = BrandPalette.TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                }
                repeat(3 - rowOptions.size) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(132.dp)
                    )
                }
            }
        }
        if (draft.categories.isEmpty()) {
            Text(
                text = stringResource(R.string.categories_required),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = BrandPalette.Danger,
                modifier = Modifier.padding(top = Dim.XXXS, bottom = Dim.XS)
            )
        }
    }
}

@Composable
fun OptionsScreen(controller: GameController) {
    val draft = controller.draft ?: return
    PageScaffold(
        title = stringResource(R.string.options_title),
        onBack = { controller.showCategories() },
        footer = {
            BrandButton(
                text = stringResource(R.string.options_continue),
                height = 58.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = { controller.beginGame() }
            )
        }
    ) {
        Text(
            text = stringResource(R.string.options_difficulty),
            style = MaterialTheme.typography.titleLarge,
            color = BrandPalette.Navy,
            modifier = Modifier.padding(bottom = Dim.XS)
        )
        val difficulties = listOf(
            R.string.difficulty_easy to R.string.difficulty_easy_hint,
            R.string.difficulty_medium to R.string.difficulty_medium_hint,
            R.string.difficulty_hard to R.string.difficulty_hard_hint
        )
        difficulties.forEachIndexed { index, (titleRes, hintRes) ->
            val level = index + 1
            ChoiceCard(
                selected = draft.difficulty == level,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp),
                onClick = { controller.setDifficulty(level) }
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = BrandPalette.Navy,
                    modifier = Modifier.padding(bottom = Dim.XXS)
                )
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandPalette.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.options_duration),
            style = MaterialTheme.typography.titleLarge,
            color = BrandPalette.Navy,
            modifier = Modifier.padding(bottom = Dim.XS)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dim.MD),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GameConstants.allowedDurations.forEach { seconds ->
                BrandButton(
                    text = stringResource(R.string.options_duration_option, seconds),
                    outlined = draft.durationSeconds != seconds,
                    height = Dim.MinTouch,
                    modifier = Modifier.weight(1f),
                    onClick = { controller.setDuration(seconds) }
                )
            }
        }

        Text(
            text = stringResource(R.string.options_rounds),
            style = MaterialTheme.typography.titleLarge,
            color = BrandPalette.Navy,
            modifier = Modifier.padding(bottom = Dim.XS)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dim.SM),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GameConstants.allowedRounds.forEach { rounds ->
                BrandButton(
                    text = stringResource(R.string.options_rounds_option, rounds),
                    outlined = draft.rounds != rounds,
                    height = Dim.MinTouch,
                    modifier = Modifier.weight(1f),
                    onClick = { controller.setRounds(rounds) }
                )
            }
        }
        Text(
            text = stringResource(R.string.options_fixed_hint),
            style = MaterialTheme.typography.labelSmall,
            color = BrandPalette.TextSecondary,
            modifier = Modifier.padding(bottom = Dim.XXS)
        )
    }
}
