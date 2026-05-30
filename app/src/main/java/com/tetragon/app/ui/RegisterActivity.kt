package com.tetragon.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityRegisterBinding
import com.tetragon.app.fragments.registrationFragments.*
import com.tetragon.app.gameModel.UserData
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    val userData = UserData()

    var isRegistrationInProgress = false
    var isProfileSaved = false
    var isGoogleAccount = false
    private var isGoogleSignInInProgress = false

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ConnectivityViewModel::class.java)) {
                    return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val fragments = listOf(
        LanguageFragment(),
        AgeFragment(),
        FullNameFragment(),
        EmailFragment(),
        PasswordFragment(),
        UiVerificationFragment()
    )
    private var currentFragmentIndex = 0

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null && !isRegistrationInProgress && !isGoogleAccount) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        isProfileSaved = true
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentFragmentIndex = intent.getIntExtra("START_STEP", 0)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initGoogleSignIn()

        startService(Intent(this, RegistrationCleanupService::class.java))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        observeConnectivity()
        showCurrentFragment()

        binding.exitBtn.setOnClickListener { handleExitCleanup() }

        binding.continueEnabledBtn.setOnClickListener {
            if (currentFragmentIndex == 0) {
                val intent = Intent(this, RegisterActivity::class.java)
                intent.putExtra("START_STEP", 1)
                startActivity(intent)
                finish()
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                return@setOnClickListener
            }

            if (isGoogleAccount && currentFragmentIndex == fragments.size - 1) {
                finishRegistrationAndSaveToFirestore()
                return@setOnClickListener
            }

            if (currentFragmentIndex < fragments.size - 1) {
                currentFragmentIndex++
                setContinueButtonEnabled(false)

                if (currentFragmentIndex == fragments.size - 1 && !isGoogleAccount) {
                    createAuthAccountAndSendEmail()
                }

                showCurrentFragment()
            } else {
                finishRegistrationAndSaveToFirestore()
            }
        }
    }

    private fun initGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)
        Log.d("TetragonAuth", "Initializing Google SDK with Client ID: $webClientId")

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                Log.d("TetragonAuth", "Launcher returned. Result Code: ${result.resultCode}")

                if (result.resultCode == RESULT_OK) {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)!!
                        Log.d("TetragonAuth", "Google Sign-In success! Email: ${account.email}")
                        firebaseAuthWithGoogle(account.idToken!!)
                    } catch (e: ApiException) {
                        Log.e("TetragonAuth", "Google Sign-In API Failure. Status Code: ${e.statusCode}", e)

                        Toast.makeText(
                            this,
                            getString(R.string.google_error_msg, e.statusCode),
                            Toast.LENGTH_LONG
                        ).show()

                        resetEmailFragmentUiState()
                    }
                } else {
                    Log.w("TetragonAuth", "Google Picker interface closed or cancelled by user.")
                    resetEmailFragmentUiState()
                }
            }
    }

    fun triggerGoogleRegistration() {
        if (isGoogleSignInInProgress || isRegistrationInProgress) return
        isGoogleSignInInProgress = true

        // Freeze current view inputs inside the email step fragment interface
        val activeFragment = supportFragmentManager.findFragmentById(R.id.questionFragmentContainer)
        if (activeFragment is EmailFragment) {
            activeFragment.setControlsEnabled(false)
        }

        // Lock bottom container layout and state to signal interactive signing runtime processing
        binding.continueEnabledBtnContainer.visibility = View.GONE
        binding.continueDisabledBtnContainer.visibility = View.VISIBLE
        binding.continueDisabledBtn.text = getString(R.string.signing_in)

        googleSignInClient.signOut().addOnCompleteListener {
            Log.d("TetragonAuth", "Launching Google Account Picker intent...")
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.d("TetragonAuth", "Attempting Firebase Authentication with received ID Token...")

        binding.continueEnabledBtnContainer.visibility = View.GONE
        binding.continueDisabledBtnContainer.visibility = View.VISIBLE
        binding.continueDisabledBtn.text = getString(R.string.signing_in)

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user == null) {
                        Log.e("TetragonAuth", "Firebase user reference is null post-authentication.")
                        Toast.makeText(this, getString(R.string.firebase_empty_user_error), Toast.LENGTH_SHORT).show()
                        resetEmailFragmentUiState()
                        return@addOnCompleteListener
                    }

                    Log.d("TetragonAuth", "Firebase authentication complete. UID: ${user.uid}. Querying Firestore profile...")

                    db.collection("users").document(user.uid).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                isProfileSaved = true
                                Log.i("TetragonAuth", "Existing user document found. Aborting registration, booting to MainActivity.")
                                Toast.makeText(this, getString(R.string.welcome_back), Toast.LENGTH_SHORT).show()

                                val intent = Intent(this, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                                return@addOnSuccessListener
                            }

                            Log.i("TetragonAuth", "New Google user confirmed. Preparing profile map records...")
                            isGoogleAccount = true
                            userData.email = user.email ?: ""

                            if (userData.firstName.isEmpty()) {
                                val fullName = user.displayName?.split(" ")
                                userData.firstName = fullName?.getOrNull(0) ?: ""
                                userData.lastName = fullName?.drop(1)?.joinToString(" ") ?: ""
                            }

                            finishRegistrationAndSaveToFirestore()
                        }
                        .addOnFailureListener { e ->
                            Log.e("TetragonAuth", "Firestore configuration connection check failed.", e)
                            Toast.makeText(this, getString(R.string.database_connection_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            resetEmailFragmentUiState()
                        }

                } else {
                    Log.e("TetragonAuth", "Firebase Authentication pipeline rejection.", task.exception)
                    Toast.makeText(this, getString(R.string.firebase_auth_failure, task.exception?.message ?: ""), Toast.LENGTH_LONG).show()
                    resetEmailFragmentUiState()
                }
            }
    }

    private fun resetEmailFragmentUiState() {
        isGoogleSignInInProgress = false
        val activeFragment = supportFragmentManager.findFragmentById(R.id.questionFragmentContainer)
        if (activeFragment is EmailFragment) {
            activeFragment.setControlsEnabled(true)
        }
        resetLoadingState()
    }

    private fun handleExitCleanup() {
        val user = auth.currentUser
        if (user != null && !isProfileSaved && !isGoogleAccount) {
            user.delete().addOnCompleteListener {
                stopService(Intent(this, RegistrationCleanupService::class.java))
                navigateToWelcome()
            }
        } else {
            if (isGoogleAccount && !isProfileSaved) {
                auth.signOut()
                googleSignInClient.signOut()
            }
            stopService(Intent(this, RegistrationCleanupService::class.java))
            navigateToWelcome()
        }
    }

    private fun navigateToWelcome() {
        startActivity(Intent(this, WelcomeActivity::class.java))
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
    }

    private fun createAuthAccountAndSendEmail() {
        if (isGoogleAccount) return
        isRegistrationInProgress = true

        val activeFragment = supportFragmentManager.findFragmentById(R.id.questionFragmentContainer)
        if (activeFragment is EmailFragment) {
            activeFragment.setControlsEnabled(false)
        }

        binding.continueEnabledBtnContainer.visibility = View.GONE
        binding.continueDisabledBtnContainer.visibility = View.VISIBLE
        binding.continueDisabledBtn.text = getString(R.string.creating_account)

        auth.createUserWithEmailAndPassword(userData.email, userData.password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                } else {
                    val errorMsg = getString(R.string.error_prefix, task.exception?.message ?: "Unknown")
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()

                    if (activeFragment is EmailFragment) {
                        activeFragment.setControlsEnabled(true)
                    }
                    resetLoadingState()

                    currentFragmentIndex = 3
                    showCurrentFragment()
                }
            }
    }

    private fun finishRegistrationAndSaveToFirestore() {
        val user = auth.currentUser ?: return
        Log.d("TetragonAuth", "Saving profile configuration entries to Firestore...")

        binding.continueEnabledBtnContainer.visibility = View.GONE
        binding.continueDisabledBtnContainer.visibility = View.VISIBLE
        binding.continueDisabledBtn.text = getString(R.string.creating_account)

        isRegistrationInProgress = true

        user.reload().addOnCompleteListener { task ->
            if (task.isSuccessful && (user.isEmailVerified || isGoogleAccount)) {
                val userMap = hashMapOf(
                    "uid" to user.uid,
                    "firstName" to userData.firstName,
                    "lastName" to userData.lastName,
                    "age" to userData.age,
                    "email" to userData.email,
                    "xp" to 0L,
                    "monthlyXP" to 0L,
                    "level" to 1,
                    "registeredAt" to com.google.firebase.Timestamp.now(),
                    "stars" to 15L,
                    "coins" to 30L,
                    "streak" to 0,
                    "subscriptionUntil" to null,
                    "planType" to "free",
                    "avatarName" to "avatar_1"
                )

                db.collection("users").document(user.uid).set(userMap)
                    .addOnSuccessListener {
                        Log.i("TetragonAuth", "Profile created successfully in Firestore. Routing to MainActivity.")
                        isProfileSaved = true
                        stopService(Intent(this, RegistrationCleanupService::class.java))
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("TetragonAuth", "Failed to write user data initialization map.", e)
                        resetEmailFragmentUiState()
                        Toast.makeText(this, getString(R.string.database_connection_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
            } else {
                Log.w("TetragonAuth", "Reload complete but criteria failed. Verified: ${user.isEmailVerified}, Google: $isGoogleAccount")
                resetEmailFragmentUiState()
                Toast.makeText(this, getString(R.string.verify_email_first), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteUserAuthNodeSilently() {
        val user = auth.currentUser
        if (user != null && !isProfileSaved && !isGoogleAccount && !isGoogleSignInInProgress && !isChangingConfigurations) {
            user.delete()
        }
    }

    private fun resetLoadingState() {
        isRegistrationInProgress = false
        isGoogleSignInInProgress = false
        binding.continueEnabledBtnContainer.visibility = View.VISIBLE
        binding.continueDisabledBtnContainer.visibility = View.GONE
        binding.continueEnabledBtn.isEnabled = true
        binding.continueDisabledBtn.text = getString(R.string.continue_text)
    }

    fun updateAge(value: String) { userData.age = value }

    private fun showCurrentFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.questionFragmentContainer, fragments[currentFragmentIndex])
            .commit()
        updateProgress(currentFragmentIndex)
    }

    fun setContinueButtonEnabled(enabled: Boolean) {
        if (isRegistrationInProgress || isGoogleSignInInProgress) return
        binding.continueEnabledBtnContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.continueDisabledBtnContainer.visibility = if (enabled) View.GONE else View.VISIBLE
        binding.continueEnabledBtn.isEnabled = enabled
    }

    private fun updateProgress(stepIndex: Int) {
        val totalSteps = fragments.size - 1
        val percent = stepIndex.toFloat() / totalSteps.toFloat()
        val params = binding.progressActive.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.matchConstraintPercentWidth = percent
        binding.progressActive.layoutParams = params

        val circles = listOf(binding.step1, binding.step2, binding.step3, binding.step4, binding.step5, binding.step6)
        for (i in circles.indices) {
            circles[i].setBackgroundResource(
                if (i <= stepIndex) R.drawable.circle_active else R.drawable.circle_inactive
            )
        }
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    binding.internetConnection.visibility = if (isConnected) View.GONE else View.VISIBLE
                    binding.offlineContainer.visibility = if (isConnected) View.GONE else View.VISIBLE
                    binding.questionFragmentContainer.visibility = if (isConnected) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onBackPressed() {
        if (isRegistrationInProgress || isGoogleSignInInProgress) return // Block back interaction when processing
        if (currentFragmentIndex > 0) {
            if (currentFragmentIndex == 3 && isGoogleAccount) {
                handleExitCleanup()
            } else {
                currentFragmentIndex--
                showCurrentFragment()
            }
        } else {
            handleExitCleanup()
        }
    }

    override fun onDestroy() {
        deleteUserAuthNodeSilently()
        super.onDestroy()
    }
}