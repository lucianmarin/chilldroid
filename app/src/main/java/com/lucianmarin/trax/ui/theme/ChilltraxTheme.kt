package com.lucianmarin.trax.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lucianmarin.trax.audio.PlaybackStatus
import kotlin.math.max
import kotlin.math.min

enum class PanelSection(val orderFromBottom: Int, val label: String) {
    APP_CONTROLS(0, "App Controls"),
    CURRENT_SONG_INFO(1, "Current Song Info"),
    VISUALIZER(2, "Visualizer"),
    SEARCH_AND_HISTORY(3, "Search Bar & History Results")
}

object ChilltraxTheme {
    // Exact colors from `icon.svg`
    val AccentRed = Color(0xFFFA0000)
    val LiveGreen = Color(0xFF34C759)
    val CoralRed = Color(0xFFFF5252)
    val SoftPink = Color(0xFFFFA8A8)
    val VisualizerGray = Color(0xFF808080)
    val VisualizerBlack = Color(0xFF000000)
    val VisualizerWhite = Color(0xFFFFFFFF)

    // Panel layout order from bottom to top (or top to bottom when rendering a vertical Column)
    val PanelOrderBottomToTop: List<PanelSection> = listOf(
        PanelSection.APP_CONTROLS,
        PanelSection.CURRENT_SONG_INFO,
        PanelSection.VISUALIZER,
        PanelSection.SEARCH_AND_HISTORY
    )

    val PanelOrderTopToBottom: List<PanelSection> = PanelOrderBottomToTop.reversed()

    // Compact layout dimensions matching `../chilltrax`
    const val COMPACT_WIDTH = 452f
    const val HEADER_SECTION_HEIGHT = 80f
    const val VISUALIZER_SECTION_HEIGHT = 68f
    const val CONTROLS_BAR_HEIGHT = 48f
    const val CONTROL_BUTTON_HEIGHT = 30f
    const val CONTROL_BUTTON_CORNER_RADIUS = 8f
    const val COMPACT_HEIGHT = HEADER_SECTION_HEIGHT + VISUALIZER_SECTION_HEIGHT
    const val COMPACT_NO_VISUALIZER_HEIGHT = HEADER_SECTION_HEIGHT
    const val SEARCH_BAR_SECTION_HEIGHT = 50f
    const val HISTORY_ROW_HEIGHT = 46f
    const val HISTORY_LIST_VERTICAL_PADDING = 4f
    const val MAX_VISIBLE_HISTORY_ROWS = 6
    const val EXPANDED_HEIGHT = COMPACT_HEIGHT + SEARCH_BAR_SECTION_HEIGHT +
        (MAX_VISIBLE_HISTORY_ROWS * HISTORY_ROW_HEIGHT) + HISTORY_LIST_VERTICAL_PADDING

    fun historyListHeight(forResultCount: Int): Float {
        val visibleRows = min(max(0, forResultCount), MAX_VISIBLE_HISTORY_ROWS)
        if (visibleRows == 0) return 0f
        return (visibleRows * HISTORY_ROW_HEIGHT) + HISTORY_LIST_VERTICAL_PADDING
    }

    fun contentHeight(isExpanded: Boolean, resultCount: Int, showVisualizer: Boolean = true): Float {
        val baseHeight = if (showVisualizer) COMPACT_HEIGHT else COMPACT_NO_VISUALIZER_HEIGHT
        if (!isExpanded) return baseHeight
        return baseHeight + SEARCH_BAR_SECTION_HEIGHT + historyListHeight(resultCount)
    }

    fun visualizerBackground(isDark: Boolean): Color =
        if (isDark) VisualizerBlack else VisualizerWhite

    fun visualizerContrastColor(isDark: Boolean): Color =
        if (isDark) VisualizerWhite else VisualizerBlack

    val ControlButtonBackground = VisualizerGray.copy(alpha = 0.14f)

    fun statusDotColor(status: PlaybackStatus): Color = when (status) {
        PlaybackStatus.PLAYING -> LiveGreen
        PlaybackStatus.CONNECTING -> AccentRed
        PlaybackStatus.PAUSED -> VisualizerGray
        PlaybackStatus.IDLE -> VisualizerGray
        PlaybackStatus.ERROR -> AccentRed
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = ChilltraxTheme.AccentRed,
    onPrimary = Color.White,
    secondary = ChilltraxTheme.CoralRed,
    tertiary = ChilltraxTheme.SoftPink,
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF141414),
    surfaceVariant = Color(0xFF1E1E1E),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFFA0A0A0)
)

private val LightColorScheme = lightColorScheme(
    primary = ChilltraxTheme.AccentRed,
    onPrimary = Color.White,
    secondary = ChilltraxTheme.CoralRed,
    tertiary = ChilltraxTheme.SoftPink,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFFE0E0E0),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF202020),
    onSurfaceVariant = Color(0xFF808080)
)

@Composable
fun ChilltraxAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
