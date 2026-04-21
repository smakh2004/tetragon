package com.example.tetragon.aiChatBot

data class GeminiRequest(
    val contents: List<Content>
)

data class Content(
    val role: String = "user",
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GeminiResponse(
    val candidates: List<Candidate>? = null
)

data class Candidate(
    val content: Content? = null
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    var isAnimated: Boolean = false // New flag
)