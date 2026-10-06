package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPlaybackCompleted: () -> Unit,
    private val onCrossfadeTriggered: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var activePlayer: MediaPlayer? = null
    private var crossfadePlayer: MediaPlayer? = null

    val audioEffectManager = AudioEffectManager(context)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var crossfadeJob: Job? = null
    var crossfadeDurationSeconds: Int = 0 // 0 = disabled, 1..12 seconds
    private var isCrossfadingNow = false

    val isPlaying: Boolean
        get() = try {
            activePlayer?.isPlaying == true
        } catch (e: Exception) {
            false
        }

    val currentPosition: Long
        get() = try {
            activePlayer?.currentPosition?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }

    val duration: Long
        get() = try {
            activePlayer?.duration?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }

    val audioSessionId: Int
        get() = try {
            activePlayer?.audioSessionId ?: 0
        } catch (e: Exception) {
            0
        }

    fun playSong(song: Song, speed: Float = 1.0f): Boolean {
        crossfadeJob?.cancel()
        crossfadeJob = null
        isCrossfadingNow = false

        releaseCrossfadePlayer()

        if (!requestAudioFocus()) {
            onError("Audio focus denied")
            return false
        }

        try {
            releaseActivePlayer()

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setDataSourceForSong(this, song)

                setOnPreparedListener { mp ->
                    setSpeed(speed)
                    mp.start()
                    audioEffectManager.attachSession(mp.audioSessionId)
                }

                setOnCompletionListener {
                    if (!isCrossfadingNow) {
                        onPlaybackCompleted()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayerEngine", "MediaPlayer error: what=$what extra=$extra")
                    onError("Playback error ($what, $extra)")
                    true
                }

                prepareAsync()
            }

            activePlayer = player
            return true
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Failed to start playback", e)
            onError("Unable to play track: ${e.localizedMessage}")
            return false
        }
    }

    /**
     * Executes real audio crossfade to [nextSong] over [durationSec] seconds.
     */
    fun startCrossfadeTo(nextSong: Song, speed: Float = 1.0f, durationSec: Int) {
        if (durationSec <= 0 || isCrossfadingNow) {
            playSong(nextSong, speed)
            return
        }

        val current = activePlayer ?: run {
            playSong(nextSong, speed)
            return
        }

        isCrossfadingNow = true

        try {
            val nextPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                setDataSourceForSong(this, nextSong)
                setVolume(0f, 0f)

                setOnPreparedListener { mp ->
                    setSpeedForPlayer(mp, speed)
                    mp.start()
                    audioEffectManager.attachSession(mp.audioSessionId)

                    // Crossfade volume ramp over durationSec
                    crossfadeJob = scope.launch(Dispatchers.Main) {
                        val steps = (durationSec * 10).coerceAtLeast(10)
                        val stepDelay = (durationSec * 1000L) / steps

                        for (i in 0..steps) {
                            val fraction = i.toFloat() / steps
                            val outVol = (1f - fraction).coerceIn(0f, 1f)
                            val inVol = fraction.coerceIn(0f, 1f)

                            try {
                                current.setVolume(outVol, outVol)
                                mp.setVolume(inVol, inVol)
                            } catch (e: Exception) {
                                break
                            }

                            delay(stepDelay)
                        }

                        // Complete crossfade transition
                        try {
                            current.stop()
                            current.reset()
                            current.release()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        activePlayer = mp
                        crossfadePlayer = null
                        isCrossfadingNow = false
                        onCrossfadeTriggered()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayerEngine", "Crossfade player error: $what, $extra")
                    isCrossfadingNow = false
                    crossfadePlayer = null
                    playSong(nextSong, speed)
                    true
                }

                prepareAsync()
            }

            crossfadePlayer = nextPlayer
        } catch (e: Exception) {
            Log.e("AudioPlayerEngine", "Failed to setup crossfade", e)
            isCrossfadingNow = false
            playSong(nextSong, speed)
        }
    }

    private fun setDataSourceForSong(player: MediaPlayer, song: Song) {
        val uriString = song.contentUriString
        if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
            player.setDataSource(context, Uri.parse(uriString))
        } else if (song.path.isNotBlank() && File(song.path).exists()) {
            player.setDataSource(song.path)
        } else if (uriString.isNotBlank()) {
            player.setDataSource(context, Uri.parse(uriString))
        } else {
            throw IllegalArgumentException("Audio file path or URI not found for song: ${song.title}")
        }
    }

    fun resume() {
        requestAudioFocus()
        try {
            activePlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        try {
            activePlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activePlayer?.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
            } else {
                activePlayer?.seekTo(positionMs.toInt())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSpeed(speed: Float) {
        activePlayer?.let { setSpeedForPlayer(it, speed) }
    }

    private fun setSpeedForPlayer(player: MediaPlayer, speed: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val params = player.playbackParams ?: PlaybackParams()
                params.speed = speed.coerceIn(0.5f, 2.0f)
                player.playbackParams = params
            }
        } catch (e: Exception) {
            Log.w("AudioPlayerEngine", "Speed adjustment error", e)
        }
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        try {
            activePlayer?.setVolume(v, v)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_GAIN -> resume()
                    }
                }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pause()
                    } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        resume()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun release() {
        crossfadeJob?.cancel()
        audioEffectManager.release()
        releaseActivePlayer()
        releaseCrossfadePlayer()
        abandonAudioFocus()
    }

    private fun releaseActivePlayer() {
        try {
            activePlayer?.stop()
            activePlayer?.reset()
            activePlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activePlayer = null
        }
    }

    private fun releaseCrossfadePlayer() {
        try {
            crossfadePlayer?.stop()
            crossfadePlayer?.reset()
            crossfadePlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            crossfadePlayer = null
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
