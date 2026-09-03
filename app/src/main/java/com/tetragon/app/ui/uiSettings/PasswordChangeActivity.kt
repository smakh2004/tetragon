package com.tetragon.app.ui.uiSettings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityPasswordChangeBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.registrationUtils.PasswordToggleHelper

class PasswordChangeActivity : BaseActivity() {

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
    }

    private lateinit var binding: ActivityPasswordChangeBinding
    private val auth = FirebaseAuth.getInstance()

    private var isSaving = false
    private var canChangePassword = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.saveDisabledBtn.text = getString(R.string.save)

        setupTextWatchers()
        setupSaveButton()

        binding.backBtn.setOnClickListener {
            if (isSaving) return@setOnClickListener
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        PasswordToggleHelper.attach(
            binding.passwordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed
        )

        PasswordToggleHelper.attach(
            binding.newPasswordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed
        )

        // A Google-registered account has no password to reauthenticate with, so every
        // save attempt on this screen failed with "incorrect password" and there was no
        // way for the user to understand why.
        val user = auth.currentUser
        if (user == null || !hasPasswordProvider(user)) {
            canChangePassword = false
            // Hide the whole LinearLayouts, not just the EditTexts — otherwise the two
            // empty rounded input_container boxes stay on screen with nothing in them.
            binding.passwordInputContainer.visibility = View.GONE
            binding.newPasswordInputContainer.visibility = View.GONE
            // TODO: move to strings.xml for ru/uz translation.
            Toast.makeText(
                this,
                "This account signs in with Google, so it has no password to change.",
                Toast.LENGTH_LONG
            ).show()
        }

        // The original never ran this on start, so the button state came straight from
        // the XML until the first keystroke.
        checkFields()
    }

    private fun hasPasswordProvider(user: FirebaseUser): Boolean =
        user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }

    private fun setupTextWatchers() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = checkFields()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.passwordEditText.addTextChangedListener(watcher)
        binding.newPasswordEditText.addTextChangedListener(watcher)
    }

    private fun checkFields() {
        if (isSaving) return

        // NOT trimmed. Trimming the new password meant Firebase stored a different string
        // from the one the user typed, and their next login with that password failed.
        val current = binding.passwordEditText.text.toString()
        val newPass = binding.newPasswordEditText.text.toString()
        val enable = canChangePassword &&
                current.isNotEmpty() &&
                newPass.length >= MIN_PASSWORD_LENGTH &&
                newPass != current

        binding.saveEnabledBtnContainer.visibility = if (enable) View.VISIBLE else View.INVISIBLE
        binding.saveDisabledBtnContainer.visibility = if (enable) View.INVISIBLE else View.VISIBLE
        binding.saveDisabledBtn.text = getString(R.string.save)
    }

    private fun setupSaveButton() {
        binding.saveEnabledBtn.setOnClickListener {
            if (isSaving || !canChangePassword) return@setOnClickListener

            val currentPassword = binding.passwordEditText.text.toString()
            val newPassword = binding.newPasswordEditText.text.toString()

            if (currentPassword.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword.length < MIN_PASSWORD_LENGTH) {
                Toast.makeText(this, getString(R.string.error_password_length), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = auth.currentUser ?: return@setOnClickListener
            val email = user.email ?: return@setOnClickListener

            isSaving = true
            binding.saveEnabledBtnContainer.visibility = View.INVISIBLE
            binding.saveDisabledBtnContainer.visibility = View.VISIBLE
            binding.saveDisabledBtn.text = getString(R.string.saving_caps)

            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential)
                .addOnSuccessListener {
                    user.updatePassword(newPassword)
                        .addOnSuccessListener {
                            if (isFinishing || isDestroyed) return@addOnSuccessListener
                            Toast.makeText(this, getString(R.string.password_updated), Toast.LENGTH_SHORT).show()
                            binding.passwordEditText.text?.clear()
                            binding.newPasswordEditText.text?.clear()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            val message = if (e is FirebaseAuthWeakPasswordException) {
                                getString(R.string.error_password_length)
                            } else {
                                getString(R.string.password_update_failed, e.message)
                            }
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            resetSaveButton()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, getString(R.string.error_incorrect_password), Toast.LENGTH_SHORT).show()
                    resetSaveButton()
                }
        }
    }

    private fun resetSaveButton() {
        isSaving = false
        binding.saveDisabledBtn.text = getString(R.string.save)
        // Re-derive from the fields instead of unconditionally showing the enabled button.
        checkFields()
    }
}