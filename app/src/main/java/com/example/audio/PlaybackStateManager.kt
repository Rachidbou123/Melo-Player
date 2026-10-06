package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import com.example.data.model.Song
import com.example.data.repository.MusicRepository
import com.example.service.MusicPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackStateManager(
    private val context: Context,
    private val repository: MusicRepository,
    private val scope: CoroutineScope
) : MusicPlaybackService.ServicePlaybackCallback {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    val visualizerData = VisualizerDataProvider(scope)

    private var playerEngine: AudioPlayerEngine? = null
    private var progressTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var isCrossfadePreparing = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (_playbackState.value.isPlaying) {
                    togglePlayPause()
                }
            }
        }
    }

    init {
        MusicPlaybackService.servicePlaybackCallback = this

        playerEngine = AudioPlayerEngine(
            context = context,
            scope = scope,
            onPlaybackCompleted = {
                handleTrackCompletion()
            },
            onCrossfadeTriggered = {
                isCrossfadePreparing = false
            },
            onError = { msg ->
                scope.launch { _errorEvents.emit(msg) }
                _playbackState.update { it.copy(isPlaying = false) }
                visualizerData.stopSimulation()
                isCrossfadePreparing = false
            }
        )

        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(noisyReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onServicePlay() {
        if (!_playbackState.value.isPlaying) {
            togglePlayPause()
        }
    }

    override fun onServicePause() {
        if (_playbackState.value.isPlaying) {
            togglePlayPause()
        }
    }

    override fun onServiceNext() {
        playNext()
    }

    override fun onServicePrev() {
        playPrevious()
    }

    override fun onServiceSeekTo(positionMs: Long) {
        seekTo(positionMs)
    }

    fun playSong(song: Song, newQueue: List<Song> = emptyList()) {
        val effectiveQueue = if (newQueue.isNotEmpty()) newQueue else {
            if (_playbackState.value.queue.any { it.id == song.id }) {
                _playbackState.value.queue
            } else {
                listOf(song)
            }
        }
        val targetIndex = effectiveQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        _playbackState.update {
            it.copy(
                currentSong = song,
                queue = effectiveQueue,
                queueIndex = targetIndex,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = song.durationMs
            )
        }

        val started = playerEngine?.playSong(song, _playbackState.value.playbackSpeed) == true
        if (started) {
            visualizerData.attachSession(playerEngine?.audioSessionId ?: 0)
            startProgressTracker()
            notifyServiceState()
            scope.launch { repository.recordPlay(song.id) }
        }
    }

    fun togglePlayPause() {
        val current = _playbackState.value
        if (current.currentSong == null) {
            if (current.queue.isNotEmpty()) {
                playSong(current.queue.first(), current.queue)
            }
            return
        }

        if (current.isPlaying) {
            playerEngine?.pause()
            stopProgressTracker()
            visualizerData.stopSimulation()
            _playbackState.update { it.copy(isPlaying = false) }
        } else {
            playerEngine?.resume()
            startProgressTracker()
            visualizerData.attachSession(playerEngine?.audioSessionId ?: 0)
            _playbackState.update { it.copy(isPlaying = true) }
        }
        notifyServiceState()
    }

    fun playNext() {
        val current = _playbackState.value
        if (current.queue.isEmpty()) return

        val nextIndex = if (current.isShuffle) {
            val unplayedIndices = current.queue.indices.filter { it != current.queueIndex }
            if (unplayedIndices.isNotEmpty()) unplayedIndices.random() else 0
        } else {
            val candidate = current.queueIndex + 1
            if (candidate < current.queue.size) candidate else {
                if (current.repeatMode == RepeatMode.ALL) 0 else return
            }
        }

        val nextSong = current.queue.getOrNull(nextIndex) ?: return
        
        val crossfadeSec = current.crossfadeSeconds
        if (crossfadeSec > 0 && current.isPlaying) {
            _playbackState.update {
                it.copy(
                    currentSong = nextSong,
                    queueIndex = nextIndex,
                    currentPositionMs = 0L,
                    durationMs = nextSong.durationMs
                )
            }
            playerEngine?.startCrossfadeTo(nextSong, current.playbackSpeed, crossfadeSec)
            scope.launch { repository.recordPlay(nextSong.id) }
            notifyServiceState()
        } else {
            playSong(nextSong, current.queue)
        }
    }

    fun playPrevious() {
        val current = _playbackState.value
        if (current.currentPositionMs > 3000L) {
            seekTo(0L)
            return
        }
        if (current.queue.isEmpty()) return
        val prevIndex = (current.queueIndex - 1).coerceAtLeast(0)
        val prevSong = current.queue.getOrNull(prevIndex) ?: return
        playSong(prevSong, current.queue)
    }

    fun seekTo(positionMs: Long) {
        playerEngine?.seekTo(positionMs)
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
        notifyServiceState()
    }

    fun toggleShuffle() {
        _playbackState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun toggleRepeat() {
        _playbackState.update {
            val next = when (it.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            it.copy(repeatMode = next)
        }
    }

    fun setSpeed(speed: Float) {
        playerEngine?.setSpeed(speed)
        _playbackState.update { it.copy(playbackSpeed = speed) }
    }

    fun setVolume(volume: Float) {
        playerEngine?.setVolume(volume)
        _playbackState.update { it.copy(volume = volume) }
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        _playbackState.update { it.copy(visualizerMode = mode) }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        _playbackState.update { it.copy(crossfadeSeconds = clamped) }
        playerEngine?.crossfadeDurationSeconds = clamped
    }

    fun setEqualizerPreset(preset: String) {
        _playbackState.update { it.copy(equalizerPreset = preset) }
        playerEngine?.audioEffectManager?.currentPreset = preset
    }

    fun toggleBassBoost() {
        val next = !_playbackState.value.bassBoostEnabled
        _playbackState.update { it.copy(bassBoostEnabled = next) }
        playerEngine?.audioEffectManager?.isBassBoostEnabled = next
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackState.update {
                it.copy(sleepTimerMinutes = 0, sleepTimerRemainingSeconds = 0L, isSleepTimerActive = false)
            }
            return
        }

        val totalSeconds = minutes * 60L
        _playbackState.update {
            it.copy(sleepTimerMinutes = minutes, sleepTimerRemainingSeconds = totalSeconds, isSleepTimerActive = true)
        }

        sleepTimerJob = scope.launch(Dispatchers.Main) {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining--
                _playbackState.update { it.copy(sleepTimerRemainingSeconds = remaining) }

                // 10 second volume fade out before stopping
                if (remaining <= 10L && remaining > 0L) {
                    val fadeVol = remaining.toFloat() / 10f
                    playerEngine?.setVolume(fadeVol)
                }
            }

            if (remaining <= 0L) {
                if (_playbackState.value.isPlaying) {
                    togglePlayPause()
                }
                playerEngine?.setVolume(_playbackState.value.volume)
                _playbackState.update {
                    it.copy(sleepTimerMinutes = 0, sleepTimerRemainingSeconds = 0L, isSleepTimerActive = false)
                }
            }
        }
    }

    fun addToQueue(song: Song) {
        _playbackState.update {
            val updated = it.queue + song
            it.copy(queue = updated)
        }
    }

    fun playNextInQueue(song: Song) {
        _playbackState.update {
            val list = it.queue.toMutableList()
            val insertIdx = (it.queueIndex + 1).coerceIn(0, list.size)
            list.add(insertIdx, song)
            it.copy(queue = list)
        }
    }

    fun removeFromQueue(index: Int) {
        _playbackState.update {
            if (index in it.queue.indices) {
                val list = it.queue.toMutableList()
                list.removeAt(index)
                val newIndex = if (index < it.queueIndex) it.queueIndex - 1 else it.queueIndex
                it.copy(queue = list, queueIndex = newIndex.coerceIn(-1, (list.size - 1).coerceAtLeast(-1)))
            } else it
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        _playbackState.update {
            if (fromIndex in it.queue.indices && toIndex in it.queue.indices) {
                val list = it.queue.toMutableList()
                val moved = list.removeAt(fromIndex)
                list.add(toIndex, moved)
                val newQueueIndex = when (it.queueIndex) {
                    fromIndex -> toIndex
                    in (fromIndex + 1)..toIndex -> it.queueIndex - 1
                    in toIndex until fromIndex -> it.queueIndex + 1
                    else -> it.queueIndex
                }
                it.copy(queue = list, queueIndex = newQueueIndex)
            } else it
        }
    }

    fun clearQueue() {
        val currentSong = _playbackState.value.currentSong
        _playbackState.update {
            it.copy(
                queue = if (currentSong != null) listOf(currentSong) else emptyList(),
                queueIndex = if (currentSong != null) 0 else -1
            )
        }
    }

    private fun handleTrackCompletion() {
        val current = _playbackState.value
        when (current.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                playerEngine?.resume()
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                if (current.queueIndex < current.queue.size - 1) {
                    playNext()
                } else {
                    _playbackState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                    stopProgressTracker()
                    visualizerData.stopSimulation()
                    notifyServiceState()
                }
            }
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val pos = playerEngine?.currentPosition ?: 0L
                val dur = playerEngine?.duration ?: 0L
                val current = _playbackState.value

                _playbackState.update {
                    it.copy(
                        currentPositionMs = pos,
                        durationMs = if (dur > 0) dur else it.durationMs
                    )
                }

                // Check if crossfade should trigger near end of track
                val xfadeSec = current.crossfadeSeconds
                if (xfadeSec > 0 && dur > (xfadeSec * 1000 + 3000) && (dur - pos) <= (xfadeSec * 1000L) && !isCrossfadePreparing) {
                    val nextIdx = current.queueIndex + 1
                    if (nextIdx < current.queue.size) {
                        val nextSong = current.queue[nextIdx]
                        isCrossfadePreparing = true
                        _playbackState.update {
                            it.copy(
                                currentSong = nextSong,
                                queueIndex = nextIdx,
                                currentPositionMs = 0L,
                                durationMs = nextSong.durationMs
                            )
                        }
                        playerEngine?.startCrossfadeTo(nextSong, current.playbackSpeed, xfadeSec)
                        scope.launch { repository.recordPlay(nextSong.id) }
                        notifyServiceState()
                    }
                }

                delay(300)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    private fun notifyServiceState() {
        val current = _playbackState.value
        // Only start background service if a track is selected
        if (current.currentSong == null) return

        try {
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            val service = context as? MusicPlaybackService
            service?.updatePlaybackState(
                song = current.currentSong,
                isPlaying = current.isPlaying,
                currentPositionMs = current.currentPositionMs,
                durationMs = current.durationMs
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            context.unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sleepTimerJob?.cancel()
        stopProgressTracker()
        visualizerData.release()
        playerEngine?.release()
    }
}
