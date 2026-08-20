package com.tetragon.app.ui.uiMathStormOnline

import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityOnlineResultBattleMathStormBinding
import com.tetragon.app.gameModel.GameModel
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.mathStormUtils.OnlineGameData
import com.tetragon.app.utils.soundUtils.SoundManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class OnlineResultBattleMathStormActivity : BaseActivity() {

    private lateinit var binding: ActivityOnlineResultBattleMathStormBinding
    private val db = FirebaseFirestore.getInstance()
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Rive ViewModelInstance is a ref-counted native object. If we only keep it in a local
     * variable it can be released while the artboard is still alive, and the bound properties
     * silently stop updating. Keep a strong reference per RiveAnimationView.
     */
    private val vmInstances = mutableMapOf<Int, ViewModelInstance>()

    private var soundPlayed = false

    // ---- XP counter animation state ----
    /** The user's monthlyXP as it was before this match; what the counter shows on entry. */
    private var xpStartValue = 0L
    /** monthlyXP after this match; what the counter animates to. */
    private var xpTargetValue = 0L
    private var xpRiveReady = false
    private var overlayDismissed = false
    private var xpCountUpStarted = false
    private var xpCountUpRunnable: Runnable? = null

    companion object {
        private const val TAG = "ResultRive"
        private const val MAX_RIVE_ATTEMPTS = 20   // ~1s total at 50ms steps
        private const val RIVE_RETRY_DELAY_MS = 50L
        private const val VIEW_MODEL_NAME = "ViewModel1"

        /** XP counter artboard (xp_animation.riv). */
        private const val XP_VIEW_MODEL_NAME = "ViewModel1"
        private const val XP_PROPERTY = "currentXp"
        /** How long the old value stays on screen before the counter ticks up. */
        private const val XP_COUNT_UP_DELAY_MS = 1200L

        /** Single source of truth: drives both the label on screen and the Firestore write. */
        private const val WIN_XP = 30L
        private const val LOSS_XP = 0L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityOnlineResultBattleMathStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loadingOverlayContainer.visibility = View.VISIBLE
        // Stays hidden until we know XP was actually earned; renderResult decides.
        binding.xpAnimationView.visibility = View.GONE

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
        val intentQuitter = intent.getStringExtra("QUITTER_ID")

        val model = OnlineGameData.gameModel.value
        if (model == null || model.roomID.isEmpty()) {
            renderResult(intentYourScore, intentOpponentScore, intentQuitter)
            return
        }

        db.collection("online_games").document(model.roomID).get()
            .addOnCompleteListener { task ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener

                var yourScore = intentYourScore
                var opponentScore = intentOpponentScore
                var quitterID = intentQuitter

                val snapshot = task.result
                if (task.isSuccessful && snapshot != null && snapshot.exists()) {
                    val game = runCatching { snapshot.toObject(GameModel::class.java) }.getOrNull()
                    if (game != null) {
                        val amPlayer1 = OnlineGameData.myID == game.player1
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

                renderResult(yourScore, opponentScore, quitterID)
                deleteRoom()
            }
    }

    /**
     * Single source of truth for the outcome. The title, the XP label, the sound, the avatar
     * labels and faces, the XP counter and the XP award all derive from the values computed
     * here, so they cannot disagree with each other.
     */
    private fun renderResult(yourScore: Int, opponentScore: Int, quitterID: String?) {
        val yourUID = OnlineGameData.myID
        val model = OnlineGameData.gameModel.value
        val opponentUID = when {
            model == null -> null
            yourUID == model.player1 -> model.player2
            else -> model.player1
        }

        val yourStatusInternal: String
        val opponentStatusInternal: String

        if (!quitterID.isNullOrEmpty()) {
            if (quitterID == yourUID) {
                yourStatusInternal = "LOSER"
                opponentStatusInternal = "WINNER"
            } else {
                yourStatusInternal = "WINNER"
                opponentStatusInternal = "LOSER"
            }
        } else {
            when {
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
        }

        val yourStatusText = getLocalizedStatus(yourStatusInternal)
        val opponentStatusText = getLocalizedStatus(opponentStatusInternal)
        val yourStatusColor = getStatusColor(yourStatusInternal)
        val opponentStatusColor = getStatusColor(opponentStatusInternal)

        // ---- Headline + XP label above the avatars ----
        binding.resultTitleText.text = getResultTitle(yourStatusInternal)

        val xpGained = xpForStatus(yourStatusInternal)
        binding.xpGainLabel.text = getString(R.string.xp_gain, xpGained)

        // No XP earned (loss / draw) means nothing to count up, so the counter would just sit
        // there showing a static number. Keyed off xpGained rather than the status string, so
        // it comes back automatically if losses ever start awarding XP.
        val showXpCounter = xpGained > 0L
        binding.xpAnimationView.visibility = if (showXpCounter) View.VISIBLE else View.GONE

        playResultSound(yourStatusInternal)

        var pendingRequests = 0
        if (yourUID.isNotEmpty()) pendingRequests++
        if (!opponentUID.isNullOrEmpty()) pendingRequests++

        fun checkDismissOverlay() {
            pendingRequests--
            if (pendingRequests <= 0 && !isFinishing && !isDestroyed) {
                binding.loadingOverlayContainer.visibility = View.GONE
                overlayDismissed = true
                maybeStartXpCountUp()
            }
        }

        if (yourUID.isNotEmpty()) {
            db.collection("users").document(yourUID)
                .get().addOnCompleteListener { task ->
                    if (!isFinishing && !isDestroyed && task.isSuccessful) {
                        val snapshot = task.result
                        val myName = snapshot?.getString("firstName")
                            ?: getString(R.string.player_default)
                        val myConfig = snapshot?.get("avatarConfig") as? Map<*, *>

                        // monthlyXP lives on the same user document, so no extra read is needed.
                        val currentMonthlyXP = snapshot?.getLong("monthlyXP") ?: 0L
                        xpStartValue = currentMonthlyXP
                        xpTargetValue = currentMonthlyXP + xpGained
                        // Skip binding the artboard entirely when the counter is hidden.
                        if (showXpCounter) prepareXpRive(xpStartValue)

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
            overlayDismissed = true
            maybeStartXpCountUp()
        }

        updateUserOnlineScore(yourStatusInternal)
    }

    // ---------------- Rive: avatars ----------------

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

    // ---------------- Rive: XP counter ----------------

    /**
     * Binds ViewModel1 of the XP artboard and seeds the counter with the user's monthlyXP as it
     * was before this match. Same retry dance as the avatars: post() is not a load guarantee.
     */
    private fun prepareXpRive(startValue: Long, attempt: Int = 0) {
        val riveView = binding.xpAnimationView
        riveView.post {
            if (isFinishing || isDestroyed) return@post

            val file = riveView.controller.file
            if (file == null) {
                if (attempt < MAX_RIVE_ATTEMPTS) {
                    riveView.postDelayed(
                        { prepareXpRive(startValue, attempt + 1) },
                        RIVE_RETRY_DELAY_MS
                    )
                } else {
                    Log.w(TAG, "XP rive file never loaded")
                }
                return@post
            }

            try {
                val vm = file.getViewModelByName(XP_VIEW_MODEL_NAME) ?: run {
                    Log.w(TAG, "ViewModel '$XP_VIEW_MODEL_NAME' not found in xp_animation.riv")
                    return@post
                }
                val vmi = vm.createDefaultInstance()

                riveView.controller.activeArtboard?.viewModelInstance = vmi
                riveView.controller.stateMachines.forEach { it.viewModelInstance = vmi }

                vmInstances[riveView.id] = vmi   // keep the native object alive

                pushXpValue(startValue)
                xpRiveReady = true
                maybeStartXpCountUp()

            } catch (e: Exception) {
                Log.e(TAG, "Error preparing XP rive: ${e.message}", e)
            }
        }
    }

    /** Writes a value into the bound `currentXp` property and makes sure it gets rendered. */
    private fun pushXpValue(value: Long) {
        val riveView = binding.xpAnimationView
        val vmi = vmInstances[riveView.id] ?: return
        vmi.getNumberProperty(XP_PROPERTY)?.value = value.toFloat()
        if (!riveView.isPlaying) riveView.play()
        riveView.controller.advance(0f)
        riveView.invalidate()
    }

    /**
     * Fires exactly once, and only when the artboard is bound AND the loading overlay is gone —
     * otherwise the count-up would happen behind the white overlay and the user would never
     * see the old value. On a loss or a draw the artboard is never bound, so this is a no-op.
     */
    private fun maybeStartXpCountUp() {
        if (xpCountUpStarted || !xpRiveReady || !overlayDismissed) return
        if (xpTargetValue <= xpStartValue) return   // loss / draw: nothing to count up
        xpCountUpStarted = true

        val runnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            pushXpValue(xpTargetValue)
        }
        xpCountUpRunnable = runnable
        binding.xpAnimationView.postDelayed(runnable, XP_COUNT_UP_DELAY_MS)
    }

    // ---------------- Helpers ----------------

    /** XP awarded for an outcome. Used for the on-screen label and the Firestore write. */
    private fun xpForStatus(status: String): Long =
        if (status == "WINNER") WIN_XP else LOSS_XP

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
        val model = OnlineGameData.gameModel.value ?: return
        if (OnlineGameData.myID == model.player1 && model.roomID.isNotEmpty()) {
            db.collection("online_games").document(model.roomID).delete()
        }
    }

    private fun updateUserOnlineScore(yourStatus: String) {
        val xpGain = xpForStatus(yourStatus)
        if (yourStatus != "WINNER" || xpGain <= 0L) return

        val uid = OnlineGameData.myID
        if (uid.isEmpty()) return

        val firestore = FirebaseFirestore.getInstance()
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val onlineStatsDoc = firestore.collection("users").document(uid)
            .collection("games").document("OnlineMathStorm")
        val userProfileDoc = firestore.collection("users").document(uid)

        firestore.runTransaction { transaction ->
            val userSnapshot = transaction.get(userProfileDoc)
            val statsSnapshot = transaction.get(onlineStatsDoc)

            val currentXP = userSnapshot.getLong("xp") ?: 0L
            val currentMonthlyXP = userSnapshot.getLong("monthlyXP") ?: 0L
            val currentOnlineWins = statsSnapshot.getLong("onlineScore") ?: 0L

            // Firestore hands back an immutable Map here; copying avoids a ClassCastException
            // on the `as? MutableMap` cast, which previously wiped the whole daily history.
            val rawDailyMap = userSnapshot.get("dailyXPGains") as? Map<*, *>
            val dailyXpMap = mutableMapOf<String, Long>()
            rawDailyMap?.forEach { (key, value) ->
                val k = key as? String ?: return@forEach
                val v = (value as? Number)?.toLong() ?: return@forEach
                dailyXpMap[k] = v
            }

            dailyXpMap[todayKey] = (dailyXpMap[todayKey] ?: 0L) + xpGain

            transaction.update(
                userProfileDoc, mapOf(
                    "xp" to (currentXP + xpGain),
                    "monthlyXP" to (currentMonthlyXP + xpGain),
                    "dailyXPGains" to dailyXpMap
                )
            )

            if (!statsSnapshot.exists()) {
                transaction.set(onlineStatsDoc, mapOf("onlineScore" to 1L))
            } else {
                transaction.update(onlineStatsDoc, "onlineScore", currentOnlineWins + 1)
            }

            // Returned so the counter can use the transaction's own numbers instead of the
            // plain read above, which may be stale if XP was earned elsewhere meanwhile.
            currentMonthlyXP + xpGain
        }.addOnSuccessListener { newMonthlyXP ->
            if (isFinishing || isDestroyed || newMonthlyXP == null) return@addOnSuccessListener
            if (xpCountUpStarted) return@addOnSuccessListener   // too late, already animating

            xpTargetValue = newMonthlyXP
            xpStartValue = newMonthlyXP - xpGain
            if (xpRiveReady) pushXpValue(xpStartValue)
            maybeStartXpCountUp()
        }.addOnFailureListener { e ->
            Log.e(TAG, "XP transaction failed: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        xpCountUpRunnable?.let { binding.xpAnimationView.removeCallbacks(it) }
        xpCountUpRunnable = null
        mediaPlayer?.release()
        mediaPlayer = null
        vmInstances.clear()
    }
}