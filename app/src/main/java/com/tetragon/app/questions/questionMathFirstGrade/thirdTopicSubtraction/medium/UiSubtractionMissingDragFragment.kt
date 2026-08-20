package com.tetragon.app.questions.questionMathFirstGrade.thirdTopicSubtraction.medium

import android.content.ClipData
import android.content.Context
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
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiSubtractionMissingDragFragment : Fragment(R.layout.fragment_ui_subtraction_missing_drag) {

    private lateinit var instructionText: TextView
    private lateinit var resultTexts: List<TextView>

    // Three blank drop targets, one per row
    private lateinit var slotEmpty: List<ImageView>
    private lateinit var slotShadow: List<View>
    private lateinit var slotButton: List<FrameLayout>
    private lateinit var slotText: List<TextView>
    private lateinit var slotRoots: List<FrameLayout>

    private lateinit var tileRoots: List<FrameLayout>
    private lateinit var tileButtons: List<FrameLayout>

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private val SLOT_COUNT = 3
    private val TILE_COUNT = 5
    private val MINUEND = 10

    private var resultValues = IntArray(SLOT_COUNT)
    private var correctSubs = IntArray(SLOT_COUNT)

    private var tileValues = listOf<Int>()
    private val tileUsed = BooleanArray(TILE_COUNT)

    private val slotValue = arrayOfNulls<Int>(SLOT_COUNT)
    private val slotTileIndex = IntArray(SLOT_COUNT) { -1 }

    private var draggedTileIndex = -1
    private var dropConsumed = false

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        instructionText.text = getString(R.string.find_the_answer)
        setupInitialButtonState()
        setupTileDragging()
        setupSlotDropTargets()
        generateProblem()
        setupCheckButton()
    }

    private fun initViews(view: View) {
        instructionText = view.findViewById(R.id.instructionText)

        resultTexts = listOf(
            view.findViewById(R.id.resultTextA),
            view.findViewById(R.id.resultTextB),
            view.findViewById(R.id.resultTextC)
        )
        slotRoots = listOf(view.findViewById(R.id.slotA), view.findViewById(R.id.slotB), view.findViewById(R.id.slotC))
        slotEmpty = listOf(view.findViewById(R.id.slotAEmpty), view.findViewById(R.id.slotBEmpty), view.findViewById(R.id.slotCEmpty))
        slotShadow = listOf(view.findViewById(R.id.slotAShadow), view.findViewById(R.id.slotBShadow), view.findViewById(R.id.slotCShadow))
        slotButton = listOf(view.findViewById(R.id.slotAButton), view.findViewById(R.id.slotBButton), view.findViewById(R.id.slotCButton))
        slotText = listOf(view.findViewById(R.id.slotAText), view.findViewById(R.id.slotBText), view.findViewById(R.id.slotCText))

        tileRoots = listOf(
            view.findViewById(R.id.tile1), view.findViewById(R.id.tile2),
            view.findViewById(R.id.tile3), view.findViewById(R.id.tile4),
            view.findViewById(R.id.tile5)
        )
        tileButtons = listOf(
            view.findViewById(R.id.tile1Button), view.findViewById(R.id.tile2Button),
            view.findViewById(R.id.tile3Button), view.findViewById(R.id.tile4Button),
            view.findViewById(R.id.tile5Button)
        )

        val activity = requireActivity()
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)
    }

    private fun generateProblem() {
        // Three rows: 10 - [ ] = result, with three distinct results (so three distinct answers).
        val results = mutableListOf<Int>()
        while (results.size < SLOT_COUNT) {
            val r = Random.nextInt(1, MINUEND) // 1..9
            if (r !in results) results.add(r)
        }
        for (i in 0 until SLOT_COUNT) {
            resultValues[i] = results[i]
            correctSubs[i] = MINUEND - results[i]
            resultTexts[i].text = results[i].toString()
        }

        // Two distractors, distinct from the correct answers and each other
        val correctSet = correctSubs.toSet()
        val distractors = mutableSetOf<Int>()
        var guard = 0
        while (distractors.size < TILE_COUNT - SLOT_COUNT && guard < 100) {
            guard++
            val cand = Random.nextInt(1, MINUEND) // 1..9
            if (cand !in correctSet && cand !in distractors) distractors.add(cand)
        }

        tileValues = (correctSubs.toList() + distractors).shuffled()
        tileRoots.forEachIndexed { i, root ->
            (tileButtons[i].getChildAt(0) as TextView).text = tileValues[i].toString()
            root.visibility = View.VISIBLE
            tileButtons[i].visibility = View.VISIBLE
            tileUsed[i] = false
        }

        for (s in 0 until SLOT_COUNT) {
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
                        val performed = v.performHapticFeedback(
                            HapticFeedbackConstants.KEYBOARD_TAP,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )
                        if (!performed) {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                    val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                                    val combinedVibration = CombinedVibration.createParallel(effect)
                                    val vibrationAttributes = VibrationAttributes.Builder()
                                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                                        .build()
                                    vibratorManager.vibrate(combinedVibration, vibrationAttributes)
                                } else {
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
        val target = (0 until SLOT_COUNT).firstOrNull { slotValue[it] == null } ?: return
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

    private fun fillSlot(slotIndex: Int, tileIndex: Int) {
        if (tileUsed[tileIndex]) return

        val previous = slotTileIndex[slotIndex]
        if (previous != -1) restoreTile(previous)

        val value = tileValues[tileIndex]
        slotValue[slotIndex] = value
        slotTileIndex[slotIndex] = tileIndex

        showSlotFilled(slotIndex, value)

        tileUsed[tileIndex] = true
        tileRoots[tileIndex].visibility = View.VISIBLE
        tileButtons[tileIndex].visibility = View.INVISIBLE

        updateCheckState()
    }

    private fun clearSlot(slotIndex: Int) {
        if (isAnswerChecked) return
        val tileIndex = slotTileIndex[slotIndex]
        if (tileIndex == -1) return

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
        if (slotValue.all { it != null }) enableCheckButton() else disableCheckButton()
    }

    // ---- Check button ------------------------------------------------------

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                if (slotValue.all { it != null }) checkAnswer(stateContainer, circleState)
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

        // Each row is checked against its own answer (position-specific).
        val allRight = (0 until SLOT_COUNT).all { slotValue[it] == correctSubs[it] }

        // All-or-nothing colouring so we don't reveal which single row was right.
        for (s in 0 until SLOT_COUNT) colorSlotResult(s, allRight)

        if (allRight) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.SUBTRACTION_DRAG_THREE.xp
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
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

    private fun solutionString(): String =
        (0 until SLOT_COUNT).joinToString(", ") { correctSubs[it].toString() }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answer.visibility = View.VISIBLE
        answer.text = getString(R.string.label_answer, solutionString())
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

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer, solutionString())
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            for (i in 0 until SLOT_COUNT) showSlotFilled(i, correctSubs[i])

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isAnswerChecked = true
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
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
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        isAnswerChecked = false
        isIncorrectAttempt = false

        for (s in 0 until SLOT_COUNT) {
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

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.setOnCompletionListener { it.release() }
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

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
}