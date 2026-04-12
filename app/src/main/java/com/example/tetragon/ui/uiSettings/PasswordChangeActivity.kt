package com.example.tetragon.ui.uiSettings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityPasswordChangeBinding
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.registrationUtils.PasswordToggleHelper

class PasswordChangeActivity : BaseActivity() {

    private lateinit var binding: ActivityPasswordChangeBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTextWatchers()
        setupSaveButton()

        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Set up password eye toggle functionality
        PasswordToggleHelper.attach(
            binding.passwordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed
        )

        // Set up password eye toggle functionality
        PasswordToggleHelper.attach(
            binding.newPasswordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed
        )
    }

    /** Enable / disable save button depending on inputs */
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
        val current = binding.passwordEditText.text.toString().trim()
        val newPass = binding.newPasswordEditText.text.toString().trim()
        val enable = current.isNotEmpty() && newPass.length >= 6

        binding.saveEnabledBtnContainer.visibility = if (enable) View.VISIBLE else View.INVISIBLE
        binding.saveDisabledBtnContainer.visibility = if (enable) View.INVISIBLE else View.VISIBLE
    }

    /** Save button logic with re-authentication */
    private fun setupSaveButton() {
        binding.saveEnabledBtn.setOnClickListener {

            val currentPassword = binding.passwordEditText.text.toString().trim()
            val newPassword = binding.newPasswordEditText.text.toString().trim()

            if (currentPassword.isEmpty() || newPassword.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val user = auth.currentUser ?: return@setOnClickListener
            val email = user.email ?: return@setOnClickListener

            // ✅ Disable button while operation is in progress
            binding.saveEnabledBtnContainer.visibility = View.INVISIBLE
            binding.saveDisabledBtnContainer.visibility = View.VISIBLE
            binding.saveDisabledBtn.text = "SAVING.."

            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential)
                .addOnSuccessListener {
                    user.updatePassword(newPassword)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                            binding.passwordEditText.text?.clear()
                            binding.newPasswordEditText.text?.clear()
                            finish() // Activity closes
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to update password: ${it.message}", Toast.LENGTH_SHORT).show()
                            // ✅ Re-enable button if failed
                            binding.saveEnabledBtnContainer.visibility = View.VISIBLE
                            binding.saveDisabledBtnContainer.visibility = View.INVISIBLE
                            binding.saveDisabledBtn.text = "SAVE"
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                    // ✅ Re-enable button if failed
                    binding.saveEnabledBtnContainer.visibility = View.VISIBLE
                    binding.saveDisabledBtnContainer.visibility = View.INVISIBLE
                    binding.saveDisabledBtn.text = "SAVE"
                }
        }
    }

}
