package com.tetragon.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.actionCodeSettings
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityForgotPasswordBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity

class ForgotPasswordActivity : BaseActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val auth = FirebaseAuth.getInstance()
    private var isWaitingForEmail = false // Track if email was sent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTextWatcher()

        binding.continueEnabledBtn.setOnClickListener {
            if (isWaitingForEmail) {
                // If the email was already sent, this button now acts as "Back"
                finish()
            } else {
                // Perform validation and send the email
                val email = binding.emailEditText.text.toString().trim()
                if (TextUtils.isEmpty(email)) {
                    Toast.makeText(this, getString(R.string.enter_email_reset), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                sendResetEmail(email)
            }
        }

        binding.backBtn.setOnClickListener { finish() }
    }

    private fun setupTextWatcher() {
        binding.emailEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Only toggle button if we aren't in the "Waiting" phase
                if (!isWaitingForEmail) {
                    val emailValid = s.toString().trim().isNotEmpty()
                    binding.continueEnabledBtnContainer.visibility = if (emailValid) View.VISIBLE else View.INVISIBLE
                    binding.continueDisabledBtnContainer.visibility = if (emailValid) View.INVISIBLE else View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun sendResetEmail(email: String) {
        isWaitingForEmail = true
        // Updated to use string resource
        setUiProcessingState(true, getString(R.string.sending_text))

        val actionCodeSettings = actionCodeSettings {
            url = "https://tetragon-dacc5.firebaseapp.com"
            handleCodeInApp = true
            setAndroidPackageName("com.tetragon.app", true, null)
        }

        auth.sendPasswordResetEmail(email, actionCodeSettings).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                binding.emailEditText.isEnabled = false
                binding.infoInstructionsText.text = getString(R.string.check_email_spam_instructions)

                binding.continueEnabledBtnContainer.visibility = View.VISIBLE
                binding.continueDisabledBtnContainer.visibility = View.GONE
                binding.continueEnabledBtn.text = getString(R.string.back_button_text)

                Toast.makeText(this, getString(R.string.reset_link_sent_success), Toast.LENGTH_SHORT).show()
            } else {
                isWaitingForEmail = false
                setUiProcessingState(false, getString(R.string.continue_text))
                Toast.makeText(this, "${getString(R.string.error_prefix)} ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
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