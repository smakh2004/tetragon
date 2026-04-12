package com.example.tetragon.questions.questionMathFirstGrade.twelvesTopicLengthCentimeter

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.SMINumber
import app.rive.runtime.kotlin.core.ViewModelNumberProperty
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.example.tetragon.questions.questionMathFirstGrade.MathGrade1Type

class ShowCmInRullerFragment : Fragment(R.layout.fragment_show_cm_in_ruller) {

    private lateinit var questionText: TextView
    private lateinit var riveRuler: RiveAnimationView

    private val viewModelKeys = arrayOf(
        "0_number", "2_number", "4_number", "6_number", "8_number", "10_number", "12_number"
    )
    private val numberProperties = mutableMapOf<String, ViewModelNumberProperty>()
    private val rulerValueMapping = mutableMapOf<Int, Int>()

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var targetCm: Int = 0
    private var targetChoiceIndex: Int = 0
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isInitialized = false // Guard for one-time initialization
    private var mediaPlayer: MediaPlayer? = null

    private val STATE_MACHINE = "State Machine 1"
    private val INPUT_ANSWERED = "answered"
    private val INPUT_CHOICE = "answerChoice"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || isAnswerChecked || !::riveRuler.isInitialized) return

            val currentChoice = getAnswerChoiceValue()

            if (currentChoice > 0f && !checkBtn.isEnabled) {
                enableCheckButton()
            } else if (currentChoice <= 0f && checkBtn.isEnabled) {
                disableCheckButton()
            }

            mainHandler.postDelayed(this, 100)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        // Only run setup and generation if this is the first time the fragment is viewed
        if (!isInitialized) {
            riveRuler.post {
                setupRiveViewModel()
                generateProblem()
                isInitialized = true
            }
        }
        mainHandler.post(checkRunnable)
    }

    private fun setupRiveViewModel() {
        try {
            val file = riveRuler.controller.file ?: return
            val vm = file.getViewModelByName("ViewModel1") ?: return
            val vmi = vm.createDefaultInstance()
            riveRuler.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

            viewModelKeys.forEach { key ->
                vmi.getNumberProperty(key)?.let { prop -> numberProperties[key] = prop }
            }

            vmi.getStringProperty("unit")?.let { unitProp ->
                unitProp.value = "cm"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateProblem() {
        val startValue = 0
        val availableSteps = arrayOf(2, 5, 8)
        val step = availableSteps.random()
        rulerValueMapping.clear()
        for (index in 0..12) {
            val cmValue = startValue + (index * step)
            rulerValueMapping[index] = cmValue
            if (index % 2 == 0) {
                val key = "${index}_number"
                numberProperties[key]?.value = cmValue.toFloat()
            }
        }
        targetChoiceIndex = (1..12).random()
        targetCm = rulerValueMapping[targetChoiceIndex] ?: 0
        updateQuestionText()
        resetFragmentState()
    }

    private fun getAnswerChoiceValue(): Float {
        var value = 0f
        riveRuler.controller.stateMachines.firstOrNull()?.inputs?.forEach { input ->
            if (input.name == INPUT_CHOICE && input is SMINumber) value = input.value
        }
        return value
    }

    private fun updateQuestionText() {
        val sentence = "Show $targetCm cm on the ruler."
        val spannable = SpannableString(sentence)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)
        val targetPhrase = "$targetCm cm"
        val start = sentence.indexOf(targetPhrase)
        if (start != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), start, start + targetPhrase.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        questionText.text = spannable
    }

    private fun checkAnswer() {
        val selectedIndex = getAnswerChoiceValue().toInt()
        isAnswerChecked = true
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        riveRuler.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        if (selectedIndex == targetChoiceIndex) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.CM_RULER.xp
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) "FINISH" else "CONTINUE"
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            checkBtn.text = "TRY AGAIN"
            showIncorrectState()
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = "Solution"
            answerDisplay.text = "The correct length is $targetCm cm"
            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            riveRuler.setNumberState(STATE_MACHINE, INPUT_CHOICE, targetChoiceIndex.toFloat())
            riveRuler.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = "CONTINUE"
            isIncorrectAttempt = false
        }
    }

    private fun initViews(view: View) {
        questionText = view.findViewById(R.id.questionText)
        riveRuler = view.findViewById(R.id.topic12)
        val activity = requireActivity() as Math1GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answerDisplay = activity.findViewById(R.id.answer)
        stateContainer = activity.findViewById(R.id.stateContainer)
        circleState = activity.findViewById(R.id.circleState)

        checkBtn.setOnClickListener {
            if (!isAnswerChecked) {
                checkAnswer()
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    val activity = requireActivity() as Math1GradeQuestionActivity
                    if (checkBtn.text == "FINISH") {
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
        disableCheckButton()
    }

    private fun resetFragmentState() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        checkBtn.text = "CHECK"
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        riveRuler.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)
        riveRuler.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false

        riveRuler.setNumberState(STATE_MACHINE, INPUT_CHOICE, 0f)
        riveRuler.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answerDisplay.text = ""

        checkBtn.text = "CHECK"
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        disableCheckButton()
        mainHandler.post(checkRunnable)
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()

        // STOP the listener to prevent updates during navigation
        mainHandler.removeCallbacks(checkRunnable)

        // DO NOT reset Rive or internal UI here to keep the "Success" view
        // frozen until the fragment is swapped.
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = "Correct!"
        answerDisplay.text = "Answer: $targetCm cm"
        answerDisplay.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = "Incorrect!"
        answerDisplay.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun applyButtonColors(btn: Int, back: Int, bg: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), back)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), bg))
    }

    private fun playSound(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.start()
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacks(checkRunnable)
        super.onDestroyView()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }
}