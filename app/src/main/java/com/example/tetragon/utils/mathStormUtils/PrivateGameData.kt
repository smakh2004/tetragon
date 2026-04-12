package com.example.tetragon.utils.mathStormUtils

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.tetragon.gameModel.PrivateGameModel
import com.google.firebase.firestore.FirebaseFirestore

object PrivateGameData {
    private var _gameModel: MutableLiveData<PrivateGameModel> = MutableLiveData()
    var gameModel: LiveData<PrivateGameModel> = _gameModel
    var myID = ""

    private val db = FirebaseFirestore.getInstance()

    fun saveGameModel(model: PrivateGameModel) {
        _gameModel.postValue(model)
        db.collection("private_games")
            .document(model.gameID)
            .set(model)
    }

    fun fetchGameModel() {

        gameModel.value?.let { model ->
            db.collection("private_games")
                .document(model.gameID)
                .addSnapshotListener { value, _ ->
                    val updated = value?.toObject(PrivateGameModel::class.java)
                    updated?.let { _gameModel.postValue(it) }
                }
        }
    }
}