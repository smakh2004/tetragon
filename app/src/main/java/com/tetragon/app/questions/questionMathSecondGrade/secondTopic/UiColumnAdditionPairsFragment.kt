package com.tetragon.app.questions.questionMathSecondGrade.secondTopic

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathSecondGrade.Math2GradeQuestionActivity
import com.tetragon.app.questions.questionMathSecondGrade.MathGrade2Type
import kotlin.random.Random

class UiColumnAdditionPairsFragment : Fragment(R.layout.fragment_ui_column_addition_pairs) {

    private var selectedProblem: View? = null
    private var selectedAnswer: View? = null
    private var isAnswerChecked = false
    private var mediaPlayer: MediaPlayer? = null

    private val matchedIds = mutableSetOf<Int>()
    private var isFirstAttempt = true
    private val solutionMap = mutableMapOf<Int, Int>()

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var stateContainer: FrameLayout
    private lateinit var stateAnswer: TextView
    private lateinit var circleState: ImageView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as Math2GradeQuestionActivity

        val activityAnswerTv = activity.findViewById<TextView>(R.id.answer)
        activityAnswerTv?.visibility = View.GONE

        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        stateContainer = activity.findViewById(R.id.stateContainer)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        circleState = activity.findViewById(R.id.circleState)

        generatePairs(view)
        setupClickListeners(view)
        setupCheckButton()
        setupInitialButtonState()
        disableCheckButton()
    }

    private fun generatePairs(root: View) {
        val problemIds = listOf(R.id.problem1, R.id.problem2, R.id.problem3)
        val answerIds = listOf(R.id.answer1, R.id.answer2, R.id.answer3)

        val problemData = mutableListOf<Pair<Int, Int>>()
        val solutionList = mutableListOf<Int>()

        for (i in 0 until 3) {
            val num1 = Random.nextInt(10, 50)
            val num2 = Random.nextInt(10, 40)
            val total = num1 + num2

            problemData.add(Pair(num1, num2))
            solutionList.add(total)
        }

        val shuffledProblems = problemData.shuffled()
        val shuffledAnswers = solutionList.shuffled()

        shuffledProblems.forEachIndexed { index, data ->
            val currentProblemId = problemIds[index]
            solutionMap[currentProblemId] = data.first + data.second

            val problemLayout = root.findViewById<LinearLayout>(currentProblemId)
            val textViews = mutableListOf<TextView>()
            findAllTextViews(problemLayout, textViews)

            if (textViews.size >= 3) {
                textViews[0].text = data.first.toString()
                textViews[2].text = data.second.toString()
            }
        }

        shuffledAnswers.forEachIndexed { index, sumValue ->
            val currentAnswerId = answerIds[index]
            val frame = root.findViewById<FrameLayout>(currentAnswerId)
            val tv = findTextViewInViewGroup(frame)
            tv?.text = sumValue.toString()
        }
    }

    private fun findAllTextViews(view: View, list: MutableList<TextView>) {
        if (view is TextView) {
            list.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAllTextViews(view.getChildAt(i), list)
            }
        }
    }

    private fun setupClickListeners(root: View) {
        val problems = listOf(R.id.problem1, R.id.problem2, R.id.problem3)
        val answers = listOf(R.id.answer1, R.id.answer2, R.id.answer3)

        problems.forEach { id ->
            root.findViewById<View>(id)?.setOnClickListener { handleSelection(it, isProblem = true) }
        }

        answers.forEach { id ->
            root.findViewById<View>(id)?.setOnClickListener { handleSelection(it, isProblem = false) }
        }
    }

    private fun handleSelection(view: View, isProblem: Boolean) {
        if (isAnswerChecked || matchedIds.contains(view.id)) return

        if (isProblem) {
            selectedProblem?.let { if (!matchedIds.contains(it.id)) it.setBackgroundResource(R.drawable.answer_default_box) }
            selectedProblem = view
        } else {
            selectedAnswer?.let { if (!matchedIds.contains(it.id)) it.setBackgroundResource(R.drawable.answer_default_box) }
            selectedAnswer = view
        }

        view.setBackgroundResource(R.drawable.answer_blue_box)

        if (selectedProblem != null && selectedAnswer != null) {
            autoVerifyPair()
        }
    }

    private fun autoVerifyPair() {
        val prob = selectedProblem ?: return
        val ans = selectedAnswer ?: return

        val textView = findTextViewInViewGroup(ans as ViewGroup)
        val userValue = textView?.text.toString().toIntOrNull() ?: -1
        val correctValue = solutionMap[prob.id]

        if (userValue == correctValue) {
            playSound(R.raw.correct)
            matchedIds.add(prob.id)
            matchedIds.add(ans.id)

            prob.setBackgroundResource(R.drawable.answer_correct_box)
            ans.setBackgroundResource(R.drawable.answer_correct_box)

            clearSelectionRefs()

            if (matchedIds.size == 6) {
                showFinalSuccessState()
            }
        } else {
            playSound(R.raw.wrong)
            isFirstAttempt = false
            prob.setBackgroundResource(R.drawable.answer_incorrect_box)
            ans.setBackgroundResource(R.drawable.answer_incorrect_box)

            view?.postDelayed({
                if (!matchedIds.contains(prob.id)) prob.setBackgroundResource(R.drawable.answer_default_box)
                if (!matchedIds.contains(ans.id)) ans.setBackgroundResource(R.drawable.answer_default_box)
                clearSelectionRefs()
            }, 500)
        }
    }

    private fun showFinalSuccessState() {
        val activity = requireActivity() as Math2GradeQuestionActivity
        isAnswerChecked = true
        activity.isResultCurrentlyVisible = true
        activity.isCorrectAnswerShowing = true
        activity.playSuccessAnimation()

        stateContainer.visibility = View.VISIBLE
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)

        // Localized string for "All Matched!" or "Correct!"
        stateAnswer.text = getString(R.string.state_correct)

        val isFinished = activity.incrementProgress()

        if (isFirstAttempt) {
            activity.totalXp += MathGrade2Type.COLUMN_METHOD_ADDITION_MATCH.xp
        }

        activity.handleCorrectAnswer()

        checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)

        enableCheckButton()
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math2GradeQuestionActivity

            if (checkBtn.text == getString(R.string.btn_finish)) {
                activity.navigateToXpGained()
            } else {
                val isMilestoneActive = activity.checkAndTriggerMilestone()
                if (!isMilestoneActive) {
                    activity.isResultCurrentlyVisible = false
                    activity.hideSuccessAnimation()
                    stateContainer.visibility = View.INVISIBLE
                    setupInitialButtonState()
                    activity.showRandomQuestion()
                }
            }
        }
    }

    private fun clearSelectionRefs() {
        selectedProblem = null
        selectedAnswer = null
    }

    private fun findTextViewInViewGroup(group: ViewGroup): TextView? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is TextView) return child
            if (child is ViewGroup) {
                val found = findTextViewInViewGroup(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<View>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<View>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        requireActivity().findViewById<View>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<View>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun playSound(resId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
    }
}