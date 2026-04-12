package com.example.tetragon.ui.uiMathStormOnline

import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tetragon.R
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.databinding.ActivityOnlineBattleMathStormBinding
import com.example.tetragon.utils.mathStormUtils.OnlineGameData
import com.example.tetragon.gameModel.GameModel
import com.example.tetragon.gameModel.GameStatus
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.mathStormUtils.OnlineMathStormController
import com.example.tetragon.utils.soundUtils.SoundManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlin.getValue

class OnlineBattleMathStormActivity : BaseActivity() {
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

    private var gameTimer: CountDownTimer? = null

    // ---------------- UI & Controller ----------------
    private lateinit var binding: ActivityOnlineBattleMathStormBinding
    private lateinit var controller: OnlineMathStormController

    // ---------------- Firestore ----------------
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null
    private var gameModel: GameModel? = null  // Holds the latest game snapshot

    // ---------------- Game State ----------------
    private var myScore = 0
    private var myMistakes = 0
    private var opponentScore = 0
    private var opponentMistakes = 0

    private val MAX_MISTAKES = 3
    private val TOTAL_TIME = 3 * 60 * 1000L  // 3 minutes
    private lateinit var timer: CountDownTimer

    private var gameId = ""
    private var hasNavigatedToResult = false

    // ---------------- Sounds ----------------
    private var correctSound: MediaPlayer? = null
    private var incorrectSound: MediaPlayer? = null
    private var lastOpponentMistakes = 0  // To detect when opponent made a new mistake
    private var countdownStarted = false
    private lateinit var timerPlayer: MediaPlayer  // Short beep
    private lateinit var finishPlayer: MediaPlayer // GO! sound

    // ---------------- Quit State ----------------
    private var hasQuit = false
    private var quitterID: String? = null

    // ---------------- Activity Lifecycle ---------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnlineBattleMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // API 27+
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white) // optional: set nav bar color
        }

        observeConnectivity()

        // Init sounds
        correctSound = MediaPlayer.create(this, R.raw.correct)
        incorrectSound = MediaPlayer.create(this, R.raw.wrong)

        // Timer sounds
        timerPlayer = MediaPlayer.create(this, R.raw.start)
        finishPlayer = MediaPlayer.create(this, R.raw.finish)

        // Get current game ID
        gameId = OnlineGameData.gameModel.value?.roomID ?: return

        initController()   // Initialize game logic controller
        bindButtons()      // Bind number buttons and check button
        startListening()   // Start listening to Firestore updates

        // Quit button
        binding.signFlag.setOnClickListener {
            showQuitDialog()
        }

        setupCheckButton() // Listen for answer input changes
    }

    // ---------------- Setup Check Button ----------------
    private fun setupCheckButton() {
        toggleCheckButton() // Initial check button visibility
        binding.answerInput.addTextChangedListener {
            toggleCheckButton() // Update check button dynamically when input changes
        }
    }

    // ---------------- Quit Dialog ----------------
    private fun showQuitDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_quit, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)

        val continueBtn = view.findViewById<Button>(R.id.noButton)
        val finishBtn = view.findViewById<Button>(R.id.finishButton)

        continueBtn.setOnClickListener { dialog.dismiss() }

        finishBtn.setOnClickListener {
            quitterID = OnlineGameData.myID
            hasQuit = true
            dialog.dismiss()

            val model = gameModel ?: return@setOnClickListener
            val winnerID = if (quitterID == model.player1) model.player2 else model.player1

            // Update Firestore atomically
            db.collection("online_games").document(model.roomID).update(
                mapOf(
                    "quitterID" to quitterID,
                    "winnerID" to winnerID
                )
            ).addOnSuccessListener {
                navigateToResult()
            }
        }

        dialog.show()
    }

    // ---------------- Firestore Listener ----------------
    private fun startListening() {
        OnlineGameData.gameModel.value?.roomID?.let { id ->
            db.collection("online_games")
                .document(id)
                .addSnapshotListener { snap, _ ->
                    val game = snap?.toObject(GameModel::class.java) ?: return@addSnapshotListener

                    gameModel = game // ✅ Keep latest snapshot

                    // Update own & opponent scores/mistakes
                    if (OnlineGameData.myID == game.player1) {
                        opponentScore = game.p2Score
                        opponentMistakes = game.p2Mistakes
                        myScore = game.p1Score
                        myMistakes = game.p1Mistakes
                    } else {
                        opponentScore = game.p1Score
                        opponentMistakes = game.p1Mistakes
                        myScore = game.p2Score
                        myMistakes = game.p2Mistakes
                    }

                    updateUI()       // Update UI for both players
                    checkGameEnd()   // Check if game should finish

                    // Detect if someone quit
                    if (!hasNavigatedToResult && game.quitterID != null) {
                        quitterID = game.quitterID
                        hasQuit = true
                        navigateToResult()
                    }

                    // Start countdown overlay when game joined
                    if (game.gameStatus == GameStatus.JOINED && !countdownStarted) {
                        countdownStarted = true
                        startCountdownOverlay()
                    }
                }
        }
    }

    // ---------------- Countdown Overlay ----------------
    private fun startCountdownOverlay() {
        binding.countdownOverlay.visibility = View.VISIBLE

        binding.afterCountdownImage.visibility = View.GONE
        binding.countdownText.visibility = View.VISIBLE

        object : CountDownTimer(3000, 1000) {
            override fun onTick(ms: Long) {
                val seconds = (ms / 1000) + 1
                binding.countdownText.text = "$seconds"
                playTimerSound()

                if (seconds.toInt() == 1) {
                    binding.countdownText.visibility = View.GONE
                    binding.afterCountdownImage.visibility = View.VISIBLE
                    playFinishSound() // GO! sound
                }
            }

            override fun onFinish() {
                binding.countdownOverlay.visibility = View.GONE
                binding.checkButtonContainer.visibility = View.VISIBLE
                binding.problemRow.visibility = View.VISIBLE // show problem row
                controller.start()  // start generating problems
                startTimer()       // start main timer
            }
        }.start()
    }

    // ---------------- Main Timer ----------------
    private fun startTimer() {
        gameTimer = object : CountDownTimer(TOTAL_TIME, 1000) {
            override fun onTick(ms: Long) {
                val m = (ms / 1000) / 60
                val s = (ms / 1000) % 60
                binding.gameTimer.text = "$m:${String.format("%02d", s)}"
            }

            override fun onFinish() = navigateToResult()
        }.start()
    }

    private fun playTimerSound() {
        if (!SoundManager.isSoundEnabled(this)) return
        MediaPlayer.create(this, R.raw.start).apply {
            setOnCompletionListener { mp -> mp.release() }
            start()
        }
    }

    private fun playFinishSound() {
        if (!SoundManager.isSoundEnabled(this)) return
        MediaPlayer.create(this, R.raw.finish).apply {
            setOnCompletionListener { mp -> mp.release() }
            start()
        }
    }

    // ---------------- Buttons ----------------
    private fun bindButtons() {
        // Map buttons to number strings
        val map = mapOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2",
            binding.btn3 to "3", binding.btn4 to "4", binding.btn5 to "5",
            binding.btn6 to "6", binding.btn7 to "7", binding.btn8 to "8",
            binding.btn9 to "9", binding.btnMinus to "-"
        )
        map.forEach { it.key.setOnClickListener { _ -> controller.add(it.value) } }

        binding.btnDel.setOnClickListener { controller.delete() }
        binding.checkBtn.setOnClickListener { controller.check() }
    }

    // ---------------- Game Controller ----------------
    private fun initController() {
        controller = OnlineMathStormController(
            onProblem = { binding.problemText.text = it.text }, // Show problem
            onInput = { binding.answerInput.setText(it) },      // Update input
            onScore = {
                myScore = it
                updateMyData()
                updateUI()
                playCorrectSound()
            },
            onMistake = {
                myMistakes = it
                updateMyData()
                updateUI()
                playIncorrectSound()
            },
            onFinish = {
                updateMyData()
                checkGameEnd()
            }
        )
    }

    private fun playCorrectSound() {
        if (!SoundManager.isSoundEnabled(this)) return
        MediaPlayer.create(this, R.raw.correct).apply {
            setOnCompletionListener { mp -> mp.release() }
            start()
        }
    }

    private fun playIncorrectSound() {
        if (!SoundManager.isSoundEnabled(this)) return
        MediaPlayer.create(this, R.raw.wrong).apply {
            setOnCompletionListener { mp -> mp.release() }
            start()
        }
    }

    private fun playOpponentIncorrectSound() {
        if (!SoundManager.isSoundEnabled(this)) return
        MediaPlayer.create(this, R.raw.opponent_incorrect).apply {
            setVolume(0.3f, 0.3f) // lower volume
            setOnCompletionListener { mp -> mp.release() }
            start()
        }
    }

    // ---------------- Firestore Updates ----------------
    private fun updateMyData() {
        val model = gameModel ?: return
        val map = if (OnlineGameData.myID == model.player1)
            mapOf("p1Score" to myScore, "p1Mistakes" to myMistakes)
        else
            mapOf("p2Score" to myScore, "p2Mistakes" to myMistakes)

        db.collection("online_games").document(gameId).update(map)
    }

    // ---------------- UI Updates ----------------
    private fun updateUI() {
        // Update scores
        binding.gameYourScore.text = "$myScore"
        binding.gameOpponentScore.text = "$opponentScore"

        // Your mistakes
        val yourMistakesList = listOf(binding.yourMistake1, binding.yourMistake2, binding.yourMistake3)
        for (i in yourMistakesList.indices) {
            yourMistakesList[i].setImageResource(
                if (i < myMistakes) R.drawable.wrong_circle else R.drawable.circle_empty
            )
        }

        // Opponent mistakes
        val opponentMistakesList = listOf(binding.opponentMistake1, binding.opponentMistake2, binding.opponentMistake3)
        for (i in opponentMistakesList.indices) {
            opponentMistakesList[i].setImageResource(
                if (i < opponentMistakes) R.drawable.wrong_circle else R.drawable.circle_empty
            )
        }

        // Play sound if opponent just made a new mistake
        if (opponentMistakes > lastOpponentMistakes) {
            playOpponentIncorrectSound()
        }
        lastOpponentMistakes = opponentMistakes

        // Enable/disable number buttons if max mistakes reached
        val enabled = myMistakes < MAX_MISTAKES
        listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9,
            binding.btnMinus, binding.btnDel
        ).forEach { it.isEnabled = enabled }

        // Toggle check button visibility dynamically
        toggleCheckButton()

    }

    // ---------------- Game End Check ----------------
    private fun checkGameEnd() {
        if (myMistakes >= MAX_MISTAKES && opponentMistakes >= MAX_MISTAKES)
            navigateToResult()
    }

    // ---------------- Navigate to Result ----------------
    private fun navigateToResult() {
        if (hasNavigatedToResult) return
        hasNavigatedToResult = true

        gameTimer?.cancel()

        val model = gameModel ?: return
        val yourStatus: String
        val opponentStatus: String

        if (model.quitterID != null) {
            // The quitter loses, the other player wins
            if (OnlineGameData.myID == model.quitterID) {
                yourStatus = "LOSER"
                opponentStatus = "WINNER"
            } else {
                yourStatus = "WINNER"
                opponentStatus = "LOSER"
            }
        } else {
            // Normal case: highest score wins
            when {
                myScore > opponentScore -> {
                    yourStatus = "WINNER"
                    opponentStatus = "LOSER"
                }
                myScore < opponentScore -> {
                    yourStatus = "LOSER"
                    opponentStatus = "WINNER"
                }
                else -> {
                    yourStatus = "DRAW"
                    opponentStatus = "DRAW"
                }
            }
        }

        startActivity(
            Intent(this, OnlineResultBattleMathStormActivity::class.java)
                .putExtra("YOUR_STATUS", yourStatus)
                .putExtra("YOUR_SCORE", myScore)
                .putExtra("OPPONENT_STATUS", opponentStatus)
                .putExtra("OPPONENT_SCORE", opponentScore)
        )
        overridePendingTransition(
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
        finish()
    }

    // ---------------- Check Button Visibility ----------------
    private fun toggleCheckButton() {
        val hasInput = binding.answerInput.text.toString().isNotEmpty()
        val canCheck = hasInput && myMistakes < MAX_MISTAKES

        if (canCheck) {
            binding.checkBtnFrame.visibility = View.VISIBLE
            binding.checkBtnDisabled.visibility = View.GONE
        } else {
            binding.checkBtnFrame.visibility = View.GONE
            binding.checkBtnDisabled.visibility = View.VISIBLE
        }
    }

    override fun onBackPressed() {
        showQuitDialog() // Show the same dialog as the quit button
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
                        binding.offlineContainer.visibility = View.VISIBLE
                        binding.internetConnection.visibility = View.VISIBLE
                        binding.mainContent.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gameTimer?.cancel() // Safety check
        listener?.remove()  // ALWAYS remove Firestore listeners in onDestroy
    }
}