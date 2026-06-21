package com.tetragon.app.questions.questionMathFifthGrade

import com.tetragon.app.questions.MathComplexity

enum class MathGrade5Type(val xp: Int, val complexity: MathComplexity) {
    WRITE_WITH_DIGITS(1, MathComplexity.EASY),
    NUMBER_BEFORE_AFTER(1, MathComplexity.EASY),
    IS_NATURAL(1, MathComplexity.EASY),
    ADDITION_NATURAL_NUMBERS(1, MathComplexity.EASY),
    NUMBER_OF_NATURAL_NUMBERS(2, MathComplexity.MEDIUM),
    AVERAGE_OF_NATURAL_NUMBERS(2, MathComplexity.MEDIUM),
    SUM_OF_EVEN_NUMBERS(2, MathComplexity.MEDIUM),
    SHOW_MATCH_SEGMENT(2, MathComplexity.MEDIUM),
    SUM_OF_FIRST_NATURAL_NUMBERS(3, MathComplexity.HARD),
    FIND_N_IF_SUM_OF_NAT_NUM(3, MathComplexity.HARD),
    SUM_OF_FIRST_NATURAL_NUMBERS_DIVISIBLE(3, MathComplexity.HARD),
    FIND_SUM_OF_SQUARES_IF_N(3, MathComplexity.HARD),

}