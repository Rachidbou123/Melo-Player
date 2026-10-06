package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.audio.VisualizerDataProvider
import com.example.audio.VisualizerMode

@Composable
fun VisualizerCanvas(
    dataProvider: VisualizerDataProvider,
    mode: VisualizerMode,
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val waveform by dataProvider.waveformFloats.collectAsState()
    val spectrum by dataProvider.spectrumBars.collectAsState()

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        when (mode) {
            VisualizerMode.SPECTRUM -> {
                val numBars = spectrum.size.coerceAtLeast(1)
                val spacing = 4f
                val totalSpacing = spacing * (numBars - 1)
                val barWidth = ((width - totalSpacing) / numBars).coerceAtLeast(2f)

                for (i in 0 until numBars) {
                    val magnitude = spectrum[i].coerceIn(0.04f, 1f)
                    val barHeight = height * magnitude * 0.95f
                    val left = i * (barWidth + spacing)
                    val top = height - barHeight

                    drawRoundRect(
                        color = tintColor.copy(alpha = 0.85f),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }

            VisualizerMode.WAVEFORM -> {
                val count = waveform.size
                if (count > 1) {
                    val path = Path()
                    val centerY = height / 2f
                    val dx = width / (count - 1)

                    path.moveTo(0f, centerY + waveform[0] * (height * 0.4f))
                    for (i in 1 until count) {
                        val x = i * dx
                        val y = centerY + waveform[i] * (height * 0.4f)
                        path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = tintColor.copy(alpha = 0.9f),
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )
                }
            }

            VisualizerMode.STEREO_VU -> {
                val leftLevel = spectrum.take(10).average().toFloat().coerceIn(0.05f, 1f)
                val rightLevel = spectrum.drop(10).average().toFloat().coerceIn(0.05f, 1f)

                val barH = (height / 2f) - 6f
                // Left channel bar
                drawRoundRect(
                    color = tintColor.copy(alpha = 0.75f),
                    topLeft = Offset(0f, 2f),
                    size = Size(width * leftLevel, barH),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                // Right channel bar
                drawRoundRect(
                    color = tintColor.copy(alpha = 0.75f),
                    topLeft = Offset(0f, height / 2f + 4f),
                    size = Size(width * rightLevel, barH),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }

            VisualizerMode.OFF -> {
                // Hidden
            }
        }
    }
}
