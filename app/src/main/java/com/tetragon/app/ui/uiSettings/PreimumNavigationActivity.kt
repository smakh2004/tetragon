package com.tetragon.app.ui.uiSettings

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityPreimumNavigationBinding
import com.tetragon.app.subscriptionModel.IntroSubscriptionActivity
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale

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

        // Turn loading state overlay layout view component visible before fetching data snapshots
        binding.loadingOverlayContainer.visibility = View.VISIBLE

        subscriptionListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                // Dismiss white loading view layer safely as soon as document response returns
                binding.loadingOverlayContainer.visibility = View.GONE

                if (error != null) return@addSnapshotListener

                if (snapshot != null && snapshot.exists()) {
                    val isSubscribed = snapshot.getBoolean("subscription") ?: false

                    if (isSubscribed) {
                        // Use translated "Premium" string
                        binding.currentPlanStatus.text = getString(R.string.premium_plan_premium)
                        binding.currentPlanStatus.setTextColor(getColor(R.color.blue_2))
                        binding.goPremium.visibility = View.GONE

                        // Parse out structural document expiry timestamp object
                        val expiryTimestamp = snapshot.getTimestamp("subscriptionUntil")
                        if (expiryTimestamp != null) {
                            val expiryDate = expiryTimestamp.toDate()
                            val dateFormatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                            val formattedDate = dateFormatter.format(expiryDate)

                            // Bind localized message format to the target view component layer
                            binding.subscriptionExpiryText.text = getString(R.string.subscription_ends_format, formattedDate)
                            binding.subscriptionExpiryText.visibility = View.VISIBLE
                        } else {
                            binding.subscriptionExpiryText.visibility = View.GONE
                        }
                    } else {
                        // Use translated "Free" string
                        binding.currentPlanStatus.text = getString(R.string.premium_plan_free)
                        binding.currentPlanStatus.setTextColor(getColor(R.color.blue_1))
                        binding.goPremium.visibility = View.VISIBLE

                        // Enforce isolating/hiding expiration information block when user is on a free plan tier
                        binding.subscriptionExpiryText.visibility = View.GONE
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