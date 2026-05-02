package com.example.tetragon.questions.questionMathSixthGrade.firstTopic

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathSixthGrade.Math6GradeQuestionActivity
import com.example.tetragon.questions.questionMathSixthGrade.MathGrade6Type

class UiFractionsAdditionFragment : Fragment(R.layout.fragment_ui_fractions_addition) {

    // Problem Views
    private lateinit var tvNum1: TextView
    private lateinit var tvDenom1: TextView
    private lateinit var tvOperator: TextView
    private lateinit var tvNum2: TextView
    private lateinit var tvDenom2: TextView
    private lateinit var problemImage: ImageView

    // Option Container Views
    private lateinit var options: List<LinearLayout>
    private lateinit var optionNumTexts: List<TextView>
    private lateinit var optionDenomTexts: List<TextView>

    // Activity Shared Views
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var correctAnswerString = ""
    private var optionStrings = mutableListOf<String>()
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    data class FractionParts(val num: String, val denom: String)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupInitialButtonState()
        generateProblem()
        setupOptionClicks()
        setupCheckButton()
    }

    private fun initViews(view: View) {
        tvNum1 = view.findViewById(R.id.num1)
        tvDenom1 = view.findViewById(R.id.denom1)
        tvOperator = view.findViewById(R.id.tvOperator)
        tvNum2 = view.findViewById(R.id.num2)
        tvDenom2 = view.findViewById(R.id.denom2)
        problemImage = view.findViewById(R.id.problemImage)

        options = listOf(view.findViewById(R.id.option1), view.findViewById(R.id.option2), view.findViewById(R.id.option3))

        optionNumTexts = listOf(view.findViewById(R.id.optionNum1), view.findViewById(R.id.optionNum2), view.findViewById(R.id.optionNum3))
        optionDenomTexts = listOf(view.findViewById(R.id.optionDenom1), view.findViewById(R.id.optionDenom2), view.findViewById(R.id.optionDenom3))

        val activity = requireActivity() as Math6GradeQuestionActivity
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)
    }

    private fun generateProblem() {
        val commonDenom = listOf(4, 6, 8, 10, 12).random()
        val isSubtraction = (0..1).random() == 1

        tvOperator.text = if (isSubtraction) "-" else "+"

        var n1 = (1 until commonDenom).random()
        var n2 = (1 until commonDenom).random()

        if (isSubtraction) {
            if (n2 >= n1) {
                val temp = n1; n1 = n2; n2 = temp
                if (n1 == n2) n1 += 1
            }
        }

        tvNum1.text = n1.toString(); tvDenom1.text = commonDenom.toString()
        tvNum2.text = n2.toString(); tvDenom2.text = commonDenom.toString()

        val resN = if (isSubtraction) n1 - n2 else n1 + n2

        correctAnswerString = simplifyFraction(resN, commonDenom)
        setupOptions()
        resetProblemUI()
    }

    private fun setupOptions() {
        val wrongAnswers = mutableSetOf<String>()
        while (wrongAnswers.size < 2) {
            val n = (1..15).random()
            val d = listOf(2, 3, 4, 5, 6, 8, 10, 12).random()
            val candidate = simplifyFraction(n, d)
            if (candidate != correctAnswerString) wrongAnswers.add(candidate)
        }

        optionStrings = (wrongAnswers.toList() + correctAnswerString).shuffled().toMutableList()

        optionStrings.forEachIndexed { i, s ->
            val parts = parseFraction(s)
            optionNumTexts[i].text = parts.num
            optionDenomTexts[i].text = parts.denom
            options[i].setBackgroundResource(R.drawable.custom_background)
        }
    }

    private fun parseFraction(s: String): FractionParts {
        return if (s.contains("/")) {
            val split = s.split("/")
            FractionParts(split[0], split[1])
        } else {
            FractionParts(s, "1")
        }
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                selectedOptionIndex = index
                options.forEachIndexed { i, l ->
                    l.setBackgroundResource(if (i == index) R.drawable.option_selected else R.drawable.custom_background)
                }

                checkBtn.isEnabled = true
                requireActivity().findViewById<View>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
                requireActivity().findViewById<View>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
            }
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val chosen = optionStrings[index]
        val activity = requireActivity() as Math6GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (chosen == correctAnswerString) {
            playSound(R.raw.correct)
            activity.playSuccessAnimation()
            if (isFirstAttempt) activity.totalXp += MathGrade6Type.ADDITION_FRACTIONS.xp
            activity.handleCorrectAnswer()
            val isFinished = activity.incrementProgress()
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showResultState(true, index, stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showResultState(false, index, stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun showResultState(isCorrect: Boolean, index: Int, container: FrameLayout, icon: ImageView) {
        if (isCorrect) {
            container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
            icon.setImageResource(R.drawable.correct_tick_icon)
            options[index].setBackgroundResource(R.drawable.option_correct)
            stateAnswer.text = getString(R.string.state_correct)
            answer.text = getString(R.string.label_answer, correctAnswerString)
            answer.visibility = View.VISIBLE
            applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
        } else {
            container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
            icon.setImageResource(R.drawable.wrong_circle)
            options[index].setBackgroundResource(R.drawable.option_incorrect)
            stateAnswer.text = getString(R.string.state_incorrect)
            answer.visibility = View.GONE
            seeBtn.visibility = View.VISIBLE
            seeEnabledButton.text = getString(R.string.btn_see_solution)
            applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
        }
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer, correctAnswerString)
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            options.forEachIndexed { i, layout ->
                layout.setBackgroundResource(if (optionStrings[i] == correctAnswerString) R.drawable.option_showed else R.drawable.custom_background)
            }
        }
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)
            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it, stateContainer, circleState) }
            } else {
                if (isIncorrectAttempt) resetForTryAgain()
                else {
                    val activity = requireActivity() as Math6GradeQuestionActivity
                    if (checkBtn.text == getString(R.string.btn_finish)) activity.navigateToXpGained()
                    else {
                        activity.isResultCurrentlyVisible = false
                        activity.hideSuccessAnimation()
                        stateContainer.visibility = View.INVISIBLE
                        activity.showRandomQuestion()
                    }
                }
            }
        }
    }

    private fun resetProblemUI() {
        problemImage.setImageResource(R.drawable.answer_blue_box)
        seeBtn.visibility = View.GONE
        isAnswerChecked = false; isIncorrectAttempt = false; isFirstAttempt = true
        selectedOptionIndex = null;
        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math6GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
        isAnswerChecked = false; isIncorrectAttempt = false; selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
        problemImage.setImageResource(R.drawable.answer_blue_box)
        stateContainer.visibility = View.INVISIBLE; seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
    }

    private fun simplifyFraction(n: Int, d: Int): String {
        val common = gcd(n, d)
        return "${n / common}/${d / common}"
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun playSound(id: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), id)
        mediaPlayer?.start()
    }

    private fun applyButtonColors(btn: Int, btnB: Int, layB: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btn)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), btnB)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), layB))
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
        requireActivity().findViewById<View>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<View>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    override fun onDestroy() { super.onDestroy(); mediaPlayer?.release() }
}