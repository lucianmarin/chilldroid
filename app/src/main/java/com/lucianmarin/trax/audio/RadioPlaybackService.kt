package com.lucianmarin.trax.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.lucianmarin.trax.ChilltraxApplication
import com.lucianmarin.trax.MainActivity
import com.lucianmarin.trax.R
import com.lucianmarin.trax.model.RadioStation
import com.lucianmarin.trax.model.SongMetadata
import com.lucianmarin.trax.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Snapshot of media playback state and metadata exposed to Android's MediaSession
 * and system media controls (notification shade, lock screen, Bluetooth, headsets).
 */
data class MediaPlaybackSnapshot(
    val status: PlaybackStatus,
    val isPlaying: Boolean,
    val station: RadioStation,
    val song: SongMetadata,
    val playbackStateCode: Int,
    val playbackSpeed: Float,
    val supportedActions: Long,
    val title: String,
    val artist: String,
    val album: String,
    val stationSubtitle: String
)

/**
 * Foreground media playback service that keeps Chilltrax streaming in the background
 * and integrates with Android system media controls (`MediaSession` + `Notification.MediaStyle`).
 */
class RadioPlaybackService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateObserverJob: Job? = null
    private var mediaSession: MediaSession? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var isForegroundStarted: Boolean = false
    private var noisyReceiverRegistered: Boolean = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                appStateOrNull()?.let { state ->
                    if (state.radioPlayer.isPlaying) {
                        state.radioPlayer.pause()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initWifiLock()
        initMediaSession()
        observeAppState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = appStateOrNull()
        val action = intent?.action
        if (state != null && action != null) {
            handleServiceAction(action, state)
        }
        state?.let { updateMediaSessionAndNotification(it) }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val state = appStateOrNull()
        if (state == null || !state.radioPlayer.isPlaying) {
            stopForegroundAndSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unregisterNoisyReceiver()
        releaseWifiLock()
        stateObserverJob?.cancel()
        serviceScope.cancel()
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun appStateOrNull(): AppState? {
        return (application as? ChilltraxApplication)?.appState
    }

    private fun initWifiLock() {
        runCatching {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager?.createWifiLock(lockMode, "Trax:RadioStreamWifiLock")?.apply {
                setReferenceCounted(false)
            }
        }
    }

    private fun updateWifiLockAndNoisyReceiver(isPlaying: Boolean) {
        if (isPlaying) {
            runCatching {
                if (wifiLock?.isHeld == false) {
                    wifiLock?.acquire()
                }
            }
            registerNoisyReceiver()
        } else {
            releaseWifiLock()
            unregisterNoisyReceiver()
        }
    }

    private fun releaseWifiLock() {
        runCatching {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        runCatching {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(becomingNoisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(becomingNoisyReceiver, filter)
            }
            noisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching {
            unregisterReceiver(becomingNoisyReceiver)
        }
        noisyReceiverRegistered = false
    }

    private fun initMediaSession() {
        runCatching {
            val session = MediaSession(this, MEDIA_SESSION_TAG)
            session.setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    appStateOrNull()?.let { handleMediaAction(ACTION_PLAY, it) }
                }

                override fun onPause() {
                    appStateOrNull()?.let { handleMediaAction(ACTION_PAUSE, it) }
                }

                override fun onStop() {
                    appStateOrNull()?.let { handleMediaAction(ACTION_STOP, it) }
                    stopForegroundAndSelf()
                }

                override fun onSkipToNext() {
                    appStateOrNull()?.let { handleMediaAction(ACTION_NEXT_STATION, it) }
                }

                override fun onSkipToPrevious() {
                    appStateOrNull()?.let { handleMediaAction(ACTION_PREVIOUS_STATION, it) }
                }
            })
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val sessionActivityPi = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            session.setSessionActivity(sessionActivityPi)
            session.isActive = true
            mediaSession = session
        }
    }

    private fun observeAppState() {
        val state = appStateOrNull() ?: return
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            combine(
                state.radioPlayer.statusFlow,
                state.radioPlayer.isPlayingFlow,
                state.selectedStationFlow,
                state.currentSongFlow,
                state.coverCache.revision
            ) { status, isPlaying, station, song, _ ->
                buildSnapshot(
                    status = status,
                    isPlaying = isPlaying,
                    station = station,
                    song = song
                )
            }.collect {
                updateMediaSessionAndNotification(state)
            }
        }
    }

    private fun handleServiceAction(action: String, state: AppState) {
        if (action == ACTION_STOP) {
            handleMediaAction(action, state)
            stopForegroundAndSelf()
        } else {
            handleMediaAction(action, state)
        }
    }

    private fun updateMediaSessionAndNotification(state: AppState) {
        val snapshot = buildSnapshot(
            status = state.radioPlayer.status,
            isPlaying = state.radioPlayer.isPlaying,
            station = state.selectedStation,
            song = state.currentSong
        )

        updateWifiLockAndNoisyReceiver(
            isPlaying = snapshot.isPlaying || snapshot.status == PlaybackStatus.CONNECTING
        )

        val session = mediaSession ?: return
        session.isActive = snapshot.status != PlaybackStatus.IDLE

        val playbackState = PlaybackState.Builder()
            .setActions(snapshot.supportedActions)
            .setState(
                snapshot.playbackStateCode,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                snapshot.playbackSpeed
            )
            .build()
        session.setPlaybackState(playbackState)

        val artworkBitmap = loadCachedArtworkBitmap(state)
        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, snapshot.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, snapshot.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, snapshot.album)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, snapshot.title)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, snapshot.artist)
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, snapshot.stationSubtitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, -1L)

        if (artworkBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artworkBitmap)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artworkBitmap)
        }
        session.setMetadata(metadataBuilder.build())

        if (snapshot.status == PlaybackStatus.IDLE && isForegroundStarted) {
            stopForegroundAndSelf()
            return
        }

        if (snapshot.status != PlaybackStatus.IDLE) {
            val notification = buildMediaNotification(snapshot, session, artworkBitmap)
            startOrUpdateForeground(notification, snapshot.isPlaying || snapshot.status == PlaybackStatus.CONNECTING)
        }
    }

    private fun loadCachedArtworkBitmap(state: AppState): Bitmap? {
        if (!state.fetchAlbumCoverFromChilltrax) return null
        val bytes = state.coverCache.cachedBytes(state.currentSong.coverURL) ?: return null
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun buildMediaNotification(
        snapshot: MediaPlaybackSnapshot,
        session: MediaSession,
        artworkBitmap: Bitmap?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_previous),
            "Switch to ${snapshot.station.toggledStation().displayName}",
            serviceActionPendingIntent(ACTION_PREVIOUS_STATION, 10)
        ).build()

        val playPauseAction = if (snapshot.isPlaying || snapshot.status == PlaybackStatus.CONNECTING) {
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                "Pause",
                serviceActionPendingIntent(ACTION_PAUSE, 11)
            ).build()
        } else {
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_play),
                "Play",
                serviceActionPendingIntent(ACTION_PLAY, 12)
            ).build()
        }

        val nextAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_next),
            "Switch to ${snapshot.station.toggledStation().displayName}",
            serviceActionPendingIntent(ACTION_NEXT_STATION, 13)
        ).build()

        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            serviceActionPendingIntent(ACTION_STOP, 14)
        ).build()

        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = Notification.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note_2)
            .setContentTitle(snapshot.title)
            .setContentText(snapshot.artist)
            .setSubText(snapshot.stationSubtitle)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(serviceActionPendingIntent(ACTION_STOP, 15))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(snapshot.isPlaying || snapshot.status == PlaybackStatus.CONNECTING)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(mediaStyle)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)

        if (artworkBitmap != null) {
            builder.setLargeIcon(artworkBitmap)
        }

        return builder.build()
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RadioPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startOrUpdateForeground(notification: Notification, activelyStreaming: Boolean) {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (!isForegroundStarted && activelyStreaming) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        PLAYBACK_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(PLAYBACK_NOTIFICATION_ID, notification)
                }
                isForegroundStarted = true
            } else {
                nm?.notify(PLAYBACK_NOTIFICATION_ID, notification)
                if (!activelyStreaming && isForegroundStarted) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                    isForegroundStarted = false
                }
            }
        }
    }

    private fun stopForegroundAndSelf() {
        runCatching {
            if (isForegroundStarted) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundStarted = false
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(PLAYBACK_NOTIFICATION_ID)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        runCatching {
            val channel = NotificationChannel(
                PLAYBACK_CHANNEL_ID,
                "Radio Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls and background streaming status for Chilltrax and Radio Danz"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val MEDIA_SESSION_TAG = "TraxMediaSession"
        const val PLAYBACK_CHANNEL_ID = "trax_media_playback"
        const val PLAYBACK_NOTIFICATION_ID = 1002

        const val ACTION_REFRESH = "com.lucianmarin.trax.action.REFRESH"
        const val ACTION_PLAY = "com.lucianmarin.trax.action.PLAY"
        const val ACTION_PAUSE = "com.lucianmarin.trax.action.PAUSE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.lucianmarin.trax.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_STOP = "com.lucianmarin.trax.action.STOP"
        const val ACTION_NEXT_STATION = "com.lucianmarin.trax.action.NEXT_STATION"
        const val ACTION_PREVIOUS_STATION = "com.lucianmarin.trax.action.PREVIOUS_STATION"

        const val SUPPORTED_MEDIA_ACTIONS: Long =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS

        fun mapPlaybackStateCode(status: PlaybackStatus, isPlaying: Boolean): Int {
            return when (status) {
                PlaybackStatus.PLAYING -> PlaybackState.STATE_PLAYING
                PlaybackStatus.CONNECTING -> PlaybackState.STATE_BUFFERING
                PlaybackStatus.PAUSED -> PlaybackState.STATE_PAUSED
                PlaybackStatus.ERROR -> PlaybackState.STATE_ERROR
                PlaybackStatus.IDLE -> if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_STOPPED
            }
        }

        fun buildSnapshot(
            status: PlaybackStatus,
            isPlaying: Boolean,
            station: RadioStation,
            song: SongMetadata
        ): MediaPlaybackSnapshot {
            val stateCode = mapPlaybackStateCode(status = status, isPlaying = isPlaying)
            val meaningful = song.isMeaningfulTrack
            val title = if (meaningful && song.title.isNotBlank()) {
                song.title
            } else {
                station.displayName
            }
            val artist = when {
                meaningful && song.artist.isNotBlank() && song.albumOrMix.isNotBlank() ->
                    "${song.artist} — ${song.albumOrMix}"
                meaningful && song.artist.isNotBlank() ->
                    song.artist
                status == PlaybackStatus.CONNECTING ->
                    "Connecting to ${station.displayName}…"
                status == PlaybackStatus.PAUSED ->
                    "${station.displayName} (Paused)"
                status == PlaybackStatus.ERROR ->
                    "Stream error on ${station.displayName}"
                else ->
                    station.tagline
            }
            val album = if (meaningful && song.albumOrMix.isNotBlank()) {
                song.albumOrMix
            } else {
                station.displayName
            }

            return MediaPlaybackSnapshot(
                status = status,
                isPlaying = isPlaying,
                station = station,
                song = song,
                playbackStateCode = stateCode,
                playbackSpeed = if (isPlaying) 1.0f else 0.0f,
                supportedActions = SUPPORTED_MEDIA_ACTIONS,
                title = title,
                artist = artist,
                album = album,
                stationSubtitle = "${station.badgeLabel} • ${station.displayName}"
            )
        }

        fun handleMediaAction(action: String, appState: AppState): Boolean {
            return when (action) {
                ACTION_PLAY -> {
                    if (!appState.radioPlayer.isPlaying) {
                        appState.playSelectedStation()
                    }
                    true
                }
                ACTION_PAUSE -> {
                    appState.radioPlayer.pause()
                    true
                }
                ACTION_TOGGLE_PLAY_PAUSE -> {
                    appState.togglePlayPause()
                    true
                }
                ACTION_STOP -> {
                    appState.radioPlayer.stop()
                    true
                }
                ACTION_NEXT_STATION, ACTION_PREVIOUS_STATION -> {
                    val shouldAutoPlay = appState.radioPlayer.isPlaying ||
                        appState.radioPlayer.status == PlaybackStatus.CONNECTING
                    appState.switchStation(autoPlayIfCurrentlyPlaying = shouldAutoPlay)
                    true
                }
                else -> false
            }
        }

        fun startOrUpdate(context: Context, action: String = ACTION_REFRESH) {
            runCatching {
                val appCtx = context.applicationContext
                val intent = Intent(appCtx, RadioPlaybackService::class.java).apply {
                    this.action = action
                }
                if (action == ACTION_PLAY || action == ACTION_REFRESH) {
                    ContextCompat.startForegroundService(appCtx, intent)
                } else {
                    appCtx.startService(intent)
                }
            }
        }
    }
}
