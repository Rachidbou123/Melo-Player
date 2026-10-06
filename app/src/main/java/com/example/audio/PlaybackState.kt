package com.example.audio

import com.example.data.model.Song

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class VisualizerMode(val displayName: String) {
    SPECTRUM("Spectrum"),
    WAVEFORM("Waveform"),
    STEREO_VU("Stereo Meter"),
    OFF("Hidden")
}

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val visualizerMode: VisualizerMode = VisualizerMode.SPECTRUM,
    val isVisualizerVisible: Boolean = false,
    val equalizerPreset: String = "Flat",
    val bassBoostEnabled: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val gaplessEnabled: Boolean = true,
    val sleepTimerMinutes: Int = 0,
    val sleepTimerRemainingSeconds: Long = 0L,
    val isSleepTimerActive: Boolean = false
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val positionFormatted: String
        get() {
            val totalSeconds = (currentPositionMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val remainingFormatted: String
        get() {
            val remainingMs = (durationMs - currentPositionMs).coerceAtLeast(0)
            val totalSeconds = remainingMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "-%d:%02d".format(minutes, seconds)
        }

    val sleepTimerFormatted: String
        get() {
            val mins = sleepTimerRemainingSeconds / 60
            val secs = sleepTimerRemainingSeconds % 60
            return "%d:%02d".format(mins, secs)
        }
}
