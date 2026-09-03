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
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import kotlin.random.Random

class BagTapActivity : BaseActivity() {

    private lateinit var riveBag: RiveAnimationView
    private lateinit var coinAnimationView: RiveAnimationView
    private lateinit var disabledContainer: FrameLayout
    private lateinit var enabledContainer: FrameLayout
    private lateinit var rewardStatusText: TextView
    private lateinit var continueBtn: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var openSound: MediaPlayer? = null
    private var isRewardProcessed = false
    private var droppedCoins: Int = 0

    private var coinVmi: ViewModelInstance? = null

    // ---- coin counter animation state ----
    private var coinStartValue = 0L
    private var coinTargetValue = 0L
    private var coinsLoaded = false
    private var coinRiveReady = false
    private var coinCountUpStarted = false
    private var coinCountUpRunnable: Runnable? = null

    companion object {
        private const val TAG = "BagTap"
        private const val MAX_RIVE_ATTEMPTS = 25
        private const val RIVE_RETRY_DELAY_MS = 50L

        private const val COIN_VIEW_MODEL_NAME = "ViewModel1"
        private const val COIN_PROPERTY = "coin"

        private const val COIN_COUNT_UP_DELAY_MS = 400L
        private const val BUTTON_DELAY_MS = 2500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Rive.init(this)
        } catch (e: Exception) {
            Log.e(TAG, "Rive Init Error: ${e.message}")
        }

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
        prepareCoinRive()
        loadCurrentCoins()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("IS_PROCESSED", isRewardProcessed)
        outState.putInt("DROPPED_COINS", droppedCoins)
    }

    private fun initViews() {
        riveBag = findViewById(R.id.bag)
        coinAnimationView = findViewById(R.id.coinAnimationView)
        disabledContainer = findViewById(R.id.continue_disabled_btn_container)
        enabledContainer = findViewById(R.id.continue_enabled_btn_container)
        continueBtn = findViewById(R.id.continue_enabled_btn)
        rewardStatusText = findViewById(R.id.rewardStatusText)

        openSound = MediaPlayer.create(this, R.raw.bag_opened)
    }

    private fun setupRiveListener() {
        riveBag.registerListener(object : RiveFileController.Listener {
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                if (stateName.equals("Open", ignoreCase = true) && !isRewardProcessed) {
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

    // ==================== COIN COUNTER ====================

    private fun loadCurrentCoins() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val coins = snapshot.getLong("coins") ?: 0L
                coinsLoaded = true

                if (isRewardProcessed) {
                    coinStartValue = coins - droppedCoins
                    coinTargetValue = coins
                    triggerCoinAnimationSequence()
                } else {
                    coinStartValue = coins
                    coinTargetValue = coins
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Could not read coins: ${e.message}")
            }
    }

    private fun prepareCoinRive(attempt: Int = 0) {
        coinAnimationView.post {
            if (isFinishing || isDestroyed) return@post

            try {
                val controller = coinAnimationView.controller
                val file = controller.file
                val artboard = controller.activeArtboard

                if (file == null || artboard == null) {
                    if (attempt < MAX_RIVE_ATTEMPTS) {
                        coinAnimationView.postDelayed(
                            { prepareCoinRive(attempt + 1) },
                            RIVE_RETRY_DELAY_MS
                        )
                    } else {
                        Log.w(TAG, "Coin rive file never loaded")
                    }
                    return@post
                }

                val vm = file.getViewModelByName(COIN_VIEW_MODEL_NAME) ?: run {
                    Log.w(TAG, "ViewModel '$COIN_VIEW_MODEL_NAME' not found in coin_animation.riv")
                    return@post
                }

                val vmi = vm.createDefaultInstance()
                artboard.viewModelInstance = vmi

                controller.stateMachines.forEach { sm ->
                    sm.viewModelInstance = vmi
                }

                coinVmi = vmi
                coinRiveReady = true

                if (isRewardProcessed) {
                    triggerCoinAnimationSequence()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error preparing coin rive: ${e.message}", e)
            }
        }
    }

    private fun pushCoinValue(value: Long) {
        try {
            val vmi = coinVmi ?: return
            val numberProp = vmi.getNumberProperty(COIN_PROPERTY)
            if (numberProp != null) {
                numberProp.value = value.toFloat()
                coinAnimationView.invalidate()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing coin value: ${e.message}", e)
        }
    }

    private fun triggerCoinAnimationSequence() {
        if (!coinRiveReady || !coinsLoaded || coinCountUpStarted) return
        coinCountUpStarted = true

        coinAnimationView.visibility = View.VISIBLE
        pushCoinValue(coinStartValue)
        coinAnimationView.play()

        val runnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            pushCoinValue(coinTargetValue)
        }
        coinCountUpRunnable = runnable
        coinAnimationView.postDelayed(runnable, COIN_COUNT_UP_DELAY_MS)
    }

    // ==================== REWARD ====================

    private fun handleReward() {
        droppedCoins = Random.nextInt(10, 21)

        runOnUiThread {
            displayEarnedCoins(droppedCoins)
            openSound?.start()
            startButtonTimer()

            coinTargetValue = coinStartValue + droppedCoins
            triggerCoinAnimationSequence()
        }

        updateUserCoinsSafely(droppedCoins.toLong())
    }

    private fun displayEarnedCoins(coins: Int) {
        val localizedFormat = getString(R.string.you_gained_coins, coins)
        rewardStatusText.text = Html.fromHtml(localizedFormat, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun updateUserCoinsSafely(amount: Long) {
        val uid = auth.currentUser?.uid ?: return
        val userDoc = db.collection("users").document(uid)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDoc)
            val current = snapshot.getLong("coins") ?: 0L
            val updated = current + amount
            transaction.update(userDoc, "coins", updated)
            updated
        }.addOnSuccessListener { newCoins ->
            if (isFinishing || isDestroyed || newCoins == null) return@addOnSuccessListener

            coinTargetValue = newCoins
            pushCoinValue(coinTargetValue)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Coin transaction failed: ${e.message}", e)
        }
    }

    private fun startButtonTimer() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                disabledContainer.visibility = View.INVISIBLE
                enabledContainer.visibility = View.VISIBLE
            }
        }, BUTTON_DELAY_MS)
    }

    override fun onDestroy() {
        coinCountUpRunnable?.let { coinAnimationView.removeCallbacks(it) }
        coinCountUpRunnable = null
        coinVmi = null
        openSound?.release()
        openSound = null
        super.onDestroy()
    }
}