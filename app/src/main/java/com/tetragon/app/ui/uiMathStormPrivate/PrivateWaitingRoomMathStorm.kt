package com.tetragon.app.ui.uiMathStormPrivate

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.tetragon.app.databinding.ActivityPrivateWaitingRoomMathStormBinding
import com.tetragon.app.gameModel.PrivateGameModel
import com.tetragon.app.gameModel.PrivateGameStatus
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.PrivateGameData
import kotlinx.coroutines.launch

class PrivateWaitingRoomMathStorm : BaseActivity() {

    private lateinit var binding: ActivityPrivateWaitingRoomMathStormBinding
    private var gameModel: PrivateGameModel? = null
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
        binding = ActivityPrivateWaitingRoomMathStormBinding.inflate(layoutInflater)
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
        PrivateGameData.gameModel.observe(this) { model ->
            gameModel = model

            // Robust host check handling both Firebase UID and "P1" string references
            val isHost = PrivateGameData.myID == model.player1 || PrivateGameData.myID == "P1"
            val myUID = if (isHost) model.player1 else model.player2
            val opponentUID = if (isHost) model.player2 else model.player1

            // Display Room ID Header
            binding.gameIdText.text = "${getString(R.string.room_id_prefix)} ${model.gameID}"

            // 1. MY AVATAR DATA FETCH
            if (myUID.isNotEmpty() && myListener == null) {
                myListener = db.collection("users").document(myUID)
                    .addSnapshotListener { snapshot, _ ->
                        val myName = snapshot?.getString("firstName") ?: getString(R.string.you_caps)
                        val myConfig = snapshot?.get("avatarConfig") as? Map<*, *>

                        applyAvatarConfigToRive(binding.myPlayerIconImage, myConfig, myName)
                    }
            }

            // 2. OPPONENT AVATAR FETCH
            if (opponentUID.isNotEmpty()) {
                if (opponentListener == null) {
                    opponentListener = db.collection("users").document(opponentUID)
                        .addSnapshotListener { snapshot, _ ->
                            val opponentName = snapshot?.getString("firstName") ?: getString(R.string.opponent_caps)
                            val opponentConfig = snapshot?.get("avatarConfig") as? Map<*, *>

                            applyAvatarConfigToRive(binding.opponentPlayerIconImage, opponentConfig, opponentName)
                        }
                }
            } else {
                opponentListener?.remove()
                opponentListener = null
                binding.searchingContainer.visibility = View.VISIBLE
                binding.opponentSection.visibility = View.GONE
                binding.mySection.visibility = View.GONE
            }

            // 3. MATCH JOINED: SHOW BOTH RIVE AVATARS FOR 5 SECONDS
            if (!gameStarted && model.gameStatus == PrivateGameStatus.JOINED && opponentUID.isNotEmpty()) {
                gameStarted = true

                binding.searchingContainer.visibility = View.GONE
                binding.mySection.visibility = View.VISIBLE
                binding.opponentSection.visibility = View.VISIBLE
                binding.cancelButton.visibility = View.GONE

                startGameWithDelay()
            }
        }
        PrivateGameData.fetchGameModel()
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
                Log.e("PrivateWaitingRoomRive", "Error applying avatar config: ${e.message}")
            }
        }
    }

    private fun startGameWithDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, PrivateBattleMathStormActivity::class.java))
            overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            finish()
        }, 5000)
    }

    private fun cleanupRoomIfOwner() {
        val model = gameModel ?: return
        if (model.gameStatus != PrivateGameStatus.CREATED) return
        val isHost = PrivateGameData.myID == model.player1 || PrivateGameData.myID == "P1"
        if (isHost) {
            db.collection("private_games").document(model.gameID).delete()
        }
    }

    override fun onBackPressed() {
        cleanupRoomIfOwner()
        super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        if (!gameStarted) {
            cleanupRoomIfOwner()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        opponentListener?.remove()
        myListener?.remove()
        if (!gameStarted) {
            cleanupRoomIfOwner()
        }
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