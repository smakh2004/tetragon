package com.tetragon.app.ui.uiCashStorm

import android.os.CountDownTimer

data class ProfitProblem(
    val baseAmount: Int,
    val option1Text: String,
    val option2Text: String,
    val val1Result: Double,
    val val2Result: Double,
    val correctIndex: Int
)

class ProfitStormController(
    private val onProblemChanged: (ProfitProblem) -> Unit,
    private val onScoreChanged: (Int) -> Unit,
    private val onMistakeChanged: (Int) -> Unit,
    private val onCountdownTick: (Int) -> Unit,
    private val onQuizFinished: (Int) -> Unit
) {
    private var score = 0
    private var mistakes = 0
    private var currentProblem: ProfitProblem? = null
    private var quizTimer: CountDownTimer? = null
    private var countdownTimer: CountDownTimer? = null

    fun startCountdown(seconds: Int, tick: (Int) -> Unit, finish: () -> Unit) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(ms: Long) { tick((ms / 1000).toInt() + 1) }
            override fun onFinish() { finish() }
        }.start()
    }

    fun generateProblem() {
        val level = when {
            score <= 10 -> "EASY"
            score <= 20 -> "MEDIUM"
            else -> "HARD"
        }

        val base = when (level) {
            "EASY" -> (10..100).random()
            "MEDIUM" -> (100..500).random()
            else -> (500..2000).random()
        }

        val isProfitSet = (0..1).random() == 1

        fun getOperation(): Pair<String, Double> {
            val type = when (level) {
                "EASY" -> 1
                "MEDIUM" -> (1..2).random()
                else -> (1..4).random()
            }

            return when (type) {
                1 -> { // Fixed Amount ($)
                    val range = if (level == "EASY") 5..20 else 50..250
                    val amt = range.random()

                    if (isProfitSet) {
                        // Result: +$50
                        Pair("+$$amt", base.toDouble() + amt)
                    } else {
                        // Result: -$50
                        Pair("-$$amt", base.toDouble() - amt)
                    }
                }
                2 -> { // Percentage (%)
                    val pcts = if (level == "MEDIUM") listOf(10, 20, 50) else listOf(15, 25, 30, 75)
                    val pct = pcts.random()

                    if (isProfitSet) {
                        // Result: +10%
                        Pair("+$pct%", base.toDouble() * (1 + pct / 100.0))
                    } else {
                        // Result: -10%
                        Pair("-$pct%", base.toDouble() * (1 - pct / 100.0))
                    }
                }
                3 -> { // Multiplier (×)
                    val mult = if (isProfitSet) listOf(1.2, 1.5, 2.0).random()
                    else listOf(0.2, 0.5, 0.8).random()
                    // Result: ×1.5
                    Pair("×$mult", base.toDouble() * mult)
                }
                else -> { // Fractions (/)
                    val frac = if (isProfitSet) listOf(3 to 2, 5 to 4, 2 to 1).random()
                    else listOf(1 to 2, 3 to 4, 2 to 5).random()
                    val result = base.toDouble() * (frac.first.toDouble() / frac.second.toDouble())
                    // Result: ×3/2
                    Pair("×${frac.first}/${frac.second}", result)
                }
            }
        }

        var op1 = getOperation()
        var op2 = getOperation()
        while (op1.second == op2.second) { op2 = getOperation() }

        val correct = if (op1.second > op2.second) 1 else 2
        currentProblem = ProfitProblem(base, op1.first, op2.first, op1.second, op2.second, correct)
        onProblemChanged(currentProblem!!)
    }

    fun checkAnswer(selectedIndex: Int) {
        if (currentProblem == null || selectedIndex == 0) return
        if (selectedIndex == currentProblem?.correctIndex) {
            score++
            onScoreChanged(score)
            generateProblem()
        } else {
            mistakes++
            onMistakeChanged(mistakes)
            if (mistakes < 3) generateProblem() else {
                cancelQuizTimer()
                onQuizFinished(score)
            }
        }
    }

    fun startQuizTimer(seconds: Int) {
        quizTimer?.cancel()
        quizTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(ms: Long) { onCountdownTick((ms / 1000).toInt()) }
            override fun onFinish() { onQuizFinished(score) }
        }.start()
    }

    fun cancelQuizTimer() {
        quizTimer?.cancel()
        countdownTimer?.cancel()
    }

    fun getCurrentScore() = score
}