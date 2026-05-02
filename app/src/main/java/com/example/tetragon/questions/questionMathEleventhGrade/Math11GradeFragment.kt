package com.example.tetragon.questions.questionMathEleventhGrade

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

class Math11GradeFragment : Fragment() {

    private lateinit var scrollView: ScrollView
    private lateinit var startContainer: FrameLayout
    private lateinit var startEnabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtnContainer: FrameLayout
    private lateinit var startDisabledBtn: TextView
    private lateinit var continueEnabledBtn: AppCompatButton
    private lateinit var continueEnabledBtnBack: View
    private lateinit var startLessonLabel: TextView

    private lateinit var nextGradeLabel: TextView
    private lateinit var backToGrade10Btn: View

    private lateinit var scrollTargetContainer: View
    private lateinit var scrollArrowIcon: ImageView

    private lateinit var topic1: RiveAnimationView

    private val topicViews by lazy { listOf(topic1) }

    // Use string resources for topic names
    private val topicNames by lazy { listOf(getString(R.string.topic_derivatives)) }
    private val topicKeys = listOf("DERIVATIVES")

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var topicProgressListener: ListenerRegistration? = null

    private var currentTopicProgress = mutableMapOf<String, Float>()
    private var claimedRewards = mutableMapOf<String, Boolean>()
    private var isUnlocked = mutableMapOf<String, Boolean>()

    private var userStars: Int = 15
    private var isInfinity: Boolean = false

    private var pendingTopic: String? = null
    private var pendingRewardKey: String? = null
    private var hasInitialScrolled = false
    private var userHasScrolled = false
    private var lastReportedTopic = ""
    private var currentTargetIndex: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_math11_grade, container, false)
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

        backToGrade10Btn.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", 10)
                putExtra("SELECTED_SUBJECT", "MATH")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            (requireActivity() as? Activity)?.overridePendingTransition(
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
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
        backToGrade10Btn = view.findViewById(R.id.back_to_10_grade_btn)

        scrollTargetContainer = view.findViewById(R.id.scroll_to_target_container)
        scrollArrowIcon = view.findViewById(R.id.scroll_arrow_icon)

        topic1 = view.findViewById(R.id.topic1)

        startContainer.visibility = View.GONE
        scrollTargetContainer.visibility = View.GONE
        nextGradeLabel.text = getString(R.string.finish_caps)
    }

    private fun listenToTopicProgress() {
        val user = auth.currentUser ?: return
        topicProgressListener =
            db.collection("users").document(user.uid).addSnapshotListener { snapshot, _ ->
                if (snapshot == null || !isAdded) return@addSnapshotListener

                userStars = snapshot.getLong("stars")?.toInt() ?: 15
                isInfinity = snapshot.getBoolean("subscription") ?: false

                val progressMap =
                    snapshot.get("class11MathProgress") as? Map<*, *> ?: emptyMap<String, Any>()
                val claimedMap =
                    snapshot.get("claimedRewardsMath11") as? Map<*, *> ?: emptyMap<String, Any>()

                var prevClaimed = true
                topicKeys.forEachIndexed { index, key ->
                    val prog = (progressMap[key] as? Long ?: 0).toFloat()
                    val claimed = claimedMap[key] as? Boolean ?: false
                    animateRiveProgress(
                        topicViews[index],
                        currentTopicProgress[key] ?: 0f,
                        prog
                    ) { currentTopicProgress[key] = it }
                    claimedRewards[key] = claimed
                    isUnlocked[key] = if (index == 0) true else prevClaimed
                    updateRiveButtonStates(
                        topicViews[index],
                        prog >= 100f,
                        isUnlocked[key] ?: false,
                        claimed
                    )
                    prevClaimed = prog >= 100f && claimed
                }

                currentTargetIndex = findTargetTopicIndex(progressMap, topicKeys, claimedMap)

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

    private fun showBottomControls(
        buttonText: String,
        topicKey: String?,
        rewardKey: String?,
        isLocked: Boolean
    ) {
        pendingTopic = if (isLocked) null else topicKey
        pendingRewardKey = if (isLocked) null else rewardKey

        if (rewardKey != null) {
            val idx = topicKeys.indexOf(rewardKey)
            startLessonLabel.text = getString(R.string.reward_label, topicNames[idx])
        } else if (topicKey != null) {
            val idx = topicKeys.indexOf(topicKey)
            startLessonLabel.text = getString(R.string.topic_label, idx + 1, topicNames[idx])
        }

        val startContainerBg =
            view?.findViewById<android.widget.LinearLayout>(R.id.start_container_background)

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
            // Localize START/REVIEW based on buttonText parameter
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
        val intent = Intent(requireContext(), Math11GradeQuestionActivity::class.java).apply {
            putExtra("TOPIC_KEY", pendingTopic)
        }
        startActivity(intent)
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        scrollView.postDelayed({ if (isAdded) showLoadingState(false) }, 1000)
    }

    private fun handleRewardClaimed(view: RiveAnimationView, topicKey: String) {
        val user = auth.currentUser ?: return
        showLoadingState(true)
        db.collection("users").document(user.uid).update("claimedRewardsMath11.$topicKey", true)
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

    private fun findTargetTopicIndex(
        progressMap: Map<*, *>,
        keys: List<String>,
        claimedMap: Map<*, *>
    ): Int {
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

    private fun setupRiveTopics() {
        setupSingleTopic(topic1, R.raw.progress_path_odd, 1f, "DERIVATIVES")
    }

    private fun setupSingleTopic(
        rive: RiveAnimationView,
        res: Int,
        level: Float,
        topicKey: String
    ) {
        rive.setRiveResource(res, stateMachineName = "State Machine 1", autoplay = true)
        rive.registerListener(object : RiveFileController.Listener {
            override fun notifyPlay(animation: PlayableInstance) {
                rive.controller.file?.getViewModelByName("ViewModel1")?.let { vm ->
                    val vmi = vm.createDefaultInstance()
                    rive.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi
                    vmi.getNumberProperty("level")?.value = level

                    // Localize text inside Rive
                    val isFinished = (currentTopicProgress[topicKey] ?: 0f) >= 100f
                    val startText = if (isFinished) getString(R.string.review_text) else getString(R.string.start_text)
                    try {
                        vmi.getStringProperty("startText")?.value = startText
                        vmi.getStringProperty("rewardText")?.value = getString(R.string.reward_text_rive)
                    } catch (e: Exception) {}
                }
            }

            override fun notifyStateChanged(stateMachineName: String, stateName: String) {
                activity?.runOnUiThread {
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

    private fun updateRiveButtonStates(
        view: RiveAnimationView,
        isFinished: Boolean,
        unlocked: Boolean,
        claimed: Boolean
    ) {
        view.setBooleanState("State Machine 1", "lessonAvailable", unlocked && !isFinished)
        view.setBooleanState(
            "State Machine 1",
            "rewardAvailable",
            unlocked && isFinished && !claimed
        )
        view.setBooleanState("State Machine 1", "reward", claimed)
    }

    private fun animateRiveProgress(
        view: RiveAnimationView,
        start: Float,
        end: Float,
        onUpdate: (Float) -> Unit
    ) {
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
        animate().alpha(1f).translationY(0f).setDuration(300)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun View.fadeOutAndSlideDown() {
        animate().alpha(0f).translationY(100f).setDuration(250)
            .withEndAction { visibility = View.GONE }.start()
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
        topicProgressListener?.remove()
    }
}