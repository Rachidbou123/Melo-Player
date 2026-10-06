package com.example.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

class VisualizerDataProvider(private val scope: CoroutineScope) {

    private val _waveformFloats = MutableStateFlow(FloatArray(48) { 0f })
    val waveformFloats: StateFlow<FloatArray> = _waveformFloats.asStateFlow()

    private val _spectrumBars = MutableStateFlow(FloatArray(20) { 0.04f })
    val spectrumBars: StateFlow<FloatArray> = _spectrumBars.asStateFlow()

    private var androidVisualizer: Visualizer? = null
    private var simulationJob: Job? = null
    private var phase = 0f

    fun attachSession(audioSessionId: Int) {
        releaseVisualizer()
        if (audioSessionId > 0) {
            try {
                androidVisualizer = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(128)
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                                waveform?.let { data ->
                                    val floats = FloatArray(48)
                                    val step = (data.size / 48).coerceAtLeast(1)
                                    for (i in 0 until 48) {
                                        val idx = (i * step).coerceIn(0, data.lastIndex)
                                        val raw = (data[idx].toInt() and 0xFF) - 128
                                        floats[i] = (raw / 128f).coerceIn(-1f, 1f)
                                    }
                                    _waveformFloats.value = floats
                                }
                            }

                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                                fft?.let { data ->
                                    val bars = FloatArray(20)
                                    val n = (data.size / 2).coerceAtMost(20)
                                    for (i in 0 until n) {
                                        val r = data[i * 2].toFloat()
                                        val im = data[i * 2 + 1].toFloat()
                                        val mag = kotlin.math.sqrt(r * r + im * im)
                                        bars[i] = (mag / 80f).coerceIn(0.04f, 1f)
                                    }
                                    _spectrumBars.value = bars
                                }
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        true,
                        true
                    )
                    enabled = true
                }
            } catch (e: Exception) {
                Log.w("Visualizer", "Native visualizer restricted, using smooth fallback", e)
                startSmoothSimulation()
            }
        } else {
            startSmoothSimulation()
        }
    }

    fun startSmoothSimulation() {
        if (simulationJob?.isActive == true) return
        simulationJob = scope.launch(Dispatchers.Default) {
            val barsCount = 20
            val waveCount = 48
            while (isActive) {
                phase += 0.08f
                val newBars = FloatArray(barsCount)
                for (i in 0 until barsCount) {
                    val freq = (i + 1) * 0.35f
                    val v = abs(sin(phase * freq) * 0.5f + sin(phase * 0.5f + i * 0.3f) * 0.35f)
                    newBars[i] = v.coerceIn(0.06f, 0.92f)
                }
                _spectrumBars.value = newBars

                val newWave = FloatArray(waveCount)
                for (i in 0 until waveCount) {
                    val p = i.toFloat() / waveCount
                    newWave[i] = (sin(p * 8f + phase) * 0.5f + sin(p * 16f - phase) * 0.25f).coerceIn(-1f, 1f)
                }
                _waveformFloats.value = newWave

                delay(33) // ~30 fps update
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _spectrumBars.value = FloatArray(20) { 0.04f }
        _waveformFloats.value = FloatArray(48) { 0f }
    }

    fun release() {
        stopSimulation()
        releaseVisualizer()
    }

    private fun releaseVisualizer() {
        try {
            androidVisualizer?.enabled = false
            androidVisualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            androidVisualizer = null
        }
    }
}
