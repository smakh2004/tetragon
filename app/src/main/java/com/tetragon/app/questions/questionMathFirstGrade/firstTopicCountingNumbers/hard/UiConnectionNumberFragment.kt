package com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.hard

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
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
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

private const val ARG_MAX_NUMBER = "max_number"

class UiConnectionNumberFragment : Fragment(R.layout.fragment_ui_connection_number) {

    private lateinit var instructionText: TextView
    private lateinit var connectView: ConnectNumbersView

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: ConstraintLayout
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private lateinit var teacherAnimation: RiveAnimationView
    private lateinit var lipSync: TeacherLipSync
    private lateinit var speech: TeacherSpeech

    private var maxNumber: Int = 10
    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var mediaPlayer: MediaPlayer? = null

    /** Bumped by every interaction so delayed callbacks can tell they're stale. */
    private var interactionToken = 0

    private val RIVE_CORRECT = "correct"
    private val RIVE_INCORRECT = "incorrect"
    private val RIVE_EXPLAIN = "explain"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maxNumber = arguments?.getInt(ARG_MAX_NUMBER) ?: Random.nextInt(5, 11) // 5..10
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupInstructionText()
        setupInitialButtonState()

        connectView.setTargetNumber(maxNumber)

        // Enable the check button once the child has drawn at least one link.
        connectView.onProgress = { count ->
            if (!isAnswerChecked) {
                if (count >= 2 && !checkBtn.isEnabled) enableCheckButton()
                else if (count < 2 && checkBtn.isEnabled) disableCheckButton()
            }
        }
        connectView.onComplete = {
            if (!isAnswerChecked && !checkBtn.isEnabled) enableCheckButton()
        }

        setupCheckButton()
        repeatQuestion()
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
        text = spokenText(R.string.connect_numbers, maxNumber),
        clips = UzPhrases.connectNumbers(maxNumber)
    )

    private fun tryAgainLine(): SpokenLine = SpokenLine(
        text = spokenText(R.string.teacher_try_again),
        clips = UzPhrases.tryAgain()
    )

    private fun solutionLine(): SpokenLine = SpokenLine(
        text = spokenText(R.string.explain_connect_numbers, maxNumber),
        clips = UzPhrases.connectNumbersSolution(maxNumber)
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

    private fun repeatQuestion() {
        newInteraction()
        cancelSpeechAndSound()
        speech.speak(questionLine())
    }

    // ------------------------------------------------------------------- setup

    private fun initViews(view: View) {
        instructionText = view.findViewById(R.id.instructionText)
        connectView = view.findViewById(R.id.connectView)

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
        val fullText = getString(R.string.connect_numbers, maxNumber)
        val spannable = SpannableString(fullText)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.blue_2)

        // Highlight the number "1"
        val startOne = fullText.indexOf("1")
        if (startOne != -1) {
            spannable.setSpan(
                ForegroundColorSpan(blueColor),
                startOne, startOne + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Highlight the target max number (e.g. 10)
        val wordToStyle = maxNumber.toString()
        val startMax = fullText.lastIndexOf(wordToStyle)
        if (startMax != -1 && startMax != startOne) {
            spannable.setSpan(
                ForegroundColorSpan(blueColor),
                startMax, startMax + wordToStyle.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        instructionText.text = spannable
    }

    // ---- Check button ------------------------------------------------------

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            newInteraction()
            cancelSpeechAndSound()

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
                    resetUIForNext()
                    val isMilestoneActive = activity.checkAndTriggerMilestone()
                    if (!isMilestoneActive) activity.showRandomQuestion()
                }
            }
        }
    }

    private fun checkAnswer(stateContainer: FrameLayout, circleState: ImageView) {
        isAnswerChecked = true
        connectView.setLocked(true)
        val activity = requireActivity() as Math1GradeQuestionActivity

        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (connectView.isComplete()) {
            connectView.markCorrect()
            playSound(R.raw.correct)
            lipSync.fireTrigger(RIVE_CORRECT)

            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) activity.totalXp += MathGrade1Type.NUMBER_DRAWING.xp
            activity.handleCorrectAnswer()

            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            val token = interactionToken
            connectView.markIncorrect()
            playSound(R.raw.wrong) {
                if (isStale(token)) return@playSound
                speech.speak(
                    tryAgainLine(),
                    onStarted = { if (!isStale(token)) lipSync.setBoolean(RIVE_INCORRECT, true) },
                    onFinished = { lipSync.setBoolean(RIVE_INCORRECT, false) }
                )
            }
            activity.isCorrectAnswerShowing = false
            activity.handleIncorrectAnswer()

            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answer.visibility = View.VISIBLE
        answer.text = getString(R.string.desc_correct)
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
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

            // Step-by-step logic explanation
            val solutionExplanation = getString(R.string.explain_connect_numbers, maxNumber)
            answer.text = solutionExplanation
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            // Draw the full 1..N path in gray as the solution
            connectView.completePath()

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isAnswerChecked = true
            isIncorrectAttempt = false
            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))

            speech.speak(
                solutionLine(),
                onStarted = { if (!isStale(token)) lipSync.setBoolean(RIVE_EXPLAIN, true) },
                onFinished = { lipSync.setBoolean(RIVE_EXPLAIN, false) }
            )
        }
    }

    // ---- State helpers -----------------------------------------------------

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
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

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        requireActivity().findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        requireActivity().findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun resetForTryAgain() {
        newInteraction()
        cancelSpeechAndSound()
        lipSync.clearBooleans()

        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        isAnswerChecked = false
        isIncorrectAttempt = false

        connectView.clearConnections()
        connectView.setLocked(false)

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE

        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        newInteraction()
        cancelSpeechAndSound()
        lipSync.clearBooleans()

        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        if (activity.isCorrectAnswerShowing) activity.hideSuccessAnimation()
        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        setupInitialButtonState()
    }

    private fun playSound(soundResId: Int, onComplete: (() -> Unit)? = null) {
        stopSound()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
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

    companion object {
        @JvmStatic
        fun newInstance(maxNumber: Int) =
            UiConnectionNumberFragment().apply {
                arguments = Bundle().apply { putInt(ARG_MAX_NUMBER, maxNumber) }
            }
    }
}