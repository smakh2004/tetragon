package com.example.tetragon.fragments.registrationFragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.example.tetragon.R
import com.example.tetragon.ui.RegisterActivity

class AgeFragment : Fragment() {

    private lateinit var ageEditText: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_age, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ageEditText = view.findViewById(R.id.ageEditText)

        ageEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                // not implemented
            }

            override fun beforeTextChanged(
                p0: CharSequence?,
                p1: Int,
                p2: Int,
                p3: Int
            ) {
                // not implemented
            }

            override fun onTextChanged(
                p0: CharSequence?,
                p1: Int,
                p2: Int,
                p3: Int
            ) {
                val value = p0?.toString()?.trim() ?: ""

                val activity = activity as? RegisterActivity
                activity?.updateAge(value)

                activity?.setContinueButtonEnabled(value.isNotEmpty())
            }
        })
    }

    private fun onTextPresent(hasText: Boolean) {
        val activity = activity as? RegisterActivity
        activity?.userData?.age = ageEditText.text.toString()
        activity?.setContinueButtonEnabled(hasText)
    }
}