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
import android.widget.LinearLayout
import com.tetragon.app.R
import com.tetragon.app.ui.RegisterActivity

class EmailFragment : Fragment() {

    private lateinit var emailEditText: EditText
    private lateinit var googleSignUpButton: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_email, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emailEditText = view.findViewById(R.id.emailEditText)
        googleSignUpButton = view.findViewById(R.id.googleSignUpButton)

        val activity = activity as? RegisterActivity
        val savedEmail = activity?.userData?.email ?: ""
        if (savedEmail.isNotEmpty()) {
            emailEditText.setText(savedEmail)
            emailEditText.setSelection(savedEmail.length)
        }

        googleSignUpButton.setOnClickListener {
            val registerActivity = activity as? RegisterActivity ?: return@setOnClickListener
            registerActivity.triggerGoogleRegistration()
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

    /**
     * Toggles the interactivity and visual state of the fragment inputs
     * during active background authentication requests.
     */
    fun setControlsEnabled(enabled: Boolean) {
        emailEditText.isEnabled = enabled
        googleSignUpButton.isEnabled = enabled
        googleSignUpButton.alpha = if (enabled) 1.0f else 0.5f
    }

    // Maintained public exposure to hook into RegisterActivity recovery pipelines safely
    fun restoreGoogleButtonState() {
        setControlsEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        restoreGoogleButtonState()
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