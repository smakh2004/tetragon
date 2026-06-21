package com.tetragon.app.fragments.chat

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class GameMessage(
    var senderId: String = "",
    var senderName: String = "",
    var messageText: String = "",
    var timestamp: Long = 0L
)