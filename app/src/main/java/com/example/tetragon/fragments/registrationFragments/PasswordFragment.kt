package com.example.tetragon.fragments.registrationFragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.example.tetragon.utils.registrationUtils.PasswordToggleHelper
import com.example.tetragon.R
import com.example.tetragon.ui.RegisterActivity

class PasswordFragment : Fragment() {

    private lateinit var passwordEditText: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        passwordEditText = view.findViewById(R.id.passwordEditText)

        // Set up password eye toggle functionality
        PasswordToggleHelper.attach(
            passwordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed
        )

        passwordEditText.addTextChangedListener(object : TextWatcher {
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
                if(!p0.isNullOrEmpty()) {
                    onTextPresent(true)
                } else {
                    onTextPresent(false)
                }
            }

        })
    }

    private fun onTextPresent(hasText: Boolean) {
        val activity = activity as? RegisterActivity
        activity?.userData?.password = passwordEditText.text.toString()
        activity?.setContinueButtonEnabled(hasText)
    }
}