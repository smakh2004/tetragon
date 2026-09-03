package com.tetragon.app.ui.uiSettings

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityDeleteAccountBinding
import com.tetragon.app.ui.WelcomeActivity
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.registrationUtils.PasswordToggleHelper

class DeleteAccountActivity : BaseActivity() {

    private lateinit var binding: ActivityDeleteAccountBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var countdownSeconds = 3

    private var isDeleting = false

    /**
     * True when the signed-in user has no password on the account (registered with
     * Google). The old screen always built an EmailAuthProvider credential, so those
     * users hit "reauthentication failed" no matter what they typed and could never
     * delete their account.
     */
    private var isGoogleOnlyAccount = false

    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleReauthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            resetDeleteButton()
            return@registerForActivityResult
        }
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken.isNullOrEmpty()) {
                Toast.makeText(this, getString(R.string.reauth_failed, ""), Toast.LENGTH_LONG).show()
                resetDeleteButton()
            } else {
                reauthenticateAndDelete(GoogleAuthProvider.getCredential(idToken, null))
            }
        } catch (e: ApiException) {
            Toast.makeText(this, getString(R.string.reauth_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            resetDeleteButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeleteAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = auth.currentUser
        isGoogleOnlyAccount = user != null && hasNoPasswordProvider(user)

        googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )

        binding.backBtn.setOnClickListener {
            if (isDeleting) return@setOnClickListener
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.cancelButton.setOnClickListener {
            if (isDeleting) return@setOnClickListener
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        PasswordToggleHelper.attach(
            binding.passwordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed
        )

        if (isGoogleOnlyAccount) {
            // Nothing to type: confirmation happens through the Google account picker.
            // Hide the whole LinearLayout — hiding only the EditText would leave the
            // empty rounded input_container box on screen.
            binding.passwordInputContainer.visibility = View.GONE
            startDeleteCountdown()
        } else {
            binding.passwordEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (isDeleting) return

                    binding.deleteEnabledBtnContainer.visibility = View.INVISIBLE
                    binding.deleteDisabledBtnContainer.visibility = View.VISIBLE

                    countdownRunnable?.let { handler.removeCallbacks(it) }

                    if (!s.isNullOrEmpty()) {
                        startDeleteCountdown()
                    } else {
                        binding.deleteDisabledBtn.text = getString(R.string.delete_caps)
                    }
                }
            })
        }

        binding.deleteEnabledBtn.setOnClickListener { onDeleteClicked() }
    }

    private fun hasNoPasswordProvider(user: FirebaseUser): Boolean =
        user.providerData.none { it.providerId == EmailAuthProvider.PROVIDER_ID }

    private fun startDeleteCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownSeconds = 3
        binding.deleteEnabledBtnContainer.visibility = View.INVISIBLE
        binding.deleteDisabledBtnContainer.visibility = View.VISIBLE
        binding.deleteDisabledBtn.text = getString(R.string.delete_account_caps, countdownSeconds)

        countdownRunnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                countdownSeconds--
                if (countdownSeconds > 0) {
                    binding.deleteDisabledBtn.text = getString(R.string.delete_account_caps, countdownSeconds)
                    handler.postDelayed(this, 1000)
                } else {
                    binding.deleteEnabledBtnContainer.visibility = View.VISIBLE
                    binding.deleteDisabledBtnContainer.visibility = View.INVISIBLE
                }
            }
        }
        handler.postDelayed(countdownRunnable!!, 1000)
    }

    private fun onDeleteClicked() {
        if (isDeleting) return

        val user = auth.currentUser
        if (user == null || user.email.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_user), Toast.LENGTH_SHORT).show()
            return
        }

        val password = binding.passwordEditText.text.toString()
        if (!isGoogleOnlyAccount && password.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_incorrect_password), Toast.LENGTH_SHORT).show()
            return
        }

        isDeleting = true
        binding.deleteEnabledBtn.isEnabled = false
        binding.deleteDisabledBtnContainer.visibility = View.VISIBLE
        binding.deleteEnabledBtnContainer.visibility = View.INVISIBLE
        binding.deleteDisabledBtn.text = getString(R.string.loading_caps)

        if (isGoogleOnlyAccount) {
            googleSignInClient.signOut().addOnCompleteListener {
                googleReauthLauncher.launch(googleSignInClient.signInIntent)
            }
        } else {
            reauthenticateAndDelete(EmailAuthProvider.getCredential(user.email!!, password))
        }
    }

    private fun reauthenticateAndDelete(credential: AuthCredential) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, getString(R.string.error_no_user), Toast.LENGTH_SHORT).show()
            resetDeleteButton()
            return
        }

        user.reauthenticate(credential)
            .addOnSuccessListener { deleteProfileThenAccount(user) }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.reauth_failed, e.message), Toast.LENGTH_LONG).show()
                resetDeleteButton()
            }
    }

    private fun deleteProfileThenAccount(user: FirebaseUser) {
        // Order matters: Firestore rules need the account to still exist, so the profile
        // document has to go first. The old code used addOnCompleteListener and ignored
        // the result — a failed document delete still went on to destroy the auth account,
        // leaving a profile in Firestore that nobody could ever reach or remove.
        db.collection("users").document(user.uid).delete()
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, getString(R.string.account_deleted), Toast.LENGTH_SHORT).show()
                        auth.signOut()
                        googleSignInClient.signOut()

                        startActivity(Intent(this, WelcomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                    .addOnFailureListener { e ->
                        // Previously unhandled: the screen sat on "LOADING" forever.
                        // The profile is already gone, so keep the session alive and let
                        // the user retry rather than stranding a half-deleted account.
                        Toast.makeText(
                            this,
                            getString(R.string.error_prefix, e.message ?: "Unknown"),
                            Toast.LENGTH_LONG
                        ).show()
                        resetDeleteButton()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.error_prefix, e.message ?: "Unknown"),
                    Toast.LENGTH_LONG
                ).show()
                resetDeleteButton()
            }
    }

    private fun resetDeleteButton() {
        isDeleting = false
        binding.deleteEnabledBtn.isEnabled = true
        binding.deleteEnabledBtnContainer.visibility = View.INVISIBLE
        binding.deleteDisabledBtnContainer.visibility = View.VISIBLE
        binding.deleteDisabledBtn.text = getString(R.string.delete_caps)
    }

    override fun onDestroy() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}