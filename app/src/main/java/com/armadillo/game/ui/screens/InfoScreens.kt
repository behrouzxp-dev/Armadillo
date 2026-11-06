package com.armadillo.game.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.armadillo.game.GameController
import com.armadillo.game.R
import com.armadillo.game.ui.components.PageScaffold
import com.armadillo.game.ui.components.SoundControlCard
import com.armadillo.game.ui.components.ToggleRow
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim

@Composable
fun RulesScreen(controller: GameController) {
    val sections = listOf(
        R.string.rules_goal_title to R.string.rules_goal_body,
        R.string.rules_players_title to R.string.rules_players_body,
        R.string.rules_categories_title to R.string.rules_categories_body,
        R.string.rules_correct_title to R.string.rules_correct_body,
        R.string.rules_swap_title to R.string.rules_swap_body,
        R.string.rules_explosion_title to R.string.rules_explosion_body,
        R.string.rules_pause_title to R.string.rules_pause_body,
        R.string.rules_ranking_title to R.string.rules_ranking_body
    )
    PageScaffold(
        title = stringResource(R.string.home_rules),
        onBack = { controller.showHome() }
    ) {
        sections.forEach { (titleRes, bodyRes) ->
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = BrandPalette.Navy,
                modifier = Modifier.padding(top = Dim.XS, bottom = Dim.XXS)
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = BrandPalette.TextSecondary,
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }
    }
}

@Composable
fun SoundSettingsScreen(controller: GameController) {
    val sound = controller.sound
    PageScaffold(
        title = stringResource(R.string.sound_settings_title),
        onBack = { controller.returnFromSoundSettings() }
    ) {
        Text(
            text = stringResource(R.string.sound_hint),
            style = MaterialTheme.typography.bodySmall,
            color = BrandPalette.TextSecondary,
            modifier = Modifier.padding(bottom = Dim.SM)
        )
        SoundControlCard(
            title = stringResource(R.string.sound_beep),
            enabled = sound.beepEnabled,
            volume = sound.beepVolume,
            onEnabledChange = { value -> controller.updateSound { it.copy(beepEnabled = value) } },
            onVolumeChange = { value -> controller.updateSound { it.copy(beepVolume = value) } },
            onPreview = { controller.previewBeep() },
            modifier = Modifier.padding(bottom = 9.dp)
        )
        SoundControlCard(
            title = stringResource(R.string.sound_explosion),
            enabled = sound.explosionEnabled,
            volume = sound.explosionVolume,
            onEnabledChange = { value -> controller.updateSound { it.copy(explosionEnabled = value) } },
            onVolumeChange = { value -> controller.updateSound { it.copy(explosionVolume = value) } },
            onPreview = { controller.previewExplosion() },
            modifier = Modifier.padding(bottom = 9.dp)
        )
        SoundControlCard(
            title = stringResource(R.string.sound_effects),
            enabled = sound.effectsEnabled,
            volume = sound.effectsVolume,
            onEnabledChange = { value -> controller.updateSound { it.copy(effectsEnabled = value) } },
            onVolumeChange = { value -> controller.updateSound { it.copy(effectsVolume = value) } },
            onPreview = { controller.previewEffect() },
            modifier = Modifier.padding(bottom = 9.dp)
        )
        ToggleRow(
            title = stringResource(R.string.sound_mute_all),
            checked = sound.muteAll,
            onCheckedChange = { value -> controller.updateSound { it.copy(muteAll = value) } },
            modifier = Modifier.padding(top = Dim.XXS, bottom = 10.dp)
        )
        ToggleRow(
            title = stringResource(R.string.sound_vibrate),
            checked = sound.vibrateOnExplosion,
            onCheckedChange = { value -> controller.updateSound { it.copy(vibrateOnExplosion = value) } },
            modifier = Modifier.padding(bottom = 14.dp)
        )
        Text(
            text = stringResource(R.string.sound_device_note),
            style = MaterialTheme.typography.labelSmall,
            color = BrandPalette.TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dim.XS)
        )
    }
}
