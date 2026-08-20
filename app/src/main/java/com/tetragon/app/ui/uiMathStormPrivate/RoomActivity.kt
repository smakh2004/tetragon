package com.tetragon.app.ui.uiMathStormPrivate

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityRoomBinding
import com.tetragon.app.gameModel.PrivateGameModel
import com.tetragon.app.gameModel.PrivateGameStatus
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.PrivateGameData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.getValue
import kotlin.random.Random
import kotlin.random.nextInt

class RoomActivity : BaseActivity() {
    private lateinit var binding: ActivityRoomBinding

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
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeConnectivity()

        binding.exitBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.createOnlineGameBtn.setOnClickListener {
            disableCreateButton()
            createOnlineGame()
        }

        binding.joinOnlineGameBtn.setOnClickListener {
            disableJoinButton()
            joinOnlineGame()
        }

        binding.gameIdInput.addTextChangedListener { text ->
            toggleJoinButton(text.toString())
        }
    }

    private fun createOnlineGame() {
        PrivateGameData.myID = "P1"
        val uid = auth.currentUser?.uid ?: ""
        val gameModel = PrivateGameModel(
            gameStatus = PrivateGameStatus.CREATED,
            gameID = Random.nextInt(1000..9999).toString(),
            player1 = uid
        )
        PrivateGameData.saveGameModel(gameModel)

        hasNavigated = true
        startActivity(Intent(this, PrivateWaitingRoomMathStorm::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun joinOnlineGame() {
        val gameId = binding.gameIdInput.text.toString()
        if (gameId.isEmpty()) {
            binding.gameIdInput.error = getString(R.string.error_enter_id)
            resetButtons()
            return
        }

        PrivateGameData.myID = "P2"
        val uid = auth.currentUser?.uid ?: ""

        db.collection("private_games").document(gameId).get()
            .addOnSuccessListener { doc ->
                val model = doc?.toObject(PrivateGameModel::class.java)
                if (model == null) {
                    binding.gameIdInput.error = getString(R.string.error_invalid_id)
                    resetButtons()
                } else {
                    model.player2 = uid
                    model.gameStatus = PrivateGameStatus.JOINED
                    PrivateGameData.saveGameModel(model)
                    hasNavigated = true
                    // FIX: Direct Player 2 to PrivateWaitingRoomMathStorm first
                    startActivity(Intent(this, PrivateWaitingRoomMathStorm::class.java))
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
            }
            .addOnFailureListener {
                binding.gameIdInput.error = getString(R.string.error_connection)
                resetButtons()
            }
    }

    private fun toggleJoinButton(input: String) {
        if (input.length == 4) enableJoinButton()
        else disableJoinButtonStatic()
    }

    private fun disableCreateButton() {
        binding.createOnlineGameBtnEnabled.visibility = View.GONE
        binding.createOnlineGameBtnDisabled.visibility = View.VISIBLE
        binding.createOnlineGameBtnDisabledText.text = getString(R.string.connecting_caps)
    }

    private fun disableJoinButton() {
        binding.joinOnlineGameBtnContainer.visibility = View.INVISIBLE
        binding.joinOnlineGameDisabledBtn.visibility = View.VISIBLE
        binding.joinOnlineGameDisabledText.text = getString(R.string.connecting_caps)
        disableCreateButtonStatic()
    }

    private fun enableJoinButton() {
        binding.joinOnlineGameBtnContainer.visibility = View.VISIBLE
        binding.joinOnlineGameDisabledBtn.visibility = View.INVISIBLE
        binding.joinOnlineGameDisabledText.text = getString(R.string.join_room)
    }

    private fun disableJoinButtonStatic() {
        binding.joinOnlineGameBtnContainer.visibility = View.INVISIBLE
        binding.joinOnlineGameDisabledBtn.visibility = View.VISIBLE
        binding.joinOnlineGameDisabledText.text = getString(R.string.join_room)
    }

    private fun resetButtons() {
        binding.createOnlineGameBtnEnabled.visibility = View.VISIBLE
        binding.createOnlineGameBtnDisabled.visibility = View.GONE
        binding.createOnlineGameBtnDisabledText.text = getString(R.string.create_room)

        val input = binding.gameIdInput.text.toString()
        if (input.length == 4) enableJoinButton() else disableJoinButtonStatic()
    }

    private fun disableCreateButtonStatic() {
        binding.createOnlineGameBtnEnabled.visibility = View.GONE
        binding.createOnlineGameBtnDisabled.visibility = View.VISIBLE
        binding.createOnlineGameBtnDisabledText.text = getString(R.string.create_room)
    }

    override fun onResume() {
        super.onResume()
        if (hasNavigated) {
            resetButtons()
            hasNavigated = false
        }
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    if (isConnected) {
                        binding.internetConnection.visibility = View.GONE
                        binding.gameIdInput.isEnabled = true
                        resetButtons()
                    } else {
                        binding.internetConnection.visibility = View.VISIBLE
                        binding.gameIdInput.isEnabled = false

                        binding.createOnlineGameBtnEnabled.visibility = View.GONE
                        binding.createOnlineGameBtnDisabled.visibility = View.VISIBLE
                        binding.joinOnlineGameBtnContainer.visibility = View.GONE
                        binding.joinOnlineGameDisabledBtn.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}