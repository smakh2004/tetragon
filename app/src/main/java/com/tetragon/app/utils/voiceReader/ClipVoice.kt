package com.tetragon.app.utils.voiceReader

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * Speaks a line by playing pre-recorded clips one after another, and animates
 * [TeacherLipSync] from each clip's real duration.
 *
 * This is the Uzbek path: the system TTS has no Uzbek voice, but Uzbek numerals
 * are compositional, so a handful of word clips covers every number.
 *
 * All players for a line are created and prepared BEFORE the first one starts,
 * so there is no decode gap between words.
 *
 * ENDING A LINE, same contract as QuestionVoice:
 *   onStarted  - fires when the first clip actually begins
 *   onFinished - fires when the last clip ends, on error, or on stop()
 */
class ClipVoice(
    context: Context,
    private val lipSync: TeacherLipSync
) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()

    private var players: List<MediaPlayer> = emptyList()
    private var texts: List<String> = emptyList()
    private var index = 0

    /** Bumped by every speak()/stop(); anything from an older session is dropped. */
    private var session = 0
    private var released = false

    private var onStarted: (() -> Unit)? = null
    private var onFinished: (() -> Unit)? = null

    /**
     * Plays [clips] in order.
     * [onUnavailable] runs if the clips cannot be loaded at all (missing raw file,
     * decoder error) so the caller can fall back to TTS instead of going silent.
     */
    fun speak(
        clips: List<VoiceClip>,
        onStarted: (() -> Unit)? = null,
        onFinished: (() -> Unit)? = null,
        onUnavailable: (() -> Unit)? = null
    ) {
        if (released) return
        stop()                          // closes out any previous line
        if (clips.isEmpty()) {
            onUnavailable?.invoke()
            return
        }

        this.onStarted = onStarted
        this.onFinished = onFinished

        val id = ++session
        val resIds = clips.map { it.resId }
        val clipTexts = clips.map { it.text }

        loader.execute {
            val built = ArrayList<MediaPlayer>(resIds.size)
            var ok = true
            for (res in resIds) {
                val player = runCatching { MediaPlayer.create(appContext, res) }.getOrNull()
                if (player == null) {
                    Log.w(TAG, "Missing or undecodable clip res=$res")
                    ok = false
                    break
                }
                built.add(player)
            }

            main.post {
                if (released || id != session) {
                    built.forEach { runCatching { it.release() } }
                    return@post
                }
                if (!ok) {
                    built.forEach { runCatching { it.release() } }
                    this.onStarted = null
                    this.onFinished = null
                    onUnavailable?.invoke()
                    return@post
                }

                players = built
                texts = clipTexts
                index = 0
                fireStarted()
                playCurrent(id)
            }
        }
    }

    /** Cuts audio and mouth off immediately; fires the pending onFinished. */
    fun stop() {
        session++
        onStarted = null
        releasePlayers()
        if (!released) lipSync.stop()
        fireFinished()
    }

    /** Call from onDestroyView(). */
    fun release() {
        released = true
        session++
        onStarted = null
        onFinished = null
        releasePlayers()
        main.removeCallbacksAndMessages(null)
        loader.shutdownNow()
    }

    // ------------------------------------------------------------- internals

    private fun playCurrent(id: Int) {
        if (released || id != session) return

        if (index >= players.size) {
            endLine()
            return
        }

        val player = players[index]
        val text = texts.getOrElse(index) { "" }

        player.setOnCompletionListener {
            main.post {
                if (released || id != session) return@post
                index++
                playCurrent(id)
            }
        }
        player.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "Clip error what=$what extra=$extra")
            main.post {
                if (released || id != session) return@post
                index++
                playCurrent(id)
            }
            true
        }

        val duration = player.duration.toLong()
        runCatching { player.start() }.onFailure {
            Log.w(TAG, "start() failed", it)
            index++
            playCurrent(id)
            return
        }

        // The mouth is stretched to the clip's real length, so it can never
        // outlive or fall behind the audio.
        if (text.isNotBlank() && duration > 0) {
            lipSync.speakForDuration(text, duration)
        }
    }

    private fun endLine() {
        releasePlayers()
        lipSync.stop()
        fireFinished()
    }

    private fun releasePlayers() {
        players.forEach { player ->
            runCatching {
                player.setOnCompletionListener(null)
                player.setOnErrorListener(null)
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        players = emptyList()
        texts = emptyList()
        index = 0
    }

    /** Fires at most once per line. */
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
        private const val TAG = "ClipVoice"
    }
}