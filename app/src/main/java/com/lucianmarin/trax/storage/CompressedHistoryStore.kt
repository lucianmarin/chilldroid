package com.lucianmarin.trax.storage

import com.lucianmarin.trax.model.SongHistoryEntry
import com.lucianmarin.trax.model.SongMetadata
import com.lucianmarin.trax.model.StationStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class CompressedHistoryStore(
    val file: File
) {
    val fileSizeBytes: Long
        get() = if (file.exists()) file.length() else 0L

    fun load(): List<SongHistoryEntry> {
        if (!file.exists()) return emptyList()
        val rawData = runCatching { file.readBytes() }.getOrNull() ?: return emptyList()
        if (rawData.isEmpty()) return emptyList()

        val text = decompressGzip(rawData)
            ?: String(rawData, StandardCharsets.UTF_8)

        val entries = mutableListOf<SongHistoryEntry>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val cols = trimmed.split('\t')
            if (cols.size < 4) continue

            val id = cols[0].takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
            val playedAtEpochSeconds = parseTimestamp(cols[1])
            val artist = unescape(cols[2])
            val title = unescape(cols[3])
            val album = if (cols.size > 4) unescape(cols[4]) else ""
            val year = if (cols.size > 5) unescape(cols[5]) else ""
            val stationName = if (cols.size > 6) unescape(cols[6]) else "Chilltrax"
            val stationURL = if (cols.size > 7) unescape(cols[7]) else StationStream.defaultStream.urlString
            val coverURLRaw = if (cols.size > 8) unescape(cols[8]) else ""

            entries.add(
                SongHistoryEntry(
                    id = id,
                    playedAtEpochSeconds = playedAtEpochSeconds,
                    artist = artist,
                    title = title,
                    albumOrMix = album,
                    year = year,
                    stationName = stationName,
                    stationURLString = stationURL,
                    coverURLString = coverURLRaw.takeIf { it.isNotEmpty() }
                )
            )
        }
        return entries.sortedByDescending { it.playedAtEpochSeconds }
    }

    fun save(entries: List<SongHistoryEntry>) {
        val sorted = entries.sortedByDescending { it.playedAtEpochSeconds }.take(MAX_ENTRIES)
        val lines = mutableListOf(HEADER_LINE)
        for (entry in sorted) {
            val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(entry.playedAtEpochSeconds))
            val row = listOf(
                entry.id,
                timestamp,
                escape(entry.artist),
                escape(entry.title),
                escape(entry.albumOrMix),
                escape(entry.year),
                escape(entry.stationName),
                escape(entry.stationURLString),
                escape(entry.coverURLString ?: "")
            ).joinToString("\t")
            lines.add(row)
        }

        val fullText = lines.joinToString("\n")
        val rawBytes = fullText.toByteArray(StandardCharsets.UTF_8)
        val compressed = compressGzip(rawBytes)

        file.parentFile?.mkdirs()
        file.writeBytes(compressed)
    }

    fun recordSong(
        song: SongMetadata,
        station: StationStream,
        playedAtEpochSeconds: Long = System.currentTimeMillis() / 1000L,
        existingEntries: List<SongHistoryEntry>? = null
    ): List<SongHistoryEntry> {
        var entries = (existingEntries ?: load()).toMutableList()
        if (!song.isMeaningfulTrack) return entries

        val mostRecent = entries.firstOrNull()
        if (mostRecent != null && mostRecent.dedupKey == song.dedupKey) {
            if (mostRecent.coverURLString == null && song.coverURL != null) {
                entries[0] = mostRecent.copy(coverURLString = song.coverURL)
                save(entries)
            }
            return entries
        }

        val newEntry = SongHistoryEntry(
            id = UUID.randomUUID().toString(),
            playedAtEpochSeconds = playedAtEpochSeconds,
            artist = song.artist,
            title = song.title,
            albumOrMix = song.albumOrMix,
            year = song.year,
            stationName = station.name,
            stationURLString = station.urlString,
            coverURLString = song.coverURL
        )
        entries.add(0, newEntry)
        if (entries.size > MAX_ENTRIES) {
            entries = entries.take(MAX_ENTRIES).toMutableList()
        }
        save(entries)
        return entries
    }

    fun clear() {
        if (file.exists()) {
            file.delete()
        }
    }

    private fun parseTimestamp(raw: String): Long {
        return runCatching {
            Instant.parse(raw.trim()).epochSecond
        }.getOrElse {
            raw.trim().toLongOrNull() ?: (System.currentTimeMillis() / 1000L)
        }
    }

    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\t", " ")
        .replace("\n", " ")
        .replace("\r", " ")

    private fun unescape(text: String): String = text
        .replace("\\\\", "\\")

    companion object {
        private const val HEADER_LINE = "# chilltrax-song-history-v1\tid\tplayed_at_iso8601\tartist\ttitle\talbum_or_mix\tyear\tstation_name\tstation_url\tcover_url"
        const val MAX_ENTRIES = 5_000

        fun compressGzip(data: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { gzip ->
                gzip.write(data)
            }
            return out.toByteArray()
        }

        fun decompressGzip(data: ByteArray): String? {
            if (data.size < 2) return null
            val magic = ((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8))
            if (magic != GZIPInputStream.GZIP_MAGIC) return null
            return runCatching {
                GZIPInputStream(ByteArrayInputStream(data)).use { gzip ->
                    gzip.readBytes().toString(StandardCharsets.UTF_8)
                }
            }.getOrNull()
        }
    }
}
