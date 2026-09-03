package com.tetragon.app.ui.uiMathStormOnline

import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
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

    /**
     * The countdown overlay is driven by explicit posted steps instead of a CountDownTimer.
     * CountDownTimer's first tick arrives one interval AFTER start and its remaining-ms value
     * drifts (1990, 990, ...), so integer division could show the same number twice or skip one.
     */
    private val countdownHandler = Handler(Looper.getMainLooper())

    private var gameId = ""
    private var hasNavigatedToResult = false
    private var countdownStarted = false

    // ---------------- Opponent Reaction Trackers ----------------
    private var lastOpponentScore = -1
    private var lastOpponentMistakes = -1

    // ---------------- Quit State ----------------
    private var hasQuit = false
    private var quitterID: String? = null

    // ---------------- Sound ----------------
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<Int, Int>()

    // ---------------- Rive top bar (ViewModel2) ----------------
    private var battleVmi: ViewModelInstance? = null
    private val mySlot = PlayerSlot(PATH_PLAYER_ONE)
    private val opponentSlot = PlayerSlot(PATH_PLAYER_TWO)

    private var pendingTimerText: String? = null
    private var myAvatarConfig: Map<*, *>? = null
    private var opponentAvatarConfig: Map<*, *>? = null

    private var avatarsRequested = false
    private var myUserListener: ListenerRegistration? = null
    private var opponentUserListener: ListenerRegistration? = null

    companion object {
        private const val MAX_RIVE_ATTEMPTS = 60
        private const val RIVE_RETRY_DELAY_MS = 50L

        /** Countdown overlay timings. */
        private const val COUNTDOWN_START = 3
        private const val COUNTDOWN_STEP_MS = 1000L
        private const val GO_DURATION_MS = 800L

        /** Root view model bound to the top-bar artboard. */
        private const val BATTLE_VIEW_MODEL_NAME = "ViewModel2"

        /** Nested ViewModel1 instances inside ViewModel2. */
        private const val PATH_PLAYER_ONE = "playerOne"
        private const val PATH_PLAYER_TWO = "playerTwo"

        private const val PROP_TIMER = "timer"
        private const val PROP_SCORE = "score"
        private const val PROP_SCORE_TEXT = "scoreText"
        private const val PROP_MISTAKES = "mistakes"
        private const val PROP_FACE = "face"

        private const val FACE_HAPPY = 11f
        private const val FACE_SAD = 10f
        private const val FACE_REACTION_MS = 900L

        /** Neutral face is always 1 — never taken from the saved avatarConfig. */
        private const val FACE_DEFAULT = 1f

        private val AVATAR_NUMBERS =
            listOf("face", "hair", "glasses", "hat", "mustache", "body")

        private val AVATAR_COLORS = listOf(
            "skinColor", "hairColor", "glassColor", "capColor",
            "mustacheColor", "clothColor", "backgroundColor", "eyebrowColor", "eyeColor"
        )
    }

    /**
     * One half of the top bar. Writes go to the nested ViewModel1 instance when the runtime
     * hands one back, otherwise they fall back to path access on the root ("playerOne/score").
     *
     * While a reaction (happy/sad) is playing the slot refuses to overwrite `face`, so the
     * Firestore snapshot that echoes back our own write can no longer cut the animation short.
     */
    private inner class PlayerSlot(val path: String) {
        var vmi: ViewModelInstance? = null
        val defaultFace: Float = FACE_DEFAULT
        var mistakeCount: Int = 0

        private var reacting = false
        private var reactionEnd: Runnable? = null

        fun number(name: String, value: Float) {
            val direct = vmi
            if (direct != null) {
                runCatching { direct.getNumberProperty(name)?.value = value }
            } else {
                battleVmi?.let {
                    runCatching { it.getNumberProperty("$path/$name")?.value = value }
                }
            }
        }

        fun string(name: String, value: String) {
            val direct = vmi
            if (direct != null) {
                runCatching { direct.getStringProperty(name)?.value = value }
            } else {
                battleVmi?.let {
                    runCatching { it.getStringProperty("$path/$name")?.value = value }
                }
            }
        }

        fun boolean(name: String, value: Boolean) {
            val direct = vmi
            if (direct != null) {
                runCatching { direct.getBooleanProperty(name)?.value = value }
            } else {
                battleVmi?.let {
                    runCatching { it.getBooleanProperty("$path/$name")?.value = value }
                }
            }
        }

        fun color(name: String, value: Int) {
            val direct = vmi
            if (direct != null) {
                runCatching { direct.getColorProperty(name)?.value = value }
            } else {
                battleVmi?.let {
                    runCatching { it.getColorProperty("$path/$name")?.value = value }
                }
            }
        }

        fun applyConfig(config: Map<*, *>?) {
            AVATAR_NUMBERS.forEach { key ->
                if (key == "face") {
                    // face always starts at 1; never read from the saved config,
                    // and never overwrite an in-flight reaction
                    if (!reacting) number(key, baseFace())
                } else {
                    number(key, (config?.get(key) as? Number)?.toFloat() ?: 1f)
                }
            }

            val hatValue = (config?.get("hat") as? Number)?.toInt() ?: 1
            boolean("hatOn", hatValue > 1)

            AVATAR_COLORS.forEach { propName ->
                val hex = config?.get(propName) as? String ?: return@forEach
                val colorInt = runCatching { Color.parseColor(hex) }.getOrNull() ?: return@forEach
                color(propName, colorInt)
            }
        }

        /** Pushes the mistake count into ViewModel1.mistakes and refreshes the persistent face. */
        fun pushMistakes(count: Int) {
            mistakeCount = count
            number(PROP_MISTAKES, count.toFloat())
            if (!reacting) number(PROP_FACE, baseFace())
        }

        /** Persistent face: 12/13/14 once the player has mistakes, otherwise the default face (1). */
        fun baseFace(): Float = when {
            mistakeCount <= 0 -> defaultFace
            mistakeCount == 1 -> 12f
            mistakeCount == 2 -> 13f
            else -> 14f
        }

        /** Temporary reaction (happy/sad) for 900ms, then back to the persistent face. */
        fun react(face: Float) {
            reactionEnd?.let { binding.topBarRive.removeCallbacks(it) }
            reacting = true
            number(PROP_FACE, face)

            val end = Runnable {
                reacting = false
                reactionEnd = null
                if (!isFinishing && !isDestroyed) number(PROP_FACE, baseFace())
            }
            reactionEnd = end
            binding.topBarRive.postDelayed(end, FACE_REACTION_MS)
        }

        fun cancelReaction() {
            reactionEnd?.let { binding.topBarRive.removeCallbacks(it) }
            reactionEnd = null
            reacting = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityOnlineBattleMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initSounds()
        observeConnectivity()

        gameId = OnlineGameData.gameModel.value?.roomID ?: return

        initController()
        bindButtons()

        // bind the Rive view model as early as possible so timer/scores have a target
        bindTopBarRive()
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

    // ==================== RIVE TOP BAR ====================

    /**
     * Binds ViewModel2 to the top-bar artboard and resolves the two nested ViewModel1
     * instances. Retries while the .riv file is still loading, then flushes everything
     * we already know (timer, scores, mistakes, avatar configs, localized labels).
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
                if (battleVmi == null) {
                    val vm = file.getViewModelByName(BATTLE_VIEW_MODEL_NAME) ?: return@post
                    val root = vm.createDefaultInstance()

                    riveController.activeArtboard?.viewModelInstance = root
                    riveController.stateMachines.forEach { it.viewModelInstance = root }
                    battleVmi = root

                    mySlot.vmi =
                        runCatching { root.getInstanceProperty(PATH_PLAYER_ONE) }.getOrNull()
                    opponentSlot.vmi =
                        runCatching { root.getInstanceProperty(PATH_PLAYER_TWO) }.getOrNull()
                }

                // ---- localized "score" label (en / ru / uz via resources) ----
                val label = getString(R.string.ms_score_label)
                mySlot.string(PROP_SCORE_TEXT, label)
                opponentSlot.string(PROP_SCORE_TEXT, label)

                // ---- flush anything produced before binding ----
                myAvatarConfig?.let { mySlot.applyConfig(it) }
                opponentAvatarConfig?.let { opponentSlot.applyConfig(it) }
                pendingTimerText?.let { setTimerText(it) }
                pushScoresAndMistakes()

            } catch (e: Exception) {
                Log.e("BattleMathStorm", "Error binding Rive top bar: ${e.message}")
            }
        }
    }

    private fun setTimerText(text: String) {
        pendingTimerText = text
        battleVmi?.let { runCatching { it.getStringProperty(PROP_TIMER)?.value = text } }
    }

    private fun pushScoresAndMistakes() {
        mySlot.number(PROP_SCORE, myScore.toFloat())
        opponentSlot.number(PROP_SCORE, opponentScore.toFloat())
        mySlot.pushMistakes(myMistakes)
        opponentSlot.pushMistakes(opponentMistakes)
    }

    // ==================== ANSWER BOX ====================

    /**
     * Mirrors the current input into the blue answer box in the problem row.
     * The box is wrap_content with an ms_answer_box minimum, so it grows with the digits.
     */
    private fun renderAnswerBox(input: String) {
        binding.answerBox.text = input
    }

    // ==================== AVATAR CONFIG ====================

    /** playerOne = current user, playerTwo = opponent. */
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
                    myAvatarConfig = snapshot?.get("avatarConfig") as? Map<*, *>
                    mySlot.applyConfig(myAvatarConfig)
                }
        }

        if (!opponentUID.isNullOrEmpty()) {
            opponentUserListener?.remove()
            opponentUserListener = db.collection("users").document(opponentUID)
                .addSnapshotListener { snapshot, error ->
                    if (isFinishing || isDestroyed || error != null) return@addSnapshotListener
                    opponentAvatarConfig = snapshot?.get("avatarConfig") as? Map<*, *>
                    opponentSlot.applyConfig(opponentAvatarConfig)
                }
        }
    }

    // ==================== GAME FLOW ====================

    /**
     * 3 -> 2 -> 1 -> GO, each frame set explicitly and posted one second apart.
     * Nothing is derived from a drifting remaining-ms value, so no number can be
     * skipped or repeated when the device is briefly busy.
     */
    private fun startCountdownOverlay() {
        binding.countdownOverlay.visibility = View.VISIBLE
        binding.afterCountdownImage.visibility = View.GONE
        binding.countdownText.visibility = View.VISIBLE

        countdownHandler.removeCallbacksAndMessages(null)
        showCountdownStep(COUNTDOWN_START)
    }

    private fun showCountdownStep(value: Int) {
        if (isFinishing || isDestroyed || hasNavigatedToResult) return

        if (value > 0) {
            binding.countdownText.text = value.toString()
            playTimerSound()
            countdownHandler.postDelayed({ showCountdownStep(value - 1) }, COUNTDOWN_STEP_MS)
            return
        }

        // 0 -> the "GO" frame
        binding.countdownText.visibility = View.GONE
        binding.afterCountdownImage.visibility = View.VISIBLE
        playFinishSound()
        countdownHandler.postDelayed({ finishCountdown() }, GO_DURATION_MS)
    }

    private fun finishCountdown() {
        if (isFinishing || isDestroyed || hasNavigatedToResult) return

        binding.countdownOverlay.visibility = View.GONE
        binding.checkButtonContainer.visibility = View.VISIBLE
        binding.problemRow.visibility = View.VISIBLE
        controller.start()
        startTimer()
    }

    private fun startTimer() {
        gameTimer = object : CountDownTimer(TOTAL_TIME, 1000) {
            override fun onTick(ms: Long) {
                val m = (ms / 1000) / 60
                val s = (ms / 1000) % 60
                setTimerText(String.format("%d:%02d", m, s))
            }

            override fun onFinish() {
                setTimerText("0:00")
                navigateToResult()
            }
        }.start()
    }

    // ==================== SOUND ====================

    /**
     * SoundPool decodes every clip once, at startup, and plays it off the UI thread.
     * MediaPlayer.create() used to prepare the file synchronously on each answer, which
     * blocked the main thread long enough to visibly delay the Rive face reaction.
     */
    private fun initSounds() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()
            .also { pool ->
                listOf(
                    R.raw.start,
                    R.raw.finish,
                    R.raw.correct,
                    R.raw.wrong,
                    R.raw.opponent_incorrect
                ).forEach { res -> soundIds[res] = pool.load(this, res, 1) }
            }
    }

    private fun playSound(resId: Int, volume: Float = 1f) {
        if (!SoundManager.isSoundEnabled(this)) return
        val pool = soundPool ?: return
        val id = soundIds[resId] ?: return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    private fun playTimerSound() = playSound(R.raw.start)
    private fun playFinishSound() = playSound(R.raw.finish)
    private fun playCorrectSound() = playSound(R.raw.correct)
    private fun playIncorrectSound() = playSound(R.raw.wrong)
    private fun playOpponentIncorrectSound() = playSound(R.raw.opponent_incorrect, 0.3f)

    // ==================== INPUT ====================

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
            onProblem = {
                binding.problemText.text = it.text
                // new problem -> empty box
                renderAnswerBox("")
            },
            onInput = {
                binding.answerInput.setText(it)
                renderAnswerBox(it)
            },
            onScore = {
                myScore = it
                // react first: nothing blocking runs before the face is pushed to Rive
                mySlot.react(FACE_HAPPY)
                updateUI()
                playCorrectSound()
                updateMyData()
            },
            onMistake = {
                myMistakes = it
                mySlot.react(FACE_SAD)
                updateUI()
                playIncorrectSound()
                updateMyData()
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
        pushScoresAndMistakes()

        // ---- Opponent Reaction Sync ----
        if (lastOpponentScore != -1 && opponentScore > lastOpponentScore) {
            opponentSlot.react(FACE_HAPPY)
        }
        lastOpponentScore = opponentScore

        if (lastOpponentMistakes != -1 && opponentMistakes > lastOpponentMistakes) {
            opponentSlot.react(FACE_SAD)
            playOpponentIncorrectSound()
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

    /**
     * Increments this user's loss counter, read by the LOSES card in MiniGamesFragment.
     * Each device writes only its own document and navigateToResult() runs once per
     * match, so there's no double counting. Merge-set is used because the field (and
     * possibly the document) doesn't exist for users who have never lost.
     */
    private fun recordLoss() {
        val uid = OnlineGameData.myID
        if (uid.isEmpty()) return

        db.collection("users").document(uid)
            .collection("games").document("OnlineMathStorm")
            .set(mapOf("loses" to FieldValue.increment(1)), SetOptions.merge())
    }

    private fun navigateToResult() {
        if (hasNavigatedToResult) return
        hasNavigatedToResult = true

        gameTimer?.cancel()
        countdownHandler.removeCallbacksAndMessages(null)

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

        // a draw counts as neither a win nor a loss
        if (yourStatus == "LOSER") recordLoss()

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
        gameTimer?.cancel()
        countdownHandler.removeCallbacksAndMessages(null)

        mySlot.cancelReaction()
        opponentSlot.cancelReaction()

        soundPool?.release()
        soundPool = null
        soundIds.clear()

        listener?.remove()
        listener = null
        myUserListener?.remove()
        myUserListener = null
        opponentUserListener?.remove()
        opponentUserListener = null

        battleVmi = null
        mySlot.vmi = null
        opponentSlot.vmi = null
        super.onDestroy()
    }
}