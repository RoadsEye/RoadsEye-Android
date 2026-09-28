package com.roadseye.dashcam

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceCommandsManager private constructor(private val context: Context) {

    companion object {
        private var instance: VoiceCommandsManager? = null

        fun getInstance(context: Context): VoiceCommandsManager {
            return instance ?: synchronized(this) {
                instance ?: VoiceCommandsManager(context.applicationContext).also { instance = it }
            }
        }

        private const val TAG = "VoiceCommandsManager"
        private const val WAKE_TIMEOUT_MS = 8000L
        private const val DEBOUNCE_MS = 600L

        private val WAKE_PHRASES = listOf(
            "hey road's eye", "hey rosie", "hi road's eye", "hi rosie",
            "dashcam", "hey road", "hey dashcam", "hi road", "hi dashcam",
            "dash cam", "hi dash cam", "hey dash cam",
            "roads eye", "rosie", "roadseye", "hey roadseye", "hi roadseye"
        ).map { it.lowercase() }

        private val COMMANDS = listOf(
            "clip it", "clip recording", "clip",
            "start recording", "stop recording",
            "yes", "no", "cancel"
        ).map { it.lowercase() }
    }

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    var onWakePhraseDetected: (() -> Unit)? = null
    var onCommandDetected: ((String) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var wakePhraseDetected = false
    private var awaitingStopConfirmation = false
    private var lastProcessedCommand: String? = null
    private var lastWakePhraseTime: Long = 0L

    private val scope = CoroutineScope(Dispatchers.Main)

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            restartListening()
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Speech recognition error: $error")
            restartListening()
        }

        override fun onResults(results: Bundle?) {
            handleResults(results, isFinal = true)
            restartListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            handleResults(partialResults, isFinal = false)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleResults(bundle: Bundle?, isFinal: Boolean) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        if (matches.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastWakePhraseTime < DEBOUNCE_MS) return

        val lowerMatches = matches.map { it.lowercase().trim() }

        if (!wakePhraseDetected) {
            for (text in lowerMatches) {
                if (WAKE_PHRASES.any { text.contains(it) }) {
                    wakePhraseDetected = true
                    lastWakePhraseTime = now
                    Log.d(TAG, "Wake phrase detected: '$text'")
                    onWakePhraseDetected?.invoke()
                    return
                }
            }
        }

        if (wakePhraseDetected) {
            for (text in lowerMatches) {
                val command = findBestCommand(text)
                if (command != null && command != lastProcessedCommand) {
                    Log.d(TAG, "Command detected: '$command' from '$text'")

                    if (command in listOf("yes", "no", "cancel") && !awaitingStopConfirmation) continue

                    onCommandDetected?.invoke(command)

                    lastProcessedCommand = command
                    lastWakePhraseTime = now

                    when (command) {
                        "stop recording" -> awaitingStopConfirmation = true
                        "yes", "no", "cancel" -> {
                            wakePhraseDetected = false
                            awaitingStopConfirmation = false
                            lastProcessedCommand = null
                        }
                        else -> awaitingStopConfirmation = false
                    }
                    break
                }
            }

            if (now - lastWakePhraseTime > WAKE_TIMEOUT_MS) {
                wakePhraseDetected = false
                lastProcessedCommand = null
            }
        }
    }

    private fun findBestCommand(transcription: String): String? {
        val lower = transcription.lowercase()
        return COMMANDS.firstOrNull { cmd ->
            lower.contains(cmd) || levenshteinDistance(lower, cmd) <= 2
        }
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[m][n]
    }

    fun startListening() {
        if (isListening) return

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        }

        isListening = true
        wakePhraseDetected = false
        awaitingStopConfirmation = false
        lastProcessedCommand = null

        Log.d(TAG, "Voice commands started (SpeechRecognizer mode)")
        _isActive.value = true

        startNextListening()
    }

    private fun startNextListening() {
        if (!isListening || speechRecognizer == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            restartListening()
        }
    }

    private fun restartListening() {
        if (!isListening) return
        // Small delay to avoid rapid restarts
        scope.launch {
            delay(400)
            startNextListening()
        }
    }

    fun stopListening() {
        isListening = false
        wakePhraseDetected = false
        awaitingStopConfirmation = false
        lastProcessedCommand = null
        _isActive.value = false

        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null

        Log.d(TAG, "Voice commands stopped")
    }

    fun isVoiceCommandActive(): Boolean = isListening
}
