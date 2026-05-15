package com.tetragon.app.aiChatBot

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApiService {
    // UPDATED TO 3.1 FLASH LITE PREVIEW
    @POST("v1beta/models/gemini-3.1-flash-lite-preview:generateContent")
    suspend fun getResponse(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}