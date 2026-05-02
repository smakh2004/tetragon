package com.example.tetragon.reward

import android.animation.ValueAnimator
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.MainActivity
import com.example.tetragon.R
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class BagTapActivity : BaseActivity() {

    private lateinit var riveBag: RiveAnimationView
    private lateinit var disabledContainer: FrameLayout
    private lateinit var enabledContainer: FrameLayout
    private lateinit var coinRewardContainer: LinearLayout
    private lateinit var coinRewardText: TextView
    private lateinit var currentCoinCountText: TextView
    private lateinit var continueBtn: Button

    private var openSound: MediaPlayer? = null
    private var isRewardProcessed = false
    private var currentUserCoins: Long = 0
    private var droppedCoins: Int = 0 // Store this globally to save/restore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try { Rive.init(this) } catch (e: Exception) { Log.e("BagTap", "Rive Init Error") }

        setContentView(R.layout.activity_bag_tap)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        initViews()

        // RESTORE LOGIC: Check if activity was recreated
        if (savedInstanceState != null) {
            isRewardProcessed = savedInstanceState.getBoolean("IS_PROCESSED", false)
            droppedCoins = savedInstanceState.getInt("DROPPED_COINS", 0)
            currentUserCoins = savedInstanceState.getLong("CURRENT_COINS", 0)

            if (isRewardProcessed) {
                // If reward was already claimed, keep the button enabled and show final count
                disabledContainer.visibility = View.INVISIBLE
                enabledContainer.visibility = View.VISIBLE
                currentCoinCountText.text = currentUserCoins.toString()
            }
        }

        continueBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        // Only fetch from DB if we don't have restored coins
        if (currentUserCoins == 0L) {
            fetchInitialCoins()
        }

        setupRiveListener()
    }

    // SAVE LOGIC: Keep the state safe during rotation/resize
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("IS_PROCESSED", isRewardProcessed)
        outState.putInt("DROPPED_COINS", droppedCoins)
        outState.putLong("CURRENT_COINS", currentUserCoins)
    }

    private fun initViews() {
        riveBag = findViewById(R.id.bag)
        disabledContainer = findViewById(R.id.continue_disabled_btn_container)
        enabledContainer = findViewById(R.id.continue_enabled_btn_container)
        continueBtn = findViewById(R.id.continue_enabled_btn)
        coinRewardContainer = findViewById(R.id.coinRewardContainer)
        coinRewardText = findViewById(R.id.coinRewardText)
        currentCoinCountText = findViewById(R.id.currentCoinCountText)

        openSound = MediaPlayer.create(this, R.raw.bag_opened)
    }

    private fun fetchInitialCoins() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                currentUserCoins = doc.getLong("coins") ?: 0
                currentCoinCountText.text = currentUserCoins.toString()
            }
    }

    private fun setupRiveListener() {
        riveBag.registerListener(object : RiveFileController.Listener {
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                // Only process if we haven't already processed a reward in this session OR restored session
                if (stateName == "Open" && !isRewardProcessed) {
                    isRewardProcessed = true
                    handleReward()
                }
            }
            override fun notifyPlay(animation: PlayableInstance) {}
            override fun notifyLoop(animation: PlayableInstance) {}
            override fun notifyPause(animation: PlayableInstance) {}
            override fun notifyStop(animation: PlayableInstance) {}
        })
    }

    private fun handleReward() {
        // Pick the random amount once
        droppedCoins = Random.nextInt(10, 21)

        runOnUiThread {
            coinRewardText.text = "+$droppedCoins"

            coinRewardContainer.animate().cancel()
            coinRewardContainer.visibility = View.VISIBLE
            coinRewardContainer.alpha = 0f
            coinRewardContainer.scaleX = 0.6f
            coinRewardContainer.scaleY = 0.6f
            coinRewardContainer.translationY = 60f

            coinRewardContainer.animate()
                .alpha(1f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .translationY(30f)
                .setDuration(400)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .withEndAction {
                    coinRewardContainer.animate()
                        .alpha(0f)
                        .translationY(-30f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setStartDelay(1000)
                        .setDuration(700)
                        .withEndAction {
                            coinRewardContainer.visibility = View.GONE
                        }
                        .start()
                }
                .start()

            animateCoinCount(currentUserCoins, currentUserCoins + droppedCoins)
            openSound?.start()
            startButtonTimer()
        }
        updateUserCoinsSafely(droppedCoins.toLong())
    }

    private fun animateCoinCount(start: Long, end: Long) {
        val animator = ValueAnimator.ofInt(start.toInt(), end.toInt())
        animator.duration = 1000
        animator.addUpdateListener { animation ->
            currentCoinCountText.text = animation.animatedValue.toString()
        }
        animator.start()
        currentUserCoins = end
    }

    private fun updateUserCoinsSafely(amount: Long) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("coins", FieldValue.increment(amount))
        } catch (e: Exception) { Log.e("BagTap", "Firestore Error: ${e.message}") }
    }

    private fun startButtonTimer() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                disabledContainer.visibility = View.INVISIBLE
                enabledContainer.visibility = View.VISIBLE
            }
        }, 2500)
    }

    override fun onDestroy() {
        super.onDestroy()
        openSound?.release()
    }
}