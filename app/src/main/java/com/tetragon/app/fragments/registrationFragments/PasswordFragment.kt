package com.tetragon.app.fragments.registrationFragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.ui.RegisterActivity
import com.tetragon.app.utils.registrationUtils.PasswordToggleHelper

class PasswordFragment : Fragment() {

    private lateinit var passwordEditText: EditText
    private var isPasswordCurrentlyVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_password, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        passwordEditText = view.findViewById(R.id.passwordEditText)

        PasswordToggleHelper.attach(
            passwordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed,
            onVisibilityChanged = { isPasswordVisible ->
                isPasswordCurrentlyVisible = isPasswordVisible
                val registerActivity = activity as? RegisterActivity
                if (isPasswordVisible) {
                    registerActivity?.fireMrSquareAnimation("password_close")
                } else {
                    registerActivity?.fireMrSquareAnimation("password_open")
                }
            }
        )

        // Restore on back navigation, so the user isn't forced to retype.
        val savedPassword = (activity as? RegisterActivity)?.userData?.password.orEmpty()
        if (savedPassword.isNotEmpty()) {
            passwordEditText.setText(savedPassword)
            passwordEditText.setSelection(savedPassword.length)
        }
        validate(passwordEditText.text?.toString().orEmpty())

        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validate(s?.toString().orEmpty())
            }
        })
    }

    private fun validate(password: String) {
        val activity = activity as? RegisterActivity ?: return
        activity.userData.password = password

        // Firebase rejects anything under 6 characters with a weak-password error, which
        // previously only surfaced two screens later as a toast. Block it here instead.
        activity.setContinueButtonEnabled(password.length >= RegisterActivity.MIN_PASSWORD_LENGTH)
    }

    override fun onDestroyView() {
        // If the user navigates away while the password is still visible,
        // fire password_open so MrSquare resets to the eyes-open state.
        if (isPasswordCurrentlyVisible) {
            (activity as? RegisterActivity)?.fireMrSquareAnimation("password_open")
        }
        super.onDestroyView()
    }
}