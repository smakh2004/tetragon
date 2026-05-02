package com.example.tetragon.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tetragon.MainActivity
import com.example.tetragon.R
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.databinding.ActivityRegisterBinding
import com.example.tetragon.fragments.registrationFragments.*
import com.example.tetragon.gameModel.UserData
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RegisterActivity : BaseActivity() {
    private lateinit var binding: ActivityRegisterBinding
    val userData = UserData()

    var isRegistrationInProgress = false
    var isProfileSaved = false

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
        LanguageFragment(),      // STEP 0
        AgeFragment(),           // STEP 1
        FullNameFragment(),
        EmailFragment(),
        PasswordFragment(),
        UiVerificationFragment()
    )
    private var currentFragmentIndex = 0

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
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
        // Check if we are resuming from a language change
        currentFragmentIndex = intent.getIntExtra("START_STEP", 0)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startService(Intent(this, RegistrationCleanupService::class.java))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        observeConnectivity()
        showCurrentFragment()

        binding.exitBtn.setOnClickListener { handleExitCleanup() }

        binding.continueEnabledBtn.setOnClickListener {
            if (currentFragmentIndex == 0) {
                // SPECIAL CASE: Moving from Language pick to first data fragment
                // We restart the activity so all Strings/Resources refresh to the new locale
                val intent = Intent(this, RegisterActivity::class.java)
                intent.putExtra("START_STEP", 1)
                startActivity(intent)
                finish()
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                return@setOnClickListener
            }

            if (currentFragmentIndex < fragments.size - 1) {
                currentFragmentIndex++
                setContinueButtonEnabled(false)

                if (currentFragmentIndex == fragments.size - 1) {
                    createAuthAccountAndSendEmail()
                }
                showCurrentFragment()
            } else {
                finishRegistrationAndSaveToFirestore()
            }
        }
    }

    private fun handleExitCleanup() {
        val user = auth.currentUser
        if (user != null && !isProfileSaved) {
            user.delete().addOnCompleteListener {
                stopService(Intent(this, RegistrationCleanupService::class.java))
                navigateToWelcome()
            }
        } else {
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
        setContinueButtonEnabled(false)
        auth.createUserWithEmailAndPassword(userData.email, userData.password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.sendEmailVerification()
                } else {
                    val errorMsg = getString(R.string.error_prefix, task.exception?.message ?: "Unknown")
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    currentFragmentIndex = 3 // Go back to Email step if auth fails
                    showCurrentFragment()
                }
            }
    }

    private fun finishRegistrationAndSaveToFirestore() {
        val user = auth.currentUser ?: return
        setContinueButtonEnabled(false)
        binding.continueDisabledBtn.text = getString(R.string.creating_account)
        isRegistrationInProgress = true

        user.reload().addOnCompleteListener { task ->
            if (task.isSuccessful && user.isEmailVerified) {
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
                    "planType" to "free"
                )

                db.collection("users").document(user.uid).set(userMap)
                    .addOnSuccessListener {
                        isProfileSaved = true
                        stopService(Intent(this, RegistrationCleanupService::class.java))
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        resetLoadingState()
                        Toast.makeText(this, getString(R.string.error_prefix, e.message), Toast.LENGTH_SHORT).show()
                    }
            } else {
                resetLoadingState()
                Toast.makeText(this, getString(R.string.verify_email_first), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resetLoadingState() {
        isRegistrationInProgress = false
        setContinueButtonEnabled(true)
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
        if (isRegistrationInProgress) return
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

        // Update step circles (assuming you have 6 steps now)
        val circles = listOf(binding.step1, binding.step2, binding.step3, binding.step4, binding.step5, binding.step6)
        for (i in circles.indices) {
            circles[i].setBackgroundResource(if (i <= stepIndex) R.drawable.circle_active else R.drawable.circle_inactive)
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
        if (currentFragmentIndex > 0) {
            currentFragmentIndex--
            showCurrentFragment()
        } else {
            handleExitCleanup()
        }
    }

    override fun onDestroy() {
        val user = auth.currentUser
        if (user != null && !isProfileSaved) {
            user.delete()
        }
        super.onDestroy()
    }
}