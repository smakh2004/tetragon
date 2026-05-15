package com.tetragon.app.ui.uiMathStorm

import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityMathStormBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.MathStormController
import com.tetragon.app.utils.soundUtils.SoundManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MathStormActivity : BaseActivity() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var binding: ActivityMathStormBinding
    private var isNavigatingToResult = false
    private var sessionHighestScore = 0

    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
            }
        }
    }

    // UI ELEMENTS
    private lateinit var inputEditText: EditText
    private lateinit var enabledButtonFrame: FrameLayout
    private lateinit var enabledButton: Button
    private lateinit var disabledButton: FrameLayout
    private lateinit var problemTextView: TextView
    private lateinit var countdownOverlay: FrameLayout
    private lateinit var countdownText: TextView
    private lateinit var afterCountdownImage: ImageView
    private lateinit var scoreText: TextView
    private lateinit var mistake1: ImageView
    private lateinit var mistake2: ImageView
    private lateinit var mistake3: ImageView
    private lateinit var timerText: TextView
    private lateinit var signFlag: ImageView
    private lateinit var squareImage: ImageView

    // SOUNDS
    private lateinit var timerPlayer: MediaPlayer
    private lateinit var finishPlayer: MediaPlayer
    private lateinit var wrongPlayer: MediaPlayer
    private lateinit var correctPlayer: MediaPlayer

    private lateinit var controller: MathStormController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        observeConnectivity()
        initViews()
        initSounds()
        initController()
        initButtons()

        signFlag.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

        startCountdownOverlay(3)
    }

    private fun initViews() {
        inputEditText = findViewById(R.id.editText)
        enabledButtonFrame = findViewById(R.id.enabledButtonFrame)
        enabledButton = findViewById(R.id.enabledButton)
        disabledButton = findViewById(R.id.disabledButtonFrame)
        problemTextView = findViewById(R.id.problemText)
        countdownOverlay = findViewById(R.id.countdownOverlay)
        countdownText = findViewById(R.id.countdownText)
        afterCountdownImage = findViewById(R.id.afterCountdownImage)
        signFlag = findViewById(R.id.signFlag)
        squareImage = findViewById(R.id.problemImage)
        scoreText = findViewById(R.id.scoreText)
        mistake1 = findViewById(R.id.mistake1)
        mistake2 = findViewById(R.id.mistake2)
        mistake3 = findViewById(R.id.mistake3)
        timerText = findViewById(R.id.timerText)
    }

    private fun initSounds() {
        timerPlayer = MediaPlayer.create(this, R.raw.start)
        finishPlayer = MediaPlayer.create(this, R.raw.finish)
        wrongPlayer = MediaPlayer.create(this, R.raw.wrong)
        correctPlayer = MediaPlayer.create(this, R.raw.correct)
    }

    private fun initController() {
        controller = MathStormController(
            onInputChanged = { input -> inputEditText.setText(input); toggleContinueButton(input.isNotEmpty()) },
            onProblemChanged = { problem -> problemTextView.text = problem.text },
            onScoreChanged = { score -> scoreText.text = score.toString(); if (score > sessionHighestScore) sessionHighestScore = score },
            onMistakeChanged = { mistakes -> updateMistakesUI(mistakes); if (mistakes > 0) playWrongSound() },
            onCorrectChanged = { if (it) playCorrectSound() },
            onCountdownTick = { seconds -> timerText.text = formatTime(seconds) },
            onQuizFinished = { score -> if (!isFinishing) navigateToResult(score) }
        )
    }

    private fun startCountdownOverlay(seconds: Int) {
        countdownOverlay.visibility = View.VISIBLE
        afterCountdownImage.visibility = View.GONE

        controller.startCountdown(
            seconds,
            tick = { sec ->
                if (!isFinishing) {
                    countdownText.text = sec.toString()
                    playTimerSound()
                }
            },
            finish = {
                if (!isFinishing) {
                    countdownText.visibility = View.GONE
                    afterCountdownImage.visibility = View.VISIBLE
                    playFinishSound()

                    // Critical safety: Use a handler check or check isFinishing inside delay
                    afterCountdownImage.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            countdownOverlay.visibility = View.GONE
                            afterCountdownImage.visibility = View.GONE
                            startQuiz()
                            squareImage.visibility = View.VISIBLE
                        }
                    }, 1000)
                }
            }
        )
    }

    private fun startQuiz() {
        controller.showNextProblem()
        controller.startQuizTimer()
    }

    private fun initButtons() {
        val buttonMap = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2", R.id.btn3 to "3",
            R.id.btn4 to "4", R.id.btn5 to "5", R.id.btn6 to "6", R.id.btn7 to "7",
            R.id.btn8 to "8", R.id.btn9 to "9", R.id.btnMinus to "-", R.id.btnDel to "DEL"
        )
        buttonMap.forEach { (id, value) ->
            findViewById<Button>(id).setOnClickListener {
                if (value == "DEL") controller.deleteInput() else controller.addInput(value)
            }
        }
        enabledButton.setOnClickListener { controller.checkAnswer() }
    }

    private fun showQuitBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_quit, null)
        dialog.setContentView(view)
        view.findViewById<Button>(R.id.noButton).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.finishButton).setOnClickListener {
            dialog.dismiss()
            navigateToResult(controller.getCurrentScore())
        }
        dialog.show()
    }

    private fun updateMistakesUI(count: Int) {
        val images = listOf(mistake1, mistake2, mistake3)
        images.forEachIndexed { index, image ->
            image.setImageResource(if (index < count) R.drawable.wrong_circle else R.drawable.circle_empty)
        }
    }

    private fun toggleContinueButton(hasInput: Boolean) {
        enabledButtonFrame.visibility = if (hasInput) View.VISIBLE else View.INVISIBLE
        disabledButton.visibility = if (hasInput) View.INVISIBLE else View.VISIBLE
    }

    private fun playTimerSound() { if (SoundManager.isSoundEnabled(this)) { if (timerPlayer.isPlaying) timerPlayer.seekTo(0); timerPlayer.start() } }
    private fun playFinishSound() { if (SoundManager.isSoundEnabled(this)) { if (finishPlayer.isPlaying) finishPlayer.seekTo(0); finishPlayer.start() } }
    private fun playWrongSound() { if (SoundManager.isSoundEnabled(this)) { if (wrongPlayer.isPlaying) wrongPlayer.seekTo(0); wrongPlayer.start() } }
    private fun playCorrectSound() { if (SoundManager.isSoundEnabled(this)) { if (correctPlayer.isPlaying) correctPlayer.seekTo(0); correctPlayer.start() } }

    private fun formatTime(seconds: Int): String = String.format("%d:%02d", seconds / 60, seconds % 60)

    private fun navigateToResult(score: Int) {
        if (isNavigatingToResult) return
        isNavigatingToResult = true
        controller.cancelQuizTimer()
        controller.cancelCountdown()

        val intent = Intent(this, ResultMathStormActivity::class.java).apply { putExtra("score", score) }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    override fun onDestroy() {
        // Essential cleanup to prevent crashes
        controller.cancelCountdown()
        controller.cancelQuizTimer()
        afterCountdownImage.removeCallbacks(null)

        if (isFinishing && sessionHighestScore > 0) saveScoreIfHigher(sessionHighestScore)

        timerPlayer.release()
        finishPlayer.release()
        wrongPlayer.release()
        correctPlayer.release()
        super.onDestroy()
    }

    private fun saveScoreIfHigher(newScore: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docRef = db.collection("users").document(uid).collection("games").document("MathStorm")
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentHigh = snapshot.getLong("highScore") ?: 0L
            if (newScore > currentHigh) transaction.set(docRef, mapOf("highScore" to newScore))
        }
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    binding.offlineContainer.visibility = if (isConnected) View.GONE else View.VISIBLE
                    binding.mainContent.visibility = if (isConnected) View.VISIBLE else View.GONE
                }
            }
        }
    }
}