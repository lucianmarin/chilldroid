package com.lucianmarin.trax.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class VisualizerMode(val rawValue: String, val title: String, val googleFontIconName: String) {
    OFF("off", "Info", "notes"),
    WAVEFORM("waveform", "Waveform", "vital_signs"),
    SPECTRUM("spectrum", "Spectrum", "bar_chart_4_bars");

    val isVisible: Boolean
        get() = this != OFF

    fun toggledMode(): VisualizerMode = when (this) {
        SPECTRUM -> WAVEFORM
        WAVEFORM -> SPECTRUM
        OFF -> SPECTRUM
    }

    companion object {
        /**
         * Ordered segments for the bottom app controls segmented tabs:
         * No Info (Off), Waveform (Vital Signs), Spectrum (Bar Chart 4 Bars).
         */
        val segmentedTabsOrder: List<VisualizerMode> = listOf(OFF, WAVEFORM, SPECTRUM)

        fun fromRawValue(value: String?): VisualizerMode {
            return entries.firstOrNull { it.rawValue.equals(value, ignoreCase = true) } ?: SPECTRUM
        }
    }
}

enum class RadioStation(
    val rawValue: String,
    val displayName: String,
    val badgeLabel: String,
    val tagline: String,
    val homepageURLString: String,
    val defaultSongInfoURLString: String,
    val defaultCoverImageURLString: String,
    val defaultLastTenURLString: String
) {
    CHILLTRAX(
        rawValue = "chilltrax",
        displayName = "Chilltrax",
        badgeLabel = "TRAX",
        tagline = "The World's Chillout Channel",
        homepageURLString = "https://www.chilltrax.com",
        defaultSongInfoURLString = "https://www.chilltrax.com/php/GetSongInformation.php",
        defaultCoverImageURLString = "https://www.chilltrax.com/php/GetCoverImage.php",
        defaultLastTenURLString = "https://www.chilltrax.com/php/GetLastTen.php"
    ),
    RADIO_DANZ(
        rawValue = "radiodanz",
        displayName = "Radio Danz",
        badgeLabel = "DANZ",
        tagline = "The World's Dance Music Channel",
        homepageURLString = "https://www.radiodanz.com",
        defaultSongInfoURLString = "https://www.radiodanz.com/php/GetSongInformationV2.php",
        defaultCoverImageURLString = "https://www.radiodanz.com/php/GetCoverImageV2.php",
        defaultLastTenURLString = "https://www.radiodanz.com/php/GetLastTenV2.php"
    );

    fun toggledStation(): RadioStation = when (this) {
        CHILLTRAX -> RADIO_DANZ
        RADIO_DANZ -> CHILLTRAX
    }

    val defaultAPIEndpoints: StationAPIEndpoints
        get() = StationAPIEndpoints(
            station = this,
            songInfoURLString = defaultSongInfoURLString,
            coverImageURLString = defaultCoverImageURLString,
            lastTenURLString = defaultLastTenURLString
        )

    companion object {
        fun fromRawValue(value: String?): RadioStation {
            return entries.firstOrNull { it.rawValue.equals(value, ignoreCase = true) } ?: CHILLTRAX
        }
    }
}

@Serializable
data class StationAPIEndpoints(
    val station: RadioStation,
    val songInfoURLString: String,
    val coverImageURLString: String,
    val lastTenURLString: String
)

@Serializable
data class StationStream(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val urlString: String,
    val formatDescription: String = "128 Kbps",
    val isBuiltIn: Boolean = false,
    val station: RadioStation = RadioStation.CHILLTRAX
) {
    companion object {
        val defaultChilltraxStreams: List<StationStream> = listOf(
            StationStream(
                id = "chilltrax-web-redirect",
                name = "Chilltrax Web Player Stream",
                urlString = "https://www.chilltrax.com/listen/?mode=redirect",
                formatDescription = "128 Kbps MP3 (Web Redirect)",
                isBuiltIn = true,
                station = RadioStation.CHILLTRAX
            ),
            StationStream(
                id = "chilltrax-ssl-eu",
                name = "Chilltrax SSL Stream (Direct)",
                urlString = "https://streamssleu.chilltrax.com/stream?sid=1",
                formatDescription = "128 Kbps MP3 (HTTPS)",
                isBuiltIn = true,
                station = RadioStation.CHILLTRAX
            ),
            StationStream(
                id = "chilltrax-hd-aac-9050",
                name = "Chilltrax HD 128Kbps AAC+",
                urlString = "http://streams.electronicmusicradiogroup.org:9050",
                formatDescription = "128 Kbps AAC+",
                isBuiltIn = true,
                station = RadioStation.CHILLTRAX
            ),
            StationStream(
                id = "chilltrax-hq-mp3-9000",
                name = "Chilltrax High Quality 128Kbps MP3",
                urlString = "http://streams.electronicmusicradiogroup.org:9000",
                formatDescription = "128 Kbps MP3",
                isBuiltIn = true,
                station = RadioStation.CHILLTRAX
            ),
            StationStream(
                id = "chilltrax-firewall-mp3",
                name = "Chilltrax Firewall Friendly (.pls)",
                urlString = "https://www.chilltrax.com/listen",
                formatDescription = "128 Kbps MP3 (PLS)",
                isBuiltIn = true,
                station = RadioStation.CHILLTRAX
            ),
            StationStream(
                id = "chilltrax-aac-9020",
                name = "Chilltrax 80Kbps AAC+",
                urlString = "http://streams.electronicmusicradiogroup.org:9020",
                formatDescription = "80 Kbps AAC+",
                isBuiltIn = true,
                station = RadioStation.CHILLTRAX
            ),
            StationStream(
                id = "chilltrax-aac-9010",
                name = "Chilltrax 64Kbps AAC+",
                urlString = "http://streams.electronicmusicradiogroup.org:9010",
                formatDescription = "64 Kbps AAC+",
                isBuiltIn = true,
                station = RadioStation.CHILLTRAX
            )
        )

        val defaultRadioDanzStreams: List<StationStream> = listOf(
            StationStream(
                id = "radiodanz-web-redirect",
                name = "Radio Danz Web Player Stream",
                urlString = "https://www.radiodanz.com/results/?mode=redirect",
                formatDescription = "128 Kbps MP3 (Web Redirect)",
                isBuiltIn = true,
                station = RadioStation.RADIO_DANZ
            ),
            StationStream(
                id = "radiodanz-ssl-live",
                name = "Radio Danz SSL Stream (Direct)",
                urlString = "https://streamssl.radiodanz.com/live",
                formatDescription = "128 Kbps MP3 (HTTPS)",
                isBuiltIn = true,
                station = RadioStation.RADIO_DANZ
            ),
            StationStream(
                id = "radiodanz-hd-aac-8030",
                name = "Radio Danz HD 128Kbps AAC+ (.pls)",
                urlString = "http://streams.electronicmusicradiogroup.org:8030/listen.pls",
                formatDescription = "128 Kbps AAC+ (PLS)",
                isBuiltIn = true,
                station = RadioStation.RADIO_DANZ
            ),
            StationStream(
                id = "radiodanz-hq-mp3-8000",
                name = "Radio Danz High Quality 128Kbps MP3 (.pls)",
                urlString = "http://streams.electronicmusicradiogroup.org:8000/listen.pls",
                formatDescription = "128 Kbps MP3 (PLS)",
                isBuiltIn = true,
                station = RadioStation.RADIO_DANZ
            ),
            StationStream(
                id = "radiodanz-firewall-mp3",
                name = "Radio Danz Firewall Friendly (.pls)",
                urlString = "http://streamssl.radiodanz.com/listen.pls?sid=1",
                formatDescription = "128 Kbps MP3 (PLS)",
                isBuiltIn = true,
                station = RadioStation.RADIO_DANZ
            ),
            StationStream(
                id = "radiodanz-aac-8010",
                name = "Radio Danz 64Kbps AAC+ (.pls)",
                urlString = "http://streams.electronicmusicradiogroup.org:8010/listen.pls",
                formatDescription = "64 Kbps AAC+ (PLS)",
                isBuiltIn = true,
                station = RadioStation.RADIO_DANZ
            ),
            StationStream(
                id = "radiodanz-mp3-8020",
                name = "Radio Danz 64Kbps MP3 (.pls)",
                urlString = "http://streams.electronicmusicradiogroup.org:8020/listen.pls",
                formatDescription = "64 Kbps MP3 (PLS)",
                isBuiltIn = true,
                station = RadioStation.RADIO_DANZ
            )
        )

        val defaultStreams: List<StationStream> = defaultChilltraxStreams + defaultRadioDanzStreams

        fun defaultStreams(station: RadioStation): List<StationStream> = when (station) {
            RadioStation.CHILLTRAX -> defaultChilltraxStreams
            RadioStation.RADIO_DANZ -> defaultRadioDanzStreams
        }

        fun preferredDefaultStream(
            station: RadioStation,
            streams: List<StationStream> = defaultStreams(station = station)
        ): StationStream {
            val stationStreams = streams.filter { it.station == station }
            val candidates = stationStreams.ifEmpty { defaultStreams(station = station) }
            val fallbackCandidates = defaultStreams(station = station).filter {
                !it.urlString.contains("redirect", ignoreCase = true)
            }
            return candidates.firstOrNull { it.urlString.contains("redirect", ignoreCase = true) }
                ?: candidates.firstOrNull { !it.urlString.contains("redirect", ignoreCase = true) }
                ?: fallbackCandidates.firstOrNull()
                ?: defaultStreams(station = station).first()
        }

        fun defaultStream(station: RadioStation): StationStream =
            preferredDefaultStream(station = station)

        val defaultStream: StationStream = defaultStream(station = RadioStation.CHILLTRAX)
    }
}

@Serializable
data class SongMetadata(
    val artist: String,
    val title: String,
    val albumOrMix: String = "",
    val label: String = "",
    val year: String = "",
    val coverURLString: String? = null,
    val buyURLString: String? = null,
    val songID: Int? = null
) {
    constructor(
        artist: String,
        title: String,
        albumOrMix: String = "",
        label: String = "",
        year: String = "",
        coverURLString: String? = null,
        buyURLString: String? = null,
        songID: Int? = null,
        baseURLString: String = "https://www.chilltrax.com"
    ) : this(
        artist = decodeHTMLEntities(artist).trim(),
        title = decodeHTMLEntities(title).trim(),
        albumOrMix = decodeHTMLEntities(albumOrMix).trim(),
        label = decodeHTMLEntities(label).trim(),
        year = year.trim(),
        coverURLString = normalizeCoverURL(coverURLString, baseURLString = baseURLString),
        buyURLString = buyURLString?.trim()?.takeIf { it.isNotEmpty() },
        songID = songID
    )

    val coverURL: String?
        get() = coverURLString?.takeIf { it.isNotBlank() }

    val buyURL: String?
        get() = buyURLString?.takeIf { it.isNotBlank() }

    val appleMusicURL: String?
        get() {
            if (!isMeaningfulTrack) return null
            val direct = buyURL
            if (direct != null && isValidURI(direct)) {
                return direct
            }
            val query = listOf(artist, title)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            if (query.isEmpty()) return null
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name()).replace("+", "%20")
            return "https://music.apple.com/us/search?term=$encoded"
        }

    val displayName: String
        get() = when {
            artist.isEmpty() && title.isEmpty() -> "Chilltrax"
            artist.isEmpty() -> title
            title.isEmpty() -> artist
            else -> "$artist — $title"
        }

    val clipboardText: String
        get() = when {
            artist.isEmpty() && title.isEmpty() -> "Chilltrax"
            artist.isEmpty() -> title
            title.isEmpty() -> artist
            else -> "$artist - $title"
        }

    val dedupKey: String
        get() = "${artist.lowercase().trim()}|${title.lowercase().trim()}"

    val isMeaningfulTrack: Boolean
        get() {
            val lowerTitle = title.lowercase()
            val lowerArtist = artist.lowercase()
            if (lowerTitle.isEmpty() && lowerArtist.isEmpty()) return false
            if (lowerTitle == "title" && lowerArtist == "artist") return false
            if (lowerTitle == "chilltrax" && (lowerArtist.isEmpty() || lowerArtist == "the world's chillout channel")) {
                return false
            }
            if (lowerTitle == "radio danz" && (lowerArtist.isEmpty() || lowerArtist == "the world's dance music channel")) {
                return false
            }
            if (lowerTitle.contains("chilltrax") && lowerArtist.contains("chilltrax")) {
                return false
            }
            if (lowerTitle.contains("radiodanz") || (lowerTitle.contains("radio danz") && lowerArtist.contains("radio danz"))) {
                return false
            }
            return true
        }

    companion object {
        val placeholder: SongMetadata = SongMetadata(
            artist = "",
            title = "",
            albumOrMix = "",
            label = "",
            year = ""
        )

        fun fromICYStreamTitle(rawTitle: String): SongMetadata? {
            val cleaned = decodeHTMLEntities(rawTitle).trim()
            if (cleaned.isEmpty()) return null

            val separatorIndex = cleaned.indexOf(" - ")
            return if (separatorIndex >= 0) {
                val artist = cleaned.substring(0, separatorIndex).trim()
                val title = cleaned.substring(separatorIndex + 3).trim()
                SongMetadata(artist = artist, title = title, albumOrMix = "")
            } else {
                SongMetadata(artist = "", title = cleaned, albumOrMix = "")
            }
        }

        fun normalizeCoverURL(raw: String?, baseURLString: String = "https://www.chilltrax.com"): String? {
            val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (trimmed.contains("chilltrax_default", ignoreCase = true) ||
                trimmed.contains("default_cover", ignoreCase = true) ||
                trimmed.contains("radiodanz_default", ignoreCase = true) ||
                trimmed.contains("no_image", ignoreCase = true) ||
                trimmed.contains("nocover", ignoreCase = true)
            ) {
                return null
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed
            }
            val cleanBase = baseURLString.trimEnd('/')
            val cleanPath = trimmed.trimStart('/')
            return "$cleanBase/$cleanPath"
        }

        fun decodeHTMLEntities(text: String): String {
            var result = text
            val replacements = mapOf(
                "&amp;" to "&",
                "&quot;" to "\"",
                "&#039;" to "'",
                "&apos;" to "'",
                "&lt;" to "<",
                "&gt;" to ">"
            )
            for ((entity, char) in replacements) {
                result = result.replace(entity, char)
            }
            return result
        }

        private fun isValidURI(url: String): Boolean = try {
            val uri = URI(url)
            uri.scheme != null && uri.host != null
        } catch (_: Exception) {
            false
        }
    }
}

@Serializable
data class SongHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val playedAtEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    val artist: String,
    val title: String,
    val albumOrMix: String = "",
    val year: String = "",
    val stationName: String = "Chilltrax",
    val stationURLString: String = StationStream.defaultStream.urlString,
    val coverURLString: String? = null
) {
    val coverURL: String?
        get() = coverURLString?.takeIf { it.isNotBlank() }

    val appleMusicSearchURL: String?
        get() {
            val query = listOf(artist, title)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            if (query.isEmpty()) return null
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name()).replace("+", "%20")
            return "https://music.apple.com/us/search?term=$encoded"
        }

    val dedupKey: String
        get() = "${artist.lowercase().trim()}|${title.lowercase().trim()}"

    val stationBadgeLabel: String
        get() {
            val lowerName = stationName.lowercase()
            val lowerURL = stationURLString.lowercase()
            return if (lowerName.contains("danz") || lowerURL.contains("radiodanz")) {
                RadioStation.RADIO_DANZ.badgeLabel
            } else {
                RadioStation.CHILLTRAX.badgeLabel
            }
        }

    val clipboardText: String
        get() {
            val cleanArtist = artist.trim()
            val cleanTitle = title.trim()
            return when {
                cleanArtist.isEmpty() && cleanTitle.isEmpty() -> stationName
                cleanArtist.isEmpty() -> cleanTitle
                cleanTitle.isEmpty() -> cleanArtist
                else -> "$cleanArtist - $cleanTitle"
            }
        }

    fun matches(searchQuery: String): Boolean {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) return true
        val terms = trimmed.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val haystack = listOf(
            artist,
            title,
            albumOrMix,
            year,
            stationName,
            stationBadgeLabel
        ).joinToString(" ").lowercase()
        return terms.all { haystack.contains(it) }
    }
}

@Serializable
data class ChilltraxNowPlayingResponse(
    val artist: String? = null,
    val title: String? = null,
    val mix: String? = null,
    val label: String? = null,
    val year: String? = null,
    val buycd: String? = null,
    val newSong: String? = null,
    @SerialName("songID")
    val rawSongID: JsonElement? = null
) {
    val songID: Int?
        get() = (rawSongID as? JsonPrimitive)?.content?.toIntOrNull()

    fun toSongMetadata(
        coverURLString: String?,
        baseURLString: String = "https://www.chilltrax.com"
    ): SongMetadata = SongMetadata(
        artist = artist ?: "",
        title = title ?: "",
        albumOrMix = mix ?: "",
        label = label ?: "",
        year = year ?: "",
        coverURLString = coverURLString,
        buyURLString = buycd,
        songID = songID,
        baseURLString = baseURLString
    )
}

@Serializable
data class ChilltraxCoverResponse(
    val cover: String? = null,
    val artist: String? = null,
    val title: String? = null
)

@Serializable
data class ChilltraxLastTenItem(
    val artist: String? = null,
    val title: String? = null,
    val mix: String? = null,
    val year: String? = null,
    val picture: String? = null,
    val buycd: String? = null,
    val playedDate: String? = null
)
