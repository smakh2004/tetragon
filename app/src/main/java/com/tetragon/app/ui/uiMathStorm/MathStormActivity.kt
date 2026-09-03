package com.tetragon.app.ui.uiMathStorm

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityMathStormBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.MathStormController
import com.tetragon.app.utils.soundUtils.SoundManager
import kotlinx.coroutines.launch

class MathStormActivity : BaseActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var binding: ActivityMathStormBinding

    private var isNavigatingToResult = false
    private var sessionHighestScore = 0
    private var questionsAnswered = 0
    private var correctAnswers = 0

    private var userListener: ListenerRegistration? = null

    // ---------------- Connectivity ----------------
    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
            }
        }
    }

    // ---------------- Sound Players ----------------
    private lateinit var timerPlayer: MediaPlayer
    private lateinit var finishPlayer: MediaPlayer
    private lateinit var wrongPlayer: MediaPlayer
    private lateinit var correctPlayer: MediaPlayer

    // ---------------- Controller ----------------
    private lateinit var controller: MathStormController

    // ---------------- Rive top bar state ----------------
    private var topBarVmi: ViewModelInstance? = null

    /** Always 1 — the saved avatarConfig no longer decides the neutral face. */
    private val defaultFace: Float = FACE_DEFAULT

    // values produced before the Rive file finishes loading
    private var pendingTimerText: String? = null
    private var pendingScore: Int? = null
    private var pendingConfig: Map<*, *>? = null

    companion object {
        private const val MAX_RIVE_ATTEMPTS = 60
        private const val RIVE_RETRY_DELAY_MS = 50L
        private const val VIEW_MODEL_NAME = "ViewModel1"

        // Rive ViewModel property names
        private const val PROP_TIMER = "timer"
        private const val PROP_SCORE = "score"
        private const val PROP_SCORE_TEXT = "scoreText"

        private const val FACE_HAPPY = 11f
        private const val FACE_SAD = 12f
        private const val FACE_REACTION_MS = 900L

        /** Neutral face is always 1 — never taken from the saved avatarConfig. */
        private const val FACE_DEFAULT = 1f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeConnectivity()
        initSounds()
        initController()
        initButtons()

        // bind the Rive view model as early as possible so timer/score have a target
        bindTopBarRive()
        loadAndApplyAvatarConfig()

        binding.signFlag.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

        startCountdownOverlay(3)
    }

    // ==================== RIVE TOP BAR ====================

    private fun loadAndApplyAvatarConfig() {
        val uid = auth.currentUser?.uid ?: return

        userListener?.remove()
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (isFinishing || isDestroyed || error != null) return@addSnapshotListener
                val config = snapshot?.get("avatarConfig") as? Map<*, *> ?: return@addSnapshotListener
                pendingConfig = config
                bindTopBarRive()
            }
    }

    /**
     * Creates (once) the ViewModel1 instance on the top-bar artboard and pushes
     * everything we currently know: avatar config, timer string, score, score label.
     * Retries while the .riv file is still loading.
     */
    private fun bindTopBarRive(attempt: Int = 0) {
        binding.topBarRive.post {
            if (isFinishing || isDestroyed) return@post

            val riveController = binding.topBarRive.controller
            val file = riveController.file
            val stateMachine = riveController.stateMachines.firstOrNull()

            if (file == null || stateMachine == null) {
                if (attempt < MAX_RIVE_ATTEMPTS) {
                    binding.topBarRive.postDelayed(
                        { bindTopBarRive(attempt + 1) },
                        RIVE_RETRY_DELAY_MS
                    )
                }
                return@post
            }

            try {
                val vmi = topBarVmi ?: run {
                    val vm = file.getViewModelByName(VIEW_MODEL_NAME) ?: return@post
                    val created = vm.createDefaultInstance()
                    riveController.activeArtboard?.viewModelInstance = created
                    riveController.stateMachines.forEach { it.viewModelInstance = created }
                    topBarVmi = created
                    created
                }

                // ---- avatar look ----
                pendingConfig?.let { config ->
                    listOf("face", "hair", "glasses", "hat", "mustache", "body").forEach { key ->
                        val num = if (key == "face") {
                            // face is never taken from Firestore
                            defaultFace
                        } else {
                            (config[key] as? Number)?.toFloat() ?: 1f
                        }
                        setNumber(vmi, key, num)
                    }

                    val hatValue = (config["hat"] as? Number)?.toInt() ?: 1
                    runCatching { vmi.getBooleanProperty("hatOn")?.value = (hatValue > 1) }

                    listOf(
                        "skinColor", "hairColor", "glassColor", "capColor",
                        "mustacheColor", "clothColor", "backgroundColor", "eyebrowColor", "eyeColor"
                    ).forEach { propName ->
                        val hex = config[propName] as? String ?: return@forEach
                        val colorInt = runCatching { Color.parseColor(hex) }.getOrNull() ?: return@forEach
                        setColor(vmi, propName, colorInt)
                    }
                }

                // ---- localized "score" label (en / ru / uz via resources) ----
                setString(vmi, PROP_SCORE_TEXT, getString(R.string.ms_score_label))

                // ---- flush anything produced before binding ----
                pendingTimerText?.let { setString(vmi, PROP_TIMER, it) }
                pendingScore?.let { setNumber(vmi, PROP_SCORE, it.toFloat()) }

            } catch (e: Exception) {
                Log.e("MathStorm", "Error binding Rive top bar: ${e.message}")
            }
        }
    }

    private fun setNumber(vmi: ViewModelInstance, name: String, value: Float) {
        runCatching { vmi.getNumberProperty(name)?.value = value }
    }

    private fun setString(vmi: ViewModelInstance, name: String, value: String) {
        runCatching { vmi.getStringProperty(name)?.value = value }
    }

    private fun setColor(vmi: ViewModelInstance, name: String, value: Int) {
        runCatching { vmi.getColorProperty(name)?.value = value }
    }

    // ---- data pushed into the Rive top bar ----

    private fun setTimerText(text: String) {
        pendingTimerText = text
        val vmi = topBarVmi ?: return
        setString(vmi, PROP_TIMER, text)
    }

    private fun setScore(score: Int) {
        pendingScore = score
        val vmi = topBarVmi ?: return
        setNumber(vmi, PROP_SCORE, score.toFloat())
    }

    private fun reactWithFace(face: Float) {
        setFace(face)
        binding.topBarRive.postDelayed({
            if (!isFinishing && !isDestroyed) setFace(defaultFace)
        }, FACE_REACTION_MS)
    }

    private fun setFace(face: Float) {
        val vmi = topBarVmi ?: return
        setNumber(vmi, "face", face)
    }

    // ==================== ANSWER BOX ====================

    /**
     * Mirrors the current input into the blue answer box in the problem row.
     * The box is wrap_content with a 56dp minimum, so it grows with the digits.
     */
    private fun renderAnswerBox(input: String) {
        binding.answerBox.text = input
    }

    // ==================== SOUNDS & CONTROLLER ====================

    private fun initSounds() {
        timerPlayer = MediaPlayer.create(this, R.raw.start)
        finishPlayer = MediaPlayer.create(this, R.raw.finish)
        wrongPlayer = MediaPlayer.create(this, R.raw.wrong)
        correctPlayer = MediaPlayer.create(this, R.raw.correct)
    }

    private fun initController() {
        controller = MathStormController(
            onInputChanged = { input ->
                binding.editText.setText(input)
                renderAnswerBox(input)
                toggleContinueButton(input.isNotEmpty())
            },
            onProblemChanged = { problem ->
                binding.problemText.text = problem.text
                // new problem -> empty box
                renderAnswerBox("")
            },
            onScoreChanged = { score ->
                setScore(score)
                if (score > sessionHighestScore) sessionHighestScore = score
            },
            onCorrectChanged = { isCorrect ->
                if (isCorrect) {
                    correctAnswers++
                    playCorrectSound()
                    reactWithFace(FACE_HAPPY)
                } else {
                    // wrong answer: feedback only, nothing is counted
                    playWrongSound()
                    reactWithFace(FACE_SAD)
                }
            },
            onCountdownTick = { seconds ->
                setTimerText(formatTime(seconds))
            },
            onQuizFinished = { score ->
                if (!isFinishing) navigateToResult(score)
            }
        )
    }

    private fun startCountdownOverlay(seconds: Int) {
        binding.countdownOverlay.visibility = View.VISIBLE
        binding.afterCountdownImage.visibility = View.GONE

        controller.startCountdown(
            seconds,
            tick = { sec ->
                if (!isFinishing) {
                    binding.countdownText.text = sec.toString()
                    playTimerSound()
                }
            },
            finish = {
                if (!isFinishing) {
                    binding.countdownText.visibility = View.GONE
                    binding.afterCountdownImage.visibility = View.VISIBLE
                    playFinishSound()

                    binding.afterCountdownImage.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            binding.countdownOverlay.visibility = View.GONE
                            binding.afterCountdownImage.visibility = View.GONE
                            startQuiz()
                        }
                    }, 1000)
                }
            }
        )
    }

    private fun startQuiz() {
        binding.answerBox.visibility = View.VISIBLE
        controller.showNextProblem()
        controller.startQuizTimer()
    }

    private fun initButtons() {
        val buttonMap = mapOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2", binding.btn3 to "3",
            binding.btn4 to "4", binding.btn5 to "5", binding.btn6 to "6", binding.btn7 to "7",
            binding.btn8 to "8", binding.btn9 to "9", binding.btnMinus to "-", binding.btnDel to "DEL"
        )
        buttonMap.forEach { (button, value) ->
            button.setOnClickListener {
                if (value == "DEL") controller.deleteInput() else controller.addInput(value)
            }
        }
        binding.enabledButton.setOnClickListener {
            questionsAnswered++
            controller.checkAnswer()
        }
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

    private fun toggleContinueButton(hasInput: Boolean) {
        binding.enabledButtonFrame.visibility = if (hasInput) View.VISIBLE else View.INVISIBLE
        binding.disabledButtonFrame.visibility = if (hasInput) View.INVISIBLE else View.VISIBLE
    }

    private fun playTimerSound() {
        if (SoundManager.isSoundEnabled(this)) {
            if (timerPlayer.isPlaying) timerPlayer.seekTo(0)
            timerPlayer.start()
        }
    }

    private fun playFinishSound() {
        if (SoundManager.isSoundEnabled(this)) {
            if (finishPlayer.isPlaying) finishPlayer.seekTo(0)
            finishPlayer.start()
        }
    }

    private fun playWrongSound() {
        if (SoundManager.isSoundEnabled(this)) {
            if (wrongPlayer.isPlaying) wrongPlayer.seekTo(0)
            wrongPlayer.start()
        }
    }

    private fun playCorrectSound() {
        if (SoundManager.isSoundEnabled(this)) {
            if (correctPlayer.isPlaying) correctPlayer.seekTo(0)
            correctPlayer.start()
        }
    }

    private fun formatTime(seconds: Int): String =
        String.format("%02d:%02d", seconds / 60, seconds % 60)

    private fun navigateToResult(score: Int) {
        if (isNavigatingToResult) return
        isNavigatingToResult = true
        controller.cancelQuizTimer()
        controller.cancelCountdown()

        val intent = Intent(this, ResultMathStormActivity::class.java).apply {
            putExtra("score", score)
            putExtra("questionsAnswered", questionsAnswered)
            putExtra("correctAnswers", correctAnswers)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    override fun onDestroy() {
        controller.cancelCountdown()
        controller.cancelQuizTimer()
        binding.afterCountdownImage.removeCallbacks(null)
        binding.topBarRive.removeCallbacks(null)

        if (isFinishing && sessionHighestScore > 0) {
            saveScoreIfHigher(sessionHighestScore)
        }

        userListener?.remove()
        userListener = null
        topBarVmi = null

        timerPlayer.release()
        finishPlayer.release()
        wrongPlayer.release()
        correctPlayer.release()
        super.onDestroy()
    }

    private fun saveScoreIfHigher(newScore: Int) {
        val uid = auth.currentUser?.uid ?: return
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