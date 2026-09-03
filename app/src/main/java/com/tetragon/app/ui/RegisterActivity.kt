package com.tetragon.app.ui

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
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
import com.tetragon.app.utils.applySystemBarsAndImePadding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.registrationUtils.DeviceUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class RegisterActivity : BaseActivity() {

    companion object {
        // Named step indices — no more magic numbers scattered through the file.
        const val STEP_LANGUAGE = 0
        const val STEP_AGE = 1
        const val STEP_FULL_NAME = 2
        const val STEP_NOTIFICATIONS = 3
        const val STEP_AUTH_METHOD = 4
        const val STEP_EMAIL = 5
        const val STEP_PASSWORD = 6
        const val STEP_VERIFICATION = 7
        const val LAST_STEP = STEP_VERIFICATION

        /** Firebase rejects anything shorter than this, so the UI must too. */
        const val MIN_PASSWORD_LENGTH = 6

        // Steps that fire their own Rive trigger in showCurrentFragment().
        private val CUSTOM_ANIMATION_STEPS = setOf(STEP_NOTIFICATIONS, STEP_EMAIL, STEP_PASSWORD)

        private const val KEY_STEP = "reg_step"
        private const val KEY_INTRO = "reg_intro"
        private const val KEY_GOOGLE = "reg_google"
        private const val KEY_PROFILE_SAVED = "reg_profile_saved"
        private const val KEY_CREATED_EMAIL = "reg_created_email"
        private const val KEY_LANGUAGE = "reg_language"
        private const val KEY_AGE = "reg_age"
        private const val KEY_FIRST_NAME = "reg_first_name"
        private const val KEY_LAST_NAME = "reg_last_name"
        private const val KEY_EMAIL = "reg_email"
    }

    private lateinit var binding: ActivityRegisterBinding
    val userData = UserData()

    var isRegistrationInProgress = false
    var isProfileSaved = false
    var isGoogleAccount = false
    private var isGoogleSignInInProgress = false

    private var createdAuthEmail: String? = null

    // Deliberately NOT persisted across process death: a password should not be written
    // into the saved-state Bundle. A null value simply means "unknown", which makes
    // createAuthAccountAndSendEmail() re-create the auth account instead of trusting it.
    private var createdAuthPassword: String? = null

    private var isIntroShowing = true
    private var textAnimationJob: Job? = null
    private var introJob: Job? = null
    private var progressAnimator: ValueAnimator? = null

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

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

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var currentFragmentIndex = STEP_LANGUAGE

    // Fragments are built on demand. Holding a single pre-built list as a field meant the
    // activity handed already-destroyed instances back to the FragmentManager after a
    // configuration change.
    private fun newFragmentForStep(step: Int): Fragment = when (step) {
        STEP_LANGUAGE -> LanguageFragment()
        STEP_AGE -> AgeFragment()
        STEP_FULL_NAME -> FullNameFragment()
        STEP_NOTIFICATIONS -> NotificationPermissionFragment()
        STEP_AUTH_METHOD -> AuthMethodFragment()
        STEP_EMAIL -> EmailFragment()
        STEP_PASSWORD -> PasswordFragment()
        else -> UiVerificationFragment()
    }

    // ---------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Системные панели + клавиатура: на Android 15+ окно под IME больше
        // не сжимается само, поэтому нижний отступ считаем из инсетов.
        binding.root.applySystemBarsAndImePadding()

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            currentFragmentIndex = intent.getIntExtra("START_STEP", STEP_LANGUAGE)
            if (currentFragmentIndex > STEP_LANGUAGE) isIntroShowing = false
        }

        // Re-apply the chosen locale after a recreation so headings/buttons stay translated.
        if (userData.language.isNotBlank()) applyLocaleInPlace(userData.language)

        initGoogleSignIn()
        startService(Intent(this, RegistrationCleanupService::class.java))
        observeConnectivity()
        registerBackHandling()

        if (isIntroShowing) {
            setupIntroLayoutState()
            if (savedInstanceState == null) {
                introJob?.cancel()
                introJob = lifecycleScope.launch {
                    delay(500)
                    fireMrSquareAnimation("hi")
                    binding.globalQuestionTitle.typeWrite(getString(R.string.hello_mr_square), 40)
                    delay(2000)
                    // The user may have navigated on while this was suspended.
                    if (isIntroShowing) setIntroContinueButtonEnabled(true)
                }
            } else {
                // Coming back from a rotation: don't replay the whole intro animation.
                binding.globalQuestionTitle.text = getString(R.string.hello_mr_square)
                setIntroContinueButtonEnabled(true)
            }
        } else {
            showCurrentFragment(navigatingForward = true, animate = false)
        }

        binding.exitBtn.setOnClickListener {
            if (isRegistrationInProgress || isGoogleSignInInProgress) return@setOnClickListener
            handleNavigationBackwards()
        }

        binding.continueEnabledBtn.setOnClickListener { handleContinueClicked() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, currentFragmentIndex)
        outState.putBoolean(KEY_INTRO, isIntroShowing)
        outState.putBoolean(KEY_GOOGLE, isGoogleAccount)
        outState.putBoolean(KEY_PROFILE_SAVED, isProfileSaved)
        outState.putString(KEY_CREATED_EMAIL, createdAuthEmail)
        outState.putString(KEY_LANGUAGE, userData.language)
        outState.putString(KEY_AGE, userData.age)
        outState.putString(KEY_FIRST_NAME, userData.firstName)
        outState.putString(KEY_LAST_NAME, userData.lastName)
        outState.putString(KEY_EMAIL, userData.email)
    }

    private fun restoreState(state: Bundle) {
        currentFragmentIndex = state.getInt(KEY_STEP, STEP_LANGUAGE)
        isIntroShowing = state.getBoolean(KEY_INTRO, true)
        isGoogleAccount = state.getBoolean(KEY_GOOGLE, false)
        isProfileSaved = state.getBoolean(KEY_PROFILE_SAVED, false)
        createdAuthEmail = state.getString(KEY_CREATED_EMAIL)
        userData.language = state.getString(KEY_LANGUAGE).orEmpty()
        userData.age = state.getString(KEY_AGE).orEmpty()
        userData.firstName = state.getString(KEY_FIRST_NAME).orEmpty()
        userData.lastName = state.getString(KEY_LAST_NAME).orEmpty()
        userData.email = state.getString(KEY_EMAIL).orEmpty()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null && !isRegistrationInProgress && !isGoogleAccount && !isProfileSaved) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists() && !isFinishing) {
                        isProfileSaved = true
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }
                .addOnFailureListener {
                    // Offline or rules failure — stay in the flow rather than hanging.
                }
        }
    }

    override fun onDestroy() {
        introJob?.cancel()
        textAnimationJob?.cancel()
        progressAnimator?.cancel()
        deleteUserAuthNodeSilently()
        super.onDestroy()
    }

    private fun registerBackHandling() {
        // onBackPressed() is not called at all on Android 13+ when
        // android:enableOnBackInvokedCallback="true", so route back through the dispatcher.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isRegistrationInProgress || isGoogleSignInInProgress) return
                handleNavigationBackwards()
            }
        })
    }

    // ---------------------------------------------------------------------------------
    // Step navigation
    // ---------------------------------------------------------------------------------

    private fun handleContinueClicked() {
        if (isRegistrationInProgress || isGoogleSignInInProgress) return

        if (isIntroShowing) {
            isIntroShowing = false
            animateLayoutFromIntroToNormal()
            return
        }

        when (currentFragmentIndex) {
            STEP_LANGUAGE -> {
                if (userData.language.isBlank()) return
                applyLocaleInPlace(userData.language)
                goToStep(STEP_AGE)
                return
            }

            STEP_VERIFICATION -> {
                finishRegistrationAndSaveToFirestore()
                return
            }
        }

        if (currentFragmentIndex >= LAST_STEP) return

        val nextStep = currentFragmentIndex + 1
        goToStep(nextStep)

        // Only the email/password branch needs an auth account created up front.
        if (nextStep == STEP_VERIFICATION && !isGoogleAccount) {
            createAuthAccountAndSendEmail()
        }
    }

    private fun goToStep(step: Int) {
        currentFragmentIndex = step
        setContinueButtonEnabled(false)
        if (step !in CUSTOM_ANIMATION_STEPS) fireMrSquareAnimation("okay")
        showCurrentFragment(navigatingForward = true, animate = true)
    }

    fun advanceToNextStep() {
        if (isRegistrationInProgress || isGoogleSignInInProgress) return
        if (isIntroShowing) return
        handleContinueClicked()
    }

    private fun handleNavigationBackwards() {
        if (isIntroShowing) {
            handleExitCleanup()
            return
        }

        if (currentFragmentIndex > STEP_LANGUAGE) {
            if (currentFragmentIndex == STEP_AUTH_METHOD && isGoogleAccount) {
                handleExitCleanup()
                return
            }
            currentFragmentIndex--
            setContinueButtonEnabled(false)
            showCurrentFragment(navigatingForward = false, animate = true)
            return
        }

        isIntroShowing = true
        setupIntroLayoutState()

        introJob?.cancel()
        introJob = lifecycleScope.launch {
            delay(1000)
            fireMrSquareAnimation("hi")
            binding.globalQuestionTitle.typeWrite(getString(R.string.hello_mr_square), 40)
            delay(2000)
            if (isIntroShowing) setIntroContinueButtonEnabled(true)
        }
    }

    private fun showCurrentFragment(navigatingForward: Boolean, animate: Boolean = true) {
        if (isFinishing || isDestroyed) return

        val transaction = supportFragmentManager.beginTransaction()

        if (animate) {
            if (navigatingForward) {
                transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }

        transaction.replace(R.id.questionFragmentContainer, newFragmentForStep(currentFragmentIndex))
        // Async Firebase callbacks can land while the activity is stopped; a plain commit()
        // would throw IllegalStateException there. The step index is saved separately, so
        // losing the transaction state is harmless.
        transaction.commitAllowingStateLoss()

        when (currentFragmentIndex) {
            STEP_NOTIFICATIONS -> fireMrSquareAnimation("notification")
            STEP_EMAIL -> fireMrSquareAnimation("yahoo")
            STEP_PASSWORD -> fireMrSquareAnimation("plan")
        }

        updateProgress(currentFragmentIndex)
        updateHeaderUi()
        refreshUiComponents(false)
    }

    // ---------------------------------------------------------------------------------
    // Intro layout
    // ---------------------------------------------------------------------------------

    private fun setupIntroLayoutState() {
        binding.stepProgressContainer.visibility = View.GONE
        binding.questionFragmentContainer.visibility = View.GONE
        binding.questionHeaderContainer.visibility = View.VISIBLE

        binding.globalQuestionTitle.text = ""

        val constraintLayout = binding.questionHeaderContainer.parent as ConstraintLayout
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        constraintSet.clear(R.id.questionHeaderContainer, ConstraintSet.TOP)
        constraintSet.clear(R.id.questionHeaderContainer, ConstraintSet.BOTTOM)

        constraintSet.connect(R.id.questionHeaderContainer, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraintSet.connect(R.id.questionHeaderContainer, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraintSet.setVerticalChainStyle(R.id.questionHeaderContainer, ConstraintSet.CHAIN_PACKED)

        constraintSet.applyTo(constraintLayout)
        setIntroContinueButtonEnabled(false)
    }

    private fun animateLayoutFromIntroToNormal() {
        val constraintLayout = binding.questionHeaderContainer.parent as ConstraintLayout
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        constraintSet.clear(R.id.questionHeaderContainer, ConstraintSet.TOP)
        constraintSet.clear(R.id.questionHeaderContainer, ConstraintSet.BOTTOM)
        constraintSet.connect(
            R.id.questionHeaderContainer, ConstraintSet.TOP,
            R.id.constraintLayout, ConstraintSet.BOTTOM
        )
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
            .setInterpolator(DecelerateInterpolator())
            .start()

        showCurrentFragment(navigatingForward = true, animate = false)

        binding.questionFragmentContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
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

    // ---------------------------------------------------------------------------------
    // Google sign-in
    // ---------------------------------------------------------------------------------

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
                        val account = task.getResult(ApiException::class.java)
                        val idToken = account?.idToken
                        if (idToken.isNullOrEmpty()) {
                            Toast.makeText(this, getString(R.string.google_error_msg, 0), Toast.LENGTH_LONG).show()
                            resetAuthMethodUiState()
                        } else {
                            firebaseAuthWithGoogle(idToken)
                        }
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

        (supportFragmentManager.findFragmentById(R.id.questionFragmentContainer) as? AuthMethodFragment)
            ?.setControlsEnabled(false)

        refreshUiComponents(false)

        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(
                        this,
                        getString(R.string.firebase_auth_failure, task.exception?.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                    resetAuthMethodUiState()
                    return@addOnCompleteListener
                }

                val user = auth.currentUser
                if (user == null) {
                    resetAuthMethodUiState()
                    return@addOnCompleteListener
                }

                db.collection("users").document(user.uid).get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            isProfileSaved = true
                            Toast.makeText(this, getString(R.string.welcome_back), Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                            return@addOnSuccessListener
                        }

                        isGoogleAccount = true
                        userData.email = user.email.orEmpty()

                        if (userData.firstName.isEmpty()) {
                            val parts = user.displayName?.split(" ")
                            userData.firstName = parts?.getOrNull(0).orEmpty()
                            userData.lastName = parts?.drop(1)?.joinToString(" ").orEmpty()
                        }

                        fireMrSquareAnimation("yahoo")
                        finishRegistrationAndSaveToFirestore()
                    }
                    .addOnFailureListener { e ->
                        // Without this the button stayed stuck on "Signing in…" forever.
                        Toast.makeText(
                            this,
                            getString(R.string.error_prefix, e.message ?: "Unknown"),
                            Toast.LENGTH_LONG
                        ).show()
                        auth.signOut()
                        googleSignInClient.signOut()
                        resetAuthMethodUiState()
                    }
            }
    }

    fun navigateToManualEmailInput() {
        if (currentFragmentIndex != STEP_AUTH_METHOD) return

        currentFragmentIndex = STEP_EMAIL
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

        binding.questionHeaderContainer.animate().alpha(1f).translationY(0f).setDuration(400).start()
        binding.stepProgressContainer.animate().alpha(1f).translationY(0f).setDuration(450).start()
        binding.continueDisabledBtnContainer.animate().alpha(1f).translationY(0f).setDuration(500).start()
    }

    private fun resetAuthMethodUiState() {
        isGoogleSignInInProgress = false
        (supportFragmentManager.findFragmentById(R.id.questionFragmentContainer) as? AuthMethodFragment)
            ?.setControlsEnabled(true)
        resetLoadingState()
    }

    // ---------------------------------------------------------------------------------
    // Account creation / completion
    // ---------------------------------------------------------------------------------

    private fun createAuthAccountAndSendEmail() {
        if (isGoogleAccount) return

        val existing = auth.currentUser
        val alreadyCreatedForCurrentCreds = existing != null &&
                createdAuthEmail != null &&
                createdAuthEmail == userData.email &&
                createdAuthPassword != null &&
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
                    return@addOnCompleteListener
                }

                val error = task.exception
                Toast.makeText(
                    this,
                    getString(R.string.error_prefix, error?.message ?: "Unknown"),
                    Toast.LENGTH_LONG
                ).show()
                resetLoadingState()

                // Send the user back to the field that actually caused the failure.
                currentFragmentIndex = when (error) {
                    is FirebaseAuthWeakPasswordException -> STEP_PASSWORD
                    is FirebaseAuthUserCollisionException -> STEP_EMAIL
                    is FirebaseAuthInvalidCredentialsException -> STEP_EMAIL
                    else -> STEP_PASSWORD
                }
                showCurrentFragment(navigatingForward = false, animate = true)
            }
    }

    private fun finishRegistrationAndSaveToFirestore() {
        if (isRegistrationInProgress) return
        val user = auth.currentUser ?: return

        isRegistrationInProgress = true
        refreshUiComponents(false)

        user.reload().addOnCompleteListener { reloadTask ->
            if (!reloadTask.isSuccessful) {
                resetLoadingState()
                Toast.makeText(
                    this,
                    getString(R.string.error_prefix, reloadTask.exception?.message ?: "Unknown"),
                    Toast.LENGTH_LONG
                ).show()
                return@addOnCompleteListener
            }

            if (!user.isEmailVerified && !isGoogleAccount) {
                resetLoadingState()
                Toast.makeText(this, getString(R.string.verify_email_first), Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener
            }

            fireMrSquareAnimation("yahoo")

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
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                .addOnFailureListener { e ->
                    // Previously missing: a failed write left the UI frozen on
                    // "Creating account…" with no continue button and no way out.
                    resetLoadingState()
                    setContinueButtonEnabled(true)
                    Toast.makeText(
                        this,
                        getString(R.string.error_prefix, e.message ?: "Unknown"),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
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

    // ---------------------------------------------------------------------------------
    // Shared UI helpers used by the step fragments
    // ---------------------------------------------------------------------------------

    fun updateAge(value: String) {
        userData.age = value
    }

    /** Current step index, so a fragment can tell whether it is still the visible one. */
    fun currentStep(): Int = currentFragmentIndex

    fun fireMrSquareAnimation(triggerName: String) {
        binding.mrSquareWelcomeAnim.fireState("State Machine 1", triggerName)
    }

    fun setContinueButtonEnabled(enabled: Boolean) {
        if (isIntroShowing) return
        refreshUiComponents(enabled)
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

    private fun updateHeaderUi() {
        if (isIntroShowing) return

        if (currentFragmentIndex == STEP_AUTH_METHOD) {
            binding.questionHeaderContainer.visibility = View.GONE
            return
        }
        binding.questionHeaderContainer.visibility = View.VISIBLE

        val titleResId = when (currentFragmentIndex) {
            STEP_LANGUAGE -> R.string.choose_your_language
            STEP_AGE -> R.string.what_s_your_age
            STEP_FULL_NAME -> R.string.what_s_your_full_name
            STEP_NOTIFICATIONS -> R.string.notif_description
            STEP_EMAIL -> R.string.enter_your_email_address
            STEP_PASSWORD -> R.string.create_a_password
            STEP_VERIFICATION -> R.string.verify_your_email_address
            else -> null
        }

        titleResId?.let { binding.globalQuestionTitle.typeWrite(getString(it), 25) }
    }

    private fun refreshUiComponents(isBtnEnabled: Boolean) {
        if (isIntroShowing) return

        if (currentFragmentIndex == STEP_AUTH_METHOD) {
            binding.stepProgressContainer.visibility = View.GONE
            binding.continueEnabledBtnContainer.visibility = View.GONE
            binding.continueDisabledBtnContainer.visibility = View.GONE
            binding.questionHeaderContainer.visibility = View.GONE
            return
        }

        if (binding.stepProgressContainer.visibility != View.VISIBLE) {
            binding.stepProgressContainer.visibility = View.VISIBLE
        }

        if (isRegistrationInProgress || isGoogleSignInInProgress) {
            binding.continueEnabledBtnContainer.visibility = View.GONE
            binding.continueDisabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtn.text = if (isGoogleSignInInProgress) {
                getString(R.string.signing_in)
            } else {
                getString(R.string.creating_account)
            }
        } else {
            binding.continueEnabledBtn.isEnabled = isBtnEnabled
            binding.continueEnabledBtnContainer.visibility = if (isBtnEnabled) View.VISIBLE else View.GONE
            binding.continueDisabledBtnContainer.visibility = if (isBtnEnabled) View.GONE else View.VISIBLE
            binding.continueDisabledBtn.text = getString(R.string.continue_text)
        }
    }

    private fun updateProgress(stepIndex: Int) {
        if (isIntroShowing || stepIndex == STEP_AUTH_METHOD) return

        val logicalStepIndex = when (stepIndex) {
            STEP_EMAIL -> 4
            STEP_PASSWORD -> 5
            STEP_VERIFICATION -> 6
            else -> stepIndex
        }

        val totalVisualSteps = 6
        val targetPercent = logicalStepIndex.toFloat() / totalVisualSteps.toFloat()

        val params = binding.progressActive.layoutParams as ConstraintLayout.LayoutParams
        val currentPercent = params.matchConstraintPercentWidth

        // Cancel the in-flight animation, otherwise fast taps leave two animators fighting
        // over the same layout params.
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(currentPercent, targetPercent).apply {
            duration = 500
            interpolator = AnticipateOvershootInterpolator(1.2f)
            addUpdateListener { animator ->
                val animatedValue = animator.animatedValue as Float
                val currentParams = binding.progressActive.layoutParams as ConstraintLayout.LayoutParams
                currentParams.matchConstraintPercentWidth = animatedValue
                binding.progressActive.layoutParams = currentParams
            }
            start()
        }
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
                            if (currentFragmentIndex != STEP_AUTH_METHOD) {
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
}