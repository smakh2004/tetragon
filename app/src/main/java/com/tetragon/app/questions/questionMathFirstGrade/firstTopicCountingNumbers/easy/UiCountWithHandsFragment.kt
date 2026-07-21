package com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.easy

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

class UiCountWithHandsFragment : Fragment(R.layout.fragment_ui_count_with_hands) {

    private lateinit var questionText: TextView
    private lateinit var handCountImage: ImageView
    private lateinit var tvCounterValue: TextView

    private lateinit var addBtn: LinearLayout
    private lateinit var minusBtn: LinearLayout

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private lateinit var addEnabledContainer: FrameLayout
    private lateinit var addDisabledContainer: FrameLayout
    private lateinit var minusEnabledContainer: FrameLayout
    private lateinit var minusDisabledContainer: FrameLayout

    // Changed default values to 0 instead of 1
    private var currentCountValue = 0
    private var targetHandCount = 1
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    private val handDrawables = intArrayOf(
        R.drawable.hand_one,
        R.drawable.hand_two,
        R.drawable.hand_three,
        R.drawable.hand_four,
        R.drawable.hand_five,
        R.drawable.hand_six,
        R.drawable.hand_seven,
        R.drawable.hand_eight,
        R.drawable.hand_nine,
        R.drawable.hand_ten
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupInitialButtonState()

        if (!isInitialized) {
            generateProblem()
            isInitialized = true
        }
        setupCheckButton()
    }

    private fun initViews(view: View) {
        addEnabledContainer = view.findViewById(R.id.add_enabled_container)
        addDisabledContainer = view.findViewById(R.id.add_disabled_container)
        minusEnabledContainer = view.findViewById(R.id.minus_enabled_container)
        minusDisabledContainer = view.findViewById(R.id.minus_disabled_container)

        questionText = view.findViewById(R.id.questionText)
        handCountImage = view.findViewById(R.id.handCountImage)
        tvCounterValue = view.findViewById(R.id.tv_counter_value)

        addBtn = view.findViewById(R.id.add_btn)
        minusBtn = view.findViewById(R.id.minus_btn)

        checkBtn = requireActivity().findViewById(R.id.check_enabled_btn)
        checkBtnBack = requireActivity().findViewById(R.id.check_enabled_button_background)
        btnBack = requireActivity().findViewById(R.id.btnBackground)
        seeBtn = requireActivity().findViewById(R.id.see_btn_container)
        seeEnabledButton = requireActivity().findViewById(R.id.see_enabled_btn)
        stateAnswer = requireActivity().findViewById(R.id.stateAnswer)
        answer = requireActivity().findViewById(R.id.answer)

        addBtn.setOnClickListener { changeCounterValue(+1) }
        minusBtn.setOnClickListener { changeCounterValue(-1) }
    }

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.setOnCompletionListener {
            it.release()
        }
        mediaPlayer?.start()
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun generateProblem() {
        targetHandCount = Random.Default.nextInt(1, 11)
        handCountImage.setImageResource(handDrawables[targetHandCount - 1])

        selectedOptionIndexReset()
        updateVisualButtonStates()
    }

    private fun selectedOptionIndexReset() {
        // Explicitly set default layout value representations back to 0
        currentCountValue = 0
        tvCounterValue.text = "0"

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true

        checkBtn.isEnabled = false
        checkBtn.text = getString(R.string.btn_check)
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        seeBtn.visibility = View.GONE
        setupInitialButtonState()
    }

    private fun changeCounterValue(delta: Int) {
        if (isAnswerChecked) return

        // Allowed selectable range maps strictly between 1 and 10
        val newValue = (currentCountValue + delta).coerceIn(1, 10)
        if (newValue == currentCountValue) return

        currentCountValue = newValue
        tvCounterValue.text = currentCountValue.toString()

        enableCheckButton()
        updateVisualButtonStates()
    }

    private fun updateVisualButtonStates() {
        if (isAnswerChecked) {
            addEnabledContainer.visibility = View.GONE
            addDisabledContainer.visibility = View.VISIBLE
            minusEnabledContainer.visibility = View.GONE
            minusDisabledContainer.visibility = View.VISIBLE
            return
        }

        val canAdd = currentCountValue < 10
        addEnabledContainer.visibility = if (canAdd) View.VISIBLE else View.GONE
        addDisabledContainer.visibility = if (canAdd) View.GONE else View.VISIBLE

        // If it's 0 (initial unselected state) or 1, disable minus button interaction layers
        val canMinus = currentCountValue > 1
        minusEnabledContainer.visibility = if (canMinus) View.VISIBLE else View.GONE
        minusDisabledContainer.visibility = if (canMinus) View.GONE else View.VISIBLE
    }

    private fun enableCheckButton() {
        // Prevent check triggering logic explicitly if a user maintains or hacks back to 0
        if (currentCountValue == 0) return

        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                checkAnswer(stateContainer, circleState)
            } else if (isIncorrectAttempt) {
                resetForTryAgain()
            } else {
                val activity = requireActivity() as Math1GradeQuestionActivity
                if (checkBtn.text == getString(R.string.btn_finish)) {
                    activity.navigateToXpGained()
                } else {
                    resetUIForNext() // always reset first
                    val isMilestoneActive = activity.checkAndTriggerMilestone()
                    if (!isMilestoneActive) {
                        activity.showRandomQuestion()
                    }
                }
            }
        }
    }

    private fun checkAnswer(stateContainer: FrameLayout, circleState: ImageView) {
        if (currentCountValue == 0) return // Fallback protection guard

        isAnswerChecked = true
        val activity = requireActivity() as Math1GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (currentCountValue == targetHandCount) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.HAND_COUNT.xp
            }

            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        updateVisualButtonStates()
        isFirstAttempt = false
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answer.text = getString(R.string.label_answer, targetHandCount.toString())
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
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
            answer.text = getString(R.string.label_answer, targetHandCount.toString())
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)
            isAnswerChecked = true
            isIncorrectAttempt = false

            checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black_3)
            checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black_2)
            btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            currentCountValue = targetHandCount
            tvCounterValue.text = currentCountValue.toString()
            updateVisualButtonStates()
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

        checkBtn.text = getString(R.string.btn_check)
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE

        setupInitialButtonState()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
        updateVisualButtonStates()
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
    }

    override fun onDestroyView() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroyView()
    }
}