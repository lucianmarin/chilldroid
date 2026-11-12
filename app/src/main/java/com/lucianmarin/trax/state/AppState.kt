package com.lucianmarin.trax.state

import android.content.SharedPreferences
import com.lucianmarin.trax.audio.PlaybackStatus
import com.lucianmarin.trax.audio.RadioPlayer
import com.lucianmarin.trax.model.RadioStation
import com.lucianmarin.trax.model.SongHistoryEntry
import com.lucianmarin.trax.model.SongMetadata
import com.lucianmarin.trax.model.StationAPIEndpoints
import com.lucianmarin.trax.model.StationStream
import com.lucianmarin.trax.model.VisualizerMode
import com.lucianmarin.trax.network.ChilltraxClient
import com.lucianmarin.trax.storage.AlbumCoverCache
import com.lucianmarin.trax.storage.CompressedHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

interface PreferencesStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
}

class InMemoryPreferencesStore : PreferencesStore {
    private val map = mutableMapOf<String, Any?>()

    override fun getString(key: String): String? = map[key] as? String
    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = (map[key] as? Boolean) ?: default
    override fun putBoolean(key: String, value: Boolean) {
        map[key] = value
    }

    override fun getInt(key: String, default: Int): Int = (map[key] as? Int) ?: default
    override fun putInt(key: String, value: Int) {
        map[key] = value
    }
}

class SharedPreferencesStore(private val prefs: SharedPreferences) : PreferencesStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }
}

class AppState(
    val client: ChilltraxClient = ChilltraxClient(),
    val historyStore: CompressedHistoryStore,
    val coverCache: AlbumCoverCache = AlbumCoverCache(
        directory = File(historyStore.file.parentFile, "Covers"),
        client = client
    ),
    val radioPlayer: RadioPlayer = RadioPlayer(),
    private val preferences: PreferencesStore = InMemoryPreferencesStore(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + RadioPlayer.defaultMainOrUnconfinedDispatcher()),
    startPollingAutomatically: Boolean = true,
    minimumHistoryPlayDurationOverride: Double? = null
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _currentSong = MutableStateFlow(SongMetadata.placeholder)
    val currentSongFlow: StateFlow<SongMetadata> = _currentSong.asStateFlow()
    var currentSong: SongMetadata
        get() = _currentSong.value
        set(value) {
            _currentSong.value = value
        }

    private val _historyEntries = MutableStateFlow<List<SongHistoryEntry>>(emptyList())
    val historyEntriesFlow: StateFlow<List<SongHistoryEntry>> = _historyEntries.asStateFlow()
    var historyEntries: List<SongHistoryEntry>
        get() = _historyEntries.value
        private set(value) {
            _historyEntries.value = value
        }

    private val _historyCommitCount = MutableStateFlow(0)
    val historyCommitCountFlow: StateFlow<Int> = _historyCommitCount.asStateFlow()
    var historyCommitCount: Int
        get() = _historyCommitCount.value
        private set(value) {
            _historyCommitCount.value = value
        }

    private val _historyDisplayLimit = MutableStateFlow(HISTORY_PAGE_SIZE)
    val historyDisplayLimitFlow: StateFlow<Int> = _historyDisplayLimit.asStateFlow()
    var historyDisplayLimit: Int
        get() = _historyDisplayLimit.value
        private set(value) {
            _historyDisplayLimit.value = value
        }

    private val _copiedHistoryEntryID = MutableStateFlow<String?>(null)
    val copiedHistoryEntryIDFlow: StateFlow<String?> = _copiedHistoryEntryID.asStateFlow()
    var copiedHistoryEntryID: String?
        get() = _copiedHistoryEntryID.value
        private set(value) {
            _copiedHistoryEntryID.value = value
        }

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQueryFlow: StateFlow<String> = _historySearchQuery.asStateFlow()
    var historySearchQuery: String
        get() = _historySearchQuery.value
        set(value) {
            val previous = _historySearchQuery.value
            _historySearchQuery.value = value
            if (previous != value) {
                historyDisplayLimit = HISTORY_PAGE_SIZE
            }
            if (manualHistoryExpanded) {
                isHistoryExpanded = true
            } else {
                val trimmed = value.trim()
                isHistoryExpanded = trimmed.isNotEmpty() && !filteredHistoryEntries.isEmpty()
            }
        }

    private val _isHistoryExpanded = MutableStateFlow(false)
    val isHistoryExpandedFlow: StateFlow<Boolean> = _isHistoryExpanded.asStateFlow()
    var isHistoryExpanded: Boolean
        get() = _isHistoryExpanded.value
        set(value) {
            _isHistoryExpanded.value = value
        }

    private var manualHistoryExpanded: Boolean = false

    private val _isSettingsVisible = MutableStateFlow(false)
    val isSettingsVisibleFlow: StateFlow<Boolean> = _isSettingsVisible.asStateFlow()
    var isSettingsVisible: Boolean
        get() = _isSettingsVisible.value
        set(value) {
            _isSettingsVisible.value = value
        }

    private val _visualizerMode = MutableStateFlow(VisualizerMode.SPECTRUM)
    val visualizerModeFlow: StateFlow<VisualizerMode> = _visualizerMode.asStateFlow()
    var visualizerMode: VisualizerMode
        get() = _visualizerMode.value
        private set(value) {
            _visualizerMode.value = value
            radioPlayer.isVisualizerEnabled = (value != VisualizerMode.OFF)
        }

    private var lastActiveVisualizerMode: VisualizerMode = VisualizerMode.SPECTRUM

    private val _selectedStation = MutableStateFlow(RadioStation.CHILLTRAX)
    val selectedStationFlow: StateFlow<RadioStation> = _selectedStation.asStateFlow()
    var selectedStation: RadioStation
        get() = _selectedStation.value
        private set(value) {
            _selectedStation.value = value
        }

    private val _availableStreams = MutableStateFlow(StationStream.defaultStreams)
    val availableStreamsFlow: StateFlow<List<StationStream>> = _availableStreams.asStateFlow()
    var availableStreams: List<StationStream>
        get() = _availableStreams.value
        private set(value) {
            _availableStreams.value = value
        }

    private val _selectedStream = MutableStateFlow(StationStream.defaultStream)
    val selectedStreamFlow: StateFlow<StationStream> = _selectedStream.asStateFlow()
    var selectedStream: StationStream
        get() = _selectedStream.value
        private set(value) {
            _selectedStream.value = value
        }

    private var stationEndpointsByStation: MutableMap<RadioStation, StationAPIEndpoints> = mutableMapOf(
        RadioStation.CHILLTRAX to RadioStation.CHILLTRAX.defaultAPIEndpoints,
        RadioStation.RADIO_DANZ to RadioStation.RADIO_DANZ.defaultAPIEndpoints
    )

    private val _fetchAlbumCoverFromChilltrax = MutableStateFlow(true)
    val fetchAlbumCoverFromChilltraxFlow: StateFlow<Boolean> = _fetchAlbumCoverFromChilltrax.asStateFlow()
    var fetchAlbumCoverFromChilltrax: Boolean
        get() = _fetchAlbumCoverFromChilltrax.value
        set(value) {
            val previous = _fetchAlbumCoverFromChilltrax.value
            _fetchAlbumCoverFromChilltrax.value = value
            preferences.putBoolean(KEY_FETCH_ALBUM_COVERS, value)
            if (!previous && value) {
                scope.launch {
                    fetchMissingAlbumCovers()
                }
            }
        }

    private val _isDiscoveringBroadcasts = MutableStateFlow(false)
    val isDiscoveringBroadcastsFlow: StateFlow<Boolean> = _isDiscoveringBroadcasts.asStateFlow()
    var isDiscoveringBroadcasts: Boolean
        get() = _isDiscoveringBroadcasts.value
        private set(value) {
            _isDiscoveringBroadcasts.value = value
        }

    private val _discoveryStatusMessage = MutableStateFlow("")
    val discoveryStatusMessageFlow: StateFlow<String> = _discoveryStatusMessage.asStateFlow()
    var discoveryStatusMessage: String
        get() = _discoveryStatusMessage.value
        private set(value) {
            _discoveryStatusMessage.value = value
        }

    private val _historyCommitDelaySeconds = MutableStateFlow(DEFAULT_HISTORY_COMMIT_DELAY_SECONDS)
    val historyCommitDelaySecondsFlow: StateFlow<Int> = _historyCommitDelaySeconds.asStateFlow()
    var historyCommitDelaySeconds: Int
        get() = _historyCommitDelaySeconds.value
        set(value) {
            val clamped = clampHistoryCommitDelaySeconds(value)
            _historyCommitDelaySeconds.value = clamped
            minimumHistoryPlayDuration = clamped.toDouble()
            preferences.putInt(KEY_HISTORY_COMMIT_DELAY_SECONDS, clamped)
        }

    var minimumHistoryPlayDuration: Double = DEFAULT_HISTORY_COMMIT_DELAY_SECONDS.toDouble()
        private set

    private var pollingJob: Job? = null
    private var pendingHistoryCommitJob: Job? = null
    private var pendingHistorySong: SongMetadata? = null
    private var pendingHistoryStream: StationStream? = null
    private var pendingHistoryAccumulatedSeconds: Double = 0.0
    private var pendingHistoryPlaybackStartedAtMillis: Long? = null

    init {
        val initialDelay = if (minimumHistoryPlayDurationOverride != null) {
            clampHistoryCommitDelaySeconds(minimumHistoryPlayDurationOverride.roundToInt())
        } else {
            val stored = preferences.getInt(KEY_HISTORY_COMMIT_DELAY_SECONDS, DEFAULT_HISTORY_COMMIT_DELAY_SECONDS)
            clampHistoryCommitDelaySeconds(stored)
        }
        _historyCommitDelaySeconds.value = initialDelay
        minimumHistoryPlayDuration = minimumHistoryPlayDurationOverride ?: initialDelay.toDouble()

        val savedStation = RadioStation.fromRawValue(preferences.getString(KEY_SELECTED_STATION))
        _selectedStation.value = savedStation

        val savedStreamsJson = preferences.getString(KEY_CUSTOM_STREAMS)
        if (!savedStreamsJson.isNullOrBlank()) {
            val decoded = runCatching { json.decodeFromString<List<StationStream>>(savedStreamsJson) }.getOrNull()
            if (!decoded.isNullOrEmpty()) {
                val merged = decoded.toMutableList()
                for (builtIn in StationStream.defaultStreams) {
                    if (merged.none { it.urlString == builtIn.urlString }) {
                        merged.add(builtIn)
                    }
                }
                _availableStreams.value = merged
            }
        }

        val savedStreamID = preferences.getString(KEY_SELECTED_STREAM_ID)
        val matchedStream = if (savedStreamID != null) {
            availableStreams.firstOrNull { it.id == savedStreamID && it.station == savedStation }
        } else {
            null
        }
        _selectedStream.value = matchedStream
            ?: StationStream.preferredDefaultStream(station = savedStation, streams = availableStreams)

        val savedVisMode = VisualizerMode.fromRawValue(preferences.getString(KEY_VISUALIZER_MODE))
        _visualizerMode.value = savedVisMode
        radioPlayer.isVisualizerEnabled = (savedVisMode != VisualizerMode.OFF)

        val savedLastActive = VisualizerMode.fromRawValue(preferences.getString(KEY_LAST_ACTIVE_VISUALIZER_MODE))
        lastActiveVisualizerMode = if (savedLastActive != VisualizerMode.OFF) {
            savedLastActive
        } else if (savedVisMode != VisualizerMode.OFF) {
            savedVisMode
        } else {
            VisualizerMode.SPECTRUM
        }

        _fetchAlbumCoverFromChilltrax.value = preferences.getBoolean(KEY_FETCH_ALBUM_COVERS, true)

        _historyEntries.value = historyStore.load()
        if (_fetchAlbumCoverFromChilltrax.value) {
            for (entry in _historyEntries.value.take(HISTORY_PAGE_SIZE)) {
                val url = entry.coverURL
                if (url != null) {
                    scope.launch {
                        coverCache.cacheCover(urlString = url)
                    }
                }
            }
        }

        radioPlayer.onICYStreamTitle = { rawTitle ->
            SongMetadata.fromICYStreamTitle(rawTitle)?.let { icySong ->
                handleIncomingSongMetadata(icySong, sourceIsICY = true)
            }
        }

        scope.launch {
            radioPlayer.isPlayingFlow.collect { playing ->
                handlePlaybackStateChanged(isPlaying = playing)
            }
        }

        if (startPollingAutomatically) {
            startMetadataPolling()
            scope.launch {
                discoverBroadcasts(silent = true)
            }
        }
    }

    val streamsForSelectedStation: List<StationStream>
        get() = availableStreams.filter { it.station == selectedStation }

    val activeStationEndpoints: StationAPIEndpoints
        get() = stationEndpointsByStation[selectedStation] ?: selectedStation.defaultAPIEndpoints

    val filteredHistoryEntries: List<SongHistoryEntry>
        get() {
            val trimmed = historySearchQuery.trim()
            if (trimmed.isEmpty()) return historyEntries
            return historyEntries.filter { it.matches(trimmed) }
        }

    val displayedHistoryEntries: List<SongHistoryEntry>
        get() {
            val filtered = filteredHistoryEntries
            val trimmed = historySearchQuery.trim()
            if (trimmed.isNotEmpty()) return filtered
            return filtered.take(historyDisplayLimit)
        }

    val hasMoreHistoryEntries: Boolean
        get() {
            val trimmed = historySearchQuery.trim()
            if (trimmed.isNotEmpty()) return false
            return displayedHistoryEntries.size < filteredHistoryEntries.size
        }

    val isVisualizerVisible: Boolean
        get() = visualizerMode != VisualizerMode.OFF

    val isHistoryButtonHighlighted: Boolean
        get() {
            val hasSearchMatches = historySearchQuery.trim().isNotEmpty() && filteredHistoryEntries.isNotEmpty()
            return isHistoryExpanded || hasSearchMatches
        }

    val isSearchBarVisible: Boolean
        get() = isHistoryButtonHighlighted

    val toolbarStatusLabel: String
        get() = when (radioPlayer.status) {
            PlaybackStatus.PLAYING -> selectedStation.badgeLabel
            PlaybackStatus.IDLE -> "READY"
            PlaybackStatus.PAUSED -> "PAUSED"
            PlaybackStatus.CONNECTING -> "LOADING"
            PlaybackStatus.ERROR -> "ERROR"
        }

    val isToolbarStatusShowingStationBadge: Boolean
        get() = radioPlayer.status == PlaybackStatus.PLAYING

    val canOpenCurrentSongOnAppleMusic: Boolean
        get() = radioPlayer.isPlaying && currentSong.appleMusicURL != null

    val canCopyCurrentSongToClipboard: Boolean
        get() = radioPlayer.isPlaying && currentSong.isMeaningfulTrack

    fun openSettings() {
        isSettingsVisible = true
    }

    fun closeSettings() {
        isSettingsVisible = false
    }

    fun toggleSettings() {
        isSettingsVisible = !isSettingsVisible
    }

    /**
     * Handles tapping the status badge in the bottom app controls bar:
     * - When playing (`TRAX` or `DANZ`), switches between Chilltrax and Radio Danz.
     * - For any other status (`READY`, `PAUSED`, `LOADING`, `ERROR`), opens Settings.
     */
    fun handleToolbarStatusTap(
        autoPlayIfCurrentlyPlaying: Boolean = true,
        openSettingsAction: () -> Unit = { openSettings() }
    ): Boolean {
        return if (isToolbarStatusShowingStationBadge) {
            switchStation(autoPlayIfCurrentlyPlaying = autoPlayIfCurrentlyPlaying)
            true
        } else {
            openSettingsAction()
            false
        }
    }

    fun togglePlayPause() {
        if (radioPlayer.isPlaying) {
            radioPlayer.stop()
        } else {
            playSelectedStation()
        }
    }

    fun playSelectedStation() {
        val streamURLString = selectedStream.urlString
        scope.launch {
            val playableURL = client.resolvePlayableStreamURL(streamURLString)
            radioPlayer.play(playableURL)
            refreshNowPlaying()
        }
    }

    fun selectVisualizerMode(mode: VisualizerMode) {
        visualizerMode = mode
        if (mode != VisualizerMode.OFF) {
            lastActiveVisualizerMode = mode
            preferences.putString(KEY_LAST_ACTIVE_VISUALIZER_MODE, mode.rawValue)
        }
        preferences.putString(KEY_VISUALIZER_MODE, mode.rawValue)
    }

    fun toggleVisualizerMode() {
        val next = visualizerMode.toggledMode()
        selectVisualizerMode(next)
    }

    fun toggleVisualizerVisibility() {
        if (visualizerMode == VisualizerMode.OFF) {
            val restored = if (lastActiveVisualizerMode == VisualizerMode.OFF) {
                VisualizerMode.SPECTRUM
            } else {
                lastActiveVisualizerMode
            }
            selectVisualizerMode(restored)
        } else {
            lastActiveVisualizerMode = visualizerMode
            preferences.putString(KEY_LAST_ACTIVE_VISUALIZER_MODE, lastActiveVisualizerMode.rawValue)
            visualizerMode = VisualizerMode.OFF
            preferences.putString(KEY_VISUALIZER_MODE, VisualizerMode.OFF.rawValue)
        }
    }

    fun toggleHistoryExpanded() {
        isHistoryExpanded = !isHistoryExpanded
        manualHistoryExpanded = isHistoryExpanded
    }

    fun toggleAlbumCovers() {
        fetchAlbumCoverFromChilltrax = !fetchAlbumCoverFromChilltrax
    }

    fun loadMoreHistoryIfNeeded(currentEntry: SongHistoryEntry? = null) {
        if (!hasMoreHistoryEntries) return
        if (currentEntry != null) {
            val displayed = displayedHistoryEntries
            val thresholdIndex = max(0, displayed.size - 8)
            val index = displayed.indexOfFirst { it.id == currentEntry.id }
            if (index < 0 || index < thresholdIndex) return
        }
        val nextLimit = min(filteredHistoryEntries.size, historyDisplayLimit + HISTORY_PAGE_SIZE)
        if (nextLimit > historyDisplayLimit) {
            val prevLimit = historyDisplayLimit
            historyDisplayLimit = nextLimit
            if (fetchAlbumCoverFromChilltrax) {
                val newlyVisible = filteredHistoryEntries.subList(prevLimit, nextLimit)
                for (entry in newlyVisible) {
                    val url = entry.coverURL
                    if (url != null) {
                        scope.launch {
                            coverCache.cacheCover(urlString = url)
                        }
                    }
                }
            }
        }
    }

    fun historyBadgeLabel(entry: SongHistoryEntry): String {
        return if (copiedHistoryEntryID == entry.id) {
            "COPY"
        } else {
            entry.stationBadgeLabel
        }
    }

    fun copyHistoryEntryToClipboard(
        entry: SongHistoryEntry,
        clipboardWriter: ((String) -> Unit)? = null
    ): String {
        val text = entry.clipboardText
        clipboardWriter?.invoke(text)
        copiedHistoryEntryID = entry.id
        return text
    }

    fun copyCurrentSongToClipboard(
        clipboardWriter: ((String) -> Unit)? = null
    ): String? {
        if (!canCopyCurrentSongToClipboard) return null
        val text = currentSong.clipboardText
        clipboardWriter?.invoke(text)
        val matchingEntry = historyEntries.firstOrNull { it.dedupKey == currentSong.dedupKey }
        copiedHistoryEntryID = matchingEntry?.id
        return text
    }

    fun openCurrentSongOnAppleMusic(openURL: ((String) -> Unit)? = null): String? {
        if (!canOpenCurrentSongOnAppleMusic) return null
        val url = currentSong.appleMusicURL ?: return null
        openURL?.invoke(url)
        return url
    }

    fun selectStation(station: RadioStation, autoPlayIfCurrentlyPlaying: Boolean = true) {
        val wasPlaying = radioPlayer.isPlaying
        val stationChanged = selectedStation != station
        selectedStation = station
        preferences.putString(KEY_SELECTED_STATION, station.rawValue)

        if (selectedStream.station != station) {
            val preferred = StationStream.preferredDefaultStream(
                station = station,
                streams = availableStreams
            )
            selectedStream = preferred
            preferences.putString(KEY_SELECTED_STREAM_ID, preferred.id)
        }

        if (stationChanged) {
            resetPendingHistoryTracking()
            currentSong = SongMetadata.placeholder
        }

        if (wasPlaying && autoPlayIfCurrentlyPlaying) {
            playSelectedStation()
        } else {
            scope.launch {
                refreshNowPlaying()
            }
        }
    }

    fun switchStation(autoPlayIfCurrentlyPlaying: Boolean = true) {
        selectStation(
            station = selectedStation.toggledStation(),
            autoPlayIfCurrentlyPlaying = autoPlayIfCurrentlyPlaying
        )
    }

    fun selectStream(stream: StationStream, autoPlayIfCurrentlyPlaying: Boolean = true) {
        val wasPlaying = radioPlayer.isPlaying
        val stationChanged = selectedStation != stream.station
        selectedStation = stream.station
        preferences.putString(KEY_SELECTED_STATION, stream.station.rawValue)
        selectedStream = stream
        preferences.putString(KEY_SELECTED_STREAM_ID, stream.id)

        if (stationChanged) {
            resetPendingHistoryTracking()
            currentSong = SongMetadata.placeholder
        }

        if (wasPlaying && autoPlayIfCurrentlyPlaying) {
            playSelectedStation()
        } else if (stationChanged) {
            scope.launch {
                refreshNowPlaying()
            }
        }
    }

    fun addAndSelectCustomStream(
        name: String,
        urlString: String,
        formatDescription: String = "Custom Stream",
        station: RadioStation? = null
    ): Boolean {
        val cleanURL = urlString.trim()
        val parsed = runCatching { URI(cleanURL) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || parsed.host.isNullOrBlank()) {
            return false
        }
        val targetStation = station ?: selectedStation
        val cleanName = name.trim().ifEmpty { parsed.host ?: "Custom Station" }
        val custom = StationStream(
            name = cleanName,
            urlString = cleanURL,
            formatDescription = formatDescription,
            isBuiltIn = false,
            station = targetStation
        )
        availableStreams = availableStreams + custom
        persistStreams()
        selectStream(custom)
        return true
    }

    suspend fun discoverBroadcasts(silent: Boolean = false) {
        isDiscoveringBroadcasts = true
        if (!silent) {
            discoveryStatusMessage = "Scanning chilltrax.com and radiodanz.com for broadcasts…"
        }

        val discoveries = client.discoverAllBroadcasts()
        val merged = availableStreams.toMutableList()
        var newlyAdded = 0

        for (station in RadioStation.entries) {
            val result = discoveries[station] ?: continue
            stationEndpointsByStation[station] = result.endpoints
            for (stream in result.streams) {
                if (merged.none { it.urlString == stream.urlString }) {
                    merged.add(stream)
                    newlyAdded += 1
                }
            }
        }

        availableStreams = merged
        persistStreams()
        isDiscoveringBroadcasts = false

        if (!silent) {
            val stationCount = streamsForSelectedStation.size
            discoveryStatusMessage = if (newlyAdded > 0) {
                "Added $newlyAdded new stream(s) ($stationCount available for ${selectedStation.displayName})."
            } else {
                "Verified $stationCount stream(s) for ${selectedStation.displayName} (${merged.size} total)."
            }
        }
    }

    suspend fun refreshNowPlaying() {
        val activeStation = selectedStation
        val endpoints = activeStationEndpoints
        val includeCovers = fetchAlbumCoverFromChilltrax
        val metadata = runCatching {
            client.fetchNowPlaying(
                station = activeStation,
                endpoints = endpoints,
                fetchAlbumCover = includeCovers
            )
        }.getOrNull()
        if (metadata != null && activeStation == selectedStation) {
            handleIncomingSongMetadata(metadata, sourceIsICY = false)
        }
    }

    fun handleIncomingSongMetadata(song: SongMetadata, sourceIsICY: Boolean) {
        if (!song.isMeaningfulTrack) return

        val effectiveSong = if (fetchAlbumCoverFromChilltrax) {
            song
        } else {
            val preservedCoverURL = if (song.dedupKey == currentSong.dedupKey) {
                currentSong.coverURLString
            } else {
                historyEntries.firstOrNull { it.dedupKey == song.dedupKey }?.coverURLString
            }
            SongMetadata(
                artist = song.artist,
                title = song.title,
                albumOrMix = song.albumOrMix,
                label = song.label,
                year = song.year,
                coverURLString = preservedCoverURL,
                buyURLString = song.buyURLString,
                songID = song.songID,
                baseURLString = selectedStation.homepageURLString
            )
        }

        val previousDedupKey = currentSong.dedupKey
        val isNewTrack = effectiveSong.dedupKey != previousDedupKey

        if (sourceIsICY && !isNewTrack && currentSong.coverURL != null) {
            return
        }

        val mergedSong = if (sourceIsICY && !isNewTrack) {
            SongMetadata(
                artist = effectiveSong.artist.ifEmpty { currentSong.artist },
                title = effectiveSong.title.ifEmpty { currentSong.title },
                albumOrMix = currentSong.albumOrMix,
                label = currentSong.label,
                year = currentSong.year,
                coverURLString = currentSong.coverURLString,
                buyURLString = currentSong.buyURLString,
                songID = currentSong.songID,
                baseURLString = selectedStation.homepageURLString
            )
        } else {
            effectiveSong
        }

        currentSong = mergedSong
        mergedSong.coverURL?.let { coverURL ->
            scope.launch {
                coverCache.cacheCover(urlString = coverURL)
            }
        }
        trackSongForHistory(mergedSong)

        if (sourceIsICY && mergedSong.coverURL == null && fetchAlbumCoverFromChilltrax) {
            val activeStation = selectedStation
            val endpoints = activeStationEndpoints
            scope.launch {
                val coverURLString = client.fetchCoverImage(
                    station = activeStation,
                    endpoints = endpoints,
                    artist = mergedSong.artist,
                    title = mergedSong.title
                )
                if (coverURLString != null && activeStation == selectedStation && currentSong.dedupKey == mergedSong.dedupKey) {
                    val enriched = SongMetadata(
                        artist = currentSong.artist,
                        title = currentSong.title,
                        albumOrMix = currentSong.albumOrMix,
                        label = currentSong.label,
                        year = currentSong.year,
                        coverURLString = coverURLString,
                        buyURLString = currentSong.buyURLString,
                        songID = currentSong.songID,
                        baseURLString = activeStation.homepageURLString
                    )
                    currentSong = enriched
                    enriched.coverURL?.let { url ->
                        scope.launch {
                            coverCache.cacheCover(urlString = url)
                        }
                    }
                    trackSongForHistory(enriched)
                }
            }
        }
    }

    private fun trackSongForHistory(song: SongMetadata) {
        if (!song.isMeaningfulTrack) return

        val mostRecent = historyEntries.firstOrNull()
        if (mostRecent != null && mostRecent.dedupKey == song.dedupKey) {
            if (mostRecent.coverURLString == null && song.coverURL != null) {
                historyEntries = historyStore.recordSong(
                    song = song,
                    station = selectedStream,
                    existingEntries = historyEntries
                )
                song.coverURL?.let { url ->
                    scope.launch {
                        coverCache.cacheCover(urlString = url)
                    }
                }
            }
            return
        }

        if (pendingHistorySong?.dedupKey == song.dedupKey) {
            pendingHistorySong = song
            pendingHistoryStream = selectedStream
            if (radioPlayer.isPlaying && pendingHistoryCommitJob == null) {
                schedulePendingHistoryCommit()
            }
            return
        }

        pendingHistoryCommitJob?.cancel()
        pendingHistoryCommitJob = null
        pendingHistorySong = song
        pendingHistoryStream = selectedStream
        pendingHistoryAccumulatedSeconds = 0.0
        pendingHistoryPlaybackStartedAtMillis = if (radioPlayer.isPlaying) System.currentTimeMillis() else null

        if (radioPlayer.isPlaying) {
            schedulePendingHistoryCommit()
        }
    }

    private fun handlePlaybackStateChanged(isPlaying: Boolean) {
        if (pendingHistorySong == null) return

        if (isPlaying) {
            if (pendingHistoryPlaybackStartedAtMillis == null) {
                pendingHistoryPlaybackStartedAtMillis = System.currentTimeMillis()
            }
            schedulePendingHistoryCommit()
        } else {
            val startedAt = pendingHistoryPlaybackStartedAtMillis
            if (startedAt != null) {
                pendingHistoryAccumulatedSeconds += max(0.0, (System.currentTimeMillis() - startedAt) / 1000.0)
                pendingHistoryPlaybackStartedAtMillis = null
            }
            pendingHistoryCommitJob?.cancel()
            pendingHistoryCommitJob = null
        }
    }

    private fun schedulePendingHistoryCommit() {
        val song = pendingHistorySong ?: return
        if (!song.isMeaningfulTrack || !radioPlayer.isPlaying) return

        val now = System.currentTimeMillis()
        val activeRun = pendingHistoryPlaybackStartedAtMillis?.let { max(0.0, (now - it) / 1000.0) } ?: 0.0
        val totalPlayed = pendingHistoryAccumulatedSeconds + activeRun
        val remaining = minimumHistoryPlayDuration - totalPlayed

        if (remaining <= 0) {
            commitPendingHistorySong()
            return
        }

        val expectedDedupKey = song.dedupKey
        val delayMillis = max(1L, (remaining * 1000.0).toLong())
        pendingHistoryCommitJob?.cancel()
        pendingHistoryCommitJob = scope.launch {
            delay(delayMillis)
            if (!isActive) return@launch
            if (radioPlayer.isPlaying && pendingHistorySong?.dedupKey == expectedDedupKey) {
                commitPendingHistorySong()
            }
        }
    }

    private fun commitPendingHistorySong() {
        val song = pendingHistorySong ?: return
        if (!song.isMeaningfulTrack) return
        val stream = pendingHistoryStream ?: selectedStream

        pendingHistoryCommitJob?.cancel()
        pendingHistoryCommitJob = null
        pendingHistorySong = null
        pendingHistoryStream = null
        pendingHistoryAccumulatedSeconds = 0.0
        pendingHistoryPlaybackStartedAtMillis = null

        historyEntries = historyStore.recordSong(
            song = song,
            station = stream,
            existingEntries = historyEntries
        )
        historyCommitCount += 1
        song.coverURL?.let { url ->
            scope.launch {
                coverCache.cacheCover(urlString = url)
            }
        }
    }

    private fun resetPendingHistoryTracking() {
        pendingHistoryCommitJob?.cancel()
        pendingHistoryCommitJob = null
        pendingHistorySong = null
        pendingHistoryStream = null
        pendingHistoryAccumulatedSeconds = 0.0
        pendingHistoryPlaybackStartedAtMillis = null
    }

    private suspend fun fetchMissingAlbumCovers() {
        if (!fetchAlbumCoverFromChilltrax) return

        val lastTenCoverMap = mutableMapOf<String, String>()
        for (station in RadioStation.entries) {
            val endpoints = stationEndpointsByStation[station] ?: station.defaultAPIEndpoints
            val recentTracks = runCatching {
                client.fetchLastTen(station = station, endpoints = endpoints, includeCovers = true)
            }.getOrDefault(emptyList())
            for (track in recentTracks) {
                val url = track.coverURL
                if (url != null) {
                    lastTenCoverMap[track.dedupKey] = url
                }
            }
        }

        if (currentSong.isMeaningfulTrack && currentSong.coverURL == null) {
            val activeStation = selectedStation
            val endpoints = activeStationEndpoints
            val resolvedCoverURL = lastTenCoverMap[currentSong.dedupKey]
                ?: client.fetchCoverImage(
                    station = activeStation,
                    endpoints = endpoints,
                    artist = currentSong.artist,
                    title = currentSong.title
                )
            if (resolvedCoverURL != null && fetchAlbumCoverFromChilltrax) {
                val updatedSong = SongMetadata(
                    artist = currentSong.artist,
                    title = currentSong.title,
                    albumOrMix = currentSong.albumOrMix,
                    label = currentSong.label,
                    year = currentSong.year,
                    coverURLString = resolvedCoverURL,
                    buyURLString = currentSong.buyURLString,
                    songID = currentSong.songID,
                    baseURLString = activeStation.homepageURLString
                )
                currentSong = updatedSong
                updatedSong.coverURL?.let { lastTenCoverMap[updatedSong.dedupKey] = it }
            }
        }

        var updatedEntries = historyEntries.toMutableList()
        var didModifyHistory = false
        for (idx in updatedEntries.indices) {
            val entry = updatedEntries[idx]
            if (entry.coverURL == null) {
                val matchedCover = lastTenCoverMap[entry.dedupKey] ?: run {
                    if (idx < 10) {
                        val entryStation = if (entry.stationBadgeLabel == RadioStation.RADIO_DANZ.badgeLabel) {
                            RadioStation.RADIO_DANZ
                        } else {
                            RadioStation.CHILLTRAX
                        }
                        client.fetchCoverImage(
                            station = entryStation,
                            endpoints = stationEndpointsByStation[entryStation] ?: entryStation.defaultAPIEndpoints,
                            artist = entry.artist,
                            title = entry.title
                        )
                    } else {
                        null
                    }
                }
                if (matchedCover != null) {
                    updatedEntries[idx] = entry.copy(coverURLString = matchedCover)
                    lastTenCoverMap[entry.dedupKey] = matchedCover
                    didModifyHistory = true
                }
            }
        }

        if (didModifyHistory && fetchAlbumCoverFromChilltrax) {
            historyEntries = updatedEntries
            historyStore.save(updatedEntries)
        }

        if (!fetchAlbumCoverFromChilltrax) return
        currentSong.coverURL?.let { coverCache.cacheCover(urlString = it) }
        for (entry in historyEntries.take(max(HISTORY_PAGE_SIZE, historyDisplayLimit))) {
            if (!fetchAlbumCoverFromChilltrax) break
            entry.coverURL?.let { coverCache.cacheCover(urlString = it) }
        }
    }

    fun clearHistory() {
        historyStore.clear()
        coverCache.clearAll()
        historyEntries = emptyList()
        historyDisplayLimit = HISTORY_PAGE_SIZE
        copiedHistoryEntryID = null
        if (!manualHistoryExpanded) {
            isHistoryExpanded = false
        }
    }

    private fun startMetadataPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            refreshNowPlaying()
            while (isActive) {
                delay(15_000L)
                if (!isActive) break
                if (radioPlayer.isPlaying) {
                    refreshNowPlaying()
                }
            }
        }
    }

    private fun persistStreams() {
        runCatching {
            preferences.putString(KEY_CUSTOM_STREAMS, json.encodeToString(availableStreams))
        }
    }

    companion object {
        const val HISTORY_PAGE_SIZE = 50
        const val DEFAULT_HISTORY_COMMIT_DELAY_SECONDS = 10
        val HISTORY_COMMIT_DELAY_RANGE = 0..99

        const val KEY_SELECTED_STATION = "chilltrax.selectedStation"
        const val KEY_SELECTED_STREAM_ID = "chilltrax.selectedStreamID"
        const val KEY_CUSTOM_STREAMS = "chilltrax.customStreams"
        const val KEY_VISUALIZER_MODE = "chilltrax.visualizerMode"
        const val KEY_LAST_ACTIVE_VISUALIZER_MODE = "chilltrax.lastActiveVisualizerMode"
        const val KEY_FETCH_ALBUM_COVERS = "chilltrax.fetchAlbumCovers"
        const val KEY_HISTORY_COMMIT_DELAY_SECONDS = "chilltrax.historyCommitDelaySeconds"

        fun clampHistoryCommitDelaySeconds(value: Int): Int =
            value.coerceIn(HISTORY_COMMIT_DELAY_RANGE.first, HISTORY_COMMIT_DELAY_RANGE.last)
    }
}
