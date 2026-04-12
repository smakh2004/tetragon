package com.example.tetragon.questions.questionMathFirstGrade.firstTopicCountingNumbers

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.example.tetragon.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiNumberOfAnglesShapeFragment : Fragment(R.layout.fragment_ui_number_of_angles_shape) {

    private lateinit var shapeImage: ImageView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView
    private lateinit var questionText: TextView

    private var correctAnswer = ""
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true

    private val allShapes = listOf(
        "circle" to 0,
        "triangle" to 3,
        "rectangle" to 4,
        "square" to 4
    )

    private val remainingShapes = allShapes.toMutableList()
    private var currentShapeName: String = ""
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shapeImage = view.findViewById(R.id.shapeImage)
        questionText = view.findViewById(R.id.textView2)

        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2),
            view.findViewById(R.id.option3)
        )

        checkBtn = requireActivity().findViewById(R.id.check_enabled_btn)
        checkBtnBack = requireActivity().findViewById(R.id.check_enabled_button_background)
        btnBack = requireActivity().findViewById(R.id.btnBackground)
        seeBtn = requireActivity().findViewById(R.id.see_btn_container)
        seeEnabledButton = requireActivity().findViewById(R.id.see_enabled_btn)
        stateAnswer = requireActivity().findViewById(R.id.stateAnswer)
        answer = requireActivity().findViewById(R.id.answer)

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

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun generateProblem() {

        if (remainingShapes.isEmpty()) return

        val (shapeName, angles) = remainingShapes.random()
        remainingShapes.removeIf { it.first == shapeName }

        currentShapeName = shapeName
        correctAnswer = angles.toString()

        setColoredQuestionText(shapeName)

        val drawableId = resources.getIdentifier(
            "figure_$shapeName",
            "drawable",
            requireContext().packageName
        )

        shapeImage.setImageResource(drawableId)

        val optionSet = mutableSetOf(correctAnswer)

        while (optionSet.size < 3)
            optionSet.add(Random.nextInt(0, 5).toString())

        val shuffledOptions = optionSet.shuffled()

        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index]
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true

        // ADD THESE LINES:
        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        seeBtn.visibility = View.GONE
        setupInitialButtonState() // Ensure colors reset to blue/white
    }

    private fun setColoredQuestionText(shapeName: String) {

        val fullText = "How many angles does a $shapeName have?"

        val spannable = SpannableString(fullText)

        val start = fullText.indexOf(shapeName)
        val end = start + shapeName.length

        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        spannable.setSpan(
            ForegroundColorSpan(blueColor),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        questionText.text = spannable
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
                if (i == selectedIndex)
                    R.drawable.option_selected
                else
                    R.drawable.custom_background
            )
        }
    }

    private fun enableCheckButton() {

        checkBtn.isEnabled = true

        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility =
            View.VISIBLE

        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility =
            View.INVISIBLE
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                selectedOptionIndex?.let {
                    checkAnswer(it, stateContainer, circleState)
                }
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    val activity = requireActivity() as Math1GradeQuestionActivity

                    if (checkBtn.text == "FINISH") {
                        activity.navigateToXpGained()
                    } else {
                        // GATE: Check for milestone ONLY after clicking CONTINUE
                        val isMilestoneActive = activity.checkAndTriggerMilestone()

                        if (!isMilestoneActive) {
                            resetUIForNext()
                            activity.showRandomQuestion()
                            setupOptionClicks()
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

            // Update to the "Correct" version of the shape image (e.g., highlighting angles)
            val drawableId = resources.getIdentifier(
                "figure_${currentShapeName}_correct",
                "drawable",
                requireContext().packageName
            )
            shapeImage.setImageResource(drawableId)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt)
                activity.totalXp += MathGrade1Type.ANGLES.xp

            // 1. Increment the milestone counter in the Activity
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState(stateContainer, circleState, index)
        } else {
            playSound(R.raw.wrong)
            val drawableId = resources.getIdentifier(
                "figure_${currentShapeName}_incorrect",
                "drawable",
                requireContext().packageName
            )
            shapeImage.setImageResource(drawableId)

            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun showCorrectState(
        stateContainer: FrameLayout,
        circleState: ImageView,
        index: Int
    ) {

        stateContainer.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.green_3)
        )

        circleState.setImageResource(R.drawable.correct_tick_icon)

        options[index].setBackgroundResource(R.drawable.option_correct)

        stateAnswer.text = "Correct!"

        answer.text = "Answer: $correctAnswer"

        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(
        stateContainer: FrameLayout,
        circleState: ImageView,
        index: Int
    ) {

        stateContainer.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.red_4)
        )

        circleState.setImageResource(R.drawable.wrong_circle)

        options[index].setBackgroundResource(R.drawable.option_incorrect)

        stateAnswer.text = "Incorrect!"

        answer.visibility = View.GONE

        seeBtn.visibility = View.VISIBLE

        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(
        stateContainer: FrameLayout,
        circleState: ImageView
    ) {

        seeEnabledButton.setOnClickListener {

            seeBtn.visibility = View.GONE

            stateAnswer.text = "Solution"

            answer.text = "Answer: $correctAnswer"

            answer.visibility = View.VISIBLE

            checkBtn.text = "CONTINUE"

            isAnswerChecked = true
            isIncorrectAttempt = false

            checkBtn.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.black_3)

            checkBtnBack.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.black_2)

            btnBack.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.gray_2)
            )

            val drawableId = resources.getIdentifier(
                "figure_${currentShapeName}_solution",
                "drawable",
                requireContext().packageName
            )

            shapeImage.setImageResource(drawableId)

            circleState.setImageResource(R.drawable.solution_lamp_icon)

            stateContainer.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.gray_2)
            )

            options.forEach {

                val tv = it.getChildAt(0) as TextView

                it.setBackgroundResource(
                    if (tv.text.toString() == correctAnswer)
                        R.drawable.option_showed
                    else
                        R.drawable.custom_background
                )
            }
        }
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {

        checkBtn.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), buttonColor)

        checkBtnBack.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), backColor)

        btnBack.setBackgroundColor(
            ContextCompat.getColor(requireContext(), backgroundColor)
        )
    }

    private fun resetForTryAgain() {

        val activity = requireActivity() as Math1GradeQuestionActivity

        activity.isResultCurrentlyVisible = false

        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null

        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }

        val drawableId = resources.getIdentifier(
            "figure_$currentShapeName",
            "drawable",
            requireContext().packageName
        )

        shapeImage.setImageResource(drawableId)

        checkBtn.text = "CHECK"

        checkBtn.isEnabled = false

        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility =
            View.INVISIBLE

        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility =
            View.VISIBLE

        setupInitialButtonState()

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility =
            View.INVISIBLE

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

        // UPDATE THESE LINES:
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}