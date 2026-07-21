package com.tetragon.app.questions.questionMathFifthGrade

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityMath5GradeQuestionBinding
import com.tetragon.app.questions.FiveCorrectAnswerFragment
import com.tetragon.app.questions.MathComplexity
import com.tetragon.app.questions.SubjectConstants
import com.tetragon.app.questions.XpGainedActivity
import com.tetragon.app.questions.questionMathFifthGrade.firstTopic.easy.*
import com.tetragon.app.questions.questionMathFifthGrade.firstTopic.hard.*
import com.tetragon.app.questions.questionMathFifthGrade.firstTopic.medium.*
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Math5GradeQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivityMath5GradeQuestionBinding
    private lateinit var selectedTopic: MathGrade5Topic
    private var lastQuestionType: MathGrade5Type? = null

    var totalXp: Int = 0
    var isResultCurrentlyVisible: Boolean = false
    var isCorrectAnswerShowing: Boolean = false
    private var correctAnswersCount = 0
    private var currentComplexity: MathComplexity = MathComplexity.EASY
    private var previousComplexity: MathComplexity? = null
    private var consecutiveCorrectAtLevel = 0
    private var isOnSecondChance = false

    private var milestoneMediaPlayer: MediaPlayer? = null

    // Queue tracking system for the Round-Robin logic
    private var remainingTypesInRound: MutableList<MathGrade5Type> = mutableListOf()
    private var lastTrackedComplexity: MathComplexity? = null

    private val viewModel: ConnectivityViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityMath5GradeQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateComplexityUi(currentComplexity)
        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

        val topicName = intent.getStringExtra("TOPIC_KEY")
            ?: throw IllegalArgumentException("TOPIC_KEY missing")

        selectedTopic = MathGrade5Topic.valueOf(topicName)

        observeConnectivity()

        if (savedInstanceState == null) {
            showRandomQuestion()
        }
    }

    fun handleCorrectAnswer() {
        correctAnswersCount++
        consecutiveCorrectAtLevel++
        isOnSecondChance = false

        if (consecutiveCorrectAtLevel >= 3) {
            val oldComplexity = currentComplexity

            currentComplexity = when (currentComplexity) {
                MathComplexity.EASY -> MathComplexity.MEDIUM
                MathComplexity.MEDIUM -> MathComplexity.HARD
                MathComplexity.HARD -> MathComplexity.HARD
            }

            if ((oldComplexity == MathComplexity.EASY && currentComplexity == MathComplexity.MEDIUM) ||
                (oldComplexity == MathComplexity.MEDIUM && currentComplexity == MathComplexity.HARD)) {

                // 1. Immediately animate complexity container drop with clean scale out/in
                binding.complexityContainer.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(150)
                    .withEndAction {
                        updateComplexityUi(currentComplexity)
                        previousComplexity = currentComplexity // Prevent duplicate showRandomQuestion alpha jump

                        binding.complexityContainer.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(250)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }.start()

                // 2. Play level up milestone sequence
                playLevelUpAnimation()
            }

            consecutiveCorrectAtLevel = 0
        }
    }

    fun handleIncorrectAnswer() {
        consecutiveCorrectAtLevel = 0
        if (!isOnSecondChance) {
            isOnSecondChance = true
        } else {
            currentComplexity = when (currentComplexity) {
                MathComplexity.HARD -> MathComplexity.MEDIUM
                MathComplexity.MEDIUM -> MathComplexity.EASY
                MathComplexity.EASY -> MathComplexity.EASY
            }
            isOnSecondChance = false
        }
    }

    fun checkAndTriggerMilestone(): Boolean {
        if (correctAnswersCount >= 5) {
            correctAnswersCount = 0
            triggerMilestoneSequence()
            return true
        }
        return false
    }

    private fun triggerMilestoneSequence() {
        isResultCurrentlyVisible = false
        binding.stateContainer.visibility = View.GONE
        binding.correctMrSquare.visibility = View.GONE
        binding.btnBackground.visibility = View.GONE
        previousComplexity = null

        binding.complexityContainer.animate().alpha(0f).setDuration(300).withEndAction {
            binding.complexityContainer.visibility = View.GONE
        }.start()

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(R.id.questionFragmentContainer, FiveCorrectAnswerFragment())
            .commit()

        lifecycleScope.launch {
            delay(2500)
            binding.btnBackground.visibility = View.VISIBLE
            showRandomQuestion()
        }
    }

    fun playSuccessAnimation() {
        binding.correctMrSquare.apply {
            val randomResource =
                if ((0..1).random() == 0) R.raw.mr_square_correct else R.raw.mr_square_correct_2
            setRiveResource(randomResource)
            visibility = View.VISIBLE
            fireState("State Machine 1", "play")
        }
    }

    fun hideSuccessAnimation() {
        binding.correctMrSquare.apply {
            fireState("State Machine 1", "finish")
            stop()
            visibility = View.INVISIBLE
        }
    }

    private fun playLevelUpAnimation() {
        triggerHapticFeedback()

        try {
            milestoneMediaPlayer?.stop()
            milestoneMediaPlayer?.release()

            milestoneMediaPlayer = MediaPlayer.create(this, R.raw.energy).apply {
                setOnCompletionListener {
                    it.release()
                    if (milestoneMediaPlayer == it) {
                        milestoneMediaPlayer = null
                    }
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding.levelUpAnimation.visibility = View.VISIBLE
        binding.levelUpAnimationRiveView.fireState("State Machine 1", "play")

        lifecycleScope.launch {
            delay(3000)
            hideLevelUpAnimation()
        }
    }

    fun hideLevelUpAnimation() {
        binding.levelUpAnimationRiveView.stop()
        binding.levelUpAnimation.visibility = View.INVISIBLE
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(300)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getTypesForTopic(topic: MathGrade5Topic): List<MathGrade5Type> {
        return when (topic) {
            MathGrade5Topic.NATURAL_NUMBERS -> listOf(
                MathGrade5Type.ADDITION_NATURAL_NUMBERS,
                MathGrade5Type.WRITE_WITH_DIGITS,
                MathGrade5Type.NUMBER_BEFORE_AFTER,
                MathGrade5Type.IS_NATURAL,
                MathGrade5Type.NUMBER_OF_NATURAL_NUMBERS,
                MathGrade5Type.AVERAGE_OF_NATURAL_NUMBERS,
                MathGrade5Type.SUM_OF_EVEN_NUMBERS,
                MathGrade5Type.SHOW_MATCH_SEGMENT,
                MathGrade5Type.SUM_OF_FIRST_NATURAL_NUMBERS,
                MathGrade5Type.FIND_N_IF_SUM_OF_NAT_NUM,
                MathGrade5Type.SUM_OF_FIRST_NATURAL_NUMBERS_DIVISIBLE,
                MathGrade5Type.FIND_SUM_OF_SQUARES_IF_N,
            )
        }
    }

    fun showRandomQuestion() {
        isResultCurrentlyVisible = false
        hideSuccessAnimation()
        binding.correctMrSquare.visibility = View.INVISIBLE

        if (currentComplexity != previousComplexity) {
            updateComplexityUi(currentComplexity)
            binding.complexityContainer.apply {
                visibility = View.VISIBLE
                alpha = 0f
                animate().alpha(1f).setDuration(400).start()
            }
            previousComplexity = currentComplexity
        } else {
            binding.complexityContainer.visibility = View.VISIBLE
            binding.complexityContainer.alpha = 1f
        }

        val allTypes = getTypesForTopic(selectedTopic)
        val targetedLevelPool = allTypes.filter { it.complexity == currentComplexity }
        val finalWorkingPool = if (targetedLevelPool.isNotEmpty()) targetedLevelPool else allTypes

        if (currentComplexity != lastTrackedComplexity || remainingTypesInRound.isEmpty()) {
            lastTrackedComplexity = currentComplexity

            remainingTypesInRound = finalWorkingPool.toMutableList()
            remainingTypesInRound.shuffle()

            if (remainingTypesInRound.size > 1 && remainingTypesInRound.first() == lastQuestionType) {
                val duplicateElement = remainingTypesInRound.removeAt(0)
                remainingTypesInRound.add(duplicateElement)
            }
        }

        val nextType = remainingTypesInRound.removeAt(0)
        lastQuestionType = nextType

        val fragment = when (nextType) {
            MathGrade5Type.ADDITION_NATURAL_NUMBERS -> UiAdditionNaturalNumbersFragment()
            MathGrade5Type.WRITE_WITH_DIGITS -> UiWriteWithDigitsFragment()
            MathGrade5Type.NUMBER_BEFORE_AFTER -> UiWhatIsAfterOrBeforeFragment()
            MathGrade5Type.IS_NATURAL -> UiIsNaturalFragment()
            MathGrade5Type.NUMBER_OF_NATURAL_NUMBERS -> UiNumberOfNaturalNumbersFragment()
            MathGrade5Type.AVERAGE_OF_NATURAL_NUMBERS -> UiFindAverageOfNaturalNumbersFragment()
            MathGrade5Type.SUM_OF_EVEN_NUMBERS -> UiSumOfAllEvenNumbersFragment()
            MathGrade5Type.SHOW_MATCH_SEGMENT -> UiSelectMatchOnSegmentFragment()
            MathGrade5Type.SUM_OF_FIRST_NATURAL_NUMBERS -> UiFindSumOfFirstSomeNaturalFragment()
            MathGrade5Type.FIND_N_IF_SUM_OF_NAT_NUM -> UiFindNifSumOfNaturalNumbersFragment()
            MathGrade5Type.SUM_OF_FIRST_NATURAL_NUMBERS_DIVISIBLE -> UiSumOfNaturalNumbersDivisibleFragment()
            MathGrade5Type.FIND_SUM_OF_SQUARES_IF_N -> UiFindSumOfSquaresIfNFragment()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
            .replace(R.id.questionFragmentContainer, fragment)
            .commit()
    }

    fun incrementProgress(): Boolean {
        val progressBar = binding.progressBar
        val increment = progressBar.max / 10
        val newProgress = (progressBar.progress + increment).coerceAtMost(progressBar.max)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            progressBar.setProgress(newProgress, true)
        } else {
            ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, newProgress).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                start()
            }
        }
        return newProgress >= progressBar.max
    }

    fun navigateToXpGained() {
        val intent = Intent(this, XpGainedActivity::class.java).apply {
            putExtra(SubjectConstants.EXTRA_XP, totalXp)
            putExtra(SubjectConstants.EXTRA_TOPIC, selectedTopic.name)
            putExtra(SubjectConstants.EXTRA_GRADE, 5)
            putExtra(SubjectConstants.EXTRA_SUBJECT, SubjectConstants.SUBJECT_MATH)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    private fun showQuitBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_quit, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.titleText).text = getString(R.string.quit_title)
        view.findViewById<TextView>(R.id.messageText).text = getString(R.string.quit_message)
        view.findViewById<Button>(R.id.noButton).apply {
            text = getString(R.string.continue_btn)
            setOnClickListener { dialog.dismiss() }
        }
        view.findViewById<Button>(R.id.finishButton).apply {
            text = getString(R.string.exit_btn)
            setOnClickListener { finish(); dialog.dismiss() }
        }
        dialog.show()
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    val isVisible = isConnected
                    binding.internetConnection.visibility = if (isVisible) View.GONE else View.VISIBLE
                    binding.offlineContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
                    binding.topBarContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
                    binding.questionFragmentContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
                    binding.btnBackground.visibility = if (isVisible) View.VISIBLE else View.GONE
                    binding.complexityContainer.visibility = if (isVisible) View.VISIBLE else View.GONE

                    if (isVisible && isResultCurrentlyVisible) {
                        binding.stateContainer.visibility = View.VISIBLE
                        binding.correctMrSquare.visibility = if (isCorrectAnswerShowing) View.VISIBLE else View.INVISIBLE
                    } else {
                        binding.stateContainer.visibility = View.INVISIBLE
                        binding.correctMrSquare.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    private fun updateComplexityUi(complexity: MathComplexity) {
        val (iconRes, textRes, colorRes) = when (complexity) {
            MathComplexity.EASY -> Triple(R.drawable.easy, R.string.complexity_easy, R.color.green_1)
            MathComplexity.MEDIUM -> Triple(R.drawable.medium, R.string.complexity_medium, R.color.orange_1)
            MathComplexity.HARD -> Triple(R.drawable.hard, R.string.complexity_hard, R.color.red_1)
        }
        binding.complexityIcon.setImageResource(iconRes)
        binding.complexityText.setText(textRes)
        binding.complexityText.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    override fun onDestroy() {
        super.onDestroy()
        milestoneMediaPlayer?.release()
        milestoneMediaPlayer = null
    }
}