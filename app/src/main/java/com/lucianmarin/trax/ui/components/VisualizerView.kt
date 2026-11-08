package com.lucianmarin.trax.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.lucianmarin.trax.model.VisualizerMode
import com.lucianmarin.trax.ui.theme.ChilltraxTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object VisualizerMetrics {
    const val VERTICAL_INSET = 4f
    private const val BAR_GAP = 3f
    private const val MIN_BAR_HEIGHT = 2f

    fun spectrumBarColor(normalizedHeight: Float, isDark: Boolean): Color {
        return when {
            normalizedHeight >= 0.72f -> ChilltraxTheme.AccentRed
            normalizedHeight >= 0.28f -> ChilltraxTheme.visualizerContrastColor(isDark)
            else -> ChilltraxTheme.VisualizerGray
        }
    }

    fun spectrumTrailColor(barHeight: Float, trailHeight: Float, isDark: Boolean): Color {
        val peak = max(barHeight, trailHeight)
        return when {
            peak >= 0.72f -> ChilltraxTheme.AccentRed
            peak >= 0.28f -> ChilltraxTheme.visualizerContrastColor(isDark)
            else -> ChilltraxTheme.VisualizerGray
        }
    }

    fun spectrumBarHeights(levels: List<Float>, canvasWidth: Float, canvasHeight: Float): List<Float> {
        val count = max(1, levels.size)
        val totalGaps = BAR_GAP * (count - 1).toFloat()
        val barWidth = max(2f, (canvasWidth - totalGaps) / count.toFloat())
        val actualCount = min(count, max(1, ((canvasWidth + BAR_GAP) / (barWidth + BAR_GAP)).toInt()))
        val maxBarHeight = max(MIN_BAR_HEIGHT, canvasHeight - VERTICAL_INSET * 2f)

        return (0 until actualCount).map { idx ->
            val rawLevel = levels[idx % levels.size].coerceIn(0.03f, 1.0f)
            max(MIN_BAR_HEIGHT, rawLevel * maxBarHeight)
        }
    }

    fun spectrumAverageHeight(levels: List<Float>, canvasWidth: Float, canvasHeight: Float): Float {
        val heights = spectrumBarHeights(levels = levels, canvasWidth = canvasWidth, canvasHeight = canvasHeight)
        if (heights.isEmpty()) return 0f
        return heights.sum() / heights.size.toFloat()
    }

    fun waveformEnvelope(levels: List<Float>, normalizedX: Float, phase: Float): Float {
        if (levels.isEmpty()) return 0f
        val scaledIndex = normalizedX * max(1, levels.size - 1).toFloat()
        val lowIndex = scaledIndex.toInt().coerceIn(0, levels.size - 1)
        val highIndex = min(levels.size - 1, lowIndex + 1)
        val frac = scaledIndex - lowIndex.toFloat()
        val interpolatedLevel = levels[lowIndex] * (1f - frac) + levels[highIndex] * frac
        val peakBoost = (levels.maxOrNull() ?: 0f) * 0.12f
        val energy = min(1.0f, interpolatedLevel * 0.92f + peakBoost)

        val window = sin(normalizedX * Math.PI).toFloat()
        val carrier1 = sin(normalizedX * Math.PI * 5.0 + phase.toDouble() * 1.4).toFloat() * 0.58f
        val carrier2 = cos(normalizedX * Math.PI * 11.0 - phase.toDouble() * 2.1).toFloat() * 0.36f
        val carrier3 = sin(normalizedX * Math.PI * 19.0 + phase.toDouble() * 0.85).toFloat() * 0.24f

        val rawWave = (carrier1 + carrier2 + carrier3) * window
        return (rawWave * energy).coerceIn(-1.0f, 1.0f)
    }

    fun waveformPeakVerticalSpan(levels: List<Float>, phase: Float, canvasHeight: Float): Float {
        val maxHalfHeight = max(2f, (canvasHeight - VERTICAL_INSET * 2f) * 0.5f)
        val steps = 110
        var maxSpan = 0f
        for (s in 0..steps) {
            val t = s.toFloat() / steps.toFloat()
            val env = abs(waveformEnvelope(levels = levels, normalizedX = t, phase = phase))
            val halfExtent = max(0.75f, env * maxHalfHeight)
            val span = min(canvasHeight - VERTICAL_INSET * 2f, halfExtent * 2f)
            if (span > maxSpan) maxSpan = span
        }
        return maxSpan
    }
}

@Composable
fun VisualizerView(
    mode: VisualizerMode,
    levels: List<Float>,
    trailLevels: List<Float> = emptyList(),
    phase: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    if (mode == VisualizerMode.OFF) return
    val isDark = isSystemInDarkTheme()
    val backgroundColor = ChilltraxTheme.visualizerBackground(isDark)

    Canvas(
        modifier = modifier.background(backgroundColor)
    ) {
        when (mode) {
            VisualizerMode.OFF -> Unit
            VisualizerMode.SPECTRUM -> drawSpectrumBars(
                levels = levels,
                trailLevels = trailLevels,
                isDark = isDark
            )
            VisualizerMode.WAVEFORM -> drawWaveform(
                levels = levels,
                phase = phase,
                isPlaying = isPlaying,
                isDark = isDark
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectrumBars(
    levels: List<Float>,
    trailLevels: List<Float>,
    isDark: Boolean
) {
    val barHeights = VisualizerMetrics.spectrumBarHeights(
        levels = levels,
        canvasWidth = size.width,
        canvasHeight = size.height
    )
    if (barHeights.isEmpty()) return

    val trailHeights = if (trailLevels.isEmpty()) {
        barHeights
    } else {
        VisualizerMetrics.spectrumBarHeights(
            levels = trailLevels,
            canvasWidth = size.width,
            canvasHeight = size.height
        )
    }

    val actualCount = barHeights.size
    val barGap = 3f
    val totalGaps = barGap * (actualCount - 1).toFloat()
    val barWidth = max(2f, (size.width - totalGaps) / actualCount.toFloat())
    val totalDrawnWidth = actualCount.toFloat() * barWidth + totalGaps
    val startX = max(0f, (size.width - totalDrawnWidth) * 0.5f)
    val maxBarHeight = max(2f, size.height - VisualizerMetrics.VERTICAL_INSET * 2f)
    val bottomY = size.height - VisualizerMetrics.VERTICAL_INSET
    val cornerRadius = CornerRadius(min(2.5f, barWidth * 0.45f), min(2.5f, barWidth * 0.45f))

    for (idx in 0 until actualCount) {
        val barHeight = barHeights[idx]
        val trailHeight = max(barHeight, if (idx < trailHeights.size) trailHeights[idx] else barHeight)
        val x = startX + idx.toFloat() * (barWidth + barGap)
        val y = bottomY - barHeight
        val normalizedBarHeight = (barHeight / maxBarHeight).coerceIn(0f, 1f)
        val normalizedTrailHeight = (trailHeight / maxBarHeight).coerceIn(0f, 1f)

        if (trailHeight > barHeight + 0.75f) {
            val trailY = bottomY - trailHeight
            val trailSpan = max(2f, trailHeight - barHeight + 2f)
            val trailColor = VisualizerMetrics.spectrumTrailColor(
                barHeight = normalizedBarHeight,
                trailHeight = normalizedTrailHeight,
                isDark = isDark
            )
            drawRoundRect(
                color = trailColor.copy(alpha = 0.28f),
                topLeft = Offset(x, trailY),
                size = Size(barWidth, trailSpan),
                cornerRadius = cornerRadius
            )

            val capHeight = min(2.5f, trailHeight - barHeight)
            drawRoundRect(
                color = trailColor.copy(alpha = 0.65f),
                topLeft = Offset(x, trailY),
                size = Size(barWidth, capHeight),
                cornerRadius = cornerRadius
            )
        }

        val barColor = VisualizerMetrics.spectrumBarColor(
            normalizedHeight = normalizedBarHeight,
            isDark = isDark
        )
        drawRoundRect(
            color = barColor,
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = cornerRadius
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveform(
    levels: List<Float>,
    phase: Float,
    isPlaying: Boolean,
    isDark: Boolean
) {
    val midY = size.height * 0.5f
    val maxHalfHeight = max(2f, (size.height - VisualizerMetrics.VERTICAL_INSET * 2f) * 0.5f)
    val steps = 110

    data class WaveLayer(
        val scale: Float,
        val phaseShift: Float,
        val color: Color
    )

    val layers = listOf(
        WaveLayer(
            scale = 1.00f,
            phaseShift = 0.0f,
            color = ChilltraxTheme.VisualizerGray.copy(alpha = if (isPlaying) 0.55f else 0.35f)
        ),
        WaveLayer(
            scale = 0.68f,
            phaseShift = 0.65f,
            color = ChilltraxTheme.visualizerContrastColor(isDark).copy(alpha = if (isPlaying) 0.88f else 0.50f)
        ),
        WaveLayer(
            scale = 0.38f,
            phaseShift = 1.30f,
            color = ChilltraxTheme.AccentRed
        )
    )

    for (layer in layers) {
        val ribbon = Path()
        val upperPoints = ArrayList<Offset>(steps + 1)
        val lowerPoints = ArrayList<Offset>(steps + 1)

        for (s in 0..steps) {
            val t = s.toFloat() / steps.toFloat()
            val x = t * size.width
            val env = abs(
                VisualizerMetrics.waveformEnvelope(
                    levels = levels,
                    normalizedX = t,
                    phase = phase + layer.phaseShift
                )
            )
            val halfExtent = max(0.75f, env * maxHalfHeight * layer.scale)
            upperPoints.add(Offset(x, max(VisualizerMetrics.VERTICAL_INSET, midY - halfExtent)))
            lowerPoints.add(Offset(x, min(size.height - VisualizerMetrics.VERTICAL_INSET, midY + halfExtent)))
        }

        if (upperPoints.isNotEmpty()) {
            ribbon.moveTo(upperPoints.first().x, upperPoints.first().y)
            for (pt in upperPoints.drop(1)) {
                ribbon.lineTo(pt.x, pt.y)
            }
            for (pt in lowerPoints.asReversed()) {
                ribbon.lineTo(pt.x, pt.y)
            }
            ribbon.close()
            drawPath(path = ribbon, color = layer.color)
        }
    }
}
