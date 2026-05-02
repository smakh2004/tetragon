package com.example.tetragon.ui.uiMathStormOnline

import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityOnlineResultBattleMathStormBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.mathStormUtils.OnlineGameData
import com.example.tetragon.utils.soundUtils.SoundManager
import com.google.firebase.firestore.FirebaseFirestore

class OnlineResultBattleMathStormActivity : BaseActivity() {

    private lateinit var binding: ActivityOnlineResultBattleMathStormBinding
    private val db = FirebaseFirestore.getInstance()
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineResultBattleMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // API 27+
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white) // optional: set nav bar color
        }

        deleteRoom()

        val yourScore = intent.getIntExtra("YOUR_SCORE", 0)
        val opponentScore = intent.getIntExtra("OPPONENT_SCORE", 0)
        val yourStatus = intent.getStringExtra("YOUR_STATUS") ?: "DRAW"
        val opponentStatus = intent.getStringExtra("OPPONENT_STATUS") ?: "DRAW"

        // Display scores
        binding.yourResultText.text = yourScore.toString()
        binding.opponentResultText.text = opponentScore.toString()

        playResultSound(yourStatus)

        // Apply colors
        applyResultStyle(yourStatus, binding.yourStatus, binding.yourResultText)
        applyResultStyle(opponentStatus, binding.opponentStatus, binding.opponentResultText)

        // Show correct player names
        displayPlayerNamesAndStatus()

        binding.exitBtn.setOnClickListener {
            finish()
            overridePendingTransition(
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }
    }

    private fun displayPlayerNamesAndStatus() {
        val model = OnlineGameData.gameModel.value ?: return
        val yourUID = OnlineGameData.myID
        val opponentUID = if (yourUID == model.player1) model.player2 else model.player1

        // Use internal constants for logic, but local variables for UI text
        val yourStatusInternal: String
        val opponentStatusInternal: String

        if (!model.quitterID.isNullOrEmpty()) {
            if (model.quitterID == yourUID) {
                yourStatusInternal = "LOSER"
                opponentStatusInternal = "WINNER"
            } else {
                yourStatusInternal = "WINNER"
                opponentStatusInternal = "LOSER"
            }
        } else {
            val yourScore = intent.getIntExtra("YOUR_SCORE", 0)
            val opponentScore = intent.getIntExtra("OPPONENT_SCORE", 0)

            when {
                yourScore > opponentScore -> {
                    yourStatusInternal = "WINNER"
                    opponentStatusInternal = "LOSER"
                }
                yourScore < opponentScore -> {
                    yourStatusInternal = "LOSER"
                    opponentStatusInternal = "WINNER"
                }
                else -> {
                    yourStatusInternal = "DRAW"
                    opponentStatusInternal = "DRAW"
                }
            }
        }

        // Set Localized Text to UI
        binding.yourStatus.text = getLocalizedStatus(yourStatusInternal)
        binding.opponentStatus.text = getLocalizedStatus(opponentStatusInternal)

        // Fetch names with localized defaults
        if (yourUID.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(yourUID)
                .get().addOnSuccessListener { snap ->
                    binding.yourName.text = snap?.getString("firstName") ?: getString(R.string.player_default)
                }
        }

        if (!opponentUID.isNullOrEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(opponentUID)
                .get().addOnSuccessListener { snap ->
                    binding.opponentName.text = snap?.getString("firstName") ?: getString(R.string.opponent_default)
                }
        }

        applyResultStyle(yourStatusInternal, binding.yourStatus, binding.yourResultText)
        applyResultStyle(opponentStatusInternal, binding.opponentStatus, binding.opponentResultText)

        updateUserOnlineScore(yourStatusInternal)
    }

    private fun getLocalizedStatus(status: String): String {
        return when (status) {
            "WINNER" -> getString(R.string.winner)
            "LOSER" -> getString(R.string.loser)
            else -> getString(R.string.draw)
        }
    }

    private fun playResultSound(status: String) {
        if (!SoundManager.isSoundEnabled(this)) return
        when (status) {
            "WINNER" -> {
                mediaPlayer = MediaPlayer.create(this, R.raw.winner)
            }
            "LOSER" -> {
                mediaPlayer = MediaPlayer.create(this, R.raw.loser)
            }
            else -> return
        }

        mediaPlayer?.start()

        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
    }

    private fun deleteRoom() {
        val model = OnlineGameData.gameModel.value ?: return
        if (OnlineGameData.myID == model.player1) {
            db.collection("online_games").document(model.roomID).delete()
        }
    }

    private fun applyResultStyle(
        status: String,
        statusView: TextView,
        scoreView: TextView
    ) {
        val color = when (status) {
            "WINNER" -> getColor(R.color.green_1)
            "LOSER" -> getColor(R.color.red_1)
            else -> getColor(R.color.gray_1)
        }

        statusView.setTextColor(color)
        scoreView.setTextColor(color)
    }

    private fun updateUserOnlineScore(yourStatus: String) {
        if (yourStatus != "WINNER") return

        val uid = OnlineGameData.myID
        val firestore = FirebaseFirestore.getInstance()

        // Reference to the Online Game Stats
        val onlineStatsDoc = firestore.collection("users").document(uid)
            .collection("games").document("OnlineMathStorm")

        // Reference to the Main User Profile for XP
        val userProfileDoc = firestore.collection("users").document(uid)

        // Using a batch or transaction to update both documents at once
        firestore.runTransaction { transaction ->
            val userSnapshot = transaction.get(userProfileDoc)
            val statsSnapshot = transaction.get(onlineStatsDoc)

            val currentXP = userSnapshot.getLong("xp") ?: 0L
            val currentMonthlyXP = userSnapshot.getLong("monthlyXP") ?: 0L // Get monthly
            val currentOnlineWins = statsSnapshot.getLong("onlineScore") ?: 0L

            // Update BOTH fields in the User Profile
            transaction.update(userProfileDoc, "xp", currentXP + 30)
            transaction.update(userProfileDoc, "monthlyXP", currentMonthlyXP + 30)

            if (!statsSnapshot.exists()) {
                transaction.set(onlineStatsDoc, mapOf("onlineScore" to 1L))
            } else {
                transaction.update(onlineStatsDoc, "onlineScore", currentOnlineWins + 1)
            }
        }.addOnSuccessListener {
            // You could show a "Winner! +30 XP" Toast here
        }.addOnFailureListener {
            // Handle error
        }
    }
}
