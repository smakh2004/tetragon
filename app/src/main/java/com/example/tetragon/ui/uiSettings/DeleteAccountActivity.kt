package com.example.tetragon.ui.uiSettings

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityDeleteAccountBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.ui.WelcomeActivity
import com.example.tetragon.utils.registrationUtils.PasswordToggleHelper
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DeleteAccountActivity : BaseActivity() {

    private lateinit var binding: ActivityDeleteAccountBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var countdownSeconds = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeleteAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // BACK BUTTON
        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // CANCEL BUTTON
        binding.cancelButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // PASSWORD EYE TOGGLE
        PasswordToggleHelper.attach(
            binding.passwordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed
        )

        // PASSWORD TEXT CHANGE → start countdown
        binding.passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Immediately hide enabled button
                binding.deleteEnabledBtnContainer.visibility = View.INVISIBLE
                binding.deleteDisabledBtnContainer.visibility = View.VISIBLE

                // Cancel previous countdown
                countdownRunnable?.let { handler.removeCallbacks(it) }

                if (!s.isNullOrEmpty()) {
                    countdownSeconds = 3
                    binding.deleteDisabledBtn.text = "DELETE ACCOUNT ($countdownSeconds)"

                    countdownRunnable = object : Runnable {
                        override fun run() {
                            countdownSeconds--
                            if (countdownSeconds > 0) {
                                binding.deleteDisabledBtn.text = "DELETE ACCOUNT ($countdownSeconds)"
                                handler.postDelayed(this, 1000)
                            } else {
                                // Enable the delete button
                                binding.deleteEnabledBtnContainer.visibility = View.VISIBLE
                                binding.deleteDisabledBtnContainer.visibility = View.INVISIBLE
                            }
                        }
                    }
                    handler.postDelayed(countdownRunnable!!, 1000)
                } else {
                    // No input → reset
                    binding.deleteDisabledBtn.text = "DELETE"
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // DELETE BUTTON CLICK
        binding.deleteEnabledBtn.setOnClickListener {
            val password = binding.passwordEditText.text.toString()
            val user = auth.currentUser
            if (user == null || user.email.isNullOrEmpty()) {
                Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button while deleting & show LOADING.. text
            binding.deleteEnabledBtn.isEnabled = false
            binding.deleteDisabledBtnContainer.visibility = View.VISIBLE
            binding.deleteEnabledBtnContainer.visibility = View.INVISIBLE
            binding.deleteDisabledBtn.text = "LOADING.."

            // Reauthenticate
            val credential = EmailAuthProvider.getCredential(user.email!!, password)
            user.reauthenticate(credential)
                .addOnSuccessListener {
                    // Delete Firestore data
                    db.collection("users").document(user.uid)
                        .delete()
                        .addOnCompleteListener { _ ->
                            // Delete Firebase account
                            user.delete()
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show()
                                    auth.signOut()

                                    // Clear all previous activities and navigate to WelcomeActivity
                                    val intent = Intent(this, WelcomeActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish() // finish current activity
                                }
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Reauthentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                    resetDeleteButton()
                }

        }
    }

    private fun resetDeleteButton() {
        binding.deleteEnabledBtn.isEnabled = true
        binding.deleteEnabledBtnContainer.visibility = View.INVISIBLE
        binding.deleteDisabledBtnContainer.visibility = View.VISIBLE
        binding.deleteDisabledBtn.text = "DELETE"
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
    }
}
