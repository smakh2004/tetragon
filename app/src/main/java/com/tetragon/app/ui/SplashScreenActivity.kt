package com.tetragon.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.languageChangeUtils.LocaleHelper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : BaseActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var rootView: View
    private lateinit var logoContainer: View

    private var splashStartTime = 0L
    private var pendingNavigation: Runnable? = null

    // Prevents double navigation / race conditions
    private val isNavigating = AtomicBoolean(false)

    companion object {
        // Minimum time the splash (Rive animation) stays on screen
        private const val MIN_DISPLAY_MS = 3000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load Rive's native library BEFORE inflating the layout that contains
        // RiveAnimationView. Inflation-order-safe regardless of the Application class.
        Rive.init(this)

        val savedLang = LocaleHelper.getLanguage(this)
        LocaleHelper.setLocale(this, savedLang)
        setContentView(R.layout.activity_splash_screen)

        rootView = findViewById(R.id.main)
        logoContainer = findViewById(R.id.logoContainer)
        splashStartTime = System.currentTimeMillis()

        // BaseActivity uses enableEdgeToEdge(), so pad the bottom logo away from
        // the nav bar while the Rive art stays full-bleed behind the bars.
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            logoContainer.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        // Rive plays automatically (riveAutoPlay="true"); just run routing.
        handleInitialRouting()
    }

    private fun handleInitialRouting() {
        // Always route into the app. The update dialog (if the user is behind
        // the current version) is handled inside MainActivity by the live
        // Firestore listener — this avoids ever getting stuck on a blank splash.
        proceedWithUserSessionValidation()
    }

    private fun proceedWithUserSessionValidation() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            navigateTo(WelcomeActivity::class.java)
            return
        }

        currentUser.reload().addOnCompleteListener { reloadTask ->
            if (isNavigating.get()) return@addOnCompleteListener

            if (reloadTask.isSuccessful) {
                if (currentUser.isEmailVerified) {
                    checkFirestoreProfile(currentUser)
                } else {
                    auth.signOut()
                    navigateTo(WelcomeActivity::class.java)
                }
            } else {
                when (reloadTask.exception) {
                    is FirebaseNetworkException -> navigateTo(MainActivity::class.java)
                    is FirebaseAuthInvalidUserException -> {
                        auth.signOut()
                        navigateTo(WelcomeActivity::class.java)
                    }
                    else -> navigateTo(MainActivity::class.java)
                }
            }
        }
    }

    private fun checkFirestoreProfile(user: com.google.firebase.auth.FirebaseUser) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (isNavigating.get()) return@addOnSuccessListener

                if (document.exists()) {
                    navigateTo(MainActivity::class.java)
                } else {
                    user.delete().addOnCompleteListener {
                        auth.signOut()
                        Toast.makeText(
                            this@SplashScreenActivity,
                            "Registration incomplete.",
                            Toast.LENGTH_LONG
                        ).show()
                        navigateTo(WelcomeActivity::class.java)
                    }
                }
            }
            .addOnFailureListener {
                if (isNavigating.get()) return@addOnFailureListener
                navigateTo(MainActivity::class.java)
            }
    }

    /**
     * Navigates to [destination], guaranteeing the splash (and its Rive
     * animation) is visible for at least MIN_DISPLAY_MS (3s).
     */
    private fun navigateTo(destination: Class<*>) {
        if (!isNavigating.compareAndSet(false, true)) return

        val elapsed = System.currentTimeMillis() - splashStartTime
        val remaining = (MIN_DISPLAY_MS - elapsed).coerceAtLeast(0L)

        val runnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            startActivity(Intent(this, destination))
            finish()
        }
        pendingNavigation = runnable
        rootView.postDelayed(runnable, remaining)
    }

    override fun onDestroy() {
        pendingNavigation?.let { rootView.removeCallbacks(it) }
        super.onDestroy()
    }
}