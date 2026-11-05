package com.armadillo.game.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armadillo.game.GameController
import com.armadillo.game.R
import com.armadillo.game.game.GamePhase
import com.armadillo.game.ui.Screen
import com.armadillo.game.ui.components.BrandButton
import com.armadillo.game.ui.components.TeamSwatch
import com.armadillo.game.ui.components.softTint
import com.armadillo.game.ui.components.teamEdgeColor
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim
import com.armadillo.game.ui.theme.Radii
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun GameScreen(controller: GameController) {
    val game = controller.game ?: return
    val nextPlayerDescription = stringResource(R.string.a11y_next_player)
    val pausedDescription = stringResource(R.string.a11y_game_paused)
    val pauseLabel = if (game.phase == GamePhase.MANUAL_PAUSED) {
        stringResource(R.string.game_resume)
    } else {
        stringResource(R.string.game_pause)
    }
    val correctHint = stringResource(R.string.game_correct_hint)
    Box(modifier = Modifier.fillMaxSize().background(BrandPalette.Canvas)) {
        if (game.phase == GamePhase.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = correctHint,
                        onClick = { controller.markCorrect() }
                    )
                    .semantics { contentDescription = correctHint }
            )
        }
        if (game.phase != GamePhase.PREPARATION) {
            WordText(
                word = game.word,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = Dim.GameHorizontal,
                    end = Dim.GameHorizontal,
                    top = Dim.GameTop,
                    bottom = Dim.GameBottom
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamChips(
                    teamCount = game.teamCount,
                    teamSeconds = game.teamSeconds,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(Dim.XXXS))
                BrandButton(
                    text = pauseLabel,
                    height = Dim.MinTouch,
                    playEffect = false,
                    modifier = Modifier.width(88.dp),
                    onClick = { controller.togglePause() }
                )
            }
            Text(
                text = stringResource(R.string.game_round_of, game.round, game.totalRounds),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = BrandPalette.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(23.dp)
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerCard(
                        name = game.currentPlayerName,
                        team = game.currentPlayerTeam,
                        secondary = false,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                    )
                    Text(
                        text = "→",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPalette.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(34.dp)
                            .semantics { contentDescription = nextPlayerDescription }
                    )
                    PlayerCard(
                        name = game.nextPlayerName,
                        team = game.nextPlayerTeam,
                        secondary = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (game.phase == GamePhase.PREPARATION) {
                    PreparationCard(
                        showEditNames = game.showEditNames,
                        onStart = { controller.startRound() },
                        onEditNames = { controller.editOwnNames() }
                    )
                }
            }
            if (game.phase != GamePhase.PREPARATION) {
                Text(
                    text = correctHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandPalette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Dim.XS)
                )
                // Consume taps on the disabled control too, so they cannot count as guesses.
                Box(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }) {
                    BrandButton(
                        text = stringResource(if (game.canSwap) R.string.game_swap else R.string.game_swap_locked),
                        height = 68.dp,
                        enabled = game.canSwap,
                        playEffect = false,
                        disabledContainerColor = BrandPalette.SurfaceAlt,
                        disabledContentColor = BrandPalette.TextSecondary,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { controller.swapWord() }
                    )
                }
            }
        }
        if (game.phase == GamePhase.MANUAL_PAUSED) {
            ManualPauseOverlay(controller = controller, description = pausedDescription)
        }
    }
}

@Composable
private fun TeamChips(
    teamCount: Int,
    teamSeconds: List<Long>,
    modifier: Modifier = Modifier
) {
    val chipShape = RoundedCornerShape(Radii.Tag)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (team in 0 until teamCount) {
                val fontSize = when {
                    teamCount >= 5 -> 9.sp
                    teamCount == 4 -> 10.sp
                    else -> 11.sp
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(chipShape)
                        .background(BrandPalette.SurfaceAlt)
                        .border(1.dp, BrandPalette.Outline, chipShape)
                        .padding(horizontal = Dim.XXXS),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    TeamSwatch(
                        teamIndex = team,
                        width = Dim.XXS,
                        height = Dim.XL,
                        radius = 2.dp
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = stringResource(
                            R.string.game_team_chip,
                            team + 1,
                            teamSeconds.getOrElse(team) { 0L }
                        ),
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.3f).sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandPalette.Navy,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(
    name: String,
    team: Int,
    secondary: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Radii.Control)
    Box(
        modifier = modifier
            .clip(shape)
            .background(softTint(teamColorOf(team), if (secondary) 0.93f else 0.86f))
            .border(
                width = if (secondary) 1.dp else 1.7f.dp,
                color = teamEdgeColor(team),
                shape = shape
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            fontSize = if (secondary) 14.sp else 16.sp,
            lineHeight = if (secondary) 20.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (secondary) BrandPalette.TextSecondary else BrandPalette.Navy,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun teamColorOf(team: Int): androidx.compose.ui.graphics.Color =
    com.armadillo.game.ui.components.teamColor(team)

@Composable
private fun WordText(word: String?, modifier: Modifier = Modifier) {
    val sizeSp = when {
        word == null -> 44
        word.length <= 8 -> 44
        word.length <= 13 -> 38
        word.length <= 18 -> 32
        word.length <= 25 -> 27
        else -> 23
    }
    Text(
        text = word.orEmpty(),
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 1.3f).sp,
        fontWeight = FontWeight.Bold,
        color = BrandPalette.Navy,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 18.dp)
    )
}

@Composable
private fun PreparationCard(
    showEditNames: Boolean,
    onStart: () -> Unit,
    onEditNames: () -> Unit
) {
    val shape = RoundedCornerShape(Radii.GameCard)
    val transition = rememberInfiniteTransition(label = "start-pulse")
    val startAlpha by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 650), repeatMode = RepeatMode.Reverse),
        label = "start-alpha"
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = shape,
        color = BrandPalette.Surface,
        contentColor = BrandPalette.Navy,
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandPalette.Outline),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = Dim.SM),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.game_word_hidden),
                style = MaterialTheme.typography.bodyLarge,
                color = BrandPalette.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dim.MD))
            BrandButton(
                text = stringResource(R.string.game_start_round),
                height = 62.dp,
                playEffect = false,
                modifier = Modifier
                    .width(220.dp)
                    .alpha(startAlpha),
                onClick = onStart
            )
            if (showEditNames) {
                Spacer(Modifier.height(Dim.SM))
                BrandButton(
                    text = stringResource(R.string.game_edit_names),
                    outlined = true,
                    height = Dim.MinTouch,
                    playEffect = false,
                    modifier = Modifier.width(220.dp),
                    onClick = onEditNames
                )
            }
        }
    }
}

@Composable
private fun ManualPauseOverlay(controller: GameController, description: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandPalette.Scrim)
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(Radii.Dialog)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.PauseMargin),
            shape = shape,
            color = BrandPalette.SurfaceRaised,
            contentColor = BrandPalette.Navy,
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandPalette.Outline),
            shadowElevation = Dim.MD
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Dim.XL, vertical = Dim.MD),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.game_pause),
                    style = MaterialTheme.typography.headlineMedium,
                    color = BrandPalette.Navy,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Dim.XS)
                )
                Text(
                    text = stringResource(R.string.pause_frozen),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandPalette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Dim.MD)
                )
                BrandButton(
                    text = stringResource(R.string.game_resume),
                    height = 56.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { controller.togglePause() }
                )
                Spacer(Modifier.height(Dim.XS))
                BrandButton(
                    text = stringResource(R.string.game_end),
                    outlined = true,
                    height = 54.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { controller.requestEndGame() }
                )
                Spacer(Modifier.height(Dim.XS))
                BrandButton(
                    text = stringResource(R.string.sound_settings_title),
                    height = Dim.MinTouch,
                    playEffect = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { controller.openSoundSettings(Screen.GAME) }
                )
            }
        }
    }
}

@Composable
fun ExplosionScreen(controller: GameController) {
    val game = controller.game ?: return
    val description = stringResource(R.string.a11y_explosion)
    val transition = rememberInfiniteTransition(label = "blast")
    val blastScale by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2000), repeatMode = RepeatMode.Reverse),
        label = "blast-scale"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandPalette.Canvas)
            .pointerInput(Unit) { detectTapGestures { controller.acknowledgeExplosion() } }
            .semantics { contentDescription = description }
            .padding(Dim.ExplosionPage),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.explosion_boom),
                fontSize = 58.sp,
                lineHeight = 66.sp,
                fontWeight = FontWeight.Bold,
                color = BrandPalette.Danger,
                modifier = Modifier.scale(blastScale)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
        ) {
            TeamSwatch(
                teamIndex = game.penaltyTeamIndex,
                width = 14.dp,
                height = 22.dp,
                radius = 6.dp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.explosion_penalty, game.penaltyTeamIndex + 1),
                fontSize = 19.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = BrandPalette.Navy
            )
        }
        Text(
            text = stringResource(R.string.explosion_round_finished, game.round),
            style = MaterialTheme.typography.bodyMedium,
            color = BrandPalette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 18.dp)
        )
        Text(
            text = stringResource(R.string.explosion_tap_hint),
            style = MaterialTheme.typography.titleSmall,
            color = BrandPalette.Navy,
            textAlign = TextAlign.Center
        )
        BrandButton(
            text = stringResource(R.string.game_pause),
            height = Dim.MinTouch,
            playEffect = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dim.XL),
            onClick = { controller.togglePause() }
        )
    }
}

@Composable
fun SystemPausedScreen(controller: GameController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandPalette.Canvas),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(Radii.Card)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dim.LG),
            shape = shape,
            color = BrandPalette.Surface,
            contentColor = BrandPalette.Navy,
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandPalette.Outline),
            shadowElevation = Dim.XS
        ) {
            Column(
                modifier = Modifier.padding(Dim.PausePanel),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.a11y_game_paused),
                    style = MaterialTheme.typography.headlineLarge,
                    color = BrandPalette.Navy,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Dim.SM)
                )
                Text(
                    text = stringResource(R.string.system_pause_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = BrandPalette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Dim.XL)
                )
                BrandButton(
                    text = stringResource(R.string.game_resume),
                    height = 62.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { controller.togglePause() }
                )
            }
        }
    }
}
