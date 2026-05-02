package com.example.tetragon.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.subscriptionModel.IntroSubscriptionActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Date

class ShopFragment : Fragment() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userListener: ListenerRegistration? = null

    // UI Components
    private lateinit var coinCountText: TextView
    private lateinit var itemStar: ConstraintLayout
    private lateinit var starPriceContainer: LinearLayout
    private lateinit var starStatusText: TextView
    private lateinit var itemInfinity: ConstraintLayout
    private lateinit var infinityPriceContainer: LinearLayout
    private lateinit var infinityStatusText: TextView

    // Claim Container UI Components (Updated for LinearLayout Button)
    private lateinit var startContainer: FrameLayout
    private lateinit var startLessonLabel: TextView
    private lateinit var confirmBtn: LinearLayout // Changed from Button
    private lateinit var confirmCoinAmount: TextView // The new TextView inside button
    private lateinit var confirmBtnContainer: FrameLayout
    private lateinit var processingBtnContainer: FrameLayout
    private lateinit var shopScrollView: NestedScrollView

    // State Variables
    private var currentCoins: Long = 0
    private var currentStars: Long = 0
    private var pendingPurchaseType: String? = null // "STAR" or "INFINITY"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_shop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners(view)
    }

    private fun initViews(view: View) {
        coinCountText = view.findViewById(R.id.coinCountText)
        shopScrollView = view.findViewById(R.id.shopScrollView)

        // Purchase Item Views
        itemStar = view.findViewById(R.id.item_star)
        starPriceContainer = view.findViewById(R.id.star_price_container)
        starStatusText = view.findViewById(R.id.star_status_text)

        itemInfinity = view.findViewById(R.id.item_infinity)
        infinityPriceContainer = view.findViewById(R.id.infinity_price_container)
        infinityStatusText = view.findViewById(R.id.infinity_status_text)

        // Confirmation Drawer Views
        startContainer = view.findViewById(R.id.start_container)
        startLessonLabel = view.findViewById(R.id.start_lesson_label)

        // References for the new Custom Button structure
        confirmBtn = view.findViewById(R.id.continue_enabled_btn)
        confirmCoinAmount = view.findViewById(R.id.confirm_coin_amount)

        confirmBtnContainer = view.findViewById(R.id.start_enabled_btn_container)
        processingBtnContainer = view.findViewById(R.id.start_disabled_btn_container)

        // Initial UI State
        startContainer.visibility = View.GONE
        startContainer.alpha = 0f
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners(view: View) {
        val topBarShadowGradient = view.findViewById<View>(R.id.topBarShadowGradient)
        val subscribeBtn = view.findViewById<View>(R.id.subscribe_enabled_btn)

        // 1. Scroll Behavior: Handle Shadow & Dismiss Drawer
        shopScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            topBarShadowGradient.visibility = if (scrollY > 0) View.VISIBLE else View.INVISIBLE

            if (startContainer.visibility == View.VISIBLE && Math.abs(scrollY - oldScrollY) > 10) {
                hidePurchaseDrawer()
            }
        })

        // 2. Background Touch: Dismiss Drawer
        shopScrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && startContainer.visibility == View.VISIBLE) {
                hidePurchaseDrawer()
            }
            false
        }

        // 3. Purchase Triggers
        itemStar.setOnClickListener {
            if (currentStars < 15) {
                pendingPurchaseType = "STAR"
                // Uses: "Refill Stars"
                showPurchaseDrawer(getString(R.string.refill_stars_label), "15")
            } else {
                // Uses: "Stars are already full!"
                Toast.makeText(context, getString(R.string.stars_full_toast), Toast.LENGTH_SHORT).show()
            }
        }

        itemInfinity.setOnClickListener {
            pendingPurchaseType = "INFINITY"
            // Uses: "Monthly Infinity"
            showPurchaseDrawer(getString(R.string.monthly_infinity_label), "1000")
        }

        // 4. Drawer Confirmation
        confirmBtn.setOnClickListener {
            handleConfirmPurchase()
        }

        // 5. Subscription Button
        subscribeBtn.setOnClickListener {
            val intent = Intent(requireContext(), IntroSubscriptionActivity::class.java)
            startActivity(intent)
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun showPurchaseDrawer(label: String, price: String) {
        startLessonLabel.text = label
        confirmCoinAmount.text = price // Update the price inside the button

        confirmBtnContainer.visibility = View.VISIBLE
        processingBtnContainer.visibility = View.GONE
        startContainer.visibility = View.VISIBLE

        startContainer.translationY = 400f
        startContainer.alpha = 0f
        startContainer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(0.7f))
            .start()
    }

    private fun hidePurchaseDrawer() {
        if (startContainer.visibility == View.GONE) return

        startContainer.animate()
            .translationY(400f)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                startContainer.visibility = View.GONE
            }
            .start()
        pendingPurchaseType = null
    }

    private fun handleConfirmPurchase() {
        val type = pendingPurchaseType ?: return

        confirmBtnContainer.visibility = View.GONE
        processingBtnContainer.visibility = View.VISIBLE

        when (type) {
            "STAR" -> {
                if (currentCoins >= 15) {
                    performPurchase(currentCoins - 15, mapOf("stars" to 15L), getString(R.string.stars_refilled_toast))
                } else {
                    showError(getString(R.string.not_enough_coins))
                }
            }
            "INFINITY" -> {
                if (currentCoins >= 1000) {
                    val calendar = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
                    val updates = mapOf(
                        "subscriptionUntil" to Timestamp(calendar.time),
                        "planType" to "monthly",
                        "subscription" to true
                    )
                    performPurchase(currentCoins - 1000, updates, getString(R.string.infinity_activated_toast))
                } else {
                    showError(getString(R.string.not_enough_coins))
                }
            }
        }
    }

    private fun performPurchase(newCoins: Long, updates: Map<String, Any>, message: String) {
        val uid = auth.currentUser?.uid ?: return
        val finalMap = updates.toMutableMap()
        finalMap["coins"] = newCoins

        db.collection("users").document(uid).update(finalMap)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    hidePurchaseDrawer()
                }
            }
            .addOnFailureListener {
                confirmBtnContainer.visibility = View.VISIBLE
                processingBtnContainer.visibility = View.GONE
                Toast.makeText(context, getString(R.string.transaction_failed), Toast.LENGTH_SHORT).show()
            }
    }

    private fun showError(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        hidePurchaseDrawer()
    }

    private fun observeUserStats() {
        val uid = auth.currentUser?.uid ?: return
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    currentCoins = snapshot.getLong("coins") ?: 0L
                    currentStars = snapshot.getLong("stars") ?: 0L

                    val expiry = snapshot.getTimestamp("subscriptionUntil")
                    val isSubscriptionActive = snapshot.getBoolean("subscription") == true &&
                            expiry != null && expiry.toDate().after(Date())

                    coinCountText.text = currentCoins.toString()
                    updateItemUI(isSubscriptionActive, currentStars >= 15)
                }
            }
    }

    private fun updateItemUI(isSubscribed: Boolean, isStarsFull: Boolean) {
        // Infinity Item
        if (isSubscribed) {
            infinityPriceContainer.visibility = View.GONE
            infinityStatusText.visibility = View.VISIBLE
            itemInfinity.isClickable = false
        } else {
            infinityPriceContainer.visibility = View.VISIBLE
            infinityStatusText.visibility = View.GONE
            itemInfinity.isClickable = true
        }

        // Star Item
        if (isStarsFull) {
            starPriceContainer.visibility = View.GONE
            starStatusText.visibility = View.VISIBLE
            itemStar.isClickable = false
        } else {
            starPriceContainer.visibility = View.VISIBLE
            starStatusText.visibility = View.GONE
            itemStar.isClickable = true
        }
    }

    override fun onStart() {
        super.onStart()
        observeUserStats()
    }

    override fun onStop() {
        super.onStop()
        userListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<FrameLayout>(R.id.start_container)?.visibility = View.GONE
        view?.findViewById<FrameLayout>(R.id.subscribe_enabled_btn_container)?.visibility = View.VISIBLE
        view?.findViewById<FrameLayout>(R.id.subscribe_disabled_btn_container)?.visibility = View.INVISIBLE
    }
}