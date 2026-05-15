package com.tetragon.app.utils.mathStormUtils

import com.tetragon.app.gameModel.MathStormModel
import kotlin.math.pow
import kotlin.random.Random

enum class Difficulty {
    EASY, LIGHT, LIGHT_MEDIUM, MEDIUM, HARD
}

class MathProblemGenerator {

    private var problemCount = 0

    fun getNextProblem(): MathStormModel {
        problemCount++
        val difficulty = getDifficultyForProblem(problemCount)
        return generateProblem(difficulty)
    }

    private fun getDifficultyForProblem(count: Int): Difficulty {
        return when {
            count <= 10 -> Difficulty.EASY
            count <= 20 -> Difficulty.LIGHT
            count <= 30 -> Difficulty.LIGHT_MEDIUM
            count <= 40 -> Difficulty.MEDIUM
            else -> Difficulty.HARD
        }
    }

    private fun generateProblem(difficulty: Difficulty): MathStormModel {
        return when (difficulty) {
            Difficulty.EASY -> generateEasy()
            Difficulty.LIGHT -> generateLight()
            Difficulty.LIGHT_MEDIUM -> generateLightMedium()
            Difficulty.MEDIUM -> generateMedium()
            Difficulty.HARD -> generateHard()
        }
    }

    // ---------------- EASY: simple addition/subtraction ----------------
    private fun generateEasy(): MathStormModel {
        val a = Random.nextInt(1, 10)
        val b = Random.nextInt(1, 10)
        return if (Random.nextBoolean()) {
            MathStormModel("$a + $b =", a + b)
        } else {
            val max = maxOf(a, b)
            val min = minOf(a, b)
            MathStormModel("$max - $min =", max - min)
        }
    }

    // ---------------- LIGHT: addition/subtraction with bigger numbers ----------------
    private fun generateLight(): MathStormModel {
        val a = Random.nextInt(10, 20)
        val b = Random.nextInt(1, 20)
        return if (Random.nextBoolean()) {
            MathStormModel("$a + $b =", a + b)
        } else {
            MathStormModel("${maxOf(a, b)} - ${minOf(a, b)} =", maxOf(a, b) - minOf(a, b))
        }
    }

    // ---------------- LIGHT_MEDIUM: multiplication/division ----------------
    private fun generateLightMedium(): MathStormModel {
        return if (Random.nextBoolean()) {
            val a = Random.nextInt(2, 10)
            val b = Random.nextInt(2, 10)
            MathStormModel("$a × $b =", a * b)
        } else {
            val b = Random.nextInt(2, 10)
            val answer = Random.nextInt(1, 10)
            val a = b * answer
            MathStormModel("$a ÷ $b =", answer)
        }
    }

    // ---------------- MEDIUM: powers and factorials (small numbers) ----------------
    private fun generateMedium(): MathStormModel {
        return if (Random.nextBoolean()) {
            // Power with small numbers
            val base = Random.nextInt(2, 6)   // 2..5
            val exponent = if (Random.nextBoolean()) 2 else 3 // ² or ³
            val expChar = if (exponent == 2) "²" else "³"
            MathStormModel("$base$expChar =", base.toDouble().pow(exponent).toInt())
        } else {
            // Factorial
            val n = Random.nextInt(1, 5) // 1! to 4!
            MathStormModel("$n! =", factorial(n))
        }
    }

    // ---------------- HARD: multi-step operations ----------------
    private fun generateHard(): MathStormModel {
        val a = Random.nextInt(2, 6)  // small base for power
        val exponent = if (Random.nextBoolean()) 2 else 3
        val expChar = if (exponent == 2) "²" else "³"
        val b = Random.nextInt(1, 10)

        return if (Random.nextBoolean()) {
            // (a² or a³) ± b
            val power = a.toDouble().pow(exponent).toInt()
            val op = if (Random.nextBoolean()) "+" else "-"
            val answer = if (op == "+") power + b else power - b
            MathStormModel("($a$expChar) $op $b =", answer)
        } else {
            // factorial ± small number
            val fact = factorial(a)
            val op = if (Random.nextBoolean()) "+" else "-"
            val answer = if (op == "+") fact + b else fact - b
            MathStormModel("($a!) $op $b =", answer)
        }
    }

    // ---------------- Factorial Helper ----------------
    private fun factorial(n: Int): Int {
        var result = 1
        for (i in 1..n) result *= i
        return result
    }
}