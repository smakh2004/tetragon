package com.tetragon.app.ui.uiCashStorm

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.soundUtils.SoundManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultCashStormActivity : BaseActivity() {

    private lateinit var resultText: TextView
    private lateinit var continueButton: Button
    private lateinit var resultTextShower: TextView
    private lateinit var stormMrSquare: RiveAnimationView

    // --- HOISTED LOADING SYSTEM ---
    private lateinit var loadingOverlayContainer: FrameLayout

    private var currentScore: Int = 0

    // SoundPool variables
    private lateinit var soundPool: SoundPool
    private var newRecordSoundId: Int = 0
    private var notRecordSoundId: Int = 0
    private var isSoundLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Rive audio engine
        Rive.init(this)
        setContentView(R.layout.activity_result_cash_storm)

        // Initialize Views
        resultText = findViewById(R.id.resultText)
        continueButton = findViewById(R.id.continueButton)
        resultTextShower = findViewById(R.id.resultTextShower)
        loadingOverlayContainer = findViewById(R.id.loadingOverlayContainer)
        stormMrSquare = findViewById(R.id.storm_mr_square)

        // Initialize SoundPool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load Record Sounds
        newRecordSoundId = soundPool.load(this, R.raw.new_record, 1)
        notRecordSoundId = soundPool.load(this, R.raw.not_record, 1)

        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                isSoundLoaded = true
            }
        }

        // Get score from Intent (passed from UiCashStormActivity)
        currentScore = intent.getIntExtra("score", 0)
        resultText.text = currentScore.toString()

        // Enforce the full-screen loader immediately before transaction tasks fire
        loadingOverlayContainer.visibility = View.VISIBLE
        checkAndUpdateCashStormProgress()

        continueButton.setOnClickListener {
            finish()
            // Ensure consistent transition animations
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun checkAndUpdateCashStormProgress() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            loadingOverlayContainer.visibility = View.GONE
            return
        }
        val firestore = FirebaseFirestore.getInstance()
        // Define the date key to match your graph's format
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val gameDocRef = firestore.collection("users").document(uid)
            .collection("games").document("CashStorm")
        val userDocRef = firestore.collection("users").document(uid)

        firestore.runTransaction { transaction ->
            val gameSnapshot = transaction.get(gameDocRef)
            val userSnapshot = transaction.get(userDocRef)

            // Fetch existing data
            val savedHighScore = gameSnapshot.getLong("highScore") ?: 0L
            val currentTotalXp = userSnapshot.getLong("xp") ?: 0L
            val currentMonthlyXp = userSnapshot.getLong("monthlyXP") ?: 0L

            // Fetch current daily map or create a new one
            val dailyXpMap = userSnapshot.get("dailyXPGains") as? MutableMap<String, Long> ?: mutableMapOf()

            // Calculate gains (10 XP for finishing)
            val xpGain = 10L
            val currentTodayXp = dailyXpMap[todayKey] ?: 0L

            // Update the Map
            dailyXpMap[todayKey] = currentTodayXp + xpGain

            // Perform updates atomically
            transaction.update(userDocRef, mapOf(
                "xp" to (currentTotalXp + xpGain),
                "monthlyXP" to (currentMonthlyXp + xpGain),
                "dailyXPGains" to dailyXpMap
            ))

            // Check for New Record
            val isNewRecord = currentScore.toLong() > savedHighScore
            if (isNewRecord) {
                transaction.set(gameDocRef, hashMapOf("highScore" to currentScore.toLong()))
            }

            isNewRecord // Return result to the onSuccess listener
        }.addOnSuccessListener { isNewRecord ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener

            if (isNewRecord) {
                resultTextShower.text = getString(R.string.new_profit_record)
                playNewRecordSound()
            } else {
                resultTextShower.text = getCashStormTierMessage()
                playNotRecordSound()
            }

            loadingOverlayContainer.visibility = View.GONE
            stormMrSquare.play()
        }.addOnFailureListener {
            if (isFinishing || isDestroyed) return@addOnFailureListener

            resultTextShower.text = getString(R.string.sync_error)
            loadingOverlayContainer.visibility = View.GONE
            stormMrSquare.play()
        }
    }

    /**
     * Themed messages based on performance
     */
    private fun getCashStormTierMessage(): String {
        return when {
            currentScore < 5 -> getString(R.string.tier_starting_business)
            currentScore < 15 -> getString(R.string.tier_smart_spender)
            currentScore < 25 -> getString(R.string.tier_wealth_builder)
            currentScore < 35 -> getString(R.string.tier_profit_master)
            currentScore < 45 -> getString(R.string.tier_cash_tycoon)
            currentScore < 55 -> getString(R.string.tier_financial_genius)
            else -> getString(R.string.tier_market_legend)
        }
    }

    private fun playNewRecordSound() {
        if (SoundManager.isSoundEnabled(this) && isSoundLoaded) {
            soundPool.play(newRecordSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    private fun playNotRecordSound() {
        if (SoundManager.isSoundEnabled(this) && isSoundLoaded) {
            soundPool.play(notRecordSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}