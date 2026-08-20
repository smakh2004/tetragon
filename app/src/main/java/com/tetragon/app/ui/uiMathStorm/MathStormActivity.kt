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
    private var currentMistakes = 0

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

    // ---------------- Rive Avatar State ----------------
    private var playerViewModelInstance: ViewModelInstance? = null
    private var defaultFace: Float = 1f

    companion object {
        private const val MAX_RIVE_ATTEMPTS = 60
        private const val RIVE_RETRY_DELAY_MS = 50L
        private const val VIEW_MODEL_NAME = "ViewModel1"

        private const val FACE_HAPPY = 11f
        private const val FACE_SAD = 10f
        private const val FACE_REACTION_MS = 900L
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
        loadAndApplyAvatarConfig()

        binding.signFlag.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

        startCountdownOverlay(3)
    }

    // ==================== RIVE AVATAR & REACTIONS ====================

    private fun loadAndApplyAvatarConfig() {
        val uid = auth.currentUser?.uid ?: return

        userListener?.remove()
        userListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (isFinishing || isDestroyed || error != null) return@addSnapshotListener
                val config = snapshot?.get("avatarConfig") as? Map<*, *> ?: return@addSnapshotListener
                applyAvatarConfigToRive(config)
            }
    }

    private fun applyAvatarConfigToRive(config: Map<*, *>, attempt: Int = 0) {
        binding.playerAvatar.post {
            if (isFinishing || isDestroyed) return@post

            val riveController = binding.playerAvatar.controller
            val file = riveController.file
            val stateMachine = riveController.stateMachines.firstOrNull()

            if (file == null || stateMachine == null) {
                if (attempt < MAX_RIVE_ATTEMPTS) {
                    binding.playerAvatar.postDelayed({
                        applyAvatarConfigToRive(config, attempt + 1)
                    }, RIVE_RETRY_DELAY_MS)
                }
                return@post
            }

            try {
                val vm = file.getViewModelByName(VIEW_MODEL_NAME) ?: return@post
                val vmi = vm.createDefaultInstance()
                playerViewModelInstance = vmi

                riveController.activeArtboard?.viewModelInstance = vmi
                riveController.stateMachines.forEach { it.viewModelInstance = vmi }

                defaultFace = (config["face"] as? Number)?.toFloat() ?: 1f

                listOf("face", "hair", "glasses", "hat", "mustache", "body").forEach { key ->
                    val num = if (key == "face") {
                        getCurrentBaseFace()
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
            } catch (e: Exception) {
                Log.e("MathStorm", "Error binding Rive avatar: ${e.message}")
            }
        }
    }

    private fun setNumber(vmi: ViewModelInstance, name: String, value: Float) {
        runCatching { vmi.getNumberProperty(name)?.value = value }
    }

    private fun setColor(vmi: ViewModelInstance, name: String, value: Int) {
        runCatching { vmi.getColorProperty(name)?.value = value }
    }

    private fun reactWithFace(face: Float) {
        setFace(face)
        binding.playerAvatar.postDelayed({
            if (!isFinishing && !isDestroyed) {
                setFace(getCurrentBaseFace())
            }
        }, FACE_REACTION_MS)
    }

    private fun setFace(face: Float) {
        val vmi = playerViewModelInstance ?: return
        setNumber(vmi, "face", face)
    }

    private fun getSadFaceForMistake(mistakesCount: Int): Float {
        return when (mistakesCount) {
            1 -> 12f
            2 -> 13f
            3 -> 14f
            else -> 14f
        }
    }

    private fun getCurrentBaseFace(): Float {
        return if (currentMistakes > 0) {
            getSadFaceForMistake(currentMistakes)
        } else {
            defaultFace
        }
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
                toggleContinueButton(input.isNotEmpty())
            },
            onProblemChanged = { problem ->
                binding.problemText.text = problem.text
            },
            onScoreChanged = { score ->
                binding.scoreText.text = score.toString()
                if (score > sessionHighestScore) sessionHighestScore = score
            },
            onMistakeChanged = { mistakes ->
                currentMistakes = mistakes
                updateMistakesUI(mistakes)
                if (mistakes > 0) {
                    playWrongSound()
                    reactWithFace(FACE_SAD)
                }
            },
            onCorrectChanged = { isCorrect ->
                if (isCorrect) {
                    playCorrectSound()
                    reactWithFace(FACE_HAPPY)
                }
            },
            onCountdownTick = { seconds ->
                binding.timerText.text = formatTime(seconds)
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
        binding.problemImage.visibility = View.VISIBLE
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

    private fun updateMistakesUI(count: Int) {
        val images = listOf(binding.mistake1, binding.mistake2, binding.mistake3)
        images.forEachIndexed { index, image ->
            image.setImageResource(if (index < count) R.drawable.wrong_circle else R.drawable.circle_empty)
        }
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

    private fun formatTime(seconds: Int): String = String.format("%d:%02d", seconds / 60, seconds % 60)

    private fun navigateToResult(score: Int) {
        if (isNavigatingToResult) return
        isNavigatingToResult = true
        controller.cancelQuizTimer()
        controller.cancelCountdown()

        val intent = Intent(this, ResultMathStormActivity::class.java).apply {
            putExtra("score", score)
            putExtra("questionsAnswered", questionsAnswered)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    override fun onDestroy() {
        controller.cancelCountdown()
        controller.cancelQuizTimer()
        binding.afterCountdownImage.removeCallbacks(null)

        if (isFinishing && sessionHighestScore > 0) {
            saveScoreIfHigher(sessionHighestScore)
        }

        userListener?.remove()
        userListener = null
        playerViewModelInstance = null

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