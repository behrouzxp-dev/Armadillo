package com.armadillo.game.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.armadillo.game.GameController
import com.armadillo.game.R
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim
import com.armadillo.game.ui.theme.Radii

private const val MAX_PLAYER_NAME_LENGTH = 24

/** All eight dialogs of the app, drawn with Material 3 and the brand palette. */
@Composable
fun GameDialog(controller: GameController, state: DialogState) {
    when (state) {
        DialogState.WordsUnavailable -> BrandDialog(
            title = stringResource(R.string.dialog_words_failed_title),
            message = stringResource(R.string.dialog_words_failed_message),
            confirmText = stringResource(R.string.action_ok),
            onConfirm = { controller.dismissWordsDialogs() },
            onDismiss = { controller.dismissWordsDialogs() }
        )

        DialogState.WordsNotReady -> BrandDialog(
            title = stringResource(R.string.dialog_words_pending_title),
            message = stringResource(R.string.dialog_words_pending_message),
            confirmText = stringResource(R.string.action_ok),
            onConfirm = { controller.dismissWordsDialogs() },
            onDismiss = { controller.dismissWordsDialogs() }
        )

        DialogState.DiscardedCheckpoint -> BrandDialog(
            title = stringResource(R.string.dialog_checkpoint_title),
            message = stringResource(R.string.dialog_checkpoint_message),
            confirmText = stringResource(R.string.action_ok),
            onConfirm = { controller.dismissWordsDialogs() },
            onDismiss = { controller.dismissWordsDialogs() }
        )

        DialogState.SetupIncomplete -> BrandDialog(
            title = stringResource(R.string.setup_incomplete_title),
            message = stringResource(R.string.setup_incomplete_message),
            confirmText = stringResource(R.string.action_ok),
            onConfirm = { controller.dismissWordsDialogs() },
            onDismiss = { controller.dismissWordsDialogs() }
        )

        DialogState.ExitApp -> BrandDialog(
            title = stringResource(R.string.dialog_exit_app_title),
            message = stringResource(R.string.dialog_exit_app_message),
            confirmText = stringResource(R.string.action_exit),
            onConfirm = { controller.exitApp() },
            dismissText = stringResource(R.string.action_dismiss),
            onDismiss = { controller.dismissWordsDialogs() }
        )

        is DialogState.ConfirmEndGame -> BrandDialog(
            title = stringResource(R.string.dialog_end_title),
            message = stringResource(R.string.dialog_end_message),
            confirmText = stringResource(R.string.game_end),
            onConfirm = { controller.confirmEndGame() },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { controller.cancelEndGame(state.wasPlaying) }
        )

        is DialogState.ConfirmExitGame -> BrandDialog(
            title = stringResource(R.string.dialog_exit_game_title),
            message = stringResource(R.string.dialog_exit_game_message),
            confirmText = stringResource(R.string.action_exit),
            onConfirm = { controller.confirmExitGame() },
            dismissText = stringResource(R.string.dialog_return_to_game),
            onDismiss = { controller.cancelExitGame(state.wasPlaying) }
        )

        is DialogState.EditName -> EditNameDialog(controller = controller, state = state)
    }
}

@Composable
private fun BrandDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = BrandPalette.Navy
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = BrandPalette.TextSecondary
            )
        },
        confirmButton = { BrandDialogButton(text = confirmText, onClick = onConfirm) },
        dismissButton = if (dismissText != null) {
            { BrandDialogButton(text = dismissText, onClick = onDismiss) }
        } else {
            null
        },
        shape = RoundedCornerShape(Radii.Dialog),
        containerColor = BrandPalette.SurfaceRaised,
        titleContentColor = BrandPalette.Navy,
        textContentColor = BrandPalette.TextSecondary
    )
}

@Composable
private fun EditNameDialog(controller: GameController, state: DialogState.EditName) {
    var value by remember(state.seat) { mutableStateOf(state.initial) }
    AlertDialog(
        onDismissRequest = { controller.dismissDialog() },
        title = {
            Text(
                text = stringResource(R.string.players_name_dialog_title, state.seat + 1),
                style = MaterialTheme.typography.headlineSmall,
                color = BrandPalette.Navy
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { updated ->
                        if (updated.length <= MAX_PLAYER_NAME_LENGTH) value = updated
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = RoundedCornerShape(Dim.SM),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BrandPalette.Navy,
                        unfocusedTextColor = BrandPalette.Navy,
                        focusedBorderColor = BrandPalette.Navy,
                        unfocusedBorderColor = BrandPalette.Outline,
                        cursorColor = BrandPalette.Navy,
                        focusedContainerColor = BrandPalette.Surface,
                        unfocusedContainerColor = BrandPalette.Surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                )
            }
        },
        confirmButton = {
            BrandDialogButton(
                text = stringResource(R.string.action_save),
                onClick = { controller.commitPlayerName(state.seat, value) }
            )
        },
        dismissButton = {
            BrandDialogButton(
                text = stringResource(R.string.action_cancel),
                onClick = { controller.dismissDialog() }
            )
        },
        shape = RoundedCornerShape(Radii.Dialog),
        containerColor = BrandPalette.SurfaceRaised,
        titleContentColor = BrandPalette.Navy,
        textContentColor = BrandPalette.TextSecondary
    )
}

@Composable
private fun BrandDialogButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(Radii.Button),
        colors = ButtonDefaults.textButtonColors(contentColor = BrandPalette.Navy),
        modifier = Modifier.heightIn(min = Dim.MinTouch)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
