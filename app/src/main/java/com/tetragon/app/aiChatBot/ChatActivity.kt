package com.tetragon.app.aiChatBot

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.languageChangeUtils.LocaleHelper
import kotlinx.coroutines.launch

// 1. Inherit from BaseActivity to apply the locale context
class ChatActivity : BaseActivity() {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var etQuestion: EditText
    private lateinit var btnSend: FrameLayout
    private lateinit var tvSendText: TextView

    // Note: Moving your API key to BuildConfig or Secrets is recommended for safety
    private val API_KEY = "AIzaSyC3cqE-6HW8xRZKB_eZiWjL43rfTY3xi-w"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        val btnClose: android.widget.ImageView = findViewById(R.id.btnClose)
        recyclerView = findViewById(R.id.chatRecyclerView)
        etQuestion = findViewById(R.id.etQuestion)
        btnSend = findViewById(R.id.btnSend)
        tvSendText = findViewById(R.id.tvSendText)

        setupRecycler()
        setupSendButtonUI()

        btnClose.setOnClickListener {
            finish()
        }

        btnSend.setOnClickListener {
            val text = etQuestion.text.toString().trim()
            if (text.isNotEmpty() && etQuestion.isEnabled) {
                sendMessage(text)
            }
        }
    }

    private fun setupRecycler() {
        adapter = ChatAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 2. Localized Greeting
        addBot(getString(R.string.bot_greeting))
    }

    private fun setupSendButtonUI() {
        etQuestion.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendButtonState(!s.isNullOrBlank())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateSendButtonState(hasText: Boolean) {
        val colorRes = if (hasText) R.color.blue_2 else R.color.gray_2
        val textColorRes = if (hasText) android.R.color.white else R.color.gray_1

        btnSend.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
        tvSendText.setTextColor(ContextCompat.getColor(this, textColorRes))
        btnSend.isEnabled = hasText
    }

    private fun sendMessage(text: String) {
        // Add User Message
        adapter.chatList.add(ChatMessage(text, true))
        adapter.notifyItemInserted(adapter.chatList.size - 1)
        recyclerView.scrollToPosition(adapter.chatList.size - 1)

        etQuestion.text.clear()
        setUIEnabled(false)

        // 3. Localized "Thinking" Placeholder
        val thinkingPos = adapter.chatList.size
        adapter.chatList.add(ChatMessage(getString(R.string.bot_thinking), false))
        adapter.notifyItemInserted(thinkingPos)
        recyclerView.scrollToPosition(thinkingPos)

        // Get Current Language for AI Prompt
        val langCode = LocaleHelper.getLanguage(this)
        val aiLanguage = when(langCode) {
            "ru" -> "Russian"
            "uz" -> "Uzbek"
            else -> "English"
        }

        lifecycleScope.launch {
            try {
                // 4. Inject language instruction into the prompt
                val request = GeminiRequest(
                    contents = listOf(
                        Content(
                            role = "user",
                            parts = listOf(
                                Part(
                                    """
                    Instruction: You are Mr. Square, a math tutor. 
                    Constraint: ONLY answer math questions. No markdown. 
                    Language: You MUST respond in $aiLanguage.
                    Context: Do not introduce yourself. Just answer the question directly.
                    
                    User question: $text
                    """.trimIndent()
                                )
                            )
                        )
                    )
                )

                val response = RetrofitClient.api.getResponse(API_KEY, request)
                val raw = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                val cleanAnswer = cleanText(raw ?: getString(R.string.bot_no_answer))

                adapter.chatList[thinkingPos] = ChatMessage(cleanAnswer, false, isAnimated = false)
                adapter.notifyItemChanged(thinkingPos)

            } catch (e: Exception) {
                // 5. Localized Error Messages
                val errorStr = if (e.message?.contains("429") == true) {
                    getString(R.string.bot_error_429)
                } else {
                    getString(R.string.bot_error_generic)
                }

                adapter.chatList[thinkingPos] = ChatMessage(errorStr, false)
                adapter.notifyItemChanged(thinkingPos)
            } finally {
                setUIEnabled(true)
            }
        }
    }

    private fun setUIEnabled(enabled: Boolean) {
        etQuestion.isEnabled = enabled
        if (enabled) {
            etQuestion.requestFocus()
            updateSendButtonState(etQuestion.text.isNotEmpty())
        }
    }

    private fun cleanText(text: String) = text.replace("*", "").replace("```", "").trim()

    private fun addBot(text: String) {
        adapter.chatList.add(ChatMessage(text, false))
        adapter.notifyItemInserted(adapter.chatList.size - 1)
        recyclerView.scrollToPosition(adapter.chatList.size - 1)
    }
}