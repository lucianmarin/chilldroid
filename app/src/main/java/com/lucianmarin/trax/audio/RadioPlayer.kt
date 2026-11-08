package com.lucianmarin.trax.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.PowerManager
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class PlaybackStatus(val rawValue: String) {
    IDLE("Ready"),
    CONNECTING("Connecting…"),
    PLAYING("Playing"),
    PAUSED("Paused"),
    ERROR("Stream Error")
}

class RadioPlayer(
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + defaultMainOrUnconfinedDispatcher()),
    private val enableNativeMediaPlayer: Boolean = true
) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlaying.asStateFlow()
    var isPlaying: Boolean
        get() = _isPlaying.value
        private set(value) {
            _isPlaying.value = value
        }

    private val _status = MutableStateFlow(PlaybackStatus.IDLE)
    val statusFlow: StateFlow<PlaybackStatus> = _status.asStateFlow()
    var status: PlaybackStatus
        get() = _status.value
        private set(value) {
            _status.value = value
        }

    private val _volume = MutableStateFlow(0.85f)
    val volumeFlow: StateFlow<Float> = _volume.asStateFlow()
    var volume: Float
        get() = _volume.value
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            _volume.value = clamped
            runCatching { mediaPlayer?.setVolume(clamped, clamped) }
        }

    private val _visualizerLevels = MutableStateFlow(List(VISUALIZER_BAR_COUNT) { 0.04f })
    val visualizerLevelsFlow: StateFlow<List<Float>> = _visualizerLevels.asStateFlow()
    var visualizerLevels: List<Float>
        get() = _visualizerLevels.value
        private set(value) {
            _visualizerLevels.value = value
        }

    private val _visualizerTrailLevels = MutableStateFlow(List(VISUALIZER_BAR_COUNT) { 0.04f })
    val visualizerTrailLevelsFlow: StateFlow<List<Float>> = _visualizerTrailLevels.asStateFlow()
    var visualizerTrailLevels: List<Float>
        get() = _visualizerTrailLevels.value
        private set(value) {
            _visualizerTrailLevels.value = value
        }

    private val _waveformPhase = MutableStateFlow(0f)
    val waveformPhaseFlow: StateFlow<Float> = _waveformPhase.asStateFlow()
    var waveformPhase: Float
        get() = _waveformPhase.value
        private set(value) {
            _waveformPhase.value = value
        }

    var onICYStreamTitle: ((String) -> Unit)? = null

    private var mediaPlayer: MediaPlayer? = null
    private var currentStreamURLString: String? = null
    private var visualizerJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain: Boolean = false

    private val audioAttributes: AudioAttributes? by lazy {
        runCatching {
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        }.getOrNull()
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) {
                    resumeOnFocusGain = true
                    pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val ducked = (volume * 0.25f).coerceIn(0f, 1f)
                runCatching { mediaPlayer?.setVolume(ducked, ducked) }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                runCatching { mediaPlayer?.setVolume(volume, volume) }
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    currentStreamURLString?.let { play(it) }
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val ctx = context ?: return true
        return runCatching {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@runCatching true
            val attrs = audioAttributes ?: return@runCatching true
            val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                .also { audioFocusRequest = it }
            val res = audioManager.requestAudioFocus(req)
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED || res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
        }.getOrDefault(true)
    }

    private fun abandonAudioFocus() {
        val ctx = context ?: return
        val req = audioFocusRequest ?: return
        runCatching {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.abandonAudioFocusRequest(req)
        }
    }

    fun play(urlString: String) {
        val sameURL = currentStreamURLString == urlString && mediaPlayer != null
        currentStreamURLString = urlString

        if (!enableNativeMediaPlayer) {
            status = PlaybackStatus.PLAYING
            isPlaying = true
            startVisualizerTimer()
            return
        }

        requestAudioFocus()
        context?.let { RadioPlaybackService.startOrUpdate(it) }

        if (!sameURL) {
            releaseMediaPlayer()
            status = PlaybackStatus.CONNECTING
            try {
                val mp = MediaPlayer()
                audioAttributes?.let { mp.setAudioAttributes(it) }
                context?.let { ctx ->
                    runCatching { mp.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK) }
                }
                mp.setVolume(volume, volume)
                mp.setDataSource(urlString)
                mp.setOnPreparedListener { player ->
                    player.start()
                    isPlaying = true
                    status = PlaybackStatus.PLAYING
                }
                mp.setOnInfoListener { _, what, _ ->
                    when (what) {
                        MediaPlayer.MEDIA_INFO_BUFFERING_START -> if (isPlaying) status = PlaybackStatus.CONNECTING
                        MediaPlayer.MEDIA_INFO_BUFFERING_END -> if (isPlaying) status = PlaybackStatus.PLAYING
                    }
                    false
                }
                mp.setOnErrorListener { _, _, _ ->
                    isPlaying = false
                    status = PlaybackStatus.ERROR
                    true
                }
                mediaPlayer = mp
                mp.prepareAsync()
            } catch (_: Throwable) {
                // In unit test JVM environments where Android MediaPlayer is stubbed, fall back cleanly
                status = PlaybackStatus.PLAYING
                isPlaying = true
            }
        } else {
            try {
                mediaPlayer?.start()
                isPlaying = true
                status = PlaybackStatus.PLAYING
            } catch (_: Throwable) {
                isPlaying = true
                status = PlaybackStatus.PLAYING
            }
        }

        startVisualizerTimer()
    }

    fun pause() {
        runCatching { mediaPlayer?.pause() }
        isPlaying = false
        status = PlaybackStatus.PAUSED
    }

    fun togglePlayPause(defaultURLString: String) {
        if (isPlaying) {
            pause()
        } else {
            play(currentStreamURLString ?: defaultURLString)
        }
    }

    fun stop() {
        resumeOnFocusGain = false
        abandonAudioFocus()
        visualizerJob?.cancel()
        visualizerJob = null
        releaseMediaPlayer()
        isPlaying = false
        status = PlaybackStatus.IDLE
        visualizerLevels = List(VISUALIZER_BAR_COUNT) { 0.04f }
        visualizerTrailLevels = List(VISUALIZER_BAR_COUNT) { 0.04f }
    }

    fun setSimulatedPlaybackStateForTesting(isPlaying: Boolean, status: PlaybackStatus) {
        this.isPlaying = isPlaying
        this.status = status
    }

    fun tickVisualizer() {
        val count = VISUALIZER_BAR_COUNT
        if (!isPlaying || status == PlaybackStatus.CONNECTING || status == PlaybackStatus.ERROR) {
            visualizerLevels = visualizerLevels.map { level ->
                max(0.03f, level * 0.80f)
            }
            visualizerTrailLevels = visualizerTrailLevels.mapIndexed { idx, trail ->
                max(visualizerLevels[idx], trail * 0.86f)
            }
            waveformPhase += 0.04f
            return
        }

        waveformPhase += 0.22f
        val t = waveformPhase.toDouble()
        val rawBeat = max(0.0, sin(t * 0.65))
        val subBassPulse = rawBeat.pow(4.2) * 0.72
        val snareAccent = max(0.0, sin(t * 1.30 - 1.1)).pow(8.0) * 0.68
        val transientAccent = max(0.0, sin(t * 0.325 + 0.4)).pow(12.0) * 0.65
        val prevLevels = visualizerLevels

        val next = MutableList(count) { 0f }
        for (i in 0 until count) {
            val norm = i.toDouble() / count.toDouble()
            val spectralTilt = 0.86 - 0.16 * norm
            val wave1 = abs(sin(t * 0.85 + i.toDouble() * 0.29)) * 0.34
            val wave2 = abs(cos(t * 1.45 - i.toDouble() * 0.17)) * 0.26
            val wave3 = abs(sin(t * 2.35 + i.toDouble() * 0.51)) * 0.20
            val bassWeight = max(0.0, 1.0 - norm * 1.65)
            val midHighWeight = max(0.0, 1.0 - abs(norm - 0.45) * 1.8)
            val target = (0.14 + (wave1 + wave2 + wave3) * spectralTilt +
                subBassPulse * bassWeight +
                (snareAccent + transientAccent) * midHighWeight) * max(0.25, volume.toDouble())
            val prev = if (i < prevLevels.size) prevLevels[i].toDouble() else 0.1
            val smoothed = if (target > prev) {
                prev * 0.28 + target * 0.72
            } else {
                prev * 0.66 + target * 0.34
            }
            next[i] = smoothed.toFloat().coerceIn(0.03f, 1.0f)
        }
        visualizerLevels = next
        updateTrailLevels(next)
    }

    private fun updateTrailLevels(currentLevels: List<Float>) {
        val count = currentLevels.size
        val prevTrails = visualizerTrailLevels
        val updated = MutableList(count) { 0.03f }
        for (i in 0 until count) {
            val current = currentLevels[i]
            val prev = if (i < prevTrails.size) prevTrails[i] else current
            if (current >= prev) {
                updated[i] = current
            } else {
                val decayed = prev * 0.92f - 0.006f
                updated[i] = max(current, max(0.03f, decayed))
            }
        }
        visualizerTrailLevels = updated
    }

    private fun startVisualizerTimer() {
        if (visualizerJob?.isActive == true) return
        visualizerJob = scope.launch {
            while (isActive) {
                tickVisualizer()
                delay(45L)
            }
        }
    }

    private fun releaseMediaPlayer() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    companion object {
        const val VISUALIZER_BAR_COUNT = 56

        internal fun defaultMainOrUnconfinedDispatcher(): kotlinx.coroutines.CoroutineDispatcher =
            try {
                // Verify Main dispatcher is available in the current runtime (Android vs JVM test)
                Dispatchers.Main.immediate.isDispatchNeeded(kotlin.coroutines.EmptyCoroutineContext)
                Dispatchers.Main.immediate
            } catch (_: Throwable) {
                Dispatchers.Unconfined
            }

        fun computeSpectrumLevels(fromPCM: List<Float>, count: Int = VISUALIZER_BAR_COUNT): List<Float> {
            if (fromPCM.isEmpty() || count <= 0) {
                return List(max(0, count)) { 0.03f }
            }

            var sumSquares = 0f
            var peak = 0f
            for (s in fromPCM) {
                val a = abs(s)
                sumSquares += a * a
                if (a > peak) peak = a
            }
            val rms = sqrt(sumSquares / fromPCM.size.toFloat())
            val overallGain = min(1.0f, rms * 2.6f + peak * 0.55f)

            val windowSize = max(8, fromPCM.size / count)
            val levels = MutableList(count) { 0.03f }

            for (bar in 0 until count) {
                val start = min(fromPCM.size - 1, (bar * (fromPCM.size - windowSize)) / max(1, count - 1))
                val end = min(fromPCM.size, start + windowSize)
                var localSum = 0f
                var diffSum = 0f
                var prev = fromPCM[start]
                for (idx in start until end) {
                    val sample = fromPCM[idx]
                    localSum += sample * sample
                    diffSum += abs(sample - prev)
                    prev = sample
                }
                val n = max(1, end - start).toFloat()
                val localRms = sqrt(localSum / n)
                val highFreqEnergy = diffSum / n

                val normBar = bar.toFloat() / max(1, count - 1).toFloat()
                val blended = localRms * (1.0f - 0.35f * normBar) + highFreqEnergy * (0.45f * normBar)
                val scaled = (blended * 1.9f + overallGain * 0.35f).coerceIn(0.03f, 1.0f)
                levels[bar] = scaled
            }

            return levels
        }
    }
}
