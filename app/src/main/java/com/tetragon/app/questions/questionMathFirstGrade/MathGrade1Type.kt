package com.tetragon.app.questions.questionMathFirstGrade

import com.tetragon.app.questions.MathComplexity

enum class MathGrade1Type(val xp: Int, val complexity: MathComplexity) {
    // 1 topic
    APPLE(1, MathComplexity.EASY),
    APPLE_COUNT(1, MathComplexity.EASY),
    NUMBER_DRAWING(1, MathComplexity.EASY),
    HAND_COUNT(1, MathComplexity.EASY),
    FIND_MISSED_NUMBER(2, MathComplexity.MEDIUM),
    ANGLES(2, MathComplexity.MEDIUM),
    FIND_LARGEST_NUMBER(2, MathComplexity.MEDIUM),
    FIND_SMALLEST_NUMBER(2, MathComplexity.MEDIUM),
    COUNT_BY(3, MathComplexity.HARD),
    FIND_NEXT_NUMBER(3, MathComplexity.HARD),
    COUNT_BY_DRAG(3, MathComplexity.HARD),
    NUMBER_CONNECTING(3, MathComplexity.HARD),

    // 2 topic
    ADDITION_NUMBERS(1, MathComplexity.EASY),
    ADDITION_VISUAL_PROBLEM(1, MathComplexity.EASY),
    FIND_COUNT_ON(1, MathComplexity.EASY),
    ADDITION_ANIMATION(1, MathComplexity.EASY),
    ADDITION_MISSED_NUMBER(2, MathComplexity.MEDIUM),
    ADDITION_TREE(2, MathComplexity.MEDIUM),
    ADDITION_MATCH(2, MathComplexity.MEDIUM),
    ADDITION_TRIPLE_ANIMATION(2, MathComplexity.MEDIUM),
    ADDITION_TWO_REPRESENTATION(3, MathComplexity.HARD),
    ADDITION_THREE_REPRESENTATION(3, MathComplexity.HARD),
    ADDITION_NUMBERS_REPEATED(3, MathComplexity.HARD),
    ADDITION_BALANCED(3, MathComplexity.HARD),

    // 3 topic
    SUBTRACTION_NUMBERS(1, MathComplexity.EASY),
    SUBTRACTION_ANIMATION(1, MathComplexity.EASY),
    SUBTRACTION_VISUAL_PROBLEM(1, MathComplexity.EASY),
    FIND_COUNT_BACK(1, MathComplexity.EASY),
    SUBTRACTION_MISSED_NUMBER(2, MathComplexity.MEDIUM),
    SUBTRACTION_DRAG(2, MathComplexity.MEDIUM),
    SUBTRACTION_DRAG_THREE(2, MathComplexity.MEDIUM),
    SUBTRACTION_MATCH(2, MathComplexity.MEDIUM),
    SUBTRACTION_TWO_REPRESENTATION(3, MathComplexity.HARD),
    SUBTRACTION_REPRESENTATION(3, MathComplexity.HARD),
    SUBTRACTION_TREE(3, MathComplexity.HARD),
    SUBTRACTION_BALANCED(3, MathComplexity.HARD),

    // 4 topic
    CONTINUE_SEQUENCE_ODD_OR_EVEN(1, MathComplexity.EASY),
    SUBTRACTION_ODD_EVEN(1, MathComplexity.EASY),
    ADDITION_ODD_EVEN(1, MathComplexity.EASY),
    ADDITION_PARITY_RULE(1, MathComplexity.EASY),
    EVEN_OR_ODD(2, MathComplexity.MEDIUM),
    FIND_EVEN_OR_ODD_SEQUENCE(2, MathComplexity.MEDIUM),
    FIND_PARITY_NUMBER(2, MathComplexity.MEDIUM),
    EVEN_NEIGHBORS(2, MathComplexity.MEDIUM),
    UP_TO_EVEN_OR_ODD(3, MathComplexity.HARD),
    PARITY_OPERATION(3, MathComplexity.HARD),
    CONNECT_PARITY(3, MathComplexity.HARD),
    CHOOSE_TWO_PARITY(3, MathComplexity.HARD),

    // 5 topic
    COMPARISON(3, MathComplexity.HARD),
    COMPARISON_TRUE_OR_FALSE(1, MathComplexity.EASY),
    COMPARISON_GRAPE_OR_STRAWBERRY(1, MathComplexity.MEDIUM),

    // 6 topic
    PARENTHESES(3, MathComplexity.EASY),
    PARENTHESES_MISSED(3, MathComplexity.MEDIUM),

    // 7 topic
    ADDITION_20(1, MathComplexity.EASY),
    ADDITION_MISSED_NUMBER_20(2, MathComplexity.MEDIUM),
    ADDITION_THREE_REPRESENTATION_20(2, MathComplexity.HARD),
    ADDITION_TWO_REPRESENT_20(2, MathComplexity.MEDIUM),
    ADDITION_VISUAL_PROBLEM_20(2, MathComplexity.MEDIUM),

    // 8 topic
    SUBTRACTION_MISSED_NUMBER_20(2, MathComplexity.MEDIUM),
    SUBTRACTION_TWO_REPRESENTATION_20(2, MathComplexity.HARD),
    SUBTRACTION_NUMBERS_20(2, MathComplexity.EASY),

    // 9 topic
    CONTINUE_SEQUENCE_ROUND_NUMBERS(1, MathComplexity.EASY),
    ARITHMETICS_ROUND_NUMBERS(2, MathComplexity.MEDIUM),
    CLOSEST_ROUND_NUMBERS(1, MathComplexity.EASY),
    HOW_MANY_TENS_ROUND_NUMBERS(2, MathComplexity.HARD),

    // 10 topic
    COMPARISON_TWO_DIGITS(2, MathComplexity.EASY),
    ARITHMETICS_TWO_DIGIT_NUMBERS(3, MathComplexity.HARD),
    COMPARISON_TRUE_OR_FALSE_TWO_DIGIT_NUMBERS(2, MathComplexity.MEDIUM),
    ADDITION_TWO_DIGIT_NUMBER_REPRESENTATION(2, MathComplexity.MEDIUM),
    ARITHMETICS_VISUAL_TWO_DIGIT_NUMBERS_PROBLEM(2, MathComplexity.MEDIUM),
    PARENTHESES_TWO_DIGIT_NUMBERS(3, MathComplexity.HARD),
    PARENTHESES_MISSED_TWO_DIGIT_NUMBERS(3, MathComplexity.HARD),

    // 11 topic
    FISH(2, MathComplexity.EASY),
    AZIZ_APPLE_PROBLEM(2, MathComplexity.MEDIUM),

    // 12 topic
    CM_RULER(2, MathComplexity.EASY),
    DM_IN_CM(3, MathComplexity.MEDIUM),
}