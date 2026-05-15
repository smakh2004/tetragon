package com.tetragon.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.languageChangeUtils.LocaleHelper
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : BaseActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedLang = LocaleHelper.getLanguage(this)
        LocaleHelper.setLocale(this, savedLang)
        setContentView(R.layout.activity_splash_screen)
        applySystemUiStyle()
        handleInitialRouting()
    }

    private fun handleInitialRouting() {
        lifecycleScope.launch {
            delay(2500)
            val currentUser = auth.currentUser

            if (currentUser == null) {
                navigateTo(WelcomeActivity::class.java)
                return@launch
            }

            // Try to refresh user status
            currentUser.reload().addOnCompleteListener { reloadTask ->
                if (reloadTask.isSuccessful) {
                    // Network is available and reload worked
                    if (currentUser.isEmailVerified) {
                        checkFirestoreProfile(currentUser)
                    } else {
                        auth.signOut()
                        navigateTo(WelcomeActivity::class.java)
                    }
                } else {
                    val exception = reloadTask.exception
                    if (exception is FirebaseNetworkException) {
                        // OFFLINE CASE: We can't verify status, but the user has a local
                        // session. Let them in; MainActivity will handle offline state.
                        navigateTo(MainActivity::class.java)
                    } else {
                        // OTHER ERROR: Session might be truly revoked (e.g., user deleted)
                        auth.signOut()
                        navigateTo(WelcomeActivity::class.java)
                    }
                }
            }
        }
    }

    // Helper to keep code clean
    private fun checkFirestoreProfile(user: com.google.firebase.auth.FirebaseUser) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    navigateTo(MainActivity::class.java)
                } else {
                    user.delete().addOnCompleteListener {
                        auth.signOut()
                        Toast.makeText(this@SplashScreenActivity, "Registration incomplete.", Toast.LENGTH_LONG).show()
                        navigateTo(WelcomeActivity::class.java)
                    }
                }
            }
            .addOnFailureListener { e ->
                if (e is FirebaseNetworkException) {
                    // Network error fetching profile? Let them in for offline mode.
                    navigateTo(MainActivity::class.java)
                } else {
                    auth.signOut()
                    navigateTo(WelcomeActivity::class.java)
                }
            }
    }

    private fun navigateTo(destination: Class<*>) {
        val intent = Intent(this, destination)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    private fun applySystemUiStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }
    }
}