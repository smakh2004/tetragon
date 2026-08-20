package com.tetragon.app.ui.uiMathStormOnline

import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.widget.addTextChangedListener
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityOnlineBattleMathStormBinding
import com.tetragon.app.gameModel.GameModel
import com.tetragon.app.gameModel.GameStatus
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.OnlineGameData
import com.tetragon.app.utils.mathStormUtils.OnlineMathStormController
import com.tetragon.app.utils.soundUtils.SoundManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class OnlineBattleMathStormActivity : BaseActivity() {

    // ---------------- Connectivity ----------------
    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ConnectivityViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ConnectivityViewModel(
                        AndroidConnectivityObserver(applicationContext)
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    // ---------------- UI & Controller ----------------
    private lateinit var binding: ActivityOnlineBattleMathStormBinding
    private lateinit var controller: OnlineMathStormController

    // ---------------- Firestore ----------------
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null
    private var gameModel: GameModel? = null

    // ---------------- Game State ----------------
    private var myScore = 0
    private var myMistakes = 0
    private var opponentScore = 0
    private var opponentMistakes = 0

    private val MAX_MISTAKES = 3
    private val TOTAL_TIME = 3 * 60 * 1000L

    private var gameTimer: CountDownTimer? = null
    private var countdownTimer: CountDownTimer? = null

    private var gameId = ""
    private var hasNavigatedToResult = false
    private var countdownStarted = false

    // ---------------- Opponent Reaction Trackers ----------------
    private var lastOpponentScore = -1
    private var lastOpponentMistakes = -1

    // ---------------- Quit State ----------------
    private var hasQuit = false
    private var quitterID: String? = null

    // ---------------- Rive Avatars ----------------
    private val vmInstances = mutableMapOf<Int, ViewModelInstance>()
    private val defaultFaces = mutableMapOf<Int, Float>()

    private var avatarsRequested = false
    private var myUserListener: ListenerRegistration? = null
    private var opponentUserListener: ListenerRegistration? = null

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
        binding = ActivityOnlineBattleMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeConnectivity()

        gameId = OnlineGameData.gameModel.value?.roomID ?: return

        initController()
        bindButtons()
        startListening()

        binding.signFlag.setOnClickListener { showQuitDialog() }
        setupCheckButton()
    }

    private fun setupCheckButton() {
        toggleCheckButton()
        binding.answerInput.addTextChangedListener { toggleCheckButton() }
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
            quitterID = OnlineGameData.myID
            hasQuit = true
            dialog.dismiss()

            val model = gameModel ?: run {
                navigateToResult()
                return@setOnClickListener
            }
            val winnerID = if (quitterID == model.player1) model.player2 else model.player1

            db.collection("online_games").document(model.roomID).update(
                mapOf(
                    "quitterID" to quitterID,
                    "winnerID" to winnerID
                )
            ).addOnSuccessListener {
                navigateToResult()
            }.addOnFailureListener {
                navigateToResult()
            }
        }

        dialog.show()
    }

    private fun startListening() {
        val id = OnlineGameData.gameModel.value?.roomID ?: return

        listener = db.collection("online_games")
            .document(id)
            .addSnapshotListener { snap, _ ->
                val game = snap?.toObject(GameModel::class.java) ?: return@addSnapshotListener

                gameModel = game

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

                loadAvatars(game)
                updateUI()
                checkGameEnd()

                if (!hasNavigatedToResult && !game.quitterID.isNullOrEmpty()) {
                    quitterID = game.quitterID
                    hasQuit = true
                    navigateToResult()
                }

                if (game.gameStatus == GameStatus.JOINED && !countdownStarted) {
                    countdownStarted = true
                    startCountdownOverlay()
                }
            }
    }

    // ==================== RIVE AVATARS ====================

    /** LEFT view = current user, RIGHT view = opponent. */
    private fun loadAvatars(game: GameModel) {
        if (avatarsRequested) return
        avatarsRequested = true

        val myUID = OnlineGameData.myID
        val opponentUID = if (myUID == game.player1) game.player2 else game.player1

        if (myUID.isNotEmpty()) {
            myUserListener?.remove()
            myUserListener = db.collection("users").document(myUID)
                .addSnapshotListener { snapshot, error ->
                    if (isFinishing || isDestroyed || error != null) return@addSnapshotListener
                    val name = snapshot?.getString("firstName") ?: getString(R.string.you_caps)
                    val config = snapshot?.get("avatarConfig") as? Map<*, *>
                    applyAvatarConfigToRive(binding.yourAvatar, config, name)
                }
        }

        if (!opponentUID.isNullOrEmpty()) {
            opponentUserListener?.remove()
            opponentUserListener = db.collection("users").document(opponentUID)
                .addSnapshotListener { snapshot, error ->
                    if (isFinishing || isDestroyed || error != null) return@addSnapshotListener
                    val name = snapshot?.getString("firstName")
                        ?: getString(R.string.opponent_caps)
                    val config = snapshot?.get("avatarConfig") as? Map<*, *>
                    applyAvatarConfigToRive(binding.opponentAvatar, config, name)
                }
        }
    }

    private fun applyAvatarConfigToRive(
        riveView: RiveAnimationView,
        config: Map<*, *>?,
        firstName: String,
        attempt: Int = 0
    ) {
        riveView.post {
            if (isFinishing || isDestroyed) return@post

            val riveController = riveView.controller
            val file = riveController.file
            val stateMachine = riveController.stateMachines.firstOrNull()

            if (file == null || stateMachine == null) {
                if (attempt < MAX_RIVE_ATTEMPTS) {
                    riveView.postDelayed({
                        applyAvatarConfigToRive(riveView, config, firstName, attempt + 1)
                    }, RIVE_RETRY_DELAY_MS)
                }
                return@post
            }

            try {
                val vm = file.getViewModelByName(VIEW_MODEL_NAME) ?: return@post

                val vmi = vm.createDefaultInstance()
                vmInstances[riveView.id] = vmi

                riveController.activeArtboard?.viewModelInstance = vmi
                riveController.stateMachines.forEach { it.viewModelInstance = vmi }

                // ---- Numbers: face / hair / glasses / hat / mustache / body ----
                val savedFace = (config?.get("face") as? Number)?.toFloat() ?: 1f
                defaultFaces[riveView.id] = savedFace

                listOf("face", "hair", "glasses", "hat", "mustache", "body").forEach { key ->
                    val num = if (key == "face") {
                        getCurrentBaseFace(riveView)
                    } else {
                        (config?.get(key) as? Number)?.toFloat() ?: 1f
                    }
                    setNumber(vmi, key, num)
                }

                // ---- Hat boolean ----
                val hatValue = (config?.get("hat") as? Number)?.toInt() ?: 1
                runCatching { vmi.getBooleanProperty("hatOn")?.value = (hatValue > 1) }

                // ---- Colors ----
                listOf(
                    "skinColor", "hairColor", "glassColor", "capColor",
                    "mustacheColor", "clothColor", "backgroundColor", "eyebrowColor", "eyeColor"
                ).forEach { propName ->
                    val hex = config?.get(propName) as? String ?: return@forEach
                    val colorInt = runCatching { Color.parseColor(hex) }.getOrNull()
                        ?: return@forEach
                    setColor(vmi, propName, colorInt)
                }

            } catch (_: Exception) { }
        }
    }

    private fun setNumber(vmi: ViewModelInstance, name: String, value: Float) {
        runCatching { vmi.getNumberProperty(name)?.value = value }
    }

    private fun setColor(vmi: ViewModelInstance, name: String, value: Int) {
        runCatching { vmi.getColorProperty(name)?.value = value }
    }

    /** Temporary reaction (11 or 10) for 900ms, then sets the continuous face (12, 13, 14, or default). */
    private fun reactWithFace(riveView: RiveAnimationView, face: Float) {
        setFace(riveView, face)
        riveView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                setFace(riveView, getCurrentBaseFace(riveView))
            }
        }, FACE_REACTION_MS)
    }

    private fun setFace(riveView: RiveAnimationView, face: Float) {
        val vmi = vmInstances[riveView.id] ?: return
        setNumber(vmi, "face", face)
    }

    /** Continuous face based on mistake count. */
    private fun getSadFaceForMistake(mistakes: Int): Float {
        return when (mistakes) {
            1 -> 12f
            2 -> 13f
            3 -> 14f
            else -> 14f
        }
    }

    /** Returns current persistent face (12/13/14 if player has mistakes, else default avatar face). */
    private fun getCurrentBaseFace(riveView: RiveAnimationView): Float {
        val mistakes = if (riveView.id == binding.yourAvatar.id) myMistakes else opponentMistakes
        return if (mistakes > 0) {
            getSadFaceForMistake(mistakes)
        } else {
            defaultFaces[riveView.id] ?: 1f
        }
    }

    // ==================== GAME FLOW ====================

    private fun startCountdownOverlay() {
        binding.countdownOverlay.visibility = View.VISIBLE
        binding.afterCountdownImage.visibility = View.GONE
        binding.countdownText.visibility = View.VISIBLE

        countdownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(ms: Long) {
                val seconds = (ms / 1000) + 1
                binding.countdownText.text = "$seconds"
                playTimerSound()

                if (seconds.toInt() == 1) {
                    binding.countdownText.visibility = View.GONE
                    binding.afterCountdownImage.visibility = View.VISIBLE
                    playFinishSound()
                }
            }

            override fun onFinish() {
                binding.countdownOverlay.visibility = View.GONE
                binding.checkButtonContainer.visibility = View.VISIBLE
                binding.problemRow.visibility = View.VISIBLE
                controller.start()
                startTimer()
            }
        }.start()
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

    private fun playSound(resId: Int, volume: Float = 1f) {
        if (!SoundManager.isSoundEnabled(this)) return
        MediaPlayer.create(this, resId)?.apply {
            setVolume(volume, volume)
            setOnCompletionListener { mp -> mp.release() }
            start()
        }
    }

    private fun playTimerSound() = playSound(R.raw.start)
    private fun playFinishSound() = playSound(R.raw.finish)
    private fun playCorrectSound() = playSound(R.raw.correct)
    private fun playIncorrectSound() = playSound(R.raw.wrong)
    private fun playOpponentIncorrectSound() = playSound(R.raw.opponent_incorrect, 0.3f)

    private fun bindButtons() {
        val map = mapOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2",
            binding.btn3 to "3", binding.btn4 to "4", binding.btn5 to "5",
            binding.btn6 to "6", binding.btn7 to "7", binding.btn8 to "8",
            binding.btn9 to "9", binding.btnMinus to "-"
        )
        map.forEach { entry -> entry.key.setOnClickListener { controller.add(entry.value) } }

        binding.btnDel.setOnClickListener { controller.delete() }
        binding.checkBtn.setOnClickListener { controller.check() }
    }

    private fun initController() {
        controller = OnlineMathStormController(
            onProblem = { binding.problemText.text = it.text },
            onInput = { binding.answerInput.setText(it) },
            onScore = {
                myScore = it
                updateMyData()
                updateUI()
                playCorrectSound()
                reactWithFace(binding.yourAvatar, FACE_HAPPY)
            },
            onMistake = {
                myMistakes = it
                updateMyData()
                updateUI()
                playIncorrectSound()
                reactWithFace(binding.yourAvatar, FACE_SAD)
            },
            onFinish = {
                updateMyData()
                checkGameEnd()
            }
        )
    }

    private fun updateMyData() {
        val model = gameModel ?: return
        val map = if (OnlineGameData.myID == model.player1)
            mapOf("p1Score" to myScore, "p1Mistakes" to myMistakes)
        else
            mapOf("p2Score" to myScore, "p2Mistakes" to myMistakes)

        db.collection("online_games").document(gameId).update(map)
    }

    private fun updateUI() {
        binding.gameYourScore.text = "$myScore"
        binding.gameOpponentScore.text = "$opponentScore"

        val yourMistakesList =
            listOf(binding.yourMistake1, binding.yourMistake2, binding.yourMistake3)
        for (i in yourMistakesList.indices) {
            yourMistakesList[i].setImageResource(
                if (i < myMistakes) R.drawable.wrong_circle else R.drawable.circle_empty
            )
        }

        val opponentMistakesList =
            listOf(binding.opponentMistake1, binding.opponentMistake2, binding.opponentMistake3)
        for (i in opponentMistakesList.indices) {
            opponentMistakesList[i].setImageResource(
                if (i < opponentMistakes) R.drawable.wrong_circle else R.drawable.circle_empty
            )
        }

        // ---- Opponent Reaction Sync ----
        if (lastOpponentScore != -1 && opponentScore > lastOpponentScore) {
            reactWithFace(binding.opponentAvatar, FACE_HAPPY)
        }
        lastOpponentScore = opponentScore

        if (lastOpponentMistakes != -1 && opponentMistakes > lastOpponentMistakes) {
            playOpponentIncorrectSound()
            reactWithFace(binding.opponentAvatar, FACE_SAD)
        }
        lastOpponentMistakes = opponentMistakes

        val enabled = myMistakes < MAX_MISTAKES
        listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9,
            binding.btnMinus, binding.btnDel
        ).forEach { it.isEnabled = enabled }

        toggleCheckButton()
    }

    private fun checkGameEnd() {
        if (myMistakes >= MAX_MISTAKES && opponentMistakes >= MAX_MISTAKES)
            navigateToResult()
    }

    private fun navigateToResult() {
        if (hasNavigatedToResult) return
        hasNavigatedToResult = true

        gameTimer?.cancel()
        countdownTimer?.cancel()

        val model = gameModel
        val effectiveQuitter = quitterID ?: model?.quitterID

        val yourStatus: String
        val opponentStatus: String

        if (!effectiveQuitter.isNullOrEmpty()) {
            if (OnlineGameData.myID == effectiveQuitter) {
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
            Intent(this, OnlineResultBattleMathStormActivity::class.java)
                .putExtra("YOUR_STATUS", yourStatus)
                .putExtra("YOUR_SCORE", myScore)
                .putExtra("OPPONENT_STATUS", opponentStatus)
                .putExtra("OPPONENT_SCORE", opponentScore)
                .putExtra("QUITTER_ID", effectiveQuitter)
        )
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
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
        showQuitDialog()
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
        gameTimer?.cancel()
        countdownTimer?.cancel()
        listener?.remove()
        listener = null
        myUserListener?.remove()
        myUserListener = null
        opponentUserListener?.remove()
        opponentUserListener = null
        vmInstances.clear()
        defaultFaces.clear()
    }
}