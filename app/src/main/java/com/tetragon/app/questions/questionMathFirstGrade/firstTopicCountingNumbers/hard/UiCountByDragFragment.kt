package com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.hard

import android.content.ClipData
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Point
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.*
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import com.tetragon.app.utils.voiceReader.SpokenLine
import com.tetragon.app.utils.voiceReader.TeacherLipSync
import com.tetragon.app.utils.voiceReader.TeacherSpeech
import com.tetragon.app.utils.voiceReader.UzPhrases
import java.util.Locale
import kotlin.random.Random

private const val ARG_SKIP_COUNT = "skip_count"

class UiCountByDragFragment : Fragment(R.layout.fragment_ui_count_by_drag) {

    private lateinit var instructionText: TextView
    private lateinit var tvSeq1: TextView
    private lateinit var tvSeq3: TextView
    private lateinit var tvSeq5: TextView

    // Two blank drop targets (flat cap once filled)
    private lateinit var slotEmpty: List<ImageView>    // blue-box placeholder
    private lateinit var slotShadow: List<View>        // grey shadow under the cap
    private lateinit var slotButton: List<FrameLayout> // flat cap
    private lateinit var slotText: List<TextView>
    private lateinit var slotRoots: List<FrameLayout>

    // Draggable number buttons (flat caps)
    private lateinit var tileRoots: List<FrameLayout>
    private lateinit var tileButtons: List<FrameLayout>

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private lateinit var teacherAnimation: RiveAnimationView
    private lateinit var lipSync: TeacherLipSync
    private lateinit var speech: TeacherSpeech

    private var skipCount: Int = 2

    // Expected values: slot 0 = position 2, slot 1 = position 4
    private var correctA = 0
    private var correctB = 0

    // The visible numbers immediately before each blank, needed for the explanation.
    private var seqV1 = 0
    private var seqV3 = 0

    private var tileValues = listOf<Int>()
    private val tileUsed = BooleanArray(4)

    private val slotValue = arrayOfNulls<Int>(2)
    private val slotTileIndex = intArrayOf(-1, -1)

    private var draggedTileIndex = -1
    private var dropConsumed = false

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Bumped by every interaction that INVALIDATES pending speech so delayed
     * callbacks can tell they're stale. Placing / removing tiles deliberately does
     * NOT bump it: the teacher must be allowed to finish reading the question while
     * the child works on the answer.
     */
    private var interactionToken = 0

    private val RIVE_CORRECT = "correct"
    private val RIVE_INCORRECT = "incorrect"
    private val RIVE_EXPLAIN = "explain"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { skipCount = it.getInt(ARG_SKIP_COUNT, 2) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupInstructionText()
        setupInitialButtonState()
        setupTileDragging()
        setupSlotDropTargets()
        generateProblem()
        setupCheckButton()
    }

    // ------------------------------------------------------------ speech language

    private fun uiLanguage(): String = resources.configuration.locales[0].language

    private fun isUzbek(): Boolean = uiLanguage() == TeacherSpeech.UZ

    /**
     * Locale for the TextToSpeech fallback only. Uzbek is spoken from clips, but
     * if a clip is ever missing the line is read in Russian rather than dropped.
     */
    private fun fallbackLocale(): Locale =
        if (isUzbek()) Locale("ru") else resources.configuration.locales[0]

    /** Text for the TTS fallback, rendered in [fallbackLocale]. */
    private fun spokenText(@StringRes id: Int, vararg args: Any): String {
        if (!isUzbek()) return getString(id, *args)
        val conf = Configuration(resources.configuration)
        conf.setLocale(Locale("ru"))
        return requireContext().createConfigurationContext(conf).getString(id, *args)
    }

    // ---------------------------------------------------------------- the lines

    private fun questionLine(): SpokenLine = SpokenLine(
        text = spokenText(R.string.count_by, skipCount),
        clips = UzPhrases.countBy(skipCount)
    )

    private fun tryAgainLine(): SpokenLine = SpokenLine(
        text = spokenText(R.string.teacher_try_again),
        clips = UzPhrases.tryAgain()
    )

    private fun solutionLine(): SpokenLine = SpokenLine(
        text = spokenText(R.string.solution_count_by, *explanationArgs()),
        clips = UzPhrases.countBySolution(skipCount, correctA, correctB)
    )

    // ------------------------------------------------------------------ tokens

    private fun newInteraction(): Int {
        interactionToken++
        return interactionToken
    }

    private fun isStale(token: Int) = token != interactionToken

    private fun cancelSpeechAndSound() {
        stopSound()
        speech.stop()
    }

    private fun repeatQuestion() {
        newInteraction()
        cancelSpeechAndSound()
        speech.speak(questionLine())
    }

    // ------------------------------------------------------------------- setup

    private fun initViews(view: View) {
        instructionText = view.findViewById(R.id.instructionText)
        tvSeq1 = view.findViewById(R.id.tvSeq1)
        tvSeq3 = view.findViewById(R.id.tvSeq3)
        tvSeq5 = view.findViewById(R.id.tvSeq5)

        slotRoots = listOf(view.findViewById(R.id.slotA), view.findViewById(R.id.slotB))
        slotEmpty = listOf(view.findViewById(R.id.slotAEmpty), view.findViewById(R.id.slotBEmpty))
        slotShadow = listOf(view.findViewById(R.id.slotAShadow), view.findViewById(R.id.slotBShadow))
        slotButton = listOf(view.findViewById(R.id.slotAButton), view.findViewById(R.id.slotBButton))
        slotText = listOf(view.findViewById(R.id.slotAText), view.findViewById(R.id.slotBText))

        tileRoots = listOf(
            view.findViewById(R.id.tile1), view.findViewById(R.id.tile2),
            view.findViewById(R.id.tile3), view.findViewById(R.id.tile4)
        )
        tileButtons = listOf(
            view.findViewById(R.id.tile1Button), view.findViewById(R.id.tile2Button),
            view.findViewById(R.id.tile3Button), view.findViewById(R.id.tile4Button)
        )

        val activity = requireActivity()
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)

        teacherAnimation = view.findViewById(R.id.teacherAnimation)
        lipSync = TeacherLipSync(teacherAnimation, language = uiLanguage())
        lipSync.prepare()
        speech = TeacherSpeech(requireContext(), lipSync, uiLanguage(), fallbackLocale())
        teacherAnimation.setOnClickListener { repeatQuestion() }
    }

    private fun setupInstructionText() {
        val fullText = getString(R.string.count_by, skipCount)
        val spannable = SpannableString(fullText)
        val wordToStyle = skipCount.toString()
        val start = fullText.indexOf(wordToStyle)
        if (start != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                start, start + wordToStyle.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        instructionText.text = spannable
    }

    private fun generateProblem() {
        newInteraction()
        lipSync.clearBooleans()

        val startMultiplier = Random.nextInt(1, 4)
        seqV1 = skipCount * startMultiplier
        correctA = skipCount * (startMultiplier + 1)
        seqV3 = skipCount * (startMultiplier + 2)
        correctB = skipCount * (startMultiplier + 3)
        val v5 = skipCount * (startMultiplier + 4)

        tvSeq1.text = "$seqV1, "
        tvSeq3.text = ", $seqV3, "
        tvSeq5.text = ", $v5"

        val wrongSet = mutableSetOf<Int>()
        var guard = 0
        while (wrongSet.size < 2 && guard < 50) {
            guard++
            val offset = Random.nextInt(1, 4) * skipCount
            val cand = if (Random.nextBoolean()) correctB + offset else correctA - offset
            if (cand > 0 && cand != correctA && cand != correctB &&
                cand != seqV1 && cand != seqV3 && cand != v5
            ) wrongSet.add(cand)
        }
        while (wrongSet.size < 2) wrongSet.add(correctB + skipCount * (wrongSet.size + 5))

        tileValues = (listOf(correctA, correctB) + wrongSet).shuffled()
        tileRoots.forEachIndexed { i, root ->
            (tileButtons[i].getChildAt(0) as TextView).text = tileValues[i].toString()
            root.visibility = View.VISIBLE
            tileButtons[i].visibility = View.VISIBLE
            tileUsed[i] = false
        }

        for (s in 0..1) {
            slotValue[s] = null
            slotTileIndex[s] = -1
            showSlotEmpty(s)
        }

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE

        disableCheckButton()
        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()

        repeatQuestion()
    }

    // ---- Drag & drop -------------------------------------------------------

    @Suppress("ClickableViewAccessibility")
    private fun setupTileDragging() {
        tileButtons.forEachIndexed { index, button ->
            button.isHapticFeedbackEnabled = true

            var downX = 0f
            var downY = 0f
            var dragging = false
            val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop

            button.setOnTouchListener { v, event ->
                if (isAnswerChecked || tileUsed[index]) return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // NOTE: the teacher is deliberately NOT hushed here. The question
                        // line must be allowed to finish while the child moves tiles;
                        // only the check button interrupts it.

                        // 1. Try standard view haptics first
                        val performed = v.performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )

                        // 2. Advanced Hardware Fallback with explicit AudioAttributes
                        if (!performed) {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                                    val combinedVibration = CombinedVibration.createParallel(effect)

                                    // Use VibrationAttributes for API 31+
                                    val vibrationAttributes = VibrationAttributes.Builder()
                                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                                        .build()

                                    vibratorManager.vibrate(combinedVibration, vibrationAttributes)
                                } else {
                                    // Keep AudioAttributes for legacy API pathways
                                    val audioAttributes = AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                        .build()

                                    @Suppress("DEPRECATION")
                                    val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        vibrator.vibrate(
                                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK),
                                            audioAttributes
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(25)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        restoreAllTileCaps()
                        downX = event.x
                        downY = event.y
                        dragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging &&
                            (kotlin.math.abs(event.x - downX) > touchSlop ||
                                    kotlin.math.abs(event.y - downY) > touchSlop)
                        ) {
                            dragging = true
                            draggedTileIndex = index
                            dropConsumed = false

                            val data = ClipData.newPlainText("tileIndex", index.toString())
                            val shadow = CenteredDragShadow(v)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                v.startDragAndDrop(data, shadow, null, 0)
                            } else {
                                @Suppress("DEPRECATION")
                                v.startDrag(data, shadow, null, 0)
                            }
                            v.visibility = View.INVISIBLE
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) placeTileInFirstEmptySlot(index)
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun placeTileInFirstEmptySlot(tileIndex: Int) {
        if (tileUsed[tileIndex]) return
        val target = (0..1).firstOrNull { slotValue[it] == null } ?: return
        fillSlot(target, tileIndex)
    }

    private fun setupSlotDropTargets() {
        slotRoots.forEachIndexed { i, slot ->
            slot.setOnDragListener { _, event -> handleDrag(i, slot, event) }
            slot.setOnClickListener { clearSlot(i) }
        }
    }

    private fun handleDrag(slotIndex: Int, slot: FrameLayout, event: DragEvent): Boolean {
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> true
            DragEvent.ACTION_DRAG_ENTERED -> {
                if (!isAnswerChecked) slot.alpha = 0.7f
                true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                slot.alpha = 1f
                true
            }
            DragEvent.ACTION_DROP -> {
                slot.alpha = 1f
                if (!isAnswerChecked) dropTileIntoSlot(slotIndex)
                true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                slot.alpha = 1f
                restoreAllTileCaps()
                true
            }
            else -> false
        }
    }

    private fun dropTileIntoSlot(slotIndex: Int) {
        val tileIndex = draggedTileIndex
        if (tileIndex < 0 || tileIndex >= tileValues.size || tileUsed[tileIndex]) return
        dropConsumed = true
        fillSlot(slotIndex, tileIndex)
    }

    /** Place a tile into a slot (shared by drag-drop and tap). */
    private fun fillSlot(slotIndex: Int, tileIndex: Int) {
        if (tileUsed[tileIndex]) return

        // Return any tile already sitting in this slot to the tray
        val previous = slotTileIndex[slotIndex]
        if (previous != -1) restoreTile(previous)

        val value = tileValues[tileIndex]
        slotValue[slotIndex] = value
        slotTileIndex[slotIndex] = tileIndex

        showSlotFilled(slotIndex, value)

        tileUsed[tileIndex] = true
        // Keep the tile's grey placeholder visible in the tray (its original spot);
        // only the white cap is hidden. It stays until the slot is cleared.
        tileRoots[tileIndex].visibility = View.VISIBLE
        tileButtons[tileIndex].visibility = View.INVISIBLE

        updateCheckState()
    }

    private fun clearSlot(slotIndex: Int) {
        if (isAnswerChecked) return
        val tileIndex = slotTileIndex[slotIndex]
        if (tileIndex == -1) return

        // The teacher keeps talking here on purpose — see the note in ACTION_DOWN.

        restoreTile(tileIndex)
        slotValue[slotIndex] = null
        slotTileIndex[slotIndex] = -1
        showSlotEmpty(slotIndex)
        updateCheckState()
    }

    private fun restoreTile(tileIndex: Int) {
        tileUsed[tileIndex] = false
        tileRoots[tileIndex].visibility = View.VISIBLE
        tileButtons[tileIndex].visibility = View.VISIBLE
    }

    /** Re-show the white cap of every tile still in the tray (i.e. not placed). */
    private fun restoreAllTileCaps() {
        tileButtons.forEachIndexed { i, cap ->
            if (!tileUsed[i]) cap.visibility = View.VISIBLE
        }
    }

    // ---- Slot visual state -------------------------------------------------

    private fun showSlotEmpty(slotIndex: Int) {
        slotEmpty[slotIndex].visibility = View.VISIBLE
        slotShadow[slotIndex].visibility = View.GONE
        slotButton[slotIndex].visibility = View.GONE
    }

    private fun showSlotFilled(slotIndex: Int, value: Int) {
        slotEmpty[slotIndex].visibility = View.GONE
        slotShadow[slotIndex].visibility = View.VISIBLE
        slotButton[slotIndex].visibility = View.VISIBLE
        slotButton[slotIndex].setBackgroundResource(R.drawable.option_btn_top)
        slotText[slotIndex].text = value.toString()
        slotText[slotIndex].setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
    }

    private fun updateCheckState() {
        if (slotValue[0] != null && slotValue[1] != null) enableCheckButton() else disableCheckButton()
    }

    // ---- Check button ------------------------------------------------------

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            newInteraction()
            cancelSpeechAndSound()

            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                if (slotValue[0] != null && slotValue[1] != null) checkAnswer(stateContainer, circleState)
            } else if (isIncorrectAttempt) {
                resetForTryAgain()
            } else {
                val activity = requireActivity() as Math1GradeQuestionActivity
                if (checkBtn.text == getString(R.string.btn_finish)) {
                    activity.navigateToXpGained()
                } else {
                    resetUIForNext()
                    val isMilestoneActive = activity.checkAndTriggerMilestone()
                    if (!isMilestoneActive) activity.showRandomQuestion()
                }
            }
        }
    }

    private fun checkAnswer(stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val activity = requireActivity() as Math1GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        val aRight = slotValue[0] == correctA
        val bRight = slotValue[1] == correctB
        val allRight = aRight && bRight

        // On a wrong answer, mark BOTH slots as incorrect so we never highlight a
        // correctly-placed option (which would give the answer away). The correct
        // values are only revealed via "See solution".
        colorSlotResult(0, allRight)
        colorSlotResult(1, allRight)

        if (allRight) {
            playSound(R.raw.correct)
            lipSync.fireTrigger(RIVE_CORRECT)

            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.COUNT_BY.xp
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            // Queued behind the buzzer, so it must verify the token before speaking:
            // tapping See solution meanwhile must cancel it.
            val token = interactionToken
            playSound(R.raw.wrong) {
                if (isStale(token)) return@playSound
                speech.speak(
                    tryAgainLine(),
                    onStarted = { if (!isStale(token)) lipSync.setBoolean(RIVE_INCORRECT, true) },
                    onFinished = { lipSync.setBoolean(RIVE_INCORRECT, false) }
                )
            }
            activity.isCorrectAnswerShowing = false
            activity.handleIncorrectAnswer()

            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun colorSlotResult(slotIndex: Int, correct: Boolean) {
        slotButton[slotIndex].setBackgroundResource(
            if (correct) R.drawable.option_btn_top_correct else R.drawable.option_btn_top_incorrect
        )
        slotText[slotIndex].setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answer.visibility = View.VISIBLE
        answer.text = getString(R.string.label_answer, "$correctA, $correctB")
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun explanationArgs() =
        arrayOf<Any>(skipCount, seqV1, correctA, seqV3, correctB)

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            // Invalidates the queued try-again line and kills the buzzer.
            val token = newInteraction()
            cancelSpeechAndSound()
            lipSync.clearBooleans()

            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.solution_count_by, *explanationArgs())
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            // Fill both blanks with the correct values in the normal flat style
            showSlotFilled(0, correctA)
            showSlotFilled(1, correctB)

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isAnswerChecked = true
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            speech.speak(
                solutionLine(),
                onStarted = { if (!isStale(token)) lipSync.setBoolean(RIVE_EXPLAIN, true) },
                onFinished = { lipSync.setBoolean(RIVE_EXPLAIN, false) }
            )
        }
    }

    // ---- State helpers -----------------------------------------------------

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun resetForTryAgain() {
        newInteraction()
        cancelSpeechAndSound()
        lipSync.clearBooleans()

        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        isAnswerChecked = false
        isIncorrectAttempt = false

        for (s in 0..1) {
            slotValue[s] = null
            slotTileIndex[s] = -1
            showSlotEmpty(s)
        }
        tileRoots.forEachIndexed { i, root ->
            tileUsed[i] = false
            root.visibility = View.VISIBLE
            tileButtons[i].visibility = View.VISIBLE
        }

        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
    }

    private fun resetUIForNext() {
        newInteraction()
        cancelSpeechAndSound()
        lipSync.clearBooleans()

        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
    }

    /** [onComplete] runs on the main thread once the clip finishes playing. */
    private fun playSound(soundResId: Int, onComplete: (() -> Unit)? = null) {
        stopSound()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.setOnCompletionListener { player ->
            player.release()
            mediaPlayer = null
            if (isAdded) onComplete?.invoke()
        }
        mediaPlayer?.start()
    }

    /** Stops the clip without firing its completion callback. */
    private fun stopSound() {
        mediaPlayer?.let { player ->
            player.setOnCompletionListener(null)
            runCatching { if (player.isPlaying) player.stop() }
            player.release()
        }
        mediaPlayer = null
    }

    override fun onDestroyView() {
        newInteraction()
        stopSound()
        speech.release()
        lipSync.release()
        super.onDestroyView()
    }

    /** Drag shadow that draws the button cap centered under the finger. */
    private class CenteredDragShadow(view: View) : View.DragShadowBuilder(view) {
        override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
            val w = view.width
            val h = view.height
            outShadowSize.set(w, h)
            outShadowTouchPoint.set(w / 2, h / 2)
        }

        override fun onDrawShadow(canvas: Canvas) {
            view.draw(canvas)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(skipCountFactor: Int) =
            UiCountByDragFragment().apply {
                arguments = Bundle().apply { putInt(ARG_SKIP_COUNT, skipCountFactor) }
            }
    }
}