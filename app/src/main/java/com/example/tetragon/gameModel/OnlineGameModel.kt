package com.example.tetragon.gameModel

class GameModel(
    var roomID: String = "",
    var player1: String = "", // Firebase UID of creator
    var player2: String = "", // Firebase UID of joined player
    var p1Score: Int = 0,
    var p2Score: Int = 0,
    var p1Mistakes: Int = 0,
    var p2Mistakes: Int = 0,
    var quitterID: String? = null,
    var gameStatus: GameStatus = GameStatus.CREATED
)

enum class GameStatus {
    CREATED,
    JOINED
}
