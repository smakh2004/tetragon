package com.example.tetragon.ui.uiSettings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivitySettingsBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.ui.WelcomeActivity
import com.example.tetragon.utils.soundUtils.SoundManager
import com.example.tetragon.connectivityCheck.userPresenceUtils.UserPresenceHelper
import com.example.tetragon.notificationsLogic.NotificationHelper
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // System Permission Launcher for Android 13+
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            updateNotificationState(true)
        } else {
            updateNotificationState(false)
            Toast.makeText(this, getString(R.string.notif_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupToggles()
    }

    private fun setupClickListeners() {
        // Back button
        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Account Navigation
        binding.account.setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Premium Navigation
        binding.premium.setOnClickListener {
            startActivity(Intent(this, PreimumNavigationActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Language Navigation
        binding.language.setOnClickListener {
            startActivity(Intent(this, LanguageChooseActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Privacy Policy (Web)
        binding.privacyPolicyText.setOnClickListener {
            val url = "https://sites.google.com/view/tetragon-privacy-policy/home"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No browser found to open link", Toast.LENGTH_SHORT).show()
            }
        }

        // Logout
        binding.logOut.setOnClickListener {
            if (auth.currentUser != null) {
                UserPresenceHelper.setOffline()
                performLogout()
            }
        }
    }

    private fun setupToggles() {
        val sharedPrefs = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val isNotificationEnabled = sharedPrefs.getBoolean("notifications_enabled", false)

        // Sync Notifications UI
        syncNotificationUI(isNotificationEnabled)

        binding.switchNotificationsCustom.setOnClickListener {
            checkAndRequestNotificationPermission()
        }

        binding.switchNotificationsOnCustom.setOnClickListener {
            updateNotificationState(false)
        }

        // --- Sounds Toggle ---
        var isSoundOn = SoundManager.isSoundEnabled(this)
        syncSoundUI(isSoundOn)

        binding.switchSoundsCustom.setOnClickListener {
            isSoundOn = true
            syncSoundUI(true)
        }

        binding.switchSoundsOnCustom.setOnClickListener {
            isSoundOn = false
            syncSoundUI(false)
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                updateNotificationState(true)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            updateNotificationState(true)
        }
    }

    private fun updateNotificationState(enabled: Boolean) {
        val sharedPrefs = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("notifications_enabled", enabled).apply()
        syncNotificationUI(enabled)

        if (enabled) {
            NotificationHelper.scheduleDailyNotification(this)
        } else {
            NotificationHelper.cancelNotifications(this)
        }
    }

    private fun syncNotificationUI(enabled: Boolean) {
        binding.switchNotificationsCustom.visibility = if (enabled) View.INVISIBLE else View.VISIBLE
        binding.switchNotificationsOnCustom.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
    }

    private fun syncSoundUI(enabled: Boolean) {
        binding.switchSoundsCustom.visibility = if (enabled) View.INVISIBLE else View.VISIBLE
        binding.switchSoundsOnCustom.visibility = if (enabled) View.VISIBLE else View.INVISIBLE
        SoundManager.setSoundEnabled(this, enabled)
    }

    private fun performLogout() {
        auth.signOut()
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }
}