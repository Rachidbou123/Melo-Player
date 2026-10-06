package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class VoiceCommand {
    object Play : VoiceCommand()
    object Pause : VoiceCommand()
    object Next : VoiceCommand()
    object Previous : VoiceCommand()
    object ShuffleFavorites : VoiceCommand()
    object ShuffleAll : VoiceCommand()
    object VolumeUp : VoiceCommand()
    object VolumeDown : VoiceCommand()
    data class PlayArtist(val artist: String) : VoiceCommand()
    data class PlaySong(val title: String) : VoiceCommand()
    data class PlayPlaylist(val playlistName: String) : VoiceCommand()
    data class PlayYear(val year: Int) : VoiceCommand()
    data class Unknown(val rawText: String) : VoiceCommand()
}

class VoiceCommandManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(onCommandRecognized: (VoiceCommand) -> Unit, onError: (String) -> Unit) {
        stopListening()

        if (!isAvailable) {
            onError("Speech recognition not available on this device")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsDb.value = rmsdB.coerceIn(0f, 10f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            else -> "Voice recognition error ($error)"
                        }
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        _lastRecognizedText.value = text
                        if (text.isNotBlank()) {
                            val command = parseCommand(text)
                            onCommandRecognized(command)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let {
                            _lastRecognizedText.value = it
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Throwable) {
            _isListening.value = false
            onError(e.localizedMessage ?: "Failed to start speech recognition")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            speechRecognizer = null
            _isListening.value = false
            _rmsDb.value = 0f
        }
    }

    fun parseCommand(spokenText: String): VoiceCommand {
        val lower = spokenText.lowercase().trim()
        return when {
            lower == "play" || lower == "resume" || lower.startsWith("play music") -> VoiceCommand.Play
            lower == "pause" || lower == "stop" -> VoiceCommand.Pause
            lower.contains("next") || lower.contains("skip") -> VoiceCommand.Next
            lower.contains("previous") || lower.contains("back") -> VoiceCommand.Previous
            lower.contains("shuffle") && lower.contains("fav") -> VoiceCommand.ShuffleFavorites
            lower.contains("shuffle") -> VoiceCommand.ShuffleAll
            lower.contains("volume up") || lower.contains("turn it up") || lower.contains("louder") -> VoiceCommand.VolumeUp
            lower.contains("volume down") || lower.contains("turn it down") || lower.contains("softer") -> VoiceCommand.VolumeDown
            lower.startsWith("play playlist") -> {
                val name = lower.removePrefix("play playlist").trim()
                VoiceCommand.PlayPlaylist(name)
            }
            lower.startsWith("play songs from") -> {
                val yearStr = lower.removePrefix("play songs from").trim()
                val year = yearStr.toIntOrNull()
                if (year != null) VoiceCommand.PlayYear(year) else VoiceCommand.Unknown(spokenText)
            }
            lower.startsWith("play by ") || lower.startsWith("play artist ") -> {
                val artist = lower.replace("play by ", "").replace("play artist ", "").trim()
                VoiceCommand.PlayArtist(artist)
            }
            lower.startsWith("play ") -> {
                val target = lower.removePrefix("play ").trim()
                VoiceCommand.PlaySong(target)
            }
            else -> VoiceCommand.Unknown(spokenText)
        }
    }
}
