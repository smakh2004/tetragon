package com.example.tetragon.ui.uiSettings

import android.content.Intent
import android.os.Bundle
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityPreimumNavigationBinding
import com.example.tetragon.subscriptionModel.IntroSubscriptionActivity
import com.example.tetragon.utils.languageChangeUtils.BaseActivity

class PreimumNavigationActivity : BaseActivity() {
    private lateinit var binding: ActivityPreimumNavigationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreimumNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Back button
        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // --- Navigate to Premium Subscription ---
        binding.goPremium.setOnClickListener {
            val intent = Intent(this, IntroSubscriptionActivity::class.java)
            startActivity(intent)
            // Optional: Add transition animation if you have one
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}