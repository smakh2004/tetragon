package com.tetragon.app.reward

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MonthlyRewardActivity : AppCompatActivity() {

    private lateinit var tvCoinCount: TextView
    private lateinit var btnClaim: Button
    private lateinit var enabledContainer: FrameLayout
    private lateinit var disabledContainer: FrameLayout

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val rewardOptions = listOf(100, 200, 300)
    private var selectedReward = 0
    private var isAnimationStarted = false // Flag to prevent re-animating on rotate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_monthly_reward)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvCoinCount = findViewById(R.id.tv_coin_count)
        btnClaim = findViewById(R.id.continue_enabled_btn)
        enabledContainer = findViewById(R.id.continue_enabled_btn_container)
        disabledContainer = findViewById(R.id.disabledContainer)

        // RESTORE LOGIC: Check if we have a saved reward from a previous state
        if (savedInstanceState != null) {
            selectedReward = savedInstanceState.getInt("SAVED_REWARD", 0)
            isAnimationStarted = savedInstanceState.getBoolean("ANIMATION_DONE", false)

            // If animation already happened, just set the text directly
            if (isAnimationStarted) {
                tvCoinCount.text = selectedReward.toString()
            }
        }

        // If selectedReward is still 0, it's the first time entering the page
        if (selectedReward == 0) {
            selectedReward = rewardOptions.random()
            startCoinAnimation(selectedReward)
        }

        btnClaim.setOnClickListener {
            handleClaimProcess()
        }
    }

    // SAVE LOGIC: Store the reward before the Activity is destroyed
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("SAVED_REWARD", selectedReward)
        outState.putBoolean("ANIMATION_DONE", isAnimationStarted)
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
                Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show()
            }
    }
}