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
        // 1. Save the selection
        LocaleHelper.setLocale(requireContext(), lang)

        // 2. Show the visual selection in the UI
        highlightSelectedLanguage(lang)

        // 3. ONLY now enable the continue button in the RegisterActivity
        (activity as? RegisterActivity)?.setContinueButtonEnabled(true)
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