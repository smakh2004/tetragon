package com.tetragon.app.questions.questionMathFirstGrade

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

class Math1GradeFragment : Fragment() {

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

    // Scroll UI
    private lateinit var scrollTargetContainer: View
    private lateinit var scrollArrowIcon: ImageView

    // Path items and junctions mapped efficiently by accurate indices
    private val clockViews = HashMap<Int, RiveAnimationView>()
    private val calculatorViews = HashMap<Int, RiveAnimationView>()
    private val clockPlayedStates = HashMap<Int, Boolean>()
    private val calculatorPlayedStates = HashMap<Int, Boolean>()

    private val junctionViews = HashMap<Int, RiveAnimationView>()
    private val junctionPlayedStates = HashMap<Int, Boolean>()

    private val frogJunctionViews = HashMap<Int, RiveAnimationView>()
    private val frogJunctionPlayedStates = HashMap<Int, Boolean>()

    private val kacheliViews = HashMap<Int, RiveAnimationView>()
    private val kacheliPlayedStates = HashMap<Int, Boolean>()
    private val buttonsViews = HashMap<Int, RiveAnimationView>()
    private val buttonsPlayedStates = HashMap<Int, Boolean>()

    private val animatorsMap = HashMap<String, ValueAnimator>()

    private lateinit var topicViews: List<RiveAnimationView>

    // --- PERFORMANCE: every Rive view we might need to pause/resume based on visibility ---
    private val riveVisibilityCandidates = mutableListOf<RiveAnimationView>()
    private val rivePlayingStates = HashMap<RiveAnimationView, Boolean>()
    // Reused across hot paths to avoid per-call Rect allocations on weak devices
    private val reusableRect = Rect()
    // How far outside the visible viewport (px) a Rive view is still kept "playing".
    // Keeps things primed just before they scroll into view without paying full-screen cost.
    private var visibilityBuffer = 0

    // Grade 1 Topics - Localized
    private val topicNames by lazy {
        listOf(
            getString(R.string.counting_numbers), getString(R.string.addition),
            getString(R.string.subtraction), getString(R.string.odd_or_even),
            getString(R.string.comparison), getString(R.string.parentheses),
            getString(R.string.addition_up_to_20), getString(R.string.subtraction_up_to_20),
            getString(R.string.round_numbers), getString(R.string.two_digit_numbers),
            getString(R.string.problem_solving), getString(R.string.length_centimeter)
        )
    }

    private val topicKeys = listOf(
        "COUNT_NUMBERS", "ADDITION", "SUBTRACTION", "ODD_OR_EVEN",
        "COMPARISON", "PARENTHESES", "ADDITION_UP_TO_20", "SUBTRACTION_UP_TO_20",
        "ROUND_NUMBERS", "TWO_DIGIT_NUMBERS", "PROBLEM_SOLVING", "LENGTH_CENTIMETER"
    )

    // Grade 2 Data for Preview - Localized
    private val grade2TopicNames by lazy {
        listOf(
            getString(R.string.arithmetic_basics), getString(R.string.column_method),
            getString(R.string.three_digit_numbers), getString(R.string.comparison_grade2),
            getString(R.string.length_measurement), getString(R.string.time),
            getString(R.string.multiplication_table), getString(R.string.division)
        )
    }

    private val grade2TopicKeys = listOf(
        "ADDITION_SUBTRACTION_BASICS", "COLUMN_METHOD", "THREE_DIGIT_NUMBERS",
        "COMPARISON_THREE_DIGIT_NUMBERS", "LENGTH_MEASUREMENT", "TIME",
        "MULTIPLICATION", "DIVISION"
    )

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var topicProgressListener: ListenerRegistration? = null

    private val currentTopicProgress = HashMap<String, Float>()
    private val claimedRewards = HashMap<String, Boolean>()
    private val isUnlocked = HashMap<String, Boolean>()

    // True only between onResume and onPause. A progress fill triggered by a Firestore
    // snapshot that arrives while we're backgrounded (e.g. the XP-gained screen is on top)
    // is deferred until we're visible again — otherwise the animator runs and finishes
    // off-screen, currentTopicProgress is already at the new value on return, and the
    // user never sees the bar move.
    private var isFragmentVisible = false
    private val pendingProgressTargets = HashMap<String, Float>()

    // Cache of last-pushed Rive button state per topic so we don't re-push identical state
    // to the Rive state machine on every Firestore snapshot (was happening for all 12 topics
    // every single update, even when only one topic actually changed).
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

    // Reusable single scroll runnable allocation
    private val scrollRunnable = Runnable {
        determineVisibleTopic()
        updateRiveActiveStates()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_math1_grade, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRiveTopics()

        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            userHasScrolled = true
            scrollView.removeCallbacks(scrollRunnable)
            // 60ms debounce - scroll position doesn't need per-frame evaluation,
            // and this keeps the check off the hot path on weak devices.
            scrollView.postDelayed(scrollRunnable, 60)
            if (startContainer.visibility == View.VISIBLE) {
                startContainer.animate().cancel()
                startContainer.visibility = View.GONE
            }
        }

        continueEnabledBtn.setOnClickListener { handleContinueClick() }

        moveOnBtn.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", 2)
                putExtra("SELECTED_SUBJECT", "MATH")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            (requireActivity() as? Activity)?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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

        scrollTargetContainer = view.findViewById(R.id.scroll_to_target_container)
        scrollArrowIcon = view.findViewById(R.id.scroll_arrow_icon)

        topicViews = listOf(
            view.findViewById(R.id.topic1), view.findViewById(R.id.topic2),
            view.findViewById(R.id.topic3), view.findViewById(R.id.topic4),
            view.findViewById(R.id.topic5), view.findViewById(R.id.topic6),
            view.findViewById(R.id.topic7), view.findViewById(R.id.topic8),
            view.findViewById(R.id.topic9), view.findViewById(R.id.topic10),
            view.findViewById(R.id.topic11), view.findViewById(R.id.topic12)
        )
        riveVisibilityCandidates.addAll(topicViews)

        // NOTE: getIdentifier() reflection lookups below run once at init (not per-frame),
        // so they are not the source of scroll/animation jank. Left as-is to avoid risking
        // a mismatch with the XML id patterns; can be replaced with direct R.id references
        // later as a minor startup-time optimization if desired.
        val packageName = requireContext().packageName
        val res = resources
        for (i in 1..12) {
            val idx = i - 1
            if (i % 2 != 0) {
                val clockId = res.getIdentifier("clock_in_path$i", "id", packageName)
                if (clockId != 0) {
                    val v = view.findViewById<RiveAnimationView>(clockId)
                    clockViews[idx] = v; clockPlayedStates[idx] = false; riveVisibilityCandidates.add(v)
                }

                val calcId = res.getIdentifier("calculator_in_path$i", "id", packageName)
                if (calcId != 0) {
                    val v = view.findViewById<RiveAnimationView>(calcId)
                    calculatorViews[idx] = v; calculatorPlayedStates[idx] = false; riveVisibilityCandidates.add(v)
                }

                val junctionId = res.getIdentifier("mr_square_junction_${i}_${i + 1}", "id", packageName)
                if (junctionId != 0) {
                    val v = view.findViewById<RiveAnimationView>(junctionId)
                    junctionViews[idx] = v; junctionPlayedStates[idx] = false; riveVisibilityCandidates.add(v)
                }
            } else {
                val kacheliId = res.getIdentifier("kacheli_in_path$i", "id", packageName)
                if (kacheliId != 0) {
                    val v = view.findViewById<RiveAnimationView>(kacheliId)
                    kacheliViews[idx] = v; kacheliPlayedStates[idx] = false; riveVisibilityCandidates.add(v)
                }

                val buttonsId = res.getIdentifier("buttons_in_path$i", "id", packageName)
                if (buttonsId != 0) {
                    val v = view.findViewById<RiveAnimationView>(buttonsId)
                    buttonsViews[idx] = v; buttonsPlayedStates[idx] = false; riveVisibilityCandidates.add(v)
                }

                val frogId = res.getIdentifier("frog_junction_${i}_${i + 1}", "id", packageName)
                if (frogId != 0) {
                    val v = view.findViewById<RiveAnimationView>(frogId)
                    frogJunctionViews[idx] = v; frogJunctionPlayedStates[idx] = false; riveVisibilityCandidates.add(v)
                }
            }
        }

        // All views start "playing" to match their autoplay=true XML state; the first
        // updateRiveActiveStates() pass (after layout) will pause whatever is off-screen.
        riveVisibilityCandidates.forEach { rivePlayingStates[it] = true }
        visibilityBuffer = (resources.displayMetrics.heightPixels * 0.5f).toInt()

        startContainer.visibility = View.GONE
        scrollTargetContainer.visibility = View.GONE
        nextGradeLabel.text = getString(R.string.second_grade)
    }

    /**
     * Pauses every Rive animation that is well outside the current viewport and resumes
     * ones that are within view (or about to scroll into view, within [visibilityBuffer]).
     * This is the main performance win: instead of ~50 Rive state machines running at once,
     * only the handful actually near the visible viewport are active at any time.
     */
    private fun updateRiveActiveStates() {
        if (!isAdded || !::scrollView.isInitialized) return
        val scrollTop = scrollView.scrollY - visibilityBuffer
        val scrollBottom = scrollView.scrollY + scrollView.height + visibilityBuffer

        for (rive in riveVisibilityCandidates) {
            if (rive.width == 0 && rive.height == 0) continue // not laid out yet

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

            val progressMap = snapshot.get("class1MathProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimedMap = snapshot.get("claimedRewardsMath1") as? Map<*, *> ?: emptyMap<String, Any>()

            var prevClaimed = true
            topicKeys.forEachIndexed { index, key ->
                val prog = (progressMap[key] as? Long ?: 0).toFloat()
                val claimed = claimedMap[key] as? Boolean ?: false
                val displayed = currentTopicProgress[key] ?: 0f

                if (isFragmentVisible) {
                    // Foreground: play the fill straight away.
                    pendingProgressTargets.remove(key)
                    animateRiveProgress(key, topicViews[index], displayed, prog) {
                        currentTopicProgress[key] = it
                    }
                    fireDecorationsForProgress(index, prog)
                } else if (prog != displayed) {
                    // Background (e.g. XP-gained screen is on top): remember the new
                    // target but DON'T advance the displayed value. onResume() replays
                    // the fill so the user actually sees the bar move on return.
                    pendingProgressTargets[key] = prog
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
            }

            currentTargetIndex = findTargetTopicIndex(progressMap, topicKeys, claimedMap)

            val progressMap2 = snapshot.get("class2MathProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimedMap2 = snapshot.get("claimedRewardsMath2") as? Map<*, *> ?: emptyMap<String, Any>()
            val activeGrade2Index = findTargetTopicIndex(progressMap2, grade2TopicKeys, claimedMap2)
            val topicName = grade2TopicNames.getOrNull(activeGrade2Index) ?: grade2TopicNames[0]
            nextTopicName.text = getString(R.string.topic_preview_format, activeGrade2Index + 1, topicName)

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

    /**
     * Fires the decorative path animations (calculator / clock / junction on odd rows,
     * kacheli / buttons / frog on even rows) as the topic crosses the 35 / 70 / 100
     * progress thresholds. Guarded by the *PlayedStates maps so each only fires once.
     */
    private fun fireDecorationsForProgress(index: Int, prog: Float) {
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

    /**
     * Replays any progress fills that advanced while the fragment was backgrounded
     * (typically the jump caused by the XP-gained screen writing new progress). Called
     * from onResume so the fill animates from the old displayed value up to the new one
     * with the user watching, instead of having silently completed off-screen.
     */
    private fun playPendingProgressAnimations() {
        if (pendingProgressTargets.isEmpty()) return
        val targets = HashMap(pendingProgressTargets)
        pendingProgressTargets.clear()
        targets.forEach { (key, target) ->
            val index = topicKeys.indexOf(key)
            if (index < 0) return@forEach
            val start = currentTopicProgress[key] ?: 0f
            animateRiveProgress(key, topicViews[index], start, target) {
                currentTopicProgress[key] = it
            }
            fireDecorationsForProgress(index, target)
        }
    }

    private fun showBottomControls(buttonText: String, topicKey: String?, rewardKey: String?, isLocked: Boolean) {
        pendingTopic = if (isLocked) null else topicKey
        pendingRewardKey = if (isLocked) null else rewardKey

        if (rewardKey != null) {
            val idx = topicKeys.indexOf(rewardKey)
            startLessonLabel.text = getString(R.string.reward_label, topicNames[idx])
        } else if (topicKey != null) {
            val idx = topicKeys.indexOf(topicKey)
            startLessonLabel.text = getString(R.string.topic_label, idx + 1, topicNames[idx])
        }

        val startContainerBg = view?.findViewById<android.widget.LinearLayout>(R.id.start_container_background)

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
        val intent = Intent(requireContext(), Math1GradeQuestionActivity::class.java).apply {
            putExtra("TOPIC_KEY", pendingTopic)
        }
        startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        scrollView.postDelayed({ if (isAdded) showLoadingState(false) }, 1000)
    }

    private fun handleRewardClaimed(view: RiveAnimationView, topicKey: String) {
        val user = auth.currentUser ?: return
        showLoadingState(true)
        db.collection("users").document(user.uid).update("claimedRewardsMath1.$topicKey", true)
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

    private fun showLoadingState(isLoading: Boolean) {
        startEnabledBtnContainer.visibility = if (isLoading) View.GONE else View.VISIBLE
        startDisabledBtnContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) startDisabledBtn.text = getString(R.string.processing_caps)
    }

    private fun findTargetTopicIndex(progressMap: Map<*, *>, keys: List<String>, claimedMap: Map<*, *>) : Int {
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
                rive.controller.file?.getViewModelByName("ViewModel1")?.let { vm ->
                    val vmi = vm.createDefaultInstance()
                    rive.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi
                    vmi.getNumberProperty("level")?.value = level
                    val isFinished = (currentTopicProgress[topicKey] ?: 0f) >= 100f
                    val startText = if (isFinished) getString(R.string.review_text) else getString(R.string.start_text)
                    try {
                        vmi.getStringProperty("startText")?.value = startText
                        vmi.getStringProperty("rewardText")?.value = getString(R.string.reward_text_rive)
                    } catch (e: Exception) {}
                }
            }

            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                val activity = activity ?: return
                activity.runOnUiThread {
                    when (stateName) {
                        "start_button_pressed" -> {
                            val unlocked = isUnlocked[topicKey] ?: false
                            val btnType = if ((currentTopicProgress[topicKey] ?: 0f) >= 100f) "REVIEW" else "START"
                            showBottomControls(btnType, topicKey, null, !unlocked)
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

    private fun findTopicViewByKey(key: String): RiveAnimationView {
        val index = topicKeys.indexOf(key).coerceAtLeast(0)
        return topicViews[index]
    }

    private fun updateRiveButtonStates(view: RiveAnimationView, isFinished: Boolean, unlocked: Boolean, claimed: Boolean) {
        view.setBooleanState("State Machine 1", "lessonAvailable", unlocked && !isFinished)
        view.setBooleanState("State Machine 1", "rewardAvailable", unlocked && isFinished && !claimed)
        view.setBooleanState("State Machine 1", "reward", claimed)
    }

    private fun animateRiveProgress(key: String, view: RiveAnimationView, start: Float, end: Float, onUpdate: (Float) -> Unit) {
        if (start == end) return // Skip allocating animator if data hasn't updated

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
        isFragmentVisible = true
        if (::startContainer.isInitialized && startContainer.visibility == View.VISIBLE) {
            startContainer.animate().cancel()
            startContainer.visibility = View.GONE
        }
        // Resume only whatever is actually near the viewport - not everything.
        if (::scrollView.isInitialized) {
            scrollView.post {
                if (!isAdded) return@post
                updateRiveActiveStates()
                // Replay any progress that advanced while we were off-screen (e.g. the
                // XP-gained screen wrote new topic progress), so the fill is visible on
                // return instead of having silently completed in the background.
                playPendingProgressAnimations()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isFragmentVisible = false
        // Stop all Rive animations while the fragment isn't visible so nothing keeps
        // ticking (and draining CPU/battery) in the background.
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
        pendingProgressTargets.clear()
        super.onDestroyView()
        topicProgressListener?.remove()
    }
}