package com.lucianmarin.trax.network

import com.lucianmarin.trax.model.ChilltraxCoverResponse
import com.lucianmarin.trax.model.ChilltraxLastTenItem
import com.lucianmarin.trax.model.ChilltraxNowPlayingResponse
import com.lucianmarin.trax.model.RadioStation
import com.lucianmarin.trax.model.SongMetadata
import com.lucianmarin.trax.model.StationAPIEndpoints
import com.lucianmarin.trax.model.StationStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class StationDiscoveryResult(
    val station: RadioStation,
    val streams: List<StationStream>,
    val endpoints: StationAPIEndpoints
)

class ChilltraxClient(
    private val httpClient: OkHttpClient = defaultOkHttpClient()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun fetchNowPlaying(
        station: RadioStation = RadioStation.CHILLTRAX,
        endpoints: StationAPIEndpoints? = null,
        fetchAlbumCover: Boolean = true
    ): SongMetadata = coroutineScope {
        val resolvedEndpoints = endpoints ?: station.defaultAPIEndpoints
        val infoDeferred = async(Dispatchers.IO) {
            fetchJSON<ChilltraxNowPlayingResponse>(resolvedEndpoints.songInfoURLString)
        }
        val coverDeferred = async(Dispatchers.IO) {
            if (fetchAlbumCover) {
                runCatching { fetchJSON<ChilltraxCoverResponse>(resolvedEndpoints.coverImageURLString) }.getOrNull()
            } else {
                null
            }
        }

        val info = infoDeferred.await()
        val cover = coverDeferred.await()

        var coverURLString = if (fetchAlbumCover) cover?.cover else null
        if (fetchAlbumCover && coverURLString.isNullOrBlank()) {
            val cleanArtist = SongMetadata.decodeHTMLEntities(info.artist ?: "").trim()
            val cleanTitle = SongMetadata.decodeHTMLEntities(info.title ?: "").trim()
            if (cleanArtist.isNotEmpty() && cleanTitle.isNotEmpty()) {
                coverURLString = runCatching {
                    fetchITunesArtworkURL(artist = cleanArtist, title = cleanTitle)
                }.getOrNull()
            }
        }

        info.toSongMetadata(
            coverURLString = coverURLString,
            baseURLString = station.homepageURLString
        )
    }

    suspend fun fetchCoverImage(
        station: RadioStation = RadioStation.CHILLTRAX,
        endpoints: StationAPIEndpoints? = null,
        artist: String = "",
        title: String = ""
    ): String? = withContext(Dispatchers.IO) {
        val resolvedEndpoints = endpoints ?: station.defaultAPIEndpoints
        val coverResponse = runCatching {
            fetchJSON<ChilltraxCoverResponse>(resolvedEndpoints.coverImageURLString)
        }.getOrNull()
        val normalized = SongMetadata.normalizeCoverURL(
            coverResponse?.cover,
            baseURLString = station.homepageURLString
        )
        if (normalized != null) {
            return@withContext normalized
        }

        val cleanArtist = artist.trim()
        val cleanTitle = title.trim()
        if (cleanArtist.isNotEmpty() && cleanTitle.isNotEmpty()) {
            return@withContext runCatching {
                fetchITunesArtworkURL(artist = cleanArtist, title = cleanTitle)
            }.getOrNull()
        }
        null
    }

    suspend fun fetchITunesArtworkURL(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val term = "$artist $title".trim()
        if (term.isEmpty()) return@withContext null
        val encoded = URLEncoder.encode(term, StandardCharsets.UTF_8.name())
        val searchURL = "https://itunes.apple.com/search?term=$encoded&entity=song&limit=1"
        val response = fetchJSON<ITunesSearchResponse>(searchURL)
        val rawArtwork = response.results.firstOrNull()?.artworkUrl100 ?: return@withContext null
        rawArtwork.replace("100x100bb", "300x300bb")
    }

    suspend fun fetchLastTen(
        station: RadioStation = RadioStation.CHILLTRAX,
        endpoints: StationAPIEndpoints? = null,
        includeCovers: Boolean = true
    ): List<SongMetadata> = withContext(Dispatchers.IO) {
        val resolvedEndpoints = endpoints ?: station.defaultAPIEndpoints
        val items = fetchJSON<List<ChilltraxLastTenItem>>(resolvedEndpoints.lastTenURLString)
        items.map { item ->
            SongMetadata(
                artist = item.artist ?: "",
                title = item.title ?: "",
                albumOrMix = item.mix ?: "",
                year = item.year ?: "",
                coverURLString = if (includeCovers) item.picture else null,
                buyURLString = item.buycd,
                baseURLString = station.homepageURLString
            )
        }
    }

    suspend fun fetchRawBytes(urlString: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", USER_AGENT)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $urlString")
            }
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    suspend fun discoverStationResources(station: RadioStation): StationDiscoveryResult = withContext(Dispatchers.IO) {
        val html = fetchText(station.homepageURLString)
        val streams = parseBroadcastStreams(fromHTML = html, station = station).toMutableList()
        val endpoints = parseAPIEndpoints(fromHTML = html, station = station)

        val plsCandidates = streams.filter { stream ->
            val lower = stream.urlString.lowercase()
            lower.endsWith("/listen") || lower.contains(".pls")
        }
        for (plsStream in plsCandidates) {
            val resolvedURL = runCatching { resolvePLSStreamURL(plsStream.urlString) }.getOrNull()
            if (resolvedURL != null && streams.none { it.urlString == resolvedURL }) {
                streams.add(
                    0,
                    StationStream(
                        id = "discovered-${station.rawValue}-${resolvedURL.hashCode()}",
                        name = "${station.displayName} Discovered SSL Stream",
                        urlString = resolvedURL,
                        formatDescription = "128 Kbps MP3 (Resolved PLS)",
                        isBuiltIn = true,
                        station = station
                    )
                )
            }
        }

        StationDiscoveryResult(
            station = station,
            streams = streams,
            endpoints = endpoints
        )
    }

    suspend fun discoverAllBroadcasts(): Map<RadioStation, StationDiscoveryResult> = coroutineScope {
        val results = mutableMapOf<RadioStation, StationDiscoveryResult>()
        for (station in RadioStation.entries) {
            val discovery = runCatching { discoverStationResources(station) }.getOrElse {
                StationDiscoveryResult(
                    station = station,
                    streams = StationStream.defaultStreams(station = station),
                    endpoints = station.defaultAPIEndpoints
                )
            }
            results[station] = discovery
        }
        results
    }

    suspend fun resolvePlayableStreamURL(urlString: String): String = withContext(Dispatchers.IO) {
        val lower = urlString.lowercase()
        if (lower.endsWith(".pls") || lower.contains(".pls?") || lower.endsWith("/listen")) {
            val resolved = runCatching { resolvePLSStreamURL(urlString) }.getOrNull()
            if (resolved != null) {
                return@withContext resolved
            }
        }
        urlString
    }

    suspend fun resolvePLSStreamURL(urlString: String): String? = withContext(Dispatchers.IO) {
        val text = fetchText(urlString)
        parsePLSStreamURL(text)
    }

    private inline fun <reified T> fetchJSON(urlString: String): T {
        val text = fetchText(urlString)
        return json.decodeFromString(text)
    }

    private fun fetchText(urlString: String): String {
        val request = Request.Builder()
            .url(urlString)
            .header("User-Agent", USER_AGENT)
            .header("Cache-Control", "no-cache")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $urlString")
            }
            return response.body?.string() ?: ""
        }
    }

    @Serializable
    private data class ITunesSearchResponse(
        val results: List<ITunesTrack> = emptyList()
    )

    @Serializable
    private data class ITunesTrack(
        @SerialName("artworkUrl100")
        val artworkUrl100: String? = null
    )

    companion object {
        const val USER_AGENT = "Chilldroid/1.0 (Android; Jetpack Compose)"

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun parseBroadcastStreams(
            fromHTML: String,
            station: RadioStation = RadioStation.CHILLTRAX
        ): List<StationStream> {
            val discovered = mutableListOf<StationStream>()
            val seenURLs = mutableSetOf<String>()

            fun appendStream(name: String, urlString: String, format: String) {
                val trimmed = urlString.trim()
                if (trimmed.isEmpty() || !seenURLs.add(trimmed)) return
                discovered.add(
                    StationStream(
                        id = "${station.rawValue}-${trimmed.hashCode()}",
                        name = name,
                        urlString = trimmed,
                        formatDescription = format,
                        isBuiltIn = true,
                        station = station
                    )
                )
            }

            // 1. Match `streamurl: "..."` from embedded web player script
            val streamURLRegex = Regex("""streamurl\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            streamURLRegex.findAll(fromHTML).forEach { match ->
                val url = match.groupValues[1]
                appendStream(
                    name = "${station.displayName} Web Player Stream",
                    urlString = url,
                    format = "128 Kbps MP3 (Web Redirect)"
                )
            }

            // 2. Match `<a ... href="...">Label</a>` stream links
            val anchorRegex = Regex(
                """<a[^>]*href="((?:https?://)[^"]*(?:electronicmusicradiogroup\.org|chilltrax\.com/listen|radiodanz\.com[^"]*|streamssl[^"]*))"[^>]*>([^<]+)</a>""",
                RegexOption.IGNORE_CASE
            )
            anchorRegex.findAll(fromHTML).forEach { match ->
                val url = match.groupValues[1]
                val rawLabel = SongMetadata.decodeHTMLEntities(match.groupValues[2]).trim()
                if (rawLabel.isEmpty()) return@forEach
                val displayName = if (rawLabel.lowercase().startsWith(station.displayName.lowercase())) {
                    rawLabel
                } else {
                    "${station.displayName} — $rawLabel"
                }
                appendStream(
                    name = displayName,
                    urlString = url,
                    format = rawLabel
                )
            }

            // Ensure built-in streams for this station are always present
            for (fallback in StationStream.defaultStreams(station = station)) {
                if (seenURLs.add(fallback.urlString)) {
                    discovered.add(fallback)
                }
            }

            return discovered
        }

        fun parseAPIEndpoints(
            fromHTML: String,
            station: RadioStation = RadioStation.CHILLTRAX
        ): StationAPIEndpoints {
            val baseURL = station.homepageURLString.trimEnd('/')

            fun resolveEndpoint(relativeOrAbsolute: String): String {
                val trimmed = relativeOrAbsolute.trim()
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    return trimmed
                }
                val cleanPath = trimmed.trimStart('/')
                return "$baseURL/$cleanPath"
            }

            var songInfoURL = station.defaultSongInfoURLString
            var coverImageURL = station.defaultCoverImageURLString
            var lastTenURL = station.defaultLastTenURLString

            val phpEndpointRegex = Regex(
                """['"]((?:https?://[^'"]+)?php/Get(?:SongInformation|CoverImage|LastTen)[A-Za-z0-9_]*\.php)['"]""",
                RegexOption.IGNORE_CASE
            )
            phpEndpointRegex.findAll(fromHTML).forEach { match ->
                val rawPath = match.groupValues[1]
                val fullURL = resolveEndpoint(rawPath)
                val lower = rawPath.lowercase()
                when {
                    lower.contains("getsonginformation") -> songInfoURL = fullURL
                    lower.contains("getcoverimage") -> coverImageURL = fullURL
                    lower.contains("getlastten") -> lastTenURL = fullURL
                }
            }

            return StationAPIEndpoints(
                station = station,
                songInfoURLString = songInfoURL,
                coverImageURLString = coverImageURL,
                lastTenURLString = lastTenURL
            )
        }

        fun parsePLSStreamURL(fromPLS: String): String? {
            for (line in fromPLS.lines()) {
                val trimmed = line.trim()
                if (trimmed.lowercase().startsWith("file1=")) {
                    val value = trimmed.substringAfter("=").trim()
                    if (value.startsWith("http://") || value.startsWith("https://")) {
                        return value
                    }
                }
            }
            return null
        }
    }
}
