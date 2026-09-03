package com.tetragon.app.utils.onlineRoomUtils

data class OnlineRoom(
    val roomID: String,
    val ownerUid: String,
    val ownerName: String = "",
    val avatarConfig: Map<*, *>? = null,
    val isMine: Boolean = false
)