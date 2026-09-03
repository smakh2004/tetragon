package com.tetragon.app.utils.voiceReader

import android.os.Handler
import android.os.Looper
import android.util.Log
import app.rive.runtime.kotlin.RiveAnimationView
import kotlin.text.iterator

/**
 * Drives ViewModel1 of teacher_animation.riv:
 *   mouth     (Number)  - viseme, animated from the spoken text
 *   correct   (Trigger) - fired on a correct answer
 *   incorrect (Boolean) - true while the "try again" line is being spoken
 *   explain   (Boolean) - true while the solution is being spoken
 *
 * Viseme mapping:
 *  0 Neutral | 1 A'E'I | 2 O | 3 C,D,N,S,T,X,Y,Z | 4 R | 5 L | 6 B,M,P
 *  7 F,V     | 8 Ee    | 9 Th | 10 J,Ch,SH       | 11 U | 12 Q,W
 *
 * [language] picks the letter -> viseme tables: "en", "ru" or "uz". Cyrillic text
 * is detected on its own, so a Russian line still reads correctly with language
 * left at "uz".
 *
 * TIMING. [speakSynced] is the accurate path and the one to use whenever real
 * audio is playing: it reads the player's position every tick, so the mouth can
 * never drift from the sound. [speak] and [speakForDuration] chain postDelayed
 * calls instead, which accumulates scheduler jitter over a long line — they are
 * only for cases with no audio clock to follow.
 */
class TeacherLipSync(
    private val riveView: RiveAnimationView,
    private val viewModelName: String = "ViewModel1",
    private val propertyName: String = "mouth",
    private val triggerNames: List<String> = listOf("correct"),
    private val booleanNames: List<String> = listOf("incorrect", "explain"),
    var language: String = "en"
) {

    object Viseme {
        const val NEUTRAL = 0
        const val A_E_I = 1
        const val O = 2
        const val C_D_N_S_T_X_Y_Z = 3
        const val R = 4
        const val L = 5
        const val B_M_P = 6
        const val F_V = 7
        const val EE = 8
        const val TH = 9
        const val J_CH_SH = 10
        const val U = 11
        const val Q_W = 12
    }

    private data class Frame(val viseme: Int, var durationMs: Long)

    /** A viseme plus the moment on the audio clock at which it stops being shown. */
    private data class Cue(val viseme: Int, val endMs: Long)

    private val handler = Handler(Looper.getMainLooper())
    private var setMouth: ((Float) -> Unit)? = null
    private val triggers = HashMap<String, () -> Unit>()
    private val booleans = HashMap<String, (Boolean) -> Unit>()
    private var frames: List<Frame> = emptyList()
    private var cursor = 0

    /** When true the timeline restarts instead of ending; only stop() ends it. */
    private var loop = false

    var isSpeaking = false
        private set

    // ---------------------------------------------------------------- public

    /** Binds the view model ahead of time. Call once after construction. */
    fun prepare() {
        if (setMouth != null) return
        if (!bindIfNeeded()) riveView.post { bindIfNeeded() }
    }

    /** Fires a trigger such as "correct". */
    fun fireTrigger(name: String) {
        bindIfNeeded()
        val fire = triggers[name]
        if (fire == null) {
            Log.w(TAG, "No trigger '$name' on $viewModelName")
            return
        }
        fire()
    }

    /** Sets a boolean such as "incorrect" or "explain". */
    fun setBoolean(name: String, value: Boolean) {
        bindIfNeeded()
        val setter = booleans[name]
        if (setter == null) {
            Log.w(TAG, "No boolean '$name' on $viewModelName")
            return
        }
        setter(value)
    }

    /** Clears every boolean back to false. */
    fun clearBooleans() {
        bindIfNeeded()
        booleans.values.forEach { it(false) }
    }

    /**
     * Animates the mouth through the visemes of [text] on a free-running timer.
     * [speed] > 1 is faster, < 1 slower. Main thread only.
     *
     * [loop] keeps the mouth moving after the last viseme instead of closing it.
     * Pass true whenever real audio is playing and its length is unknown; the
     * caller must then end it with stop().
     *
     * Prefer [speakSynced] when the audio's position can be read — this path
     * drifts on long lines.
     */
    fun speak(text: String, speed: Float = 1f, loop: Boolean = false) {
        cancelPlayback()
        val built = buildFrames(text)
        if (built.isEmpty()) return
        frames = built
        cursor = 0
        this.loop = loop        // set after cancelPlayback(), which clears it
        isSpeaking = true

        val rate = if (speed <= 0f) 1f else speed
        if (setMouth != null) {
            playNext(rate)
        } else {
            riveView.post {
                bindIfNeeded()
                playNext(rate)
            }
        }
    }

    /**
     * Stretches the viseme timeline to last [totalDurationMs] on a free-running
     * timer. Kept for callers with a known length but no readable clock; prefer
     * [speakSynced].
     */
    fun speakForDuration(text: String, totalDurationMs: Long) {
        val built = buildFrames(text)
        if (built.isEmpty() || totalDurationMs <= 0) return
        val natural = built.sumOf { it.durationMs }.coerceAtLeast(1L)
        speak(text, natural.toFloat() / totalDurationMs.toFloat())
    }

    /**
     * Animates the mouth against the audio's own clock.
     *
     * The viseme timeline is stretched across [totalDurationMs], then on every
     * tick [positionSupplier] is asked where the audio actually is and the cue
     * covering that instant is applied. Because each cue is selected from the
     * measured position rather than from the previous one's scheduled time,
     * error cannot accumulate — a line of any length stays locked to the sound.
     *
     * [positionSupplier] runs on the main thread and should return the player's
     * current position in ms, or a negative number once the player is gone (the
     * ticker then stops on its own).
     */
    fun speakSynced(text: String, totalDurationMs: Long, positionSupplier: () -> Long) {
        cancelPlayback()
        val built = buildFrames(text)
        if (built.isEmpty() || totalDurationMs <= 0) return

        val cues = toCues(built, totalDurationMs)
        if (cues.isEmpty()) return

        isSpeaking = true

        val startTicking = {
            var index = -1
            val ticker = object : Runnable {
                override fun run() {
                    if (!isSpeaking) return

                    val position = runCatching { positionSupplier() }.getOrElse { -1L }
                    if (position < 0) {
                        isSpeaking = false
                        return
                    }

                    val clock = position + LIP_SYNC_OFFSET_MS
                    var target = if (index < 0) 0 else index
                    while (target < cues.size - 1 && clock >= cues[target].endMs) target++

                    if (target != index) {
                        index = target
                        applyViseme(cues[target].viseme)
                    }
                    handler.postDelayed(this, TICK_MS)
                }
            }
            ticker.run()
        }

        if (setMouth != null) {
            startTicking()
        } else {
            riveView.post {
                bindIfNeeded()
                if (isSpeaking) startTicking()
            }
        }
    }

    /** Stops the mouth and returns it to Neutral (0). Leaves booleans untouched. */
    fun stop() {
        cancelPlayback()
        applyViseme(Viseme.NEUTRAL)
    }

    /** Call from onDestroyView(). */
    fun release() {
        cancelPlayback()
        setMouth = null
        triggers.clear()
        booleans.clear()
    }

    /** Sets a single viseme without animating (handy for testing the mapping). */
    fun setViseme(viseme: Int) {
        bindIfNeeded()
        applyViseme(viseme)
    }

    // ----------------------------------------------------------------- cues

    /**
     * Stretches [built] across [totalDurationMs] and enforces [MIN_VISEME_MS].
     *
     * A clip is regularly shorter than the spelling estimate of its own text —
     * the long try-again line is the worst case. Scaling alone would then give
     * every shape a few milliseconds on screen, and the mouth flickers instead of
     * reading as speech. So any run of frames that would land under the minimum
     * is merged into a single cue: the line still ends exactly with the audio, it
     * just shows fewer, longer shapes. A vowel inside the run wins the merge,
     * because the open shapes are what make the mouth look like it is talking.
     */
    private fun toCues(built: List<Frame>, totalDurationMs: Long): List<Cue> {
        val natural = built.sumOf { it.durationMs }.coerceAtLeast(1L)
        val scale = totalDurationMs.toDouble() / natural.toDouble()

        val out = ArrayList<Cue>(built.size)
        var elapsed = 0.0
        var runStart = 0.0
        var runViseme = -1

        for (i in built.indices) {
            val frame = built[i]
            if (runViseme == -1) {
                runViseme = frame.viseme
                runStart = elapsed
            } else if (frame.viseme in VOWEL_VISEMES && runViseme !in VOWEL_VISEMES) {
                runViseme = frame.viseme
            }

            elapsed += frame.durationMs * scale

            val held = elapsed - runStart
            if (held >= MIN_VISEME_MS || i == built.lastIndex) {
                out.add(Cue(runViseme, elapsed.toLong()))
                runViseme = -1
            }
        }

        // The last cue must cover to the end of the audio, whatever rounding did.
        if (out.isNotEmpty()) {
            out[out.lastIndex] = out.last().copy(endMs = totalDurationMs)
        }
        return out
    }

    // ------------------------------------------------------------- playback

    private fun cancelPlayback() {
        handler.removeCallbacksAndMessages(null)
        isSpeaking = false
        loop = false
        cursor = 0
        frames = emptyList()
    }

    private fun playNext(speed: Float) {
        if (!isSpeaking) return
        if (cursor >= frames.size) {
            if (loop) {
                // Audio is still playing: rewind and hold an open mouth state (A_E_I)
                // between phrase boundaries until stop() arrives.
                cursor = 0
                applyViseme(Viseme.A_E_I)
                handler.postDelayed({ playNext(speed) }, (WORD_GAP_MS / speed).toLong().coerceAtLeast(16L))
                return
            }
            isSpeaking = false
            return
        }
        val frame = frames[cursor++]
        applyViseme(frame.viseme)
        val delay = (frame.durationMs / speed).toLong().coerceAtLeast(MIN_VISEME_MS)
        handler.postDelayed({ playNext(speed) }, delay)
    }

    private fun applyViseme(viseme: Int) {
        setMouth?.invoke(viseme.toFloat())
    }

    // -------------------------------------------------------- data binding

    /** @return true once the mouth property is bound and writable. */
    private fun bindIfNeeded(): Boolean {
        if (setMouth != null) return true
        val controller = riveView.controller
        val file = controller.file ?: return false

        val instance = runCatching {
            file.getViewModelByName(viewModelName).createDefaultInstance()
        }.getOrElse {
            Log.w(TAG, "Could not create instance of $viewModelName", it)
            return false
        }

        runCatching {
            val sm = controller.stateMachines.firstOrNull()
            if (sm != null) {
                sm.viewModelInstance = instance
            } else {
                controller.activeArtboard?.viewModelInstance = instance
            }
        }.onFailure { Log.w(TAG, "Could not bind view model instance", it) }

        // Triggers and booleans are optional: a missing one is logged, not fatal.
        triggerNames.forEach { name ->
            runCatching {
                val property = instance.getTriggerProperty(name)
                triggers[name] = { property.trigger() }
            }.onFailure { Log.w(TAG, "No trigger property '$name'", it) }
        }

        booleanNames.forEach { name ->
            runCatching {
                val property = instance.getBooleanProperty(name)
                booleans[name] = { value -> property.value = value }
            }.onFailure { Log.w(TAG, "No boolean property '$name'", it) }
        }

        return runCatching {
            val property = instance.getNumberProperty(propertyName)
            setMouth = { value -> property.value = value }
            true
        }.getOrElse {
            Log.w(TAG, "No number property '$propertyName' on $viewModelName", it)
            false
        }
    }

    // ------------------------------------------------------- text -> visemes

    private fun buildFrames(raw: String): List<Frame> {
        val text = expandDigits(raw).lowercase()
        val cyrillic = hasCyrillic(text)
        val lang = if (cyrillic) RU else language

        val digraphs = if (lang == UZ) DIGRAPHS_UZ else DIGRAPHS
        val singles = if (lang == UZ) SINGLES_UZ else SINGLES
        val silentERule = lang == EN

        val out = ArrayList<Frame>()
        var i = 0

        while (i < text.length) {
            val c = text[i]
            when {
                c in SKIPPED_CHARS -> i++

                c.isWhitespace() -> {
                    // Transition to open mouth (A_E_I) during gaps between words
                    push(out, Viseme.A_E_I, WORD_GAP_MS)
                    i++
                }

                !c.isLetter() -> {
                    if (c in PAUSE_CHARS) push(out, Viseme.A_E_I, PUNCT_MS)
                    i++
                }

                else -> {
                    val pair = if (i + 1 < text.length) text.substring(i, i + 2) else ""
                    val digraph = digraphs[pair]
                    if (digraph != null) {
                        push(out, digraph, durationFor(digraph))
                        i += 2
                    } else {
                        val single = singles[c]
                        val isSilentE = silentERule && c == 'e' &&
                                (i == text.length - 1 || !text[i + 1].isLetter()) &&
                                i > 0 && text[i - 1].isLetter()
                        if (single != null && !isSilentE) push(out, single, durationFor(single))
                        i++
                    }
                }
            }
        }

        return out
    }

    private fun push(out: ArrayList<Frame>, viseme: Int, durationMs: Long) {
        val last = out.lastOrNull()
        if (last != null && last.viseme == viseme) {
            last.durationMs = minOf(last.durationMs + durationMs, MAX_HOLD_MS)
        } else {
            out.add(Frame(viseme, durationMs))
        }
    }

    private fun durationFor(viseme: Int) =
        if (viseme in VOWEL_VISEMES) VOWEL_MS else CONSONANT_MS

    private fun hasCyrillic(text: String) =
        text.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

    /**
     * Turns "10" into spoken words so it produces a realistic number of visemes.
     */
    private fun expandDigits(text: String): String {
        if (text.none { it.isDigit() }) return text
        val lang = if (hasCyrillic(text)) RU else language
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i].isDigit()) {
                var j = i
                while (j < text.length && text[j].isDigit()) j++
                sb.append(' ').append(numberToWords(text.substring(i, j), lang)).append(' ')
                i = j
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun numberToWords(digits: String, lang: String): String {
        val ones = when (lang) {
            RU -> ONES_RU
            UZ -> ONES_UZ
            else -> ONES
        }
        val tens = when (lang) {
            RU -> TENS_RU
            UZ -> TENS_UZ
            else -> TENS
        }
        val n = digits.toIntOrNull() ?: return digits.map { ones[it - '0'] }.joinToString(" ")
        return when {
            n < 20 -> ones[n]
            n < 100 -> tens[n / 10] + if (n % 10 != 0) " " + ones[n % 10] else ""
            else -> digits.map { ones[it - '0'] }.joinToString(" ")
        }
    }

    companion object {
        private const val TAG = "TeacherLipSync"

        private const val EN = "en"
        private const val RU = "ru"
        private const val UZ = "uz"

        /** How often [speakSynced] re-reads the audio position. 30 fps. */
        private const val TICK_MS = 33L

        /**
         * Compensates for the gap between MediaPlayer.currentPosition and what the
         * speaker is actually emitting. Raise it if the mouth still trails the
         * audio on a device; lower it (even negative) if the mouth runs ahead.
         */
        private const val LIP_SYNC_OFFSET_MS = 60L

        /**
         * Shortest time any shape stays on screen. Below roughly this, a mouth
         * stops reading as speech and starts reading as noise — and at a 33 ms
         * tick there are not enough frames to draw it anyway.
         */
        private const val MIN_VISEME_MS = 80L

        private const val CONSONANT_MS = 55L
        private const val VOWEL_MS = 85L
        private const val WORD_GAP_MS = 70L
        private const val PUNCT_MS = 160L
        private const val MAX_HOLD_MS = 220L

        private val VOWEL_VISEMES = setOf(Viseme.A_E_I, Viseme.O, Viseme.EE, Viseme.U)

        private val PAUSE_CHARS = setOf('.', ',', '!', '?', ':', ';', '—', '–', '-')

        /**
         * Uzbek writes o' and g' with any of these, depending on the keyboard used.
         * They are consumed as part of the digraph, and skipped anywhere else.
         */
        private val APOSTROPHES = listOf('\'', '\u2018', '\u2019', '\u02BB', '\u02BC', '`')

        private val SKIPPED_CHARS = APOSTROPHES.toSet()

        private val DIGRAPHS: Map<String, Int> = buildMap {
            put("ch", Viseme.J_CH_SH)
            put("sh", Viseme.J_CH_SH)
            put("th", Viseme.TH)
            put("ph", Viseme.F_V)
            put("qu", Viseme.Q_W)
            put("ee", Viseme.EE)
            put("ea", Viseme.EE)
            put("ie", Viseme.EE)
            put("oo", Viseme.U)
            put("ou", Viseme.U)
            put("ow", Viseme.O)
            put("ck", Viseme.C_D_N_S_T_X_Y_Z)
            put("ng", Viseme.C_D_N_S_T_X_Y_Z)
            for (a in APOSTROPHES) {
                put("o$a", Viseme.O)
                put("g$a", Viseme.C_D_N_S_T_X_Y_Z)
            }
        }

        /**
         * Uzbek Latin only. The English vowel digraphs are deliberately absent:
         * they would merge two separate Uzbek sounds into one viseme.
         */
        private val DIGRAPHS_UZ: Map<String, Int> = buildMap {
            put("ch", Viseme.J_CH_SH)
            put("sh", Viseme.J_CH_SH)
            put("ng", Viseme.C_D_N_S_T_X_Y_Z)
            for (a in APOSTROPHES) {
                put("o$a", Viseme.O)
                put("g$a", Viseme.C_D_N_S_T_X_Y_Z)
            }
        }

        private val SINGLES: Map<Char, Int> = buildMap {
            put('a', Viseme.A_E_I); put('e', Viseme.A_E_I); put('i', Viseme.A_E_I)
            put('h', Viseme.A_E_I)
            put('o', Viseme.O)
            put('u', Viseme.U)
            put('r', Viseme.R)
            put('l', Viseme.L)
            put('b', Viseme.B_M_P); put('m', Viseme.B_M_P); put('p', Viseme.B_M_P)
            put('f', Viseme.F_V); put('v', Viseme.F_V)
            put('j', Viseme.J_CH_SH)
            put('q', Viseme.Q_W); put('w', Viseme.Q_W)
            for (c in "cdnstxyzkg") put(c, Viseme.C_D_N_S_T_X_Y_Z)

            for (c in "аяэе") put(c, Viseme.A_E_I)
            put('о', Viseme.O); put('ё', Viseme.O)
            put('у', Viseme.U); put('ю', Viseme.U)
            put('и', Viseme.EE)
            put('р', Viseme.R)
            put('л', Viseme.L)
            for (c in "бмп") put(c, Viseme.B_M_P)
            for (c in "фв") put(c, Viseme.F_V)
            for (c in "жчшщ") put(c, Viseme.J_CH_SH)
            for (c in "кгхцсзднтйы") put(c, Viseme.C_D_N_S_T_X_Y_Z)
        }

        /**
         * Uzbek overrides. `q` is a back consonant here, not the English "qu"
         * rounding, so it must not use the Q,W viseme.
         */
        private val SINGLES_UZ: Map<Char, Int> = buildMap {
            putAll(SINGLES)
            put('q', Viseme.C_D_N_S_T_X_Y_Z)
            put('x', Viseme.C_D_N_S_T_X_Y_Z)
            put('y', Viseme.EE)
        }

        private val ONES = arrayOf(
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"
        )

        private val TENS = arrayOf(
            "", "ten", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
        )

        private val ONES_RU = arrayOf(
            "ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять",
            "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать",
            "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
        )

        private val TENS_RU = arrayOf(
            "", "десять", "двадцать", "тридцать", "сорок", "пятьдесят",
            "шестьдесят", "семьдесят", "восемьдесят", "девяносто"
        )

        private val ONES_UZ = arrayOf(
            "nol", "bir", "ikki", "uch", "to'rt", "besh", "olti", "yetti", "sakkiz", "to'qqiz",
            "o'n", "o'n bir", "o'n ikki", "o'n uch", "o'n to'rt", "o'n besh",
            "o'n olti", "o'n yetti", "o'n sakkiz", "o'n to'qqiz"
        )

        private val TENS_UZ = arrayOf(
            "", "o'n", "yigirma", "o'ttiz", "qirq", "ellik",
            "oltmish", "yetmish", "sakson", "to'qson"
        )
    }
}