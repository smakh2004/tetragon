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

    // CRITICAL FLAGS
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

        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        isProfileSaved = true
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        // User exists in Auth but has no Firestore profile.
                        // We allow them to continue, but we don't 'finish' yet.
                    }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // START the cleanup service
        startService(Intent(this, RegistrationCleanupService::class.java))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        observeConnectivity()
        showCurrentFragment()

        binding.exitBtn.setOnClickListener {
            handleExitCleanup()
        }

        binding.continueEnabledBtn.setOnClickListener {
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
        // If they exit and the Firestore profile isn't saved, wipe the Auth account
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
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    currentFragmentIndex = 2
                    showCurrentFragment()
                }
            }
    }

    private fun finishRegistrationAndSaveToFirestore() {
        val user = auth.currentUser ?: return

        // 1. IMMEDIATELY show the disabled/loading state for instant feedback
        setContinueButtonEnabled(false)
        binding.continueDisabledBtn.text = "CREATING ACCOUNT..." // Change text inside the disabled button
        isRegistrationInProgress = true // Prevents other UI interactions

        user.reload().addOnCompleteListener { task ->
            if (task.isSuccessful && user.isEmailVerified) {

                // Prepare data
                val userMap = hashMapOf(
                    "uid" to user.uid,
                    "firstName" to userData.firstName,
                    "lastName" to userData.lastName,
                    "age" to userData.age,
                    "email" to userData.email,
                    "xp" to 0,
                    "level" to 1,
                    "registeredAt" to com.google.firebase.Timestamp.now()
                )

                // Save to Firestore
                db.collection("users").document(user.uid).set(userMap)
                    .addOnSuccessListener {
                        isProfileSaved = true
                        stopService(Intent(this, RegistrationCleanupService::class.java))
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        // Re-enable if Firestore fails
                        resetLoadingState()
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                // Re-enable if they aren't verified yet
                resetLoadingState()
                Toast.makeText(this, "Please verify your email first!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper to reset the button if verification or saving fails
    private fun resetLoadingState() {
        isRegistrationInProgress = false
        setContinueButtonEnabled(true)
        binding.continueDisabledBtn.text = "CONTINUE" // Reset to original text
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
        val percent = stepIndex.toFloat() / (fragments.size - 1).toFloat()
        val params = binding.progressActive.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.matchConstraintPercentWidth = percent
        binding.progressActive.layoutParams = params

        val circles = listOf(binding.step1, binding.step2, binding.step3, binding.step4, binding.step5)
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
        if (currentFragmentIndex >= fragments.size - 1) {
            handleExitCleanup()
        } else if (currentFragmentIndex > 0) {
            currentFragmentIndex--
            showCurrentFragment()
        } else {
            handleExitCleanup()
        }
    }

    override fun onDestroy() {
        val user = auth.currentUser
        // If the activity dies and Firestore document isn't saved, wipe the user
        if (user != null && !isProfileSaved) {
            user.delete()
        }
        super.onDestroy()
    }
}