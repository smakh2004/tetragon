package com.tetragon.app.ui.uiMathStormOnline

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.activity.viewModels
import com.tetragon.app.R
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityOnlineWaitingRoomBinding
import com.tetragon.app.utils.mathStormUtils.OnlineGameData
import com.tetragon.app.gameModel.GameModel
import com.tetragon.app.gameModel.GameStatus
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.getValue

class OnlineWaitingRoomMathStormActivity : BaseActivity() {

    private lateinit var binding: ActivityOnlineWaitingRoomBinding
    private var gameModel: GameModel? = null
    private val db = FirebaseFirestore.getInstance()

    private var gameStarted = false

    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ConnectivityViewModel::class.java)) {
                    return ConnectivityViewModel(
                        AndroidConnectivityObserver(applicationContext)
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityOnlineWaitingRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // API 27+
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white) // optional: set nav bar color
        }

        observeConnectivity()

        binding.cancelButton.setOnClickListener {
            cleanupRoomIfOwner()
            finish()
            overridePendingTransition(
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }

        observeGame()
    }

    private fun observeGame() {
        OnlineGameData.gameModel.observe(this) { model ->
            gameModel = model

            val myUID = OnlineGameData.myID
            val opponentUID = if (myUID == model.player1) model.player2 else model.player1
            val youUID = myUID

            // Opponent → top
            if (opponentUID.isNotEmpty()) {
                db.collection("users").document(opponentUID)
                    .addSnapshotListener { snapshot, _ ->
                        // Use translated "Opponent" if name is missing
                        binding.playerTwoTxt.text = snapshot?.getString("firstName") ?: getString(R.string.opponent_caps)

                        binding.searchIcon.visibility = View.GONE
                        binding.playerIconImage.visibility = View.VISIBLE
                    }
            } else {
                // Use translated "Searching.."
                binding.playerTwoTxt.text = getString(R.string.searching)
            }

            // Current user → bottom
            if (youUID.isNotEmpty()) {
                db.collection("users").document(youUID)
                    .addSnapshotListener { snapshot, _ ->
                        // Use translated "You" if name is missing
                        binding.playerOneTxt.text = snapshot?.getString("firstName") ?: getString(R.string.you_caps)
                    }
            }
            // Game start logic
            if (!gameStarted && model.gameStatus == GameStatus.JOINED) {
                gameStarted = true
                binding.cancelButton.visibility = View.GONE

                decreaseAttemptOnline()

                startGameWithDelay()
            }
        }

        OnlineGameData.fetchGameModel()
    }

    private fun decreaseAttemptOnline() {

        val uid = OnlineGameData.myID

        val docRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("games")
            .document("Attempts")

        FirebaseFirestore.getInstance().runTransaction { transaction ->

            val snapshot = transaction.get(docRef)
            val current =
                snapshot.getLong("remainingAttempts") ?: 5L

            if (current > 0) {
                transaction.update(docRef, "remainingAttempts", current - 1)
            }
        }
    }

    private fun startGameWithDelay() {
        Handler(mainLooper).postDelayed({
            startActivity(Intent(this, OnlineBattleMathStormActivity::class.java))
            overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            finish()
        }, 2000)
    }

    private fun cleanupRoomIfOwner() {
        val model = gameModel ?: return
        if (model.gameStatus != GameStatus.CREATED) return
        if (OnlineGameData.myID == model.player1) {
            db.collection("online_games").document(model.roomID).delete()
        }
    }

    override fun onStop() {
        super.onStop()
        cleanupRoomIfOwner()
        finish()
    }
    // ✅ Delete room if user presses back
    override fun onBackPressed() {
        cleanupRoomIfOwner()
        super.onBackPressed()
    }

    // ✅ Delete room if activity is destroyed (app closed or activity finished)
    override fun onDestroy() {
        super.onDestroy()
        cleanupRoomIfOwner()
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->

                    if (isConnected) {
                        // Hide banner
                        binding.internetConnection.visibility = View.GONE
                        binding.offlineContainer.visibility = View.GONE
                        binding.mainContent.visibility = View.VISIBLE

                    } else {
                        // Show banner
                        binding.internetConnection.visibility = View.VISIBLE
                        binding.offlineContainer.visibility = View.VISIBLE
                        binding.mainContent.visibility = View.GONE

                        // 🔥 Offline → cleanup room and exit
                        cleanupRoomIfOwner()
                    }
                }
            }
        }
    }
}
