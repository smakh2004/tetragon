package com.example.tetragon.questions.questionPhysicsSevenGrade

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
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

class Physics7GradeFragment : Fragment() {

    private lateinit var scrollView: ScrollView
    private lateinit var startContainer: FrameLayout
    private lateinit var startEnabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtn: TextView
    private lateinit var continueEnabledBtn: AppCompatButton
    private lateinit var startLessonLabel: TextView

    // --- JUMP AHEAD UI ---
    private lateinit var nextGradeLabel: TextView
    private lateinit var nextTopicName: TextView
    private lateinit var moveOnBtn: AppCompatButton

    // Scroll Navigation UI
    private lateinit var scrollTargetContainer: View
    private lateinit var scrollArrowIcon: ImageView

    private lateinit var topic1: RiveAnimationView
    private lateinit var topic2: RiveAnimationView
    private lateinit var topic3: RiveAnimationView

    private val topicViews by lazy { listOf(topic1, topic2, topic3) }
    private val topicNames = listOf("SI Units", "Density", "Simple Machines")
    private val physicsTopics = listOf("SI_UNITS", "DENSITY", "SIMPLE_MACHINES")

    // --- GRADE 8 TOPIC DATA FOR JUMP AHEAD ---
    private val grade8TopicNames = listOf(
        "Force and Motion"
    )
    private val grade8TopicKeys = listOf(
        "FORCE_AND_MOTION"
    )

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listener: ListenerRegistration? = null

    private var currentProgress = mutableMapOf<String, Float>()
    private var claimedRewards = mutableMapOf<String, Boolean>()
    private var isUnlocked = mutableMapOf<String, Boolean>()

    private var pendingTopic: PhysicsGrade7Topic? = null
    private var pendingRewardKey: String? = null

    private var hasInitialScrolled = false
    private var userHasScrolled = false
    private var lastReportedTopic = ""
    private var currentTargetIndex: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_physics7_grade, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRive()

        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            userHasScrolled = true
            determineVisibleTopic()
            if (startContainer.visibility == View.VISIBLE) {
                startContainer.animate().cancel()
                startContainer.fadeOutAndSlideDown()
            }
        }

        continueEnabledBtn.setOnClickListener { handleContinue() }

        moveOnBtn.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", 8)
                putExtra("SELECTED_SUBJECT", "PHYSICS")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            activity?.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        scrollTargetContainer.setOnClickListener {
            userHasScrolled = false
            scrollToSpecificTopic(currentTargetIndex, instant = false)
        }

        listenProgress()
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
        startLessonLabel = view.findViewById(R.id.start_lesson_label)

        // --- JUMP AHEAD VIEWS ---
        moveOnBtn = view.findViewById(R.id.move_on_btn)
        nextGradeLabel = view.findViewById(R.id.next_grade_label)
        nextTopicName = view.findViewById(R.id.next_topic_name)

        scrollTargetContainer = view.findViewById(R.id.scroll_to_target_container)
        scrollArrowIcon = view.findViewById(R.id.scroll_arrow_icon)

        topic1 = view.findViewById(R.id.topic1)
        topic2 = view.findViewById(R.id.topic2)
        topic3 = view.findViewById(R.id.topic3)

        startContainer.visibility = View.GONE
        scrollTargetContainer.visibility = View.GONE

        nextGradeLabel.text = "8 GRADE"
    }

    private fun setupRive() {
        setupSingleTopic(topic1, R.raw.progress_path_odd, 1f, PhysicsGrade7Topic.SI_UNITS)
        setupSingleTopic(topic2, R.raw.progress_path_even, 2f, PhysicsGrade7Topic.DENSITY)
        setupSingleTopic(topic3, R.raw.progress_path_odd, 3f, PhysicsGrade7Topic.SIMPLE_MACHINES)
    }

    private fun setupSingleTopic(rive: RiveAnimationView, res: Int, level: Float, topic: PhysicsGrade7Topic) {
        rive.setRiveResource(res, stateMachineName = "State Machine 1", autoplay = true)
        rive.registerListener(object : RiveFileController.Listener {
            override fun notifyPlay(animation: PlayableInstance) { setupViewModel(rive, level) }
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                activity?.runOnUiThread {
                    when (stateName) {
                        "start_button_pressed" -> {
                            val unlocked = if (topic == PhysicsGrade7Topic.SI_UNITS) true else isUnlocked[topic.name] ?: false
                            val btnText = if ((currentProgress[topic.name] ?: 0f) >= 100f) "REVIEW" else "START"
                            showBottom(btnText, topic, null, !unlocked)
                        }
                        "reward_button_pressed" -> {
                            if (claimedRewards[topic.name] == true) return@runOnUiThread
                            val canClaim = (currentProgress[topic.name] ?: 0f) >= 100f && (isUnlocked[topic.name] == true || topic == PhysicsGrade7Topic.SI_UNITS)
                            showBottom("CLAIM", null, topic.name, !canClaim)
                        }
                    }
                }
            }
            override fun notifyLoop(p: PlayableInstance) {}
            override fun notifyPause(p: PlayableInstance) {}
            override fun notifyStop(p: PlayableInstance) {}
        })
    }

    private fun listenProgress() {
        val user = auth.currentUser ?: return
        listener = db.collection("users").document(user.uid).addSnapshotListener { snap, _ ->
            if (snap == null || !isAdded) return@addSnapshotListener

            // 1. GRADE 7 PROGRESS
            val progress = snap.get("class7PhysicsProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimed = snap.get("claimedRewardsPhysics7") as? Map<*, *> ?: emptyMap<String, Any>()

            var prevClaimed = true
            physicsTopics.forEachIndexed { index, key ->
                val prog = (progress[key] as? Long ?: 0).toFloat()
                val isClaimed = claimed[key] as? Boolean ?: false

                animateProgress(topicViews[index], currentProgress[key] ?: 0f, prog) {
                    currentProgress[key] = it
                }

                claimedRewards[key] = isClaimed
                isUnlocked[key] = prevClaimed
                updateStates(topicViews[index], prog >= 100f, prevClaimed, isClaimed)
                prevClaimed = prog >= 100f && isClaimed
            }

            currentTargetIndex = findTargetTopicIndex(progress, physicsTopics, claimed)

            // 2. DYNAMIC JUMP AHEAD (GRADE 8)
            val progressMap8 = snap.get("class8PhysicsProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimedMap8 = snap.get("claimedRewardsPhysics8") as? Map<*, *> ?: emptyMap<String, Any>()

            val activeGrade8Index = findTargetTopicIndex(progressMap8, grade8TopicKeys, claimedMap8)
            val activeTopicName8 = grade8TopicNames.getOrNull(activeGrade8Index) ?: grade8TopicNames[0]
            nextTopicName.text = "Topic ${activeGrade8Index + 1}: $activeTopicName8"

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

        if (instant) scrollView.scrollTo(0, scrollY) else scrollView.smoothScrollTo(0, scrollY)
        determineVisibleTopic()
    }

    private fun handleContinue() {
        showLoadingState(true)
        if (pendingRewardKey != null) {
            claimReward(pendingRewardKey!!)
        } else if (pendingTopic != null) {
            val intent = Intent(context, Physics7GradeQuestionActivity::class.java).apply {
                putExtra("TOPIC_KEY", pendingTopic!!.name)
            }
            startActivity(intent)
            view?.postDelayed({ if(isAdded) showLoadingState(false) }, 1000)
        }
    }

    private fun claimReward(key: String) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid)
            .update("claimedRewardsPhysics7.$key", true)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                startContainer.fadeOutAndSlideDown()
                // Update based on which topic it is
                val view = if (key == "SI_UNITS") topic1 else topic2
                view.setBooleanState("State Machine 1", "reward", true)
                view.setBooleanState("State Machine 1", "rewardAvailable", false)
                startActivity(Intent(requireContext(), BagTapActivity::class.java))
            }
            .addOnFailureListener { if (isAdded) showLoadingState(false) }
    }

    private fun showLoadingState(isLoading: Boolean) {
        startEnabledBtnContainer.visibility = if (isLoading) View.GONE else View.VISIBLE
        startDisabledBtnContainer.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) startDisabledBtn.text = "PROCESSING..."
    }

    private fun showBottom(text: String, topic: PhysicsGrade7Topic?, rewardKey: String?, locked: Boolean) {
        pendingTopic = if (locked) null else topic
        pendingRewardKey = if (locked) null else rewardKey

        if (rewardKey != null) {
            startLessonLabel.text = "Get reward"
        } else if (topic != null) {
            val idx = PhysicsGrade7Topic.values().indexOf(topic)
            startLessonLabel.text = "${idx + 1} Topic: ${topicNames[idx]}"
        }

        if (locked) {
            startEnabledBtnContainer.visibility = View.GONE
            startDisabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtn.text = "NOT AVAILABLE"
        } else {
            continueEnabledBtn.text = text
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
        }
        startContainer.fadeInAndSlideUp()
    }

    private fun updateStates(view: RiveAnimationView, finished: Boolean, unlocked: Boolean, claimed: Boolean) {
        view.setBooleanState("State Machine 1", "lessonAvailable", unlocked && !finished)
        view.setBooleanState("State Machine 1", "rewardAvailable", finished && !claimed)
        view.setBooleanState("State Machine 1", "reward", claimed)
    }

    private fun setupViewModel(view: RiveAnimationView, level: Float) {
        val controller = view.controller
        controller.file?.getViewModelByName("ViewModel1")?.let {
            val inst = it.createDefaultInstance()
            controller.stateMachines.firstOrNull()?.viewModelInstance = inst
            inst.getNumberProperty("level")?.value = level
        }
    }

    private fun animateProgress(view: RiveAnimationView, start: Float, end: Float, onUpdate: (Float) -> Unit) {
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
        listener?.remove()
    }
}