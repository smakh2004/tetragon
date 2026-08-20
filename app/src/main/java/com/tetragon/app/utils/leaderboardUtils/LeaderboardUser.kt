package com.tetragon.app.utils.leaderboardUtils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class LeaderboardUser(
    val firstName: String = "",
    val lastName: String = "",
    var monthlyXP: Long = 0, // Changed from val to var to allow visual hotfix zeroing
    val email: String = "",
    var uid: String = "",
    var isOnline: Boolean = false,
    val avatarName: String? = null,
    val avatarConfig: @RawValue Map<String, Any>? = null
) : Parcelable