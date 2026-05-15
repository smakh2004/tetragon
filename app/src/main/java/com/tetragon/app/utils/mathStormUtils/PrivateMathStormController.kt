package com.tetragon.app.utils.mathStormUtils

import com.tetragon.app.gameModel.MathStormModel

class PrivateMathStormController(
    private val onProblem: (MathStormModel) -> Unit,
    private val onInput: (String) -> Unit,
    private val onScore: (Int) -> Unit,
    private val onMistake: (Int) -> Unit,
    private val onFinish: () -> Unit
) {

    private val generator = MathProblemGenerator()
    private var currentProblem: MathStormModel? = null

    private var input = ""
    private var score = 0
    private var mistakes = 0
    private val MAX_MISTAKES = 3

    fun start() {
        nextProblem()
    }

    fun add(value: String) {
        input += value
        onInput(input)
    }

    fun delete() {
        if (input.isNotEmpty()) {
            input = input.dropLast(1)
            onInput(input)
        }
    }

    fun check() {
        val ans = input.toIntOrNull()
        if (ans == currentProblem?.answer) {
            score++
            onScore(score)
            nextProblem()
        } else {
            mistakes++
            onMistake(mistakes)
            if (mistakes >= MAX_MISTAKES) onFinish()
            else {
                input = ""
                onInput(input)
            }
        }
    }

    private fun nextProblem() {
        currentProblem = generator.getNextProblem()
        input = ""
        onInput(input)
        onProblem(currentProblem!!)
    }
}