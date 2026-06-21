package com.tetragon.app.fragments.registrationFragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.databinding.FragmentLanguageBinding
import com.tetragon.app.ui.RegisterActivity
import com.tetragon.app.utils.languageChangeUtils.LocaleHelper

class LanguageFragment : Fragment() {
    private var _binding: FragmentLanguageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // We do NOT call highlightSelectedLanguage or setContinueButtonEnabled here.
        // The UI stays neutral, and the button stays disabled until a click occurs.

        binding.english.setOnClickListener { onLanguageSelected("en") }
        binding.russian.setOnClickListener { onLanguageSelected("ru") }
        binding.uzbek.setOnClickListener { onLanguageSelected("uz") }
    }

    private fun onLanguageSelected(lang: String) {
        val registerActivity = activity as? RegisterActivity ?: return

        // 1. Persist the selection to SharedPreferences via LocaleHelper
        LocaleHelper.setLocale(requireContext(), lang)

        // 2. Store the chosen language code on the shared UserData model
        registerActivity.userData.language = lang

        // 3. Apply the locale immediately to the live activity Resources so that
        //    all subsequent getString() calls (header titles, button labels, etc.)
        //    reflect the new language right away — no activity recreation needed.
        registerActivity.applyLocaleInPlace(lang)

        // 4. Show the visual selection highlight in the UI
        highlightSelectedLanguage(lang)

        // 5. Enable the continue button now that a valid selection has been made
        registerActivity.setContinueButtonEnabled(true)
    }

    private fun highlightSelectedLanguage(lang: String) {
        val layouts = listOf(binding.english, binding.russian, binding.uzbek)

        // Reset all items to default state
        layouts.forEach {
            it.setBackgroundResource(R.drawable.custom_background)
            val textView = (it as? ViewGroup)?.getChildAt(1) as? TextView
            textView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
        }

        // Apply blue style to the chosen one
        val selected = when (lang) {
            "en" -> binding.english
            "ru" -> binding.russian
            "uz" -> binding.uzbek
            else -> null
        }

        selected?.let {
            it.setBackgroundResource(R.drawable.custom_background_selected)
            val textView = (it as? ViewGroup)?.getChildAt(1) as? TextView
            textView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_1))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}