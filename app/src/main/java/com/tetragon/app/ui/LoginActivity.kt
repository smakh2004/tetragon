package com.tetragon.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    private val db = FirebaseFirestore.getInstance()
    private var isLoginInProgress = false

    // Google Sign-In Client variable
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

    // Launcher that catches the result after user picks their Google account
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            Log.d("TetragonAuth", "Google account selected: ${account.email}")

            // Now pass this account's token over to Firebase
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            isLoginInProgress = false
            updateLoadingUi(false)
            checkFields()
            Log.e("TetragonAuth", "Google sign in failed! Status Code: ${e.statusCode}")
            Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
        }
    }

    public override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        // Initialize Google Sign-In options
        setupGoogleSignIn()
        observeConnectivity()

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
            startActivity(Intent(this, WelcomeActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        binding.googleSignInButton.setOnClickListener {
            if (isLoginInProgress) return@setOnClickListener

            isLoginInProgress = true
            updateLoadingUi(true)

            // Clear out the sticky session history before launching to show account picker every time
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }

        binding.continueEnabledBtn.setOnClickListener {
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
                        handleSuccessfulLogin(auth.currentUser!!)
                    } else {
                        isLoginInProgress = false
                        updateLoadingUi(false)
                        checkFields()
                        Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile() // Forces profile verification criteria
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
                    isLoginInProgress = false
                    updateLoadingUi(false)
                    checkFields()
                    Log.e("TetragonAuth", "Firebase credential swap failed", task.exception)
                    Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Unified handler ensuring your activeDeviceId strategy remains enforced
    private fun handleSuccessfulLogin(user: FirebaseUser) {
        val uid = user.uid
        val deviceId = DeviceUtils.getDeviceId(this)

        db.collection("users")
            .document(uid)
            .update("activeDeviceId", deviceId)
            .addOnSuccessListener {
                isLoginInProgress = false
                Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                finish()
            }
            .addOnFailureListener { e ->
                // Fallback for new Google accounts to prevent crash if document doesn't exist
                val userMap = hashMapOf(
                    "uid" to uid,
                    "email" to user.email,
                    "activeDeviceId" to deviceId
                )
                db.collection("users").document(uid).set(userMap)
                    .addOnSuccessListener {
                        isLoginInProgress = false
                        startActivity(Intent(this, MainActivity::class.java))
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        finish()
                    }
            }
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