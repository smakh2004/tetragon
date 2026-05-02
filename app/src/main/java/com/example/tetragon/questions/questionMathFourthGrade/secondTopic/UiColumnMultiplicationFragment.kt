package com.example.tetragon.questions.questionMathFourthGrade.secondTopic

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.databinding.FragmentUiColumnMultiplicationBinding
import com.example.tetragon.questions.questionMathFourthGrade.Math4GradeQuestionActivity
import com.example.tetragon.questions.questionMathFourthGrade.MathGrade4Type
import kotlin.random.Random

class UiColumnMultiplicationFragment : Fragment(R.layout.fragment_ui_column_multiplication) {

    private var _binding: FragmentUiColumnMultiplicationBinding? = null
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

    private var num1 = 0
    private var num2 = 0
    private var correctP1Input = 0
    private var correctP2Input = 0
    private var correctFinalInput = 0

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentUiColumnMultiplicationBinding.bind(view)

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
            binding.frameP1 to Triple(binding.tvInputPartial1, binding.ivBoxP1, binding.cursorP1),
            binding.frameP2 to Triple(binding.tvInputPartial2, binding.ivBoxP2, binding.cursorP2),
            binding.frameFinal to Triple(binding.tvInputFinal, binding.ivBoxFinal, binding.cursorFinal)
        )

        focusMap.forEach { (frame, views) ->
            frame.setOnClickListener { setFocus(views.first, views.second, views.third) }
            views.first.setOnClickListener { setFocus(views.first, views.second, views.third) }
        }
    }

    private fun setFocus(targetTextView: TextView, targetBox: ImageView, targetCursor: View) {
        if (isAnswerChecked) return

        listOf(binding.ivBoxP1, binding.ivBoxP2, binding.ivBoxFinal).forEach {
            it.setImageResource(R.drawable.answer_default_box)
        }
        listOf(binding.cursorP1, binding.cursorP2, binding.cursorFinal).forEach {
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

        var p1Full: Int
        var p2Full: Int
        var finalFull: Int

        while (true) {
            num1 = Random.nextInt(10, 51)
            num2 = Random.nextInt(11, 25)

            val n1Ones = num1 % 10
            val n2Tens = num2 / 10
            val n2Ones = num2 % 10

            p1Full = num1 * n2Ones
            p2Full = num1 * n2Tens
            finalFull = num1 * num2

            val noCarryP1 = (n1Ones * n2Ones < 10)
            val noCarryP2 = (n1Ones * n2Tens < 10)
            val p2SizeValid = p2Full < 100
            val finalSizeValid = (finalFull / 100) < 10

            if (noCarryP1 && noCarryP2 && p2SizeValid && finalSizeValid) break
        }

        binding.tvNum1Tens.text = (num1 / 10).toString()
        binding.tvNum1Ones.text = (num1 % 10).toString()
        binding.tvNum2Tens.text = (num2 / 10).toString()
        binding.tvNum2Ones.text = (num2 % 10).toString()

        binding.tvP1Static.text = (p1Full / 10).toString()
        correctP1Input = p1Full % 10

        correctP2Input = p2Full / 10
        binding.tvP2Static.text = (p2Full % 10).toString()

        binding.tvFinalStaticLeft.text = (finalFull / 100).toString()
        correctFinalInput = (finalFull / 10) % 10
        binding.tvFinalStaticRight.text = (finalFull % 10).toString()

        binding.tvInputPartial1.text = ""
        binding.tvInputPartial2.text = ""
        binding.tvInputFinal.text = ""

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE

        setFocus(binding.tvInputPartial1, binding.ivBoxP1, binding.cursorP1)
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

        val u1 = binding.tvInputPartial1.text.toString().toIntOrNull() ?: -1
        val u2 = binding.tvInputPartial2.text.toString().toIntOrNull() ?: -1
        val u3 = binding.tvInputFinal.text.toString().toIntOrNull() ?: -1

        stateContainer.visibility = View.VISIBLE

        if (u1 == correctP1Input && u2 == correctP2Input && u3 == correctFinalInput) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            setAllBoxes(R.drawable.answer_correct_box)

            answer.text = getString(R.string.label_answer_multiplication, num1, num2, num1 * num2)
            answer.visibility = View.VISIBLE

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade4Type.MULTIPLICATION.xp

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

            answer.text = getString(R.string.label_answer_multiplication, num1, num2, num1 * num2)
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            setAllBoxes(R.drawable.answer_solution_box)
            binding.tvInputPartial1.text = correctP1Input.toString()
            binding.tvInputPartial2.text = correctP2Input.toString()
            binding.tvInputFinal.text = correctFinalInput.toString()

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
        binding.tvInputPartial1.text = ""
        binding.tvInputPartial2.text = ""
        binding.tvInputFinal.text = ""

        setAllBoxes(R.drawable.answer_default_box)
        seeBtn.visibility = View.GONE
        setFocus(binding.tvInputPartial1, binding.ivBoxP1, binding.cursorP1)
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
        val isReady = binding.tvInputPartial1.text.isNotEmpty() &&
                binding.tvInputPartial2.text.isNotEmpty() &&
                binding.tvInputFinal.text.isNotEmpty()
        if (isReady) enableCheckButton() else disableCheckButton()
    }

    private fun setAllBoxes(resId: Int) {
        binding.ivBoxP1.setImageResource(resId)
        binding.ivBoxP2.setImageResource(resId)
        binding.ivBoxFinal.setImageResource(resId)
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