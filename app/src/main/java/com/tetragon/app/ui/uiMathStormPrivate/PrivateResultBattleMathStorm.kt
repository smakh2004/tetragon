package com.tetragon.app.ui.uiMathStormPrivate

import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityPrivateResultBattleMathStormBinding
import com.tetragon.app.gameModel.PrivateGameModel
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.PrivateGameData
import com.tetragon.app.utils.soundUtils.SoundManager
import kotlin.math.max

class PrivateResultBattleMathStorm : BaseActivity() {

    private lateinit var binding: ActivityPrivateResultBattleMathStormBinding
    private val db = FirebaseFirestore.getInstance()
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Rive ViewModelInstance is a ref-counted native object. If we only keep it in a local
     * variable it can be released while the artboard is still alive, and the bound properties
     * silently stop updating. Keep a strong reference per RiveAnimationView.
     */
    private val vmInstances = mutableMapOf<Int, ViewModelInstance>()

    private var soundPlayed = false

    companion object {
        private const val TAG = "PrivateResultRive"
        private const val MAX_RIVE_ATTEMPTS = 20   // ~1s total at 50ms steps
        private const val RIVE_RETRY_DELAY_MS = 50L
        private const val VIEW_MODEL_NAME = "ViewModel1"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityPrivateResultBattleMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loadingOverlayContainer.visibility = View.VISIBLE

        binding.exitBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Read the final scores from Firestore BEFORE deleting the room, so that a last-second
        // answer by the opponent is not lost. Falls back to the intent extras.
        loadFinalScores()
    }

    // ---------------- Score resolution ----------------

    private fun loadFinalScores() {
        val intentYourScore = intent.getIntExtra("YOUR_SCORE", 0)
        val intentOpponentScore = intent.getIntExtra("OPPONENT_SCORE", 0)
        val intentYourStatus = intent.getStringExtra("YOUR_STATUS")
        // Optional: the battle screen can pass this along, same as the online flow does.
        val intentQuitter = intent.getStringExtra("QUITTER_ID")

        val model = PrivateGameData.gameModel.value
        if (model == null || model.gameID.isEmpty()) {
            renderResult(intentYourScore, intentOpponentScore, intentQuitter, intentYourStatus)
            return
        }

        db.collection("private_games").document(model.gameID).get()
            .addOnCompleteListener { task ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener

                var yourScore = intentYourScore
                var opponentScore = intentOpponentScore
                var quitterID = intentQuitter

                val snapshot = task.result
                if (task.isSuccessful && snapshot != null && snapshot.exists()) {
                    val game = runCatching {
                        snapshot.toObject(PrivateGameModel::class.java)
                    }.getOrNull()
                    if (game != null) {
                        val amPlayer1 = PrivateGameData.myID == "P1"
                        // max() guards against a snapshot that is somehow older than what we
                        // already carried over in the intent.
                        yourScore = max(
                            yourScore,
                            if (amPlayer1) game.p1Score else game.p2Score
                        )
                        opponentScore = max(
                            opponentScore,
                            if (amPlayer1) game.p2Score else game.p1Score
                        )
                        if (quitterID.isNullOrEmpty()) quitterID = game.quitterID
                    }
                }

                renderResult(yourScore, opponentScore, quitterID, intentYourStatus)
                deleteRoom()
            }
    }

    /**
     * Single source of truth for the outcome. The title, the sound, the avatar labels and faces
     * and the win count all derive from the values computed here, so they cannot disagree with
     * each other. Private games award no XP, so there is no XP label and no XP write.
     */
    private fun renderResult(
        yourScore: Int,
        opponentScore: Int,
        quitterID: String?,
        intentYourStatus: String?
    ) {
        val model = PrivateGameData.gameModel.value
        // PrivateGameData.myID is "P1" / "P2"; the real UIDs live on the game model.
        val amPlayer1 = PrivateGameData.myID == "P1"
        val yourUID = if (amPlayer1) model?.player1 else model?.player2
        val opponentUID = if (amPlayer1) model?.player2 else model?.player1

        val yourStatusInternal: String
        val opponentStatusInternal: String

        when {
            // quitterID is stored as "P1" / "P2", the same form as PrivateGameData.myID.
            !quitterID.isNullOrEmpty() -> {
                if (quitterID == PrivateGameData.myID) {
                    yourStatusInternal = "LOSER"
                    opponentStatusInternal = "WINNER"
                } else {
                    yourStatusInternal = "WINNER"
                    opponentStatusInternal = "LOSER"
                }
            }
            // Scores tied but the battle screen already decided a winner: that can only have
            // come from a quit whose quitterID we no longer see, so trust what it sent.
            yourScore == opponentScore && intentYourStatus == "WINNER" -> {
                yourStatusInternal = "WINNER"
                opponentStatusInternal = "LOSER"
            }
            yourScore == opponentScore && intentYourStatus == "LOSER" -> {
                yourStatusInternal = "LOSER"
                opponentStatusInternal = "WINNER"
            }
            yourScore > opponentScore -> {
                yourStatusInternal = "WINNER"
                opponentStatusInternal = "LOSER"
            }
            yourScore < opponentScore -> {
                yourStatusInternal = "LOSER"
                opponentStatusInternal = "WINNER"
            }
            else -> {
                yourStatusInternal = "DRAW"
                opponentStatusInternal = "DRAW"
            }
        }

        val yourStatusText = getLocalizedStatus(yourStatusInternal)
        val opponentStatusText = getLocalizedStatus(opponentStatusInternal)
        val yourStatusColor = getStatusColor(yourStatusInternal)
        val opponentStatusColor = getStatusColor(opponentStatusInternal)

        // ---- Headline above the avatars ----
        binding.resultTitleText.text = getResultTitle(yourStatusInternal)

        playResultSound(yourStatusInternal)

        var pendingRequests = 0
        if (!yourUID.isNullOrEmpty()) pendingRequests++
        if (!opponentUID.isNullOrEmpty()) pendingRequests++

        fun checkDismissOverlay() {
            pendingRequests--
            if (pendingRequests <= 0 && !isFinishing && !isDestroyed) {
                binding.loadingOverlayContainer.visibility = View.GONE
            }
        }

        if (!yourUID.isNullOrEmpty()) {
            db.collection("users").document(yourUID)
                .get().addOnCompleteListener { task ->
                    if (!isFinishing && !isDestroyed && task.isSuccessful) {
                        val snapshot = task.result
                        val myName = snapshot?.getString("firstName")
                            ?: getString(R.string.player_default)
                        val myConfig = snapshot?.get("avatarConfig") as? Map<*, *>

                        applyResultAvatarConfigToRive(
                            riveView = binding.myPlayerIconImage,
                            config = myConfig,
                            firstName = myName,
                            statusText = yourStatusText,
                            statusColor = yourStatusColor,
                            score = yourScore,
                            statusInternal = yourStatusInternal
                        )
                    }
                    checkDismissOverlay()
                }
        }

        if (!opponentUID.isNullOrEmpty()) {
            db.collection("users").document(opponentUID)
                .get().addOnCompleteListener { task ->
                    if (!isFinishing && !isDestroyed && task.isSuccessful) {
                        val snapshot = task.result
                        val opponentName = snapshot?.getString("firstName")
                            ?: getString(R.string.opponent_default)
                        val opponentConfig = snapshot?.get("avatarConfig") as? Map<*, *>

                        applyResultAvatarConfigToRive(
                            riveView = binding.opponentPlayerIconImage,
                            config = opponentConfig,
                            firstName = opponentName,
                            statusText = opponentStatusText,
                            statusColor = opponentStatusColor,
                            score = opponentScore,
                            statusInternal = opponentStatusInternal
                        )
                    }
                    checkDismissOverlay()
                }
        }

        if (pendingRequests == 0) {
            binding.loadingOverlayContainer.visibility = View.GONE
        }

        updateUserPrivateScore(yourStatusInternal, yourUID)
    }

    // ---------------- Rive ----------------

    private fun applyResultAvatarConfigToRive(
        riveView: RiveAnimationView,
        config: Map<*, *>?,
        firstName: String,
        statusText: String,
        statusColor: Int,
        score: Int,
        statusInternal: String,
        attempt: Int = 0
    ) {
        riveView.post {
            if (isFinishing || isDestroyed) return@post

            // The .riv file may still be loading; post() is not a load guarantee.
            val file = riveView.controller.file
            if (file == null) {
                if (attempt < MAX_RIVE_ATTEMPTS) {
                    riveView.postDelayed({
                        applyResultAvatarConfigToRive(
                            riveView, config, firstName, statusText,
                            statusColor, score, statusInternal, attempt + 1
                        )
                    }, RIVE_RETRY_DELAY_MS)
                } else {
                    Log.w(TAG, "Rive file never loaded for view ${riveView.id}")
                }
                return@post
            }

            try {
                val vm = file.getViewModelByName(VIEW_MODEL_NAME) ?: run {
                    Log.w(TAG, "ViewModel '$VIEW_MODEL_NAME' not found in the .riv file")
                    return@post
                }
                val vmi = vm.createDefaultInstance()

                // Bind unconditionally: assigning only to stateMachines.firstOrNull() is a
                // silent no-op whenever that list is still empty at this moment.
                riveView.controller.activeArtboard?.viewModelInstance = vmi
                riveView.controller.stateMachines.forEach { it.viewModelInstance = vmi }

                vmInstances[riveView.id] = vmi   // keep the native object alive

                // ---- Number: the score shown in the animation ----
                vmi.getNumberProperty("result")?.value = score.toFloat()

                // ---- Strings ----
                vmi.getStringProperty("firstName")?.value = firstName
                vmi.getStringProperty("state")?.value = statusText

                // ---- Face expression overrides the saved avatar face ----
                val faceValue = when (statusInternal) {
                    "WINNER" -> 11f
                    "LOSER" -> 10f
                    else -> 9f
                }

                listOf("face", "hair", "glasses", "hat", "mustache", "body").forEach { key ->
                    val num = if (key == "face") {
                        faceValue
                    } else {
                        (config?.get(key) as? Number)?.toFloat() ?: 1f
                    }
                    vmi.getNumberProperty(key)?.value = num
                }

                // ---- Boolean ----
                val hatValue = (config?.get("hat") as? Number)?.toInt() ?: 1
                vmi.getBooleanProperty("hatOn")?.value = (hatValue > 1)

                // ---- Colors ----
                vmi.getColorProperty("stateColor")?.value = statusColor

                listOf(
                    "skinColor", "hairColor", "glassColor",
                    "capColor", "mustacheColor", "clothColor",
                    "backgroundColor", "eyebrowColor"
                ).forEach { propName ->
                    (config?.get(propName) as? String)?.let { hex ->
                        runCatching { Color.parseColor(hex) }.getOrNull()?.let { colorInt ->
                            vmi.getColorProperty(propName)?.value = colorInt
                        }
                    }
                }

                // Bound values are not applied to the artboard until the state machine advances,
                // so make sure something is actually driving it.
                if (!riveView.isPlaying) riveView.play()
                riveView.controller.advance(0f)
                riveView.invalidate()

            } catch (e: Exception) {
                Log.e(TAG, "Error applying result avatar config: ${e.message}", e)
            }
        }
    }

    // ---------------- Helpers ----------------

    /** Headline shown above the avatars. */
    private fun getResultTitle(status: String): String {
        return when (status) {
            "WINNER" -> getString(R.string.you_won)
            "LOSER" -> getString(R.string.you_lose)
            else -> getString(R.string.you_draw)
        }
    }

    /** Short badge rendered inside the Rive avatar. */
    private fun getLocalizedStatus(status: String): String {
        return when (status) {
            "WINNER" -> getString(R.string.winner)
            "LOSER" -> getString(R.string.loser)
            else -> getString(R.string.draw)
        }
    }

    private fun getStatusColor(status: String): Int {
        return when (status) {
            "WINNER" -> ContextCompat.getColor(this, R.color.green_1)
            "LOSER" -> ContextCompat.getColor(this, R.color.red_1)
            else -> ContextCompat.getColor(this, R.color.gray_1)
        }
    }

    private fun playResultSound(status: String) {
        if (soundPlayed) return
        soundPlayed = true

        if (!SoundManager.isSoundEnabled(this)) return
        mediaPlayer = when (status) {
            "WINNER" -> MediaPlayer.create(this, R.raw.winner)
            "LOSER" -> MediaPlayer.create(this, R.raw.loser)
            else -> return
        }

        mediaPlayer?.setOnCompletionListener {
            it.release()
            mediaPlayer = null
        }
        mediaPlayer?.start()
    }

    private fun deleteRoom() {
        val model = PrivateGameData.gameModel.value ?: return
        if (PrivateGameData.myID == "P1" && model.gameID.isNotEmpty()) {
            db.collection("private_games").document(model.gameID).delete()
        }
    }

    /**
     * Private matches award NO XP — no xp / monthlyXP / dailyXPGains write at all. The only
     * thing recorded is the win count, kept in the same document the previous version used.
     */
    private fun updateUserPrivateScore(yourStatus: String, yourUID: String?) {
        if (yourStatus != "WINNER") return
        if (yourUID.isNullOrEmpty()) return

        val gameDoc = db.collection("users").document(yourUID)
            .collection("games").document("OnlineMathStorm")

        gameDoc.set(mapOf("onlineScore" to FieldValue.increment(1)), com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e(TAG, "Win count update failed: ${e.message}", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        vmInstances.clear()
    }
}