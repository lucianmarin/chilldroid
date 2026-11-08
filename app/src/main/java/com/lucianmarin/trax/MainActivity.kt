package com.lucianmarin.trax

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.lucianmarin.trax.ui.components.CompactPlayerScreen
import com.lucianmarin.trax.ui.components.SettingsScreen
import com.lucianmarin.trax.ui.theme.ChilldroidAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()

        val app = application as ChilldroidApplication
        val appState = app.appState

        setContent {
            ChilldroidAppTheme {
                val isSettingsVisible by appState.isSettingsVisibleFlow.collectAsState()

                BackHandler(enabled = isSettingsVisible) {
                    appState.closeSettings()
                }

                if (isSettingsVisible) {
                    SettingsScreen(
                        appState = appState,
                        onClose = { appState.closeSettings() }
                    )
                } else {
                    CompactPlayerScreen(
                        appState = appState,
                        onCopyText = { text -> copyToSystemClipboard(text) },
                        onOpenURL = { url -> openExternalURL(url) }
                    )
                }
            }
        }
    }

    private fun copyToSystemClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText("Chilldroid Song", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun openExternalURL(urlString: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
}
