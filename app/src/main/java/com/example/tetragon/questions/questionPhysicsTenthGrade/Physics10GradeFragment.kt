package com.example.tetragon.questions.questionPhysicsTenthGrade

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.PlayableInstance
import com.example.tetragon.MainActivity
import com.example.tetragon.R
import com.example.tetragon.fragments.HomeFragment
import com.example.tetragon.reward.BagTapActivity
import com.example.tetragon.subscriptionModel.IntroSubscriptionActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class Physics10GradeFragment : Fragment() {

    private lateinit var scrollView: ScrollView
    private lateinit var startContainer: FrameLayout
    private lateinit var startEnabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtn: TextView
    private lateinit var continueEnabledBtn: AppCompatButton
    private lateinit var continueEnabledBtnBack: View
    private lateinit var startLessonLabel: TextView

    // --- JUMP AHEAD UI ---
    private lateinit var nextGradeLabel: TextView
    private lateinit var nextTopicName: TextView
    private lateinit var moveOnBtn: View
    private lateinit var backToPrevGradeBtn: View

    // Scroll Navigation UI
    private lateinit var scrollTargetContainer: View
    private lateinit var scrollArrowIcon: ImageView

    private lateinit var topic1: RiveAnimationView

    private val topicViews by lazy { listOf(topic1) }

    // --- LOCALIZED TOPIC DATA ---
    private val topicNames by lazy { listOf(getString(R.string.magnetism)) }
    private val topicKeys = listOf("MAGNETISM")

    // --- GRADE 11 TOPIC DATA FOR JUMP AHEAD ---
    private val grade11TopicNames by lazy { listOf(getString(R.string.modern_physics)) }
    private val grade11TopicKeys = listOf("MODERN_PHYSICS")

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listener: ListenerRegistration? = null

    private var currentProgress = mutableMapOf<String, Float>()
    private var claimedRewards = mutableMapOf<String, Boolean>()
    private var isUnlocked = mutableMapOf<String, Boolean>()

    private var userStars: Int = 15
    private var isInfinity: Boolean = false

    private var pendingTopicKey: String? = null
    private var pendingRewardKey: String? = null

    private var hasInitialScrolled = false
    private var userHasScrolled = false
    private var lastReportedTopic = ""
    private var currentTargetIndex: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_physics10_grade, container, false)
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
        moveOnBtn.setOnClickListener { navigateToGrade(11, "PHYSICS", R.anim.slide_in_right, R.anim.slide_out_left) }
        backToPrevGradeBtn.setOnClickListener { navigateToGrade(9, "PHYSICS", R.anim.slide_in_left, R.anim.slide_out_right) }

        scrollTargetContainer.setOnClickListener {
            userHasScrolled = false
            scrollToSpecificTopic(currentTargetIndex, instant = false)
        }

        listenProgress()
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
        moveOnBtn = view.findViewById(R.id.move_on_btn)
        nextGradeLabel = view.findViewById(R.id.next_grade_label)
        nextTopicName = view.findViewById(R.id.next_topic_name)
        backToPrevGradeBtn = view.findViewById(R.id.back_to_prev_grade_btn)
        scrollTargetContainer = view.findViewById(R.id.scroll_to_target_container)
        scrollArrowIcon = view.findViewById(R.id.scroll_arrow_icon)
        topic1 = view.findViewById(R.id.topic1)

        startContainer.visibility = View.GONE
        scrollTargetContainer.visibility = View.GONE
        nextGradeLabel.text = getString(R.string.eleventh_grade)
    }

    private fun listenProgress() {
        val user = auth.currentUser ?: return
        listener = db.collection("users").document(user.uid).addSnapshotListener { snap, _ ->
            if (snap == null || !isAdded) return@addSnapshotListener

            userStars = snap.getLong("stars")?.toInt() ?: 15
            isInfinity = snap.getBoolean("subscription") ?: false

            val progress = snap.get("class10PhysicsProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimed = snap.get("claimedRewardsPhysics10") as? Map<*, *> ?: emptyMap<String, Any>()

            var prevUnlocked = true
            topicKeys.forEachIndexed { index, key ->
                val prog = (progress[key] as? Long ?: 0).toFloat()
                val isClaimed = claimed[key] as? Boolean ?: false
                animateProgress(topicViews[index], currentProgress[key] ?: 0f, prog) { currentProgress[key] = it }
                isUnlocked[key] = prevUnlocked
                updateStates(topicViews[index], prog >= 100f, prevUnlocked, isClaimed)
                claimedRewards[key] = isClaimed
                prevUnlocked = prog >= 100f && isClaimed
            }

            currentTargetIndex = findTargetTopicIndex(progress, topicKeys, claimed)

            val progress11 = snap.get("class11PhysicsProgress") as? Map<*, *> ?: emptyMap<String, Any>()
            val claimed11 = snap.get("claimedRewardsPhysics11") as? Map<*, *> ?: emptyMap<String, Any>()
            val activeIndex11 = findTargetTopicIndex(progress11, grade11TopicKeys, claimed11)

            val tName = grade11TopicNames.getOrElse(activeIndex11) { grade11TopicNames[0] }
            nextTopicName.text = getString(R.string.topic_label, activeIndex11 + 1, tName)

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

    private fun handleContinue() {
        if (continueEnabledBtn.text == getString(R.string.subscribe_caps)) {
            startActivity(Intent(requireContext(), IntroSubscriptionActivity::class.java))
            return
        }

        showLoadingState(true)
        if (pendingRewardKey != null) {
            claimReward(pendingRewardKey!!)
        } else if (pendingTopicKey != null) {
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
        }
    }

    private fun launchQuestionActivity() {
        val intent = Intent(requireContext(), Physics10GradeQuestionActivity::class.java).apply {
            putExtra("TOPIC_KEY", pendingTopicKey)
        }
        startActivity(intent)
        view?.postDelayed({ if (isAdded) {
            showLoadingState(false)
            startContainer.fadeOutAndSlideDown()
        } }, 1000)
    }

    private fun showBottom(text: String, topicKey: String?, rewardKey: String?, locked: Boolean) {
        pendingTopicKey = if (locked) null else topicKey
        pendingRewardKey = if (locked) null else rewardKey

        val startContainerBg = view?.findViewById<LinearLayout>(R.id.start_container_background)

        if (rewardKey != null) {
            val idx = topicKeys.indexOf(rewardKey)
            startLessonLabel.text = getString(R.string.reward_label, topicNames.getOrNull(idx) ?: "")
        } else if (topicKey != null) {
            val idx = topicKeys.indexOf(topicKey)
            startLessonLabel.text = getString(R.string.topic_label, idx + 1, topicNames[idx])
        }

        if (locked) {
            startEnabledBtnContainer.visibility = View.GONE
            startDisabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtn.text = getString(R.string.not_available)
            startContainerBg?.setBackgroundResource(R.drawable.custom_background)
        } else if (rewardKey != null) {
            continueEnabledBtn.text = getString(R.string.claim_reward_btn)
            startContainerBg?.setBackgroundResource(R.drawable.custom_background)
            resetButtonTheme()
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
        } else if (!isInfinity && userStars <= 0) {
            continueEnabledBtn.text = getString(R.string.subscribe_caps)
            startLessonLabel.text = getString(R.string.out_of_stars_label)
            startContainerBg?.setBackgroundResource(R.drawable.custom_premium_background_2)
            applyPremiumTheme()
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
        } else {
            continueEnabledBtn.text = if (text == "REVIEW") getString(R.string.review_text) else getString(R.string.start_text)
            startContainerBg?.setBackgroundResource(R.drawable.custom_background)
            resetButtonTheme()
            startEnabledBtnContainer.visibility = View.VISIBLE
            startDisabledBtnContainer.visibility = View.GONE
        }
        startContainer.fadeInAndSlideUp()
    }

    private fun applyPremiumTheme() {
        continueEnabledBtn.backgroundTintList = null
        continueEnabledBtnBack.backgroundTintList = null
        continueEnabledBtn.setBackgroundResource(R.drawable.custom_gradient_button)
        continueEnabledBtnBack.setBackgroundResource(R.drawable.custom_gradient_button_shadow)
    }

    private fun resetButtonTheme() {
        val black3 = ContextCompat.getColor(requireContext(), R.color.black_3)
        val black2 = ContextCompat.getColor(requireContext(), R.color.black_2)
        continueEnabledBtn.setBackgroundResource(R.drawable.custom_button)
        continueEnabledBtnBack.setBackgroundResource(R.drawable.custom_button)
        continueEnabledBtn.backgroundTintList = ColorStateList.valueOf(black3)
        continueEnabledBtnBack.backgroundTintList = ColorStateList.valueOf(black2)
    }

    private fun setupRive() {
        setupSingleTopic(topic1, R.raw.progress_path_odd, 1f, "MAGNETISM")
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

                    // --- INJECT LOCALIZED RIVE TEXT ---
                    val isFinished = (currentProgress[topicKey] ?: 0f) >= 100f
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

    private fun claimReward(key: String) {
        val user = auth.currentUser ?: return
        showLoadingState(true)
        db.collection("users").document(user.uid).update("claimedRewardsPhysics10.$key", true)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                startContainer.fadeOutAndSlideDown()
                startActivity(Intent(requireContext(), BagTapActivity::class.java))
            }.addOnFailureListener { if (isAdded) showLoadingState(false) }
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
        if (loading) startDisabledBtn.text = getString(R.string.processing_caps)
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

    override fun onResume() {
        super.onResume()
        if (::startContainer.isInitialized && startContainer.visibility == View.VISIBLE) {
            startContainer.animate().cancel()
            startContainer.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
    }
}