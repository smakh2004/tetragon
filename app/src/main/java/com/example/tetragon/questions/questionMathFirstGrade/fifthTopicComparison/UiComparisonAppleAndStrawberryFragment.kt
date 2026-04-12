package com.example.tetragon.questions.questionMathFirstGrade.fifthTopicComparison

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.example.tetragon.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiComparisonAppleAndStrawberryFragment : Fragment(R.layout.fragment_ui_comparison_apple_and_strawberry) {

    private lateinit var strawberryImages: List<ImageView>
    private lateinit var grapeImages: List<ImageView>
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

    private fun initViews(view: View) {
        // --- Setup Header Text (Blue Highlights) ---
        val instructionText = view.findViewById<TextView>(R.id.textView2)
        val text = "What is more strawberries or grapes?"
        val spannable = SpannableString(text)

        // Use the consistent blue color from your theme
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        // Highlight "strawberries"
        val sWord = "strawberries"
        val sIndex = text.indexOf(sWord)
        if (sIndex != -1) {
            spannable.setSpan(
                ForegroundColorSpan(blueColor),
                sIndex,
                sIndex + sWord.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Highlight "grapes"
        val gWord = "grapes"
        val gIndex = text.indexOf(gWord)
        if (gIndex != -1) {
            spannable.setSpan(
                ForegroundColorSpan(blueColor),
                gIndex,
                gIndex + gWord.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        instructionText.text = spannable

        // --- Collect ImageViews into Lists ---
        strawberryImages = (1..10).map {
            view.findViewById(resources.getIdentifier("strawberry$it", "id", requireContext().packageName))
        }
        grapeImages = (1..10).map {
            view.findViewById(resources.getIdentifier("grape$it", "id", requireContext().packageName))
        }

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

    private fun generateProblem() {
        val sCount = Random.nextInt(1, 11)
        val gCount = Random.nextInt(1, 11)

        // Show/Hide Strawberry Images
        strawberryImages.forEachIndexed { index, img ->
            img.visibility = if (index < sCount) View.VISIBLE else View.GONE
        }

        // Show/Hide Grape Images
        grapeImages.forEachIndexed { index, img ->
            img.visibility = if (index < gCount) View.VISIBLE else View.GONE
        }

        // Set Options Text
        val optionLabels = listOf("Strawberry", "Grape", "Same")
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = optionLabels[index]
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        // Determine Answer
        correctAnswer = when {
            sCount > gCount -> "Strawberry"
            gCount > sCount -> "Grape"
            else -> "Same"
        }

        resetState()
    }

    private fun resetState() {
        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        checkBtn.isEnabled = false
        seeBtn.visibility = View.GONE
        isFirstAttempt = true
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                options.forEachIndexed { i, l ->
                    l.setBackgroundResource(if (i == index) R.drawable.option_selected else R.drawable.custom_background)
                }
                enableCheckButton()
            }
        }
    }

    private fun checkAnswer(index: Int) {
        isAnswerChecked = true
        val chosen = (options[index].getChildAt(0) as TextView).text.toString()
        val activity = requireActivity() as Math1GradeQuestionActivity
        val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
        val circleState = activity.findViewById<ImageView>(R.id.circleState)

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true

            // 1. Show celebration immediately
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.COMPARISON_GRAPE_OR_STRAWBERRY.xp

            // 2. Update the milestone counter
            activity.handleCorrectAnswer()

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

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = "Correct!"
        answer.text = "Answer: $correctAnswer"
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        answer.visibility = View.GONE
        stateAnswer.text = "Incorrect!"
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            answer.text = "Answer: $correctAnswer"
            answer.visibility = View.VISIBLE
            checkBtn.text = "CONTINUE"
            isAnswerChecked = true
            isIncorrectAttempt = false

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(if (tv.text == correctAnswer) R.drawable.option_showed else R.drawable.custom_background)
            }
        }
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it) }
            } else if (isIncorrectAttempt) {
                resetForTryAgain()
            } else {
                val activity = requireActivity() as Math1GradeQuestionActivity
                if (checkBtn.text == "FINISH") {
                    activity.navigateToXpGained()
                } else {
                    // GATE: Check for milestone only when moving forward
                    val isMilestoneActive = activity.checkAndTriggerMilestone()

                    if (!isMilestoneActive) {
                        resetUIForNext()
                        activity.showRandomQuestion()
                    }
                }
            }
        }
    }

    private fun resetForTryAgain() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false
        setupInitialButtonState()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation() // Ensure Mr. Square is hidden

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        checkBtn.text = "CHECK"
        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
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
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun applyButtonColors(btn: Int, btnB: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btnB)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun playSound(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}