package com.tetragon.app.gameModel

fun calculateLevel(xp: Long): Int {
    return (xp / 1000 + 1).toInt()
}