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
    var xp: Long = 0,
    var monthlyXP: Long = 0,
    var level: Int = 1,
    var streak: Int = 0,

    // --- UPDATED SUBSCRIPTION LOGIC ---
    // Instead of Boolean, we store the end date.
    // If null or time has passed, they are a free user.
    var subscriptionUntil: Timestamp? = null,

    // --- NEW VALUES ---
    var coins: Long = 30,         // To store the currency for the shop
    var stars: Long = 15         // Default starting stars (attempts)
)