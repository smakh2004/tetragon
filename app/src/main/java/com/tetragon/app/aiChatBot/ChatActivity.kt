package com.tetragon.app.aiChatBot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.kotlin.RiveAnimationView
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.languageChangeUtils.LocaleHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*

class ChatActivity : BaseActivity(), TextToSpeech.OnInitListener {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var etQuestion: EditText
    private lateinit var btnSend: FrameLayout
    private lateinit var ivSendIcon: ImageView
    private lateinit var btnMic: ImageView
    private lateinit var voiceWaveView: VoiceWaveView
    private lateinit var riveAnimationView: RiveAnimationView

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var voiceModeActive = false
    private var isCurrentlyListening = false
    private var isBotSpeaking = false

    private var aiJob: Job? = null
    private var startSound: MediaPlayer? = null
    private var stopSound: MediaPlayer? = null
    private var hasGreeted = false

    private val API_KEY = "AIzaSyC3cqE-6HW8xRZKB_eZiWjL43rfTY3xi-w"

    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) startVoiceMode() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        tts = TextToSpeech(this, this)
        setupTTSListener()

        startSound = MediaPlayer.create(this, R.raw.mic_start)
        stopSound = MediaPlayer.create(this, R.raw.mic_stop)

        recyclerView = findViewById(R.id.chatRecyclerView)
        etQuestion = findViewById(R.id.etQuestion)
        btnSend = findViewById(R.id.btnSend)
        ivSendIcon = findViewById(R.id.ivSendIcon)
        btnMic = findViewById(R.id.btnMic)
        voiceWaveView = findViewById(R.id.voiceWaveView)
        riveAnimationView = findViewById(R.id.main_mr_square_rive)

        findViewById<ImageView>(R.id.btnClose).setOnClickListener { finish() }

        setupRecycler()
        setupSendButtonUI()
        setupKeyboardVisibilityListener()
        setupNetworkMonitoring()

        if (!hasGreeted) {
            adapter.chatList.add(ChatMessage(getString(R.string.bot_greeting), false))
            adapter.notifyItemInserted(0)
            hasGreeted = true
        }

        btnMic.setOnClickListener {
            if (voiceModeActive) {
                stopVoiceMode()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        btnSend.setOnClickListener {
            if (voiceModeActive) return@setOnClickListener
            val text = etQuestion.text.toString().trim()
            if (text.isNotEmpty() && etQuestion.isEnabled) sendMessage(text, isVoice = false)
        }
    }

    // -------- Network Monitoring Logic --------

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    riveAnimationView.setBooleanState("State Machine 1", "Problem", false)
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    riveAnimationView.setBooleanState("State Machine 1", "Problem", true)
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, networkCallback!!)

        // Initial setup check on launch
        if (!isNetworkAvailable()) {
            riveAnimationView.setBooleanState("State Machine 1", "Problem", true)
        }
    }

    // -------- Voice mode lifecycle --------

    private fun startVoiceMode() {
        if (voiceModeActive) return
        voiceModeActive = true

        riveAnimationView.setBooleanState("State Machine 1", "Problem", !isNetworkAvailable())

        startSound?.start()
        setInputLocked(true)
        updateMicUI(true)
        showWaveView()
        voiceWaveView.setMode(VoiceWaveView.Mode.IDLE)
        startListening()
    }

    private fun stopVoiceMode() {
        if (!voiceModeActive && !isCurrentlyListening && !isBotSpeaking) return
        voiceModeActive = false
        isCurrentlyListening = false
        isBotSpeaking = false

        stopSound?.start()
        tts?.stop()
        riveAnimationView.setBooleanState("State Machine 1", "Speaking", false)
        destroyRecognizers()

        updateMicUI(false)
        setInputLocked(false)
        hideWaveView()
    }

    // -------- Main listening --------

    private fun startListening() {
        if (!voiceModeActive || isBotSpeaking) return
        speechRecognizer?.destroy()
        speechRecognizer = null

        voiceWaveView.setMode(VoiceWaveView.Mode.USER_SPEAKING)

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, getRecognitionLocale())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isCurrentlyListening = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                voiceWaveView.onRmsChanged(rmsdB)
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotEmpty()) {
                    sendMessage(spoken, isVoice = true)
                } else {
                    restartListeningIfActive()
                }
            }

            override fun onError(error: Int) {
                isCurrentlyListening = false
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stopVoiceMode()
                    else -> restartListeningIfActive()
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer = recognizer
        recognizer.startListening(intent)
    }

    private fun restartListeningIfActive() {
        if (!voiceModeActive || isBotSpeaking) return
        recyclerView.post { startListening() }
    }

    // -------- Sending a message --------

    private fun sendMessage(text: String, isVoice: Boolean) {
        aiJob?.cancel()
        tts?.stop()
        isBotSpeaking = false
        riveAnimationView.setBooleanState("State Machine 1", "Speaking", false)
        destroyRecognizers()
        isCurrentlyListening = false

        if (voiceModeActive) voiceWaveView.setMode(VoiceWaveView.Mode.IDLE)

        riveAnimationView.setBooleanState("State Machine 1", "Typing", false)
        riveAnimationView.setBooleanState("State Machine 1", "Problem", false)

        adapter.chatList.add(ChatMessage(text, true))
        adapter.notifyItemInserted(adapter.chatList.size - 1)
        recyclerView.scrollToPosition(adapter.chatList.size - 1)
        etQuestion.text.clear()

        val thinkingPos = adapter.chatList.size
        adapter.chatList.add(ChatMessage("", isUser = false, isThinking = true))
        adapter.notifyItemInserted(thinkingPos)
        recyclerView.scrollToPosition(thinkingPos)

        if (!isNetworkAvailable()) {
            riveAnimationView.setBooleanState("State Machine 1", "Problem", true)
            adapter.chatList[thinkingPos] = ChatMessage(getString(R.string.bot_error_generic), isUser = false, isThinking = false)
            adapter.notifyItemChanged(thinkingPos)
            if (voiceModeActive) restartListeningIfActive()
            return
        }

        riveAnimationView.setBooleanState("State Machine 1", "Thinking", true)

        val langCode = LocaleHelper.getLanguage(this)
        val aiLanguage = when (langCode) {
            "ru" -> "Russian"
            "uz" -> "Uzbek"
            else -> "English"
        }

        aiJob = lifecycleScope.launch {
            try {
                val instruction = "You are Mr. Square, an interactive and playful Science tutor for Math and Physics for kids. Answer only questions related to these. Do not greet or introduce yourself again. IMPORTANT: Do not use any LaTeX or markdown formatting code blocks like $$, $, ^, or \\frac. Write equations simply and cleanly in plain text (e.g., use x^2 or normal symbols) so a child can easily read it. Respond in $aiLanguage. Question: $text"
                val response = RetrofitClient.api.getResponse(
                    API_KEY,
                    GeminiRequest(contents = listOf(Content(role = "user", parts = listOf(Part(instruction)))))
                )
                val answer = cleanText(
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "No answer"
                )

                riveAnimationView.setBooleanState("State Machine 1", "Thinking", false)

                adapter.chatList[thinkingPos] = ChatMessage(answer, isUser = false, isThinking = false)
                adapter.notifyItemChanged(thinkingPos)
                recyclerView.scrollToPosition(thinkingPos)

                if (isVoice && voiceModeActive) {
                    speakAnswer(answer)
                }
            } catch (e: Exception) {
                riveAnimationView.setBooleanState("State Machine 1", "Thinking", false)
                riveAnimationView.setBooleanState("State Machine 1", "Problem", true)

                adapter.chatList[thinkingPos] = ChatMessage(getString(R.string.bot_error_generic), isUser = false, isThinking = false)
                adapter.notifyItemChanged(thinkingPos)
                if (voiceModeActive) restartListeningIfActive()
            }
        }
    }

    // -------- TTS --------

    private fun speakAnswer(answer: String) {
        isBotSpeaking = true
        voiceWaveView.setMode(VoiceWaveView.Mode.BOT_SPEAKING)

        // Splits text into semantic chunks right after punctuation marks (. , ! ?) followed by whitespace
        val chunks = answer.split(Regex("(?<=[.,!?])\\s+")).filter { it.isNotBlank() }

        if (chunks.isEmpty()) {
            isBotSpeaking = false
            if (voiceModeActive) startListening()
            return
        }

        // Send every chunk into the TTS engine queue sequentially
        for (i in chunks.indices) {
            val params = Bundle()
            val isLastChunk = i == chunks.lastIndex
            val utteranceId = "${UTTERANCE_ID}_${i}_${isLastChunk}"

            // Flush out old speech only on index 0, append the rest
            val queueMode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(chunks[i], queueMode, params, utteranceId)
        }
    }

    private fun setupTTSListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    riveAnimationView.setBooleanState("State Machine 1", "Speaking", true)
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    if (utteranceId != null && utteranceId.startsWith(UTTERANCE_ID)) {
                        val tokens = utteranceId.split("_")
                        val isLastChunk = tokens.lastOrNull() == "true"

                        if (isLastChunk) {
                            isBotSpeaking = false
                            riveAnimationView.setBooleanState("State Machine 1", "Speaking", false)
                            if (voiceModeActive) startListening()
                        } else {
                            // Fires the 'Pause' trigger right as a sentence chunk completes
                            riveAnimationView.fireState("State Machine 1", "Pause")
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handleError()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleError()
            }

            private fun handleError() {
                runOnUiThread {
                    isBotSpeaking = false
                    riveAnimationView.setBooleanState("State Machine 1", "Speaking", false)
                    if (voiceModeActive) startListening()
                }
            }
        })
    }

    // -------- Keyboard Visibility Detector --------

    private fun setupKeyboardVisibilityListener() {
        val rootView = findViewById<View>(android.R.id.content)
        keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val isThinking = aiJob?.isActive == true
            if (isThinking) return@OnGlobalLayoutListener

            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom

            val isKeyboardVisible = keypadHeight > screenHeight * 0.15
            riveAnimationView.setBooleanState("State Machine 1", "Typing", isKeyboardVisible)
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
    }

    // -------- Network Health Utility --------

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // -------- UI helpers --------

    private fun showWaveView() {
        voiceWaveView.visibility = View.VISIBLE
        voiceWaveView.alpha = 0f
        voiceWaveView.translationY = 16f
        voiceWaveView.start()
        voiceWaveView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250L)
            .start()
    }

    private fun hideWaveView() {
        voiceWaveView.animate()
            .alpha(0f)
            .translationY(16f)
            .setDuration(200L)
            .withEndAction {
                voiceWaveView.visibility = View.GONE
                voiceWaveView.stop()
            }
            .start()
    }

    private fun setInputLocked(locked: Boolean) {
        etQuestion.isEnabled = !locked
        etQuestion.isFocusable = !locked
        etQuestion.isFocusableInTouchMode = !locked
        etQuestion.alpha = if (locked) 0.5f else 1.0f

        btnSend.isEnabled = !locked && etQuestion.text.toString().trim().isNotEmpty()
        btnSend.alpha = if (locked) 0.5f else 1.0f

        if (locked) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(etQuestion.windowToken, 0)
            etQuestion.clearFocus()
        }
    }

    private fun updateMicUI(listening: Boolean) {
        val color = ContextCompat.getColor(this, if (listening) R.color.blue_2 else R.color.gray_1)
        btnMic.imageTintList = ColorStateList.valueOf(color)
    }

    private fun setupRecycler() {
        adapter = ChatAdapter(mutableListOf())
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
    }

    private fun setupSendButtonUI() {
        etQuestion.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                btnSend.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@ChatActivity,
                        if (hasText) R.color.blue_2 else R.color.gray_2
                    )
                )
                ivSendIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        this@ChatActivity,
                        if (hasText) android.R.color.white else R.color.gray_1
                    )
                )
                btnSend.isEnabled = hasText && !voiceModeActive
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun destroyRecognizers() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun getRecognitionLocale(): String {
        return when (LocaleHelper.getLanguage(this)) {
            "ru" -> "ru-RU"
            "uz" -> "uz-UZ"
            else -> "en-US"
        }
    }

    // Strips raw math tokens and switches powers like ^2, ^3 into clean superscripts (², ³)
    private fun cleanText(text: String): String {
        var cleaned = text
            .replace(Regex("\\\$\\\$?"), "") // Removes single $ and double $$ blocks
            .replace("\\frac", "")
            .replace("*", "")
            .replace("```", "")

        // Map basic power values directly to tiny superscript numbers for easy reading
        cleaned = cleaned.replace("^2", "²")
        cleaned = cleaned.replace("^3", "³")
        cleaned = cleaned.replace("^4", "⁴")

        // Remove curly braces that often warp around exponents in LaTeX responses like ^{2}
        cleaned = cleaned.replace(Regex("\\^\\{(.*?)\\}")) { it.groupValues[1] }

        return cleaned.trim()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = when (LocaleHelper.getLanguage(this)) {
                "ru" -> Locale("ru", "RU")
                "uz" -> Locale("uz", "UZ")
                else -> Locale.US
            }
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (voiceModeActive) stopVoiceMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        aiJob?.cancel()
        destroyRecognizers()

        keyboardLayoutListener?.let { listener ->
            findViewById<View>(android.R.id.content).viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }

        networkCallback?.let { callback ->
            connectivityManager?.unregisterNetworkCallback(callback)
        }

        tts?.stop()
        tts?.shutdown()
        startSound?.release()
        stopSound?.release()
    }

    companion object {
        private const val UTTERANCE_ID = "bot_speech"
    }
}