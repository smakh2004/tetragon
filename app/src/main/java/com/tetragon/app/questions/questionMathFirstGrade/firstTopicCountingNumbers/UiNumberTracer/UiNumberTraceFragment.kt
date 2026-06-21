package com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.UiNumberTracer

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import kotlin.random.Random

private const val ARG_TARGET_NUMBER = "target_number"

class UiNumberTraceFragment : Fragment() {

    private lateinit var instructionText: TextView
    private lateinit var numberTraceView: NumberTraceView

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var targetNumber: Int = 1
    private var isAnswerChecked = false
    private var isAnswerIncorrect = false
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetNumber = arguments?.getInt(ARG_TARGET_NUMBER) ?: Random.nextInt(1, 10)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_ui_number_trace, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupInstructionText()
        setupInitialButtonState()

        numberTraceView.setTargetNumber(targetNumber)

        // Handles conditional enabling of check UI as soon as more than 1% is filled
        numberTraceView.onTraceProgressListener = { percent ->
            if (!isAnswerChecked && percent >= 1f && !checkBtn.isEnabled) {
                checkBtn.isEnabled = true
                requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
                requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
            }
        }

        setupCheckButton()
    }

    private fun initViews(view: View) {
        instructionText = view.findViewById(R.id.instructionText)
        numberTraceView = view.findViewById(R.id.numberTraceView)

        val activity = requireActivity()
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)
    }

    private fun setupInstructionText() {
        val fullText = getString(R.string.instruction_trace_number, targetNumber)
        val spannable = SpannableString(fullText)
        val wordToStyle = targetNumber.toString()
        val start = fullText.indexOf(wordToStyle)

        if (start != -1) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                start,
                start + wordToStyle.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        instructionText.text = spannable
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)

        checkBtn.isEnabled = false
        checkBtn.text = getString(R.string.btn_check)

        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math1GradeQuestionActivity
            val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = activity.findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                isAnswerChecked = true
                stateContainer.visibility = View.VISIBLE

                // CRITICAL: Evaluates correctness state only if total trace percent is > 90%
                val tracedPercentage = numberTraceView.currentFilledPercentage
                if (tracedPercentage > 90f) {
                    isAnswerIncorrect = false
                    playSound(R.raw.correct)

                    activity.isResultCurrentlyVisible = true
                    activity.isCorrectAnswerShowing = true
                    activity.playSuccessAnimation()

                    val isFinished = activity.incrementProgress()
                    activity.totalXp += MathGrade1Type.NUMBER_DRAWING.xp
                    activity.handleCorrectAnswer()

                    stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
                    circleState.setImageResource(R.drawable.correct_tick_icon)

                    stateAnswer.text = getString(R.string.state_correct)
                    answer.text = getString(R.string.desc_correct)
                    checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
                    applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
                } else {
                    // Incorrect Route: User hit check button with less than 90% filled
                    isAnswerIncorrect = true
                    playSound(R.raw.wrong)

                    activity.isResultCurrentlyVisible = true
                    activity.isCorrectAnswerShowing = false

                    stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_3))
                    circleState.setImageResource(R.drawable.wrong_circle)

                    stateAnswer.text = getString(R.string.state_incorrect)
                    answer.text = getString(R.string.desc_incorrect)
                    checkBtn.text = getString(R.string.btn_try_again)
                    applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
                }
            } else {
                // If the user got it wrong and clicked "Try Again", reset the state to trace again
                if (isAnswerIncorrect) {
                    isAnswerChecked = false
                    isAnswerIncorrect = false

                    activity.isResultCurrentlyVisible = false
                    stateContainer.visibility = View.INVISIBLE

                    // Clear tracking parameters back to fresh initialization layout structures
                    setupInitialButtonState()
                    numberTraceView.setTargetNumber(targetNumber)
                } else {
                    // Normal continuous progression navigation for correct answers
                    activity.isResultCurrentlyVisible = false
                    if (activity.isCorrectAnswerShowing) {
                        activity.hideSuccessAnimation()
                    }
                    stateContainer.visibility = View.INVISIBLE

                    if (checkBtn.text == getString(R.string.btn_finish)) {
                        activity.navigateToXpGained()
                    } else {
                        val isMilestoneActive = activity.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
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

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        @JvmStatic
        fun newInstance(targetNumber: Int) =
            UiNumberTraceFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_TARGET_NUMBER, targetNumber)
                }
            }
    }
}