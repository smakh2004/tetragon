package com.example.tetragon.questions.questionMathFirstGrade.eleventhTopicProblemSolving

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

class UiApplesAzizProblemFragment :
    Fragment(R.layout.fragment_obosolete_problem) {

    private lateinit var questionText: TextView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var correctAnswer = 0
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
        questionText = view.findViewById(R.id.questionText)

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
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun generateProblem() {
        val first = Random.Default.nextInt(1, 10)
        val second = Random.Default.nextInt(1, 11 - first)
        correctAnswer = first + second

        val sentence =
            "Aziz has $first apples, he got $second apples more. How many apples he has now?"

        val spannable = SpannableString(sentence)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        // Color first number
        val firstIndex = sentence.indexOf(first.toString())
        spannable.setSpan(
            ForegroundColorSpan(blueColor),
            firstIndex,
            firstIndex + first.toString().length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Color second number
        val secondIndex = sentence.indexOf(second.toString(), firstIndex + 1)
        spannable.setSpan(
            ForegroundColorSpan(blueColor),
            secondIndex,
            secondIndex + second.toString().length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Color all "apples"
        var startIndex = sentence.indexOf("apples")
        while (startIndex >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(blueColor),
                startIndex,
                startIndex + "apples".length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = sentence.indexOf("apples", startIndex + 1)
        }

        questionText.text = spannable

        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 3) {
            optionSet.add(Random.Default.nextInt(1, 11))
        }

        val shuffled = optionSet.shuffled()
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffled[index].toString()
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        // Reset internal logic variables
        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true

        // REINFORCE: Ensure Activity button is DISABLED/CHECK when navigating here
        checkBtn.isEnabled = false
        checkBtn.text = "CHECK"
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

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

    private fun highlightSelectedOption(index: Int) {
        options.forEachIndexed { i, layout ->
            layout.setBackgroundResource(
                if (i == index) R.drawable.option_selected
                else R.drawable.custom_background
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
                selectedOptionIndex?.let { checkAnswer(it, stateContainer, circleState) }
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    val activity = requireActivity() as Math1GradeQuestionActivity

                    if (checkBtn.text == "FINISH") {
                        activity.navigateToXpGained()
                    } else {
                        // Check for milestone ONLY when CONTINUE is pressed
                        val isMilestoneActive = activity.checkAndTriggerMilestone()

                        if (!isMilestoneActive) {
                            // If no milestone, proceed to next question
                            resetUIForNext()
                            activity.showRandomQuestion()
                        }
                        // If isMilestoneActive is true, Activity has already
                        // replaced the fragment and started the 2.5s timer.
                    }
                }
            }
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val chosen = (options[index].getChildAt(0) as TextView).text.toString().toInt()
        val activity = requireActivity() as Math1GradeQuestionActivity

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            isIncorrectAttempt = false
            val isFinished = activity.incrementProgress()

            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.AZIZ_APPLE_PROBLEM.xp
            }

            // Increment the count in Activity
            activity.handleCorrectAnswer()

            // ALWAYS show success UI here so they can see it before clicking CONTINUE
            activity.playSuccessAnimation()
            stateContainer.visibility = View.VISIBLE
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState(stateContainer, circleState, index)
        } else {
            // Standard incorrect logic
            stateContainer.visibility = View.VISIBLE
            playSound(R.raw.wrong)
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

            // Reset button colors to gray
            checkBtn.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.black_3)
            checkBtnBack.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.black_2)
            btnBack.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.gray_2)
            )

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.gray_2)
            )

            options.forEachIndexed { i, layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(
                    if (tv.text.toString().toInt() == correctAnswer)
                        R.drawable.option_showed
                    else
                        R.drawable.custom_background
                )
            }

            isAnswerChecked = true
            isIncorrectAttempt = false
            checkBtn.text = "CONTINUE"
        }
    }

    private fun applyButtonColors(
        buttonColor: Int,
        backColor: Int,
        backgroundColor: Int
    ) {
        checkBtn.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(
            ContextCompat.getColor(requireContext(), backgroundColor)
        )
    }

    private fun resetForTryAgain() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null

        options.forEach {
            it.setBackgroundResource(R.drawable.custom_background)
        }

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
        activity.hideSuccessAnimation() // Clear Mr. Square

        // 1. Hide the result banner
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE

        // 2. Reset Button to DISABLED "CHECK" state
        checkBtn.text = "CHECK"
        checkBtn.isEnabled = false

        // 3. Switch containers to show the gray/disabled button version
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        // 4. Reset colors and fragment-specific options
        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    private fun playSound(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.setOnCompletionListener { it.release() }
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}