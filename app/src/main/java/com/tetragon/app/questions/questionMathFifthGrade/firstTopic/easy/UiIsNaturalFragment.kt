package com.tetragon.app.questions.questionMathFifthGrade.firstTopic.easy

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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFifthGrade.Math5GradeQuestionActivity
import com.tetragon.app.questions.questionMathFifthGrade.MathGrade5Type
import java.text.DecimalFormat
import java.text.NumberFormat
import kotlin.random.Random

class UiIsNaturalFragment : Fragment(R.layout.fragment_ui_is_natural) {

    private lateinit var instructionText: TextView
    private lateinit var options: List<LinearLayout>

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var displayedNumberString: String = ""
    private var correctAnswerString: String = ""
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

        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math5GradeQuestionActivity
            if (!isAnswerChecked) {
                selectedOptionIndex?.let { index -> checkAnswer(index) }
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

    private fun initViews(view: View) {
        instructionText = view.findViewById(R.id.instructionText)
        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2)
        )

        val activity = requireActivity() as Math5GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)
        stateContainer = activity.findViewById(R.id.stateContainer)
        circleState = activity.findViewById(R.id.circleState)
    }

    private fun generateProblem() {
        val currentLocale = resources.configuration.locales[0]
        val isNaturalExpected = Random.nextBoolean()

        if (isNaturalExpected) {
            val naturalNum = Random.nextLong(1, 150_000)
            displayedNumberString = NumberFormat.getNumberInstance(currentLocale).format(naturalNum)
            correctAnswerString = getString(R.string.option_true)
        } else {
            if (Random.nextBoolean()) {
                val wholePart = Random.nextInt(1, 1000)
                val fractionalPart = Random.nextInt(1, 9)
                val decimalValue = wholePart + (fractionalPart / 10.0)

                val df = NumberFormat.getNumberInstance(currentLocale) as DecimalFormat
                df.applyPattern("#.#")
                displayedNumberString = df.format(decimalValue)
            } else {
                val negativeNum = Random.nextInt(-500, 0)
                displayedNumberString = NumberFormat.getNumberInstance(currentLocale).format(negativeNum)
            }
            correctAnswerString = getString(R.string.option_false)
        }

        val template = getString(R.string.format_is_natural)
        val fullText = String.format(template, displayedNumberString)
        val spannable = SpannableString(fullText)
        val colorBlue = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val startPos = fullText.indexOf(displayedNumberString)
        if (startPos != -1) {
            spannable.setSpan(
                ForegroundColorSpan(colorBlue),
                startPos,
                startPos + displayedNumberString.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        instructionText.text = spannable

        val optionsList = listOf(getString(R.string.option_true), getString(R.string.option_false))
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = optionsList[index]
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        resetFragmentState()
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
                if (i == selectedIndex) R.drawable.option_selected
                else R.drawable.custom_background
            )
        }
    }

    private fun checkAnswer(index: Int) {
        isAnswerChecked = true
        val activity = requireActivity() as Math5GradeQuestionActivity
        val chosenText = (options[index].getChildAt(0) as TextView).text.toString()

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (chosenText == correctAnswerString) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade5Type.IS_NATURAL.xp
            }
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(index)
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(index)
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun showCorrectState(index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = getString(R.string.state_correct)
        statusAnswerLabel(correctAnswerString)
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
        enableCheckButton()
    }

    private fun showIncorrectState(index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
        enableCheckButton()
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)

            statusAnswerLabel(correctAnswerString)
            answer.visibility = View.VISIBLE

            checkBtn.text = getString(R.string.btn_continue)
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                if (tv.text.toString() == correctAnswerString) {
                    layout.setBackgroundResource(R.drawable.option_showed)
                } else {
                    layout.setBackgroundResource(R.drawable.custom_background)
                }
            }
        }
    }

    private fun resetFragmentState() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isCorrectAnswerShowing = false

        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        disableCheckButton()

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()

        stateContainer.visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        disableCheckButton()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    private fun statusAnswerLabel(textResult: String) {
        answer.text = getString(R.string.label_answer, textResult)
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
        checkBtnBack.visibility = View.VISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        checkBtnBack.visibility = View.VISIBLE
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun playSound(soundResId: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}