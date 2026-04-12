package com.example.tetragon.gameModel

class PrivateGameModel(
    var gameID: String = "",
    var player1: String = "",   // Firebase UID
    var player2: String = "",   // Firebase UID
    var p1Score: Int = 0,
    var p2Score: Int = 0,
    var p1Mistakes: Int = 0,
    var p2Mistakes: Int = 0,
    var quitterID: String? = null,
    var gameStatus: PrivateGameStatus = PrivateGameStatus.CREATED
)

enum class PrivateGameStatus {
    CREATED,
    JOINED
}