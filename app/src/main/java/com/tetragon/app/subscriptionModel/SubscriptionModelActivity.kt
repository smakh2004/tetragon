package com.tetragon.app.subscriptionModel

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import app.rive.runtime.kotlin.core.Rive
import com.android.billingclient.api.Purchase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivitySubscriptionModelBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import java.util.Calendar
import java.util.Date

class SubscriptionModelActivity : BaseActivity() {
    private lateinit var binding: ActivitySubscriptionModelBinding
    private lateinit var billingHelper: BillingHelper

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var selectedProductId = BillingHelper.PREMIUM_ANNUAL

    // Флаг для полной блокировки любых нажатий и навигации во время загрузки/оплаты
    private var isProcessing = false

    // Флаг для отслеживания уже купленной подписки
    private var isAlreadySubscribed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivitySubscriptionModelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Restore state if the Activity was recreated (e.g. system killed it
        // while the Play purchase sheet was in front of it).
        savedInstanceState?.let {
            selectedProductId = it.getString(KEY_SELECTED_PRODUCT, BillingHelper.PREMIUM_ANNUAL)
            isAlreadySubscribed = it.getBoolean(KEY_ALREADY_SUBSCRIBED, false)
        }

        // Handle back presses via the modern dispatcher instead of overriding
        // the deprecated onBackPressed() — avoids crashes/inconsistent
        // behavior on Android 13+ with predictive back enabled.
        onBackPressedDispatcher.addCallback(this) {
            if (!isProcessing) {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }

        showLoading(true)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, getString(R.string.error_auth_failed), Toast.LENGTH_SHORT).show()
            showLoading(false)
            finish()
            return
        }

        applySelectedProductUi()
        checkExistingSubscription(uid)
        setupClickListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED_PRODUCT, selectedProductId)
        outState.putBoolean(KEY_ALREADY_SUBSCRIBED, isAlreadySubscribed)
    }

    override fun onResume() {
        super.onResume()
        // Safety net: if we come back from the Play purchase sheet (user backed
        // out without buying) and, for whatever reason, onPurchaseCancelled /
        // onError never arrived, don't leave the button stuck on "loading".
        if (isProcessing && ::billingHelper.isInitialized) {
            showLoading(false)
        }
    }

    // Проверяем базу данных: если подписка активна для ЭТОГО uid, меняем статус кнопки
    private fun checkExistingSubscription(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                if (!isFinishing && !isDestroyed) {
                    if (snapshot != null && snapshot.exists()) {
                        val isSubscribed = snapshot.getBoolean("subscription") == true
                        val expiry = snapshot.getTimestamp("subscriptionUntil")
                        val isSubscriptionActive = isSubscribed && expiry != null && expiry.toDate().after(Date())

                        if (isSubscriptionActive) {
                            isAlreadySubscribed = true
                        }
                    }
                    initBilling(uid)
                }
            }
            .addOnFailureListener {
                if (!isFinishing && !isDestroyed) {
                    initBilling(uid)
                }
            }
    }

    private fun initBilling(uid: String) {
        billingHelper = BillingHelper(
            context = this,
            currentUserId = uid,
            onBillingReady = {
                if (!isFinishing && !isDestroyed) {
                    showLoading(false)
                }
            },
            onPurchaseSuccess = { purchase, isNewPurchase ->
                if (!isFinishing && !isDestroyed) {
                    handleSuccessfulPremium(purchase, isNewPurchase)
                }
            },
            onPurchaseCancelled = {
                // User pressed Back / dismissed the Play purchase sheet without buying —
                // just unlock the UI again, no error toast needed.
                if (!isFinishing && !isDestroyed) {
                    showLoading(false)
                }
            },
            onError = { errorMessage ->
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    showLoading(false)
                }
            }
        )
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.containerAnnual.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            selectedProductId = BillingHelper.PREMIUM_ANNUAL
            applySelectedProductUi()
        }

        binding.containerMonthly.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            selectedProductId = BillingHelper.PREMIUM_MONTHLY
            applySelectedProductUi()
        }

        binding.subscribeEnabledBtn.setOnClickListener {
            if (isProcessing || isAlreadySubscribed) return@setOnClickListener
            if (!::billingHelper.isInitialized) return@setOnClickListener
            showLoading(true)
            billingHelper.launchPurchaseFlow(this, selectedProductId)
        }
    }

    // Pulled out of the click listeners so it can also run after process
    // restoration, keeping the highlighted plan in sync with selectedProductId.
    private fun applySelectedProductUi() {
        if (selectedProductId == BillingHelper.PREMIUM_ANNUAL) {
            binding.containerAnnual.setBackgroundResource(R.drawable.bg_subscription_selected)
            binding.containerMonthly.setBackgroundResource(R.drawable.bg_subscription_unselected)

            binding.correctMrSquare2.visibility = View.VISIBLE
            binding.correctMrSquare2.fireState("State Machine 1", "play")

            binding.correctMrSquare.visibility = View.GONE
            binding.correctMrSquare.fireState("State Machine 1", "finish")
        } else {
            binding.containerAnnual.setBackgroundResource(R.drawable.bg_subscription_unselected)
            binding.containerMonthly.setBackgroundResource(R.drawable.bg_subscription_selected)

            binding.correctMrSquare2.visibility = View.GONE
            binding.correctMrSquare2.fireState("State Machine 1", "finish")

            binding.correctMrSquare.visibility = View.VISIBLE
            binding.correctMrSquare.fireState("State Machine 1", "play")
        }
    }

    private fun showLoading(isLoading: Boolean) {
        isProcessing = isLoading

        if (isAlreadySubscribed) {
            binding.subscribeEnabledBtnContainer.visibility = View.INVISIBLE
            binding.subscribeDisabledBtnContainer.visibility = View.VISIBLE
            binding.subscribeEnabledBtn.isEnabled = false

            binding.subscribeDisabledBtn.text = getString(R.string.activated)

            binding.btnBack.isEnabled = !isLoading
            binding.containerAnnual.isEnabled = !isLoading
            binding.containerMonthly.isEnabled = !isLoading
            return
        }

        if (isLoading) {
            binding.subscribeEnabledBtnContainer.visibility = View.INVISIBLE
            binding.subscribeDisabledBtnContainer.visibility = View.VISIBLE

            binding.subscribeEnabledBtn.isEnabled = false
            binding.btnBack.isEnabled = false
            binding.containerAnnual.isEnabled = false
            binding.containerMonthly.isEnabled = false
        } else {
            binding.subscribeEnabledBtnContainer.visibility = View.VISIBLE
            binding.subscribeDisabledBtnContainer.visibility = View.INVISIBLE

            binding.subscribeEnabledBtn.isEnabled = true
            binding.btnBack.isEnabled = true
            binding.containerAnnual.isEnabled = true
            binding.containerMonthly.isEnabled = true
        }
    }

    private fun handleSuccessfulPremium(purchase: Purchase, isNewPurchase: Boolean) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, getString(R.string.error_auth_failed), Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        val userDocRef = db.collection("users").document(uid)
        val purchasedProductId = purchase.products.firstOrNull() ?: selectedProductId
        val planType = if (purchasedProductId == BillingHelper.PREMIUM_MONTHLY) "monthly" else "annual"

        // Extend from existing expiry if still active, otherwise from now —
        // this covers a user renewing before their current period ends.
        userDocRef.get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            val existingExpiry = snapshot?.getTimestamp("subscriptionUntil")?.toDate()
            val baseDate = if (existingExpiry != null && existingExpiry.after(Date())) existingExpiry else Date()

            val calendar = Calendar.getInstance().apply { time = baseDate }
            if (planType == "monthly") calendar.add(Calendar.MONTH, 1) else calendar.add(Calendar.YEAR, 1)

            val subscriptionUpdates = hashMapOf<String, Any>(
                "subscription" to true,
                "planType" to planType,
                "subscriptionUntil" to Timestamp(calendar.time)
            )

            userDocRef.update(subscriptionUpdates)
                .addOnSuccessListener {
                    if (!isFinishing && !isDestroyed) {
                        showLoading(false)
                        Toast.makeText(this, getString(R.string.premium_activated_success), Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    if (!isFinishing && !isDestroyed) {
                        showLoading(false)
                        val errorMsg = getString(R.string.error_firestore_sync, e.message)
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingHelper.isInitialized) {
            billingHelper.release()
        }
    }

    companion object {
        private const val KEY_SELECTED_PRODUCT = "selected_product_id"
        private const val KEY_ALREADY_SUBSCRIBED = "already_subscribed"
    }
}