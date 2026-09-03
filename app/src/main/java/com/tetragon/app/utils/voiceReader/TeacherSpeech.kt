package com.tetragon.app.utils.voiceReader

import android.content.Context
import java.util.Locale

/**
 * Single entry point for the teacher's voice.
 *
 *   uz  -> pre-recorded Uzbek clips (ClipVoice), because Android has no uz voice
 *   any -> system TextToSpeech (QuestionVoice)
 *
 * If an Uzbek line has no clips yet, or a clip file is missing, the line falls
 * back to TTS with [SpokenLine.text] (rendered in [fallbackLocale]) rather than
 * going silent, so a half-recorded set of clips still ships.
 */
class TeacherSpeech(
    context: Context,
    lipSync: TeacherLipSync,
    private val language: String,
    fallbackLocale: Locale
) {

    private val clipVoice: ClipVoice? =
        if (language == UZ) ClipVoice(context, lipSync) else null

    private val ttsVoice = QuestionVoice(context, lipSync, fallbackLocale)

    fun speak(
        line: SpokenLine,
        onStarted: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ) {
        val clips = clipVoice
        if (clips != null && line.clips.isNotEmpty()) {
            clips.speak(
                clips = line.clips,
                onStarted = onStarted,
                onFinished = onFinished,
                onUnavailable = { ttsVoice.speak(line.text, onStarted, onFinished) }
            )
        } else {
            ttsVoice.speak(line.text, onStarted, onFinished)
        }
    }

    fun stop() {
        clipVoice?.stop()
        ttsVoice.stop()
    }

    /** Call from onDestroyView(). */
    fun release() {
        clipVoice?.release()
        ttsVoice.release()
    }

    companion object {
        const val UZ = "uz"
    }
}