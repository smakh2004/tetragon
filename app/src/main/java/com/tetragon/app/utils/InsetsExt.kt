package com.tetragon.app.utils

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Добавляет системные отступы (статус-бар, навбар, вырез) к ИСХОДНЫМ паддингам вью.
 * Исходные паддинги запоминаются один раз, поэтому повторные вызовы не накапливаются.
 */
fun View.applySystemBarsPadding(
    left: Boolean = true,
    top: Boolean = true,
    right: Boolean = true,
    bottom: Boolean = true
) {
    val initial = Insets.of(paddingLeft, paddingTop, paddingRight, paddingBottom)

    ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(
            initial.left + if (left) bars.left else 0,
            initial.top + if (top) bars.top else 0,
            initial.right + if (right) bars.right else 0,
            initial.bottom + if (bottom) bars.bottom else 0
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

/**
 * То же самое, но с учётом клавиатуры — для экранов с полями ввода
 * (шаги регистрации, ввод ответа в Math Storm).
 */
fun View.applySystemBarsAndImePadding() {
    val initial = Insets.of(paddingLeft, paddingTop, paddingRight, paddingBottom)

    ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
        v.setPadding(
            initial.left + bars.left,
            initial.top + bars.top,
            initial.right + bars.right,
            initial.bottom + maxOf(bars.bottom, ime.bottom)
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}