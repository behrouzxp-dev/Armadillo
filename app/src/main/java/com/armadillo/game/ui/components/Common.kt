package com.armadillo.game.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.armadillo.game.R
import com.armadillo.game.game.GameConstants
import com.armadillo.game.ui.theme.BrandPalette
import com.armadillo.game.ui.theme.Dim
import com.armadillo.game.ui.theme.Radii
import kotlin.math.roundToInt

/** Plays the short UI click sample. Screens stay free of the audio implementation. */
val LocalSoundEffect = staticCompositionLocalOf<() -> Unit> { {} }

fun teamColor(index: Int): Color {
    val colors = GameConstants.teamColors
    return Color(colors[index.coerceIn(0, colors.lastIndex)])
}

fun teamEdgeColor(index: Int): Color = teamColor(index)

/** Blend a team colour towards white; matches the old Pillow-free tint helper. */
fun softTint(color: Color, fraction: Float): Color = lerp(color, Color.White, fraction)

@Composable
fun selectionLabel(selected: Boolean): String =
    stringResource(if (selected) R.string.state_selected else R.string.state_not_selected)

@Composable
fun formatFinalTime(nanos: Long): String {
    val seconds = String.format(java.util.Locale.US, "%.2f", nanos.coerceAtLeast(0L) / 1_000_000_000.0)
    return stringResource(R.string.unit_seconds_suffix, seconds)
}

@Composable
fun teamNames(players: List<com.armadillo.game.game.Player>, teamIndex: Int): String {
    val separator = stringResource(R.string.join_and)
    return players.filter { it.teamIndex == teamIndex }
        .sortedBy { it.seat }
        .joinToString(separator) { it.name }
}

/** The brand button: navy fill with white text, or cream fill with a navy hairline. */
@Composable
fun BrandButton(
    text: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    outlinedFillColor: Color = BrandPalette.ButtonOutlinedFill,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    playEffect: Boolean = true,
    disabledContainerColor: Color? = null,
    disabledContentColor: Color? = null,
    onClick: () -> Unit
) {
    val sound = LocalSoundEffect.current
    val shape = RoundedCornerShape(Radii.Button)
    val container = if (outlined) outlinedFillColor else BrandPalette.Navy
    val content = if (outlined) BrandPalette.Navy else BrandPalette.ButtonText
    val fontSize = when {
        height < 44.dp -> 12.sp
        height >= 60.dp -> 17.sp
        else -> 14.sp
    }
    Button(
        onClick = {
            if (playEffect) sound()
            onClick()
        },
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(horizontal = Dim.CardHorizontal),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = disabledContainerColor ?: container,
            disabledContentColor = disabledContentColor ?: BrandPalette.ButtonDisabledText
        ),
        modifier = modifier
            .height(height)
            .then(if (outlined) Modifier.border(1.5.dp, BrandPalette.Navy, shape) else Modifier)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize, lineHeight = (fontSize.value * 1.3f).sp),
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A tappable option card with a transparent unselected state and navy selected state. */
@Composable
fun ChoiceCard(
    selected: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sound = LocalSoundEffect.current
    val shape = RoundedCornerShape(Radii.Control)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) BrandPalette.Navy else Color.Transparent)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = BrandPalette.Navy,
                shape = shape
            )
            .clickable(role = Role.Button) {
                sound()
                onClick()
            }
            .padding(horizontal = Dim.SM, vertical = 10.dp)
            .semantics {
                this.selected = selected
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        content = content
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    fillColor: Color = BrandPalette.Surface,
    strokeColor: Color = BrandPalette.Outline,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Radii.Card)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = fillColor,
        contentColor = BrandPalette.Navy,
        border = BorderStroke(1.dp, strokeColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Dim.CardHorizontal, vertical = Dim.CardVertical),
            content = content
        )
    }
}

@Composable
fun TeamSwatch(
    teamIndex: Int,
    modifier: Modifier = Modifier,
    width: Dp = 6.dp,
    height: Dp = 22.dp,
    radius: Dp = Radii.Chip
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(teamColor(teamIndex))
    )
}

@Composable
fun brandSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = BrandPalette.SurfaceRaised,
    checkedTrackColor = BrandPalette.Navy,
    checkedBorderColor = BrandPalette.Navy,
    uncheckedThumbColor = BrandPalette.SurfaceRaised,
    uncheckedTrackColor = BrandPalette.SurfaceAlt,
    uncheckedBorderColor = BrandPalette.Outline
)

@Composable
fun brandSliderColors() = SliderDefaults.colors(
    thumbColor = BrandPalette.Navy,
    activeTrackColor = BrandPalette.Navy,
    inactiveTrackColor = BrandPalette.SurfaceAccent,
    activeTickColor = BrandPalette.SurfaceRaised,
    inactiveTickColor = BrandPalette.Outline
)

/** One sound channel: preview button, enable switch and a live volume slider. */
@Composable
fun SoundControlCard(
    title: String,
    enabled: Boolean,
    volume: Int,
    onEnabledChange: (Boolean) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = BrandPalette.Navy,
                modifier = Modifier.weight(1f)
            )
            BrandButton(
                text = stringResource(R.string.sound_preview),
                height = Dim.MinTouch,
                playEffect = false,
                modifier = Modifier.width(104.dp),
                onClick = onPreview
            )
        }
        Spacer(Modifier.height(Dim.XXS))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.sound_enabled),
                style = MaterialTheme.typography.bodySmall,
                color = BrandPalette.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange, colors = brandSwitchColors())
        }
        Text(
            text = stringResource(R.string.sound_volume, volume),
            style = MaterialTheme.typography.labelSmall,
            color = BrandPalette.TextSecondary,
            modifier = Modifier.padding(top = Dim.XXXS)
        )
        Slider(
            value = volume.toFloat(),
            onValueChange = { onVolumeChange(it.roundToInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            colors = brandSliderColors()
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Radii.Control)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BrandPalette.SurfaceAlt)
            .border(1.dp, BrandPalette.Outline, shape)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = Dim.SM, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandPalette.Navy,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = brandSwitchColors())
    }
}

/**
 * Standard page frame: RTL header with a back button, a scrolling body and an
 * optional fixed footer action. The scroll position survives same-page updates
 * because the state is remembered across recompositions.
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sound = LocalSoundEffect.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandPalette.Canvas)
            .padding(
                start = Dim.PageSide,
                end = Dim.PageSide,
                top = Dim.PageTop,
                bottom = Dim.PageBottom
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dim.PageHeader),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = BrandPalette.Navy,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { sound(); onBack() },
                modifier = Modifier.size(Dim.MinTouch)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back_arrow),
                    contentDescription = stringResource(R.string.action_back),
                    tint = BrandPalette.Navy,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp, bottom = Dim.MD)
        ) {
            content()
        }
        if (footer != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dim.XS, bottom = Dim.XXS)
            ) {
                footer()
            }
        }
    }
}
