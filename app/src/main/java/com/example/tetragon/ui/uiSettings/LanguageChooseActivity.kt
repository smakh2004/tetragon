package com.example.tetragon.ui.uiSettings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.tetragon.MainActivity
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityLanguageChooseBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.languageChangeUtils.LocaleHelper

class LanguageChooseActivity : BaseActivity() {
    private lateinit var binding: ActivityLanguageChooseBinding

    // Track currently selected language
    private var selectedLang: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLanguageChooseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read saved language, default to system language if none
        val savedLang = LocaleHelper.getLanguage(this)
        selectedLang = savedLang

        // Highlight UI based on saved language
        highlightSelectedLanguage(selectedLang)
        binding.saveEnabledBtnContainer.visibility = View.INVISIBLE
        binding.saveDisabledBtnContainer.visibility = View.VISIBLE
        binding.saveDisabledBtn.text = "SAVE"

        // Back button
        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Language click listeners
        binding.english.setOnClickListener { onLanguageSelected("en") }
        binding.russian.setOnClickListener { onLanguageSelected("ru") }
        binding.uzbek.setOnClickListener { onLanguageSelected("uz") }

        // Save button
        binding.saveEnabledBtn.setOnClickListener {
            binding.saveEnabledBtnContainer.visibility = View.INVISIBLE
            binding.saveDisabledBtnContainer.visibility = View.VISIBLE
            binding.saveDisabledBtn.text = "LOADING.."

            LocaleHelper.setLocale(this, selectedLang)

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun onLanguageSelected(lang: String) {
        selectedLang = lang
        highlightSelectedLanguage(lang)

        // Enable save button only if selectedLang != saved language
        val savedLang = LocaleHelper.getLanguage(this)
        if (selectedLang != savedLang) {
            binding.saveEnabledBtnContainer.visibility = View.VISIBLE
            binding.saveDisabledBtnContainer.visibility = View.INVISIBLE
            binding.saveDisabledBtn.text = "SAVE"
        } else {
            binding.saveEnabledBtnContainer.visibility = View.INVISIBLE
            binding.saveDisabledBtnContainer.visibility = View.VISIBLE
            binding.saveDisabledBtn.text = "SAVE"
        }
    }

    private fun highlightSelectedLanguage(lang: String) {
        // Reset all first
        binding.english.setBackgroundResource(R.drawable.custom_background)
        binding.russian.setBackgroundResource(R.drawable.custom_background)
        binding.uzbek.setBackgroundResource(R.drawable.custom_background)

        (binding.english.getChildAt(0) as? TextView)?.setTextColor(getColor(R.color.text_color))
        (binding.russian.getChildAt(0) as? TextView)?.setTextColor(getColor(R.color.text_color))
        (binding.uzbek.getChildAt(0) as? TextView)?.setTextColor(getColor(R.color.text_color))

        // Highlight selected
        when (lang) {
            "en" -> {
                binding.english.setBackgroundResource(R.drawable.custom_background_selected)
                (binding.english.getChildAt(0) as? TextView)?.setTextColor(getColor(R.color.blue_1))
            }
            "ru" -> {
                binding.russian.setBackgroundResource(R.drawable.custom_background_selected)
                (binding.russian.getChildAt(0) as? TextView)?.setTextColor(getColor(R.color.blue_1))
            }
            "uz" -> {
                binding.uzbek.setBackgroundResource(R.drawable.custom_background_selected)
                (binding.uzbek.getChildAt(0) as? TextView)?.setTextColor(getColor(R.color.blue_1))
            }
        }
    }

}
