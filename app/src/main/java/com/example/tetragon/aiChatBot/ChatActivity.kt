package com.example.tetragon.aiChatBot

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tetragon.R
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var etQuestion: EditText
    private lateinit var btnSend: FrameLayout
    private lateinit var tvSendText: TextView

    private val API_KEY = "AIzaSyC3cqE-6HW8xRZKB_eZiWjL43rfTY3xi-w"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        // Find the close button
        val btnClose: android.widget.ImageView = findViewById(R.id.btnClose)

        recyclerView = findViewById(R.id.chatRecyclerView)
        etQuestion = findViewById(R.id.etQuestion)
        btnSend = findViewById(R.id.btnSend)
        tvSendText = findViewById(R.id.tvSendText)

        setupRecycler()
        setupSendButtonUI()

        // Handle Quit/Exit
        btnClose.setOnClickListener {
            finish() // This closes the activity and goes back
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
        addBot("Hello! I'm Mr. Square. I am your personal math tutor.")
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
        if (hasText) {
            btnSend.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue_2))
            tvSendText.setTextColor(Color.WHITE)
            btnSend.isEnabled = true
        } else {
            btnSend.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_2))
            tvSendText.setTextColor(ContextCompat.getColor(this, R.color.gray_1))
            btnSend.isEnabled = false
        }
    }

    private fun sendMessage(text: String) {
        // 1. Add YOUR actual typed message to the list
        // This 'text' parameter comes from etQuestion.text.toString()
        adapter.chatList.add(ChatMessage(text, true))
        adapter.notifyItemInserted(adapter.chatList.size - 1)
        recyclerView.scrollToPosition(adapter.chatList.size - 1)

        // 2. Clear the input and disable it so the user can't spam while waiting
        etQuestion.text.clear()
        setUIEnabled(false)

        // 3. Add the "Thinking" Placeholder
        // We save its position so we can replace it with the real answer later
        val thinkingPos = adapter.chatList.size
        adapter.chatList.add(ChatMessage("Mr. Square is thinking...", false))
        adapter.notifyItemInserted(thinkingPos)
        recyclerView.scrollToPosition(thinkingPos)

        lifecycleScope.launch {
            try {
                // Prepare the Gemini request
                val request = GeminiRequest(
                    contents = listOf(
                        Content(
                            role = "user",
                            parts = listOf(
                                Part(
                                    """
                    Instruction: You are Mr. Square, a math tutor. 
                    Constraint: ONLY answer math questions. No markdown. 
                    Context: You have already greeted the user. Do not introduce yourself or say 'Hello, I am Mr. Square' again. Just answer the question directly.
                    
                    User question: $text
                    """.trimIndent()
                                )
                            )
                        )
                    )
                )

                // Make the API Call
                val response = RetrofitClient.api.getResponse(API_KEY, request)

                // Extract the text content
                val raw = response.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text

                val cleanAnswer = cleanText(raw ?: "I'm sorry, I couldn't process that.")

                // 4. REPLACE the "Thinking" message
                adapter.chatList[thinkingPos] = ChatMessage(cleanAnswer, false, isAnimated = false)
                adapter.notifyItemChanged(thinkingPos)

            } catch (e: Exception) {
                // Character-driven error messages
                val errorMessage = when {
                    e.message?.contains("429") == true -> {
                        "Mr. Square is taking a short math break to sharpen his pencils. Please wait a moment!"
                    }
                    else -> {
                        "Mr. Square got his angles crossed! Something went wrong. Let's try again in a bit."
                    }
                }

                adapter.chatList[thinkingPos] = ChatMessage(errorMessage, false)
                adapter.notifyItemChanged(thinkingPos)
            } finally {
                // 5. Re-enable the UI so the user can ask the next question
                setUIEnabled(true)
            }
        }
    }

    private fun setUIEnabled(enabled: Boolean) {
        etQuestion.isEnabled = enabled
        if (enabled) {
            etQuestion.requestFocus()
            updateSendButtonState(etQuestion.text.isNotEmpty())
        } else {
            btnSend.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_2))
            tvSendText.setTextColor(ContextCompat.getColor(this, R.color.gray_1))
        }
    }

    private fun cleanText(text: String) = text.replace("*", "").replace("```", "").trim()

    private fun addBot(text: String) {
        adapter.chatList.add(ChatMessage(text, false))
        adapter.notifyItemInserted(adapter.chatList.size - 1)
        recyclerView.scrollToPosition(adapter.chatList.size - 1)
    }
}