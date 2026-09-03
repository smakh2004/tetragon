package com.tetragon.app.ui.uiCashStorm

import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityUiCashStormBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.soundUtils.SoundManager

class UiCashStormActivity : BaseActivity() {

    private lateinit var binding: ActivityUiCashStormBinding
    private lateinit var controller: ProfitStormController

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userListener: ListenerRegistration? = null

    private var selectedOption = 0
    private var isNavigatingToResult = false

    // ---------------- Sound ----------------
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<Int, Int>()

    // ---------------- Rive top bar state ----------------
    private var topBarVmi: ViewModelInstance? = null

    /** Always 1 — the saved avatarConfig no longer decides the neutral face. */
    private val defaultFace: Float = FACE_DEFAULT

    private var reacting = false
    private var reactionEnd: Runnable? = null

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
        private const val PROP_FACE = "face"

        // Same face numbering as the solo Math Storm top bar
        private const val FACE_HAPPY = 11f
        private const val FACE_SAD = 12f
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityUiCashStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                val config = snapshot?.get("avatarConfig") as? Map<*, *>
                    ?: return@addSnapshotListener
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
                    AVATAR_NUMBERS.forEach { key ->
                        if (key == "face") {
                            // face is never taken from Firestore, and an in-flight
                            // reaction is never overwritten
                            if (!reacting) setNumber(vmi, key, defaultFace)
                        } else {
                            setNumber(vmi, key, (config[key] as? Number)?.toFloat() ?: 1f)
                        }
                    }

                    val hatValue = (config["hat"] as? Number)?.toInt() ?: 1
                    runCatching { vmi.getBooleanProperty("hatOn")?.value = (hatValue > 1) }

                    AVATAR_COLORS.forEach { propName ->
                        val hex = config[propName] as? String ?: return@forEach
                        val colorInt =
                            runCatching { Color.parseColor(hex) }.getOrNull() ?: return@forEach
                        setColor(vmi, propName, colorInt)
                    }
                }

                // ---- localized "score" label (en / ru / uz via resources) ----
                setString(vmi, PROP_SCORE_TEXT, getString(R.string.ms_score_label))

                // ---- flush anything produced before binding ----
                pendingTimerText?.let { setString(vmi, PROP_TIMER, it) }
                pendingScore?.let { setNumber(vmi, PROP_SCORE, it.toFloat()) }

            } catch (e: Exception) {
                Log.e("CashStorm", "Error binding Rive top bar: ${e.message}")
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

    private fun setFace(face: Float) {
        val vmi = topBarVmi ?: return
        setNumber(vmi, PROP_FACE, face)
    }

    /** Temporary reaction (happy/sad) for 900ms, then back to the default face. */
    private fun reactWithFace(face: Float) {
        reactionEnd?.let { binding.topBarRive.removeCallbacks(it) }
        reacting = true
        setFace(face)

        val end = Runnable {
            reacting = false
            reactionEnd = null
            if (!isFinishing && !isDestroyed) setFace(defaultFace)
        }
        reactionEnd = end
        binding.topBarRive.postDelayed(end, FACE_REACTION_MS)
    }

    private fun cancelReaction() {
        reactionEnd?.let { binding.topBarRive.removeCallbacks(it) }
        reactionEnd = null
        reacting = false
    }

    // ==================== CONTROLLER ====================

    private fun initController() {
        controller = ProfitStormController(
            onProblemChanged = { problem ->
                resetSelection()
                binding.totalBalanceText.text = "$${problem.baseAmount}"

                // --- DYNAMIC CASH IMAGE LOGIC ---
                when {
                    problem.baseAmount < 100 ->
                        binding.mainCashIcon.setImageResource(R.drawable.one_cash)
                    problem.baseAmount < 500 ->
                        binding.mainCashIcon.setImageResource(R.drawable.two_cash)
                    else ->
                        binding.mainCashIcon.setImageResource(R.drawable.three_cash)
                }
                // --------------------------------

                val card1Text = binding.optionCard1.getChildAt(0) as TextView
                val card2Text = binding.optionCard2.getChildAt(0) as TextView

                card1Text.text = formatSignToBlue(problem.option1Text)
                card2Text.text = formatSignToBlue(problem.option2Text)
            },
            onScoreChanged = { score ->
                // react first: nothing blocking runs before the face is pushed to Rive
                reactWithFace(FACE_HAPPY)
                setScore(score)
                playCorrectSound()
            },
            onMistakeChanged = { count ->
                // wrong answer: feedback only, nothing is counted or displayed
                if (count > 0) {
                    reactWithFace(FACE_SAD)
                    playWrongSound()
                }
            },
            onCountdownTick = { sec -> setTimerText(formatTime(sec)) },
            onQuizFinished = { score -> navigateToResult(score) }
        )
    }

    private fun formatSignToBlue(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        val color = ContextCompat.getColor(this, R.color.blue_2)
        // Identify math signs including the fraction slash and the multiplier prefix
        val targetSigns = charArrayOf('+', '-', '×', '%', '/')

        text.forEachIndexed { index, char ->
            if (char in targetSigns) {
                builder.setSpan(
                    ForegroundColorSpan(color),
                    index,
                    index + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }

    private fun initButtons() {
        binding.optionCard1.setOnClickListener { selectCard(1) }
        binding.optionCard2.setOnClickListener { selectCard(2) }
        binding.enabledButton.setOnClickListener { controller.checkAnswer(selectedOption) }
    }

    private fun startCountdownOverlay(seconds: Int) {
        binding.countdownOverlay.visibility = View.VISIBLE
        binding.afterCountdownImage.visibility = View.GONE
        binding.countdownText.visibility = View.VISIBLE

        controller.startCountdown(
            seconds,
            tick = { sec ->
                binding.countdownText.text = sec.toString()
                playTimerSound()
            },
            finish = {
                binding.countdownText.visibility = View.GONE
                binding.afterCountdownImage.visibility = View.VISIBLE
                playFinishSound()

                binding.afterCountdownImage.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        binding.countdownOverlay.visibility = View.GONE
                        controller.generateProblem()
                        controller.startQuizTimer(180)
                    }
                }, 1000)
            }
        )
    }

    private fun selectCard(index: Int) {
        selectedOption = index
        binding.optionCard1.setBackgroundResource(R.drawable.answer_default_box)
        binding.optionCard2.setBackgroundResource(R.drawable.answer_default_box)
        val selected = if (index == 1) binding.optionCard1 else binding.optionCard2
        selected.setBackgroundResource(R.drawable.answer_blue_box)
        binding.enabledButtonFrame.visibility = View.VISIBLE
        binding.disabledButtonFrame.visibility = View.INVISIBLE
    }

    private fun resetSelection() {
        selectedOption = 0
        binding.optionCard1.setBackgroundResource(R.drawable.answer_default_box)
        binding.optionCard2.setBackgroundResource(R.drawable.answer_default_box)
        binding.enabledButtonFrame.visibility = View.INVISIBLE
        binding.disabledButtonFrame.visibility = View.VISIBLE
    }

    private fun formatTime(seconds: Int) = String.format("%d:%02d", seconds / 60, seconds % 60)

    // ==================== SOUND ====================

    /**
     * SoundPool decodes every clip once, at startup, and plays it off the UI thread,
     * so the Rive face reaction is never delayed by media preparation.
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
                    R.raw.wrong,
                    R.raw.sound_cash
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
    private fun playCorrectSound() = playSound(R.raw.sound_cash)
    private fun playWrongSound() = playSound(R.raw.wrong)

    // ==================== NAVIGATION ====================

    private fun showQuitBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_quit, null)
        dialog.setContentView(view)
        view.findViewById<Button>(R.id.noButton).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.finishButton).setOnClickListener {
            controller.cancelQuizTimer()
            navigateToResult(controller.getCurrentScore())
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun navigateToResult(score: Int) {
        if (isNavigatingToResult || isFinishing) return
        isNavigatingToResult = true
        controller.cancelQuizTimer()

        val intent = Intent(this, ResultCashStormActivity::class.java).apply {
            putExtra("score", score)
            putExtra("questionsAnswered", controller.getQuestionsAnswered())
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        controller.cancelQuizTimer()
        cancelReaction()
        binding.afterCountdownImage.removeCallbacks(null)

        soundPool?.release()
        soundPool = null
        soundIds.clear()

        userListener?.remove()
        userListener = null
        topBarVmi = null

        super.onDestroy()
    }
}