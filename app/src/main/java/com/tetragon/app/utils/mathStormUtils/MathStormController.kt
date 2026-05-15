package com.tetragon.app.utils.mathStormUtils

import android.os.CountDownTimer
import com.tetragon.app.gameModel.MathStormModel

class MathStormController(
    private val onInputChanged: (String) -> Unit,
    private val onProblemChanged: (MathStormModel) -> Unit,
    private val onScoreChanged: (Int) -> Unit,
    private val onMistakeChanged: (Int) -> Unit,
    private val onCorrectChanged: (Boolean) -> Unit,
    private val onCountdownTick: (Int) -> Unit,
    private val onQuizFinished: (score: Int) -> Unit
) {

    private var currentInput = ""
    private var currentProblem: MathStormModel? = null
    private val generator = MathProblemGenerator()

    private var score = 0
    private var mistakes = 0

    private var quizTimer: CountDownTimer? = null
    private var overlayCountdownTimer: CountDownTimer? = null // Reference to stop crashes
    private val totalTimeMillis = 3 * 60 * 1000L // 3 minutes

    // ---------------- Input Handling ----------------
    fun addInput(value: String) {
        currentInput += value
        onInputChanged(currentInput)
    }

    fun deleteInput() {
        if (currentInput.isNotEmpty()) {
            currentInput = currentInput.dropLast(1)
            onInputChanged(currentInput)
        }
    }

    fun getCurrentInput() = currentInput

    // ---------------- Problem Handling ----------------
    fun showNextProblem() {
        currentProblem = generator.getNextProblem()
        currentInput = ""
        onInputChanged(currentInput)
        currentProblem?.let { onProblemChanged(it) }
    }

    // ---------------- Answer Checking ----------------
    fun checkAnswer() {
        val answerInt = currentInput.toIntOrNull()
        if (answerInt != null && answerInt == currentProblem?.answer) {
            score++
            onScoreChanged(score)
            onCorrectChanged(true)
            showNextProblem()
        } else {
            mistakes++
            onMistakeChanged(mistakes)
            if (mistakes >= 3) finishQuiz()
            else {
                currentInput = ""
                onInputChanged(currentInput)
            }
        }
    }

    // ---------------- Quiz Timer ----------------
    fun startQuizTimer() {
        quizTimer?.cancel()
        quizTimer = object : CountDownTimer(totalTimeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                onCountdownTick(secondsRemaining)
            }

            override fun onFinish() {
                finishQuiz()
            }
        }.start()
    }

    fun cancelQuizTimer() {
        quizTimer?.cancel()
    }

    // ---------------- Countdown Overlay (Safe Version) ----------------
    fun startCountdown(seconds: Int, tick: (Int) -> Unit, finish: () -> Unit) {
        overlayCountdownTimer?.cancel()
        overlayCountdownTimer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            var current = seconds
            override fun onTick(millisUntilFinished: Long) {
                if (current > 0) tick(current)
                current--
            }

            override fun onFinish() {
                finish()
            }
        }.start()
    }

    fun cancelCountdown() {
        overlayCountdownTimer?.cancel()
        overlayCountdownTimer = null
    }

    // ---------------- Finish Quiz ----------------
    private fun finishQuiz() {
        quizTimer?.cancel()
        onQuizFinished(score)
    }

    fun getCurrentScore(): Int {
        return score
    }
}