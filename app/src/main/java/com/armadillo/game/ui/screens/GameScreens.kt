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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armadillo.game.GameController
import com.armadillo.game.R
import com.armadillo.game.game.GameEngine
import com.armadillo.game.game.GamePhase
import com.armadillo.game.game.RoundIndicator
import com.armadillo.game.game.TeamStats
import com.armadillo.game.game.roundIndicator
import com.armadillo.game.game.teamTimeProgress
import com.armadillo.game.ui.GameSnapshot
import com.armadillo.game.ui.components.BrandButton
import com.armadillo.game.ui.components.TeamSwatch
import com.armadillo.game.ui.components.teamColor
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim
import com.armadillo.game.ui.theme.Radii

@Composable
fun GameScreen(controller: GameController) {
    val game = controller.game ?: return
    val isPlaying = game.phase == GamePhase.PLAYING
    val isPreparing = game.phase == GamePhase.PREPARATION
    val actionLabel = stringResource(
        if (isPreparing) R.string.game_start_by_touch else R.string.game_correct_hint
    )
    val pausedDescription = stringResource(R.string.a11y_game_paused)
    val pausedDescriptionForButton = stringResource(R.string.a11y_game_pause)
    val currentTeamColor = teamColor(game.currentPlayerTeam)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandPalette.Canvas)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(currentTeamColor.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.maxDimension * 0.85f
                    )
                )
            }
    ) {
        if (isPlaying || isPreparing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = actionLabel,
                        onClick = { if (isPreparing) controller.startRound() else controller.markCorrect() }
                    )
                    .semantics { contentDescription = actionLabel }
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CurrentPlayer(
                    name = game.currentPlayerName,
                    team = game.currentPlayerTeam,
                    modifier = Modifier.weight(0.38f)
                )
                RoundIndicators(
                    currentRound = game.round,
                    totalRounds = game.totalRounds,
                    phase = game.phase,
                    modifier = Modifier.weight(0.62f)
                )
            }
            TeamTimeBars(
                teamStats = game.teamStats,
                durationSeconds = game.durationSeconds,
                totalRounds = game.totalRounds,
                modifier = Modifier.padding(top = Dim.XS)
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isPreparing) {
                    Text(
                        text = stringResource(R.string.game_start_by_touch),
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandPalette.Navy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Dim.MD)
                    )
                } else {
                    WordText(word = game.word, modifier = Modifier.fillMaxWidth())
                }
            }
            if (!isPreparing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dim.XS),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Consume disabled-control taps so they cannot register as correct guesses.
                    Box(Modifier.weight(4f).pointerInput(Unit) { detectTapGestures { } }) {
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
                    Button(
                        onClick = { controller.togglePause() },
                        modifier = Modifier.weight(1f).height(68.dp)
                            .semantics { contentDescription = pausedDescriptionForButton },
                        shape = RoundedCornerShape(Radii.Button),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPalette.Navy)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pause),
                            contentDescription = null,
                            tint = BrandPalette.ButtonText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        if (game.phase == GamePhase.MANUAL_PAUSED) {
            ManualPauseOverlay(controller = controller, description = pausedDescription, game = game)
        }
    }
}

@Composable
private fun CurrentPlayer(name: String, team: Int, modifier: Modifier = Modifier) {
    val color = teamColor(team)
    val textColor = if (team in 0..2) Color.Black else Color.White
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.Control))
            .background(color)
            .padding(horizontal = Dim.XS, vertical = Dim.XXS),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(R.string.game_team_number, team + 1),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RoundIndicators(currentRound: Int, totalRounds: Int, phase: GamePhase, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (round in 1..totalRounds) {
            val state = roundIndicator(round, currentRound, phase)
            val description = stringResource(
                when (state) {
                    RoundIndicator.COMPLETED -> R.string.game_round_completed
                    RoundIndicator.ACTIVE -> R.string.game_round_active
                    RoundIndicator.UPCOMING -> R.string.game_round_upcoming
                }, round
            )
            val color = when (state) {
                RoundIndicator.COMPLETED -> BrandPalette.Danger
                RoundIndicator.ACTIVE -> BrandPalette.Success
                RoundIndicator.UPCOMING -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(12.dp)
                    .background(color, CircleShape)
                    .border(1.dp, BrandPalette.Navy, CircleShape)
                    .semantics { contentDescription = description }
            )
        }
    }
}

@Composable
private fun TeamTimeBars(
    teamStats: List<TeamStats>,
    durationSeconds: Int,
    totalRounds: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        teamStats.forEachIndexed { team, stats ->
            val progress = teamTimeProgress(stats.finalTimeNanos, durationSeconds, totalRounds)
            val seconds = stats.finalTimeNanos / GameEngine.NANOS_PER_SECOND
            val description = stringResource(R.string.game_team_progress, team + 1, seconds)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.game_team_number, team + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandPalette.Navy,
                    modifier = Modifier.widthIn(min = 48.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(BrandPalette.SurfaceAlt)
                        .border(1.dp, BrandPalette.Outline, CircleShape)
                        .semantics {
                            contentDescription = description
                            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(teamColor(team), CircleShape)
                    )
                }
                Text(
                    text = stringResource(R.string.unit_seconds_suffix, seconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandPalette.Navy,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 52.dp)
                )
            }
        }
    }
}

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
private fun ManualPauseOverlay(controller: GameController, description: String, game: GameSnapshot) {
    BoxWithConstraints(
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
                .padding(horizontal = Dim.PauseMargin)
                .heightIn(max = (maxHeight - Dim.XXL).coerceAtLeast(0.dp)),
            shape = shape,
            color = BrandPalette.SurfaceRaised,
            contentColor = BrandPalette.Navy,
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandPalette.Outline),
            shadowElevation = Dim.MD
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Dim.MD, vertical = Dim.MD),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.game_pause),
                    style = MaterialTheme.typography.headlineMedium,
                    color = BrandPalette.Navy,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Dim.XXS)
                )
                Text(
                    text = stringResource(R.string.pause_frozen),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandPalette.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Dim.XS)
                )
                Text(
                    text = stringResource(R.string.results_words_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = BrandPalette.Navy,
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dim.XXS)
                )
                WordHistoryList(game = game, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.height(Dim.XS))
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
            }
        }
    }
}

@Composable
fun ExplosionScreen(controller: GameController) {
    val game = controller.game ?: return
    val description = stringResource(R.string.a11y_explosion)
    val continueLabel = stringResource(R.string.a11y_explosion_continue)
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
            .clickable(
                role = Role.Button,
                onClickLabel = continueLabel,
                onClick = { controller.acknowledgeExplosion() }
            )
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
