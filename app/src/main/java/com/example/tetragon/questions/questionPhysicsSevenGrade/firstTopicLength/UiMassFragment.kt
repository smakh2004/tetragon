package com.example.tetragon.questions.questionPhysicsSevenGrade.firstTopicLength

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.ViewModelNumberProperty
import com.example.tetragon.R
import com.example.tetragon.questions.questionPhysicsSevenGrade.Physics7GradeQuestionActivity
import com.example.tetragon.questions.questionPhysicsSevenGrade.PhysicsGrade7Type

class UiMassFragment : Fragment(R.layout.fragment_ui_mass) {

    private lateinit var questionText: TextView
    private lateinit var riveAnimation: RiveAnimationView

    private lateinit var addBtn: LinearLayout
    private lateinit var minusBtn: LinearLayout
    private lateinit var addBtnLabel: TextView
    private lateinit var minusBtnLabel: TextView

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answerDisplay: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    private lateinit var addEnabledContainer: FrameLayout
    private lateinit var addDisabledContainer: FrameLayout
    private lateinit var minusEnabledContainer: FrameLayout
    private lateinit var minusDisabledContainer: FrameLayout

    private lateinit var minusBtnDisabledText: TextView
    private lateinit var addBtnDisabledText: TextView

    private var massProperty: ViewModelNumberProperty? = null
    private var currentMassKg = 0
    private var targetMassKg = 0
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    private val STATE_MACHINE = "State Machine 1"
    private val INPUT_CLICKED = "clicked"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        disableCheckButton()

        if (!isInitialized) {
            riveAnimation.post {
                setupRiveViewModel()
                generateProblem()
                isInitialized = true
            }
        }
    }

    private fun initViews(view: View) {
        addEnabledContainer = view.findViewById(R.id.add_enabled_container)
        addDisabledContainer = view.findViewById(R.id.add_disabled_container)
        minusEnabledContainer = view.findViewById(R.id.minus_enabled_container)
        minusDisabledContainer = view.findViewById(R.id.minus_disabled_container)

        minusBtnDisabledText = view.findViewById(R.id.minus_btn_disabled_text)
        addBtnDisabledText = view.findViewById(R.id.add_btn_disabled_text)

        questionText = view.findViewById(R.id.questionText)
        riveAnimation = view.findViewById(R.id.topic12)

        addBtn = view.findViewById(R.id.add_btn)
        minusBtn = view.findViewById(R.id.minus_btn)
        addBtnLabel = view.findViewById(R.id.add_btn_label)
        minusBtnLabel = view.findViewById(R.id.minus_btn_label)

        val activity = requireActivity() as Physics7GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answerDisplay = activity.findViewById(R.id.answer)
        stateContainer = activity.findViewById(R.id.stateContainer)
        circleState = activity.findViewById(R.id.circleState)

        val labelText = getString(R.string.unit_kg_uppercase)
        addBtnLabel.text = labelText
        addBtnDisabledText.text = labelText
        minusBtnLabel.text = labelText
        minusBtnDisabledText.text = labelText

        addBtn.setOnClickListener { changeMass(+1) }
        minusBtn.setOnClickListener { changeMass(-1) }

        checkBtn.setOnClickListener {
            if (!isAnswerChecked) {
                checkAnswer()
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    val act = requireActivity() as Physics7GradeQuestionActivity
                    if (checkBtn.text == getString(R.string.btn_finish)) {
                        act.navigateToXpGained()
                    } else {
                        val isMilestoneActive = act.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            resetUIForNext()
                            act.showRandomQuestion()
                        }
                    }
                }
            }
        }
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
    }

    private fun setupRiveViewModel() {
        try {
            val file = riveAnimation.controller.file ?: return
            val vm = file.getViewModelByName("ViewModel1") ?: return
            val vmi = vm.createDefaultInstance()
            riveAnimation.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

            vmi.getNumberProperty("mass")?.let { prop ->
                massProperty = prop
            }

            vmi.getStringProperty("unit")?.let { unitProp ->
                unitProp.value = getString(R.string.unit_kg_lowercase)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun changeMass(delta: Int) {
        if (isAnswerChecked) return
        val newValue = (currentMassKg + delta).coerceIn(0, 9)
        if (newValue == currentMassKg) return

        currentMassKg = newValue
        massProperty?.value = currentMassKg.toFloat()
        riveAnimation.setNumberState(STATE_MACHINE, "weight", currentMassKg.toFloat())
        riveAnimation.fireState(STATE_MACHINE, INPUT_CLICKED)

        if (currentMassKg > 0) enableCheckButton() else disableCheckButton()
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

        val canAdd = currentMassKg < 9
        addEnabledContainer.visibility = if (canAdd) View.VISIBLE else View.GONE
        addDisabledContainer.visibility = if (canAdd) View.GONE else View.VISIBLE

        val canMinus = currentMassKg > 0
        minusEnabledContainer.visibility = if (canMinus) View.VISIBLE else View.GONE
        minusDisabledContainer.visibility = if (canMinus) View.GONE else View.VISIBLE
    }

    private fun generateProblem() {
        targetMassKg = (1..9).random()
        val grams = targetMassKg * 1000

        val gramsStr = grams.toString()
        val gramsUnit = getString(R.string.unit_grams_full)
        val kgUnit = getString(R.string.unit_kilograms_full)

        val fullText = getString(R.string.question_mass_conversion, gramsStr, gramsUnit, kgUnit)
        val spannable = SpannableString(fullText)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        fun applyHighlight(target: String) {
            val start = fullText.indexOf(target)
            if (start != -1) {
                spannable.setSpan(
                    StyleSpan(android.graphics.Typeface.BOLD),
                    start, start + target.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(blueColor),
                    start, start + target.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        applyHighlight(gramsStr)
        applyHighlight(gramsUnit)
        applyHighlight(kgUnit)

        questionText.text = spannable
        resetFragmentState()
    }

    private fun checkAnswer() {
        isAnswerChecked = true
        val activity = requireActivity() as Physics7GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (currentMassKg == targetMassKg) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += PhysicsGrade7Type.LENGTH.xp
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState()
            setupSeeSolution()
        }
        updateVisualButtonStates()
        isFirstAttempt = false
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answerDisplay.text = getString(R.string.solution_mass_correct, targetMassKg)
        answerDisplay.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answerDisplay.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answerDisplay.text = getString(R.string.solution_mass_explanation, targetMassKg)
            answerDisplay.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            massProperty?.value = targetMassKg.toFloat()
            riveAnimation.setNumberState(STATE_MACHINE, "weight", targetMassKg.toFloat())
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            checkBtn.text = getString(R.string.btn_continue)
            isIncorrectAttempt = false
            isAnswerChecked = true
            updateVisualButtonStates()
        }
    }

    private fun resetFragmentState() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        currentMassKg = 0
        massProperty?.value = 0f
        riveAnimation.setNumberState(STATE_MACHINE, "weight", 0f)
        disableCheckButton()
        hideResultUI()
        updateVisualButtonStates()
    }

    private fun resetForTryAgain() {
        isAnswerChecked = false
        isIncorrectAttempt = false
        hideResultUI()
        massProperty?.value = currentMassKg.toFloat()
        if (currentMassKg > 0) enableCheckButton() else disableCheckButton()
        updateVisualButtonStates()
    }

    private fun resetUIForNext() {
        val act = requireActivity() as Physics7GradeQuestionActivity
        act.hideSuccessAnimation()
        act.isResultCurrentlyVisible = false
    }

    private fun hideResultUI() {
        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answerDisplay.text = ""
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        (requireActivity() as Physics7GradeQuestionActivity).isResultCurrentlyVisible = false
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        checkBtn.text = getString(R.string.btn_check)
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
        mediaPlayer?.release()
        super.onDestroyView()
    }
}