package com.tetragon.app.utils.voiceReader

import com.tetragon.app.R

/**
 * Turns any number into a list of Uzbek word clips.
 *
 * Uzbek numerals are completely regular, so 21 recordings cover 0..999 999:
 *   11  -> o'n + bir
 *   47  -> qirq + yetti
 *   345 -> uch + yuz + qirq + besh
 *   2050 -> ikki + ming + ellik
 *
 * That is why a random target number needs no per-number MP3.
 */
object UzNumberAudio {

    private val ZERO = VoiceClip(R.raw.uz_num_0, "nol")
    private val YUZ = VoiceClip(R.raw.uz_num_100, "yuz")
    private val MING = VoiceClip(R.raw.uz_num_1000, "ming")

    /** index = digit, 1..9 */
    private val ONES = arrayOf(
        ZERO,
        VoiceClip(R.raw.uz_num_1, "bir"),
        VoiceClip(R.raw.uz_num_2, "ikki"),
        VoiceClip(R.raw.uz_num_3, "uch"),
        VoiceClip(R.raw.uz_num_4, "to'rt"),
        VoiceClip(R.raw.uz_num_5, "besh"),
        VoiceClip(R.raw.uz_num_6, "olti"),
        VoiceClip(R.raw.uz_num_7, "yetti"),
        VoiceClip(R.raw.uz_num_8, "sakkiz"),
        VoiceClip(R.raw.uz_num_9, "to'qqiz")
    )

    /** index = tens digit, 1..9 */
    private val TENS = arrayOf(
        ZERO,
        VoiceClip(R.raw.uz_num_10, "o'n"),
        VoiceClip(R.raw.uz_num_20, "yigirma"),
        VoiceClip(R.raw.uz_num_30, "o'ttiz"),
        VoiceClip(R.raw.uz_num_40, "qirq"),
        VoiceClip(R.raw.uz_num_50, "ellik"),
        VoiceClip(R.raw.uz_num_60, "oltmish"),
        VoiceClip(R.raw.uz_num_70, "yetmish"),
        VoiceClip(R.raw.uz_num_80, "sakson"),
        VoiceClip(R.raw.uz_num_90, "to'qson")
    )

    /** @return the clips that spell [value] out loud, in order. */
    fun clips(value: Int): List<VoiceClip> {
        val n = if (value < 0) -value else value
        if (n == 0) return listOf(ZERO)

        val out = ArrayList<VoiceClip>(6)
        var rest = n

        if (rest >= 1000) {
            val thousands = rest / 1000
            // "ming" alone means 1000; "ikki ming" for 2000
            if (thousands > 1) out += clips(thousands)
            out += MING
            rest %= 1000
        }
        if (rest >= 100) {
            val hundreds = rest / 100
            if (hundreds > 1) out += ONES[hundreds]
            out += YUZ
            rest %= 100
        }
        if (rest >= 10) {
            out += TENS[rest / 10]
            rest %= 10
        }
        if (rest > 0) {
            out += ONES[rest]
        }
        return out
    }
}