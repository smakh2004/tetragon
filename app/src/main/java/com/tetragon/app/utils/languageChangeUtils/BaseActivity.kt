package com.tetragon.app.utils.languageChangeUtils

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        val context = LocaleHelper.setLocaleContext(newBase, lang)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Включаем отображение от края до края
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

    }
}