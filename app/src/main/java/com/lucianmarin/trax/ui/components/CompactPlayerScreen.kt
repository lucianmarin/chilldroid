package com.lucianmarin.trax.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.trax.model.SongMetadata
import com.lucianmarin.trax.model.VisualizerMode
import com.lucianmarin.trax.state.AppState
import com.lucianmarin.trax.ui.theme.ChilltraxIconSvgCanvas
import com.lucianmarin.trax.ui.theme.ChilltraxIcons
import com.lucianmarin.trax.ui.theme.ChilltraxTheme

/**
 * Main player screen with the inverted panel order (bottom to top):
 * 1. Bottom: App controls bar (radio station status in text, segmented tabs for No Info / Waveform / Spectrum,
 *    and Settings icon with results counter).
 * 2. Above App Controls: Current song info card (60×60 dp album artwork with `music_note_2` icon,
 *    clickable Title/Artist/Album, and Play/Pause button).
 * 3. Above Current Song Info: Visualizer (`VisualizerView`, 68 dp tall when Waveform or Spectrum is selected).
 * 4. Top: Search bar and song history results (`HistorySearchAndListSection`).
 */
@Composable
fun CompactPlayerScreen(
    appState: AppState,
    onCopyText: (String) -> Unit,
    onOpenURL: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSong by appState.currentSongFlow.collectAsState()
    val visualizerMode by appState.visualizerModeFlow.collectAsState()
    val showCover by appState.fetchAlbumCoverFromChilltraxFlow.collectAsState()
    val isPlaying by appState.radioPlayer.isPlayingFlow.collectAsState()
    val playbackStatus by appState.radioPlayer.statusFlow.collectAsState()
    val selectedStation by appState.selectedStationFlow.collectAsState()
    val cacheRevision by appState.coverCache.revision.collectAsState()

    val statusText = remember(playbackStatus, selectedStation) {
        appState.toolbarStatusLabel
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 4. TOP PANEL: Search Bar and History Results
            HistorySearchAndListSection(
                appState = appState,
                onCopyText = onCopyText,
                onOpenURL = onOpenURL,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // 3. ABOVE SONG INFO: Visualizer Panel (Waveform or Spectrum; hidden when OFF / No Info)
            if (visualizerMode != VisualizerMode.OFF) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ActiveVisualizerSection(
                    appState = appState,
                    mode = visualizerMode,
                    isPlaying = isPlaying
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))

            // 2. ABOVE APP CONTROLS: Current Song Info Panel
            CurrentSongInfoPanel(
                currentSong = currentSong,
                isPlaying = isPlaying,
                showCover = showCover,
                canOpenAppleMusic = isPlaying && currentSong.appleMusicURL != null,
                canCopySong = isPlaying && currentSong.isMeaningfulTrack,
                appState = appState,
                cacheRevision = cacheRevision,
                onOpenAppleMusic = {
                    appState.openCurrentSongOnAppleMusic(onOpenURL)
                },
                onCopyCurrentSong = {
                    appState.copyCurrentSongToClipboard(onCopyText)
                },
                onTogglePlayPause = {
                    appState.togglePlayPause()
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))

            // 1. BOTTOM PANEL: App Controls Bar
            BottomAppControlsBar(
                statusLabel = statusText,
                statusDotColor = ChilltraxTheme.statusDotColor(playbackStatus),
                visualizerMode = visualizerMode,
                onStatusTap = {
                    appState.handleToolbarStatusTap(autoPlayIfCurrentlyPlaying = true) {
                        appState.openSettings()
                    }
                },
                onSelectVisualizerMode = { mode ->
                    appState.selectVisualizerMode(mode)
                }
            )
        }
    }
}

@Composable
private fun CurrentSongInfoPanel(
    currentSong: SongMetadata,
    isPlaying: Boolean,
    showCover: Boolean,
    canOpenAppleMusic: Boolean,
    canCopySong: Boolean,
    appState: AppState,
    cacheRevision: Int,
    onOpenAppleMusic: () -> Unit,
    onCopyCurrentSong: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val coverURLString = if (isPlaying && showCover) currentSong.coverURL else null
    val cachedBitmap = remember(coverURLString, cacheRevision) {
        val bytes = appState.coverCache.cachedBytes(coverURLString) ?: return@remember null
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    }

    LaunchedEffect(coverURLString) {
        if (coverURLString != null && !appState.coverCache.isCachedOnDisk(coverURLString)) {
            appState.coverCache.cacheCover(coverURLString)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChilltraxTheme.HEADER_SECTION_HEIGHT.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = ChilltraxTheme.HORIZONTAL_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCover) {
            val isDark = isSystemInDarkTheme()
            val placeholderBackground = ChilltraxTheme.visualizerBackground(isDark)
            val placeholderNoteTint = ChilltraxTheme.visualizerContrastColor(isDark)
            val coverCornerShape = RoundedCornerShape(12.dp)

            // 60x60 dp Album Artwork with `music_note_2` replacing the Apple logo
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .shadow(4.dp, coverCornerShape)
                    .clip(coverCornerShape)
                    .background(placeholderBackground)
                    .clickable(enabled = canOpenAppleMusic, onClick = onOpenAppleMusic),
                contentAlignment = Alignment.Center
            ) {
                if (cachedBitmap != null) {
                    Image(
                        bitmap = cachedBitmap,
                        contentDescription = "Open on Apple Music",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (canOpenAppleMusic) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ChilltraxIcons.MusicNote2,
                                contentDescription = "Music Note 2",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                } else {
                    // Default station artwork rendered from icon.svg + Music Note 2 icon replacing Apple logo
                    ChilltraxIconSvgCanvas(
                        modifier = Modifier.fillMaxSize(),
                        isDark = isDark
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = ChilltraxIcons.MusicNote2,
                            contentDescription = "Music Note 2",
                            tint = placeholderNoteTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
        }

        // Song Title, Artist, Album/Mix (equal height to the 60dp album cover)
        val onePxThickness = with(LocalDensity.current) { 1.toDp() }
        val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        Column(
            modifier = Modifier
                .weight(1f)
                .height(60.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val titleText = if (isPlaying) currentSong.title else SongMetadata.placeholder.title
            val artistText = if (isPlaying) currentSong.artist else SongMetadata.placeholder.artist
            val albumText = if (isPlaying) {
                currentSong.albumOrMix.ifEmpty { SongMetadata.placeholder.albumOrMix }
            } else {
                SongMetadata.placeholder.albumOrMix
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(enabled = canCopySong, onClick = onCopyCurrentSong),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = titleText,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(thickness = onePxThickness, color = dividerColor)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(enabled = canCopySong, onClick = onCopyCurrentSong),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = artistText,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isPlaying) ChilltraxTheme.AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(thickness = onePxThickness, color = dividerColor)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = albumText,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Play / Stop Button (#FA0000 red circle)
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(6.dp, CircleShape, ambientColor = ChilltraxTheme.AccentRed, spotColor = ChilltraxTheme.AccentRed)
                .clip(CircleShape)
                .background(ChilltraxTheme.AccentRed)
                .clickable(onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) ChilltraxIcons.StopFilled else ChilltraxIcons.PlayArrowFilled,
                contentDescription = if (isPlaying) "Stop" else "Play",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ActiveVisualizerSection(
    appState: AppState,
    mode: VisualizerMode,
    isPlaying: Boolean
) {
    val levels by appState.radioPlayer.visualizerLevelsFlow.collectAsState()
    val trailLevels by appState.radioPlayer.visualizerTrailLevelsFlow.collectAsState()
    val phase by appState.radioPlayer.waveformPhaseFlow.collectAsState()

    VisualizerView(
        mode = mode,
        levels = levels,
        trailLevels = trailLevels,
        phase = phase,
        isPlaying = isPlaying,
        modifier = Modifier
            .fillMaxWidth()
            .height(ChilltraxTheme.VISUALIZER_SECTION_HEIGHT.dp)
    )
}

@Composable
private fun BottomAppControlsBar(
    statusLabel: String,
    statusDotColor: Color,
    visualizerMode: VisualizerMode,
    onStatusTap: () -> Unit,
    onSelectVisualizerMode: (VisualizerMode) -> Unit
) {
    val controlButtonHeight = ChilltraxTheme.CONTROL_BUTTON_HEIGHT.dp
    val controlButtonShape = RoundedCornerShape(ChilltraxTheme.CORNER_RADIUS.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .height(ChilltraxTheme.CONTROLS_BAR_HEIGHT.dp)
            .padding(horizontal = ChilltraxTheme.HORIZONTAL_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Radio station status in text (tapping switches TRAX <-> DANZ when playing, or opens Settings)
        Row(
            modifier = Modifier
                .height(controlButtonHeight)
                .clip(controlButtonShape)
                .background(statusDotColor.copy(alpha = 0.14f))
                .clickable(onClick = onStatusTap)
                .padding(horizontal = 10.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )
            Text(
                text = statusLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = statusDotColor
            )
        }

        // Right: Segmented tabs for No Info (OFF), Waveform (Vital Signs), Spectrum (Bar Chart 4 Bars)
        Row(
            modifier = Modifier
                .height(controlButtonHeight)
                .clip(controlButtonShape)
                .background(ChilltraxTheme.ControlButtonBackground),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (tabMode in VisualizerMode.segmentedTabsOrder) {
                val isSelected = visualizerMode == tabMode
                val icon = when (tabMode) {
                    VisualizerMode.OFF -> ChilltraxIcons.Notes
                    VisualizerMode.WAVEFORM -> ChilltraxIcons.VitalSigns
                    VisualizerMode.SPECTRUM -> ChilltraxIcons.BarChart4Bars
                }
                Row(
                    modifier = Modifier
                        .height(controlButtonHeight)
                        .clip(controlButtonShape)
                        .background(
                            if (isSelected) ChilltraxTheme.AccentRed else Color.Transparent
                        )
                        .clickable { onSelectVisualizerMode(tabMode) }
                        .padding(horizontal = 9.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tabMode.title,
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = tabMode.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
