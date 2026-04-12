package com.example.tetragon.questions.questionMathFirstGrade

import android.animation.ValueAnimator
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
import com.example.tetragon.R
import com.example.tetragon.fragments.HomeFragment
import com.example.tetragon.reward.BagTapActivity
import com.google.firebase.auth.FirebaseAuth
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

    // Scroll Navigation UI
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
    private lateinit var topic9: RiveAnimationView
    private lateinit var topic10: RiveAnimationView
    private lateinit var topic11: RiveAnimationView
    private lateinit var topic12: RiveAnimationView

    private val topicViews by lazy { listOf(topic1, topic2, topic3, topic4, topic5, topic6, topic7, topic8, topic9, topic10, topic11, topic12) }
    private val topicNames = listOf("Counting numbers", "Addition", "Subtraction", "Odd or Even", "Comparison", "Parentheses", "Addition up to 20", "Subtraction up to 20", "Round numbers", "Two-digit numbers", "Problem solving", "Length. Centimeter.")

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var topicProgressListener: ListenerRegistration? = null

    private var currentTopicProgress = mutableMapOf<String, Float>()
    private var claimedRewards = mutableMapOf<String, Boolean>()
    private var isUnlocked = mutableMapOf<String, Boolean>()

    private var pendingTopic: MathGrade1Topic? = null
    private var pendingRewardKey: String? = null
    private var hasInitialScrolled = false
    private var userHasScrolled = false
    private var lastReportedTopic = ""
    private var currentTargetIndex: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_math1_grade, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRiveTopics()

        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            userHasScrolled = true
            determineVisibleTopic()

            if (startContainer.visibility == View.VISIBLE) {
                startContainer.animate().cancel()
                startContainer.visibility = View.GONE
            }
        }

        continueEnabledBtn.setOnClickListener { handleContinueClick() }

        scrollTargetContainer.setOnClickListener {
            userHasScrolled = false
            scrollToSpecificTopic(currentTargetIndex, instant = false)
        }

        listenToTopicProgress()
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

        // --- NAVIGATION UI LOGIC ---
        if (userHasScrolled && hasInitialScrolled) {
            if (bestIndex != currentTargetIndex) {
                if (scrollTargetContainer.visibility != View.VISIBLE) {
                    scrollTargetContainer.fadeInAndSlideUp()
                }

                // If bestIndex < currentTargetIndex, current view is below target (target is UP)
                if (bestIndex < currentTargetIndex) {
                    scrollArrowIcon.setImageResource(R.drawable.arrow_up)
                } else {
                    scrollArrowIcon.setImageResource(R.drawable.arrow_down)
                }
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

    private fun initViews(view: View) {
        scrollView = view.findViewById(R.id.level_scroll_container)
        startContainer = view.findViewById(R.id.start_container)
        startEnabledBtnContainer = view.findViewById(R.id.start_enabled_btn_container)
        startDisabledBtnContainer = view.findViewById(R.id.start_disabled_btn_container)
        startDisabledBtn = view.findViewById(R.id.start_disabled_btn)
        continueEnabledBtn = view.findViewById(R.id.continue_enabled_btn)
        continueEnabledBtnBack = view.findViewById(R.id.continue_enabled_btn_back)
        startLessonLabel = view.findViewById(R.id.start_lesson_label)

        scrollTargetContainer = view.findViewById(R.id.scroll_to_target_container)
        scrollArrowIcon = view.findViewById(R.id.scroll_arrow_icon)

        topic1 = view.findViewById(R.id.topic1)
        topic2 = view.findViewById(R.id.topic2)
        topic3 = view.findViewById(R.id.topic3)
        topic4 = view.findViewById(R.id.topic4)
        topic5 = view.findViewById(R.id.topic5)
        topic6 = view.findViewById(R.id.topic6)
        topic7 = view.findViewById(R.id.topic7)
        topic8 = view.findViewById(R.id.topic8)
        topic9 = view.findViewById(R.id.topic9)
        topic10 = view.findViewById(R.id.topic10)
        topic11 = view.findViewById(R.id.topic11)
        topic12 = view.findViewById(R.id.topic12)

        startContainer.visibility = View.GONE
        scrollTargetContainer.visibility = View.GONE
    }

    private fun setupRiveTopics() {
        setupSingleTopic(topic1, R.raw.progress_path_odd, 1f, MathGrade1Topic.COUNT_NUMBERS)
        setupSingleTopic(topic2, R.raw.progress_path_even, 2f, MathGrade1Topic.ADDITION)
        setupSingleTopic(topic3, R.raw.progress_path_odd, 3f, MathGrade1Topic.SUBTRACTION)
        setupSingleTopic(topic4, R.raw.progress_path_even, 4f, MathGrade1Topic.ODD_OR_EVEN)
        setupSingleTopic(topic5, R.raw.progress_path_odd, 5f, MathGrade1Topic.COMPARISON)
        setupSingleTopic(topic6, R.raw.progress_path_even, 6f, MathGrade1Topic.PARENTHESES)
        setupSingleTopic(topic7, R.raw.progress_path_odd, 7f, MathGrade1Topic.ADDITION_UP_TO_20)
        setupSingleTopic(topic8, R.raw.progress_path_even, 8f, MathGrade1Topic.SUBTRACTION_UP_TO_20)
        setupSingleTopic(topic9, R.raw.progress_path_odd, 9f, MathGrade1Topic.ROUND_NUMBERS)
        setupSingleTopic(topic10, R.raw.progress_path_even, 10f, MathGrade1Topic.TWO_DIGIT_NUMBERS)
        setupSingleTopic(topic11, R.raw.progress_path_odd, 11f, MathGrade1Topic.PROBLEM_SOLVING)
        setupSingleTopic(topic12, R.raw.progress_path_even, 12f, MathGrade1Topic.LENGTH_CENTIMETER)
    }

    private fun setupSingleTopic(rive: RiveAnimationView, res: Int, level: Float, topic: MathGrade1Topic) {
        rive.setRiveResource(res, stateMachineName = "State Machine 1", autoplay = true)
        rive.registerListener(object : RiveFileController.Listener {
            override fun notifyPlay(animation: PlayableInstance) { setupRiveViewModel(rive, level) }
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                activity?.runOnUiThread {
                    when (stateName) {
                        "start_button_pressed" -> {
                            val unlocked = if (topic == MathGrade1Topic.COUNT_NUMBERS) true else isUnlocked[topic.name] ?: false
                            val btnText = if ((currentTopicProgress[topic.name] ?: 0f) >= 100f) "REVIEW" else "START"
                            showBottomControls(btnText, topic, null, !unlocked)
                        }
                        "reward_button_pressed" -> {
                            if (claimedRewards[topic.name] == true) return@runOnUiThread
                            val canClaim = (currentTopicProgress[topic.name] ?: 0f) >= 100f && (if (topic == MathGrade1Topic.COUNT_NUMBERS) true else isUnlocked[topic.name] == true)
                            showBottomControls("CLAIM", null, topic.name, !canClaim)
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

            val progressMap = snapshot.get("class1MathProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimedMap = snapshot.get("claimedRewardsMath1") as? Map<*, *> ?: emptyMap<String, Any>()

            val topics = listOf("COUNT_NUMBERS", "ADDITION", "SUBTRACTION", "ODD_OR_EVEN", "COMPARISON", "PARENTHESES", "ADDITION_UP_TO_20", "SUBTRACTION_UP_TO_20", "ROUND_NUMBERS", "TWO_DIGIT_NUMBERS", "PROBLEM_SOLVING", "LENGTH_CENTIMETER")
            var prevClaimed = true

            topics.forEachIndexed { index, key ->
                val prog = (progressMap[key] as? Long ?: 0).toFloat()
                val claimed = claimedMap[key] as? Boolean ?: false

                animateRiveProgress(topicViews[index], currentTopicProgress[key] ?: 0f, prog) {
                    currentTopicProgress[key] = it
                }

                claimedRewards[key] = claimed
                isUnlocked[key] = prevClaimed
                updateRiveButtonStates(topicViews[index], prog >= 100f, prevClaimed, claimed)
                prevClaimed = prog >= 100f && claimed
            }

            currentTargetIndex = findTargetTopicIndex(progressMap, topics, claimedMap)

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

    private fun findTargetTopicIndex(progressMap: Map<*, *>, topics: List<String>, claimedMap: Map<*, *>): Int {
        topics.forEachIndexed { index, key ->
            val prog = (progressMap[key] as? Long ?: 0).toInt()
            if (prog in 1..99) return index
            val prevClaimed = if (index == 0) true else {
                val prevKey = topics[index - 1]
                val prevProg = (progressMap[prevKey] as? Long ?: 0)
                val prevClaimedStatus = claimedMap[prevKey] as? Boolean ?: false
                prevProg >= 100L && prevClaimedStatus
            }
            if (prog == 0 && prevClaimed) return index
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

        if (instant) {
            scrollView.scrollTo(0, scrollY)
        } else {
            scrollView.smoothScrollTo(0, scrollY)
        }

        determineVisibleTopic()
    }

    private fun showLoadingState(isLoading: Boolean) {
        if (isLoading) {
            startEnabledBtnContainer.visibility = View.GONE
            startDisabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtn.text = "PROCESSING..."
        } else {
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
        }
    }

    private fun showBottomControls(buttonText: String, topic: MathGrade1Topic?, rewardKey: String?, isLocked: Boolean) {
        pendingTopic = if (isLocked) null else topic
        pendingRewardKey = if (isLocked) null else rewardKey

        if (rewardKey != null) {
            startLessonLabel.text = "Get reward"
        } else if (topic != null) {
            val idx = MathGrade1Topic.values().indexOf(topic)
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
            val view = findTopicViewByKey(pendingRewardKey!!)
            handleRewardClaimed(view, pendingRewardKey!!)
        } else if (pendingTopic != null) {
            val intent = Intent(context, Math1GradeQuestionActivity::class.java).apply {
                putExtra("TOPIC_KEY", pendingTopic!!.name)
            }
            startActivity(intent)
            view?.postDelayed({ if(isAdded) showLoadingState(false) }, 1000)
        }
    }

    private fun findTopicViewByKey(key: String): RiveAnimationView {
        return when (key) {
            "COUNT_NUMBERS" -> topic1
            "ADDITION" -> topic2
            "SUBTRACTION" -> topic3
            "ODD_OR_EVEN" -> topic4
            "COMPARISON" -> topic5
            "PARENTHESES" -> topic6
            "ADDITION_UP_TO_20" -> topic7
            "SUBTRACTION_UP_TO_20" -> topic8
            "ROUND_NUMBERS" -> topic9
            "TWO_DIGIT_NUMBERS" -> topic10
            "PROBLEM_SOLVING" -> topic11
            "LENGTH_CENTIMETER" -> topic12
            else -> topic1
        }
    }

    private fun handleRewardClaimed(view: RiveAnimationView, topicKey: String) {
        val user = auth.currentUser ?: return
        showLoadingState(true)

        db.collection("users").document(user.uid)
            .update("claimedRewardsMath1.$topicKey", true)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                view.setBooleanState("State Machine 1", "rewardAvailable", false)
                view.setBooleanState("State Machine 1", "reward", true)
                startContainer.fadeOutAndSlideDown()
                startActivity(Intent(requireContext(), BagTapActivity::class.java))
            }
            .addOnFailureListener {
                if (isAdded) showLoadingState(false)
            }
    }

    private fun updateRiveButtonStates(view: RiveAnimationView, isFinished: Boolean, unlocked: Boolean, claimed: Boolean) {
        view.setBooleanState("State Machine 1", "lessonAvailable", unlocked && !isFinished && !claimed)
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