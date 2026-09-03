package com.tetragon.app.utils.voiceReader

import com.tetragon.app.R

/**
 * The Uzbek clip recipes, one per spoken line. Each recipe mirrors the exact
 * values-uz wording of its string resource — if a string changes, its recipe
 * has to change with it.
 *
 * A recipe returns an empty list when it cannot cover the value it was given;
 * TeacherSpeech then reads the line through TTS instead of going silent.
 *
 * SUFFIXES. Uzbek fuses case endings onto the number and the result depends on
 * the stem, so a suffix can never be its own clip. Every suffixed form the app
 * needs is recorded whole — hence the parallel [COUNTERS] (-ta), [UPTO] (-gacha)
 * and [DATIVE] (-ga) sets rather than one number set plus suffix clips. Where a
 * line embeds an arbitrary number, the Uzbek wording keeps it bare (suffix on a
 * following noun, or no suffix at all) so [UzNumberAudio] can spell any value.
 */
object UzPhrases {

    // ------------------------------------------------- apple count (easy)
    // "%1$d ta olma ko'rsat" / "Qara! Mana ekrandagi %1$d ta olma."
    private val OLMA_KORSAT = VoiceClip(R.raw.uz_ph_olma_korsat, "olma ko'rsat")
    private val QARA_MANA_EKRANDAGI =
        VoiceClip(R.raw.uz_ph_qara_mana_ekrandagi, "Qara! Mana ekrandagi")
    private val OLMA = VoiceClip(R.raw.uz_ph_olma, "olma")

    /** "-ta" counter forms ("beshta olma"). "bitta" is irregular, not bir + ta. */
    private val COUNTERS: Map<Int, VoiceClip> = mapOf(
        1 to VoiceClip(R.raw.uz_cnt_1, "bitta"),
        2 to VoiceClip(R.raw.uz_cnt_2, "ikkita"),
        3 to VoiceClip(R.raw.uz_cnt_3, "uchta"),
        4 to VoiceClip(R.raw.uz_cnt_4, "to'rtta"),
        5 to VoiceClip(R.raw.uz_cnt_5, "beshta"),
        6 to VoiceClip(R.raw.uz_cnt_6, "oltita"),
        7 to VoiceClip(R.raw.uz_cnt_7, "yettita"),
        8 to VoiceClip(R.raw.uz_cnt_8, "sakkizta"),
        9 to VoiceClip(R.raw.uz_cnt_9, "to'qqizta"),
        10 to VoiceClip(R.raw.uz_cnt_10, "o'nta")
    )

    // ------------------------------------------- largest / smallest (medium)
    private val ENG_KATTA_SON = VoiceClip(R.raw.uz_ph_eng_katta_son, "Eng katta sonni toping")
    private val ENG_KICHIK_SON = VoiceClip(R.raw.uz_ph_eng_kichik_son, "Eng kichik sonni toping")

    // ------------------------------------------- find missed number (medium)
    private val TUSHIRIB_QOLDIRILGAN_SON =
        VoiceClip(R.raw.uz_ph_tushirib_qoldirilgan_son, "Tushirib qoldirilgan sonni toping")

    // -------------------------------------------- next / previous number (hard)
    // "%1$s sonidan keyingi sonni toping." / "%1$s sonidan oldingi sonni toping."
    // The suffix sits on "son", not on the number, so the number stays bare.
    private val SONIDAN_KEYINGI =
        VoiceClip(R.raw.uz_ph_sonidan_keyingi, "sonidan keyingi sonni toping")
    private val SONIDAN_OLDINGI =
        VoiceClip(R.raw.uz_ph_sonidan_oldingi, "sonidan oldingi sonni toping")

    // ------------------------------------------------ connect numbers (hard)
    // "1 dan %1$d gacha bo'lgan sonlarni ulang"
    private val BIRDAN = VoiceClip(R.raw.uz_ph_birdan, "Birdan")
    private val BOLGAN_SONLARNI_ULANG =
        VoiceClip(R.raw.uz_ph_bolgan_sonlarni_ulang, "bo'lgan sonlarni ulang")

    // "Eng kichik sondan boshlang va %1$d ga yetguningizcha ..."
    private val ENG_KICHIK_SONDAN_BOSHLANG =
        VoiceClip(R.raw.uz_ph_eng_kichik_sondan_boshlang, "Eng kichik sondan boshlang va")
    private val YETGUNINGIZCHA = VoiceClip(
        R.raw.uz_ph_yetguningizcha,
        "yetguningizcha har safar keyingi songa birni qo'shib boring"
    )

    /** "-gacha" forms, for the connect question. Only 5..10 are ever asked for. */
    private val UPTO: Map<Int, VoiceClip> = mapOf(
        5 to VoiceClip(R.raw.uz_num_5_gacha, "beshgacha"),
        6 to VoiceClip(R.raw.uz_num_6_gacha, "oltigacha"),
        7 to VoiceClip(R.raw.uz_num_7_gacha, "yettigacha"),
        8 to VoiceClip(R.raw.uz_num_8_gacha, "sakkizgacha"),
        9 to VoiceClip(R.raw.uz_num_9_gacha, "to'qqizgacha"),
        10 to VoiceClip(R.raw.uz_num_10_gacha, "o'ngacha")
    )

    /** "-ga" (dative) forms, for the connect solution: "o'nga yetguningizcha". */
    private val DATIVE: Map<Int, VoiceClip> = mapOf(
        5 to VoiceClip(R.raw.uz_num_5_ga, "beshga"),
        6 to VoiceClip(R.raw.uz_num_6_ga, "oltiga"),
        7 to VoiceClip(R.raw.uz_num_7_ga, "yettiga"),
        8 to VoiceClip(R.raw.uz_num_8_ga, "sakkizga"),
        9 to VoiceClip(R.raw.uz_num_9_ga, "to'qqizga"),
        10 to VoiceClip(R.raw.uz_num_10_ga, "o'nga")
    )

    // ------------------------------------------------------ count by (hard)
    // "Har safar %1$d ta qo'shib sanang."
    // "Har safar %1$d ta qo'shing. Bo'sh kataklar: %3$d va %5$d."
    private val HAR_SAFAR = VoiceClip(R.raw.uz_ph_har_safar, "Har safar")
    private val QOSHIB_SANANG = VoiceClip(R.raw.uz_ph_qoshib_sanang, "qo'shib sanang")
    private val QOSHING_BOSH_KATAKLAR =
        VoiceClip(R.raw.uz_ph_qoshing_bosh_kataklar, "qo'shing. Bo'sh kataklar:")
    private val VA = VoiceClip(R.raw.uz_ph_va, "va")

    // ------------------------------------------------------------- shared
    private val TRY_AGAIN = VoiceClip(
        R.raw.uz_ph_try_again,
        "Xavotir olma, yana urinib ko'r! Yoki «Yechimni ko'rish» tugmasini bos, men tushuntiraman."
    )
    private val JAVOB = VoiceClip(R.raw.uz_ph_javob, "Javob")

    /**
     * The "-ta" counted form of [n] — what goes in front of a noun.
     * 1..10 are single clips; 11..19 compose as "o'n" + counter ("o'n bitta").
     */
    fun counted(n: Int): List<VoiceClip> = when (n) {
        in 1..10 -> listOf(COUNTERS.getValue(n))
        in 11..19 -> UzNumberAudio.clips(10) + COUNTERS.getValue(n - 10)
        else -> emptyList()
    }

    /** "<n> ta olma ko'rsat" */
    fun showApples(count: Int): List<VoiceClip> {
        val counted = counted(count)
        if (counted.isEmpty()) return emptyList()
        return counted + OLMA_KORSAT
    }

    /** "Qara! Mana ekrandagi <n> ta olma." */
    fun applesSolution(count: Int): List<VoiceClip> {
        val counted = counted(count)
        if (counted.isEmpty()) return emptyList()
        return listOf(QARA_MANA_EKRANDAGI) + counted + OLMA
    }

    /** "Eng katta sonni toping" */
    fun findLargestNumber(): List<VoiceClip> = listOf(ENG_KATTA_SON)

    /** "Eng kichik sonni toping" */
    fun findSmallestNumber(): List<VoiceClip> = listOf(ENG_KICHIK_SON)

    /** "Tushirib qoldirilgan sonni toping" */
    fun findMissedNumber(): List<VoiceClip> = listOf(TUSHIRIB_QOLDIRILGAN_SON)

    /** "<n> sonidan keyingi sonni toping." */
    fun nextAfter(base: Int): List<VoiceClip> {
        val number = UzNumberAudio.clips(base)
        if (number.isEmpty()) return emptyList()
        return number + SONIDAN_KEYINGI
    }

    /** "<n> sonidan oldingi sonni toping." */
    fun numberBefore(base: Int): List<VoiceClip> {
        val number = UzNumberAudio.clips(base)
        if (number.isEmpty()) return emptyList()
        return number + SONIDAN_OLDINGI
    }

    /** "1 dan <n> gacha bo'lgan sonlarni ulang" — empty outside 5..10. */
    fun connectNumbers(max: Int): List<VoiceClip> {
        val upto = UPTO[max] ?: return emptyList()
        return listOf(BIRDAN, upto, BOLGAN_SONLARNI_ULANG)
    }

    /**
     * "Eng kichik sondan boshlang va <n> ga yetguningizcha har safar keyingi
     * songa 1 ni qo'shib boring." — empty outside 5..10.
     */
    fun connectNumbersSolution(max: Int): List<VoiceClip> {
        val dative = DATIVE[max] ?: return emptyList()
        return listOf(ENG_KICHIK_SONDAN_BOSHLANG, dative, YETGUNINGIZCHA)
    }

    /** "Har safar <skip> ta qo'shib sanang." */
    fun countBy(skip: Int): List<VoiceClip> {
        val counted = counted(skip)
        if (counted.isEmpty()) return emptyList()
        return listOf(HAR_SAFAR) + counted + QOSHIB_SANANG
    }

    /**
     * "Har safar <skip> ta qo'shing. Bo'sh kataklar: <a> va <b>."
     *
     * The two blanks stay in bare form on purpose — they are arbitrary multiples,
     * so a suffixed clip set could not cover them.
     */
    fun countBySolution(skip: Int, a: Int, b: Int): List<VoiceClip> {
        val counted = counted(skip)
        if (counted.isEmpty()) return emptyList()
        val first = UzNumberAudio.clips(a)
        val second = UzNumberAudio.clips(b)
        if (first.isEmpty() || second.isEmpty()) return emptyList()
        return listOf(HAR_SAFAR) + counted + QOSHING_BOSH_KATAKLAR + first + VA + second
    }

    /** "Javob: <n>" — plain number, not counted. */
    fun answerIs(value: Int): List<VoiceClip> =
        listOf(JAVOB) + UzNumberAudio.clips(value)

    /** The long try-again line. */
    fun tryAgain(): List<VoiceClip> = listOf(TRY_AGAIN)
}