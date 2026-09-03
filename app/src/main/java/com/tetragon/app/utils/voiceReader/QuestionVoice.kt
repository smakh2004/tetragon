package com.tetragon.app.utils.voiceReader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speaks question text and drives [TeacherLipSync] from the engine's own word
 * boundaries, so the mouth stays locked to the audio.
 *
 * speak() takes two optional callbacks, both on the main thread:
 *   onStarted  - fires the moment audio actually begins (not when speak() is called)
 *   onFinished - fires when the line ends, errors, or is cut off by stop()
 *
 * Use them to raise and clear Rive booleans such as "incorrect" and "explain",
 * so the character reacts exactly while it is talking.
 *
 * ENDING A LINE. The mouth must never outlive the audio, so it is ended by every
 * possible exit:
 *   onDone     - the line finished normally
 *   onStop     - TextToSpeech.stop() was called (this does NOT fire onDone)
 *   onError    - synthesis failed
 *   watchdog   - nothing arrived within a generous estimate of the line's length
 * The watchdog is the safety net: without it a dropped callback leaves the mouth
 * looping forever.
 */
class QuestionVoice(
    context: Context,
    private val lipSync: TeacherLipSync,
    private val locale: Locale = Locale.getDefault(),
    private val speechRate: Float = 0.9f
) {

    private val main = Handler(Looper.getMainLooper())
    private var currentText: String? = null
    private var onStarted: (() -> Unit)? = null
    private var onFinished: (() -> Unit)? = null
    private var released = false
    private var watchdog: Runnable? = null

    private val progressListener = object : UtteranceProgressListener() {

        /**
         * Re-anchors the mouth to the audio on every word (API 26+).
         * Not looped: words are short and the next boundary lands quickly, so a
         * loop here would visibly stutter on a single shape.
         */
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val full = currentText ?: return
            if (start < 0 || end > full.length || start >= end) return
            val word = full.substring(start, end)
            main.post {
                if (released) return@post
                fireStarted()
                lipSync.speak(word, speechRate)
            }
        }

        /**
         * Fallback for engines with no word boundaries; the first onRangeStart
         * (if any) supersedes it immediately. Looped, because the whole-sentence
         * spelling estimate is regularly shorter than the real audio.
         */
        override fun onStart(utteranceId: String?) {
            val text = currentText ?: return
            main.post {
                if (released) return@post
                fireStarted()
                lipSync.speak(text, speechRate, loop = true)
            }
        }

        override fun onDone(utteranceId: String?) {
            main.post { endLine() }
        }

        /** TextToSpeech.stop() lands here, never in onDone. */
        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            main.post { endLine() }
        }

        @Deprecated("Required by the base class", ReplaceWith(""))
        override fun onError(utteranceId: String?) {
            main.post { endLine() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { endLine() }
        }
    }

    init {
        TtsEngine.warmUp(context, locale)
    }

    /**
     * Speaks [text] and animates the mouth.
     * [onStarted] runs when audio begins; [onFinished] when it ends or is stopped.
     */
    fun speak(
        text: String,
        onStarted: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null
    ) {
        if (released || text.isBlank()) return
        cancelWatchdog()
        fireFinished()                 // close out any previous line first
        currentText = text
        this.onStarted = onStarted
        this.onFinished = onFinished

        TtsEngine.whenReady {
            if (released || currentText != text) return@whenReady

            val accepted = TtsEngine.speak(text, speechRate, progressListener, UTTERANCE_ID, this)
            if (accepted) {
                armWatchdog(text)
            } else {
                // No voice for this language, or the engine refused the line:
                // mouth only, but keep the same contract.
                fireStarted()
                lipSync.speak(text, speechRate, loop = true)
                val estimate = (text.length * SILENT_MS_PER_CHAR).coerceAtLeast(600L)
                val done = Runnable { endLine() }
                watchdog = done
                main.postDelayed(done, estimate)
            }
        }
    }

    /** Cuts audio and mouth off immediately; fires the pending onFinished. */
    fun stop() {
        currentText = null
        onStarted = null
        cancelWatchdog()
        TtsEngine.stop(this)           // only if this instance still owns the engine
        lipSync.stop()
        fireFinished()
    }

    /** Call from onDestroyView(). Leaves the shared engine running. */
    fun release() {
        released = true
        currentText = null
        onStarted = null
        onFinished = null
        cancelWatchdog()
        main.removeCallbacksAndMessages(null)
        // Scoped on purpose: a fragment being destroyed must not silence the
        // fragment that replaced it.
        TtsEngine.stop(this)
    }

    // ------------------------------------------------------------- internals

    /** The single place a line ends: stops the mouth and fires onFinished once. */
    private fun endLine() {
        if (released) return
        cancelWatchdog()
        lipSync.stop()
        fireFinished()
    }

    /**
     * Safety net for a callback that never arrives. Sized well above any real
     * line at this speech rate, so it only fires when something went wrong.
     */
    private fun armWatchdog(text: String) {
        cancelWatchdog()
        val ms = (text.length * WATCHDOG_MS_PER_CHAR).coerceIn(WATCHDOG_MIN_MS, WATCHDOG_MAX_MS)
        val timeout = Runnable { endLine() }
        watchdog = timeout
        main.postDelayed(timeout, ms)
    }

    private fun cancelWatchdog() {
        watchdog?.let { main.removeCallbacks(it) }
        watchdog = null
    }

    /** Fires at most once per utterance. */
    private fun fireStarted() {
        val callback = onStarted ?: return
        onStarted = null
        callback()
    }

    private fun fireFinished() {
        val callback = onFinished ?: return
        onFinished = null
        callback()
    }

    companion object {
        private const val UTTERANCE_ID = "question"

        private const val SILENT_MS_PER_CHAR = 70L
        private const val WATCHDOG_MS_PER_CHAR = 220L
        private const val WATCHDOG_MIN_MS = 4_000L
        private const val WATCHDOG_MAX_MS = 25_000L
    }
}