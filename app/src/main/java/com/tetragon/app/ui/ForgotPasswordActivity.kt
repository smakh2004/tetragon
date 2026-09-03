package com.tetragon.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityForgotPasswordBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity

class ForgotPasswordActivity : BaseActivity() {

    companion object {
        const val EXTRA_PREFILL_EMAIL = "prefill_email"
        private const val KEY_WAITING = "waiting_for_email"
        private const val KEY_SENDING = "sending_in_progress"
    }

    private lateinit var binding: ActivityForgotPasswordBinding
    private val auth = FirebaseAuth.getInstance()

    private var isWaitingForEmail = false   // reset link already sent
    private var isSending = false           // request in flight

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Rotating after sending used to wipe the "sent" state: the field re-enabled
        // itself, the instructions reverted and the button went back to "Continue",
        // which made it look like nothing had happened.
        isWaitingForEmail = savedInstanceState?.getBoolean(KEY_WAITING, false) ?: false
        isSending = savedInstanceState?.getBoolean(KEY_SENDING, false) ?: false

        intent.getStringExtra(EXTRA_PREFILL_EMAIL)?.takeIf { it.isNotBlank() }?.let {
            binding.emailEditText.setText(it)
            binding.emailEditText.setSelection(it.length)
        }

        setupTextWatcher()

        binding.continueEnabledBtn.setOnClickListener {
            if (isSending) return@setOnClickListener

            if (isWaitingForEmail) {
                // The email was already sent — this button now acts as "Back".
                finish()
                return@setOnClickListener
            }

            val email = binding.emailEditText.text.toString().trim()
            if (!isEmailValid(email)) {
                Toast.makeText(this, getString(R.string.enter_email_reset), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendResetEmail(email)
        }

        binding.backBtn.setOnClickListener { finish() }

        if (isWaitingForEmail) showSentState() else refreshButtonState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAITING, isWaitingForEmail)
        outState.putBoolean(KEY_SENDING, isSending)
    }

    private fun setupTextWatcher() {
        binding.emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isWaitingForEmail && !isSending) refreshButtonState()
            }
        })
    }

    /**
     * The old version only checked for a non-empty string, so "abc" enabled the button and
     * the malformed-email error only surfaced as a raw Firebase message afterwards.
     */
    private fun isEmailValid(email: String): Boolean =
        email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun refreshButtonState() {
        val valid = isEmailValid(binding.emailEditText.text.toString().trim())
        binding.continueEnabledBtnContainer.visibility = if (valid) View.VISIBLE else View.INVISIBLE
        binding.continueDisabledBtnContainer.visibility = if (valid) View.INVISIBLE else View.VISIBLE
        binding.continueDisabledBtn.text = getString(R.string.continue_text)
    }

    private fun sendResetEmail(email: String) {
        // isWaitingForEmail used to be set here, before the network call had returned.
        isSending = true
        setUiProcessingState(true, getString(R.string.sending_text))

        // Firebase's own hosted reset page (no Dynamic Links, no in-app deep-link
        // handler needed) fully completes the reset in the browser.
        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            isSending = false

            // A "user not found" failure would tell anyone holding this screen which
            // addresses have accounts. Show the same confirmation either way.
            val treatAsSent = task.isSuccessful || task.exception is FirebaseAuthInvalidUserException

            if (treatAsSent) {
                isWaitingForEmail = true
                showSentState()
                Toast.makeText(this, getString(R.string.reset_link_sent_success), Toast.LENGTH_SHORT).show()
            } else {
                setUiProcessingState(false, getString(R.string.continue_text))
                refreshButtonState()
                // error_prefix carries a %1$s placeholder. Calling getString() without the
                // argument printed the placeholder itself into the toast.
                Toast.makeText(
                    this,
                    getString(R.string.error_prefix, task.exception?.message ?: "Unknown"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showSentState() {
        binding.emailEditText.isEnabled = false
        binding.infoInstructionsText.text = getString(R.string.check_email_spam_instructions)

        binding.continueEnabledBtnContainer.visibility = View.VISIBLE
        binding.continueDisabledBtnContainer.visibility = View.GONE
        binding.continueEnabledBtn.text = getString(R.string.back_button_text)
    }

    private fun setUiProcessingState(processing: Boolean, label: String) {
        if (processing) {
            binding.continueEnabledBtnContainer.visibility = View.INVISIBLE
            binding.continueDisabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtn.text = label
        } else {
            binding.continueEnabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtnContainer.visibility = View.INVISIBLE
        }
    }
}