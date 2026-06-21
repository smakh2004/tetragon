package com.tetragon.app.fragments.registrationFragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.ui.RegisterActivity

class AuthMethodFragment : Fragment() {

    private lateinit var googleSignUpButton: LinearLayout
    private lateinit var emailSignUpButton: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_auth_method, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        googleSignUpButton = view.findViewById(R.id.googleSignUpButton)
        emailSignUpButton = view.findViewById(R.id.emailSignUpButton)

        val parentActivity = activity as? RegisterActivity

        // Explicitly hide parent action button layer since method interaction manages downstream routing context
        parentActivity?.setContinueButtonEnabled(false)

        googleSignUpButton.setOnClickListener {
            parentActivity?.triggerGoogleRegistration()
        }

        emailSignUpButton.setOnClickListener {
            parentActivity?.navigateToManualEmailInput()
        }
    }

    fun setControlsEnabled(enabled: Boolean) {
        googleSignUpButton.isEnabled = enabled
        googleSignUpButton.alpha = if (enabled) 1.0f else 0.5f
        emailSignUpButton.isEnabled = enabled
        emailSignUpButton.alpha = if (enabled) 1.0f else 0.5f
    }
}