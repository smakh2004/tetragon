package com.tetragon.app.questions.questionMathFourthGrade.thirdTopic

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.databinding.FragmentUiColumnDivisionBinding
import com.tetragon.app.questions.questionMathFourthGrade.Math4GradeQuestionActivity
import com.tetragon.app.questions.questionMathFourthGrade.MathGrade4Type
import kotlin.random.Random

class UiColumnDivisionFragment : Fragment(R.layout.fragment_ui_column_division) {

    private var _binding: FragmentUiColumnDivisionBinding? = null
    private val binding get() = _binding!!

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var activeInput: TextView? = null
    private var activeBox: ImageView? = null
    private var activeCursor: View? = null
    private var cursorAnimator: ObjectAnimator? = null

    private var dividend = 0
    private var divisor = 0
    private var correctQuotient = 0
    private var correctSub1 = 0
    private var correctSub2 = 0

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUiColumnDivisionBinding.bind(view)

        val activity = requireActivity() as Math4GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)

        setupFocusLogic()
        setupKeyboard(view)
        generateProblem()
        setupCheckButton()
    }

    private fun setupFocusLogic() {
        val focusMap = mapOf(
            binding.frameQuotient to Triple(binding.tvInputQuotient, binding.ivBoxQuotient, binding.cursorQuotient),
            binding.frameSub1 to Triple(binding.tvInputSub1, binding.ivBoxSub1, binding.cursorSub1),
            binding.frameSub2 to Triple(binding.tvInputSub2, binding.ivBoxSub2, binding.cursorSub2)
        )

        focusMap.forEach { (frame, views) ->
            val onClick = View.OnClickListener { setFocus(views.first, views.second, views.third) }
            frame.setOnClickListener(onClick)
            views.first.setOnClickListener(onClick)
        }
    }

    private fun setFocus(targetTextView: TextView, targetBox: ImageView, targetCursor: View) {
        if (isAnswerChecked) return

        listOf(binding.ivBoxQuotient, binding.ivBoxSub1, binding.ivBoxSub2).forEach {
            it.setImageResource(R.drawable.answer_default_box)
        }
        listOf(binding.cursorQuotient, binding.cursorSub1, binding.cursorSub2).forEach {
            it.visibility = View.GONE
        }
        cursorAnimator?.cancel()

        activeInput = targetTextView
        activeBox = targetBox
        activeCursor = targetCursor

        activeBox?.setImageResource(R.drawable.answer_blue_box)
        activeCursor?.visibility = View.VISIBLE
        updateCursorPosition()

        cursorAnimator = ObjectAnimator.ofFloat(targetCursor, "alpha", 1.0f, 0.0f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun updateCursorPosition() {
        val cursor = activeCursor ?: return
        val input = activeInput ?: return
        val density = resources.displayMetrics.density
        cursor.translationX = if (input.text.isNotEmpty()) 12f * density else 0f
    }

    private fun generateProblem() {
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE

        while (true) {
            divisor = Random.Default.nextInt(11, 25)
            val quotient = Random.Default.nextInt(11, 15)
            dividend = divisor * quotient

            val d1 = dividend / 100
            val d2 = (dividend / 10) % 10
            val d3 = dividend % 10

            val step1Dividend = dividend / 10
            val step1Sub = divisor * 1
            val remainder1 = step1Dividend - step1Sub
            val step2Dividend = remainder1 * 10 + d3

            if (remainder1 in 1..9 && (step2Dividend / divisor) in 1..9) {
                correctSub1 = step1Sub % 10
                correctQuotient = quotient % 10
                correctSub2 = step2Dividend % 10

                binding.tvDividend1.text = d1.toString()
                binding.tvDividend2.text = d2.toString()
                binding.tvDividend3.text = d3.toString()
                binding.tvDivisor.text = divisor.toString()

                binding.tvQuotientStatic.text = (quotient / 10).toString()
                binding.tvSub1Static.text = (step1Sub / 10).toString()

                binding.tvStep2Static1.text = remainder1.toString()
                binding.tvStep2Static2.text = d3.toString()
                binding.tvSub2Static.text = (step2Dividend / 10).toString()
                break
            }
        }

        binding.tvInputQuotient.text = ""
        binding.tvInputSub1.text = ""
        binding.tvInputSub2.text = ""

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE

        setFocus(binding.tvInputQuotient, binding.ivBoxQuotient, binding.cursorQuotient)
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun setupKeyboard(view: View) {
        val buttonIds = listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)
        buttonIds.forEach { id ->
            view.findViewById<Button>(id).setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                if (activeInput?.text.isNullOrEmpty()) {
                    activeInput?.text = (it as Button).text.toString()
                    updateCursorPosition()
                    toggleCheckButtonState()
                }
            }
        }
        view.findViewById<Button>(R.id.btnDel).setOnClickListener {
            if (isAnswerChecked) return@setOnClickListener
            activeInput?.text = ""
            updateCursorPosition()
            toggleCheckButtonState()
        }
    }

    private fun checkAnswer() {
        cursorAnimator?.cancel()
        activeCursor?.visibility = View.GONE

        val activity = requireActivity() as Math4GradeQuestionActivity
        val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
        val circleState = activity.findViewById<ImageView>(R.id.circleState)

        isAnswerChecked = true
        activity.isResultCurrentlyVisible = true

        val u1 = binding.tvInputQuotient.text.toString().toIntOrNull() ?: -1
        val u2 = binding.tvInputSub1.text.toString().toIntOrNull() ?: -1
        val u3 = binding.tvInputSub2.text.toString().toIntOrNull() ?: -1

        stateContainer.visibility = View.VISIBLE

        if (u1 == correctQuotient && u2 == correctSub1 && u3 == correctSub2) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            setAllBoxes(R.drawable.answer_correct_box)

            val fullDividend = "${binding.tvDividend1.text}${binding.tvDividend2.text}${binding.tvDividend3.text}"
            val fullDivisor = binding.tvDivisor.text
            val fullQuotient = "${binding.tvQuotientStatic.text}${binding.tvInputQuotient.text}"

            answer.text = getString(R.string.label_answer_division, fullDividend, fullDivisor, fullQuotient)
            answer.visibility = View.VISIBLE

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade4Type.DIVISION.xp

            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            setAllBoxes(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math4GradeQuestionActivity
            if (!isAnswerChecked) {
                checkAnswer()
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    if (checkBtn.text == getString(R.string.btn_finish)) {
                        activity.navigateToXpGained()
                    } else {
                        val isMilestoneActive = activity.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            resetUIForNext()
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)

            val fullDividend = "${binding.tvDividend1.text}${binding.tvDividend2.text}${binding.tvDividend3.text}"
            val fullDivisor = binding.tvDivisor.text
            val result = dividend / divisor

            answer.text = getString(R.string.label_answer_division, fullDividend, fullDivisor, result.toString())
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            setAllBoxes(R.drawable.answer_solution_box)
            binding.tvInputQuotient.text = correctQuotient.toString()
            binding.tvInputSub1.text = correctSub1.toString()
            binding.tvInputSub2.text = correctSub2.toString()

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE

        isAnswerChecked = false
        isIncorrectAttempt = false
        binding.tvInputQuotient.text = ""
        binding.tvInputSub1.text = ""
        binding.tvInputSub2.text = ""

        setAllBoxes(R.drawable.answer_default_box)
        seeBtn.visibility = View.GONE
        setFocus(binding.tvInputQuotient, binding.ivBoxQuotient, binding.cursorQuotient)
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE
        seeBtn.visibility = View.GONE
        setupInitialButtonState()
        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
        enableCheckButton()
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
        enableCheckButton()
    }

    private fun toggleCheckButtonState() {
        val isReady = binding.tvInputQuotient.text.isNotEmpty() &&
                binding.tvInputSub1.text.isNotEmpty() &&
                binding.tvInputSub2.text.isNotEmpty()
        if (isReady) enableCheckButton() else disableCheckButton()
    }

    private fun setAllBoxes(resId: Int) {
        binding.ivBoxQuotient.setImageResource(resId)
        binding.ivBoxSub1.setImageResource(resId)
        binding.ivBoxSub2.setImageResource(resId)
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        checkBtnBack.visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math4GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        checkBtnBack.visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cursorAnimator?.cancel()
        mediaPlayer?.release()
        _binding = null
    }
}