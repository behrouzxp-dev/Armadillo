package com.armadillo.game.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armadillo.game.GameController
import com.armadillo.game.R
import com.armadillo.game.game.rankTeams
import com.armadillo.game.ui.components.BrandButton
import com.armadillo.game.ui.components.PageScaffold
import com.armadillo.game.ui.components.SectionCard
import com.armadillo.game.ui.components.TeamSwatch
import com.armadillo.game.ui.components.formatFinalTime
import com.armadillo.game.ui.components.teamNames
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim

@Composable
fun ResultsScreen(controller: GameController) {
    val game = controller.game ?: return
    val ranking = remember(game.teamStats) { rankTeams(game.teamStats) }
    val winners = ranking.filter { it.rank == 1 }
    val neverStarted = game.wordHistory.isEmpty() &&
        game.teamStats.all { it.realTimeNanos == 0L && it.penaltyNanos == 0L }

    PageScaffold(
        title = stringResource(R.string.results_title),
        onBack = { controller.returnToHome() }
    ) {
        if (neverStarted) {
            Text(
                text = stringResource(R.string.results_never_started),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = BrandPalette.Danger,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        SectionCard(
            fillColor = BrandPalette.SurfaceAccent,
            strokeColor = BrandPalette.Navy,
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            Text(
                text = stringResource(if (winners.size == 1) R.string.results_winner else R.string.results_tie),
                style = MaterialTheme.typography.titleLarge,
                color = BrandPalette.Navy,
                modifier = Modifier.padding(bottom = Dim.XS)
            )
            winners.forEach { winner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp)
                ) {
                    TeamSwatch(teamIndex = winner.teamIndex, width = 10.dp, height = 32.dp, radius = 5.dp)
                    Spacer(Modifier.width(Dim.XXS))
                    Text(
                        text = stringResource(R.string.results_team_label, winner.teamIndex + 1),
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandPalette.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatFinalTime(winner.stats.finalTimeNanos),
                        style = MaterialTheme.typography.titleMedium,
                        color = BrandPalette.Navy
                    )
                }
                Text(
                    text = teamNames(game.players, winner.teamIndex),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandPalette.TextSecondary,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
        }

        Text(
            text = stringResource(R.string.results_ranking),
            style = MaterialTheme.typography.titleLarge,
            color = BrandPalette.Navy,
            modifier = Modifier.padding(bottom = Dim.XS)
        )
        ranking.forEach { row ->
            SectionCard(modifier = Modifier.padding(bottom = Dim.XS)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.results_rank, row.rank),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = BrandPalette.TextSecondary,
                        modifier = Modifier.width(62.dp)
                    )
                    TeamSwatch(teamIndex = row.teamIndex, width = Dim.XS, height = 30.dp, radius = Dim.XXS)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.results_team_label_row, row.teamIndex + 1),
                        style = MaterialTheme.typography.titleMedium,
                        color = BrandPalette.Navy,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatFinalTime(row.stats.finalTimeNanos),
                        style = MaterialTheme.typography.titleSmall,
                        color = BrandPalette.Navy
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.results_team_players, teamNames(game.players, row.teamIndex)),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = BrandPalette.Navy,
                    modifier = Modifier.padding(bottom = Dim.XXS)
                )
                Text(
                    text = stringResource(R.string.results_team_stats, row.stats.correctWords, row.stats.explosions),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandPalette.TextSecondary
                )
            }
        }

        Text(
            text = stringResource(R.string.results_time_note),
            style = MaterialTheme.typography.labelSmall,
            color = BrandPalette.TextSecondary,
            modifier = Modifier.padding(top = 3.dp, bottom = Dim.SM)
        )
        BrandButton(
            text = stringResource(R.string.results_replay),
            height = 58.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            onClick = { controller.replayWithSamePlayers() }
        )
        BrandButton(
            text = stringResource(R.string.results_home),
            outlined = true,
            height = 54.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { controller.returnToHome() }
        )
    }
}
