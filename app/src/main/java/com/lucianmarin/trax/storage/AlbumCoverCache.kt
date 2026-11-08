package com.lucianmarin.trax.storage

import com.lucianmarin.trax.network.ChilltraxClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class AlbumCoverCache(
    val directory: File,
    private val client: ChilltraxClient = ChilltraxClient()
) {
    private val memoryCache = ConcurrentHashMap<String, ByteArray>()
    private val inFlightMutex = Mutex()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    init {
        directory.mkdirs()
    }

    val cachedFileCount: Int
        get() = directory.listFiles()?.count { it.isFile } ?: 0

    val cachedFilesSizeBytes: Long
        get() = directory.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun cachedBytes(urlString: String?): ByteArray? {
        val url = urlString?.takeIf { it.isNotBlank() } ?: return null
        val key = cacheKey(url)
        memoryCache[key]?.let { return it }

        val file = fileForKey(key)
        if (!file.exists()) return null
        val bytes = runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        memoryCache[key] = bytes
        return bytes
    }

    fun isCachedOnDisk(urlString: String?): Boolean {
        val url = urlString?.takeIf { it.isNotBlank() } ?: return false
        return fileForKey(cacheKey(url)).exists()
    }

    suspend fun cacheCover(urlString: String?): ByteArray? {
        val url = urlString?.takeIf { it.isNotBlank() } ?: return null
        cachedBytes(url)?.let { return it }

        return inFlightMutex.withLock {
            cachedBytes(url)?.let { return@withLock it }
            val bytes = runCatching { client.fetchRawBytes(url) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: return@withLock null

            withContext(Dispatchers.IO) {
                directory.mkdirs()
                val key = cacheKey(url)
                fileForKey(key).writeBytes(bytes)
                memoryCache[key] = bytes
            }
            _revision.value += 1
            bytes
        }
    }

    fun storeBytes(urlString: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        directory.mkdirs()
        val key = cacheKey(urlString)
        fileForKey(key).writeBytes(bytes)
        memoryCache[key] = bytes
        _revision.value += 1
    }

    fun clearAll() {
        memoryCache.clear()
        directory.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
        directory.mkdirs()
        _revision.value += 1
    }

    private fun fileForKey(key: String): File = File(directory, "$key.img")

    companion object {
        fun cacheKey(urlString: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(urlString.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
