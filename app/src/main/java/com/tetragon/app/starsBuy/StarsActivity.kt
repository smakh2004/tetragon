package com.tetragon.app.starsBuy

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
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
    private val REFILL_COST = 50L

    private var currentCoins: Long = 0
    private var currentStars: Int = 0
    private var isInfinityPlan: Boolean = false

    // Control initial selection state
    private var isAnyOptionSelected: Boolean = false
    private var isInfinitySelected: Boolean = false

    private var isFirstLoad: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stars)

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

        loadingOverlayContainer = findViewById(R.id.loadingOverlayContainer)

        backBtn.setOnClickListener { finish() }

        setupSelectionBehaviors()
        listenToUserData()
    }

    private fun setupSelectionBehaviors() {
        shopScrollView.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            scrollDivider.visibility = if (scrollY > 0) View.VISIBLE else View.INVISIBLE
        })

        itemInfinity.setOnClickListener { selectOption(selectInfinity = true) }

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

        subscribeDisabledBtnContainer.visibility = View.GONE
        startDisabledBtnContainer.visibility = View.GONE
        startEnabledBtnContainer.visibility = View.VISIBLE
    }

    private fun listenToUserData() {
        val user = auth.currentUser ?: return

        if (isFirstLoad) loadingOverlayContainer.visibility = View.VISIBLE

        userDataListener = db.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
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
                    bottomActionContainer.visibility = View.GONE
                } else {
                    bottomActionContainer.visibility = View.VISIBLE
                    centerStarGraphic.setImageResource(if (currentStars <= 0) R.drawable.star_null else R.drawable.star)

                    if (currentStars >= MAX_STARS) {
                        attemptsLabel.text = "$currentStars / $MAX_STARS"
                        starPriceContainer.visibility = View.GONE
                        starStatusText.visibility = View.VISIBLE
                        starStatusText.text = getString(R.string.full_stars)
                        itemStar.isClickable = false
                        itemStar.setBackgroundResource(R.drawable.custom_background)
                        if (isAnyOptionSelected && !isInfinitySelected) selectOption(true)
                        else if (!isAnyOptionSelected) resetToInactiveState()
                    } else {
                        starStatusText.visibility = View.GONE
                        starPriceContainer.visibility = View.VISIBLE
                        itemStar.isClickable = true
                        if (isAnyOptionSelected) selectOption(isInfinitySelected)
                        else resetToInactiveState()

                        if (lastStarUsed != null) startRegenTimer(lastStarUsed, currentStars)
                        else {
                            attemptsLabel.text = "$currentStars / $MAX_STARS"
                            attemptsLabel.setTextColor(ContextCompat.getColor(this, R.color.text_color))
                        }
                    }
                    infinityStatusText.visibility = View.GONE
                    infinityPriceContainer.visibility = View.VISIBLE
                    itemInfinity.isClickable = true
                }

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

        countDownTimer = object : CountDownTimer(REGEN_TIME_MILLIS - elapsedTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isInfinityPlan) return
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                val staticLabel = getString(R.string.next_star_in)
                val timeString = getString(R.string.timer_format, minutes, seconds)
                val spannable = android.text.SpannableStringBuilder(staticLabel + timeString)
                spannable.setSpan(android.text.style.ForegroundColorSpan(ContextCompat.getColor(this@StarsActivity, R.color.text_color)), 0, staticLabel.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FA236E")), staticLabel.length, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                attemptsLabel.text = spannable
            }
            override fun onFinish() { applyRecovery(starsOnRecord, lastUsedMillis, 1) }
        }.start()
    }

    private fun applyRecovery(starsOnRecord: Int, lastUsedMillis: Long, amount: Int) {
        val user = auth.currentUser ?: return
        val nextStars = (starsOnRecord + amount).coerceAtMost(MAX_STARS)
        val updates = mutableMapOf<String, Any>("stars" to nextStars)
        if (nextStars < MAX_STARS) updates["lastStarUsedTime"] = Timestamp(java.util.Date(lastUsedMillis + (amount * REGEN_TIME_MILLIS)))
        else updates["lastStarUsedTime"] = com.google.firebase.firestore.FieldValue.delete()
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
                } else throw Exception(getString(R.string.not_enough_coins))
            } else {
                if (freshCoins >= REFILL_COST && freshStars < MAX_STARS) {
                    transaction.update(userDocRef, "coins", freshCoins - REFILL_COST)
                    transaction.update(userDocRef, "stars", MAX_STARS)
                    transaction.update(userDocRef, "lastStarUsedTime", com.google.firebase.firestore.FieldValue.delete())
                } else throw Exception(getString(R.string.transaction_failed))
            }
        }.addOnSuccessListener {
            Toast.makeText(this, getString(if (isInfinityPurchase) R.string.infinity_activated_toast else R.string.stars_refilled_toast), Toast.LENGTH_SHORT).show()
            clearSelectionState()
        }.addOnFailureListener { e ->
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
            Toast.makeText(this, e.message ?: getString(R.string.transaction_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userDataListener?.remove()
        countDownTimer?.cancel()
    }
}