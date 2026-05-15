package com.tetragon.app.utils.mathStormUtils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tetragon.app.gameModel.GameModel
import com.tetragon.app.gameModel.GameStatus
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

object OnlineGameData {
    private var _gameModel: MutableLiveData<GameModel> = MutableLiveData()
    var gameModel: LiveData<GameModel> = _gameModel
    var myID = ""

    private val db = FirebaseFirestore.getInstance()

    fun saveGameModel(model: GameModel) {
        _gameModel.postValue(model)
        db.collection("online_games")
            .document(model.roomID)
            .set(model)
    }

    fun fetchGameModel() {
        gameModel.value?.let { model ->
            db.collection("online_games")
                .document(model.roomID)
                .addSnapshotListener { value, _ ->
                    val updated = value?.toObject(GameModel::class.java)
                    updated?.let { _gameModel.postValue(it) }
                }
        }
    }

    fun findOrCreateRoom(myUid: String, onRoomReady: (GameModel) -> Unit) {
        db.collection("online_games")
            .whereEqualTo("gameStatus", GameStatus.CREATED)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    // Join existing room
                    val doc = snapshot.documents[0]
                    val game = doc.toObject(GameModel::class.java)!!

                    game.player2 = myUid
                    game.gameStatus = GameStatus.JOINED

                    db.collection("online_games").document(game.roomID).set(game)
                    onRoomReady(game)
                } else {
                    // No room → create one
                    val roomId = UUID.randomUUID().toString()
                    val game = GameModel(
                        roomID = roomId,
                        player1 = myUid,
                        gameStatus = GameStatus.CREATED
                    )
                    db.collection("online_games").document(roomId).set(game)
                    onRoomReady(game)
                }
            }
    }
}