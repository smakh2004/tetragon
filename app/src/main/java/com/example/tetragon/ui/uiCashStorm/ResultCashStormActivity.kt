package com.example.tetragon.ui.uiCashStorm

import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.R
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.soundUtils.SoundManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ResultCashStormActivity : BaseActivity() {

    private lateinit var resultText: TextView
    private lateinit var continueButton: Button
    private lateinit var resultTextShower: TextView

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

        // UI: Light navigation bar for a clean look
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        // Initialize Views
        resultText = findViewById(R.id.resultText)
        continueButton = findViewById(R.id.continueButton)
        resultTextShower = findViewById(R.id.resultTextShower)

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

        // Sync with Firebase Firestore
        checkAndUpdateCashStormProgress()

        continueButton.setOnClickListener {
            finish()
            // Ensure consistent transition animations
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun checkAndUpdateCashStormProgress() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()

        // Document specific to this game mode
        val gameDocRef = firestore.collection("users").document(uid)
            .collection("games").document("CashStorm")
        val userDocRef = firestore.collection("users").document(uid)

        firestore.runTransaction { transaction ->
            val gameSnapshot = transaction.get(gameDocRef)
            val userSnapshot = transaction.get(userDocRef)

            val savedHighScore = gameSnapshot.getLong("highScore") ?: 0L
            val currentTotalXp = userSnapshot.getLong("xp") ?: 0L
            val currentMonthlyXp = userSnapshot.getLong("monthlyXP") ?: 0L

            // Update XP (+10 for finishing a round)
            if (currentScore > 0) {
                transaction.update(userDocRef, "xp", currentTotalXp + 10)
                transaction.update(userDocRef, "monthlyXP", currentMonthlyXp + 10)
            }

            // Check for New Record
            if (currentScore.toLong() > savedHighScore) {
                transaction.set(gameDocRef, hashMapOf("highScore" to currentScore))
                true // Returns true for onSuccessListener
            } else {
                false
            }
        }.addOnSuccessListener { isNewRecord ->
            if (isNewRecord) {
                resultTextShower.text = getString(R.string.new_profit_record)
                playNewRecordSound()
            } else {
                resultTextShower.text = getCashStormTierMessage()
                playNotRecordSound()
            }
        }.addOnFailureListener {
            resultTextShower.text = getString(R.string.sync_error)
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