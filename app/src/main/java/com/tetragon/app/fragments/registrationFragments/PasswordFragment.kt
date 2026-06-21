package com.tetragon.app.fragments.registrationFragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.tetragon.app.utils.registrationUtils.PasswordToggleHelper
import com.tetragon.app.R
import com.tetragon.app.ui.RegisterActivity

class PasswordFragment : Fragment() {

    private lateinit var passwordEditText: EditText
    private var isPasswordCurrentlyVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_password, container, false)
    }

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

        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {}
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                onTextPresent(!p0.isNullOrEmpty())
            }
        })
    }

    private fun onTextPresent(hasText: Boolean) {
        val activity = activity as? RegisterActivity
        activity?.userData?.password = passwordEditText.text.toString()
        activity?.setContinueButtonEnabled(hasText)
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