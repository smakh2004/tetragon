package com.tetragon.app.ui

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.languageChangeUtils.LocaleHelper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : BaseActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var logoContainer: LinearLayout
    private lateinit var mascotImageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTextView: TextView

    private var currentProgressAnimator: ValueAnimator? = null
    private var introAnimationSet: AnimatorSet? = null

    // Track MediaPlayer instance safely
    private var introMediaPlayer: MediaPlayer? = null

    // Guard flag to prevent multi-navigation or race-condition redundancies
    private val isNavigating = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 0% Milestone: Window Setup
        val savedLang = LocaleHelper.getLanguage(this)
        LocaleHelper.setLocale(this, savedLang)
        setContentView(R.layout.activity_splash_screen)

        logoContainer = findViewById(R.id.logoContainer)
        mascotImageView = findViewById(R.id.mascotImageView)
        progressBar = findViewById(R.id.progressBar)
        progressTextView = findViewById(R.id.progressTextView)

        applySystemUiStyle()

        // Wait for 2 seconds (2000ms) while keeping the logo stationary and 1.2x scale in the middle
        logoContainer.postDelayed({
            if (!isFinishing && !isDestroyed) {
                runIntroAnimation()
            }
        }, 2000)
    }

    /**
     * Slides the logo up, shrinks scale to normal, plays intro sound,
     * and triggers early overlapping reveal transitions.
     */
    private fun runIntroAnimation() {
        // --- Play Sound Exactly As Layout Movement Begins ---
        try {
            introMediaPlayer = MediaPlayer.create(this, R.raw.intro).apply {
                setOnCompletionListener { mp ->
                    mp.release()
                    if (introMediaPlayer == mp) introMediaPlayer = null
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace() // Keeps app from crashing if audio driver breaks or file is missing
        }

        // 1. Shifting vertical constraint bias from centered (0.5f) up towards the top (0.08f)
        val biasAnimator = ValueAnimator.ofFloat(0.5f, 0.08f).apply {
            addUpdateListener { animator ->
                val params = logoContainer.layoutParams as ConstraintLayout.LayoutParams
                params.verticalBias = animator.animatedValue as Float
                logoContainer.layoutParams = params
            }
        }

        // 2. Scaling down X and Y from 1.2f back to normal 1.0f
        val scaleXAnimator = ObjectAnimator.ofFloat(logoContainer, View.SCALE_X, 1.2f, 1.0f)
        val scaleYAnimator = ObjectAnimator.ofFloat(logoContainer, View.SCALE_Y, 1.2f, 1.0f)

        // Play translation and scale modifications simultaneously
        introAnimationSet = AnimatorSet().apply {
            duration = 800
            interpolator = DecelerateInterpolator(1.5f)
            playTogether(biasAnimator, scaleXAnimator, scaleYAnimator)

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Start progress metrics tracking once logo sequence fully wraps up
                    animateProgressTo(20) {
                        handleInitialRouting()
                    }
                }
            })
            start()
        }

        // Overlap: Make secondary elements appear early (400ms into the 800ms logo movement)
        logoContainer.postDelayed({
            if (!isFinishing && !isDestroyed) {
                revealRemainingElementsEarly()
            }
        }, 400)
    }

    /**
     * Un-hides and smoothly blends secondary assets while logo is still moving.
     */
    private fun revealRemainingElementsEarly() {
        mascotImageView.visibility = View.VISIBLE
        progressTextView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE

        mascotImageView.animate().alpha(1f).setDuration(400).start()
        progressTextView.animate().alpha(1f).setDuration(400).start()
        progressBar.animate().alpha(1f).setDuration(400).start()
    }

    private fun handleInitialRouting() {
        lifecycleScope.launch {
            // Visual breathing room
            delay(400)

            val currentUser = auth.currentUser
            if (currentUser == null) {
                // No session -> Jump instantly to completion
                finishProgressAndNavigate(WelcomeActivity::class.java)
                return@launch
            }

            // 50% Milestone: Authenticated Session found, reloading credentials
            animateProgressTo(50)

            currentUser.reload().addOnCompleteListener { reloadTask ->
                // Guard check: if already navigating out, drop background execution
                if (isNavigating.get()) return@addOnCompleteListener

                if (reloadTask.isSuccessful) {
                    if (currentUser.isEmailVerified) {
                        // 75% Milestone: Network reload verified, jumping to profile validation
                        animateProgressTo(75)
                        checkFirestoreProfile(currentUser)
                    } else {
                        auth.signOut()
                        finishProgressAndNavigate(WelcomeActivity::class.java)
                    }
                } else {
                    val exception = reloadTask.exception
                    when (exception) {
                        is FirebaseNetworkException -> {
                            finishProgressAndNavigate(MainActivity::class.java)
                        }
                        is FirebaseAuthInvalidUserException -> {
                            auth.signOut()
                            finishProgressAndNavigate(WelcomeActivity::class.java)
                        }
                        else -> {
                            finishProgressAndNavigate(MainActivity::class.java)
                        }
                    }
                }
            }
        }
    }

    private fun checkFirestoreProfile(user: com.google.firebase.auth.FirebaseUser) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (isNavigating.get()) return@addOnSuccessListener

                if (document.exists()) {
                    finishProgressAndNavigate(MainActivity::class.java)
                } else {
                    user.delete().addOnCompleteListener {
                        auth.signOut()
                        Toast.makeText(this@SplashScreenActivity, "Registration incomplete.", Toast.LENGTH_LONG).show()
                        finishProgressAndNavigate(WelcomeActivity::class.java)
                    }
                }
            }
            .addOnFailureListener { e ->
                if (isNavigating.get()) return@addOnFailureListener

                if (e is FirebaseNetworkException) {
                    finishProgressAndNavigate(MainActivity::class.java)
                } else {
                    finishProgressAndNavigate(MainActivity::class.java)
                }
            }
    }

    /**
     * Smoothly transitions the progress indicator from its current position up to a target milestone percentage.
     */
    private fun animateProgressTo(target: Int, onComplete: (() -> Unit)? = null) {
        currentProgressAnimator?.cancel()

        val startValue = progressBar.progress
        currentProgressAnimator = ValueAnimator.ofInt(startValue, target).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Int
                progressBar.progress = progress
                progressTextView.text = "$progress%"
            }
            onComplete?.let {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        it.invoke()
                    }
                })
            }
            start()
        }
    }

    /**
     * Snaps progress directly to 100%, updates visual displays, and performs the screen intent redirection.
     */
    private fun finishProgressAndNavigate(destination: Class<*>) {
        if (isNavigating.compareAndSet(false, true)) {
            animateProgressTo(100) {
                // If audio is still lingering when screen changes, gracefully halt it
                stopAndReleaseAudio()

                val intent = Intent(this, destination)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                finish()
            }
        }
    }

    private fun applySystemUiStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }
    }

    private fun stopAndReleaseAudio() {
        try {
            introMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            introMediaPlayer = null
        }
    }

    override fun onDestroy() {
        logoContainer.removeCallbacks(null)
        introAnimationSet?.cancel()
        currentProgressAnimator?.cancel()
        stopAndReleaseAudio() // Prevent background sound leaks if user exits app early
        super.onDestroy()
    }
}