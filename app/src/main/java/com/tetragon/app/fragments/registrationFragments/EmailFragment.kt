package com.tetragon.app.fragments.registrationFragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.tetragon.app.R
import com.tetragon.app.ui.RegisterActivity

class EmailFragment : Fragment() {

    private lateinit var emailEditText: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_email, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emailEditText = view.findViewById(R.id.emailEditText)

        val activity = activity as? RegisterActivity
        val savedEmail = activity?.userData?.email ?: ""
        if (savedEmail.isNotEmpty()) {
            emailEditText.setText(savedEmail)
            emailEditText.setSelection(savedEmail.length)
            validateEmail(savedEmail)
        }

        emailEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {}
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val input = p0.toString().trim()
                validateEmail(input)
            }
        })
    }

    fun setControlsEnabled(enabled: Boolean) {
        emailEditText.isEnabled = enabled
    }

    private fun validateEmail(email: String) {
        val activity = activity as? RegisterActivity
        val isValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

        if (isValid) {
            activity?.userData?.email = email
            activity?.setContinueButtonEnabled(true)
        } else {
            activity?.setContinueButtonEnabled(false)
        }
    }
}