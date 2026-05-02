package com.example.tetragon.ui.uiSettings

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityPreimumNavigationBinding
import com.example.tetragon.subscriptionModel.IntroSubscriptionActivity
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class PreimumNavigationActivity : BaseActivity() {
    private lateinit var binding: ActivityPreimumNavigationBinding
    private var subscriptionListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreimumNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Back button
        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Navigate to Premium Subscription
        binding.goPremium.setOnClickListener {
            val intent = Intent(this, IntroSubscriptionActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun listenToSubscriptionStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        subscriptionListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    val isSubscribed = snapshot.getBoolean("subscription") ?: false

                    if (isSubscribed) {
                        // Use translated "Premium" string
                        binding.currentPlanStatus.text = getString(R.string.premium_plan_premium)
                        binding.currentPlanStatus.setTextColor(getColor(R.color.blue_2))
                        binding.goPremium.visibility = View.GONE
                    } else {
                        // Use translated "Free" string
                        binding.currentPlanStatus.text = getString(R.string.premium_plan_free)
                        binding.currentPlanStatus.setTextColor(getColor(R.color.blue_1))
                        binding.goPremium.visibility = View.VISIBLE
                    }
                }
            }
    }

    override fun onStart() {
        super.onStart()
        listenToSubscriptionStatus()
    }

    override fun onStop() {
        super.onStop()
        // Stop listening when activity is not visible to save resources
        subscriptionListener?.remove()
    }
}