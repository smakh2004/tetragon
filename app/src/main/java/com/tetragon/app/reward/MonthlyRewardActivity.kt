package com.tetragon.app.reward

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelInstance
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity

class MonthlyRewardActivity : BaseActivity() {

    private lateinit var winnerRive: RiveAnimationView
    private lateinit var btnClaim: Button
    private lateinit var btnClaimShadow: View
    private lateinit var continueContainer: FrameLayout
    private lateinit var enabledContainer: FrameLayout

    private var soundPool: SoundPool? = null
    private var rewardSoundId: Int = 0
    private var soundPlayed = false

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val buttonHandler = Handler(Looper.getMainLooper())

    /** The button's first appearance, once the artboard's intro has played. */
    private val showButtonRunnable = Runnable {
        if (!isFinishing && !isDestroyed) {
            enabledContainer.visibility = View.VISIBLE
        }
    }

    /** Fires two seconds after coinsClaimed flips — that's when the coins are granted. */
    private val creditCoinsRunnable = Runnable {
        if (!isFinishing && !isDestroyed) creditCoins()
    }

    /** 1, 2 or 3 — passed in by MainActivity when the reward is granted. */
    private var winnerRank = 0
    private var rewardCoins = 0L

    /** The user's balance BEFORE the reward, read from their document. */
    private var currentCoins = 0L

    private var claimStarted = false   // coinsClaimed has been flipped
    private var coinsCredited = false  // Firestore write succeeded

    // ---------------- Rive (ViewModel2) ----------------
    private var winnerVmi: ViewModelInstance? = null
    private val firstSlot = PlaceSlot(PATH_FIRST_PLACE)
    private val secondSlot = PlaceSlot(PATH_SECOND_PLACE)
    private val thirdSlot = PlaceSlot(PATH_THIRD_PLACE)

    /** Snapshot of the top 3, index 0 = 1st place. May hold fewer than 3 entries. */
    private val podium = mutableListOf<MutableMap<Any?, Any?>>()

    companion object {
        const val EXTRA_WINNER_RANK = "WINNER_RANK"

        private const val TAG = "MonthlyReward"

        private const val MAX_RIVE_ATTEMPTS = 60
        private const val RIVE_RETRY_DELAY_MS = 50L
        private const val BUTTON_DELAY_MS = 2000L // before the button first appears

        /**
         * Gap between coinsClaimed flipping and the coins actually being granted, so the
         * artboard's coin animation plays before the total jumps. During this window the
         * button simply sits in its disabled colours — no view is swapped or animated.
         */
        private const val CREDIT_DELAY_MS = 2000L

        private const val WINNER_VIEW_MODEL_NAME = "ViewModel2"

        /** Nested ViewModel1 instances inside ViewModel2. */
        private const val PATH_FIRST_PLACE = "firstPlace"
        private const val PATH_SECOND_PLACE = "secondPlace"
        private const val PATH_THIRD_PLACE = "thirdPlace"

        private const val PROP_PLACE = "place"
        private const val PROP_TEXT = "text"
        private const val PROP_FIRST_NAME = "firstName"
        private const val PROP_XP = "xp"

        // ViewModel2 root — the coin display
        private const val PROP_COINS = "coins"                // Number: the user's total
        private const val PROP_ADDED_COINS = "addedCoins"     // String: "+300 coins"
        private const val PROP_COINS_CLAIMED = "coinsClaimed" // Boolean

        /** Prize scales with the place. */
        private const val REWARD_FIRST = 300L
        private const val REWARD_SECOND = 200L
        private const val REWARD_THIRD = 100L

        /** Button colours — kept here so enabled/disabled is a tint swap, never a view swap. */
        private const val BTN_FACE_ENABLED = "#4E4E4E"
        private const val BTN_SHADOW_ENABLED = "#333333"

        private val AVATAR_NUMBERS =
            listOf("face", "hair", "glasses", "hat", "mustache", "body")

        private val AVATAR_COLORS = listOf(
            "skinColor", "hairColor", "glassColor", "capColor",
            "mustacheColor", "clothColor", "backgroundColor", "eyebrowColor", "eyeColor"
        )
    }

    private inner class PlaceSlot(val path: String) {
        var vmi: ViewModelInstance? = null

        fun number(name: String, value: Float) {
            vmi?.let { runCatching { it.getNumberProperty(name)?.value = value } }
            winnerVmi?.let { runCatching { it.getNumberProperty("$path/$name")?.value = value } }
        }

        fun string(name: String, value: String) {
            vmi?.let { runCatching { it.getStringProperty(name)?.value = value } }
            winnerVmi?.let { runCatching { it.getStringProperty("$path/$name")?.value = value } }
        }

        fun boolean(name: String, value: Boolean) {
            vmi?.let { runCatching { it.getBooleanProperty(name)?.value = value } }
            winnerVmi?.let { runCatching { it.getBooleanProperty("$path/$name")?.value = value } }
        }

        fun color(name: String, value: Int) {
            vmi?.let { runCatching { it.getColorProperty(name)?.value = value } }
            winnerVmi?.let { runCatching { it.getColorProperty("$path/$name")?.value = value } }
        }

        fun apply(entry: Map<*, *>?) {
            if (entry == null) {
                Log.d(TAG, "$path is empty — leaving the artboard defaults alone")
                return
            }

            string(PROP_FIRST_NAME, entry["firstName"] as? String ?: "")
            string(PROP_XP, "${(entry["monthlyXP"] as? Number)?.toLong() ?: 0L} XP")

            val config = entry["avatarConfig"] as? Map<*, *>
            if (config.isNullOrEmpty()) {
                Log.w(TAG, "$path has no avatarConfig yet")
                return
            }

            AVATAR_NUMBERS.forEach { key ->
                number(key, (config[key] as? Number)?.toFloat() ?: 1f)
            }

            val hatValue = (config["hat"] as? Number)?.toInt() ?: 1
            boolean("hatOn", hatValue > 1)

            AVATAR_COLORS.forEach { propName ->
                val hex = config[propName] as? String ?: return@forEach
                val colorInt = runCatching { Color.parseColor(hex) }.getOrNull() ?: return@forEach
                color(propName, colorInt)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // No window transition — a window animation fades the ENTIRE screen, including
        // the .riv, on top of the artboard's own intro.
        overridePendingTransition(0, 0)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        winnerRank = intent.getIntExtra(EXTRA_WINNER_RANK, 0)
        if (winnerRank !in 1..3) {
            Log.w(TAG, "opened without a winner rank — closing")
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        try {
            Rive.init(this)
        } catch (e: Exception) {
            // Context already running
        }

        setContentView(R.layout.activity_monthly_reward)

        winnerRive = findViewById(R.id.winnerRive)
        btnClaim = findViewById(R.id.continue_enabled_btn)
        btnClaimShadow = findViewById(R.id.continue_enabled_btn_shadow)
        continueContainer = findViewById(R.id.continue_btn_container)
        enabledContainer = findViewById(R.id.continue_enabled_btn_container)

        // Kill the platform press/elevation animation and the ripple. Without this the
        // button lifts and fades on tap, which is the "transition" we don't want.
        btnClaim.stateListAnimator = null
        btnClaim.isHapticFeedbackEnabled = false

        applyEdgeToEdgeInsets()

        rewardCoins = when (winnerRank) {
            1 -> REWARD_FIRST
            2 -> REWARD_SECOND
            else -> REWARD_THIRD
        }

        soundPlayed = savedInstanceState?.getBoolean("SOUND_PLAYED", false) ?: false

        setClaimButtonEnabled(true)

        initSound()
        bindWinnerRive()
        loadPodium()
        loadCurrentCoins()

        // Show the button once the intro has played
        buttonHandler.postDelayed(showButtonRunnable, BUTTON_DELAY_MS)

        btnClaim.setOnClickListener { onButtonTapped() }
    }

    private fun applyEdgeToEdgeInsets() {
        val basePaddingBottom = continueContainer.paddingBottom
        val basePaddingStart = continueContainer.paddingStart
        val basePaddingEnd = continueContainer.paddingEnd

        ViewCompat.setOnApplyWindowInsetsListener(continueContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = basePaddingStart + bars.left,
                right = basePaddingEnd + bars.right,
                bottom = basePaddingBottom + bars.bottom
            )
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("SOUND_PLAYED", soundPlayed)
    }

    // ==================== BUTTON STATE ====================

    /**
     * Enabled / disabled is nothing but a tint + text-colour swap on the SAME views.
     * No visibility change, no second container, no alpha — so there is no transition
     * of any kind over the artboard.
     */
    private fun setClaimButtonEnabled(enabled: Boolean) {
        val faceColor = if (enabled) {
            Color.parseColor(BTN_FACE_ENABLED)
        } else {
            ContextCompat.getColor(this, R.color.gray_2)
        }

        val shadowColor = if (enabled) {
            Color.parseColor(BTN_SHADOW_ENABLED)
        } else {
            ContextCompat.getColor(this, R.color.gray_2)
        }

        val textColor = if (enabled) {
            ContextCompat.getColor(this, R.color.white)
        } else {
            ContextCompat.getColor(this, R.color.gray_1)
        }

        ViewCompat.setBackgroundTintList(btnClaim, ColorStateList.valueOf(faceColor))
        ViewCompat.setBackgroundTintList(btnClaimShadow, ColorStateList.valueOf(shadowColor))
        btnClaim.setTextColor(textColor)

        btnClaim.isEnabled = enabled
        btnClaim.isClickable = enabled
    }

    // ==================== SOUND ====================

    private fun initSound() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
            .also { pool ->
                pool.setOnLoadCompleteListener { _, _, status ->
                    if (status == 0 && !soundPlayed && !isFinishing && !isDestroyed) {
                        soundPlayed = true
                        pool.play(rewardSoundId, 1f, 1f, 1, 0, 1f)
                    }
                }
                rewardSoundId = pool.load(this, R.raw.monthly_reward, 1)
            }
    }

    // ==================== RIVE ====================

    private fun bindWinnerRive(attempt: Int = 0) {
        winnerRive.post {
            if (isFinishing || isDestroyed) return@post

            val riveController = winnerRive.controller
            val file = riveController.file
            val stateMachine = riveController.stateMachines.firstOrNull()

            if (file == null || stateMachine == null) {
                if (attempt < MAX_RIVE_ATTEMPTS) {
                    winnerRive.postDelayed({ bindWinnerRive(attempt + 1) }, RIVE_RETRY_DELAY_MS)
                }
                return@post
            }

            try {
                if (winnerVmi == null) {
                    val vm = file.getViewModelByName(WINNER_VIEW_MODEL_NAME) ?: return@post
                    val root = vm.createDefaultInstance()

                    riveController.activeArtboard?.viewModelInstance = root
                    riveController.stateMachines.forEach { it.viewModelInstance = root }
                    winnerVmi = root

                    firstSlot.vmi =
                        runCatching { root.getInstanceProperty(PATH_FIRST_PLACE) }.getOrNull()
                    secondSlot.vmi =
                        runCatching { root.getInstanceProperty(PATH_SECOND_PLACE) }.getOrNull()
                    thirdSlot.vmi =
                        runCatching { root.getInstanceProperty(PATH_THIRD_PLACE) }.getOrNull()
                }

                setRootNumber(PROP_PLACE, winnerRank.toFloat())

                // just the place — the coin amount lives in addedCoins, not here
                setRootString(PROP_TEXT, placeMessage())
                setRootString(PROP_ADDED_COINS, addedCoinsText())
                setRootBoolean(PROP_COINS_CLAIMED, claimStarted)
                setRootNumber(PROP_COINS, currentCoins.toFloat())

                pushPodium()

            } catch (e: Exception) {
                Log.e(TAG, "Error binding Rive winner screen: ${e.message}")
            }
        }
    }

    private fun setRootNumber(name: String, value: Float) {
        winnerVmi?.let { runCatching { it.getNumberProperty(name)?.value = value } }
    }

    private fun setRootString(name: String, value: String) {
        winnerVmi?.let { runCatching { it.getStringProperty(name)?.value = value } }
    }

    private fun setRootBoolean(name: String, value: Boolean) {
        winnerVmi?.let { runCatching { it.getBooleanProperty(name)?.value = value } }
    }

    /** "You got the 1-st place" — no coin amount here. */
    private fun placeMessage(): String = when (winnerRank) {
        1 -> getString(R.string.you_got_1st_place)
        2 -> getString(R.string.you_got_2nd_place)
        else -> getString(R.string.you_got_3rd_place)
    }

    /** "+300 coins" — what this win adds, shown next to the running total. */
    private fun addedCoinsText(): String =
        getString(R.string.added_coins_format, rewardCoins.toInt())

    private fun pushPodium() {
        if (winnerVmi == null) return

        listOf(firstSlot, secondSlot, thirdSlot).forEachIndexed { index, slot ->
            slot.apply(podium.getOrNull(index))
        }
    }

    // ==================== COINS ====================

    /** The balance before the reward, so the artboard can count up from it. */
    private fun loadCurrentCoins() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                // don't overwrite the post-reward total if the claim already landed
                if (coinsCredited) return@addOnSuccessListener

                currentCoins = userDoc.getLong("coins") ?: 0L
                Log.d(TAG, "current coins=$currentCoins, reward=$rewardCoins")

                setRootNumber(PROP_COINS, currentCoins.toFloat())
                setRootString(PROP_ADDED_COINS, addedCoinsText())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "cannot read current coins", e)
            }
    }

    // ==================== PODIUM DATA ====================

    private fun loadPodium() {
        db.collection("system").document("leaderboard").get()
            .addOnSuccessListener { doc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val stored = (doc.get("podium") as? List<*>)
                    ?.mapNotNull { (it as? Map<*, *>)?.toMutableMap() }
                    ?: emptyList()

                podium.clear()
                podium.addAll(stored)
                Log.d(TAG, "podium entries=${podium.size}, myRank=$winnerRank")

                if (podium.isEmpty()) {
                    Log.w(TAG, "no podium snapshot — falling back to winnerEmails")
                    loadPodiumFromWinnerEmails(doc.get("winnerEmails") as? List<*>)
                    return@addOnSuccessListener
                }

                pushPodium()
                patchMissingAvatarConfigs()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "cannot read system/leaderboard", e)
                loadOwnAvatarIntoOwnSlot()
            }
    }

    private fun patchMissingAvatarConfigs() {
        var ownSlotHasAvatar = false

        podium.forEachIndexed { index, entry ->
            val config = entry["avatarConfig"] as? Map<*, *>
            if (!config.isNullOrEmpty()) {
                if (index == winnerRank - 1) ownSlotHasAvatar = true
                return@forEachIndexed
            }

            val uid = entry["uid"] as? String
            if (uid.isNullOrEmpty()) {
                Log.w(TAG, "podium entry $index has neither avatarConfig nor uid")
                return@forEachIndexed
            }

            db.collection("users").document(uid).get()
                .addOnSuccessListener { userDoc ->
                    if (isFinishing || isDestroyed) return@addOnSuccessListener

                    entry["avatarConfig"] =
                        userDoc.get("avatarConfig") as? Map<*, *> ?: return@addOnSuccessListener
                    if ((entry["firstName"] as? String).isNullOrEmpty()) {
                        entry["firstName"] = userDoc.getString("firstName") ?: ""
                    }
                    pushPodium()
                }
        }

        if (!ownSlotHasAvatar) loadOwnAvatarIntoOwnSlot()
    }

    private fun loadOwnAvatarIntoOwnSlot() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                val entry = mutableMapOf<Any?, Any?>(
                    "uid" to uid,
                    "firstName" to (userDoc.getString("firstName") ?: ""),
                    "monthlyXP" to (userDoc.getLong("monthlyXP") ?: 0L),
                    "avatarConfig" to userDoc.get("avatarConfig")
                )

                while (podium.size < winnerRank) {
                    podium.add(mutableMapOf())
                }
                podium[winnerRank - 1] = entry
                pushPodium()
            }
    }

    private fun loadPodiumFromWinnerEmails(emails: List<*>?) {
        val ordered = emails?.mapNotNull { it as? String }?.take(3) ?: emptyList()
        if (ordered.isEmpty()) {
            loadOwnAvatarIntoOwnSlot()
            return
        }

        ordered.forEach { email -> podium.add(mutableMapOf<Any?, Any?>("email" to email)) }

        ordered.forEachIndexed { index, email ->
            db.collection("users").whereEqualTo("email", email).limit(1).get()
                .addOnSuccessListener { snap ->
                    if (isFinishing || isDestroyed) return@addOnSuccessListener

                    val userDoc = snap.documents.firstOrNull() ?: return@addOnSuccessListener
                    podium[index]["uid"] = userDoc.id
                    podium[index]["firstName"] = userDoc.getString("firstName") ?: ""
                    podium[index]["avatarConfig"] = userDoc.get("avatarConfig")
                    pushPodium()
                }
        }
    }

    // ==================== CLAIM ====================

    /** Tap 1 starts the claim, tap 2 leaves the screen. */
    private fun onButtonTapped() {
        if (coinsCredited) goToMain() else startClaim()
    }

    /**
     * Flips coinsClaimed and does NOTHING else on screen except greying the button out
     * for two seconds. The artboard owns every other visual change from here.
     */
    private fun startClaim() {
        if (claimStarted) return

        claimStarted = true
        setRootBoolean(PROP_COINS_CLAIMED, true)

        setClaimButtonEnabled(false)

        buttonHandler.postDelayed(creditCoinsRunnable, CREDIT_DELAY_MS)
    }

    /** The actual grant, two seconds after the flag flipped. */
    private fun creditCoins() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId)
            .update("coins", FieldValue.increment(rewardCoins))
            .addOnSuccessListener {
                if (isFinishing || isDestroyed) return@addOnSuccessListener

                coinsCredited = true

                val newTotal = currentCoins + rewardCoins
                currentCoins = newTotal
                Log.d(TAG, "coins credited, new total=$newTotal")

                setRootNumber(PROP_COINS, newTotal.toFloat())
                setClaimButtonEnabled(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "claim failed", e)

                // let the user try again
                claimStarted = false
                setRootBoolean(PROP_COINS_CLAIMED, false)
                setClaimButtonEnabled(true)

                Toast.makeText(
                    this,
                    getString(R.string.error_saving_progress),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun goToMain() {
        if (isFinishing || isDestroyed) return

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // Remove pending callbacks to avoid leaks
        buttonHandler.removeCallbacksAndMessages(null)

        soundPool?.release()
        soundPool = null

        winnerVmi = null
        firstSlot.vmi = null
        secondSlot.vmi = null
        thirdSlot.vmi = null

        super.onDestroy()
    }
}