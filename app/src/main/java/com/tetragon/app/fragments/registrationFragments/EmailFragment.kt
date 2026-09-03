package com.tetragon.app.fragments.registrationFragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.ui.RegisterActivity

class EmailFragment : Fragment() {

    private lateinit var emailEditText: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_email, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emailEditText = view.findViewById(R.id.emailEditText)

        val savedEmail = (activity as? RegisterActivity)?.userData?.email.orEmpty()
        if (savedEmail.isNotEmpty()) {
            emailEditText.setText(savedEmail)
            emailEditText.setSelection(savedEmail.length)
        }
        validateEmail(emailEditText.text?.toString().orEmpty())

        emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateEmail(s?.toString().orEmpty())
            }
        })
    }

    fun setControlsEnabled(enabled: Boolean) {
        if (::emailEditText.isInitialized) emailEditText.isEnabled = enabled
    }

    private fun validateEmail(raw: String) {
        val activity = activity as? RegisterActivity ?: return
        val email = raw.trim()
        val isValid = email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

        // Always mirror the field into the model. The old version only wrote on success,
        // so an edited-then-invalid address left a stale value behind that could be sent
        // to createUserWithEmailAndPassword().
        activity.userData.email = if (isValid) email else ""
        activity.setContinueButtonEnabled(isValid)
    }
}