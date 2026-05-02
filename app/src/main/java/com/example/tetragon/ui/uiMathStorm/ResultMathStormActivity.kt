package com.example.tetragon.ui.uiMathStorm

import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.R
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.soundUtils.SoundManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ResultMathStormActivity : BaseActivity() {

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
        setContentView(R.layout.activity_result_math_storm)

        // Light navigation bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

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

        newRecordSoundId = soundPool.load(this, R.raw.new_record, 1)
        notRecordSoundId = soundPool.load(this, R.raw.not_record, 1)

        // Ensure sound is loaded before playing
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                isSoundLoaded = true
            }
        }

        // Get score from Intent
        currentScore = intent.getIntExtra("score", 0)
        resultText.text = currentScore.toString()

        // Check and update high score
        checkAndUpdateHighScore()

        continueButton.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun checkAndUpdateHighScore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()

        val gameDocRef = firestore.collection("users").document(uid)
            .collection("games").document("MathStorm")
        val userDocRef = firestore.collection("users").document(uid)

        firestore.runTransaction { transaction ->

            val gameSnapshot = transaction.get(gameDocRef)
            val userSnapshot = transaction.get(userDocRef)

            val savedHighScore = gameSnapshot.getLong("highScore") ?: 0L
            val currentTotalXp = userSnapshot.getLong("xp") ?: 0L
            val currentMonthlyXp = userSnapshot.getLong("monthlyXP") ?: 0L

            // Update XP
            if (currentScore > 0) {
                transaction.update(userDocRef, "xp", currentTotalXp + 10)
                transaction.update(userDocRef, "monthlyXP", currentMonthlyXp + 10)
            }

            // Determine if this is a new record
            if (currentScore > savedHighScore) {
                transaction.set(gameDocRef, hashMapOf("highScore" to currentScore))
                true // <-- returns true for new record
            } else {
                false // <-- returns false if not a new record
            }
        }.addOnSuccessListener { isNewRecord ->
            if (isNewRecord) {
                resultTextShower.text = getString(R.string.new_record_caps)
                playNewRecordSound()
            } else {
                resultTextShower.text = getTieredMessage()
                playNotRecordSound()
            }
        }.addOnFailureListener {
            resultTextShower.text = getString(R.string.error_saving_progress)
        }
    }

    private fun getTieredMessage(): String {
        return when {
            currentScore < 5 -> getString(R.string.tier_starting_out)
            currentScore < 15 -> getString(R.string.tier_keep_going)
            currentScore < 25 -> getString(R.string.tier_nice_work)
            currentScore < 35 -> getString(R.string.tier_getting_better)
            currentScore < 45 -> getString(R.string.tier_strong_effort)
            currentScore < 55 -> getString(R.string.tier_good_job)
            currentScore < 65 -> getString(R.string.tier_math_master)
            else -> getString(R.string.tier_you_rock)
        }
    }

    // --- SOUND METHODS ---
    private fun playNewRecordSound() {
        if (!SoundManager.isSoundEnabled(this) || !isSoundLoaded) return
        soundPool.play(newRecordSoundId, 1f, 1f, 1, 0, 1f)
    }

    private fun playNotRecordSound() {
        if (!SoundManager.isSoundEnabled(this) || !isSoundLoaded) return
        soundPool.play(notRecordSoundId, 1f, 1f, 1, 0, 1f)
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}