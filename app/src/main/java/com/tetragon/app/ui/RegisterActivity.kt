package com.tetragon.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
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
import com.tetragon.app.utils.registrationUtils.DeviceUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    val userData = UserData()

    var isRegistrationInProgress = false
    var isProfileSaved = false
    var isGoogleAccount = false
    private var isGoogleSignInInProgress = false

    private var createdAuthEmail: String? = null
    private var createdAuthPassword: String? = null

    private var isIntroShowing = true
    private var textAnimationJob: Job? = null

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

    // PIPELINE INDICES:
    // 0: LanguageFragment
    // 1: AgeFragment
    // 2: FullNameFragment
    // 3: NotificationPermissionFragment
    // 4: AuthMethodFragment
    // 5: EmailFragment
    // 6: PasswordFragment
    // 7: UiVerificationFragment
    private val fragments = listOf(
        LanguageFragment(),
        AgeFragment(),
        FullNameFragment(),
        NotificationPermissionFragment(),
        AuthMethodFragment(),
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

        if (currentFragmentIndex > 0) {
            isIntroShowing = false
        }

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initGoogleSignIn()
        startService(Intent(this, RegistrationCleanupService::class.java))
        observeConnectivity()

        if (isIntroShowing) {
            setupIntroLayoutState()

            lifecycleScope.launch {
                delay(500)
                binding.mrSquareWelcomeAnim.fireState("State Machine 1", "hi")
                binding.globalQuestionTitle.typeWrite(getString(R.string.hello_mr_square), 40)
                delay(2000)
                setIntroContinueButtonEnabled(true)
            }
        } else {
            showCurrentFragment(navigatingForward = true, animate = false)
        }

        binding.exitBtn.setOnClickListener {
            if (isRegistrationInProgress || isGoogleSignInInProgress) return@setOnClickListener
            handleNavigationBackwards()
        }

        binding.continueEnabledBtn.setOnClickListener {
            handleContinueClicked()
        }
    }

    private fun handleContinueClicked() {
        if (isIntroShowing) {
            isIntroShowing = false
            animateLayoutFromIntroToNormal()
            return
        }

        if (currentFragmentIndex == 0) {
            binding.mrSquareWelcomeAnim.fireState("State Machine 1", "okay")
            applyLocaleInPlace(userData.language)
            currentFragmentIndex = 1
            setContinueButtonEnabled(false)
            showCurrentFragment(navigatingForward = true, animate = true)
            return
        }

        if (currentFragmentIndex == fragments.size - 1) {
            finishRegistrationAndSaveToFirestore()
            return
        }

        if (currentFragmentIndex < fragments.size - 1) {
            currentFragmentIndex++
            setContinueButtonEnabled(false)

            if (currentFragmentIndex == 5 && isGoogleAccount) {
                finishRegistrationAndSaveToFirestore()
                return
            }

            if (currentFragmentIndex == fragments.size - 1 && !isGoogleAccount) {
                createAuthAccountAndSendEmail()
            }

            // Fire generic "okay" trigger ONLY when navigating to steps without their own custom Rive trigger
            val customAnimationIndices = listOf(3, 5, 6)
            if (currentFragmentIndex !in customAnimationIndices) {
                binding.mrSquareWelcomeAnim.fireState("State Machine 1", "okay")
            }

            showCurrentFragment(navigatingForward = true, animate = true)
        }
    }

    fun advanceToNextStep() {
        if (isRegistrationInProgress || isGoogleSignInInProgress) return
        if (isIntroShowing) return
        handleContinueClicked()
    }

    private fun TextView.typeWrite(text: String, charDelayMs: Long = 30) {
        textAnimationJob?.cancel()
        this.text = ""
        textAnimationJob = lifecycleScope.launch {
            for (ch in text) {
                append(ch.toString())
                delay(charDelayMs)
            }
        }
    }

    fun fireMrSquareAnimation(triggerName: String) {
        binding.mrSquareWelcomeAnim.fireState("State Machine 1", triggerName)
    }

    internal fun applyLocaleInPlace(languageCode: String) {
        if (languageCode.isBlank()) return
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun setupIntroLayoutState() {
        binding.stepProgressContainer.visibility = View.GONE
        binding.questionFragmentContainer.visibility = View.GONE
        binding.questionHeaderContainer.visibility = View.VISIBLE

        binding.globalQuestionTitle.text = ""

        val constraintLayout = binding.root as ConstraintLayout
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        constraintSet.clear(R.id.questionHeaderContainer, ConstraintSet.TOP)
        constraintSet.clear(R.id.questionHeaderContainer, ConstraintSet.BOTTOM)
        constraintSet.clear(R.id.mr_square_welcome_anim, ConstraintSet.TOP)
        constraintSet.clear(R.id.mr_square_welcome_anim, ConstraintSet.BOTTOM)

        constraintSet.connect(R.id.questionHeaderContainer, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(R.id.questionHeaderContainer, ConstraintSet.BOTTOM, R.id.mr_square_welcome_anim, ConstraintSet.TOP)
        constraintSet.connect(R.id.mr_square_welcome_anim, ConstraintSet.TOP, R.id.questionHeaderContainer, ConstraintSet.BOTTOM)
        constraintSet.connect(R.id.mr_square_welcome_anim, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.setVerticalChainStyle(R.id.questionHeaderContainer, ConstraintSet.CHAIN_PACKED)

        constraintSet.applyTo(constraintLayout)
        setIntroContinueButtonEnabled(false)
    }

    private fun animateLayoutFromIntroToNormal() {
        val constraintLayout = binding.root as ConstraintLayout
        val constraintSet = ConstraintSet()
        constraintSet.clone(this, R.layout.activity_register)
        constraintSet.applyTo(constraintLayout)

        binding.stepProgressContainer.alpha = 0f
        binding.stepProgressContainer.translationY = -40f
        binding.stepProgressContainer.visibility = View.VISIBLE

        binding.questionFragmentContainer.alpha = 0f
        binding.questionFragmentContainer.translationY = 60f
        binding.questionFragmentContainer.visibility = View.VISIBLE

        binding.stepProgressContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        showCurrentFragment(navigatingForward = true, animate = false)

        binding.questionFragmentContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        refreshUiComponents(false)
    }

    private fun setIntroContinueButtonEnabled(enabled: Boolean) {
        if (enabled) {
            binding.continueEnabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtnContainer.visibility = View.GONE
            binding.continueEnabledBtn.isEnabled = true
        } else {
            binding.continueEnabledBtnContainer.visibility = View.GONE
            binding.continueDisabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtn.text = getString(R.string.continue_text)
        }
    }

    private fun handleNavigationBackwards() {
        if (isIntroShowing) {
            handleExitCleanup()
            return
        }

        if (currentFragmentIndex > 0) {
            if (currentFragmentIndex == 4 && isGoogleAccount) {
                handleExitCleanup()
            } else {
                val wasAtAuthMethod = (currentFragmentIndex == 5)
                currentFragmentIndex--

                if (wasAtAuthMethod) {
                    showCurrentFragment(navigatingForward = false, animate = true)
                } else {
                    showCurrentFragment(navigatingForward = false, animate = true)
                }
            }
        } else {
            isIntroShowing = true
            setupIntroLayoutState()

            lifecycleScope.launch {
                delay(1000)
                binding.mrSquareWelcomeAnim.fireState("State Machine 1", "hi")
                binding.globalQuestionTitle.typeWrite(getString(R.string.hello_mr_square), 40)
                delay(2000)
                setIntroContinueButtonEnabled(true)
            }
        }
    }

    private fun initGoogleSignIn() {
        val webClientId = getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)!!
                        firebaseAuthWithGoogle(account.idToken!!)
                    } catch (e: ApiException) {
                        Toast.makeText(this, getString(R.string.google_error_msg, e.statusCode), Toast.LENGTH_LONG).show()
                        resetAuthMethodUiState()
                    }
                } else {
                    resetAuthMethodUiState()
                }
            }
    }

    fun triggerGoogleRegistration() {
        if (isGoogleSignInInProgress || isRegistrationInProgress) return
        isGoogleSignInInProgress = true

        val activeFragment = supportFragmentManager.findFragmentById(R.id.questionFragmentContainer)
        if (activeFragment is AuthMethodFragment) {
            activeFragment.setControlsEnabled(false)
        }

        refreshUiComponents(false)

        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser ?: return@addOnCompleteListener
                    db.collection("users").document(user.uid).get()
                        .addOnSuccessListener { document ->
                            if (document.exists()) {
                                isProfileSaved = true
                                Toast.makeText(this, getString(R.string.welcome_back), Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                                finish()
                                return@addOnSuccessListener
                            }

                            isGoogleAccount = true
                            userData.email = user.email ?: ""

                            if (userData.firstName.isEmpty()) {
                                val fullName = user.displayName?.split(" ")
                                userData.firstName = fullName?.getOrNull(0) ?: ""
                                userData.lastName = fullName?.drop(1)?.joinToString(" ") ?: ""
                            }

                            binding.mrSquareWelcomeAnim.fireState("State Machine 1", "yahoo")
                            finishRegistrationAndSaveToFirestore()
                        }
                } else {
                    Toast.makeText(this, getString(R.string.firebase_auth_failure, task.exception?.message ?: ""), Toast.LENGTH_LONG).show()
                    resetAuthMethodUiState()
                }
            }
    }

    fun navigateToManualEmailInput() {
        if (currentFragmentIndex == 4) {
            currentFragmentIndex = 5
            setContinueButtonEnabled(false)

            binding.questionHeaderContainer.alpha = 0f
            binding.questionHeaderContainer.translationY = -20f
            binding.questionHeaderContainer.visibility = View.VISIBLE

            binding.stepProgressContainer.alpha = 0f
            binding.stepProgressContainer.translationY = -30f
            binding.stepProgressContainer.visibility = View.VISIBLE

            binding.continueDisabledBtnContainer.alpha = 0f
            binding.continueDisabledBtnContainer.translationY = 30f

            showCurrentFragment(navigatingForward = true, animate = true)

            binding.questionHeaderContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .start()

            binding.stepProgressContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .start()

            binding.continueDisabledBtnContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .start()
        }
    }

    private fun resetAuthMethodUiState() {
        isGoogleSignInInProgress = false
        val activeFragment = supportFragmentManager.findFragmentById(R.id.questionFragmentContainer)
        if (activeFragment is AuthMethodFragment) {
            activeFragment.setControlsEnabled(true)
        }
        resetLoadingState()
    }

    private fun handleExitCleanup() {
        val user = auth.currentUser
        if (user != null && !isProfileSaved && !isGoogleAccount) {
            user.delete().addOnCompleteListener {
                createdAuthEmail = null
                createdAuthPassword = null
                stopService(Intent(this, RegistrationCleanupService::class.java))
                finish()
            }
        } else {
            if (isGoogleAccount && !isProfileSaved) {
                auth.signOut()
                googleSignInClient.signOut()
            }
            stopService(Intent(this, RegistrationCleanupService::class.java))
            finish()
        }
    }

    private fun createAuthAccountAndSendEmail() {
        if (isGoogleAccount) return

        val existing = auth.currentUser
        val alreadyCreatedForCurrentCreds = existing != null &&
                createdAuthEmail == userData.email &&
                createdAuthPassword == userData.password

        if (alreadyCreatedForCurrentCreds) {
            existing?.sendEmailVerification()
            refreshUiComponents(false)
            return
        }

        isRegistrationInProgress = true
        refreshUiComponents(false)

        if (existing != null) {
            existing.delete().addOnCompleteListener {
                createdAuthEmail = null
                createdAuthPassword = null
                performAuthCreation()
            }
        } else {
            performAuthCreation()
        }
    }

    private fun performAuthCreation() {
        auth.createUserWithEmailAndPassword(userData.email, userData.password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    createdAuthEmail = userData.email
                    createdAuthPassword = userData.password
                    auth.currentUser?.sendEmailVerification()
                    isRegistrationInProgress = false
                    refreshUiComponents(false)
                } else {
                    Toast.makeText(this, getString(R.string.error_prefix, task.exception?.message ?: "Unknown"), Toast.LENGTH_LONG).show()
                    resetLoadingState()
                    currentFragmentIndex = 5
                    showCurrentFragment(navigatingForward = false, animate = true)
                }
            }
    }

    private fun finishRegistrationAndSaveToFirestore() {
        val user = auth.currentUser ?: return
        isRegistrationInProgress = true
        refreshUiComponents(false)

        user.reload().addOnCompleteListener { task ->
            if (task.isSuccessful && (user.isEmailVerified || isGoogleAccount)) {
                binding.mrSquareWelcomeAnim.fireState("State Machine 1", "yahoo")

                val userMap = hashMapOf(
                    "uid" to user.uid,
                    "firstName" to userData.firstName,
                    "lastName" to userData.lastName,
                    "age" to userData.age,
                    "email" to userData.email,
                    "xp" to 0L,
                    "monthlyXP" to 0L,
                    "level" to 1,
                    "language" to userData.language,
                    "registeredAt" to com.google.firebase.Timestamp.now(),
                    "stars" to 15L,
                    "coins" to 30L,
                    "streak" to 0,
                    "activeDeviceId" to DeviceUtils.getDeviceId(this),
                    "subscription" to false,
                    "subscriptionUntil" to null,
                    "planType" to "free",
                    "avatarName" to "avatar_1",
                    "avatarConfig" to hashMapOf(
                        "face" to 1L,
                        "hair" to 1L,
                        "glasses" to 1L,
                        "hat" to 1L,
                        "mustache" to 1L,
                        "body" to 1L,
                        "backgroundColor" to "#00AEEF"
                    )
                )

                db.collection("users").document(user.uid).set(userMap)
                    .addOnSuccessListener {
                        isProfileSaved = true
                        stopService(Intent(this, RegistrationCleanupService::class.java))
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
            } else {
                resetLoadingState()
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
        refreshUiComponents(false)
    }

    fun updateAge(value: String) { userData.age = value }

    private fun showCurrentFragment(navigatingForward: Boolean, animate: Boolean = true) {
        val transaction = supportFragmentManager.beginTransaction()

        if (animate) {
            if (navigatingForward) {
                transaction.setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
            } else {
                transaction.setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
            }
        }

        transaction
            .replace(R.id.questionFragmentContainer, fragments[currentFragmentIndex])
            .commit()

        // --- RIVE TRIGGER HOOKS ON FRAGMENT NAVIGATION ---
        when (currentFragmentIndex) {
            3 -> fireMrSquareAnimation("notification")
            5 -> fireMrSquareAnimation("yahoo")
            6 -> fireMrSquareAnimation("plan")
        }

        updateProgress(currentFragmentIndex)
        updateHeaderUi()
        refreshUiComponents(false)
    }

    private fun updateHeaderUi() {
        if (isIntroShowing) return

        if (currentFragmentIndex == 4) {
            binding.questionHeaderContainer.visibility = View.GONE
            return
        }
        binding.questionHeaderContainer.visibility = View.VISIBLE

        val titleResId = when (currentFragmentIndex) {
            0 -> R.string.choose_your_language
            1 -> R.string.what_s_your_age
            2 -> R.string.what_s_your_full_name
            3 -> R.string.notif_description
            5 -> R.string.enter_your_email_address
            6 -> R.string.create_a_password
            7 -> R.string.verify_your_email_address
            else -> null
        }

        titleResId?.let {
            binding.globalQuestionTitle.typeWrite(getString(it), 25)
        }
    }

    fun setContinueButtonEnabled(enabled: Boolean) {
        if (isIntroShowing) return
        refreshUiComponents(enabled)
    }

    private fun refreshUiComponents(isBtnEnabled: Boolean) {
        if (!isIntroShowing && currentFragmentIndex == 4) {
            binding.stepProgressContainer.visibility = View.GONE
            binding.continueEnabledBtnContainer.visibility = View.GONE
            binding.continueDisabledBtnContainer.visibility = View.GONE
            binding.questionHeaderContainer.visibility = View.GONE
            return
        }

        if (isIntroShowing) return

        if (binding.stepProgressContainer.visibility != View.VISIBLE && currentFragmentIndex != 4) {
            binding.stepProgressContainer.visibility = View.VISIBLE
        }

        if (isRegistrationInProgress || isGoogleSignInInProgress) {
            binding.continueEnabledBtnContainer.visibility = View.GONE
            binding.continueDisabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtn.text = when {
                isGoogleSignInInProgress -> getString(R.string.signing_in)
                else -> getString(R.string.creating_account)
            }
        } else {
            binding.continueEnabledBtn.isEnabled = isBtnEnabled
            binding.continueEnabledBtnContainer.visibility = if (isBtnEnabled) View.VISIBLE else View.GONE
            binding.continueDisabledBtnContainer.visibility = if (isBtnEnabled) View.GONE else View.VISIBLE
            binding.continueDisabledBtn.text = getString(R.string.continue_text)
        }
    }

    private fun updateProgress(stepIndex: Int) {
        if (isIntroShowing || stepIndex == 4) return

        val logicalStepIndex = when (stepIndex) {
            5 -> 4
            6 -> 5
            7 -> 6
            else -> stepIndex
        }

        val totalVisualSteps = 6
        val targetPercent = logicalStepIndex.toFloat() / totalVisualSteps.toFloat()

        val params = binding.progressActive.layoutParams as ConstraintLayout.LayoutParams
        val currentPercent = params.matchConstraintPercentWidth

        val progressAnimator = android.animation.ValueAnimator.ofFloat(currentPercent, targetPercent).apply {
            duration = 500
            interpolator = android.view.animation.AnticipateOvershootInterpolator(1.2f)

            addUpdateListener { animator ->
                val animatedValue = animator.animatedValue as Float
                val currentParams = binding.progressActive.layoutParams as ConstraintLayout.LayoutParams
                currentParams.matchConstraintPercentWidth = animatedValue
                binding.progressActive.layoutParams = currentParams
            }
        }

        progressAnimator.start()
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    binding.internetConnection.visibility = if (isConnected) View.GONE else View.VISIBLE
                    binding.offlineContainer.visibility = if (isConnected) View.GONE else View.VISIBLE

                    if (isConnected) {
                        if (isIntroShowing) {
                            binding.questionHeaderContainer.visibility = View.VISIBLE
                        } else {
                            binding.questionFragmentContainer.visibility = View.VISIBLE
                            if (currentFragmentIndex != 4) {
                                binding.questionHeaderContainer.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        binding.questionFragmentContainer.visibility = View.GONE
                        binding.questionHeaderContainer.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        if (isRegistrationInProgress || isGoogleSignInInProgress) return
        handleNavigationBackwards()
    }

    override fun onDestroy() {
        deleteUserAuthNodeSilently()
        super.onDestroy()
    }
}