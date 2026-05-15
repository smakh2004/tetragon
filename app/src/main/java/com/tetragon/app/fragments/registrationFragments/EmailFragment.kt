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

        emailEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {}
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val input = p0.toString().trim()
                validateEmail(input)
            }
        })
    }

    private fun validateEmail(email: String) {
        val activity = activity as? RegisterActivity

        // Android's built-in email pattern matcher
        val isValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

        if (isValid) {
            // Only update data if it's a real email format
            activity?.userData?.email = email
            activity?.setContinueButtonEnabled(true)
        } else {
            // Disable continue button if format is wrong (e.g., missing @ or .com)
            activity?.setContinueButtonEnabled(false)
        }
    }
}