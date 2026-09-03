package com.tetragon.app.utils.voiceReader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * One TextToSpeech connection for the whole process.
 *
 * Binding to a TTS engine takes roughly 0.5–2s on a cold start. Creating it per
 * screen means the first line is always late; creating it once at app start means
 * every screen after that speaks immediately.
 *
 * Call [warmUp] from Application.onCreate() (best) or an early Activity.onCreate().
 * Calling it again from anywhere else is harmless.
 *
 * OWNERSHIP: because the engine is shared, a screen that is being destroyed must
 * not be able to silence the screen that replaced it. [speak] records the caller
 * as the current owner and [stop] ignores anyone else. Pass `this` from callers
 * that are stopping their own line; pass nothing to force a stop.
 */
object TtsEngine {

    private const val TAG = "TtsEngine"
    private const val WARMUP_ID = "warmup"

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var requestedLocale: Locale? = null
    private var appliedLanguage: String? = null
    private val waiting = mutableListOf<() -> Unit>()

    /** Whoever most recently called [speak]. Only that caller may stop it. */
    private var owner: Any? = null

    @Volatile
    var isReady = false
        private set

    @Volatile
    var hasVoice = false
        private set

    /** True once the init callback has run, successfully or not. */
    @Volatile
    var isInitialized = false
        private set

    /** Safe to call repeatedly and from anywhere; only the first call builds the engine. */
    fun warmUp(context: Context, locale: Locale) {
        requestedLocale = locale

        if (tts != null) {
            // Engine exists. If it is still connecting, the locale is applied in the
            // init callback below — calling setLanguage() now would fail and poison
            // the cached result.
            if (isReady) applyLocale(locale)
            return
        }

        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            isInitialized = true
            if (isReady) {
                applyLocale(requestedLocale ?: locale)
                runCatching {
                    tts?.playSilentUtterance(1L, TextToSpeech.QUEUE_FLUSH, WARMUP_ID)
                }
            } else {
                Log.w(TAG, "TTS init failed (status=$status)")
            }
            main.post {
                val pending = waiting.toList()
                waiting.clear()
                pending.forEach { it() }
            }
        }
    }

    /** Only ever called once the engine reports ready. */
    private fun applyLocale(locale: Locale) {
        val engine = tts ?: return
        if (!isReady) return
        if (appliedLanguage == locale.language && hasVoice) return

        val result = engine.setLanguage(locale)
        hasVoice = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        appliedLanguage = locale.language
        Log.i(TAG, "setLanguage(${locale.language}) -> $result, hasVoice=$hasVoice")
        if (!hasVoice) {
            Log.w(
                TAG,
                "No voice data for '${locale.language}'. The device needs that language " +
                        "installed in the system TTS settings; until then callers must " +
                        "fall back to a silent (mouth-only) line."
            )
        }
    }

    /**
     * Runs [action] now if the engine is ready, otherwise once init finishes.
     * If init already finished and FAILED, [action] still runs — callers check
     * [hasVoice] and take their silent fallback, instead of waiting forever for a
     * callback that will never come.
     */
    fun whenReady(action: () -> Unit) {
        if (isReady || isInitialized) action() else waiting.add(action)
    }

    /**
     * Speaks [text], taking ownership of the engine.
     * @return true if the engine accepted the utterance. When it returns false no
     *   progress callback will ever fire, so the caller must handle the line itself.
     */
    fun speak(
        text: String,
        rate: Float,
        listener: UtteranceProgressListener,
        utteranceId: String,
        owner: Any? = null
    ): Boolean {
        val engine = tts ?: return false
        if (!isReady || !hasVoice) return false

        this.owner = owner
        engine.stop()
        engine.setSpeechRate(rate)
        engine.setOnUtteranceProgressListener(listener)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.w(TAG, "speak() returned ERROR")
            this.owner = null
            return false
        }
        return true
    }

    /**
     * Stops the current line.
     * [owner] is the object that called [speak]; if it no longer owns the engine
     * the call is ignored, so a screen being torn down cannot cut off the screen
     * that replaced it. Pass null to stop unconditionally.
     */
    fun stop(owner: Any? = null) {
        if (owner != null && owner !== this.owner) return
        this.owner = null
        tts?.stop()
    }

    /** True when [owner] is the one currently holding the engine. */
    fun isOwnedBy(owner: Any?): Boolean = owner != null && owner === this.owner

    /** Only for a full app teardown – normally the engine lives for the process. */
    fun shutdown() {
        waiting.clear()
        owner = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        isInitialized = false
        hasVoice = false
        appliedLanguage = null
        requestedLocale = null
    }
}