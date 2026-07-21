package com.tetragon.app.reward

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class BagTapActivity : BaseActivity() {

    private lateinit var riveBag: RiveAnimationView
    private lateinit var disabledContainer: FrameLayout
    private lateinit var enabledContainer: FrameLayout
    private lateinit var rewardStatusText: TextView
    private lateinit var continueBtn: Button

    private var openSound: MediaPlayer? = null
    private var isRewardProcessed = false
    private var droppedCoins: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try { Rive.init(this) } catch (e: Exception) { Log.e("BagTap", "Rive Init Error") }

        setContentView(R.layout.activity_bag_tap)

        initViews()

        if (savedInstanceState != null) {
            isRewardProcessed = savedInstanceState.getBoolean("IS_PROCESSED", false)
            droppedCoins = savedInstanceState.getInt("DROPPED_COINS", 0)

            if (isRewardProcessed) {
                disabledContainer.visibility = View.INVISIBLE
                enabledContainer.visibility = View.VISIBLE
                displayEarnedCoins(droppedCoins)
            }
        }

        continueBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        setupRiveListener()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("IS_PROCESSED", isRewardProcessed)
        outState.putInt("DROPPED_COINS", droppedCoins)
    }

    private fun initViews() {
        riveBag = findViewById(R.id.bag)
        disabledContainer = findViewById(R.id.continue_disabled_btn_container)
        enabledContainer = findViewById(R.id.continue_enabled_btn_container)
        continueBtn = findViewById(R.id.continue_enabled_btn)
        rewardStatusText = findViewById(R.id.rewardStatusText)

        openSound = MediaPlayer.create(this, R.raw.bag_opened)
    }

    private fun setupRiveListener() {
        riveBag.registerListener(object : RiveFileController.Listener {
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
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
        droppedCoins = Random.nextInt(10, 21)

        runOnUiThread {
            displayEarnedCoins(droppedCoins)
            openSound?.start()
            startButtonTimer()
        }
        updateUserCoinsSafely(droppedCoins.toLong())
    }

    private fun displayEarnedCoins(coins: Int) {
        // Pulls the translated format string and safely drops the dynamic coin integer into %1$d
        val localizedFormat = getString(R.string.you_gained_coins, coins)

        rewardStatusText.text = Html.fromHtml(localizedFormat, Html.FROM_HTML_MODE_LEGACY)
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