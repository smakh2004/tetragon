package com.example.tetragon.ui.uiMathStormPrivate

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tetragon.R
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.databinding.ActivityPrivateWaitingRoomMathStormBinding
import com.example.tetragon.gameModel.PrivateGameModel
import com.example.tetragon.gameModel.PrivateGameStatus
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.mathStormUtils.PrivateGameData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.getValue

class PrivateWaitingRoomMathStorm : BaseActivity() {

    private lateinit var binding: ActivityPrivateWaitingRoomMathStormBinding
    private var gameModel: PrivateGameModel? = null
    private var navigated = false
    private val db = FirebaseFirestore.getInstance()
    // CONNECTIVITY
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

        PrivateGameData.fetchGameModel()
        PrivateGameData.gameModel.observe(this) {
            gameModel = it
            updateUI()
        }
    }

    private fun fetchUserName(uid: String, callback: (String) -> Unit) {
        if (uid.isEmpty()) {
            callback("Unknown")
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc?.getString("firstName") ?: "Unknown"
                callback(name)
            }
            .addOnFailureListener { callback("Unknown") }
    }

    private fun updateUI() {
        val model = gameModel ?: return

        when (model.gameStatus) {
            PrivateGameStatus.CREATED -> {
                binding.gameIdText.text = "Room ID: ${model.gameID}"

                fetchUserName(model.player1) { name ->
                    binding.playerOneName.text = name
                }

                if (model.player2.isEmpty()) {
                    binding.playerTwoName.text = "Waiting for opponent..."
                } else {
                    fetchUserName(model.player2) { name ->
                        binding.playerTwoName.text = name
                        // 🔹 SWAP ANIMATION FOR IMAGE
                        binding.searchIcon.visibility = View.GONE
                        binding.playerIconImage.visibility = View.VISIBLE
                    }
                }

                binding.cancelButton.visibility =
                    if (PrivateGameData.myID == "P1") View.VISIBLE else View.GONE
            }

            PrivateGameStatus.JOINED -> {
                fetchUserName(model.player1) { name ->
                    binding.playerOneName.text = name
                }
                fetchUserName(model.player2) { name ->
                    binding.playerTwoName.text = name
                }

                binding.cancelButton.visibility = View.GONE
                if (!navigated) {
                    navigated = true
                    startActivity(
                        Intent(this@PrivateWaitingRoomMathStorm, PrivateBattleMathStormActivity::class.java)
                    )
                    overridePendingTransition(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                    )
                    finish()
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
        cleanupRoomIfOwner()
        finish()
    }

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

                    }
                }
            }
        }
    }
}
