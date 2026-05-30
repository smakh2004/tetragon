package com.tetragon.app.aiChatBot

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private var speechRecognizer: SpeechRecognizer? = null
    private var bargeInRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var voiceModeActive = false
    private var isCurrentlyListening = false
    private var isBotSpeaking = false

    private var aiJob: Job? = null
    private var startSound: MediaPlayer? = null
    private var stopSound: MediaPlayer? = null
    private var hasGreeted = false

    private val BARGE_IN_RMS_THRESHOLD = 4.0f
    private val BARGE_IN_REQUIRED_FRAMES = 3
    private var bargeInFrameCount = 0

    private val API_KEY = "AIzaSyC3cqE-6HW8xRZKB_eZiWjL43rfTY3xi-w"

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

        findViewById<ImageView>(R.id.btnClose).setOnClickListener { finish() }

        setupRecycler()
        setupSendButtonUI()

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

    // -------- Voice mode lifecycle --------

    private fun startVoiceMode() {
        if (voiceModeActive) return
        voiceModeActive = true
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
        bargeInFrameCount = 0

        stopSound?.start()
        tts?.stop()
        destroyRecognizers()

        updateMicUI(false)
        setInputLocked(false)
        hideWaveView()
    }

    // -------- Main listening --------

    private fun startListening() {
        if (!voiceModeActive) return
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

    // -------- Barge-in listener --------

    private fun startBargeInListener() {
        if (!voiceModeActive) return
        bargeInRecognizer?.destroy()
        bargeInRecognizer = null
        bargeInFrameCount = 0

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, getRecognitionLocale())
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onRmsChanged(rmsdB: Float) {
                if (!isBotSpeaking) return
                if (rmsdB > BARGE_IN_RMS_THRESHOLD) {
                    bargeInFrameCount++
                    if (bargeInFrameCount >= BARGE_IN_REQUIRED_FRAMES) {
                        handleBargeIn()
                    }
                } else if (bargeInFrameCount > 0) {
                    bargeInFrameCount--
                }
            }

            override fun onError(error: Int) {
                if (isBotSpeaking && voiceModeActive) {
                    recyclerView.post {
                        if (isBotSpeaking && voiceModeActive) startBargeInListener()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                if (isBotSpeaking) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    if (spoken.isNotEmpty()) {
                        isBotSpeaking = false
                        tts?.stop()
                        bargeInRecognizer?.destroy()
                        bargeInRecognizer = null
                        sendMessage(spoken, isVoice = true)
                    }
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        bargeInRecognizer = recognizer
        recognizer.startListening(intent)
    }

    private fun handleBargeIn() {
        if (!isBotSpeaking) return
        isBotSpeaking = false
        bargeInFrameCount = 0
        tts?.stop()
        bargeInRecognizer?.destroy()
        bargeInRecognizer = null
        startListening()
    }

    // -------- Sending a message --------

    // -------- Sending a message --------

    private fun sendMessage(text: String, isVoice: Boolean) {
        aiJob?.cancel()
        tts?.stop()
        isBotSpeaking = false
        destroyRecognizers()
        isCurrentlyListening = false

        if (voiceModeActive) voiceWaveView.setMode(VoiceWaveView.Mode.IDLE)

        // 1. Add User Message
        adapter.chatList.add(ChatMessage(text, true))
        adapter.notifyItemInserted(adapter.chatList.size - 1)
        recyclerView.scrollToPosition(adapter.chatList.size - 1)
        etQuestion.text.clear()

        // 2. Add Thinking Message (using the flag instead of text)
        val thinkingPos = adapter.chatList.size
        adapter.chatList.add(ChatMessage("", isUser = false, isThinking = true))
        adapter.notifyItemInserted(thinkingPos)
        recyclerView.scrollToPosition(thinkingPos)

        val langCode = LocaleHelper.getLanguage(this)
        val aiLanguage = when (langCode) {
            "ru" -> "Russian"
            "uz" -> "Uzbek"
            else -> "English"
        }

        aiJob = lifecycleScope.launch {
            try {
                val instruction = "You are Mr. Square, a Science tutor for Math, Physics, Biology, and Chemistry. Answer only questions related to these. Do not greet or introduce yourself again. Respond in $aiLanguage. Question: $text"
                val response = RetrofitClient.api.getResponse(
                    API_KEY,
                    GeminiRequest(contents = listOf(Content(role = "user", parts = listOf(Part(instruction)))))
                )
                val answer = cleanText(
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: "No answer"
                )

                // 3. Update the thinking position with the real answer and isThinking = false
                adapter.chatList[thinkingPos] = ChatMessage(answer, isUser = false, isThinking = false)
                adapter.notifyItemChanged(thinkingPos)
                recyclerView.scrollToPosition(thinkingPos)

                if (isVoice && voiceModeActive) {
                    speakAnswer(answer)
                }
            } catch (e: Exception) {
                // Update with error message
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
        startBargeInListener()

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
        tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    private fun setupTTSListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    isBotSpeaking = false
                    bargeInRecognizer?.destroy()
                    bargeInRecognizer = null
                    bargeInFrameCount = 0
                    if (voiceModeActive) startListening()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    isBotSpeaking = false
                    bargeInRecognizer?.destroy()
                    bargeInRecognizer = null
                    if (voiceModeActive) startListening()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                runOnUiThread {
                    isBotSpeaking = false
                    bargeInRecognizer?.destroy()
                    bargeInRecognizer = null
                    if (voiceModeActive) startListening()
                }
            }
        })
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
        recyclerView.layoutManager = LinearLayoutManager(this)
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
        bargeInRecognizer?.destroy()
        bargeInRecognizer = null
    }

    private fun getRecognitionLocale(): String {
        return when (LocaleHelper.getLanguage(this)) {
            "ru" -> "ru-RU"
            "uz" -> "uz-UZ"
            else -> "en-US"
        }
    }

    private fun cleanText(text: String) = text.replace("*", "").replace("```", "").trim()

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
        tts?.stop()
        tts?.shutdown()
        startSound?.release()
        stopSound?.release()
    }

    companion object {
        private const val UTTERANCE_ID = "bot_speech"
    }
}