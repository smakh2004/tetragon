package com.tetragon.app.questions.questionMathFourthGrade

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.PlayableInstance
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.fragments.HomeFragment
import com.tetragon.app.reward.BagTapActivity
import com.tetragon.app.subscriptionModel.IntroSubscriptionActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class Math4GradeFragment : Fragment() {

    private lateinit var scrollView: ScrollView
    private lateinit var startContainer: FrameLayout
    private lateinit var startEnabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtn: TextView
    private lateinit var continueEnabledBtn: AppCompatButton
    private lateinit var continueEnabledBtnBack: View
    private lateinit var startLessonLabel: TextView

    // --- NAVIGATION UI ---
    private lateinit var nextGradeLabel: TextView
    private lateinit var nextTopicName: TextView
    private lateinit var moveOnBtn: AppCompatButton
    private lateinit var backToGrade3Btn: View

    // Scroll UI
    private lateinit var scrollTargetContainer: View
    private lateinit var scrollArrowIcon: ImageView

    private lateinit var topic1: RiveAnimationView
    private lateinit var topic2: RiveAnimationView
    private lateinit var topic3: RiveAnimationView
    private lateinit var topic4: RiveAnimationView
    private lateinit var topic5: RiveAnimationView
    private lateinit var topic6: RiveAnimationView

    // --- SUB-DECORATIONS & PATH JUNCTIONS ---
    private var clockInPath1: RiveAnimationView? = null
    private var calculatorInPath1: RiveAnimationView? = null
    private var mrSquareJunction12: RiveAnimationView? = null

    private var kacheliInPath2: RiveAnimationView? = null
    private var buttonsInPath2: RiveAnimationView? = null
    private var frogJunction23: RiveAnimationView? = null

    private var clockInPath3: RiveAnimationView? = null
    private var calculatorInPath3: RiveAnimationView? = null
    private var mrSquareJunction34: RiveAnimationView? = null

    private var kacheliInPath4: RiveAnimationView? = null
    private var buttonsInPath4: RiveAnimationView? = null
    private var frogJunction45: RiveAnimationView? = null

    private var clockInPath5: RiveAnimationView? = null
    private var calculatorInPath5: RiveAnimationView? = null
    private var mrSquareJunction56: RiveAnimationView? = null

    private var kacheliInPath6: RiveAnimationView? = null
    private var buttonsInPath6: RiveAnimationView? = null

    private val topicViews by lazy { listOf(topic1, topic2, topic3, topic4, topic5, topic6) }

    // Index-matched positioning lists
    private val calculatorViews by lazy { listOf(calculatorInPath1, null, calculatorInPath3, null, calculatorInPath5, null) }
    private val clockViews by lazy { listOf(clockInPath1, null, clockInPath3, null, clockInPath5, null) }
    private val junctionViews by lazy { listOf(mrSquareJunction12, null, mrSquareJunction34, null, mrSquareJunction56, null) }

    private val kacheliViews by lazy { listOf(null, kacheliInPath2, null, kacheliInPath4, null, kacheliInPath6) }
    private val buttonsViews by lazy { listOf(null, buttonsInPath2, null, buttonsInPath4, null, buttonsInPath6) }
    private val frogJunctionViews by lazy { listOf(null, frogJunction23, null, frogJunction45, null, null) }

    // --- DECORATION TRACKING HASHMAPS ---
    private val calculatorPlayedStates = HashMap<Int, Boolean>()
    private val clockPlayedStates = HashMap<Int, Boolean>()
    private val junctionPlayedStates = HashMap<Int, Boolean>()
    private val kacheliPlayedStates = HashMap<Int, Boolean>()
    private val buttonsPlayedStates = HashMap<Int, Boolean>()
    private val frogJunctionPlayedStates = HashMap<Int, Boolean>()

    // --- DEBOUNCING & VIEWPORT PERFORMANCE ---
    private val riveVisibilityCandidates = mutableListOf<RiveAnimationView>()
    private val rivePlayingStates = HashMap<RiveAnimationView, Boolean>()
    private val animatorsMap = HashMap<String, ValueAnimator>()
    private val reusableRect = Rect()
    private var visibilityBuffer = 0

    // Grade 4 Topics - Localized
    private val topicNames by lazy {
        listOf(
            getString(R.string.complex_arithmetics),
            getString(R.string.column_multiplication),
            getString(R.string.column_division),
            getString(R.string.fraction_arithmetics),
            getString(R.string.mixed_numbers),
            getString(R.string.graphs),
        )
    }

    private val topicKeys = listOf(
        "COMPLEX_ARITHMETICS", "COLUMN_MULTIPLICATION", "COLUMN_DIVISION",
        "FRACTION_ARITHMETICS", "MIXED_NUMBERS", "GRAPHS",
    )

    // Grade 5 Data for Preview - Localized
    private val grade5TopicNames by lazy {
        listOf(getString(R.string.natural_numbers))
    }
    private val grade5TopicKeys = listOf("NATURAL_NUMBERS")

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var topicProgressListener: ListenerRegistration? = null

    private val currentTopicProgress = HashMap<String, Float>()
    private val claimedRewards = HashMap<String, Boolean>()
    private val isUnlocked = HashMap<String, Boolean>()

    private data class RiveButtonState(val finished: Boolean, val unlocked: Boolean, val claimed: Boolean)
    private val lastPushedRiveState = HashMap<String, RiveButtonState>()

    private var userStars: Int = 15
    private var isInfinity: Boolean = false

    private var pendingTopic: String? = null
    private var pendingRewardKey: String? = null
    private var hasInitialScrolled = false
    private var userHasScrolled = false
    private var lastReportedTopic = ""
    private var currentTargetIndex: Int = 0

    private val scrollRunnable = Runnable {
        determineVisibleTopic()
        updateRiveActiveStates()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_math4_grade, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRiveTopics()

        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            userHasScrolled = true
            scrollView.removeCallbacks(scrollRunnable)
            scrollView.postDelayed(scrollRunnable, 60)
            if (startContainer.visibility == View.VISIBLE) {
                startContainer.animate().cancel()
                startContainer.visibility = View.GONE
            }
        }

        continueEnabledBtn.setOnClickListener { handleContinueClick() }

        moveOnBtn.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", 5)
                putExtra("SELECTED_SUBJECT", "MATH")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            (requireActivity() as? Activity)?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        backToGrade3Btn.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", 3)
                putExtra("SELECTED_SUBJECT", "MATH")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            (requireActivity() as? Activity)?.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        scrollTargetContainer.setOnClickListener {
            userHasScrolled = false
            scrollToSpecificTopic(currentTargetIndex, instant = false)
        }

        listenToTopicProgress()
    }

    private fun initViews(view: View) {
        scrollView = view.findViewById(R.id.level_scroll_container)
        startContainer = view.findViewById(R.id.start_container)
        startEnabledBtnContainer = view.findViewById(R.id.start_enabled_btn_container)
        startDisabledBtnContainer = view.findViewById(R.id.start_disabled_btn_container)
        startDisabledBtn = view.findViewById(R.id.start_disabled_btn)
        continueEnabledBtn = view.findViewById(R.id.continue_enabled_btn)
        continueEnabledBtnBack = view.findViewById(R.id.continue_enabled_btn_back)
        startLessonLabel = view.findViewById(R.id.start_lesson_label)

        nextGradeLabel = view.findViewById(R.id.next_grade_label)
        nextTopicName = view.findViewById(R.id.next_topic_name)
        moveOnBtn = view.findViewById(R.id.move_on_btn)
        backToGrade3Btn = view.findViewById(R.id.back_to_grade3_btn)

        scrollTargetContainer = view.findViewById(R.id.scroll_to_target_container)
        scrollArrowIcon = view.findViewById(R.id.scroll_arrow_icon)

        topic1 = view.findViewById(R.id.topic1); topic2 = view.findViewById(R.id.topic2)
        topic3 = view.findViewById(R.id.topic3); topic4 = view.findViewById(R.id.topic4)
        topic5 = view.findViewById(R.id.topic5); topic6 = view.findViewById(R.id.topic6)

        // Find Sub-Decorations safely matching XML IDs
        clockInPath1 = view.findViewById(R.id.clock_in_path1)
        calculatorInPath1 = view.findViewById(R.id.calculator_in_path1)
        mrSquareJunction12 = view.findViewById(R.id.mr_square_junction_1_2)

        kacheliInPath2 = view.findViewById(R.id.kacheli_in_path2)
        buttonsInPath2 = view.findViewById(R.id.buttons_in_path2)
        frogJunction23 = view.findViewById(R.id.frog_junction_2_3)

        clockInPath3 = view.findViewById(R.id.clock_in_path3)
        calculatorInPath3 = view.findViewById(R.id.calculator_in_path3)
        mrSquareJunction34 = view.findViewById(R.id.mr_square_junction_3_4)

        kacheliInPath4 = view.findViewById(R.id.kacheli_in_path4)
        buttonsInPath4 = view.findViewById(R.id.buttons_in_path4)
        frogJunction45 = view.findViewById(R.id.frog_junction_4_5)

        clockInPath5 = view.findViewById(R.id.clock_in_path5)
        calculatorInPath5 = view.findViewById(R.id.calculator_in_path5)
        mrSquareJunction56 = view.findViewById(R.id.mr_square_junction_5_6)

        kacheliInPath6 = view.findViewById(R.id.kacheli_in_path6)
        buttonsInPath6 = view.findViewById(R.id.buttons_in_path6)

        // Register candidates into visibility calculation layer
        riveVisibilityCandidates.addAll(topicViews)
        listOfNotNull(
            clockInPath1, calculatorInPath1, mrSquareJunction12,
            kacheliInPath2, buttonsInPath2, frogJunction23,
            clockInPath3, calculatorInPath3, mrSquareJunction34,
            kacheliInPath4, buttonsInPath4, frogJunction45,
            clockInPath5, calculatorInPath5, mrSquareJunction56,
            kacheliInPath6, buttonsInPath6
        ).forEach { riveVisibilityCandidates.add(it) }

        // Setup trigger default states
        topicKeys.forEachIndexed { index, _ ->
            calculatorPlayedStates[index] = false
            clockPlayedStates[index] = false
            junctionPlayedStates[index] = false
            kacheliPlayedStates[index] = false
            buttonsPlayedStates[index] = false
            frogJunctionPlayedStates[index] = false
        }

        riveVisibilityCandidates.forEach { rivePlayingStates[it] = true }
        visibilityBuffer = (resources.displayMetrics.heightPixels * 0.5f).toInt()

        startContainer.visibility = View.GONE
        scrollTargetContainer.visibility = View.GONE
        nextGradeLabel.text = getString(R.string.fifth_grade)
    }

    private fun updateRiveActiveStates() {
        if (!isAdded || !::scrollView.isInitialized) return
        val scrollTop = scrollView.scrollY - visibilityBuffer
        val scrollBottom = scrollView.scrollY + scrollView.height + visibilityBuffer

        for (rive in riveVisibilityCandidates) {
            if (rive.width == 0 && rive.height == 0) continue

            reusableRect.set(0, 0, rive.width, rive.height)
            scrollView.offsetDescendantRectToMyCoords(rive, reusableRect)
            val isNearViewport = reusableRect.bottom >= scrollTop && reusableRect.top <= scrollBottom

            val wasPlaying = rivePlayingStates[rive] ?: true
            if (isNearViewport && !wasPlaying) {
                rive.play()
                rivePlayingStates[rive] = true
            } else if (!isNearViewport && wasPlaying) {
                rive.pause()
                rivePlayingStates[rive] = false
            }
        }
    }

    private fun listenToTopicProgress() {
        val user = auth.currentUser ?: return
        topicProgressListener = db.collection("users").document(user.uid).addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !isAdded) return@addSnapshotListener

            userStars = snapshot.getLong("stars")?.toInt() ?: 15
            isInfinity = snapshot.getBoolean("subscription") ?: false

            val progressMap = snapshot.get("class4MathProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimedMap = snapshot.get("claimedRewardsMath4") as? Map<*, *> ?: emptyMap<String, Any>()

            var prevClaimed = true
            topicKeys.forEachIndexed { index, key ->
                val prog = (progressMap[key] as? Long ?: 0).toFloat()
                val claimed = claimedMap[key] as? Boolean ?: false

                animateRiveProgress(key, topicViews[index], currentTopicProgress[key] ?: 0f, prog) {
                    currentTopicProgress[key] = it
                }
                claimedRewards[key] = claimed

                val topicUnlocked = if (index == 0) true else prevClaimed
                isUnlocked[key] = topicUnlocked

                val finished = prog >= 100f
                val newState = RiveButtonState(finished, topicUnlocked, claimed)
                if (lastPushedRiveState[key] != newState) {
                    updateRiveButtonStates(topicViews[index], finished, topicUnlocked, claimed)
                    lastPushedRiveState[key] = newState
                }
                prevClaimed = finished && claimed

                // --- PROGRESS TRANSITION TRIGGER PATHS ---
                if (index % 2 == 0) {
                    if (prog >= 35f && calculatorPlayedStates[index] == false) {
                        calculatorViews[index]?.fireState("State Machine 1", "play")
                        calculatorPlayedStates[index] = true
                    }
                    if (prog >= 70f && clockPlayedStates[index] == false) {
                        clockViews[index]?.fireState("State Machine 1", "play")
                        clockPlayedStates[index] = true
                    }
                    if (prog >= 100f && junctionPlayedStates[index] == false) {
                        junctionViews[index]?.fireState("State Machine 1", "play")
                        junctionPlayedStates[index] = true
                    }
                } else {
                    if (prog >= 35f && kacheliPlayedStates[index] == false) {
                        kacheliViews[index]?.fireState("State Machine 1", "play")
                        kacheliPlayedStates[index] = true
                    }
                    if (prog >= 70f && buttonsPlayedStates[index] == false) {
                        buttonsViews[index]?.fireState("State Machine 1", "play")
                        buttonsPlayedStates[index] = true
                    }
                    if (prog >= 100f && frogJunctionPlayedStates[index] == false) {
                        frogJunctionViews[index]?.fireState("State Machine 1", "play")
                        frogJunctionPlayedStates[index] = true
                    }
                }
            }

            currentTargetIndex = findTargetTopicIndex(progressMap, topicKeys, claimedMap)

            // Dynamic Preview Grade 5
            val progressMap5 = snapshot.get("class5MathProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimedMap5 = snapshot.get("claimedRewardsMath5") as? Map<*, *> ?: emptyMap<String, Any>()
            val activeGrade5Index = findTargetTopicIndex(progressMap5, grade5TopicKeys, claimedMap5)
            val topicName = grade5TopicNames.getOrNull(activeGrade5Index) ?: grade5TopicNames[0]
            nextTopicName.text = getString(R.string.topic_preview_format, activeGrade5Index + 1, topicName)

            if (!hasInitialScrolled) {
                scrollView.post {
                    if (isAdded && !userHasScrolled) {
                        scrollToSpecificTopic(currentTargetIndex, instant = true)
                        hasInitialScrolled = true
                        updateRiveActiveStates()
                    }
                }
            }
        }
    }

    private fun showBottomControls(buttonText: String, topicKey: String?, rewardKey: String?, isLocked: Boolean) {
        pendingTopic = if (isLocked) null else topicKey
        pendingRewardKey = if (isLocked) null else rewardKey

        val startContainerBg = view?.findViewById<android.widget.LinearLayout>(R.id.start_container_background)

        if (rewardKey != null) {
            val idx = topicKeys.indexOf(rewardKey)
            startLessonLabel.text = getString(R.string.reward_label, topicNames[idx])
        } else if (topicKey != null) {
            val idx = topicKeys.indexOf(topicKey)
            startLessonLabel.text = getString(R.string.topic_label, idx + 1, topicNames[idx])
        }

        if (isLocked) {
            startEnabledBtnContainer.visibility = View.GONE
            startDisabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtn.text = getString(R.string.not_available)
            startContainerBg?.setBackgroundResource(R.drawable.custom_background)
        } else if (rewardKey != null) {
            continueEnabledBtn.text = getString(R.string.claim_reward_btn)
            startContainerBg?.setBackgroundResource(R.drawable.custom_background)
            resetButtonToDefaultTheme()
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
        } else if (!isInfinity && userStars <= 0) {
            continueEnabledBtn.text = getString(R.string.subscribe_caps)
            startLessonLabel.text = getString(R.string.out_of_stars_label)
            startContainerBg?.setBackgroundResource(R.drawable.custom_premium_background_2)
            continueEnabledBtn.backgroundTintList = null
            continueEnabledBtnBack.backgroundTintList = null
            continueEnabledBtn.setBackgroundResource(R.drawable.custom_gradient_button)
            continueEnabledBtnBack.setBackgroundResource(R.drawable.custom_gradient_button_shadow)
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
        } else {
            continueEnabledBtn.text = if (buttonText == "REVIEW") getString(R.string.review_text) else getString(R.string.start_text)
            startContainerBg?.setBackgroundResource(R.drawable.custom_background)
            resetButtonToDefaultTheme()
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
        }
        startContainer.fadeInAndSlideUp()
    }

    private fun handleContinueClick() {
        if (continueEnabledBtn.text == getString(R.string.subscribe_caps)) {
            startActivity(Intent(requireContext(), IntroSubscriptionActivity::class.java))
            return
        }

        showLoadingState(true)

        if (pendingRewardKey != null) {
            handleRewardClaimed(findTopicViewByKey(pendingRewardKey!!), pendingRewardKey!!)
        } else if (pendingTopic != null) {
            val user = auth.currentUser
            if (user != null && !isInfinity) {
                val updates = mutableMapOf<String, Any>("stars" to FieldValue.increment(-1))
                if (userStars >= 15) updates["lastStarUsedTime"] = Timestamp.now()
                db.collection("users").document(user.uid).update(updates).addOnCompleteListener {
                    if (isAdded) launchQuestionActivity()
                }
            } else {
                launchQuestionActivity()
            }
        } else {
            showLoadingState(false)
        }
    }

    private fun launchQuestionActivity() {
        val intent = Intent(requireContext(), Math4GradeQuestionActivity::class.java).apply {
            putExtra("TOPIC_KEY", pendingTopic)
        }
        startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        scrollView.postDelayed({ if (isAdded) {
            showLoadingState(false)
            startContainer.fadeOutAndSlideDown()
        } }, 1000)
    }

    private fun handleRewardClaimed(view: RiveAnimationView, topicKey: String) {
        val user = auth.currentUser ?: return
        showLoadingState(true)
        db.collection("users").document(user.uid).update("claimedRewardsMath4.$topicKey", true)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                view.setBooleanState("State Machine 1", "rewardAvailable", false)
                view.setBooleanState("State Machine 1", "reward", true)
                startContainer.fadeOutAndSlideDown()
                startActivity(Intent(requireContext(), BagTapActivity::class.java))
            }.addOnFailureListener { if (isAdded) showLoadingState(false) }
    }

    private fun resetButtonToDefaultTheme() {
        val black3 = ContextCompat.getColor(requireContext(), R.color.black_3)
        val black2 = ContextCompat.getColor(requireContext(), R.color.black_2)
        continueEnabledBtn.setBackgroundResource(R.drawable.custom_button)
        continueEnabledBtnBack.setBackgroundResource(R.drawable.custom_button)
        continueEnabledBtn.backgroundTintList = ColorStateList.valueOf(black3)
        continueEnabledBtnBack.backgroundTintList = ColorStateList.valueOf(black2)
    }

    private fun findTopicViewByKey(key: String): RiveAnimationView {
        val index = topicKeys.indexOf(key).coerceAtLeast(0)
        return topicViews[index]
    }

    private fun showLoadingState(isLoading: Boolean) {
        startEnabledBtnContainer.visibility = if (isLoading) View.GONE else View.VISIBLE
        startDisabledBtnContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) startDisabledBtn.text = getString(R.string.processing_caps)
    }

    private fun findTargetTopicIndex(progressMap: Map<*, *>, keys: List<String>, claimedMap: Map<*, *>): Int {
        keys.forEachIndexed { index, key ->
            val prog = (progressMap[key] as? Long ?: 0).toInt()
            if (prog in 1..99) return index
            val unlocked = if (index == 0) true else {
                val prevKey = keys[index - 1]
                val prevProg = (progressMap[prevKey] as? Long ?: 0)
                val prevClaimedStatus = claimedMap[prevKey] as? Boolean ?: false
                prevProg >= 100L && prevClaimedStatus
            }
            if (prog == 0 && unlocked) return index
        }
        return 0
    }

    private fun scrollToSpecificTopic(index: Int, instant: Boolean = false) {
        if (!isAdded || index < 0 || index >= topicViews.size) return
        val targetView = topicViews[index]
        reusableRect.set(0, 0, targetView.width, targetView.height)
        scrollView.offsetDescendantRectToMyCoords(targetView, reusableRect)
        val offset = scrollView.height / 16
        val scrollY = (reusableRect.top - offset).coerceAtLeast(0)
        if (instant) scrollView.scrollTo(0, scrollY) else scrollView.smoothScrollTo(0, scrollY)
        determineVisibleTopic()
        updateRiveActiveStates()
    }

    private fun determineVisibleTopic() {
        if (!isAdded) return
        var bestIndex = 0
        var minDistance = Int.MAX_VALUE
        val focusPoint = scrollView.height / 2

        topicViews.forEachIndexed { index, view ->
            if (view.getGlobalVisibleRect(reusableRect)) {
                val center = (reusableRect.top + reusableRect.bottom) / 2
                val distance = Math.abs(center - focusPoint)
                if (distance < minDistance) {
                    minDistance = distance
                    bestIndex = index
                }
            }
        }
        if (userHasScrolled && hasInitialScrolled) {
            if (bestIndex != currentTargetIndex) {
                if (scrollTargetContainer.visibility != View.VISIBLE) scrollTargetContainer.fadeInAndSlideUp()
                scrollArrowIcon.setImageResource(if (bestIndex < currentTargetIndex) R.drawable.arrow_up else R.drawable.arrow_down)
            } else if (scrollTargetContainer.visibility == View.VISIBLE) {
                scrollTargetContainer.fadeOutAndSlideDown()
            }
        }
        val currentTitle = topicNames.getOrNull(bestIndex) ?: ""
        if (currentTitle != lastReportedTopic && currentTitle.isNotEmpty()) {
            lastReportedTopic = currentTitle
            (parentFragment as? HomeFragment)?.onTopicChanged(currentTitle)
        }
    }

    private fun setupRiveTopics() {
        topicKeys.forEachIndexed { index, key ->
            val res = if (index % 2 == 0) R.raw.progress_path_odd else R.raw.progress_path_even
            setupSingleTopic(topicViews[index], res, (index + 1).toFloat(), key)
        }
    }

    private fun setupSingleTopic(rive: RiveAnimationView, res: Int, level: Float, topicKey: String) {
        rive.setRiveResource(res, stateMachineName = "State Machine 1", autoplay = true)
        rive.registerListener(object : RiveFileController.Listener {
            override fun notifyPlay(animation: PlayableInstance) {
                val controller = rive.controller
                controller.file?.getViewModelByName("ViewModel1")?.let { vm ->
                    val vmi = vm.createDefaultInstance()
                    controller.stateMachines.firstOrNull()?.viewModelInstance = vmi
                    vmi.getNumberProperty("level")?.value = level

                    val isFinished = (currentTopicProgress[topicKey] ?: 0f) >= 100f
                    val startTxt = if (isFinished) getString(R.string.review_text) else getString(R.string.start_text)
                    try {
                        vmi.getStringProperty("startText")?.value = startTxt
                        vmi.getStringProperty("rewardText")?.value = getString(R.string.reward_text_rive)
                    } catch (e: Exception) {}
                }
            }
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                activity?.runOnUiThread {
                    when (stateName) {
                        "start_button_pressed" -> {
                            val unlocked = isUnlocked[topicKey] ?: false
                            val btnText = if ((currentTopicProgress[topicKey] ?: 0f) >= 100f) "REVIEW" else "START"
                            showBottomControls(btnText, topicKey, null, !unlocked)
                        }
                        "reward_button_pressed" -> {
                            if (claimedRewards[topicKey] == true) return@runOnUiThread
                            val canClaim = (currentTopicProgress[topicKey] ?: 0f) >= 100f
                            showBottomControls("CLAIM", null, topicKey, !canClaim)
                        }
                    }
                }
            }
            override fun notifyLoop(p: PlayableInstance) {}
            override fun notifyPause(p: PlayableInstance) {}
            override fun notifyStop(p: PlayableInstance) {}
        })
    }

    private fun updateRiveButtonStates(view: RiveAnimationView, isFinished: Boolean, unlocked: Boolean, claimed: Boolean) {
        view.setBooleanState("State Machine 1", "lessonAvailable", unlocked && !isFinished)
        view.setBooleanState("State Machine 1", "rewardAvailable", unlocked && isFinished && !claimed)
        view.setBooleanState("State Machine 1", "reward", claimed)
    }

    private fun animateRiveProgress(key: String, view: RiveAnimationView, start: Float, end: Float, onUpdate: (Float) -> Unit) {
        if (start == end) return

        animatorsMap[key]?.cancel()

        val animator = ValueAnimator.ofFloat(start, end).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                view.setNumberState("State Machine 1", "progress", v)
                onUpdate(v)
            }
        }
        animatorsMap[key] = animator
        animator.start()
    }

    private fun View.fadeInAndSlideUp() {
        animate().cancel()
        visibility = View.VISIBLE
        alpha = 0f
        translationY = 100f
        animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun View.fadeOutAndSlideDown() {
        animate().cancel()
        animate().alpha(0f).translationY(100f).setDuration(250).withEndAction { visibility = View.GONE }.start()
    }

    override fun onResume() {
        super.onResume()
        if (::startContainer.isInitialized && startContainer.visibility == View.VISIBLE) {
            startContainer.animate().cancel()
            startContainer.visibility = View.GONE
        }
        if (::scrollView.isInitialized) {
            scrollView.post { updateRiveActiveStates() }
        }
    }

    override fun onPause() {
        super.onPause()
        riveVisibilityCandidates.forEach { rive ->
            rive.pause()
            rivePlayingStates[rive] = false
        }
    }

    override fun onDestroyView() {
        scrollView.removeCallbacks(scrollRunnable)
        animatorsMap.values.forEach { it.cancel() }
        animatorsMap.clear()
        riveVisibilityCandidates.clear()
        rivePlayingStates.clear()
        calculatorPlayedStates.clear()
        clockPlayedStates.clear()
        junctionPlayedStates.clear()
        kacheliPlayedStates.clear()
        buttonsPlayedStates.clear()
        frogJunctionPlayedStates.clear()
        lastPushedRiveState.clear()
        super.onDestroyView()
        topicProgressListener?.remove()
    }
}