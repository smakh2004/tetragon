package com.example.tetragon.ui.uiSettings

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivitySettingsBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.ui.WelcomeActivity
import com.example.tetragon.utils.soundUtils.SoundManager
import com.example.tetragon.connectivityCheck.userPresenceUtils.UserPresenceHelper
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : BaseActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Back button
        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Account
        binding.account.setOnClickListener {
            startActivity(Intent(this, AccountActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Premium
        binding.premium.setOnClickListener {
            startActivity(Intent(this, PreimumNavigationActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Notifications
        var isNotificationOn = false

        binding.switchNotificationsCustom.setOnClickListener {

            isNotificationOn = !isNotificationOn

            if (isNotificationOn) {
                binding.switchNotificationsCustom.visibility = View.INVISIBLE
                binding.switchNotificationsOnCustom.visibility = View.VISIBLE
            } else {
                binding.switchNotificationsCustom.visibility = View.VISIBLE
                binding.switchNotificationsOnCustom.visibility = View.INVISIBLE
            }
        }

        binding.switchNotificationsOnCustom.setOnClickListener {

            isNotificationOn = !isNotificationOn

            if (isNotificationOn) {
                binding.switchNotificationsCustom.visibility = View.INVISIBLE
                binding.switchNotificationsOnCustom.visibility = View.VISIBLE
            } else {
                binding.switchNotificationsCustom.visibility = View.VISIBLE
                binding.switchNotificationsOnCustom.visibility = View.INVISIBLE
            }
        }

        // Sounds
        var isSoundOn = SoundManager.isSoundEnabled(this)

        if (isSoundOn) {
            binding.switchSoundsCustom.visibility = View.INVISIBLE
            binding.switchSoundsOnCustom.visibility = View.VISIBLE
        } else {
            binding.switchSoundsCustom.visibility = View.VISIBLE
            binding.switchSoundsOnCustom.visibility = View.INVISIBLE
        }

        fun updateSoundUI() {
            binding.switchSoundsCustom.visibility =
                if (isSoundOn) View.INVISIBLE else View.VISIBLE

            binding.switchSoundsOnCustom.visibility =
                if (isSoundOn) View.VISIBLE else View.INVISIBLE

            SoundManager.setSoundEnabled(this, isSoundOn)
        }

        binding.switchSoundsCustom.setOnClickListener {
            isSoundOn = true
            updateSoundUI()
        }

        binding.switchSoundsOnCustom.setOnClickListener {
            isSoundOn = false
            updateSoundUI()
        }

        // Language
        binding.language.setOnClickListener {
            startActivity(Intent(this, LanguageChooseActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // Logout button
        binding.logOut.setOnClickListener {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // 1. Manually trigger offline status first
                UserPresenceHelper.setOffline()

                // 2. Sign out and navigate
                auth.signOut()
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                finish()
            }
        }


    }

    // Helper function to handle navigation and signing out
    fun performLogout() {
        auth.signOut()
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

}