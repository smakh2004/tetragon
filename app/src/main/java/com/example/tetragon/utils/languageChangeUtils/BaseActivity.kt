package com.example.tetragon.utils.languageChangeUtils

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        val context = LocaleHelper.setLocaleContext(newBase, lang)
        super.attachBaseContext(context)
    }
}