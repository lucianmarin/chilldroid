package com.lucianmarin.trax

import android.app.Application
import com.lucianmarin.trax.audio.RadioPlayer
import com.lucianmarin.trax.audio.SongNotificationManager
import com.lucianmarin.trax.network.ChilltraxClient
import com.lucianmarin.trax.state.AppState
import com.lucianmarin.trax.state.SharedPreferencesStore
import com.lucianmarin.trax.storage.AlbumCoverCache
import com.lucianmarin.trax.storage.CompressedHistoryStore
import java.io.File

class ChilltraxApplication : Application() {
    lateinit var appState: AppState
        private set

    override fun onCreate() {
        super.onCreate()
        val storageDir = File(filesDir, "Chilltrax")
        val prefs = getSharedPreferences("chilltrax_prefs", MODE_PRIVATE)
        appState = AppState(
            radioPlayer = RadioPlayer(context = applicationContext),
            client = ChilltraxClient(),
            historyStore = CompressedHistoryStore(file = File(storageDir, "history.tsv.gz")),
            coverCache = AlbumCoverCache(directory = File(storageDir, "Covers")),
            notificationManager = SongNotificationManager(applicationContext),
            preferences = SharedPreferencesStore(prefs)
        )
    }
}
