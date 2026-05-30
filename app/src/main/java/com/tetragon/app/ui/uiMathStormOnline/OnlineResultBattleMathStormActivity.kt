package com.tetragon.app.ui.uiMathStormOnline

import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityOnlineResultBattleMathStormBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.OnlineGameData
import com.tetragon.app.utils.soundUtils.SoundManager
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        deleteRoom()

        val yourScore = intent.getIntExtra("YOUR_SCORE", 0)
        val opponentScore = intent.getIntExtra("OPPONENT_SCORE", 0)
        val yourStatus = intent.getStringExtra("YOUR_STATUS") ?: "DRAW"

        // Display scores
        binding.yourResultText.text = yourScore.toString()
        binding.opponentResultText.text = opponentScore.toString()

        playResultSound(yourStatus)

        // Show loading display layout layer instantly before background query calls initiate
        binding.loadingOverlayContainer.visibility = View.VISIBLE

        // Handle structural evaluation and user lookups
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
        val model = OnlineGameData.gameModel.value ?: run {
            binding.loadingOverlayContainer.visibility = View.GONE
            return
        }
        val yourUID = OnlineGameData.myID
        val opponentUID = if (yourUID == model.player1) model.player2 else model.player1

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

        applyResultStyle(yourStatusInternal, binding.yourStatus, binding.yourResultText)
        applyResultStyle(opponentStatusInternal, binding.opponentStatus, binding.opponentResultText)

        // Track expected firestore async completion routines safely before closing overlay layout
        var pendingRequests = 0
        if (yourUID.isNotEmpty()) pendingRequests++
        if (!opponentUID.isNullOrEmpty()) pendingRequests++

        fun checkDismissOverlay() {
            pendingRequests--
            if (pendingRequests <= 0) {
                if (!isFinishing && !isDestroyed) {
                    binding.loadingOverlayContainer.visibility = View.GONE
                }
            }
        }

        if (yourUID.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(yourUID)
                .get().addOnCompleteListener { task ->
                    if (!isFinishing && !isDestroyed && task.isSuccessful) {
                        val snapshot = task.result
                        binding.yourName.text = snapshot?.getString("firstName") ?: getString(R.string.player_default)
                        // Load your avatar
                        loadAvatar(binding.yourAvatar, snapshot?.getString("avatarName"))
                    }
                    checkDismissOverlay()
                }
        }

        if (!opponentUID.isNullOrEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(opponentUID)
                .get().addOnCompleteListener { task ->
                    if (!isFinishing && !isDestroyed && task.isSuccessful) {
                        val snapshot = task.result
                        binding.opponentName.text = snapshot?.getString("firstName") ?: getString(R.string.opponent_default)
                        // Load opponent avatar
                        loadAvatar(binding.opponentAvatar, snapshot?.getString("avatarName"))
                    }
                    checkDismissOverlay()
                }
        }

        if (pendingRequests == 0) {
            binding.loadingOverlayContainer.visibility = View.GONE
        }

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
            "WINNER" -> ContextCompat.getColor(this, R.color.green_1)
            "LOSER" -> ContextCompat.getColor(this, R.color.red_1)
            else -> ContextCompat.getColor(this, R.color.gray_1)
        }

        statusView.setTextColor(color)
        scoreView.setTextColor(color)
    }

    private fun updateUserOnlineScore(yourStatus: String) {
        if (yourStatus != "WINNER") return

        val uid = OnlineGameData.myID
        val firestore = FirebaseFirestore.getInstance()
        // Standardized date format to match your graph logic
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val onlineStatsDoc = firestore.collection("users").document(uid)
            .collection("games").document("OnlineMathStorm")
        val userProfileDoc = firestore.collection("users").document(uid)

        firestore.runTransaction { transaction ->
            val userSnapshot = transaction.get(userProfileDoc)
            val statsSnapshot = transaction.get(onlineStatsDoc)

            val currentXP = userSnapshot.getLong("xp") ?: 0L
            val currentMonthlyXP = userSnapshot.getLong("monthlyXP") ?: 0L
            val currentOnlineWins = statsSnapshot.getLong("onlineScore") ?: 0L

            // Fetch daily map or create new
            val dailyXpMap = userSnapshot.get("dailyXPGains") as? MutableMap<String, Long> ?: mutableMapOf()

            // Winning an online battle awards 30 XP
            val xpGain = 30L
            val currentTodayXp = dailyXpMap[todayKey] ?: 0L

            // Update map
            dailyXpMap[todayKey] = currentTodayXp + xpGain

            // Update User Profile fields atomically
            transaction.update(userProfileDoc, mapOf(
                "xp" to (currentXP + xpGain),
                "monthlyXP" to (currentMonthlyXP + xpGain),
                "dailyXPGains" to dailyXpMap
            ))

            // Update Online Stats
            if (!statsSnapshot.exists()) {
                transaction.set(onlineStatsDoc, mapOf("onlineScore" to 1L))
            } else {
                transaction.update(onlineStatsDoc, "onlineScore", currentOnlineWins + 1)
            }
        }.addOnSuccessListener {
            // Log or handle success if required
        }.addOnFailureListener {
            // Log or handle failure if required
        }
    }

    // Add this helper method inside the class
    private fun loadAvatar(imageView: android.widget.ImageView, avatarName: String?) {
        val resId = if (!avatarName.isNullOrEmpty()) {
            resources.getIdentifier(avatarName, "drawable", packageName)
        } else { 0 }
        if (resId != 0) imageView.setImageResource(resId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}