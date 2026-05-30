package com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiWhatIsEvenOrOddNumberUpToFragment : Fragment(R.layout.fragment_ui_even_or_odd_number) {

    private lateinit var questionText: TextView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var correctAnswer = ""
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupInitialButtonState()
        generateProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.setOnCompletionListener { it.release() }
        mediaPlayer?.start()
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.questionText)
        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2),
            view.findViewById(R.id.option3)
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

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun generateProblem() {
        val maxNumber = if (Random.nextBoolean()) 8 else 10
        val askEven = Random.nextBoolean()

        val correctNumbers = (1..maxNumber).filter {
            if (askEven) it % 2 == 0 else it % 2 != 0
        }

        val wrongNumbers1 = (1..maxNumber).shuffled().take(correctNumbers.size).sorted()
        val wrongNumbers2 = (1..maxNumber).shuffled().take(correctNumbers.size).sorted()

        correctAnswer = correctNumbers.joinToString(", ")

        val optionSet = mutableSetOf(correctAnswer)
        optionSet.add(wrongNumbers1.joinToString(", "))
        optionSet.add(wrongNumbers2.joinToString(", "))

        val shuffledOptions = optionSet.toList().shuffled()

        // Localization Logic
        val typeText = if (askEven) getString(R.string.type_even) else getString(R.string.type_odd)
        val maxNumStr = maxNumber.toString()
        val fullText = getString(R.string.question_numbers_upto, typeText, maxNumStr)

        val spannable = SpannableString(fullText)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        // Highlight Type and Number
        listOf(typeText, maxNumStr).forEach { target ->
            val start = fullText.indexOf(target)
            if (start != -1) {
                spannable.setSpan(ForegroundColorSpan(blueColor), start, start + target.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        questionText.text = spannable

        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index]
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        resetFragmentState()
    }

    private fun resetFragmentState() {
        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        checkBtn.isEnabled = false
        checkBtn.text = getString(R.string.btn_check)

        val activity = requireActivity()
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        setupInitialButtonState()
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()
            }
        }
    }

    private fun highlightSelectedOption(selectedIndex: Int) {
        options.forEachIndexed { i, layout ->
            layout.setBackgroundResource(
                if (i == selectedIndex) R.drawable.option_selected else R.drawable.custom_background
            )
        }
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math1GradeQuestionActivity
            val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = activity.findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it, stateContainer, circleState) }
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

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val chosen = (options[index].getChildAt(0) as TextView).text.toString()
        val activity = requireActivity() as Math1GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.UP_TO_EVEN_OR_ODD.xp
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState, index)
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = getString(R.string.state_correct)
        answer.text = getString(R.string.label_answer, correctAnswer)
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answer.visibility = View.GONE
        stateAnswer.text = getString(R.string.state_incorrect)
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer, correctAnswer)
            answer.visibility = View.VISIBLE

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEachIndexed { i, layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(
                    if (tv.text.toString() == correctAnswer) R.drawable.option_showed
                    else R.drawable.custom_background
                )
            }

            isAnswerChecked = true
            isIncorrectAttempt = false
            checkBtn.text = getString(R.string.btn_continue)
        }
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        checkBtn.text = getString(R.string.btn_check)
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
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
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}