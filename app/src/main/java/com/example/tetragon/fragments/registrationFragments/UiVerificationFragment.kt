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

        val email = (activity as? RegisterActivity)?.userData?.email ?: "your email"

        val fullText = "We've sent a verification link to $email."

        val spannable = SpannableString(fullText)

        val start = fullText.indexOf(email)
        val end = start + email.length

        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        spannable.setSpan(
            ForegroundColorSpan(blueColor),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        textViewInstructions.text = spannable

        handler.post(checkVerificationRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(checkVerificationRunnable)
    }
}