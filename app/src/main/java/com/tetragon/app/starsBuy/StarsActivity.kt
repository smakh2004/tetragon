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

    private lateinit var shopScrollView: NestedScrollView
    private lateinit var scrollDivider: View

    private lateinit var bottomActionContainer: FrameLayout
    private lateinit var confirmCoinAmount: TextView
    private lateinit var startEnabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtnContainer: FrameLayout
    private lateinit var subscribeDisabledBtnContainer: FrameLayout
    private lateinit var continueEnabledBtn: LinearLayout

    private lateinit var loadingOverlayContainer: FrameLayout

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var userDataListener: ListenerRegistration? = null
    private var countDownTimer: CountDownTimer? = null

    private val REGEN_TIME_MILLIS = 1800000L
    private val MAX_STARS = 15
    private val INFINITY_COST = 1000L
    private val REFILL_COST = 50L

    private var currentCoins: Long = 0
    private var currentStars: Int = 0
    private var isInfinityPlan: Boolean = false

    private var isAnyOptionSelected: Boolean = false
    private var isInfinitySelected: Boolean = false
    private var isSubscribedState: Boolean = false

    private var isFirstLoad: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stars)

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

        shopScrollView = findViewById(R.id.shopScrollView)
        scrollDivider = findViewById(R.id.scrollDivider)

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

        itemInfinity.setOnClickListener {
            if (isSubscribedState) return@setOnClickListener
            selectOption(selectInfinity = true)
        }

        itemStar.setOnClickListener {
            if (isSubscribedState) return@setOnClickListener
            if (currentStars >= MAX_STARS) {
                Toast.makeText(this, getString(R.string.stars_full_toast), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectOption(selectInfinity = false)
        }

        continueEnabledBtn.setOnClickListener {
            if (isAnyOptionSelected && !isSubscribedState) {
                executePlanTransaction(isInfinitySelected)
            }
        }
    }

    private fun selectOption(selectInfinity: Boolean) {
        if (isSubscribedState) return

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
                isSubscribedState = isInfinityPlan

                val lastStarUsed = snapshot.getTimestamp("lastStarUsedTime")

                coinCountText.text = currentCoins.toString()
                countDownTimer?.cancel()

                if (isSubscribedState) {
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
                    isAnyOptionSelected = false
                    isInfinitySelected = false
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
        subscribeDisabledBtnContainer.visibility = if (isSubscribedState) View.GONE else View.VISIBLE
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
                if (isSubscribedState) return
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

    // 🟢 ИСПРАВЛЕНО: раньше INFINITY и STAR кейсы отправляли по 3-4 отдельных
    // transaction.update(ref, "field", value) вызова. Теперь каждый кейс — ОДНО
    // объединённое обновление (map), одна атомарная запись.
    private fun executePlanTransaction(isInfinityPurchase: Boolean) {
        if (isSubscribedState) return

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
                    val calendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, 1) }
                    transaction.update(userDocRef, mapOf(
                        "coins" to freshCoins - INFINITY_COST,
                        "subscription" to true,
                        "subscriptionUntil" to com.google.firebase.Timestamp(calendar.time),
                        "planType" to "monthly"
                    ))
                } else throw Exception(getString(R.string.transaction_failed))
            } else {
                if (freshCoins >= REFILL_COST && freshStars < MAX_STARS) {
                    transaction.update(userDocRef, mapOf(
                        "coins" to freshCoins - REFILL_COST,
                        "stars" to MAX_STARS,
                        "lastStarUsedTime" to com.google.firebase.firestore.FieldValue.delete()
                    ))
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