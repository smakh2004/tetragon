package com.tetragon.app.fragments.registrationFragments

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
import com.google.firebase.auth.FirebaseAuth
import com.tetragon.app.R
import com.tetragon.app.ui.RegisterActivity

class UiVerificationFragment : Fragment() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val handler = Handler(Looper.getMainLooper())

    private var isPollingActive = false
    private var isVerified = false

    private val checkVerificationRunnable = object : Runnable {
        override fun run() {
            if (!isPollingActive || !isAdded) return

            val user = auth.currentUser
            if (user == null) {
                scheduleNextCheck()
                return
            }

            user.reload().addOnCompleteListener {
                // The reload result can land after onPause/onDestroyView. Without this
                // check the loop re-scheduled itself in the background and, combined with
                // the fresh post in onResume, ended up running several times over.
                if (!isPollingActive || !isAdded || activity == null) return@addOnCompleteListener

                if (it.isSuccessful && user.isEmailVerified) {
                    isVerified = true
                    isPollingActive = false

                    val registerActivity = activity as? RegisterActivity
                    registerActivity?.setContinueButtonEnabled(true)
                    registerActivity?.fireMrSquareAnimation("yahoo")
                } else {
                    scheduleNextCheck()
                }
            }
        }
    }

    private fun scheduleNextCheck() {
        handler.removeCallbacks(checkVerificationRunnable)
        handler.postDelayed(checkVerificationRunnable, 3000)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_ui_verification, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textViewInstructions = view.findViewById<TextView>(R.id.textViewInstructions)

        val email = (activity as? RegisterActivity)?.userData?.email ?: "your email"
        val fullText = getString(R.string.verify_email_instructions, email)
        val spannable = SpannableString(fullText)

        val start = fullText.indexOf(email)
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
    }

    override fun onResume() {
        super.onResume()

        if (isVerified) {
            // Already confirmed before the user left the screen — just restore the button.
            (activity as? RegisterActivity)?.setContinueButtonEnabled(true)
            return
        }

        // Force an immediate check when the user returns from their email client.
        isPollingActive = true
        handler.removeCallbacks(checkVerificationRunnable)
        handler.post(checkVerificationRunnable)
    }

    override fun onPause() {
        super.onPause()
        isPollingActive = false
        handler.removeCallbacks(checkVerificationRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isPollingActive = false
        handler.removeCallbacks(checkVerificationRunnable)
    }
}