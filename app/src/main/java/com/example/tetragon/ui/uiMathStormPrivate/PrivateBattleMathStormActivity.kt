package com.example.tetragon.ui.uiMathStormPrivate

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
import com.example.tetragon.databinding.ActivityPrivateBattleMathStormBinding
import com.example.tetragon.gameModel.PrivateGameModel
import com.example.tetragon.gameModel.PrivateGameStatus
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.mathStormUtils.PrivateGameData
import com.example.tetragon.utils.mathStormUtils.PrivateMathStormController
import com.example.tetragon.utils.soundUtils.SoundManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlin.getValue

class PrivateBattleMathStormActivity : BaseActivity() {
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

    // 1. Change to Nullable
    private var gameTimer: CountDownTimer? = null
    private lateinit var binding: ActivityPrivateBattleMathStormBinding
    private lateinit var controller: PrivateMathStormController

    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private var myScore = 0
    private var myMistakes = 0
    private var opponentScore = 0
    private var opponentMistakes = 0

    private val MAX_MISTAKES = 3
    private val TOTAL_TIME = 3 * 60 * 1000L
    private lateinit var timer: CountDownTimer

    private var gameId = ""
    private var hasNavigatedToResult = false

    private var correctSound: MediaPlayer? = null
    private var incorrectSound: MediaPlayer? = null
    private var lastOpponentMistakes = 0
    private var countdownStarted = false
    private lateinit var timerPlayer: MediaPlayer
    private lateinit var finishPlayer: MediaPlayer
    private var hasQuit = false
    private var quitterID: String? = null // store which player pressed quit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivateBattleMathStormBinding.inflate(layoutInflater)
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

        // Timer and finish sounds
        timerPlayer = MediaPlayer.create(this, R.raw.start) // short beep sound
        finishPlayer = MediaPlayer.create(this, R.raw.finish)        // GO! sound

        gameId = PrivateGameData.gameModel.value?.gameID ?: return

        initController()
        bindButtons()
        startListening()

        binding.signFlag.setOnClickListener {
            showQuitDialog()
        }

        setupCheckButton()
    }

    private fun showQuitDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_quit, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)

        val continueBtn = view.findViewById<Button>(R.id.noButton)
        val finishBtn = view.findViewById<Button>(R.id.finishButton)

        continueBtn.setOnClickListener { dialog.dismiss() }

        finishBtn.setOnClickListener {
            quitterID = PrivateGameData.myID
            hasQuit = true
            dialog.dismiss()

            // Update Firestore so other player sees it
            PrivateGameData.gameModel.value?.gameID?.let { id ->
                Firebase.firestore.collection("private_games")
                    .document(id)
                    .update("quitterID", quitterID)
                    .addOnSuccessListener {
                        navigateToResult()
                    }
            }
        }

        dialog.show()
    }

    private fun navigateToResult() {
        if (hasNavigatedToResult) return
        hasNavigatedToResult = true

        // Kill the timer and listener immediately
        gameTimer?.cancel()
        listener?.remove()

        val yourStatus: String
        val opponentStatus: String

        if (hasQuit) {
            if (PrivateGameData.myID == quitterID) {
                yourStatus = "LOSER"
                opponentStatus = "WINNER"
            } else {
                yourStatus = "WINNER"
                opponentStatus = "LOSER"
            }
        } else {
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
            Intent(this, PrivateResultBattleMathStorm::class.java)
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

    private fun initController() {
        controller = PrivateMathStormController(
            onProblem = { binding.problemText.text = it.text },
            onInput = { binding.answerInput.setText(it) },
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

    private fun playIncorrectSound() {
        if (!SoundManager.isSoundEnabled(this)) return
        MediaPlayer.create(this, R.raw.wrong).apply {
            setOnCompletionListener { mp -> mp.release() }
            start()
        }
    }

    private fun playCorrectSound() {
        if (!SoundManager.isSoundEnabled(this)) return
        MediaPlayer.create(this, R.raw.correct).apply {
            setOnCompletionListener { mp -> mp.release() }
            start()
        }
    }

    private fun bindButtons() {
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

    private fun startListening() {
        PrivateGameData.gameModel.value?.gameID?.let { id ->
            Firebase.firestore.collection("private_games")
                .document(id)
                .addSnapshotListener { snap, _ ->
                    val game = snap?.toObject(PrivateGameModel::class.java) ?: return@addSnapshotListener

                    // Update opponent scores/mistakes
                    if (PrivateGameData.myID == "P1") {
                        opponentScore = game.p2Score
                        opponentMistakes = game.p2Mistakes
                    } else {
                        opponentScore = game.p1Score
                        opponentMistakes = game.p1Mistakes
                    }

                    updateUI()
                    checkGameEnd()

                    // ✅ Detect if someone quit
                    if (!hasNavigatedToResult && game.quitterID != null) {
                        quitterID = game.quitterID
                        hasQuit = true
                        navigateToResult()
                    }

                    // Countdown
                    if (game.gameStatus == PrivateGameStatus.JOINED && !countdownStarted) {
                        countdownStarted = true
                        startCountdownOverlay()
                    }
                }
        }
    }
    private fun startCountdownOverlay() {
        binding.countdownOverlay.visibility = View.VISIBLE
        binding.checkBtn.visibility = View.GONE  // hide buttons

        // Optional: show GO! image at the last second
        binding.afterCountdownImage.visibility = View.GONE
        binding.countdownText.visibility = View.VISIBLE

        object : CountDownTimer(3000, 1000) {
            override fun onTick(ms: Long) {
                val seconds = (ms / 1000) + 1
                binding.countdownText.text = "$seconds"

                // Play beep for each second
                playTimerSound()

                if (seconds.toInt() == 1) {
                    binding.countdownText.visibility = View.GONE
                    binding.afterCountdownImage.visibility = View.VISIBLE
                    playFinishSound()  // play GO! sound
                }
            }

            override fun onFinish() {
                binding.countdownOverlay.visibility = View.GONE
                binding.checkBtn.visibility = View.VISIBLE
                // ✅ Make problem row visible
                binding.problemRow.visibility = View.VISIBLE
                controller.start()   // start generating problems
                startTimer()        // start main game timer
            }
        }.start()
    }

    // ---------------- SOUND PLAYBACK ----------------
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

    private fun updateMyData() {
        val map = if (PrivateGameData.myID == "P1")
            mapOf("p1Score" to myScore, "p1Mistakes" to myMistakes)
        else
            mapOf("p2Score" to myScore, "p2Mistakes" to myMistakes)

        db.collection("private_games").document(gameId).update(map)
    }

    private fun updateUI() {
        // Update scores
        binding.gameYourScore.text = "$myScore"
        binding.gameOpponentScore.text = "$opponentScore"

        // Update your mistakes
        val yourMistakesList = listOf(binding.yourMistake1, binding.yourMistake2, binding.yourMistake3)
        for (i in yourMistakesList.indices) {
            yourMistakesList[i].setImageResource(
                if (i < myMistakes) R.drawable.wrong_circle else R.drawable.circle_empty
            )
        }

        // Update opponent mistakes
        val opponentMistakesList = listOf(binding.opponentMistake1, binding.opponentMistake2, binding.opponentMistake3)
        for (i in opponentMistakesList.indices) {
            opponentMistakesList[i].setImageResource(
                if (i < opponentMistakes) R.drawable.wrong_circle else R.drawable.circle_empty
            )
        }

        // Play sound if opponent just made a mistake
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

        // ✅ Dynamic CHECK button
        toggleCheckButton()
    }

    private fun playOpponentIncorrectSound() {
        if (!SoundManager.isSoundEnabled(this)) return
        MediaPlayer.create(this, R.raw.opponent_incorrect).apply {
            setVolume(0.3f, 0.3f) // lower volume, 30% of max
            setOnCompletionListener { mp -> mp.release() }
            start()
        }
    }

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

    private fun checkGameEnd() {
        if (myMistakes >= MAX_MISTAKES && opponentMistakes >= MAX_MISTAKES)
            navigateToResult()
    }

    private fun setupCheckButton() {
        // Initially check
        toggleCheckButton()

        // Listen to input changes
        binding.answerInput.addTextChangedListener {
            toggleCheckButton()
        }
    }

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
        // Instead of default behavior, show the quit dialog
        showQuitDialog()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        gameTimer?.cancel() // No longer crashes if null

        // Release persistent players
        timerPlayer.release()
        finishPlayer.release()
        correctSound?.release()
        incorrectSound?.release()
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
