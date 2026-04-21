package com.example.tetragon.questions.questionMathSecondGrade

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
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
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.PlayableInstance
import com.example.tetragon.MainActivity
import com.example.tetragon.R
import com.example.tetragon.fragments.HomeFragment
import com.example.tetragon.reward.BagTapActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class Math2GradeFragment : Fragment() {

    private lateinit var scrollView: ScrollView
    private lateinit var startContainer: FrameLayout
    private lateinit var startEnabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtn: TextView
    private lateinit var continueEnabledBtn: AppCompatButton
    private lateinit var continueEnabledBtnBack: View
    private lateinit var startLessonLabel: TextView

    // --- JUMP AHEAD / NAVIGATION UI ---
    private lateinit var nextGradeLabel: TextView
    private lateinit var nextTopicName: TextView
    private lateinit var moveOnBtn: AppCompatButton
    private lateinit var backToGrade1Btn: View

    // Scroll to Target UI
    private lateinit var scrollTargetContainer: View
    private lateinit var scrollArrowIcon: ImageView

    private lateinit var topic1: RiveAnimationView
    private lateinit var topic2: RiveAnimationView
    private lateinit var topic3: RiveAnimationView
    private lateinit var topic4: RiveAnimationView
    private lateinit var topic5: RiveAnimationView
    private lateinit var topic6: RiveAnimationView
    private lateinit var topic7: RiveAnimationView
    private lateinit var topic8: RiveAnimationView

    private val topicViews by lazy { listOf(topic1, topic2, topic3, topic4, topic5, topic6, topic7, topic8) }
    private val topicNames = listOf("Arithmetics Basics", "Column Method", "Three Digit Numbers", "Comparison", "Length Measurement", "Time", "Multiplication table", "Division")
    private val topicKeys = listOf("ADDITION_SUBTRACTION_BASICS", "COLUMN_METHOD", "THREE_DIGIT_NUMBERS", "COMPARISON_THREE_DIGIT_NUMBERS", "LENGTH_MEASUREMENT", "TIME", "MULTIPLICATION", "DIVISION")

    // --- GRADE 3 DATA FOR DYNAMIC JUMP AHEAD ---
    private val grade3TopicNames = listOf("Complex Multiplication", "Complex Division", "Fractions", "Perimeter & Area")
    private val grade3TopicKeys = listOf("COMPLEX_MULTIPLICATION", "COMPLEX_DIVISION", "FRACTIONS", "PERIMETER_AREA")

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var topicProgressListener: ListenerRegistration? = null

    private var currentTopicProgress = mutableMapOf<String, Float>()
    private var claimedRewards = mutableMapOf<String, Boolean>()
    private var isUnlocked = mutableMapOf<String, Boolean>()

    private var pendingTopic: String? = null
    private var pendingRewardKey: String? = null
    private var hasInitialScrolled = false
    private var userHasScrolled = false
    private var lastReportedTopic = ""
    private var currentTargetIndex: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_math2_grade, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRiveTopics()
        setupJumpAheadUI()

        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            userHasScrolled = true
            determineVisibleTopic()
            if (startContainer.visibility == View.VISIBLE) {
                startContainer.animate().cancel()
                startContainer.visibility = View.GONE
            }
        }

        continueEnabledBtn.setOnClickListener { handleContinueClick() }

        // --- JUMP AHEAD LOGIC (To Grade 3) ---
        moveOnBtn.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", 3)
                putExtra("SELECTED_SUBJECT", "MATH")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            (requireActivity() as? Activity)?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        // --- GO BACK LOGIC (To Grade 1) ---
        backToGrade1Btn.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", 1)
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
        backToGrade1Btn = view.findViewById(R.id.back_to_grade1_btn)

        scrollTargetContainer = view.findViewById(R.id.scroll_to_target_container)
        scrollArrowIcon = view.findViewById(R.id.scroll_arrow_icon)

        topic1 = view.findViewById(R.id.topic1); topic2 = view.findViewById(R.id.topic2)
        topic3 = view.findViewById(R.id.topic3); topic4 = view.findViewById(R.id.topic4)
        topic5 = view.findViewById(R.id.topic5); topic6 = view.findViewById(R.id.topic6)
        topic7 = view.findViewById(R.id.topic7); topic8 = view.findViewById(R.id.topic8)

        startContainer.visibility = View.GONE
        scrollTargetContainer.visibility = View.GONE
    }

    private fun setupJumpAheadUI() {
        nextGradeLabel.text = "3 GRADE"
    }

    private fun determineVisibleTopic() {
        var bestIndex = 0
        var minDistance = Int.MAX_VALUE
        val focusPoint = scrollView.height / 2

        topicViews.forEachIndexed { index, view ->
            val rect = Rect()
            if (view.getGlobalVisibleRect(rect)) {
                val center = (rect.top + rect.bottom) / 2
                val distance = Math.abs(center - focusPoint)
                if (distance < minDistance) {
                    minDistance = distance
                    bestIndex = index
                }
            }
        }

        if (userHasScrolled && hasInitialScrolled) {
            if (bestIndex != currentTargetIndex) {
                if (scrollTargetContainer.visibility != View.VISIBLE) {
                    scrollTargetContainer.fadeInAndSlideUp()
                }
                scrollArrowIcon.setImageResource(if (bestIndex < currentTargetIndex) R.drawable.arrow_up else R.drawable.arrow_down)
            } else {
                if (scrollTargetContainer.visibility == View.VISIBLE) {
                    scrollTargetContainer.fadeOutAndSlideDown()
                }
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
            override fun notifyPlay(animation: PlayableInstance) { setupRiveViewModel(rive, level) }
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

    private fun listenToTopicProgress() {
        val user = auth.currentUser ?: return
        topicProgressListener = db.collection("users").document(user.uid).addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !isAdded) return@addSnapshotListener

            // 1. GRADE 2 PROGRESS
            val progressMap = snapshot.get("class2MathProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimedMap = snapshot.get("claimedRewardsMath2") as? Map<*, *> ?: emptyMap<String, Any>()

            var prevClaimed = true
            topicKeys.forEachIndexed { index, key ->
                val prog = (progressMap[key] as? Long ?: 0).toFloat()
                val claimed = claimedMap[key] as? Boolean ?: false

                animateRiveProgress(topicViews[index], currentTopicProgress[key] ?: 0f, prog) {
                    currentTopicProgress[key] = it
                }

                claimedRewards[key] = claimed
                isUnlocked[key] = if (index == 0) true else prevClaimed
                updateRiveButtonStates(topicViews[index], prog >= 100f, isUnlocked[key] ?: false, claimed)
                prevClaimed = prog >= 100f && claimed
            }

            currentTargetIndex = findTargetTopicIndex(progressMap, topicKeys, claimedMap)

            // 2. DYNAMIC JUMP AHEAD (TO GRADE 3)
            val progressMap3 = snapshot.get("class3MathProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimedMap3 = snapshot.get("claimedRewardsMath3") as? Map<*, *> ?: emptyMap<String, Any>()

            val activeGrade3Index = findTargetTopicIndex(progressMap3, grade3TopicKeys, claimedMap3)
            val activeTopicName3 = grade3TopicNames.getOrNull(activeGrade3Index) ?: grade3TopicNames[0]

            nextTopicName.text = "Topic ${activeGrade3Index + 1}: $activeTopicName3"

            if (!hasInitialScrolled) {
                scrollView.post {
                    if (isAdded && !userHasScrolled) {
                        scrollToSpecificTopic(currentTargetIndex, instant = true)
                        hasInitialScrolled = true
                    }
                }
            }
        }
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
        val rect = Rect()
        targetView.getDrawingRect(rect)
        scrollView.offsetDescendantRectToMyCoords(targetView, rect)
        val offset = scrollView.height / 16
        val scrollY = (rect.top - offset).coerceAtLeast(0)
        if (instant) scrollView.scrollTo(0, scrollY) else scrollView.smoothScrollTo(0, scrollY)
        determineVisibleTopic()
    }

    private fun showLoadingState(isLoading: Boolean) {
        startEnabledBtnContainer.visibility = if (isLoading) View.GONE else View.VISIBLE
        startDisabledBtnContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) startDisabledBtn.text = "PROCESSING..."
    }

    private fun showBottomControls(buttonText: String, topicKey: String?, rewardKey: String?, isLocked: Boolean) {
        pendingTopic = if (isLocked) null else topicKey
        pendingRewardKey = if (isLocked) null else rewardKey

        if (rewardKey != null) {
            val idx = topicKeys.indexOf(rewardKey)
            startLessonLabel.text = "Reward: ${topicNames[idx]}"
        } else if (topicKey != null) {
            val idx = topicKeys.indexOf(topicKey)
            startLessonLabel.text = "${idx + 1} Topic: ${topicNames[idx]}"
        }

        if (isLocked) {
            startEnabledBtnContainer.visibility = View.GONE
            startDisabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtn.text = "NOT AVAILABLE"
        } else {
            continueEnabledBtn.text = buttonText
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
        }
        startContainer.fadeInAndSlideUp()
    }

    private fun handleContinueClick() {
        showLoadingState(true)
        if (pendingRewardKey != null) {
            handleRewardClaimed(findTopicViewByKey(pendingRewardKey!!), pendingRewardKey!!)
        } else if (pendingTopic != null) {
            startActivity(Intent(requireContext(), Math2GradeQuestionActivity::class.java).apply {
                putExtra("TOPIC_KEY", pendingTopic)
            })
            scrollView.postDelayed({ if (isAdded) showLoadingState(false) }, 1000)
        }
    }

    private fun findTopicViewByKey(key: String): RiveAnimationView {
        val index = topicKeys.indexOf(key).coerceAtLeast(0)
        return topicViews[index]
    }

    private fun handleRewardClaimed(view: RiveAnimationView, topicKey: String) {
        val user = auth.currentUser ?: return
        showLoadingState(true)
        db.collection("users").document(user.uid)
            .update("claimedRewardsMath2.$topicKey", true)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                view.setBooleanState("State Machine 1", "rewardAvailable", false)
                view.setBooleanState("State Machine 1", "reward", true)
                startContainer.fadeOutAndSlideDown()
                startActivity(Intent(requireContext(), BagTapActivity::class.java))
            }
            .addOnFailureListener { if (isAdded) showLoadingState(false) }
    }

    private fun updateRiveButtonStates(view: RiveAnimationView, isFinished: Boolean, unlocked: Boolean, claimed: Boolean) {
        view.setBooleanState("State Machine 1", "lessonAvailable", unlocked && !isFinished)
        view.setBooleanState("State Machine 1", "rewardAvailable", unlocked && isFinished && !claimed)
        view.setBooleanState("State Machine 1", "reward", claimed)
    }

    private fun setupRiveViewModel(view: RiveAnimationView, level: Float) {
        val controller = view.controller
        controller.file?.getViewModelByName("ViewModel1")?.let { vm ->
            val vmi = vm.createDefaultInstance()
            controller.stateMachines.firstOrNull()?.viewModelInstance = vmi
            vmi.getNumberProperty("level")?.value = level
        }
    }

    private fun animateRiveProgress(view: RiveAnimationView, start: Float, end: Float, onUpdate: (Float) -> Unit) {
        ValueAnimator.ofFloat(start, end).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                view.setNumberState("State Machine 1", "progress", v)
                onUpdate(v)
            }
            start()
        }
    }

    private fun View.fadeInAndSlideUp() {
        visibility = View.VISIBLE
        alpha = 0f
        translationY = 100f
        animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun View.fadeOutAndSlideDown() {
        animate().alpha(0f).translationY(100f).setDuration(250).withEndAction { visibility = View.GONE }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        topicProgressListener?.remove()
    }
}