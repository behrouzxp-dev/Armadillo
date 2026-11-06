package com.armadillo.game.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armadillo.game.R
import com.armadillo.game.game.WordOutcome
import com.armadillo.game.ui.GameSnapshot
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The history is independently scrollable so the pause actions stay visible. */
@Composable
fun WordHistoryList(game: GameSnapshot, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        if (game.wordHistory.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.results_no_words),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandPalette.TextSecondary,
                    modifier = Modifier.padding(bottom = Dim.XS)
                )
            }
        } else {
            itemsIndexed(game.wordHistory) { index, entry ->
                val player = game.players.getOrNull(entry.playerSeat)
                val playerName = player?.name ?: stringResource(R.string.results_unknown_player)
                val teamText = player?.let {
                    stringResource(R.string.results_team_label_history, it.teamIndex + 1)
                } ?: stringResource(R.string.results_team_unknown)
                Text(
                    text = "${index + 1}. ${entry.word}",
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandPalette.Navy,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
                Text(
                    text = stringResource(
                        R.string.results_history_line,
                        playerName,
                        teamText,
                        entry.round,
                        formatWordTimestamp(entry.shownAtEpochMillis),
                        wordOutcomeLabel(entry.outcome)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrandPalette.TextSecondary,
                    modifier = Modifier.padding(bottom = Dim.XS)
                )
            }
        }
    }
}

private val wordTimestampFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
private fun formatWordTimestamp(epochMillis: Long): String =
    if (epochMillis <= 0L) stringResource(R.string.time_unknown)
    else wordTimestampFormat.format(Date(epochMillis))

@Composable
private fun wordOutcomeLabel(outcome: WordOutcome): String = stringResource(
    when (outcome) {
        WordOutcome.PENDING -> R.string.outcome_pending
        WordOutcome.CORRECT -> R.string.outcome_correct
        WordOutcome.SWAPPED -> R.string.outcome_swapped
        WordOutcome.EXPIRED -> R.string.outcome_expired
        WordOutcome.ENDED -> R.string.outcome_ended
    }
)
