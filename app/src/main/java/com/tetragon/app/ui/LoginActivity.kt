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
import com.tetragon.app.utils.registrationUtils.PasswordToggleHelper
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityLoginBinding
import com.tetragon.app.utils.registrationUtils.DeviceUtils
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db = FirebaseFirestore.getInstance()
    private var isLoginInProgress = false

    private lateinit var googleSignInClient: GoogleSignInClient

    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ConnectivityViewModel::class.java)) {
                    return ConnectivityViewModel(
                        AndroidConnectivityObserver(applicationContext)
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            Log.d("TetragonAuth", "Google account selected: ${account.email}")
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            resetLoginState()
            Log.e("TetragonAuth", "Google sign in failed! Status Code: ${e.statusCode}")
            Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser ?: return

        // Only auto-redirect a session that is actually complete: verified email AND an
        // existing Firestore profile. Previously this redirected on *any* non-null user,
        // which could drop an orphaned/unverified auth account into MainActivity with no
        // profile document (empty state, session listener bound to a doc that doesn't
        // exist). If the account isn't in a valid state we simply stay on the login
        // screen and let the user sign in cleanly.
        if (!currentUser.isEmailVerified) return

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                // else: no profile — abandoned/orphaned account. Stay on login.
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        observeConnectivity()

        // --- SYSTEM BACK PRESS NAVIGATION HANDLING ---
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isLoginInProgress) return
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkFields()
            }
            override fun afterTextChanged(s: Editable?) {}
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
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // --- FORGOT PASSWORD NAVIGATION ROUTING INTERFACE ---
        binding.forgotPasswordText.setOnClickListener {
            if (isLoginInProgress) return@setOnClickListener

            // Route execution loop forward to ForgotPasswordActivity
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.googleSignInButton.setOnClickListener {
            if (isLoginInProgress) return@setOnClickListener

            isLoginInProgress = true
            updateLoadingUi(true)

            Toast.makeText(this, "Redirecting to sign in with Google...", Toast.LENGTH_SHORT).show()

            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }

        binding.continueEnabledBtn.setOnClickListener {
            if (isLoginInProgress) return@setOnClickListener

            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

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
                        Log.d("TetragonAuth", "Email auth successful.")
                        handleSuccessfulLogin(auth.currentUser!!)
                    } else {
                        Log.e("TetragonAuth", "Email sign in failed directly from Firebase!", task.exception)
                        resetLoginState()

                        if (task.exception is FirebaseAuthInvalidCredentialsException) {
                            Toast.makeText(this, "Incorrect email or password. Please try again.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, task.exception?.localizedMessage ?: getString(R.string.auth_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }

        checkFields()
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
                if (task.isSuccessful) {
                    handleSuccessfulLogin(auth.currentUser!!)
                } else {
                    resetLoginState()
                    Log.e("TetragonAuth", "Firebase credential swap failed", task.exception)
                    Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun handleSuccessfulLogin(user: FirebaseUser) {
        val uid = user.uid
        val deviceId = DeviceUtils.getDeviceId(this)

        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    db.collection("users")
                        .document(uid)
                        .update("activeDeviceId", deviceId)
                        .addOnSuccessListener {
                            isLoginInProgress = false
                            Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()

                            val intent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            resetLoginState()
                            Log.e("TetragonAuth", "Failed to update device ID in Firestore", e)
                            Toast.makeText(this, "Session error. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Log.w("TetragonAuth", "User authenticated but missing Firestore Doc UID: $uid")
                    auth.signOut()
                    googleSignInClient.signOut()
                    resetLoginState()
                    Toast.makeText(this, "Account not found. Please register first.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                resetLoginState()
                Log.e("TetragonAuth", "Firestore user look-up failed entirely", e)
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
        val password = binding.passwordEditText.text.toString().trim()
        val isValid = email.isNotEmpty() && password.isNotEmpty()

        if (isValid) {
            binding.continueEnabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtnContainer.visibility = View.INVISIBLE
        } else {
            binding.continueEnabledBtnContainer.visibility = View.INVISIBLE
            binding.continueDisabledBtnContainer.visibility = View.VISIBLE
        }
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
                    } else {
                        binding.continueEnabledBtnContainer.visibility = View.INVISIBLE
                        binding.continueDisabledBtnContainer.visibility = View.VISIBLE
                        binding.continueDisabledBtn.text = getString(R.string.continue_text)
                    }
                }
            }
        }
    }
}