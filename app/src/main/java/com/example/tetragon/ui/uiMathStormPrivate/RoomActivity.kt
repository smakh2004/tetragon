package com.example.tetragon.ui.uiMathStormPrivate

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tetragon.R
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.databinding.ActivityRoomBinding
import com.example.tetragon.gameModel.PrivateGameModel
import com.example.tetragon.gameModel.PrivateGameStatus
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.mathStormUtils.PrivateGameData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.getValue
import kotlin.random.Random
import kotlin.random.nextInt

class RoomActivity : BaseActivity() {
    private lateinit var binding: ActivityRoomBinding

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
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // API 27+
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white) // optional: set nav bar color
        }

        observeConnectivity()

        binding.exitBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.createOnlineGameBtn.setOnClickListener {
            disableCreateButton()
            createOnlineGame()
            overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }

        binding.joinOnlineGameBtn.setOnClickListener {
            disableJoinButton()
            joinOnlineGame()
            overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }

        binding.gameIdInput.addTextChangedListener { text ->
            toggleJoinButton(text.toString())
            overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
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
    }

    private fun joinOnlineGame() {
        val gameId = binding.gameIdInput.text.toString()
        if (gameId.isEmpty()) {
            binding.gameIdInput.error = "Please enter Room ID"
            resetButtons()
            return
        }

        PrivateGameData.myID = "P2"
        val uid = auth.currentUser?.uid ?: ""

        db.collection("private_games").document(gameId).get()
            .addOnSuccessListener { doc ->
                val model = doc?.toObject(PrivateGameModel::class.java)
                if (model == null) {
                    binding.gameIdInput.error = "Invalid game ID"
                    resetButtons()
                } else {
                    model.player2 = uid
                    model.gameStatus = PrivateGameStatus.JOINED
                    PrivateGameData.saveGameModel(model)
                    hasNavigated = true
                    startActivity(Intent(this, PrivateBattleMathStormActivity::class.java))
                }
            }
            .addOnFailureListener {
                binding.gameIdInput.error = "Error connecting. Try again."
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
        binding.createOnlineGameBtnDisabled.findViewById<TextView>(
            R.id.create_online_game_btn_disabled_text
        ).text = "CONNECTING..."
    }

    private fun disableJoinButton() {
        binding.joinOnlineGameBtnContainer.visibility = View.INVISIBLE
        binding.joinOnlineGameDisabledBtn.visibility = View.VISIBLE
        binding.joinOnlineGameDisabledBtn.findViewById<TextView>(
            R.id.join_online_game_disabled_text
        ).text = "CONNECTING..."

        disableCreateButtonStatic()
    }

    private fun enableJoinButton() {
        binding.joinOnlineGameBtnContainer.visibility = View.VISIBLE
        binding.joinOnlineGameDisabledBtn.visibility = View.INVISIBLE
        binding.joinOnlineGameDisabledBtn.findViewById<TextView>(
            R.id.join_online_game_disabled_text
        ).text = "JOIN ROOM"
    }

    private fun disableJoinButtonStatic() {
        binding.joinOnlineGameBtnContainer.visibility = View.INVISIBLE
        binding.joinOnlineGameDisabledBtn.visibility = View.VISIBLE
        binding.joinOnlineGameDisabledBtn.findViewById<TextView>(
            R.id.join_online_game_disabled_text
        ).text = "JOIN ROOM"
    }

    private fun resetButtons() {
        binding.createOnlineGameBtnEnabled.visibility = View.VISIBLE
        binding.createOnlineGameBtnDisabled.visibility = View.GONE
        binding.createOnlineGameBtnDisabled.findViewById<TextView>(
            R.id.create_online_game_btn_disabled_text
        ).text = "CREATE ROOM"

        val input = binding.gameIdInput.text.toString()
        if (input.length == 4) enableJoinButton() else disableJoinButtonStatic()
    }

    private fun disableCreateButtonStatic() {
        binding.createOnlineGameBtnEnabled.visibility = View.GONE
        binding.createOnlineGameBtnDisabled.visibility = View.VISIBLE
        binding.createOnlineGameBtnDisabled.findViewById<TextView>(
            R.id.create_online_game_btn_disabled_text
        ).text = "CREATE ROOM"
    }

    override fun onResume() {
        super.onResume()
        if (hasNavigated) {
            // User returned to this activity
            resetButtons()
            hasNavigated = false
        }
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->

                    if (isConnected) {
                        // Hide offline banner
                        binding.internetConnection.visibility = View.GONE

                        // Enable buttons
                        binding.joinOnlineGameBtnContainer.visibility = View.INVISIBLE
                        binding.joinOnlineGameDisabledBtn.visibility = View.VISIBLE
                        binding.joinOnlineGameDisabledText.text = "JOIN ROOM"

                        binding.createOnlineGameBtnEnabled.visibility = View.VISIBLE
                        binding.createOnlineGameBtnDisabled.visibility = View.GONE
                        binding.createOnlineGameBtnDisabledText.text = "CREATE ROOM"

                        // ✅ Enable room ID input
                        binding.gameIdInput.isEnabled = true

                    } else {
                        // Show offline banner
                        binding.internetConnection.visibility = View.VISIBLE

                        // Disable buttons
                        binding.joinOnlineGameBtnContainer.visibility = View.GONE
                        binding.joinOnlineGameDisabledBtn.visibility = View.VISIBLE
                        binding.joinOnlineGameDisabledText.text = "JOIN ROOM"

                        binding.createOnlineGameBtnEnabled.visibility = View.GONE
                        binding.createOnlineGameBtnDisabled.visibility = View.VISIBLE
                        binding.createOnlineGameBtnDisabledText.text = "CREATE ROOM"

                        // ✅ Disable room ID input
                        binding.gameIdInput.isEnabled = false
                    }
                }
            }
        }
    }

}
