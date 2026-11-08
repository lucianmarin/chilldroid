package com.lucianmarin.trax.audio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lucianmarin.trax.R
import com.lucianmarin.trax.model.RadioStation
import com.lucianmarin.trax.model.SongMetadata

data class SongNotificationPayload(
    val title: String,
    val subtitle: String,
    val body: String
)

class SongNotificationManager(
    private val context: Context? = null
) {
    private var lastNotifiedDedupKey: String? = null

    init {
        createChannelIfNeeded()
    }

    fun notifySongChangeIfNeeded(
        song: SongMetadata,
        station: RadioStation = RadioStation.CHILLTRAX,
        isEnabled: Boolean
    ) {
        if (!isEnabled || !song.isMeaningfulTrack) return
        val key = song.dedupKey
        if (key == lastNotifiedDedupKey) return
        lastNotifiedDedupKey = key

        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val payload = makeNotificationContent(song = song, station = station)
        runCatching {
            val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note_2)
                .setContentTitle(payload.title)
                .setSubText(payload.subtitle.takeIf { it.isNotEmpty() })
                .setContentText(payload.body)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createChannelIfNeeded() {
        val ctx = context ?: return
        runCatching {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Now Playing Track Alerts",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications when a new song starts playing on Chilltrax or Radio Danz"
            }
            val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "chilltrax_now_playing"
        const val NOTIFICATION_ID = 1001

        fun makeNotificationContent(
            song: SongMetadata,
            station: RadioStation
        ): SongNotificationPayload {
            val title = song.title.ifEmpty { station.displayName }
            val subtitle = buildList {
                if (song.artist.isNotEmpty()) add(song.artist)
                if (song.albumOrMix.isNotEmpty()) add(song.albumOrMix)
            }.joinToString(" — ")
            val body = "Now playing on ${station.displayName}"
            return SongNotificationPayload(
                title = title,
                subtitle = subtitle,
                body = body
            )
        }
    }
}
