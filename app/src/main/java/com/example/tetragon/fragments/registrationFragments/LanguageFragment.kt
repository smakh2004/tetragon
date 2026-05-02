package com.example.tetragon.fragments.registrationFragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.databinding.FragmentLanguageBinding
import com.example.tetragon.ui.RegisterActivity
import com.example.tetragon.utils.languageChangeUtils.LocaleHelper

class LanguageFragment : Fragment() {
    private var _binding: FragmentLanguageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Highlight based on currently saved language
        val currentLang = LocaleHelper.getLanguage(requireContext())
        highlightSelectedLanguage(currentLang)

        binding.english.setOnClickListener { onLanguageSelected("en") }
        binding.russian.setOnClickListener { onLanguageSelected("ru") }
        binding.uzbek.setOnClickListener { onLanguageSelected("uz") }
    }

    private fun onLanguageSelected(lang: String) {
        LocaleHelper.setLocale(requireContext(), lang)
        highlightSelectedLanguage(lang)
        // Notify Activity that picking a language allows them to continue
        (activity as? RegisterActivity)?.setContinueButtonEnabled(true)
    }

    private fun highlightSelectedLanguage(lang: String) {
        val layouts = listOf(binding.english, binding.russian, binding.uzbek)
        layouts.forEach {
            it.setBackgroundResource(R.drawable.custom_background)
            ((it as ViewGroup).getChildAt(1) as TextView).setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
        }

        val selected = when (lang) {
            "en" -> binding.english
            "ru" -> binding.russian
            "uz" -> binding.uzbek
            else -> null
        }

        selected?.let {
            it.setBackgroundResource(R.drawable.custom_background_selected)
            ((it as ViewGroup).getChildAt(1) as TextView).setTextColor(ContextCompat.getColor(requireContext(), R.color.blue_1))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}