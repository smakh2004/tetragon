package com.example.tetragon.questions.questionPhysicsTenthGrade.firstTopic

import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionPhysicsNinthGrade.PhysicsGrade9Type
import com.example.tetragon.questions.questionPhysicsTenthGrade.Physics10GradeQuestionActivity
import com.example.tetragon.questions.questionPhysicsTenthGrade.PhysicsGrade10Topic
import com.example.tetragon.questions.questionPhysicsTenthGrade.PhysicsGrade10Type

class UiFindForceBILFragment : Fragment(R.layout.fragment_ui_find_force_b_i_l) {

    private lateinit var instructionText: TextView
    private lateinit var options: List<LinearLayout>

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView

    private var bValue: Int = 0
    private var iValue: Int = 0
    private var lValue: Int = 0
    private var correctAnswer: Int = 0
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

    private fun initViews(view: View) {
        instructionText = view.findViewById(R.id.instructionText)
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
        answerDisplay = activity.findViewById(R.id.answer)
    }

    private fun generateProblem() {
        // F = B * I * L
        bValue = (1..5).random()
        iValue = (1..10).random()
        lValue = (1..4).random()
        correctAnswer = bValue * iValue * lValue

        val optionsSet = mutableSetOf(correctAnswer)
        while (optionsSet.size < 3) {
            val wrong = when((1..3).random()) {
                1 -> bValue + iValue + lValue // Addition error
                2 -> correctAnswer + (2..5).random()
                else -> (correctAnswer - 5).coerceAtLeast(1)
            }
            if (wrong != correctAnswer) optionsSet.add(wrong)
        }

        val shuffled = optionsSet.toList().shuffled()
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = "${shuffled[index]} N"
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        updateQuestionText()
        resetFragmentState()
    }

    private fun resetFragmentState() {
        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE
        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"

        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }

        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE

        setupInitialButtonState()
    }

    private fun updateQuestionText() {
        val fullText = "Find the Ampere force F. B = $bValue T, I = $iValue A, L = $lValue m"
        val spannable = SpannableStringBuilder(fullText)
        val lightBlue = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val keywords = listOf("force F", "B =", "I =", "L =")
        keywords.forEach { word ->
            val start = fullText.indexOf(word)
            if (start != -1) {
                spannable.setSpan(ForegroundColorSpan(lightBlue), start, start + word.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        instructionText.text = spannable
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

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it, stateContainer, circleState) }
            } else if (isIncorrectAttempt) {
                resetForTryAgain()
            } else {
                val activity = requireActivity() as Physics10GradeQuestionActivity
                if (checkBtn.text == "FINISH") {
                    activity.navigateToXpGained()
                } else if (!activity.checkAndTriggerMilestone()) {
                    resetUIForNext()
                    activity.showRandomQuestion()
                }
            }
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val activity = requireActivity() as Physics10GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        val chosenText = (options[index].getChildAt(0) as TextView).text.toString()
        val chosenValue = chosenText.filter { it.isDigit() }.toInt()

        if (chosenValue == correctAnswer) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()

            // XP Logic for Grade 10
            if (isFirstAttempt) activity.totalXp += PhysicsGrade9Type.FIND_FORCE.xp // Assuming 10 XP for Magnetism
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState(stateContainer, circleState, index)
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            answerDisplay.text = "F = B × I × L = $bValue × $iValue × $lValue = $correctAnswer N"
            answerDisplay.visibility = View.VISIBLE
            checkBtn.text = "CONTINUE"

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isAnswerChecked = true
            isIncorrectAttempt = false

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                val valInTv = tv.text.toString().filter { it.isDigit() }.toInt()
                layout.setBackgroundResource(if (valInTv == correctAnswer) R.drawable.option_showed else R.drawable.custom_background)
            }
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Physics10GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        resetFragmentState()
        stateAnswer.text = ""
        answerDisplay.text = ""
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Physics10GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answerDisplay.text = ""
        seeBtn.visibility = View.GONE
        checkBtn.text = "CHECK"
        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    private fun highlightSelectedOption(selectedIndex: Int) {
        options.forEachIndexed { i, layout ->
            layout.setBackgroundResource(if (i == selectedIndex) R.drawable.option_selected else R.drawable.custom_background)
        }
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun applyButtonColors(btn: Int, btnB: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btnB)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = "Correct!"
        answerDisplay.text = "Answer: $correctAnswer N"
        answerDisplay.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = "Incorrect!"
        answerDisplay.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun playSound(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.start()
    }

    override fun onDestroyView() {
        mediaPlayer?.release()
        super.onDestroyView()
    }
}