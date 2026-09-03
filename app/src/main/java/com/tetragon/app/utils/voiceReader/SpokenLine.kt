package com.tetragon.app.utils.voiceReader

import androidx.annotation.RawRes

/**
 * One audio clip plus the text it contains.
 * The text is not spoken — it is what the mouth is animated from.
 */
data class VoiceClip(@RawRes val resId: Int, val text: String)

/**
 * A line the teacher can say, in both forms:
 *
 *   [text]  - plain text for the TTS path (en / ru)
 *   [clips] - ordered audio clips for the Uzbek path
 *
 * If [clips] is empty, or a clip cannot be loaded, the line falls back to [text]
 * through the normal TextToSpeech engine, so nothing ever goes silent.
 */
data class SpokenLine(
    val text: String,
    val clips: List<VoiceClip> = emptyList()
)