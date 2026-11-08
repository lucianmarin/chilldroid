package com.lucianmarin.trax.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucianmarin.trax.model.RadioStation
import com.lucianmarin.trax.state.AppState
import com.lucianmarin.trax.ui.theme.ChilltraxIcons
import com.lucianmarin.trax.ui.theme.ChilltraxTheme
import kotlinx.coroutines.launch

/**
 * Single-page Settings screen for Android, combining both macOS settings tabs
 * ("Broadcast & Station" and "Player & Storage") onto one unified scrollable page.
 */
@Composable
fun SettingsScreen(
    appState: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val selectedStation by appState.selectedStationFlow.collectAsState()
    val selectedStream by appState.selectedStreamFlow.collectAsState()
    val availableStreams by appState.availableStreamsFlow.collectAsState()
    val isDiscovering by appState.isDiscoveringBroadcastsFlow.collectAsState()
    val discoveryMessage by appState.discoveryStatusMessageFlow.collectAsState()
    val fetchAlbumCovers by appState.fetchAlbumCoverFromChilltraxFlow.collectAsState()
    val notificationsEnabled by appState.sendSongChangeNotificationsFlow.collectAsState()
    val historyCommitDelaySeconds by appState.historyCommitDelaySecondsFlow.collectAsState()
    val historyEntries by appState.historyEntriesFlow.collectAsState()
    val cacheRevision by appState.coverCache.revision.collectAsState()

    var storageRefreshTrigger by remember { mutableIntStateOf(0) }

    val stationStreams = remember(selectedStation, availableStreams) {
        appState.streamsForSelectedStation
    }

    val coverCacheBytes = remember(cacheRevision, storageRefreshTrigger, historyEntries.size) {
        appState.coverCache.cachedFilesSizeBytes
    }
    val historyDiskBytes = remember(historyEntries.size, storageRefreshTrigger) {
        appState.historyStore.fileSizeBytes
    }

    var streamDropdownExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar with Settings Slow Motion icon and Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = ChilltraxIcons.SettingsSlowMotion,
                        contentDescription = "Settings",
                        tint = ChilltraxTheme.AccentRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Chilltrax Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                OutlinedButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Done",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ChilltraxTheme.AccentRed
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Unified single-page scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION 1: Radio Station
                SettingsSectionCard(title = "Radio Station") {
                    Text(
                        text = "Station",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (station in RadioStation.entries) {
                            val isSelected = selectedStation == station
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ChilltraxTheme.AccentRed else Color.Transparent)
                                    .clickable {
                                        appState.selectStation(station, autoPlayIfCurrentlyPlaying = true)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = station.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${selectedStation.tagline} (${selectedStation.homepageURLString})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // SECTION 2: Active Broadcast Stream
                SettingsSectionCard(title = "Active Broadcast Stream") {
                    Text(
                        text = "Stream Channel",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { streamDropdownExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${selectedStream.name} (${selectedStream.formatDescription})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "▼",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = streamDropdownExpanded,
                            onDismissRequest = { streamDropdownExpanded = false }
                        ) {
                            for (stream in stationStreams) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${stream.name} (${stream.formatDescription})",
                                            fontSize = 12.sp,
                                            fontWeight = if (stream.id == selectedStream.id) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        streamDropdownExpanded = false
                                        appState.selectStream(stream, autoPlayIfCurrentlyPlaying = true)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsMonospaceRow(label = "Stream URL", value = selectedStream.urlString)
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsMonospaceRow(label = "Song Info API", value = appState.activeStationEndpoints.songInfoURLString)
                }

                // SECTION 3: Broadcast Discovery
                SettingsSectionCard(title = "Broadcast Discovery") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    appState.discoverBroadcasts(silent = false)
                                }
                            },
                            enabled = !isDiscovering,
                            colors = ButtonDefaults.buttonColors(containerColor = ChilltraxTheme.AccentRed)
                        ) {
                            Text(
                                text = "Find Broadcasts",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        if (isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = ChilltraxTheme.AccentRed
                            )
                        }

                        if (discoveryMessage.isNotEmpty()) {
                            Text(
                                text = discoveryMessage,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scans chilltrax.com and radiodanz.com (including playlist .pls endpoints and player scripts) to refresh available broadcast streams and live song metadata APIs.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // SECTION 4: Metadata, Artwork & Notifications
                SettingsSectionCard(title = "Metadata, Artwork & Notifications") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Display and fetch album covers",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Automatically fetches artwork from station API or iTunes Search.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = fetchAlbumCovers,
                            onCheckedChange = { appState.fetchAlbumCoverFromChilltrax = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ChilltraxTheme.AccentRed)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Send notification on song change",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Posts an Android notification with track title, artist, and station.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { appState.sendSongChangeNotifications = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ChilltraxTheme.AccentRed)
                        )
                    }
                }

                // SECTION 5: Covers Cache & History File
                SettingsSectionCard(title = "Covers Cache & History File") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Commit Song After Playing",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    appState.historyCommitDelaySeconds =
                                        AppState.clampHistoryCommitDelaySeconds(historyCommitDelaySeconds - 1)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            OutlinedTextField(
                                value = historyCommitDelaySeconds.toString(),
                                onValueChange = { text ->
                                    val parsed = text.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                                    appState.historyCommitDelaySeconds =
                                        AppState.clampHistoryCommitDelaySeconds(parsed)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.width(60.dp)
                            )

                            IconButton(
                                onClick = {
                                    appState.historyCommitDelaySeconds =
                                        AppState.clampHistoryCommitDelaySeconds(historyCommitDelaySeconds + 1)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Text(
                                text = "sec (0–99)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsMonospaceRow(
                        label = "Recorded Songs",
                        value = "${historyEntries.size} tracks"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsMonospaceRow(
                        label = "Album Covers Cache",
                        value = formatByteCount(coverCacheBytes)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsMonospaceRow(
                        label = "Compressed History",
                        value = "${formatByteCount(historyDiskBytes)} (history.tsv.gz)"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsMonospaceRow(
                        label = "Storage Path",
                        value = appState.historyStore.file.parentFile?.absolutePath ?: appState.historyStore.file.absolutePath
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                appState.clearHistory()
                                storageRefreshTrigger += 1
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ChilltraxTheme.AccentRed)
                        ) {
                            Text(
                                text = "Clear Cache & History",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Removes cached album covers and resets history.tsv.gz.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ChilltraxTheme.AccentRed
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SettingsMonospaceRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

internal fun formatByteCount(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(java.util.Locale.US, "%.1f KB", kb)
    }
    val mb = kb / 1024.0
    return String.format(java.util.Locale.US, "%.2f MB", mb)
}
