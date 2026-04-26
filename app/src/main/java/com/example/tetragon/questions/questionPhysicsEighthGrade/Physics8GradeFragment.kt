package com.example.tetragon.questions.questionPhysicsEighthGrade

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

class Physics8GradeFragment : Fragment() {

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
    private lateinit var backToPrevGradeBtn: View

    // Scroll Navigation UI
    private lateinit var scrollTargetContainer: View
    private lateinit var scrollArrowIcon: ImageView

    private lateinit var topic1: RiveAnimationView

    private val topicViews by lazy { listOf(topic1) }
    private val topicNames = listOf("Force and Motion")
    private val physics8Topics = listOf("FORCE_AND_MOTION")

    // --- GRADE 9 TOPIC DATA FOR JUMP AHEAD ---
    private val grade9TopicNames = listOf("Newton's Laws")
    private val grade9TopicKeys = listOf("NEWTONS_LAW")

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listener: ListenerRegistration? = null

    private var currentProgress = mutableMapOf<String, Float>()
    private var claimedRewards = mutableMapOf<String, Boolean>()
    private var isUnlocked = mutableMapOf<String, Boolean>()

    // FIXED: Using Grade 8 Enum instead of Grade 7
    private var pendingTopic: PhysicsGrade8Topic? = null
    private var pendingRewardKey: String? = null

    private var hasInitialScrolled = false
    private var userHasScrolled = false
    private var lastReportedTopic = ""
    private var currentTargetIndex: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_physics8_grade, container, false)
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
            navigateToGrade(9, "PHYSICS", R.anim.slide_in_right, R.anim.slide_out_left)
        }

        backToPrevGradeBtn.setOnClickListener {
            navigateToGrade(7, "PHYSICS", R.anim.slide_in_left, R.anim.slide_out_right)
        }

        scrollTargetContainer.setOnClickListener {
            userHasScrolled = false
            scrollToSpecificTopic(currentTargetIndex, instant = false)
        }

        listenProgress()
    }

    private fun navigateToGrade(grade: Int, subject: String, enterAnim: Int, exitAnim: Int) {
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            putExtra("SELECTED_GRADE", grade)
            putExtra("SELECTED_SUBJECT", subject)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        activity?.overridePendingTransition(enterAnim, exitAnim)
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
                if (scrollTargetContainer.visibility != View.VISIBLE) scrollTargetContainer.fadeInAndSlideUp()
                scrollArrowIcon.setImageResource(if (bestIndex < currentTargetIndex) R.drawable.arrow_up else R.drawable.arrow_down)
            } else {
                if (scrollTargetContainer.visibility == View.VISIBLE) scrollTargetContainer.fadeOutAndSlideDown()
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

        moveOnBtn = view.findViewById(R.id.move_on_btn)
        nextGradeLabel = view.findViewById(R.id.next_grade_label)
        nextTopicName = view.findViewById(R.id.next_topic_name)
        backToPrevGradeBtn = view.findViewById(R.id.back_to_prev_grade_btn)

        scrollTargetContainer = view.findViewById(R.id.scroll_to_target_container)
        scrollArrowIcon = view.findViewById(R.id.scroll_arrow_icon)

        topic1 = view.findViewById(R.id.topic1)
        startContainer.visibility = View.GONE
        scrollTargetContainer.visibility = View.GONE
        nextGradeLabel.text = "9 GRADE"
    }

    private fun setupRive() {
        setupSingleTopic(topic1, R.raw.progress_path_odd, 1f, "FORCE_AND_MOTION")
    }

    private fun setupSingleTopic(rive: RiveAnimationView, res: Int, level: Float, topicKey: String) {
        rive.setRiveResource(res, stateMachineName = "State Machine 1", autoplay = true)
        rive.registerListener(object : RiveFileController.Listener {
            override fun notifyPlay(animation: PlayableInstance) { setupViewModel(rive, level) }
            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                activity?.runOnUiThread {
                    when (stateName) {
                        "start_button_pressed" -> {
                            val unlocked = isUnlocked[topicKey] ?: false
                            val btnText = if ((currentProgress[topicKey] ?: 0f) >= 100f) "REVIEW" else "START"
                            showBottom(btnText, topicKey, null, !unlocked)
                        }
                        "reward_button_pressed" -> {
                            if (claimedRewards[topicKey] == true) return@runOnUiThread
                            val canClaim = (currentProgress[topicKey] ?: 0f) >= 100f && isUnlocked[topicKey] == true
                            showBottom("CLAIM", null, topicKey, !canClaim)
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

            val progress = snap.get("class8PhysicsProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimed = snap.get("claimedRewardsPhysics8") as? Map<*, *> ?: emptyMap<String, Any>()

            var prevUnlocked = true
            physics8Topics.forEachIndexed { index, key ->
                val prog = (progress[key] as? Long ?: 0).toFloat()
                val isClaimed = claimed[key] as? Boolean ?: false

                animateProgress(topicViews[index], currentProgress[key] ?: 0f, prog) { currentProgress[key] = it }

                isUnlocked[key] = prevUnlocked
                updateStates(topicViews[index], prog >= 100f, prevUnlocked, isClaimed)
                claimedRewards[key] = isClaimed
                prevUnlocked = prog >= 100f && isClaimed
            }

            currentTargetIndex = findTargetTopicIndex(progress, physics8Topics, claimed)

            val progress9 = snap.get("class9PhysicsProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimed9 = snap.get("claimedRewardsPhysics9") as? Map<*, *> ?: emptyMap<String, Any>()
            val activeIndex9 = findTargetTopicIndex(progress9, grade9TopicKeys, claimed9)
            nextTopicName.text = "Topic ${activeIndex9 + 1}: ${grade9TopicNames.getOrElse(activeIndex9) { grade9TopicNames[0] }}"

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
                (progressMap[prevKey] as? Long ?: 0) >= 100L && (claimedMap[prevKey] as? Boolean ?: false)
            }
            if (prog == 0 && prevClaimed) return index
        }
        return 0
    }

    // FIXED: HandleContinue now performs the Intent navigation
    private fun handleContinue() {
        showLoadingState(true)
        if (pendingRewardKey != null) {
            claimReward(pendingRewardKey!!)
        } else if (pendingTopic != null) {
            val intent = Intent(requireContext(), Physics8GradeQuestionActivity::class.java).apply {
                putExtra("TOPIC_KEY", pendingTopic!!.name)
            }
            startActivity(intent)
            view?.postDelayed({ if(isAdded) showLoadingState(false) }, 1000)
        }
    }

    private fun claimReward(key: String) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).update("claimedRewardsPhysics8.$key", true)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                startContainer.fadeOutAndSlideDown()
                startActivity(Intent(requireContext(), BagTapActivity::class.java))
            }.addOnFailureListener { if (isAdded) showLoadingState(false) }
    }

    // FIXED: Correctly mapping topicKey String to the PhysicsGrade8Topic Enum
    private fun showBottom(text: String, topicKey: String?, rewardKey: String?, locked: Boolean) {
        pendingTopic = if (locked || topicKey == null) null else {
            try { PhysicsGrade8Topic.valueOf(topicKey) } catch (e: Exception) { null }
        }
        pendingRewardKey = if (locked) null else rewardKey

        if (rewardKey != null) {
            startLessonLabel.text = "Get reward"
        } else if (pendingTopic != null) {
            val idx = physics8Topics.indexOf(topicKey)
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

    private fun scrollToSpecificTopic(index: Int, instant: Boolean = false) {
        if (!isAdded || index < 0 || index >= topicViews.size) return
        val targetView = topicViews[index]
        val rect = Rect()
        targetView.getDrawingRect(rect)
        scrollView.offsetDescendantRectToMyCoords(targetView, rect)
        val scrollY = (rect.top - (scrollView.height / 16)).coerceAtLeast(0)
        if (instant) scrollView.scrollTo(0, scrollY) else scrollView.smoothScrollTo(0, scrollY)
        determineVisibleTopic()
    }

    private fun updateStates(v: RiveAnimationView, f: Boolean, u: Boolean, c: Boolean) {
        v.setBooleanState("State Machine 1", "lessonAvailable", u && !f)
        v.setBooleanState("State Machine 1", "rewardAvailable", f && !c)
        v.setBooleanState("State Machine 1", "reward", c)
    }

    private fun setupViewModel(v: RiveAnimationView, l: Float) {
        v.controller.file?.getViewModelByName("ViewModel1")?.let {
            val inst = it.createDefaultInstance()
            v.controller.stateMachines.firstOrNull()?.viewModelInstance = inst
            inst.getNumberProperty("level")?.value = l
        }
    }

    private fun animateProgress(v: RiveAnimationView, s: Float, e: Float, update: (Float) -> Unit) {
        ValueAnimator.ofFloat(s, e).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val valF = it.animatedValue as Float
                v.setNumberState("State Machine 1", "progress", valF)
                update(valF)
            }
            start()
        }
    }

    private fun showLoadingState(loading: Boolean) {
        startEnabledBtnContainer.visibility = if (loading) View.GONE else View.VISIBLE
        startDisabledBtnContainer.visibility = if (loading) View.VISIBLE else View.GONE
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