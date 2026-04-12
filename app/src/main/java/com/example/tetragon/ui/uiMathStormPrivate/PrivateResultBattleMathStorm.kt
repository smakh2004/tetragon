package com.example.tetragon.ui.uiMathStormPrivate

import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import com.example.tetragon.R
import androidx.core.content.ContextCompat
import com.example.tetragon.databinding.ActivityPrivateResultBattleMathStormBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.mathStormUtils.PrivateGameData
import com.example.tetragon.utils.soundUtils.SoundManager
import com.google.firebase.firestore.FirebaseFirestore


class PrivateResultBattleMathStorm : BaseActivity() {

    private lateinit var binding: ActivityPrivateResultBattleMathStormBinding
    private val db = FirebaseFirestore.getInstance()
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivateResultBattleMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // API 27+
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white) // optional: set nav bar color
        }

        deleteRoom()

        val yourStatus = intent.getStringExtra("YOUR_STATUS") ?: "DRAW"
        val opponentStatus = intent.getStringExtra("OPPONENT_STATUS") ?: "DRAW"
        val yourScore = intent.getIntExtra("YOUR_SCORE", 0)
        val opponentScore = intent.getIntExtra("OPPONENT_SCORE", 0)

        binding.yourResultText.text = yourScore.toString()
        binding.opponentResultText.text = opponentScore.toString()

        playResultSound(yourStatus)

        // Fetch names
        val model = PrivateGameData.gameModel.value
        if (model != null) {
            // Fetch both names
            fetchUserName(model.player1) { name1 ->
                fetchUserName(model.player2) { name2 ->
                    if (PrivateGameData.myID == "P1") {
                        binding.yourName.text = name1
                        binding.opponentName.text = name2
                    } else {
                        binding.yourName.text = name2
                        binding.opponentName.text = name1
                    }
                }
            }
        }

        binding.yourStatus.text = yourStatus
        binding.opponentStatus.text = opponentStatus

        applyResultStyle(yourStatus, binding.yourStatus, binding.yourResultText)
        applyResultStyle(opponentStatus, binding.opponentStatus, binding.opponentResultText)
        updateUserOnlineScore(yourStatus)

        binding.exitBtn.setOnClickListener {
            finish()
            overridePendingTransition(
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
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
        if (PrivateGameData.myID == "P1") {
            PrivateGameData.gameModel.value?.gameID?.let {
                db.collection("private_games").document(it).delete()
            }
        }
    }

    private fun applyResultStyle(status: String, statusView: android.widget.TextView, scoreView: android.widget.TextView) {
        val color = when (status) {
            "WINNER" -> getColor(R.color.green_1)
            "LOSER" -> getColor(R.color.red_1)
            else -> getColor(R.color.black_3)
        }
        statusView.setTextColor(color)
        scoreView.setTextColor(color)
    }

    private fun fetchUserName(uid: String, callback: (String) -> Unit) {
        if (uid.isEmpty()) { callback("Unknown"); return }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc?.getString("firstName") ?: "Unknown"
                callback(name)
            }
            .addOnFailureListener { callback("Unknown") }
    }

    private fun updateUserOnlineScore(yourStatus: String) {
        if (yourStatus != "WINNER") return

        val model = PrivateGameData.gameModel.value ?: return

        // Determine real UID of winner
        val myUID = if (PrivateGameData.myID == "P1") model.player1 else model.player2

        val yourScore = intent.getIntExtra("YOUR_SCORE", 0)
        val opponentScore = intent.getIntExtra("OPPONENT_SCORE", 0)

        val winnerUID = when {
            yourScore > opponentScore -> myUID
            yourScore < opponentScore -> if (PrivateGameData.myID == "P1") model.player2 else model.player1
            else -> null
        }

        if (winnerUID == null || winnerUID != myUID) return

        val gameDoc = db.collection("users")
            .document(myUID)       // <-- now using actual Firebase UID
            .collection("games")
            .document("OnlineMathStorm")

        // Use atomic increment
        gameDoc.set(mapOf("onlineScore" to 0)) // ensures document exists
            .addOnCompleteListener {
                gameDoc.update("onlineScore", com.google.firebase.firestore.FieldValue.increment(1))
            }
    }
}
