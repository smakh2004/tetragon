package com.tetragon.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityLoginBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.registrationUtils.DeviceUtils
import com.tetragon.app.utils.registrationUtils.PasswordToggleHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {

    companion object {
        private const val TAG = "TetragonAuth"
        private const val KEY_LOGIN_IN_PROGRESS = "login_in_progress"
        /** GoogleSignInStatusCodes.SIGN_IN_CANCELLED — user backed out of the picker. */
        private const val GOOGLE_SIGN_IN_CANCELLED = 12501
        private const val GOOGLE_SIGN_IN_CURRENTLY_IN_PROGRESS = 12502
    }

    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db = FirebaseFirestore.getInstance()
    private var isLoginInProgress = false

    private lateinit var googleSignInClient: GoogleSignInClient

    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ConnectivityViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // The old version went straight to getSignedInAccountFromIntent(). When the user
        // simply dismissed the account picker, result.data was null, the task failed, and
        // they were shown an "authentication failed" error for doing nothing wrong.
        if (result.resultCode != RESULT_OK) {
            resetLoginState()
            return@registerForActivityResult
        }

        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account?.idToken

            // account.idToken!! could throw a raw NPE here (misconfigured web client id),
            // which the ApiException catch below would not have caught.
            if (idToken.isNullOrEmpty()) {
                Log.e(TAG, "Google account returned without an id token")
                resetLoginState()
                Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
            } else {
                firebaseAuthWithGoogle(idToken)
            }
        } catch (e: ApiException) {
            resetLoginState()
            Log.e(TAG, "Google sign in failed! Status Code: ${e.statusCode}")
            val userCancelled = e.statusCode == GOOGLE_SIGN_IN_CANCELLED ||
                    e.statusCode == GOOGLE_SIGN_IN_CURRENTLY_IN_PROGRESS ||
                    e.statusCode == CommonStatusCodes.CANCELED
            if (!userCancelled) {
                Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Don't race the in-flight sign-in: handleSuccessfulLogin() is already navigating.
        if (isLoginInProgress) return

        val currentUser = auth.currentUser ?: return

        // Only auto-redirect a session that is actually complete: verified email AND an
        // existing Firestore profile. Google-provider accounts are verified by Google, so
        // isEmailVerified is already true for them.
        if (!currentUser.isEmailVerified) return

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists() && !isFinishing) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                // else: no profile — abandoned/orphaned account. Stay on login.
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Auto-login profile check failed; staying on login", e)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isLoginInProgress = savedInstanceState?.getBoolean(KEY_LOGIN_IN_PROGRESS, false) ?: false

        setupGoogleSignIn()
        observeConnectivity()

        // --- SYSTEM BACK PRESS NAVIGATION HANDLING ---
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isLoginInProgress) return
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = checkFields()
        }

        binding.emailEditText.addTextChangedListener(textWatcher)
        binding.passwordEditText.addTextChangedListener(textWatcher)

        PasswordToggleHelper.attach(
            binding.passwordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed
        )

        binding.exitBtn.setOnClickListener {
            if (isLoginInProgress) return@setOnClickListener
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.forgotPasswordText.setOnClickListener {
            if (isLoginInProgress) return@setOnClickListener

            val intent = Intent(this, ForgotPasswordActivity::class.java).apply {
                // Carry the typed address over so the user doesn't retype it.
                putExtra(ForgotPasswordActivity.EXTRA_PREFILL_EMAIL, binding.emailEditText.text.toString().trim())
            }
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.googleSignInButton.setOnClickListener {
            if (isLoginInProgress) return@setOnClickListener

            isLoginInProgress = true
            updateLoadingUi(true)

            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        binding.continueEnabledBtn.setOnClickListener {
            if (isLoginInProgress) return@setOnClickListener

            val email = binding.emailEditText.text.toString().trim()
            // NOT trimmed. Registration stores the password exactly as typed, so trimming
            // here meant any password with a leading/trailing space could never be used
            // to sign in to the account it created.
            val password = binding.passwordEditText.text.toString()

            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, getString(R.string.enter_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (TextUtils.isEmpty(password)) {
                Toast.makeText(this, getString(R.string.enter_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            isLoginInProgress = true
            updateLoadingUi(true)

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user == null) {
                            resetLoginState()
                            Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                            return@addOnCompleteListener
                        }
                        handleSuccessfulLogin(user)
                    } else {
                        Log.e(TAG, "Email sign in failed", task.exception)
                        resetLoginState()

                        val message = when (task.exception) {
                            is FirebaseAuthInvalidCredentialsException,
                            is FirebaseAuthInvalidUserException ->
                                // TODO: move to strings.xml for ru/uz translation.
                                "Incorrect email or password. Please try again."
                            else -> task.exception?.localizedMessage ?: getString(R.string.auth_failed)
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
        }

        checkFields()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOGIN_IN_PROGRESS, isLoginInProgress)
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (!task.isSuccessful) {
                    resetLoginState()
                    Log.e(TAG, "Firebase credential swap failed", task.exception)
                    Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                val user = auth.currentUser
                if (user == null) {
                    resetLoginState()
                    Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                // signInWithCredential SILENTLY CREATES an account when the Google address
                // has never been used. On the *login* screen that is wrong: the old code
                // just signed out afterwards, leaving a permanent auth record with no
                // Firestore profile behind every failed login attempt. Remove it again.
                if (task.result?.additionalUserInfo?.isNewUser == true) {
                    user.delete().addOnCompleteListener {
                        auth.signOut()
                        googleSignInClient.signOut()
                        resetLoginState()
                        // TODO: move to strings.xml.
                        Toast.makeText(this, "Account not found. Please register first.", Toast.LENGTH_LONG).show()
                    }
                    return@addOnCompleteListener
                }

                handleSuccessfulLogin(user)
            }
    }

    private fun handleSuccessfulLogin(user: FirebaseUser) {
        val uid = user.uid
        val deviceId = DeviceUtils.getDeviceId(this)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Log.w(TAG, "User authenticated but missing Firestore doc: $uid")
                    val unverified = !user.isEmailVerified
                    auth.signOut()
                    googleSignInClient.signOut()
                    resetLoginState()

                    // Distinguish "you never finished registering" from "no such account".
                    val message = if (unverified) {
                        getString(R.string.verify_email_first)
                    } else {
                        "Account not found. Please register first." // TODO: strings.xml
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                db.collection("users").document(uid)
                    .update("activeDeviceId", deviceId)
                    .addOnSuccessListener {
                        isLoginInProgress = false
                        Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()

                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        resetLoginState()
                        Log.e(TAG, "Failed to update device ID in Firestore", e)
                        Toast.makeText(this, "Session error. Please try again.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                resetLoginState()
                Log.e(TAG, "Firestore user look-up failed entirely", e)
                Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun resetLoginState() {
        isLoginInProgress = false
        updateLoadingUi(false)
        checkFields()
    }

    private fun updateLoadingUi(loading: Boolean) {
        if (loading) {
            binding.continueEnabledBtnContainer.visibility = View.INVISIBLE
            binding.continueDisabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtn.text = getString(R.string.loading_caps)
        } else {
            binding.continueDisabledBtn.text = getString(R.string.continue_text)
        }
    }

    private fun checkFields() {
        if (isLoginInProgress) return

        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()
        val isValid = email.isNotEmpty() && password.isNotEmpty()

        binding.continueEnabledBtnContainer.visibility = if (isValid) View.VISIBLE else View.INVISIBLE
        binding.continueDisabledBtnContainer.visibility = if (isValid) View.INVISIBLE else View.VISIBLE
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    binding.internetConnection.visibility = if (isConnected) View.GONE else View.VISIBLE

                    binding.emailEditText.isEnabled = isConnected
                    binding.passwordEditText.isEnabled = isConnected
                    binding.continueEnabledBtn.isEnabled = isConnected
                    binding.googleSignInButton.isEnabled = isConnected
                    binding.googleSignInButton.alpha = if (isConnected) 1.0f else 0.5f
                    binding.forgotPasswordText.isEnabled = isConnected
                    binding.forgotPasswordText.alpha = if (isConnected) 1.0f else 0.5f

                    if (isConnected) {
                        checkFields()
                    } else if (!isLoginInProgress) {
                        binding.continueEnabledBtnContainer.visibility = View.INVISIBLE
                        binding.continueDisabledBtnContainer.visibility = View.VISIBLE
                        binding.continueDisabledBtn.text = getString(R.string.continue_text)
                    }
                }
            }
        }
    }
}