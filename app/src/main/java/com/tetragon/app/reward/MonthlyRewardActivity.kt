package com.tetragon.app.reward

import android.animation.ValueAnimator
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive

class MonthlyRewardActivity : BaseActivity() {

    private lateinit var tvCoinCount: TextView
    private lateinit var btnClaim: Button
    private lateinit var enabledContainer: FrameLayout
    private lateinit var disabledContainer: FrameLayout

    private lateinit var riveAnimationView: RiveAnimationView
    private lateinit var centerContentWrapper: View
    private lateinit var rewardContainer: FrameLayout
    private lateinit var continueBtnContainer: FrameLayout

    private var soundPool: SoundPool? = null
    private var rewardSoundId: Int = 0

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val rewardOptions = listOf(100, 200, 300)
    private var selectedReward = 0
    private var isAnimationStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Rive.init(this)
        } catch (e: Exception) {}

        setContentView(R.layout.activity_monthly_reward)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        rewardSoundId = soundPool?.load(this, R.raw.monthly_reward, 1) ?: 0

        tvCoinCount = findViewById(R.id.tv_coin_count)
        btnClaim = findViewById(R.id.continue_enabled_btn)
        enabledContainer = findViewById(R.id.continue_enabled_btn_container)
        disabledContainer = findViewById(R.id.disabledContainer)

        riveAnimationView = findViewById(R.id.rive_mr_square)
        centerContentWrapper = findViewById(R.id.center_content_wrapper)
        rewardContainer = findViewById(R.id.reward_container)
        continueBtnContainer = findViewById(R.id.continue_btn_container)

        if (savedInstanceState != null) {
            selectedReward = savedInstanceState.getInt("SAVED_REWARD", 0)
            isAnimationStarted = savedInstanceState.getBoolean("ANIMATION_DONE", false)

            if (isAnimationStarted) {
                tvCoinCount.text = selectedReward.toString()

                centerContentWrapper.visibility = View.VISIBLE
                centerContentWrapper.alpha = 1f
                rewardContainer.visibility = View.VISIBLE
                rewardContainer.alpha = 1f
                continueBtnContainer.visibility = View.VISIBLE
                continueBtnContainer.alpha = 1f

                riveAnimationView.translationY = 20f * resources.displayMetrics.density
                riveAnimationView.play()
            }
        }

        if (selectedReward == 0) {
            selectedReward = rewardOptions.random()

            centerContentWrapper.visibility = View.INVISIBLE
            rewardContainer.visibility = View.INVISIBLE
            continueBtnContainer.visibility = View.INVISIBLE

            riveAnimationView.post {
                if (!isDestroyed && !isFinishing) {
                    val screenHeight = resources.displayMetrics.heightPixels.toFloat()
                    val riveCenterY = riveAnimationView.top + (riveAnimationView.height / 2f)
                    val targetCenterTranslation = (screenHeight / 2f) - riveCenterY

                    riveAnimationView.translationY = targetCenterTranslation

                    riveAnimationView.postDelayed({
                        if (!isDestroyed && !isFinishing) {
                            runEntranceAndRevealSequence()
                        }
                    }, 900)
                }
            }
        }

        btnClaim.setOnClickListener {
            handleClaimProcess()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("SAVED_REWARD", selectedReward)
        outState.putBoolean("ANIMATION_DONE", isAnimationStarted)
    }

    private fun runEntranceAndRevealSequence() {
        if (!isDestroyed && !isFinishing) {
            soundPool?.play(rewardSoundId, 1f, 1f, 1, 0, 1f)
        }

        riveAnimationView.play()
        startCoinAnimation(selectedReward)

        val restingTranslationYPx = 20f * resources.displayMetrics.density
        riveAnimationView.animate()
            .translationY(restingTranslationYPx)
            .setDuration(300)
            .start()

        centerContentWrapper.alpha = 0f
        centerContentWrapper.visibility = View.VISIBLE

        rewardContainer.alpha = 0f
        rewardContainer.visibility = View.VISIBLE

        continueBtnContainer.alpha = 0f
        continueBtnContainer.visibility = View.VISIBLE

        centerContentWrapper.animate()
            .alpha(1f)
            .setStartDelay(1000)
            .setDuration(400)
            .start()

        rewardContainer.animate()
            .alpha(1f)
            .setStartDelay(1000)
            .setDuration(400)
            .start()

        continueBtnContainer.animate()
            .alpha(1f)
            .setStartDelay(1000)
            .setDuration(400)
            .start()
    }

    private fun startCoinAnimation(targetValue: Int) {
        isAnimationStarted = true
        val animator = ValueAnimator.ofInt(0, targetValue)
        animator.duration = 1500
        animator.addUpdateListener { animation ->
            tvCoinCount.text = animation.animatedValue.toString()
        }
        animator.start()
    }

    private fun handleClaimProcess() {
        val userId = auth.currentUser?.uid ?: return

        enabledContainer.visibility = View.GONE
        disabledContainer.visibility = View.VISIBLE

        val userRef = db.collection("users").document(userId)
        userRef.update("coins", FieldValue.increment(selectedReward.toLong()))
            .addOnSuccessListener {
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                enabledContainer.visibility = View.VISIBLE
                disabledContainer.visibility = View.GONE
                Toast.makeText(this, getString(R.string.error_saving_progress), Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
        soundPool = null
    }
}