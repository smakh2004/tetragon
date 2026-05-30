package com.tetragon.app.utils.leaderboardUtils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize // Make sure this import exists!

@Parcelize
data class LeaderboardUser(
    val firstName: String = "",
    val lastName: String = "",
    val monthlyXP: Long = 0,
    val email: String = "",
    var uid: String = "",
    var isOnline: Boolean = false,
    val avatarName: String? = null
) : Parcelable