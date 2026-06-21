package com.tetragon.app.questions.questionMathThirdGrade.fourthTopic

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
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathThirdGrade.Math3GradeQuestionActivity
import com.tetragon.app.questions.questionMathThirdGrade.MathGrade3Type
import java.util.Locale
import kotlin.random.Random

class UiShowRadiusFragment : Fragment(R.layout.fragment_show_radius) {

    private lateinit var radiusQuestionText: TextView
    private lateinit var radiusTargetValueText: TextView
    private lateinit var radiusRiveView: RiveAnimationView

    private var radiusNumberProp: ViewModelNumberProperty? = null

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private var targetRadius: Int = 0
    private val defaultStartRadius = 1f

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isUserInteracting = false
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    private val STATE_MACHINE = "State Machine 1"
    private val INPUT_RADIUS = "radius"
    private val INPUT_ANSWERED = "answered"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || isAnswerChecked || !::radiusRiveView.isInitialized) return

            val currentRadius = getRiveValue(INPUT_RADIUS)

            if (!isUserInteracting) {
                val lockedValue = radiusRiveView.tag as? Float ?: defaultStartRadius
                if (Math.abs(currentRadius - lockedValue) > 0.1f) {
                    isUserInteracting = true
                    enableCheckButton()
                }
            }

            radiusNumberProp?.value = currentRadius

            if (currentRadius > 0f && isUserInteracting) {
                if (!checkBtn.isEnabled) enableCheckButton()
            } else {
                if (checkBtn.isEnabled) disableCheckButton()
            }
            mainHandler.postDelayed(this, 100)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        if (!isInitialized) {
            radiusRiveView.post {
                setupRiveViewModel()
                generateProblem()
                isInitialized = true
            }
        }
        mainHandler.postDelayed(checkRunnable, 500)
    }

    private fun setupRiveViewModel() {
        try {
            val file = radiusRiveView.controller.file ?: return
            val vm = file.getViewModelByName("ViewModel1") ?: return
            val vmi = vm.createDefaultInstance()
            radiusRiveView.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

            radiusNumberProp = vmi.getNumberProperty("radiusNumber")
            radiusNumberProp?.value = defaultStartRadius

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateProblem() {
        targetRadius = Random.nextInt(2, 11)
        updateQuestionText()
        resetFragmentState(defaultStartRadius)
    }

    private fun updateQuestionText() {
        val sentence = getString(R.string.question_show_radius)
        val highlightWord = getString(R.string.word_radius)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        // 1. Top instructions Text Setup
        val spannableSentence = SpannableString(sentence)
        val radiusWordStart = sentence.lowercase(Locale.getDefault()).indexOf(highlightWord.lowercase(Locale.getDefault()))

        if (radiusWordStart != -1) {
            spannableSentence.setSpan(
                ForegroundColorSpan(blueColor),
                radiusWordStart,
                radiusWordStart + highlightWord.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        radiusQuestionText.text = spannableSentence

        // 2. Localized formatted target template value display setup ("r = X")
        val targetValueCombined = getString(R.string.radius_target_template, targetRadius)
        val spannableValue = SpannableString(targetValueCombined)

        val rIndex = targetValueCombined.indexOf("r")
        if (rIndex != -1) {
            spannableValue.setSpan(
                ForegroundColorSpan(blueColor),
                rIndex,
                rIndex + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        radiusTargetValueText.text = spannableValue
    }

    private fun checkAnswer() {
        val currentVmValue = radiusNumberProp?.value ?: getRiveValue(INPUT_RADIUS)
        val userRadius = Math.round(currentVmValue)

        isAnswerChecked = true
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        radiusRiveView.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

        val isCorrect = (userRadius == targetRadius)

        if (isCorrect) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade3Type.RADIUS.xp
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(userRadius)
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState()
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)

            answerDisplay.text = getString(R.string.display_solution_answer, targetRadius)
            answerDisplay.visibility = View.VISIBLE

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            radiusRiveView.setNumberState(STATE_MACHINE, INPUT_RADIUS, targetRadius.toFloat())
            radiusRiveView.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, true)

            radiusNumberProp?.value = targetRadius.toFloat()

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = getString(R.string.btn_continue)
            isIncorrectAttempt = false
        }
    }

    private fun initViews(view: View) {
        radiusQuestionText = view.findViewById(R.id.radiusQuestionText)
        radiusTargetValueText = view.findViewById(R.id.radiusTargetValueText)
        radiusRiveView = view.findViewById(R.id.radiusRiveView)

        val activity = requireActivity() as Math3GradeQuestionActivity
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
            if (!isAnswerChecked) checkAnswer()
            else if (isIncorrectAttempt) resetForTryAgain()
            else handleNavigation(activity)
        }
        disableCheckButton()
    }

    private fun resetFragmentState(startRadius: Float) {
        isAnswerChecked = false
        isIncorrectAttempt = false
        isUserInteracting = false
        checkBtn.text = getString(R.string.btn_check)
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE

        radiusRiveView.setNumberState(STATE_MACHINE, INPUT_RADIUS, startRadius)
        radiusRiveView.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)
        radiusRiveView.tag = startRadius

        radiusNumberProp?.value = startRadius

        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        mainHandler.removeCallbacks(checkRunnable)

        isAnswerChecked = false
        isIncorrectAttempt = false
        isUserInteracting = false

        radiusRiveView.tag = getRiveValue(INPUT_RADIUS)
        radiusRiveView.setBooleanState(STATE_MACHINE, INPUT_ANSWERED, false)

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))

        disableCheckButton()
        mainHandler.postDelayed(checkRunnable, 300)
    }

    private fun handleNavigation(activity: Math3GradeQuestionActivity) {
        if (checkBtn.text == getString(R.string.btn_finish)) activity.navigateToXpGained()
        else if (!activity.checkAndTriggerMilestone()) {
            resetUIForNext()
            activity.showRandomQuestion()
        }
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math3GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()
        mainHandler.removeCallbacks(checkRunnable)
    }

    private fun showCorrectState(radius: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answerDisplay.text = getString(R.string.display_correct_answer, radius)
        answerDisplay.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        answerDisplay.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun getRiveValue(inputName: String): Float {
        var value = 0f
        radiusRiveView.controller.stateMachines.firstOrNull()?.inputs?.forEach { input ->
            if (input.name == inputName && input is SMINumber) value = input.value
        }
        return value
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