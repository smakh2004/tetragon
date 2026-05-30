package com.tetragon.app.questions.questionMathFirstGrade

import com.tetragon.app.questions.MathComplexity

enum class MathGrade1Type(val xp: Int, val complexity: MathComplexity) {
    APPLE(1, MathComplexity.EASY),
    FIND_MISSED_NUMBER(2, MathComplexity.MEDIUM),
    FIND_NEXT_NUMBER(1, MathComplexity.EASY),
    ANGLES(3, MathComplexity.HARD),

    ADDITION_NUMBERS(1, MathComplexity.EASY),
    ADDITION_MISSED_NUMBER(2, MathComplexity.MEDIUM),
    ADDITION_TWO_REPRESENTATION(2, MathComplexity.HARD),
    ADDITION_THREE_REPRESENTATION(3, MathComplexity.HARD),
    ADDITION_VISUAL_PROBLEM(2, MathComplexity.EASY),
    ADDITION_TREE(2, MathComplexity.MEDIUM),
    BASIC_AI_ADDITION(2, MathComplexity.MEDIUM),

    SUBTRACTION_NUMBERS(2, MathComplexity.EASY),
    SUBTRACTION_MISSED_NUMBER(2, MathComplexity.MEDIUM),
    SUBTRACTION_TWO_REPRESENTATION(2, MathComplexity.HARD),

    UP_TO_EVEN_OR_ODD(3, MathComplexity.HARD),
    EVEN_OR_ODD(2, MathComplexity.MEDIUM),
    FIND_EVEN_OR_ODD_SEQUENCE(2, MathComplexity.MEDIUM),
    CONTINUE_SEQUENCE_ODD_OR_EVEN(1, MathComplexity.EASY),

    COMPARISON(3, MathComplexity.HARD),
    COMPARISON_TRUE_OR_FALSE(1, MathComplexity.EASY),
    COMPARISON_GRAPE_OR_STRAWBERRY(1, MathComplexity.MEDIUM),

    PARENTHESES(3, MathComplexity.EASY),
    PARENTHESES_MISSED(3, MathComplexity.MEDIUM),

    ADDITION_20(1, MathComplexity.EASY),
    ADDITION_MISSED_NUMBER_20(2, MathComplexity.MEDIUM),
    ADDITION_THREE_REPRESENTATION_20(2, MathComplexity.HARD),
    ADDITION_TWO_REPRESENT_20(2, MathComplexity.MEDIUM),
    ADDITION_VISUAL_PROBLEM_20(2, MathComplexity.MEDIUM),

    SUBTRACTION_MISSED_NUMBER_20(2, MathComplexity.MEDIUM),
    SUBTRACTION_TWO_REPRESENTATION_20(2, MathComplexity.HARD),
    SUBTRACTION_NUMBERS_20(2, MathComplexity.EASY),

    CONTINUE_SEQUENCE_ROUND_NUMBERS(1, MathComplexity.EASY),
    ARITHMETICS_ROUND_NUMBERS(2, MathComplexity.MEDIUM),
    CLOSEST_ROUND_NUMBERS(1, MathComplexity.EASY),
    HOW_MANY_TENS_ROUND_NUMBERS(2, MathComplexity.HARD),

    COMPARISON_TWO_DIGITS(2, MathComplexity.EASY),
    ARITHMETICS_TWO_DIGIT_NUMBERS(3, MathComplexity.HARD),
    COMPARISON_TRUE_OR_FALSE_TWO_DIGIT_NUMBERS(2, MathComplexity.MEDIUM),
    ADDITION_TWO_DIGIT_NUMBER_REPRESENTATION(2, MathComplexity.MEDIUM),
    ARITHMETICS_VISUAL_TWO_DIGIT_NUMBERS_PROBLEM(2, MathComplexity.MEDIUM),
    PARENTHESES_TWO_DIGIT_NUMBERS(3, MathComplexity.HARD),
    PARENTHESES_MISSED_TWO_DIGIT_NUMBERS(3, MathComplexity.HARD),

    FISH(2, MathComplexity.EASY),
    AZIZ_APPLE_PROBLEM(2, MathComplexity.MEDIUM),

    CM_RULER(2, MathComplexity.EASY),
    DM_IN_CM(3, MathComplexity.MEDIUM),
}