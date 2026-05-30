package com.tetragon.app.ui.uiMathStormPrivate

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityPrivateWaitingRoomMathStormBinding
import com.tetragon.app.gameModel.PrivateGameModel
import com.tetragon.app.gameModel.PrivateGameStatus
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.PrivateGameData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.getValue

class PrivateWaitingRoomMathStorm : BaseActivity() {

    private lateinit var binding: ActivityPrivateWaitingRoomMathStormBinding
    private var gameModel: PrivateGameModel? = null
    private var navigated = false
    private val db = FirebaseFirestore.getInstance()

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
        binding = ActivityPrivateWaitingRoomMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        observeConnectivity()

        binding.cancelButton.setOnClickListener {
            cleanupRoomIfOwner()
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        PrivateGameData.fetchGameModel()
        PrivateGameData.gameModel.observe(this) {
            gameModel = it
            updateUI()
        }
    }

    private fun fetchUserData(uid: String, callback: (String, String) -> Unit) {
        val unknown = getString(R.string.unknown_player)
        if (uid.isEmpty()) {
            callback(unknown, "")
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc?.getString("firstName") ?: unknown
                val avatar = doc?.getString("avatarName") ?: ""
                callback(name, avatar)
            }
            .addOnFailureListener { callback(unknown, "") }
    }

    private fun updateUI() {
        val model = gameModel ?: return
        val myUID = if (PrivateGameData.myID == "P1") model.player1 else model.player2
        val opponentUID = if (PrivateGameData.myID == "P1") model.player2 else model.player1

        // 1. Set "YOU" (Bottom slot)
        fetchUserData(myUID) { name, avatar ->
            binding.playerOneName.text = "$name (${getString(R.string.you_caps)})"
            loadAvatar(binding.myPlayerIconImage, avatar)
        }

        // 2. Set "OPPONENT" (Top slot)
        if (opponentUID.isEmpty()) {
            binding.playerTwoName.text = getString(R.string.searching)
            binding.searchIcon.visibility = View.VISIBLE
            binding.opponentAvatarContainer.visibility = View.GONE
        } else {
            fetchUserData(opponentUID) { name, avatar ->
                binding.playerTwoName.text = name
                loadAvatar(binding.playerIconImage, avatar)

                binding.searchIcon.visibility = View.GONE
                binding.opponentAvatarContainer.visibility = View.VISIBLE
            }
        }

        // 3. Handle Room ID and Status
        // Extracted "Room ID:" prefix
        binding.gameIdText.text = "${getString(R.string.room_id_prefix)} ${model.gameID}"

        when (model.gameStatus) {
            PrivateGameStatus.CREATED -> {
                binding.cancelButton.visibility =
                    if (PrivateGameData.myID == "P1") View.VISIBLE else View.GONE
            }

            PrivateGameStatus.JOINED -> {
                binding.cancelButton.visibility = View.GONE
                if (!navigated) {
                    navigated = true
                    Handler(Looper.getMainLooper()).postDelayed({
                        startActivity(Intent(this, PrivateBattleMathStormActivity::class.java))
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        finish()
                    }, 1500)
                }
            }
        }
    }

    private fun cleanupRoomIfOwner() {
        val model = gameModel ?: return
        if (model.gameStatus != PrivateGameStatus.CREATED) return
        if (PrivateGameData.myID == "P1") {
            db.collection("private_games").document(model.gameID).delete()
        }
    }

    override fun onBackPressed() {
        cleanupRoomIfOwner()
        super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        // Only cleanup if we aren't navigating to the game
        if (!navigated) {
            cleanupRoomIfOwner()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!navigated) {
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

    private fun loadAvatar(imageView: android.widget.ImageView, avatarName: String?) {
        val resId = if (!avatarName.isNullOrEmpty()) {
            resources.getIdentifier(avatarName, "drawable", packageName)
        } else { 0 }
        if (resId != 0) imageView.setImageResource(resId)
    }
}