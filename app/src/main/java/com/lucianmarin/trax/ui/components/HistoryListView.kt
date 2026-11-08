package com.lucianmarin.trax.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.trax.model.SongHistoryEntry
import com.lucianmarin.trax.state.AppState
import com.lucianmarin.trax.storage.AlbumCoverCache
import com.lucianmarin.trax.ui.theme.ChilltraxIcons
import com.lucianmarin.trax.ui.theme.ChilltraxTheme
import kotlin.math.max

@Composable
fun HistorySearchAndListSection(
    appState: AppState,
    onCopyText: (String) -> Unit,
    onOpenURL: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by appState.historySearchQueryFlow.collectAsState()
    val historyEntries by appState.historyEntriesFlow.collectAsState()
    val historyCommitCount by appState.historyCommitCountFlow.collectAsState()
    val displayLimit by appState.historyDisplayLimitFlow.collectAsState()
    val copiedID by appState.copiedHistoryEntryIDFlow.collectAsState()
    val showCover by appState.fetchAlbumCoverFromChilltraxFlow.collectAsState()
    val isSettingsVisible by appState.isSettingsVisibleFlow.collectAsState()
    val cacheRevision by appState.coverCache.revision.collectAsState()
    val listState = rememberLazyListState()

    val filteredEntries = remember(searchQuery, historyEntries) {
        appState.filteredHistoryEntries
    }
    val displayedEntries = remember(searchQuery, historyEntries, displayLimit) {
        appState.displayedHistoryEntries
    }

    LaunchedEffect(historyCommitCount) {
        if (historyCommitCount > 0 && displayedEntries.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val controlButtonHeight = ChilltraxTheme.CONTROL_BUTTON_HEIGHT.dp
    val controlButtonShape = RoundedCornerShape(ChilltraxTheme.CORNER_RADIUS.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        // Search Bar + Settings button at the very top of the inverted player layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChilltraxTheme.SEARCH_BAR_SECTION_HEIGHT.dp)
                .padding(horizontal = ChilltraxTheme.HORIZONTAL_PADDING.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(controlButtonHeight)
                    .clip(controlButtonShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = ChilltraxIcons.Search,
                    contentDescription = "Search song history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search title, artist, album, station…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { appState.historySearchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(ChilltraxTheme.AccentRed),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { appState.historySearchQuery = "" },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = ChilltraxIcons.Cancel,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${filteredEntries.size}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Right of Search Bar: Settings Slow Motion icon button
            Box(
                modifier = Modifier
                    .height(controlButtonHeight)
                    .clip(controlButtonShape)
                    .background(
                        if (isSettingsVisible) {
                            ChilltraxTheme.AccentRed
                        } else {
                            ChilltraxTheme.ControlButtonBackground
                        }
                    )
                    .clickable { appState.toggleSettings() }
                    .padding(horizontal = 10.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ChilltraxIcons.SettingsSlowMotion,
                    contentDescription = "Settings",
                    tint = if (isSettingsVisible) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        // History Results List
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (displayedEntries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = ChilltraxIcons.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (historyEntries.isEmpty()) {
                            "Played songs will appear here automatically."
                        } else {
                            "No matching tracks found."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    itemsIndexed(
                        items = displayedEntries,
                        key = { _, item -> item.id }
                    ) { index, entry ->
                        val badgeLabel = if (copiedID == entry.id) "COPY" else entry.stationBadgeLabel
                        HistoryRowItem(
                            entry = entry,
                            showCover = showCover,
                            coverCache = appState.coverCache,
                            cacheRevision = cacheRevision,
                            badgeLabel = badgeLabel,
                            onRowClick = {
                                val text = appState.copyHistoryEntryToClipboard(entry)
                                onCopyText(text)
                            },
                            onCoverClick = {
                                entry.appleMusicSearchURL?.let(onOpenURL)
                            }
                        )
                        if (index < displayedEntries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            )
                        }
                        if (index >= max(0, displayedEntries.size - 6)) {
                            LaunchedEffect(entry.id, displayedEntries.size) {
                                appState.loadMoreHistoryIfNeeded(entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRowItem(
    entry: SongHistoryEntry,
    showCover: Boolean,
    coverCache: AlbumCoverCache,
    cacheRevision: Int,
    badgeLabel: String,
    onRowClick: () -> Unit,
    onCoverClick: () -> Unit
) {
    val isCopied = badgeLabel == "COPY"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChilltraxTheme.HISTORY_ROW_HEIGHT.dp)
            .clickable(onClick = onRowClick)
            .padding(horizontal = ChilltraxTheme.HORIZONTAL_PADDING.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCover) {
            HistoryCoverThumbnail(
                coverURLString = entry.coverURL,
                coverCache = coverCache,
                cacheRevision = cacheRevision,
                onClick = onCoverClick
            )

            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .padding(vertical = 3.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.title.ifEmpty { "Unknown Title" },
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            val subtitle = buildString {
                append(entry.artist.ifEmpty { "Unknown Artist" })
                if (entry.albumOrMix.isNotEmpty()) {
                    append(" • ")
                    append(entry.albumOrMix)
                }
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .height(40.dp)
                .padding(vertical = 1.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatRelativeTimestamp(entry.playedAtEpochSeconds),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isCopied) {
                            ChilltraxTheme.AccentRed
                        } else {
                            ChilltraxTheme.SoftPink.copy(alpha = 0.20f)
                        }
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeLabel,
                    fontSize = 11.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCopied) Color.White else ChilltraxTheme.CoralRed,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
    }
}

@Composable
private fun HistoryCoverThumbnail(
    coverURLString: String?,
    coverCache: AlbumCoverCache,
    cacheRevision: Int,
    onClick: () -> Unit
) {
    val cachedBitmap = remember(coverURLString, cacheRevision) {
        val bytes = coverCache.cachedBytes(coverURLString) ?: return@remember null
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    }

    LaunchedEffect(coverURLString) {
        if (coverURLString != null && !coverCache.isCachedOnDisk(coverURLString)) {
            coverCache.cacheCover(coverURLString)
        }
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(ChilltraxTheme.CORNER_RADIUS.dp))
            .background(Color(0xFF141414))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (cachedBitmap != null) {
            Image(
                bitmap = cachedBitmap,
                contentDescription = "Open song on Apple Music",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Music Note symbol from Google Fonts Material Symbols for history results
            Icon(
                imageVector = ChilltraxIcons.MusicNote,
                contentDescription = "Music Note",
                tint = ChilltraxTheme.CoralRed,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

internal fun formatRelativeTimestamp(epochSeconds: Long, nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): String {
    val diff = max(0L, nowEpochSeconds - epochSeconds)
    return when {
        diff < 60L -> "just now"
        diff < 3600L -> "${diff / 60L}m ago"
        diff < 86_400L -> "${diff / 3600L}h ago"
        else -> "${diff / 86_400L}d ago"
    }
}
