package com.armadillo.game.ui.screens

import android.content.pm.PackageManager
import android.os.Build

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
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
import com.armadillo.game.ui.components.LocalSoundEffect
import com.armadillo.game.ui.components.SeatingCircle
import com.armadillo.game.ui.components.selectionLabel
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim

@Composable
fun HomeScreen(controller: GameController) {
    val context = LocalContext.current
    val version = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName?.takeIf { it.isNotBlank() }
    }.getOrNull()
    val sound = LocalSoundEffect.current

    Column(Modifier.fillMaxSize()) {
        // The controls stack downward from the physical top-right corner. The
        // scoped LTR override keeps that placement stable under app-wide RTL.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dim.HomeSide, vertical = Dim.XS),
                horizontalArrangement = Arrangement.End
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dim.SM)) {
                    HomeCornerButton(Icons.Filled.Settings, R.string.sound_settings_title) {
                        sound()
                        controller.openSoundSettings(Screen.HOME)
                    }
                    HomeCornerButton(Icons.Filled.Info, R.string.home_rules) {
                        sound()
                        controller.showRules()
                    }
                }
            }
        }
        // The content can grow and scroll on small screens or with large system fonts.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val availableHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = availableHeight)
                    .padding(horizontal = Dim.HomeSide, vertical = Dim.SM),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        color = BrandPalette.Navy
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = BrandPalette.Navy,
                        textAlign = TextAlign.Center
                    )
                    if (version != null) {
                        Text(
                            text = stringResource(R.string.about_version, version),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandPalette.Navy,
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandPalette.Navy,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HomePlayButton(onClick = { controller.startSetup() })
                    Text(
                        text = stringResource(R.string.home_start),
                        style = MaterialTheme.typography.titleMedium,
                        color = BrandPalette.Navy,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (controller.canContinue) {
                        Spacer(Modifier.height(12.dp))
                        BrandButton(
                            text = stringResource(R.string.home_continue_saved),
                            outlined = true,
                            height = 50.dp,
                            onClick = { controller.continueSavedGame() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCornerButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp).background(BrandPalette.Navy, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(label),
            tint = BrandPalette.ButtonText,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun HomePlayButton(onClick: () -> Unit) {
    val sound = LocalSoundEffect.current
    val label = stringResource(R.string.home_start)
    Button(
        onClick = { sound(); onClick() },
        modifier = Modifier.size(160.dp).semantics { contentDescription = label },
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
    ) {
        Image(
            painter = painterResource(R.drawable.home_play_art),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
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
                    color = if (selected) BrandPalette.ButtonText else BrandPalette.Navy,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
                Text(
                    text = stringResource(R.string.count_option_teams, count / 2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) BrandPalette.ButtonText else BrandPalette.TextSecondary
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
                                color = if (selected) BrandPalette.ButtonText else BrandPalette.Navy,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                            Text(
                                text = stringResource(R.string.category_word_count),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = if (selected) BrandPalette.ButtonText else BrandPalette.TextSecondary,
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
            val selected = draft.difficulty == level
            ChoiceCard(
                selected = selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp),
                onClick = { controller.setDifficulty(level) }
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) BrandPalette.ButtonText else BrandPalette.Navy,
                    modifier = Modifier.padding(bottom = Dim.XXS)
                )
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) BrandPalette.ButtonText else BrandPalette.TextSecondary
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
                val isSelected = draft.durationSeconds == seconds
                BrandButton(
                    text = stringResource(R.string.options_duration_option, seconds),
                    outlined = !isSelected,
                    outlinedFillColor = Color.Transparent,
                    height = Dim.MinTouch,
                    modifier = Modifier.weight(1f).semantics { selected = isSelected },
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
                val isSelected = draft.rounds == rounds
                BrandButton(
                    text = stringResource(R.string.options_rounds_option, rounds),
                    outlined = !isSelected,
                    outlinedFillColor = Color.Transparent,
                    height = Dim.MinTouch,
                    modifier = Modifier.weight(1f).semantics { selected = isSelected },
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
