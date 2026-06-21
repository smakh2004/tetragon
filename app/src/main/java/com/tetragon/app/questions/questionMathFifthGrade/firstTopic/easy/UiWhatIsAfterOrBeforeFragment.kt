package com.tetragon.app.questions.questionMathFifthGrade.firstTopic.easy

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFifthGrade.Math5GradeQuestionActivity
import com.tetragon.app.questions.questionMathFifthGrade.MathGrade5Type
import java.text.NumberFormat
import kotlin.random.Random

class UiWhatIsAfterOrBeforeFragment : Fragment(R.layout.fragment_ui_what_is_after_or_before) {

    private lateinit var tvInstruction: TextView
    private lateinit var editText: EditText

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var baseNumber: Long = 0
    private var correctTargetAnswer: Long = 0
    private var userEnteredText: String = ""

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupKeyboard(view)
        setupInitialButtonState()
        generateProblem()

        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math5GradeQuestionActivity
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

    private fun initViews(view: View) {
        tvInstruction = view.findViewById(R.id.tvInstruction)
        editText = view.findViewById(R.id.editText)

        val activity = requireActivity() as Math5GradeQuestionActivity

        // Input Management
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        editText.viewTreeObserver.addOnPreDrawListener {
            editText.showSoftInputOnFocus = false
            true
        }

        // Shared Activity Layout View Binds
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

    private fun setupKeyboard(view: View) {
        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        buttonIds.forEach { id ->
            view.findViewById<Button>(id).setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                if (userEnteredText == "0") userEnteredText = ""
                if (userEnteredText.length < 12) {
                    userEnteredText += (it as Button).text.toString()
                    updateEditTextDisplay()
                    enableCheckButton()
                }
            }
        }

        view.findViewById<Button>(R.id.btnDel).setOnClickListener {
            if (isAnswerChecked) return@setOnClickListener
            if (userEnteredText.isNotEmpty()) {
                userEnteredText = userEnteredText.dropLast(1)
                updateEditTextDisplay()
                if (userEnteredText.isEmpty()) disableCheckButton()
            }
        }
    }

    private fun updateEditTextDisplay() {
        if (userEnteredText.isEmpty()) {
            editText.setText("")
            editText.hint = getString(R.string.hint_input_example)
        } else {
            try {
                val parsed = userEnteredText.toLong()
                val currentLocale = resources.configuration.locales[0]
                val formatted = NumberFormat.getNumberInstance(currentLocale).format(parsed)
                editText.setText(formatted)
            } catch (e: Exception) {
                editText.setText(userEnteredText)
            }
        }
        editText.setSelection(editText.text.length)
    }

    private fun generateProblem() {
        baseNumber = Random.nextLong(100, 9_999_999)
        val isAfterQuestion = Random.nextBoolean()
        correctTargetAnswer = if (isAfterQuestion) baseNumber + 1 else baseNumber - 1

        val currentLocale = resources.configuration.locales[0]
        val formattedBaseNumber = NumberFormat.getNumberInstance(currentLocale).format(baseNumber)

        val stringTemplate = if (isAfterQuestion) {
            getString(R.string.format_what_is_after)
        } else {
            getString(R.string.format_what_is_before)
        }

        val fullText = String.format(stringTemplate, formattedBaseNumber)
        val builder = SpannableStringBuilder(fullText)
        val colorBlue = ContextCompat.getColor(requireContext(), R.color.blue_2)

        val startPos = fullText.indexOf(formattedBaseNumber)
        if (startPos != -1) {
            builder.setSpan(
                ForegroundColorSpan(colorBlue),
                startPos,
                startPos + formattedBaseNumber.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        tvInstruction.text = builder
        resetFragmentState()
    }

    private fun checkAnswer() {
        isAnswerChecked = true
        editText.isCursorVisible = false
        val activity = requireActivity() as Math5GradeQuestionActivity
        val userAnswerLong = userEnteredText.toLongOrNull() ?: -1L

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (userAnswerLong == correctTargetAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade5Type.NUMBER_BEFORE_AFTER.xp
            }
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState()
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)

        val currentLocale = resources.configuration.locales[0]
        statusAnswerLabel(NumberFormat.getNumberInstance(currentLocale).format(correctTargetAnswer))

        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
        enableCheckButton()
    }

    private fun showIncorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
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

            val currentLocale = resources.configuration.locales[0]
            statusAnswerLabel(NumberFormat.getNumberInstance(currentLocale).format(correctTargetAnswer))
            answer.visibility = View.VISIBLE

            checkBtn.text = getString(R.string.btn_continue)
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            userEnteredText = correctTargetAnswer.toString()
            updateEditTextDisplay()
        }
    }

    private fun resetFragmentState() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isCorrectAnswerShowing = false

        userEnteredText = ""
        editText.isCursorVisible = true
        updateEditTextDisplay()

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
        userEnteredText = ""
        editText.isCursorVisible = true
        updateEditTextDisplay()

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