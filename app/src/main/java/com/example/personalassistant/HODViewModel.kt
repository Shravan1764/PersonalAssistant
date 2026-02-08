package com.example.personalassistant

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel // ✅ Import Gemini
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class UIState(
    val isListening: Boolean = false,
    val status: String = "Ready",
    val dailyLog: String = "",     // The raw text of the whole day
    val summary: String = "",      // The AI summary
    val isLoadingAI: Boolean = false
)

class HODViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UIState())
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var vibrator: Vibrator? = null

    // ✅ Initialize Gemini (Use Flash for speed + long context)
    private val generativeModel = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = "AIzaSyCdt3kiTEZ2OL08n99OVARTgKzzfsBNhzw" // <--- Your actual key string
    )

    // A buffer to hold the ENTIRE day's conversation
    private val fullDayTranscript = StringBuilder()

    init {
        vibrator = application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // --- PART 1: LISTENING & LOGGING ---

    fun startListening() {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                setupSpeechRecognizer()
                speechRecognizer?.startListening(createSpeechIntent())

                _uiState.value = _uiState.value.copy(
                    isListening = true,
                    status = "🎤 Listening..."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "❌ Error: ${e.message}")
            }
        }
    }

    fun stopListening() {
        viewModelScope.launch {
            speechRecognizer?.stopListening()
            _uiState.value = _uiState.value.copy(isListening = false, status = "Paused")
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.get(0) ?: ""

                if (text.isNotEmpty()) {
                    // 1. Append to our daily transcript
                    fullDayTranscript.append(text).append(". \n")

                    // 2. Update UI
                    _uiState.value = _uiState.value.copy(
                        status = "✅ Heard: $text",
                        dailyLog = fullDayTranscript.toString()
                    )

                    // 3. Keyword Detection (Vibrate if "Schedule" is heard)
                    if (text.lowercase().contains("schedule") || text.lowercase().contains("remind")) {
                        vibratePhone()
                    }
                }

                // 4. Keep listening (Continuous loop)
                restartListening()
            }

            override fun onError(error: Int) {
                // If error is "No match" or "Timeout", just restart quietly
                restartListening()
            }

            // Boilerplate overrides...
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun restartListening() {
        if (_uiState.value.isListening) {
            viewModelScope.launch {
                delay(500)
                try { speechRecognizer?.startListening(createSpeechIntent()) } catch (e: Exception) {}
            }
        }
    }

    // --- PART 2: GEMINI AI SUMMARY ---

    fun generateDailySummary() {
        if (fullDayTranscript.isEmpty()) {
            _uiState.value = _uiState.value.copy(summary = "Nothing recorded today yet!")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAI = true, status = "🧠 Analyzing day...")

            try {
                // The Prompt: Asking Gemini to act as an Executive Assistant
                val prompt = """
                    You are an Executive Assistant to a Head of Department (HOD).
                    Here is the raw transcript of everything heard today:

                    --- TRANSCRIPT START ---
                    ${fullDayTranscript.toString()}
                    --- TRANSCRIPT END ---

                    Please provide a structured summary:
                    1. 📅 **Meetings & Events**: What meetings happened?
                    2. ❗ **Action Items**: What was the HOD asked to do?
                    3. 🗓️ **Scheduling Requests**: Specifically list any requests to "schedule" something.
                    4. 📝 **General Summary**: A brief paragraph of the day.
                """.trimIndent()
//                val prompt = """
//                    Summarize this daily log for an HOD.
//                    Log: ${fullDayTranscript.toString()}
//
//                    Structure:
//                    1. Meetings
//                    2. Action Items
//                    3. Scheduling Requests
//                """.trimIndent()

                val response = generativeModel.generateContent(prompt)

                _uiState.value = _uiState.value.copy(
                    summary = response.text ?: "Could not generate summary.",
                    isLoadingAI = false,
                    status = "Summary Ready"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    summary = "Error connecting to AI: ${e.message}",
                    isLoadingAI = false
                )
            }
        }
    }

    // Helper: Vibrate
    private fun vibratePhone() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(500)
        }
    }

    private fun createSpeechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    }
}