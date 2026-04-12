package com.example.tetragon.gameModel

fun calculateLevel(xp: Long): Int {
    return (xp / 1000 + 1).toInt()
}