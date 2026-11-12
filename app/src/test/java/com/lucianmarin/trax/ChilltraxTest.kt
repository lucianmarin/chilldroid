package com.lucianmarin.trax

import com.lucianmarin.trax.audio.PlaybackStatus
import com.lucianmarin.trax.audio.RadioPlayer
import com.lucianmarin.trax.model.ChilltraxCoverResponse
import com.lucianmarin.trax.model.ChilltraxLastTenItem
import com.lucianmarin.trax.model.ChilltraxNowPlayingResponse
import com.lucianmarin.trax.model.RadioStation
import com.lucianmarin.trax.model.SongHistoryEntry
import com.lucianmarin.trax.model.SongMetadata
import com.lucianmarin.trax.model.StationStream
import com.lucianmarin.trax.model.VisualizerMode
import com.lucianmarin.trax.network.ChilltraxClient
import com.lucianmarin.trax.state.AppState
import com.lucianmarin.trax.state.InMemoryPreferencesStore
import com.lucianmarin.trax.storage.AlbumCoverCache
import com.lucianmarin.trax.storage.CompressedHistoryStore
import com.lucianmarin.trax.ui.components.VisualizerMetrics
import com.lucianmarin.trax.ui.theme.ChilltraxIcons
import com.lucianmarin.trax.ui.theme.ChilltraxTheme
import com.lucianmarin.trax.ui.theme.PanelSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class ChilltraxTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodeChilltraxNowPlayingAndCoverResponses() {
        val nowPlayingJSON = """
            {"title":"Teardrop","artist":"Massive Attack","mix":"Mezzanine","label":"Virgin","year":"1998"}
        """.trimIndent()
        val coverJSON = """
            {"cover":"https://www.chilltrax.com/covers/mezzanine.jpg"}
        """.trimIndent()

        val nowPlaying = json.decodeFromString<ChilltraxNowPlayingResponse>(nowPlayingJSON)
        val cover = json.decodeFromString<ChilltraxCoverResponse>(coverJSON)
        val metadata = nowPlaying.toSongMetadata(coverURLString = cover.cover)

        assertEquals("Teardrop", metadata.title)
        assertEquals("Massive Attack", metadata.artist)
        assertEquals("Mezzanine", metadata.albumOrMix)
        assertEquals("https://www.chilltrax.com/covers/mezzanine.jpg", metadata.coverURL)
        assertTrue(metadata.isMeaningfulTrack)
    }

    @Test
    fun decodeLastTenTracks() {
        val raw = """
            [
                {"title":"Porcelain","artist":"Moby","mix":"Play","year":"1999","picture":"https://is1-ssl.mzstatic.com/image/thumb/Music124/cover.jpg"},
                {"title":"Lebanese Blonde","artist":"Thievery Corporation","mix":"The Mirror Conspiracy","year":"2000","picture":""}
            ]
        """.trimIndent()
        val items = json.decodeFromString<List<ChilltraxLastTenItem>>(raw)
        assertEquals(2, items.size)
        assertEquals("Porcelain", items[0].title)
        assertEquals("Moby", items[0].artist)
        assertEquals("Play", items[0].mix)
    }

    @Test
    fun parseBroadcastStreamsFromStationHTML() {
        val sampleHTML = """
            <html>
            <body>
                <a href="http://streams.electronicmusicradiogroup.org:9050">128Kbps AAC+</a>
                <script>
                    var config = { streamurl: "https://www.chilltrax.com/listen/?mode=redirect" };
                </script>
            </body>
            </html>
        """.trimIndent()

        val streams = ChilltraxClient.parseBroadcastStreams(
            fromHTML = sampleHTML,
            station = RadioStation.CHILLTRAX
        )

        assertTrue(streams.isNotEmpty())
        assertTrue(streams.any { it.urlString.contains("electronicmusicradiogroup.org:9050") })
        assertTrue(streams.any { it.urlString.contains("chilltrax.com/listen") })
    }

    @Test
    fun defaultStreamPrefersRedirectURLAndFallsBackToSecondBestOption() {
        val chilltraxDefault = StationStream.defaultStream(station = RadioStation.CHILLTRAX)
        assertTrue(chilltraxDefault.urlString.contains("redirect", ignoreCase = true))
        assertEquals("https://www.chilltrax.com/listen/?mode=redirect", chilltraxDefault.urlString)

        val radioDanzDefault = StationStream.defaultStream(station = RadioStation.RADIO_DANZ)
        assertTrue(radioDanzDefault.urlString.contains("redirect", ignoreCase = true))
        assertEquals("https://www.radiodanz.com/results/?mode=redirect", radioDanzDefault.urlString)

        val chilltraxWithoutRedirect = StationStream.defaultChilltraxStreams.filter {
            !it.urlString.contains("redirect", ignoreCase = true)
        }
        val chilltraxFallback = StationStream.preferredDefaultStream(
            station = RadioStation.CHILLTRAX,
            streams = chilltraxWithoutRedirect
        )
        assertEquals("https://streamssleu.chilltrax.com/stream?sid=1", chilltraxFallback.urlString)

        val radioDanzWithoutRedirect = StationStream.defaultRadioDanzStreams.filter {
            !it.urlString.contains("redirect", ignoreCase = true)
        }
        val radioDanzFallback = StationStream.preferredDefaultStream(
            station = RadioStation.RADIO_DANZ,
            streams = radioDanzWithoutRedirect
        )
        assertEquals("https://streamssl.radiodanz.com/live", radioDanzFallback.urlString)
    }

    @Test
    fun parseAPIEndpointsFromStationHTML() {
        val sampleHTML = """
            <script>
                fetch("https://www.chilltrax.com/php/GetSongInformation.php");
                fetch("/php/GetCoverImage.php");
                fetch("https://www.chilltrax.com/php/GetLastTen.php");
            </script>
        """.trimIndent()

        val endpoints = ChilltraxClient.parseAPIEndpoints(
            fromHTML = sampleHTML,
            station = RadioStation.CHILLTRAX
        )
        assertEquals("https://www.chilltrax.com/php/GetSongInformation.php", endpoints.songInfoURLString)
        assertEquals("https://www.chilltrax.com/php/GetCoverImage.php", endpoints.coverImageURLString)
        assertEquals("https://www.chilltrax.com/php/GetLastTen.php", endpoints.lastTenURLString)
    }

    @Test
    fun parsePLSPlaylistContent() {
        val plsText = """
            [playlist]
            NumberOfEntries=2
            File1=https://server1.radiodanz.com:8000/stream1
            Title1=Radio Danz
            Length1=-1
        """.trimIndent()

        val extracted = ChilltraxClient.parsePLSStreamURL(plsText)
        assertEquals("https://server1.radiodanz.com:8000/stream1", extracted)
    }

    @Test
    fun compressedHistoryStoreRoundTripAndDeduplication() {
        val tempDir = Files.createTempDirectory("ChilldroidHistoryTests-${UUID.randomUUID()}").toFile()
        try {
            val store = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz"))
            val chilltraxStream = StationStream.defaultStream(station = RadioStation.CHILLTRAX)
            val danzStream = StationStream.defaultStream(station = RadioStation.RADIO_DANZ)

            val song1 = SongMetadata(
                artist = "Petit Biscuit",
                title = "Sunset Lover",
                albumOrMix = "Presence",
                coverURLString = "https://example.com/sunset.jpg"
            )
            val song2 = SongMetadata(
                artist = "Daft Punk",
                title = "One More Time",
                albumOrMix = "Discovery",
                coverURLString = null
            )

            val afterFirst = store.recordSong(
                song = song1,
                station = chilltraxStream,
                playedAtEpochSeconds = 1_700_000_000L,
                existingEntries = emptyList()
            )
            assertEquals(1, afterFirst.size)

            // Duplicate consecutive track should be ignored
            val afterDuplicate = store.recordSong(
                song = song1,
                station = chilltraxStream,
                playedAtEpochSeconds = 1_700_000_010L,
                existingEntries = afterFirst
            )
            assertEquals(1, afterDuplicate.size)

            val afterSecond = store.recordSong(
                song = song2,
                station = danzStream,
                playedAtEpochSeconds = 1_700_000_300L,
                existingEntries = afterDuplicate
            )
            assertEquals(2, afterSecond.size)
            assertEquals("One More Time", afterSecond.first().title)

            val loaded = store.load()
            assertEquals(2, loaded.size)
            assertEquals("One More Time", loaded[0].title)
            assertEquals("Sunset Lover", loaded[1].title)
            assertEquals("https://example.com/sunset.jpg", loaded[1].coverURL)
            assertTrue(store.fileSizeBytes > 0)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun albumCoverCacheStoresAndReadsData() {
        val tempDir = Files.createTempDirectory("ChilldroidCoverCacheTests-${UUID.randomUUID()}").toFile()
        try {
            val cache = AlbumCoverCache(directory = tempDir)
            val sampleURL = "https://example.com/cover123.jpg"
            val sampleBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

            assertFalse(cache.isCachedOnDisk(sampleURL))
            cache.storeBytes(sampleURL, sampleBytes)
            assertTrue(cache.isCachedOnDisk(sampleURL))
            assertEquals(sampleBytes.toList(), cache.cachedBytes(sampleURL)?.toList())
            assertTrue(cache.cachedFilesSizeBytes >= 4L)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun visualizerSpectrumGeneratorProducesValidLevels() {
        val silence = List(512) { 0f }
        val idle = RadioPlayer.computeSpectrumLevels(fromPCM = silence)
        assertEquals(RadioPlayer.VISUALIZER_BAR_COUNT, idle.size)
        assertTrue(idle.all { it in 0.03f..1.0f })

        val sinePCM = List(512) { i ->
            kotlin.math.sin(i * 0.25f) * 0.65f
        }
        val active = RadioPlayer.computeSpectrumLevels(fromPCM = sinePCM)
        assertEquals(RadioPlayer.VISUALIZER_BAR_COUNT, active.size)
        assertTrue((active.maxOrNull() ?: 0f) > (idle.maxOrNull() ?: 0f))
    }

    @Test
    fun visualizerHeightFitsAbove35PercentAndUnder100PercentDuringPlayback() {
        val player = RadioPlayer(enableNativeMediaPlayer = false)
        player.setSimulatedPlaybackStateForTesting(isPlaying = true, status = PlaybackStatus.PLAYING)
        for (i in 0 until 24) {
            player.tickVisualizer()
        }

        val canvasWidth = ChilltraxTheme.COMPACT_WIDTH
        val canvasHeight = ChilltraxTheme.VISUALIZER_SECTION_HEIGHT

        val avgSpectrumHeight = VisualizerMetrics.spectrumAverageHeight(
            levels = player.visualizerLevels,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        )
        val avgSpectrumRatio = avgSpectrumHeight / canvasHeight

        val maxBarHeight = VisualizerMetrics.spectrumBarHeights(
            levels = player.visualizerLevels,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight
        ).maxOrNull() ?: 0f

        assertTrue(
            "Expected average spectrum height ratio > 0.35, got $avgSpectrumRatio",
            avgSpectrumRatio > 0.35f
        )
        assertTrue(
            "Expected max spectrum bar height < canvasHeight ($canvasHeight), got $maxBarHeight",
            maxBarHeight < canvasHeight
        )

        val waveformSpan = VisualizerMetrics.waveformPeakVerticalSpan(
            levels = player.visualizerLevels,
            phase = player.waveformPhase,
            canvasHeight = canvasHeight
        )
        val waveformRatio = waveformSpan / canvasHeight
        assertTrue(
            "Expected waveform peak vertical span ratio > 0.35, got $waveformRatio",
            waveformRatio > 0.35f
        )
        assertTrue(
            "Expected waveform peak vertical span < canvasHeight ($canvasHeight), got $waveformSpan",
            waveformSpan < canvasHeight
        )
    }

    @Test
    fun spectrumTrailFollowsAndDecaysAboveCurrentBars() {
        val player = RadioPlayer(enableNativeMediaPlayer = false)
        player.setSimulatedPlaybackStateForTesting(isPlaying = true, status = PlaybackStatus.PLAYING)
        for (i in 0 until 20) {
            player.tickVisualizer()
        }
        assertEquals(RadioPlayer.VISUALIZER_BAR_COUNT, player.visualizerTrailLevels.size)

        for (i in 0 until RadioPlayer.VISUALIZER_BAR_COUNT) {
            assertTrue(player.visualizerTrailLevels[i] + 0.0001f >= player.visualizerLevels[i])
        }

        player.setSimulatedPlaybackStateForTesting(isPlaying = false, status = PlaybackStatus.PAUSED)
        player.tickVisualizer()
        val hasTrailingPeakAboveBar = player.visualizerTrailLevels.zip(player.visualizerLevels).any { (trail, current) ->
            trail > current + 0.01f
        }
        assertTrue("Expected spectrum trail levels to remain visible above decaying bars", hasTrailingPeakAboveBar)
    }

    @Test
    fun invertedPanelOrderAndGoogleMaterialSymbolsRequirements() {
        // Verify bottom-to-top panel order matches specification:
        // 1. App Controls at the bottom
        // 2. Current Song Info on top of controls
        // 3. Visualizer on top of song info
        // 4. Search Bar and History Results on top
        assertEquals(
            listOf(
                PanelSection.APP_CONTROLS,
                PanelSection.CURRENT_SONG_INFO,
                PanelSection.VISUALIZER,
                PanelSection.SEARCH_AND_HISTORY
            ),
            ChilltraxTheme.PanelOrderBottomToTop
        )

        // Verify segmented tabs order: No Info (OFF), Waveform, Spectrum
        assertEquals(
            listOf(
                VisualizerMode.OFF,
                VisualizerMode.WAVEFORM,
                VisualizerMode.SPECTRUM
            ),
            VisualizerMode.segmentedTabsOrder
        )
        assertEquals("Info", VisualizerMode.OFF.title)
        assertEquals("Waveform", VisualizerMode.WAVEFORM.title)
        assertEquals("Spectrum", VisualizerMode.SPECTRUM.title)

        // Verify Google Fonts Material Symbols icons mapping:
        // - Music Note 2 (music_note_2) replacing Apple logo
        // - Music Note (music_note) for history results
        // - Vital Signs (vital_signs) for waveform visualizer
        // - Bar Chart 4 Bars (bar_chart_4_bars) for spectrum visualizer
        // - Settings Slow Motion (settings_slow_motion) for settings
        assertEquals("music_note_2", ChilltraxIcons.MusicNote2.name)
        assertEquals("music_note", ChilltraxIcons.MusicNote.name)
        assertEquals("vital_signs", ChilltraxIcons.VitalSigns.name)
        assertEquals("bar_chart_4_bars", ChilltraxIcons.BarChart4Bars.name)
        assertEquals("settings_slow_motion", ChilltraxIcons.SettingsSlowMotion.name)
        assertEquals("vital_signs", VisualizerMode.WAVEFORM.googleFontIconName)
        assertEquals("bar_chart_4_bars", VisualizerMode.SPECTRUM.googleFontIconName)
    }

    @Test
    fun themeAndVisualizerColorPaletteRules() {
        assertEquals(ChilltraxTheme.VisualizerBlack, ChilltraxTheme.visualizerBackground(isDark = true))
        assertEquals(ChilltraxTheme.VisualizerWhite, ChilltraxTheme.visualizerBackground(isDark = false))
        assertEquals(ChilltraxTheme.VisualizerWhite, ChilltraxTheme.visualizerContrastColor(isDark = true))
        assertEquals(ChilltraxTheme.VisualizerBlack, ChilltraxTheme.visualizerContrastColor(isDark = false))

        assertEquals(ChilltraxTheme.AccentRed, VisualizerMetrics.spectrumBarColor(0.85f, isDark = true))
        assertEquals(ChilltraxTheme.VisualizerWhite, VisualizerMetrics.spectrumBarColor(0.50f, isDark = true))
        assertEquals(ChilltraxTheme.VisualizerBlack, VisualizerMetrics.spectrumBarColor(0.50f, isDark = false))
        assertEquals(ChilltraxTheme.VisualizerGray, VisualizerMetrics.spectrumBarColor(0.15f, isDark = true))

        assertEquals(ChilltraxTheme.LiveGreen, ChilltraxTheme.statusDotColor(PlaybackStatus.PLAYING))
        assertEquals(ChilltraxTheme.AccentRed, ChilltraxTheme.statusDotColor(PlaybackStatus.CONNECTING))
        assertEquals(ChilltraxTheme.VisualizerGray, ChilltraxTheme.statusDotColor(PlaybackStatus.IDLE))
        assertEquals(ChilltraxTheme.VisualizerGray, ChilltraxTheme.statusDotColor(PlaybackStatus.PAUSED))
        assertEquals(ChilltraxTheme.AccentRed, ChilltraxTheme.statusDotColor(PlaybackStatus.ERROR))
    }

    @Test
    fun toolbarStatusLabelAndStationBadgeSwitch() {
        val tempDir = Files.createTempDirectory("ChilldroidToolbarTests-${UUID.randomUUID()}").toFile()
        try {
            val player = RadioPlayer(enableNativeMediaPlayer = false)
            val state = AppState(
                client = ChilltraxClient(),
                historyStore = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz")),
                coverCache = AlbumCoverCache(directory = File(tempDir, "Covers")),
                radioPlayer = player,
                preferences = InMemoryPreferencesStore(),
                startPollingAutomatically = false
            )
            state.selectStation(RadioStation.CHILLTRAX, autoPlayIfCurrentlyPlaying = false)

            player.setSimulatedPlaybackStateForTesting(isPlaying = false, status = PlaybackStatus.IDLE)
            assertEquals("READY", state.toolbarStatusLabel)

            player.setSimulatedPlaybackStateForTesting(isPlaying = false, status = PlaybackStatus.CONNECTING)
            assertEquals("LOADING", state.toolbarStatusLabel)

            player.setSimulatedPlaybackStateForTesting(isPlaying = false, status = PlaybackStatus.ERROR)
            assertEquals("ERROR", state.toolbarStatusLabel)

            player.setSimulatedPlaybackStateForTesting(isPlaying = false, status = PlaybackStatus.PAUSED)
            assertEquals("PAUSED", state.toolbarStatusLabel)

            player.setSimulatedPlaybackStateForTesting(isPlaying = true, status = PlaybackStatus.PLAYING)
            assertEquals("TRAX", state.toolbarStatusLabel)

            var didOpenSettings = false
            state.handleToolbarStatusTap(autoPlayIfCurrentlyPlaying = false) {
                didOpenSettings = true
            }
            assertFalse(didOpenSettings)
            assertEquals(RadioStation.RADIO_DANZ, state.selectedStation)

            player.setSimulatedPlaybackStateForTesting(isPlaying = true, status = PlaybackStatus.PLAYING)
            assertEquals("DANZ", state.toolbarStatusLabel)

            player.setSimulatedPlaybackStateForTesting(isPlaying = false, status = PlaybackStatus.IDLE)
            state.handleToolbarStatusTap(autoPlayIfCurrentlyPlaying = false) {
                didOpenSettings = true
            }
            assertTrue(didOpenSettings)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun clearHistoryAndCoverCacheRemovesStoredData() {
        val tempDir = Files.createTempDirectory("ChilldroidClearCacheTests-${UUID.randomUUID()}").toFile()
        try {
            val store = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz"))
            val cache = AlbumCoverCache(directory = File(tempDir, "Covers"))

            val entry = SongHistoryEntry(
                playedAtEpochSeconds = 1_700_001_000L,
                artist = "The Cure",
                title = "Lullaby",
                albumOrMix = "Disintegration",
                stationName = "Chilltrax",
                coverURLString = "https://example.com/cure.jpg"
            )
            store.save(listOf(entry))
            cache.storeBytes("https://example.com/cure.jpg", byteArrayOf(0x01, 0x02, 0x03, 0x04))

            val state = AppState(
                client = ChilltraxClient(),
                historyStore = store,
                coverCache = cache,
                radioPlayer = RadioPlayer(enableNativeMediaPlayer = false),
                preferences = InMemoryPreferencesStore(),
                startPollingAutomatically = false
            )
            assertEquals(1, state.historyEntries.size)
            assertTrue(cache.cachedFilesSizeBytes > 0)
            assertTrue(store.fileSizeBytes > 0)

            state.clearHistory()

            assertTrue(state.historyEntries.isEmpty())
            assertEquals(0L, cache.cachedFilesSizeBytes)
            assertEquals(0L, store.fileSizeBytes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun minimumPlaySecondsClampingAndDelayedHistoryCommit() = runBlocking {
        assertEquals(0, AppState.clampHistoryCommitDelaySeconds(-5))
        assertEquals(10, AppState.clampHistoryCommitDelaySeconds(10))
        assertEquals(99, AppState.clampHistoryCommitDelaySeconds(140))

        val tempDir = Files.createTempDirectory("ChilldroidCommitDelayTests-${UUID.randomUUID()}").toFile()
        try {
            val player = RadioPlayer(enableNativeMediaPlayer = false)
            val state = AppState(
                client = ChilltraxClient(),
                historyStore = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz")),
                coverCache = AlbumCoverCache(directory = File(tempDir, "Covers")),
                radioPlayer = player,
                preferences = InMemoryPreferencesStore(),
                startPollingAutomatically = false
            )
            state.historyCommitDelaySeconds = 1
            player.setSimulatedPlaybackStateForTesting(isPlaying = true, status = PlaybackStatus.PLAYING)

            val firstSong = SongMetadata(
                artist = "Transient Artist",
                title = "Short Skip Song",
                albumOrMix = "Album 1"
            )
            val secondSong = SongMetadata(
                artist = "Persistent Artist",
                title = "Committed Song",
                albumOrMix = "Album 2"
            )

            state.handleIncomingSongMetadata(firstSong, sourceIsICY = false)
            assertTrue(state.historyEntries.isEmpty())

            // Switch before 1 second elapses
            state.handleIncomingSongMetadata(secondSong, sourceIsICY = false)
            assertTrue(state.historyEntries.isEmpty())

            delay(1_250L)
            assertEquals(1, state.historyEntries.size)
            assertEquals("Committed Song", state.historyEntries.first().title)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun immediateHistoryCommitWhenMinimumSecondsIsZero() {
        val tempDir = Files.createTempDirectory("ChilldroidImmediateCommitTests-${UUID.randomUUID()}").toFile()
        try {
            val player = RadioPlayer(enableNativeMediaPlayer = false)
            val state = AppState(
                client = ChilltraxClient(),
                historyStore = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz")),
                coverCache = AlbumCoverCache(directory = File(tempDir, "Covers")),
                radioPlayer = player,
                preferences = InMemoryPreferencesStore(),
                startPollingAutomatically = false
            )
            state.selectStation(RadioStation.RADIO_DANZ, autoPlayIfCurrentlyPlaying = false)
            state.historyCommitDelaySeconds = 0
            player.setSimulatedPlaybackStateForTesting(isPlaying = true, status = PlaybackStatus.PLAYING)

            val song = SongMetadata(
                artist = "Immediate DJ",
                title = "Instant Track",
                albumOrMix = "Club Mix"
            )
            state.handleIncomingSongMetadata(song, sourceIsICY = false)

            assertEquals(1, state.historyEntries.size)
            assertEquals(1, state.historyCommitCount)
            assertEquals("Instant Track", state.historyEntries.first().title)
            assertEquals("DANZ", state.historyEntries.first().stationBadgeLabel)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun historyPaginationLoadsFiftyAtATimeAndResetsOnSearch() {
        val tempDir = Files.createTempDirectory("ChilldroidPaginationTests-${UUID.randomUUID()}").toFile()
        try {
            val store = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz"))
            val entries = (0 until 120).map { i ->
                SongHistoryEntry(
                    playedAtEpochSeconds = 1_700_000_000L + i.toLong(),
                    artist = "Artist $i",
                    title = "Track $i",
                    albumOrMix = "Album $i",
                    stationName = if (i % 2 == 0) "Chilltrax" else "Radio Danz"
                )
            }
            store.save(entries)

            val state = AppState(
                client = ChilltraxClient(),
                historyStore = store,
                coverCache = AlbumCoverCache(directory = File(tempDir, "Covers")),
                radioPlayer = RadioPlayer(enableNativeMediaPlayer = false),
                preferences = InMemoryPreferencesStore(),
                startPollingAutomatically = false
            )

            assertEquals(120, state.filteredHistoryEntries.size)
            assertEquals(50, state.displayedHistoryEntries.size)

            val lastFirstPageEntry = state.displayedHistoryEntries.last()
            state.loadMoreHistoryIfNeeded(lastFirstPageEntry)
            assertEquals(100, state.displayedHistoryEntries.size)

            val lastSecondPageEntry = state.displayedHistoryEntries.last()
            state.loadMoreHistoryIfNeeded(lastSecondPageEntry)
            assertEquals(120, state.displayedHistoryEntries.size)

            state.historySearchQuery = "Track 1"
            assertEquals(AppState.HISTORY_PAGE_SIZE, state.historyDisplayLimit)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun historyAndCurrentSongActionsCopyAndBuildAppleMusicURLs() {
        val tempDir = Files.createTempDirectory("ChilldroidCopyTests-${UUID.randomUUID()}").toFile()
        try {
            val player = RadioPlayer(enableNativeMediaPlayer = false)
            val state = AppState(
                client = ChilltraxClient(),
                historyStore = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz")),
                coverCache = AlbumCoverCache(directory = File(tempDir, "Covers")),
                radioPlayer = player,
                preferences = InMemoryPreferencesStore(),
                startPollingAutomatically = false
            )

            val entry = SongHistoryEntry(
                playedAtEpochSeconds = 1_700_000_500L,
                artist = "M83",
                title = "Midnight City",
                albumOrMix = "Hurry Up, We're Dreaming",
                stationName = "Chilltrax"
            )
            assertEquals("M83 - Midnight City", entry.clipboardText)
            assertEquals("TRAX", entry.stationBadgeLabel)
            assertEquals(
                "https://music.apple.com/us/search?term=M83%20Midnight%20City",
                entry.appleMusicSearchURL
            )

            val copiedText = state.copyHistoryEntryToClipboard(entry)
            assertEquals("M83 - Midnight City", copiedText)
            assertEquals(entry.id, state.copiedHistoryEntryID)

            state.currentSong = SongMetadata(
                artist = "Todd Terje",
                title = "Inspector Norse",
                albumOrMix = "It's Album Time"
            )
            player.setSimulatedPlaybackStateForTesting(isPlaying = false, status = PlaybackStatus.PAUSED)
            assertNull(state.copyCurrentSongToClipboard())
            assertNull(state.openCurrentSongOnAppleMusic())

            player.setSimulatedPlaybackStateForTesting(isPlaying = true, status = PlaybackStatus.PLAYING)
            assertEquals("Todd Terje - Inspector Norse", state.copyCurrentSongToClipboard())
            assertEquals(
                "https://music.apple.com/us/search?term=Todd%20Terje%20Inspector%20Norse",
                state.openCurrentSongOnAppleMusic()
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun pausedMetadataDoesNotCommitUntilPlayingResumes() {
        val tempDir = Files.createTempDirectory("ChilldroidPausedTests-${UUID.randomUUID()}").toFile()
        try {
            val player = RadioPlayer(enableNativeMediaPlayer = false)
            val state = AppState(
                client = ChilltraxClient(),
                historyStore = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz")),
                coverCache = AlbumCoverCache(directory = File(tempDir, "Covers")),
                radioPlayer = player,
                preferences = InMemoryPreferencesStore(),
                startPollingAutomatically = false
            )
            state.historyCommitDelaySeconds = 0
            player.setSimulatedPlaybackStateForTesting(isPlaying = false, status = PlaybackStatus.PAUSED)

            val pausedSong = SongMetadata(
                artist = "Paused Artist",
                title = "Should Not Commit While Paused",
                albumOrMix = "Paused Album"
            )
            state.handleIncomingSongMetadata(pausedSong, sourceIsICY = false)
            assertTrue(state.historyEntries.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun mediaPlaybackSnapshotAndActionControlsSwitchStationsAndControlStream() {
        val tempDir = Files.createTempDirectory("ChilldroidMediaSessionTests-${UUID.randomUUID()}").toFile()
        try {
            val player = RadioPlayer(enableNativeMediaPlayer = false)
            val state = AppState(
                client = ChilltraxClient(),
                historyStore = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz")),
                coverCache = AlbumCoverCache(directory = File(tempDir, "Covers")),
                radioPlayer = player,
                preferences = InMemoryPreferencesStore(),
                startPollingAutomatically = false
            )
            state.selectStation(RadioStation.CHILLTRAX, autoPlayIfCurrentlyPlaying = false)

            // Placeholder snapshot before track metadata arrives
            val idleSnapshot = com.lucianmarin.trax.audio.RadioPlaybackService.buildSnapshot(
                status = PlaybackStatus.IDLE,
                isPlaying = false,
                station = state.selectedStation,
                song = state.currentSong
            )
            assertEquals("Chilltrax", idleSnapshot.title)
            assertEquals("The World's Chillout Channel", idleSnapshot.artist)
            assertEquals("TRAX • Chilltrax", idleSnapshot.stationSubtitle)
            assertEquals(android.media.session.PlaybackState.STATE_STOPPED, idleSnapshot.playbackStateCode)

            // Play action via media session handler
            player.setSimulatedPlaybackStateForTesting(isPlaying = true, status = PlaybackStatus.PLAYING)
            val track = SongMetadata(
                artist = "Bonobo",
                title = "Kerala",
                albumOrMix = "Migration"
            )
            state.handleIncomingSongMetadata(track, sourceIsICY = false)

            val playingSnapshot = com.lucianmarin.trax.audio.RadioPlaybackService.buildSnapshot(
                status = state.radioPlayer.status,
                isPlaying = state.radioPlayer.isPlaying,
                station = state.selectedStation,
                song = state.currentSong
            )
            assertEquals("Kerala", playingSnapshot.title)
            assertEquals("Bonobo — Migration", playingSnapshot.artist)
            assertEquals("Migration", playingSnapshot.album)
            assertEquals(android.media.session.PlaybackState.STATE_PLAYING, playingSnapshot.playbackStateCode)

            // Skip to next switches station from Chilltrax -> Radio Danz
            assertTrue(
                com.lucianmarin.trax.audio.RadioPlaybackService.handleMediaAction(
                    com.lucianmarin.trax.audio.RadioPlaybackService.ACTION_NEXT_STATION,
                    state
                )
            )
            assertEquals(RadioStation.RADIO_DANZ, state.selectedStation)

            // Pause via media controls
            assertTrue(
                com.lucianmarin.trax.audio.RadioPlaybackService.handleMediaAction(
                    com.lucianmarin.trax.audio.RadioPlaybackService.ACTION_PAUSE,
                    state
                )
            )
            assertFalse(state.radioPlayer.isPlaying)
            assertEquals(PlaybackStatus.PAUSED, state.radioPlayer.status)

            // Stop via media controls
            assertTrue(
                com.lucianmarin.trax.audio.RadioPlaybackService.handleMediaAction(
                    com.lucianmarin.trax.audio.RadioPlaybackService.ACTION_STOP,
                    state
                )
            )
            assertFalse(state.radioPlayer.isPlaying)
            assertEquals(PlaybackStatus.IDLE, state.radioPlayer.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun visualizerTimerStopsWhenOffOrNotPlayingAndStopTearsDownStream() {
        val tempDir = Files.createTempDirectory("ChilldroidVisualizerTimerTests-${UUID.randomUUID()}").toFile()
        try {
            val player = RadioPlayer(enableNativeMediaPlayer = false)
            val state = AppState(
                client = ChilltraxClient(),
                historyStore = CompressedHistoryStore(file = File(tempDir, "history.tsv.gz")),
                coverCache = AlbumCoverCache(directory = File(tempDir, "Covers")),
                radioPlayer = player,
                preferences = InMemoryPreferencesStore(),
                startPollingAutomatically = false
            )

            assertEquals("stop", ChilltraxIcons.StopFilled.name)

            // Idle player does not run visualizer timer
            assertFalse(state.radioPlayer.isVisualizerTimerRunning)

            // Playing with visualizer enabled runs timer
            state.selectVisualizerMode(VisualizerMode.SPECTRUM)
            player.setSimulatedPlaybackStateForTesting(isPlaying = true, status = PlaybackStatus.PLAYING)
            assertTrue(state.radioPlayer.isVisualizerTimerRunning)

            // Turning visualizer OFF stops timer immediately even while playing
            state.selectVisualizerMode(VisualizerMode.OFF)
            assertFalse(state.radioPlayer.isVisualizerTimerRunning)

            // Turning visualizer back on resumes timer while playing
            state.selectVisualizerMode(VisualizerMode.WAVEFORM)
            assertTrue(state.radioPlayer.isVisualizerTimerRunning)

            // Toggling play/stop stops playback, resets status to IDLE (READY), and stops timer
            state.togglePlayPause()
            assertFalse(state.radioPlayer.isPlaying)
            assertEquals(PlaybackStatus.IDLE, state.radioPlayer.status)
            assertEquals("READY", state.toolbarStatusLabel)
            assertFalse(state.radioPlayer.isVisualizerTimerRunning)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
