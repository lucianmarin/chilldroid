package com.lucianmarin.trax.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Native Material Icons from `androidx.compose.material.icons.Icons`:
 * - `MusicNote2` / `MusicNote` (`Icons.Default.MusicNote`): Station artwork, Apple Music overlay, and song history placeholder artwork.
 * - `BarChart4Bars` (`Icons.Default.Equalizer`): Spectrum visualizer segment.
 * - `VitalSigns` (`Icons.Default.GraphicEq`): Waveform visualizer segment.
 * - `SettingsSlowMotion` (`Icons.Default.Settings`): Settings button and header.
 */
object ChilltraxIcons {
    val MusicNote2: ImageVector
        get() = Icons.Default.MusicNote

    val MusicNote: ImageVector
        get() = Icons.Default.MusicNote

    val BarChart4Bars: ImageVector
        get() = Icons.Default.Equalizer

    val VitalSigns: ImageVector
        get() = Icons.Default.GraphicEq

    val SettingsSlowMotion: ImageVector
        get() = Icons.Default.Settings

    val Notes: ImageVector
        get() = Icons.AutoMirrored.Filled.Notes

    val PlayArrowFilled: ImageVector
        get() = Icons.Default.PlayArrow

    val PauseFilled: ImageVector
        get() = Icons.Default.Pause

    val StopFilled: ImageVector
        get() = Icons.Default.Stop

    val Search: ImageVector
        get() = Icons.Default.Search

    val Cancel: ImageVector
        get() = Icons.Default.Cancel

    val Close: ImageVector
        get() = Icons.Default.Close

    val Sensors: ImageVector
        get() = Icons.Default.Sensors
}

/**
 * Full-bleed Compose rendering of `../chilltrax/white.svg` and `../chilltrax/black.svg`:
*/
@Composable
fun ChilltraxIconSvgCanvas(
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    val leftBackgroundColor = ChilltraxTheme.visualizerBackground(isDark)
    val stripe1Color = if (isDark) ChilltraxTheme.AccentRed else ChilltraxTheme.DarkAccentRed
    val stripe2Color = if (isDark) ChilltraxTheme.CoralRed else ChilltraxTheme.DarkCoralRed
    val stripe3Color = if (isDark) ChilltraxTheme.SoftPink else ChilltraxTheme.DarkSoftPink
    val stripe4Color = if (isDark) ChilltraxTheme.VisualizerWhite else ChilltraxTheme.VisualizerBlack

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val leftWidth = w * 0.75f
        val rightWidth = w - leftWidth
        val stripeHeight = h * 0.25f

        // Right 1/4 4-tier vertical blocks
        drawRect(
            color = stripe1Color,
            topLeft = Offset(leftWidth, 0f),
            size = Size(rightWidth, stripeHeight + 0.5f)
        )
        drawRect(
            color = stripe2Color,
            topLeft = Offset(leftWidth, stripeHeight),
            size = Size(rightWidth, stripeHeight + 0.5f)
        )
        drawRect(
            color = stripe3Color,
            topLeft = Offset(leftWidth, stripeHeight * 2f),
            size = Size(rightWidth, stripeHeight + 0.5f)
        )
        drawRect(
            color = stripe4Color,
            topLeft = Offset(leftWidth, stripeHeight * 3f),
            size = Size(rightWidth, stripeHeight)
        )

        // Left 3/4 solid panel
        drawRect(
            color = leftBackgroundColor,
            topLeft = Offset.Zero,
            size = Size(leftWidth + 0.5f, h)
        )
    }
}
