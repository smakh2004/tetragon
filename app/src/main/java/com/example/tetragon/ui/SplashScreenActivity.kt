package com.example.tetragon.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.tetragon.MainActivity
import com.example.tetragon.R
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.languageChangeUtils.LocaleHelper
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
            } else {
                // Check the server for the latest status
                currentUser.reload().addOnCompleteListener { reloadTask ->
                    if (reloadTask.isSuccessful && currentUser.isEmailVerified) {

                        // CRITICAL: Check if the Firestore profile exists
                        db.collection("users").document(currentUser.uid).get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    // 1. User is verified AND has a profile -> Success
                                    navigateTo(MainActivity::class.java)
                                } else {
                                    // 2. User is verified BUT has no profile (closed app early)
                                    // We delete the Auth account so they can try again fresh
                                    currentUser.delete().addOnCompleteListener {
                                        auth.signOut()
                                        Toast.makeText(this@SplashScreenActivity, "Registration incomplete. Please try again.", Toast.LENGTH_LONG).show()
                                        navigateTo(WelcomeActivity::class.java)
                                    }
                                }
                            }
                            .addOnFailureListener {
                                // Network error or Firestore down
                                auth.signOut()
                                navigateTo(WelcomeActivity::class.java)
                            }
                    } else {
                        // 3. Not verified -> Back to Welcome
                        auth.signOut()
                        navigateTo(WelcomeActivity::class.java)
                    }
                }
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