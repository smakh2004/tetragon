package com.example.tetragon.gameModel

import com.google.firebase.Timestamp

data class UserData(
    var age: String = "",
    var firstName: String = "",
    var lastName: String = "",
    var email: String = "",
    var password: String = "",
    var online: Boolean = false,
    var lastOnline: Timestamp? = null,
    var registeredAt: Timestamp? = null,
    var xp: Long = 0,          // total XP (never resets)
    var monthlyXP: Long = 0,   // leaderboard XP (resets monthly)
    var level: Int = 1,
    var streak: Int = 0
)