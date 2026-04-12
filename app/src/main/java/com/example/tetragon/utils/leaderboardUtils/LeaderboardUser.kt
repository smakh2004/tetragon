package com.example.tetragon.utils.leaderboardUtils

data class LeaderboardUser(
    val firstName: String = "",
    val lastName: String = "",
    val monthlyXP: Long = 0,
    val email: String = "",
    val uid: String = "",
    var isOnline: Boolean = false
)