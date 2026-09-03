package com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.medium

import android.content.res.Configuration
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
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.tetragon.app.questions.questionMathFirstGrade.MathGrade1Type
import com.tetragon.app.utils.voiceReader.SpokenLine
import com.tetragon.app.utils.voiceReader.TeacherLipSync
import com.tetragon.app.utils.voiceReader.TeacherSpeech
import com.tetragon.app.utils.voiceReader.UzPhrases
import java.util.Locale
import kotlin.random.Random

class UiFindMIssedNumberInSequenceFragment : Fragment(R.layout.fragment_ui_find_missed_number) {

    private lateinit var firstNumberText: TextView
    private lateinit var secondNumberText: TextView
    private lateinit var instructionText: TextView
    private lateinit var problemImage: ImageView
    private lateinit var problemAnswerText: TextView
    private lateinit var options: List<LinearLayout>
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var correctAnswer = 0
    private var selectedOptionIndex: Int? = null
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isInitialized = false
    private var mediaPlayer: MediaPlayer? = null

    private var interactionToken = 0

    private val RIVE_CORRECT = "correct"
    private val RIVE_INCORRECT = "incorrect"
    private val RIVE_EXPLAIN = "explain"

    private lateinit var teacherAnimation: RiveAnimationView
    private lateinit var lipSync: TeacherLipSync
    private lateinit var speech: TeacherSpeech

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupInstructionText()
        setupInitialButtonState()

        if (!isInitialized) {
            teacherAnimation.post {
                generateProblem()
                isInitialized = true
            }
        }

        setupOptionClicks()
        setupCheckButton()
    }

    // ------------------------------------------------------------ speech language

    private fun uiLanguage(): String = resources.configuration.locales[0].language

    private fun isUzbek(): Boolean = uiLanguage() == TeacherSpeech.UZ

    /**
     * Locale for the TextToSpeech fallback only. Uzbek is spoken from clips, but
     * if a clip is ever missing the line is read in Russian rather than dropped.
     */
    private fun fallbackLocale(): Locale =
        if (isUzbek()) Locale("ru") else resources.configuration.locales[0]

    /** Text for the TTS fallback, rendered in [fallbackLocale]. */
    private fun spokenText(@StringRes id: Int, vararg args: Any): String {
        if (!isUzbek()) return getString(id, *args)
        val conf = Configuration(resources.configuration)
        conf.setLocale(Locale("ru"))
        return requireContext().createConfigurationContext(conf).getString(id, *args)
    }

    // ---------------------------------------------------------------- the lines

    private fun questionLine(): SpokenLine = SpokenLine(
        text = spokenText(R.string.find_the_missed_number),
        clips = UzPhrases.findMissedNumber()
    )

    private fun tryAgainLine(): SpokenLine = SpokenLine(
        text = spokenText(R.string.teacher_try_again),
        clips = UzPhrases.tryAgain()
    )

    private fun answerLine(): SpokenLine = SpokenLine(
        text = spokenText(R.string.label_answer, correctAnswer.toString()),
        clips = UzPhrases.answerIs(correctAnswer)
    )

    // ------------------------------------------------------------------ tokens

    private fun newInteraction(): Int {
        interactionToken++
        return interactionToken
    }

    private fun isStale(token: Int) = token != interactionToken

    private fun cancelSpeechAndSound() {
        stopSound()
        speech.stop()
    }

    // ------------------------------------------------------------------- setup

    private fun initViews(view: View) {
        firstNumberText = view.findViewById(R.id.firstNumber)
        secondNumberText = view.findViewById(R.id.secondNumber)
        problemImage = view.findViewById(R.id.problemImage)
        problemAnswerText = view.findViewById(R.id.problemAnswerText)
        instructionText = view.findViewById(R.id.instructionText)

        options = listOf(
            view.findViewById(R.id.option1),
            view.findViewById(R.id.option2),
            view.findViewById(R.id.option3)
        )

        val activity = requireActivity()
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)

        teacherAnimation = view.findViewById(R.id.teacherAnimation)
        lipSync = TeacherLipSync(teacherAnimation, language = uiLanguage())
        lipSync.prepare()
        speech = TeacherSpeech(requireContext(), lipSync, uiLanguage(), fallbackLocale())
        teacherAnimation.setOnClickListener { repeatQuestion() }
    }

    private fun setupInstructionText() {
        val fullText = getString(R.string.find_the_missed_number)
        val spannable = SpannableString(fullText)

        val keyWords = listOf("missed", "пропущенное", "пропущенные", "tushirib")
        val wordToStyle = keyWords.firstOrNull { fullText.contains(it, ignoreCase = true) }.orEmpty()

        val start = fullText.indexOf(wordToStyle, ignoreCase = true)
        if (start != -1 && wordToStyle.isNotEmpty()) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue_2)),
                start,
                start + wordToStyle.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        instructionText.text = spannable
    }

    private fun repeatQuestion() {
        newInteraction()
        cancelSpeechAndSound()
        speech.speak(questionLine())
    }

    private fun generateProblem() {
        newInteraction()

        val step = 1
        val start = Random.nextInt(1, 9)
        val sequence = listOf(start, start + step, start + step * 2)

        correctAnswer = sequence[1]

        firstNumberText.text = "${sequence[0]}, "
        secondNumberText.text = ", ${sequence[2]}"

        val optionSet = mutableSetOf(correctAnswer)
        while (optionSet.size < 3) {
            val random = Random.nextInt(1, 11)
            if (random != correctAnswer) optionSet.add(random)
        }

        val shuffledOptions = optionSet.shuffled()
        options.forEachIndexed { index, layout ->
            val tv = layout.getChildAt(0) as TextView
            tv.text = shuffledOptions[index].toString()
            layout.setBackgroundResource(R.drawable.custom_background)
        }

        problemAnswerText.visibility = View.GONE
        problemImage.setImageResource(R.drawable.answer_blue_box)

        selectedOptionIndex = null
        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        lipSync.clearBooleans()
        seeBtn.visibility = View.GONE

        disableCheckButton()
        setupInitialButtonState()
        repeatQuestion()
    }

    private fun setupOptionClicks() {
        options.forEachIndexed { index, layout ->
            layout.setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener
                newInteraction()
                cancelSpeechAndSound()
                selectedOptionIndex = index
                highlightSelectedOption(index)
                enableCheckButton()

                val chosen = (layout.getChildAt(0) as TextView).text.toString()
                problemAnswerText.text = chosen
                problemAnswerText.visibility = View.VISIBLE
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

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        checkBtn.text = getString(R.string.btn_check)
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            newInteraction()
            cancelSpeechAndSound()

            val stateContainer = requireActivity().findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = requireActivity().findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                selectedOptionIndex?.let { checkAnswer(it, stateContainer, circleState) }
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    val activity = requireActivity() as Math1GradeQuestionActivity
                    if (checkBtn.text == getString(R.string.btn_finish)) {
                        activity.navigateToXpGained()
                    } else {
                        resetUIForNext()
                        val isMilestoneActive = activity.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
    }

    private fun checkAnswer(index: Int, stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        val chosen = (options[index].getChildAt(0) as TextView).text.toString().toInt()
        val activity = requireActivity() as Math1GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE
        problemAnswerText.text = chosen.toString()
        problemAnswerText.visibility = View.VISIBLE

        if (chosen == correctAnswer) {
            playSound(R.raw.correct)
            lipSync.fireTrigger(RIVE_CORRECT)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            problemImage.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.FIND_MISSED_NUMBER.xp
            }
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState, index)
        } else {
            val token = interactionToken
            playSound(R.raw.wrong) {
                if (isStale(token)) return@playSound
                speech.speak(
                    tryAgainLine(),
                    onStarted = { if (!isStale(token)) lipSync.setBoolean(RIVE_INCORRECT, true) },
                    onFinished = { lipSync.setBoolean(RIVE_INCORRECT, false) }
                )
            }
            problemImage.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer()
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState, index)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        options[index].setBackgroundResource(R.drawable.option_correct)
        stateAnswer.text = getString(R.string.state_correct)
        answer.text = getString(R.string.label_answer, correctAnswer.toString())
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView, index: Int) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        options[index].setBackgroundResource(R.drawable.option_incorrect)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            val token = newInteraction()
            cancelSpeechAndSound()
            lipSync.clearBooleans()

            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer, correctAnswer.toString())
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)
            problemImage.setImageResource(R.drawable.answer_solution_box)
            problemAnswerText.text = correctAnswer.toString()
            problemAnswerText.visibility = View.VISIBLE
            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isAnswerChecked = true
            isIncorrectAttempt = false

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            options.forEach { layout ->
                val tv = layout.getChildAt(0) as TextView
                layout.setBackgroundResource(
                    if (tv.text.toString().toInt() == correctAnswer) R.drawable.option_showed
                    else R.drawable.custom_background
                )
            }

            speech.speak(
                answerLine(),
                onStarted = { if (!isStale(token)) lipSync.setBoolean(RIVE_EXPLAIN, true) },
                onFinished = { lipSync.setBoolean(RIVE_EXPLAIN, false) }
            )
        }
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
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        newInteraction()
        cancelSpeechAndSound()
        lipSync.clearBooleans()

        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        isAnswerChecked = false
        isIncorrectAttempt = false
        selectedOptionIndex = null
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }

        problemImage.setImageResource(R.drawable.answer_blue_box)
        problemAnswerText.visibility = View.GONE

        disableCheckButton()
        setupInitialButtonState()

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
    }

    private fun resetUIForNext() {
        newInteraction()
        cancelSpeechAndSound()
        lipSync.clearBooleans()

        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE

        disableCheckButton()
        setupInitialButtonState()
        options.forEach { it.setBackgroundResource(R.drawable.custom_background) }
    }

    private fun playSound(resId: Int, onComplete: (() -> Unit)? = null) {
        stopSound()
        mediaPlayer = MediaPlayer.create(requireContext(), resId)
        mediaPlayer?.setOnCompletionListener { player ->
            player.release()
            mediaPlayer = null
            if (isAdded) onComplete?.invoke()
        }
        mediaPlayer?.start()
    }

    private fun stopSound() {
        mediaPlayer?.let { player ->
            player.setOnCompletionListener(null)
            runCatching { if (player.isPlaying) player.stop() }
            player.release()
        }
        mediaPlayer = null
    }

    override fun onDestroyView() {
        newInteraction()
        stopSound()
        speech.release()
        lipSync.release()
        super.onDestroyView()
    }
}