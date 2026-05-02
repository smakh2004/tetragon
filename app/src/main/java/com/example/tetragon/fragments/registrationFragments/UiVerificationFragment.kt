package com.example.tetragon.fragments.registrationFragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.ui.RegisterActivity
import com.google.firebase.auth.FirebaseAuth

class UiVerificationFragment : Fragment() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val handler = Handler(Looper.getMainLooper())

    private val checkVerificationRunnable = object : Runnable {
        override fun run() {
            val user = auth.currentUser
            if (user == null) {
                handler.postDelayed(this, 3000)
                return
            }

            user.reload().addOnCompleteListener { task ->
                if (isAdded && activity != null) {
                    if (task.isSuccessful && user.isEmailVerified) {
                        (activity as? RegisterActivity)?.setContinueButtonEnabled(true)
                        handler.removeCallbacks(this)
                    } else {
                        handler.postDelayed(this, 3000)
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_ui_verification, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textViewInstructions = view.findViewById<TextView>(R.id.textViewInstructions)

        // 1. Get the email from activity
        val email = (activity as? RegisterActivity)?.userData?.email ?: "your email"

        // 2. Get the localized string and format it with the email
        // This inserts the email into the %1$s position
        val fullText = getString(R.string.verify_email_instructions, email)

        val spannable = SpannableString(fullText)

        // 3. Find the email position within the translated text
        val start = fullText.indexOf(email)

        // Safety check: only apply span if email string was found
        if (start != -1) {
            val end = start + email.length
            val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

            spannable.setSpan(
                ForegroundColorSpan(blueColor),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        textViewInstructions.text = spannable

        handler.post(checkVerificationRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(checkVerificationRunnable)
    }
}