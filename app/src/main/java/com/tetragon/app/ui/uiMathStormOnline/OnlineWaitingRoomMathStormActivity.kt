package com.tetragon.app.ui.uiMathStormOnline

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityOnlineWaitingRoomBinding
import com.tetragon.app.gameModel.GameModel
import com.tetragon.app.gameModel.GameStatus
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.OnlineGameData
import kotlinx.coroutines.launch

class OnlineWaitingRoomMathStormActivity : BaseActivity() {

    private lateinit var binding: ActivityOnlineWaitingRoomBinding
    private var gameModel: GameModel? = null
    private val db = FirebaseFirestore.getInstance()

    private var opponentListener: ListenerRegistration? = null
    private var myListener: ListenerRegistration? = null
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

            // 1. OPPONENT STATE MANAGEMENT
            if (opponentUID.isNotEmpty()) {
                binding.searchingContainer.visibility = View.GONE
                binding.mySection.visibility = View.VISIBLE
                binding.opponentSection.visibility = View.VISIBLE

                opponentListener?.remove()
                opponentListener = db.collection("users").document(opponentUID)
                    .addSnapshotListener { snapshot, _ ->
                        val opponentName = snapshot?.getString("firstName") ?: getString(R.string.opponent_caps)
                        val opponentConfig = snapshot?.get("avatarConfig") as? Map<*, *>

                        applyAvatarConfigToRive(binding.opponentPlayerIconImage, opponentConfig, opponentName)
                    }
            } else {
                opponentListener?.remove()
                binding.searchingContainer.visibility = View.VISIBLE
                binding.opponentSection.visibility = View.GONE
                binding.mySection.visibility = View.GONE
            }

            // 2. MY AVATAR DATA FETCH
            if (myUID.isNotEmpty()) {
                myListener?.remove()
                myListener = db.collection("users").document(myUID)
                    .addSnapshotListener { snapshot, _ ->
                        val myName = snapshot?.getString("firstName") ?: getString(R.string.you_caps)
                        val myConfig = snapshot?.get("avatarConfig") as? Map<*, *>

                        applyAvatarConfigToRive(binding.myPlayerIconImage, myConfig, myName)
                    }
            }

            // 3. MATCH JOINED: HIDE CANCEL BUTTON & DELAY 3 SECONDS
            if (!gameStarted && model.gameStatus == GameStatus.JOINED) {
                gameStarted = true
                binding.cancelButton.visibility = View.GONE
                decreaseAttemptOnline()
                startGameWithDelay()
            }
        }
        OnlineGameData.fetchGameModel()
    }

    private fun applyAvatarConfigToRive(riveView: RiveAnimationView, config: Map<*, *>?, firstName: String) {
        riveView.post {
            try {
                val file = riveView.controller.file ?: return@post
                val vm = file.getViewModelByName("ViewModel1") ?: return@post
                val vmi = vm.createDefaultInstance()
                riveView.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

                // Assign First Name Text Property inside Rive State Machine
                vmi.getStringProperty("firstName")?.value = firstName

                // Number Properties (Force face = 9f for both players)
                val numberKeys = listOf("face", "hair", "glasses", "hat", "mustache", "body")
                numberKeys.forEach { key ->
                    val num = if (key == "face") {
                        9f
                    } else {
                        (config?.get(key) as? Number)?.toFloat() ?: 1f
                    }
                    vmi.getNumberProperty(key)?.value = num
                }

                // Hat Boolean
                val hatValue = (config?.get("hat") as? Number)?.toInt() ?: 1
                vmi.getBooleanProperty("hatOn")?.value = (hatValue > 1)

                // Color Properties
                val colorKeys = listOf(
                    "skinColor", "hairColor", "glassColor",
                    "capColor", "mustacheColor", "clothColor", "backgroundColor"
                )
                colorKeys.forEach { propName ->
                    (config?.get(propName) as? String)?.let { hex ->
                        runCatching { Color.parseColor(hex) }.getOrNull()?.let { colorInt ->
                            vmi.getColorProperty(propName)?.value = colorInt
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WaitingRoomRive", "Error applying avatar config: ${e.message}")
            }
        }
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
            val current = snapshot.getLong("remainingAttempts") ?: 5L

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
        }, 5000)
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

    override fun onBackPressed() {
        cleanupRoomIfOwner()
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        opponentListener?.remove()
        myListener?.remove()
        cleanupRoomIfOwner()
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    if (isConnected) {
                        binding.internetConnection.visibility = View.GONE
                        binding.offlineContainer.visibility = View.GONE
                        binding.mainContent.visibility = View.VISIBLE
                    } else {
                        binding.internetConnection.visibility = View.VISIBLE
                        binding.offlineContainer.visibility = View.VISIBLE
                        binding.mainContent.visibility = View.GONE

                        cleanupRoomIfOwner()
                    }
                }
            }
        }
    }
}