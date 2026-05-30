package com.tetragon.app.starsBuy

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import java.util.concurrent.TimeUnit

class StarsActivity : BaseActivity() {

    private lateinit var backBtn: ImageView
    private lateinit var coinCountText: TextView
    private lateinit var centerStarGraphic: ImageView
    private lateinit var attemptsLabel: TextView

    private lateinit var itemInfinity: ConstraintLayout
    private lateinit var infinityPriceContainer: LinearLayout
    private lateinit var infinityStatusText: TextView

    private lateinit var itemStar: ConstraintLayout
    private lateinit var starPriceContainer: LinearLayout
    private lateinit var starStatusText: TextView

    // Scroll Layer Layout Components
    private lateinit var shopScrollView: NestedScrollView
    private lateinit var scrollDivider: View

    // Persistent Bottom Button References
    private lateinit var bottomActionContainer: FrameLayout
    private lateinit var confirmCoinAmount: TextView
    private lateinit var startEnabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtnContainer: FrameLayout
    private lateinit var subscribeDisabledBtnContainer: FrameLayout
    private lateinit var continueEnabledBtn: LinearLayout

    // Initial Fetching State Layer Overlay
    private lateinit var loadingOverlayContainer: FrameLayout

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var userDataListener: ListenerRegistration? = null
    private var countDownTimer: CountDownTimer? = null

    private val REGEN_TIME_MILLIS = 1800000L // 30 minutes
    private val MAX_STARS = 15
    private val INFINITY_COST = 1000L
    private val REFILL_COST = 15L

    private var currentCoins: Long = 0
    private var currentStars: Int = 0
    private var isInfinityPlan: Boolean = false

    // Control initial selection state (false means nothing is highlighted yet)
    private var isAnyOptionSelected: Boolean = false
    private var isInfinitySelected: Boolean = false

    // Monitors the active listener stream lifecycle setup
    private var isFirstLoad: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_stars)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Base Views
        backBtn = findViewById(R.id.back_btn)
        coinCountText = findViewById(R.id.coinCountText)
        centerStarGraphic = findViewById(R.id.center_star_graphic)
        attemptsLabel = findViewById(R.id.attempts_label)

        itemInfinity = findViewById(R.id.item_infinity)
        infinityPriceContainer = findViewById(R.id.infinity_price_container)
        infinityStatusText = findViewById(R.id.infinity_status_text)

        itemStar = findViewById(R.id.item_star)
        starPriceContainer = findViewById(R.id.star_price_container)
        starStatusText = findViewById(R.id.star_status_text)

        // Initialize Scroll Layers
        shopScrollView = findViewById(R.id.shopScrollView)
        scrollDivider = findViewById(R.id.scrollDivider)

        // Initialize Persistent Bottom Action Button UI elements
        bottomActionContainer = findViewById(R.id.bottom_action_container)
        confirmCoinAmount = findViewById(R.id.confirm_coin_amount)
        startEnabledBtnContainer = findViewById(R.id.start_enabled_btn_container)
        startDisabledBtnContainer = findViewById(R.id.start_disabled_btn_container)
        subscribeDisabledBtnContainer = findViewById(R.id.subscribe_disabled_btn_container)
        continueEnabledBtn = findViewById(R.id.continue_enabled_btn)

        // Connect Fullscreen Network Fetch Mask
        loadingOverlayContainer = findViewById(R.id.loadingOverlayContainer)

        backBtn.setOnClickListener {
            finish()
        }

        setupSelectionBehaviors()
        listenToUserData()
    }

    private fun setupSelectionBehaviors() {
        // Toggle the visible top line divider when inner layouts scroll
        shopScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            scrollDivider.visibility = if (scrollY > 0) View.VISIBLE else View.INVISIBLE
        })

        // Tap item options to switch selected state backgrounds and swap button cost data indicators
        itemInfinity.setOnClickListener {
            selectOption(selectInfinity = true)
        }

        itemStar.setOnClickListener {
            if (currentStars >= MAX_STARS) {
                Toast.makeText(this, getString(R.string.stars_full_toast), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectOption(selectInfinity = false)
        }

        continueEnabledBtn.setOnClickListener {
            if (isAnyOptionSelected) {
                executePlanTransaction(isInfinitySelected)
            }
        }
    }

    private fun selectOption(selectInfinity: Boolean) {
        isAnyOptionSelected = true
        isInfinitySelected = selectInfinity

        if (selectInfinity) {
            itemInfinity.setBackgroundResource(R.drawable.custom_background_selected)
            itemStar.setBackgroundResource(R.drawable.custom_background)
            confirmCoinAmount.text = INFINITY_COST.toString()
        } else {
            itemStar.setBackgroundResource(R.drawable.custom_background_selected)
            itemInfinity.setBackgroundResource(R.drawable.custom_background)
            confirmCoinAmount.text = REFILL_COST.toString()
        }

        // Hide inactive/loading states and expose active buy flow layout
        subscribeDisabledBtnContainer.visibility = View.GONE
        startDisabledBtnContainer.visibility = View.GONE
        startEnabledBtnContainer.visibility = View.VISIBLE
    }

    private fun listenToUserData() {
        val user = auth.currentUser ?: return

        // Assure full overlay intercept blocks navigation visibility flags safely during synchronization
        if (isFirstLoad) {
            loadingOverlayContainer.visibility = View.VISIBLE
        }

        userDataListener = db.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    // Turn off loading state even on failure to avoid deadlocks
                    loadingOverlayContainer.visibility = View.GONE
                    return@addSnapshotListener
                }

                currentCoins = snapshot.getLong("coins") ?: 0L
                val starsLong = snapshot.getLong("stars") ?: 15L
                currentStars = starsLong.toInt()
                isInfinityPlan = snapshot.getBoolean("subscription") ?: false
                val lastStarUsed = snapshot.getTimestamp("lastStarUsedTime")

                coinCountText.text = currentCoins.toString()
                countDownTimer?.cancel()

                if (isInfinityPlan) {
                    centerStarGraphic.setImageResource(R.drawable.star_infinity)
                    attemptsLabel.text = getString(R.string.infinity)
                    attemptsLabel.setTextColor(ContextCompat.getColor(this, R.color.text_color))

                    infinityPriceContainer.visibility = View.GONE
                    infinityStatusText.visibility = View.VISIBLE
                    itemInfinity.isClickable = false
                    itemInfinity.setBackgroundResource(R.drawable.custom_background)

                    starPriceContainer.visibility = View.GONE
                    starStatusText.visibility = View.VISIBLE
                    starStatusText.text = getString(R.string.full_stars)
                    itemStar.isClickable = false
                    itemStar.setBackgroundResource(R.drawable.custom_background)

                    // Both items are unlocked/activated, completely hide buy actions
                    bottomActionContainer.visibility = View.GONE
                } else {
                    bottomActionContainer.visibility = View.VISIBLE

                    if (currentStars <= 0) {
                        centerStarGraphic.setImageResource(R.drawable.star_null)
                    } else {
                        centerStarGraphic.setImageResource(R.drawable.star)
                    }

                    if (currentStars >= MAX_STARS) {
                        attemptsLabel.text = "$currentStars / $MAX_STARS"
                        attemptsLabel.setTextColor(ContextCompat.getColor(this, R.color.text_color))
                        starPriceContainer.visibility = View.GONE
                        starStatusText.visibility = View.VISIBLE
                        starStatusText.text = getString(R.string.full_stars)
                        itemStar.isClickable = false
                        itemStar.setBackgroundResource(R.drawable.custom_background)

                        // Force clear selections if they had standard star selected, then select infinity
                        if (isAnyOptionSelected && !isInfinitySelected) {
                            selectOption(selectInfinity = true)
                        } else if (!isAnyOptionSelected) {
                            // Leave it unselected! Keep the "Select an Option" screen block standing
                            resetToInactiveState()
                        }
                    } else {
                        starStatusText.visibility = View.GONE
                        starPriceContainer.visibility = View.VISIBLE
                        itemStar.isClickable = true

                        // Check if the user already interacted with a selection previously
                        if (isAnyOptionSelected) {
                            selectOption(isInfinitySelected)
                        } else {
                            // Default state: Nothing highlighted yet, button is inactive
                            resetToInactiveState()
                        }

                        if (lastStarUsed != null) {
                            startRegenTimer(lastStarUsed, currentStars)
                        } else {
                            attemptsLabel.text = "$currentStars / $MAX_STARS"
                            attemptsLabel.setTextColor(ContextCompat.getColor(this, R.color.text_color))
                        }
                    }

                    infinityStatusText.visibility = View.GONE
                    infinityPriceContainer.visibility = View.VISIBLE
                    itemInfinity.isClickable = true
                }

                // Drop screen shielding immediately upon successful data acquisition
                if (isFirstLoad) {
                    isFirstLoad = false
                    loadingOverlayContainer.visibility = View.GONE
                }
            }
    }

    private fun resetToInactiveState() {
        itemInfinity.setBackgroundResource(R.drawable.custom_background)
        itemStar.setBackgroundResource(R.drawable.custom_background)

        startEnabledBtnContainer.visibility = View.GONE
        startDisabledBtnContainer.visibility = View.GONE
        subscribeDisabledBtnContainer.visibility = View.VISIBLE
    }

    private fun clearSelectionState() {
        isAnyOptionSelected = false
        isInfinitySelected = false
        resetToInactiveState()
    }

    private fun startRegenTimer(lastUsed: Timestamp, starsOnRecord: Int) {
        val currentTime = System.currentTimeMillis()
        val lastUsedMillis = lastUsed.toDate().time
        val elapsedTime = currentTime - lastUsedMillis

        val starsToRecover = (elapsedTime / REGEN_TIME_MILLIS).toInt()
        if (starsToRecover > 0) {
            applyRecovery(starsOnRecord, lastUsedMillis, starsToRecover)
            return
        }

        val timeLeft = REGEN_TIME_MILLIS - elapsedTime

        countDownTimer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isInfinityPlan) return

                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                // 1. Fetch localized string label elements dynamically (spacing is guaranteed by the XML quotes)
                val staticLabel = getString(R.string.next_star_in)
                val timeString = getString(R.string.timer_format, minutes, seconds)
                val spannable = android.text.SpannableStringBuilder(staticLabel + timeString)

                // 2. Safely color the prefix label with standard color
                val defaultTextColor = ContextCompat.getColor(this@StarsActivity, R.color.text_color)
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(defaultTextColor),
                    0,
                    staticLabel.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                // 3. Highlight moving clock digits (including localized time metrics) in vivid pink
                val pinkColor = android.graphics.Color.parseColor("#FA236E")
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(pinkColor),
                    staticLabel.length,
                    spannable.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                attemptsLabel.text = spannable
            }

            override fun onFinish() {
                applyRecovery(starsOnRecord, lastUsedMillis, 1)
            }
        }.start()
    }

    private fun applyRecovery(starsOnRecord: Int, lastUsedMillis: Long, amount: Int) {
        val user = auth.currentUser ?: return
        val nextStars = (starsOnRecord + amount).coerceAtMost(MAX_STARS)
        val updates = mutableMapOf<String, Any>("stars" to nextStars)

        if (nextStars < MAX_STARS) {
            val newTimeMillis = lastUsedMillis + (amount * REGEN_TIME_MILLIS)
            updates["lastStarUsedTime"] = Timestamp(java.util.Date(newTimeMillis))
        } else {
            updates["lastStarUsedTime"] = com.google.firebase.firestore.FieldValue.delete()
        }

        db.collection("users").document(user.uid).update(updates)
    }

    private fun executePlanTransaction(isInfinityPurchase: Boolean) {
        val cost = if (isInfinityPurchase) INFINITY_COST else REFILL_COST
        if (currentCoins < cost) {
            Toast.makeText(this, getString(R.string.not_enough_coins), Toast.LENGTH_SHORT).show()
            clearSelectionState()
            return
        }

        val user = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(user.uid)

        startEnabledBtnContainer.visibility = View.GONE
        subscribeDisabledBtnContainer.visibility = View.GONE
        startDisabledBtnContainer.visibility = View.VISIBLE

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val freshCoins = snapshot.getLong("coins") ?: 0L
            val freshStars = (snapshot.getLong("stars") ?: 15L).toInt()

            if (isInfinityPurchase) {
                if (freshCoins >= INFINITY_COST) {
                    transaction.update(userDocRef, "coins", freshCoins - INFINITY_COST)
                    transaction.update(userDocRef, "subscription", true)
                } else {
                    throw Exception(getString(R.string.not_enough_coins))
                }
            } else {
                if (freshCoins >= REFILL_COST && freshStars < MAX_STARS) {
                    transaction.update(userDocRef, "coins", freshCoins - REFILL_COST)
                    transaction.update(userDocRef, "stars", MAX_STARS)
                    transaction.update(userDocRef, "lastStarUsedTime", com.google.firebase.firestore.FieldValue.delete())
                } else {
                    throw Exception(getString(R.string.transaction_failed))
                }
            }
        }.addOnSuccessListener {
            val successMessage = if (isInfinityPurchase) {
                getString(R.string.infinity_activated_toast)
            } else {
                getString(R.string.stars_refilled_toast)
            }
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
            clearSelectionState()
        }.addOnFailureListener { e ->
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
            val errorMsg = e.message ?: getString(R.string.transaction_failed)
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userDataListener?.remove()
        countDownTimer?.cancel()
    }
}