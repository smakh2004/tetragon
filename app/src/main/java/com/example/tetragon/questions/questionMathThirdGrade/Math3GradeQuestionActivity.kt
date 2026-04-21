package com.example.tetragon.questions.questionMathThirdGrade

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.R
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.databinding.ActivityMath3GradeQuestionBinding // Ensure this matches your XML name
import com.example.tetragon.questions.FiveCorrectAnswerFragment
import com.example.tetragon.questions.SubjectConstants
import com.example.tetragon.questions.XpGainedActivity
import com.example.tetragon.questions.questionMathSecondGrade.MathGrade2Topic
import com.example.tetragon.questions.questionMathThirdGrade.firstTopic.UiComplexMultiplicationFragment
import com.example.tetragon.questions.questionMathThirdGrade.firstTopic.UiComplexMultiplicationInteractiveFragment
import com.example.tetragon.questions.questionMathThirdGrade.fourthTopic.UiFindAreaFragment
import com.example.tetragon.questions.questionMathThirdGrade.fourthTopic.UiFindPerimeterFragment
import com.example.tetragon.questions.questionMathThirdGrade.secondTopic.UiComplexDivisionFragment
import com.example.tetragon.questions.questionMathThirdGrade.secondTopic.UiComplexDivisionInteractiveFragment
import com.example.tetragon.questions.questionMathThirdGrade.thirdTopic.UiFractionPizzaSixPiecesFragment
import com.example.tetragon.questions.questionMathThirdGrade.thirdTopic.UiFractionProblemFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Math3GradeQuestionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMath3GradeQuestionBinding
    private lateinit var selectedTopic: MathGrade3Topic
    private var lastQuestionType: MathGrade3Type? = null

    var totalXp: Int = 0
    var isResultCurrentlyVisible: Boolean = false
    var isCorrectAnswerShowing: Boolean = false
    private var correctAnswersCount = 0

    private val viewModel: ConnectivityViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityMath3GradeQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Styling for Android Engineering best practices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

        val topicName = intent.getStringExtra("TOPIC_KEY")
            ?: throw IllegalArgumentException("TOPIC_KEY missing")

        selectedTopic = MathGrade3Topic.valueOf(topicName)

        observeConnectivity()

        if (savedInstanceState == null) {
            showRandomQuestion()
        }
    }

    fun handleCorrectAnswer() {
        correctAnswersCount++
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
            val randomResource = if ((0..1).random() == 0) R.raw.mr_square_correct else R.raw.mr_square_correct_2
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

    private fun getTypesForTopic(topic: MathGrade3Topic): List<MathGrade3Type> {
        return when (topic) {
            MathGrade3Topic.COMPLEX_MULTIPLICATION -> listOf(
                MathGrade3Type.MULTIPLICATION,
                MathGrade3Type.MULTIPLICATION_INTERACTIVE,
            )

            MathGrade3Topic.COMPLEX_DIVISION -> listOf(
                MathGrade3Type.DIVISION,
                MathGrade3Type.DIVISION_INTERACTIVE,
            )

            MathGrade3Topic.FRACTIONS -> listOf(
                MathGrade3Type.PIZZA_6,
                MathGrade3Type.FRACTION,
            )

            MathGrade3Topic.PERIMETER_AREA -> listOf(
                MathGrade3Type.PERIMETER,
                MathGrade3Type.AREA,
            )
        }
    }

    fun showRandomQuestion() {
        isResultCurrentlyVisible = false
        hideSuccessAnimation()
        binding.correctMrSquare.visibility = View.INVISIBLE

        val allTypes = getTypesForTopic(selectedTopic)
        val filteredTypes = allTypes.filter { it != lastQuestionType }
        val nextType = if (filteredTypes.isNotEmpty()) filteredTypes.random() else allTypes.random()

        lastQuestionType = nextType

        val fragment = when (nextType) {
            MathGrade3Type.MULTIPLICATION -> UiComplexMultiplicationFragment()
            MathGrade3Type.MULTIPLICATION_INTERACTIVE -> UiComplexMultiplicationInteractiveFragment()

            MathGrade3Type.DIVISION -> UiComplexDivisionFragment()
            MathGrade3Type.DIVISION_INTERACTIVE -> UiComplexDivisionInteractiveFragment()

            MathGrade3Type.PIZZA_6 -> UiFractionPizzaSixPiecesFragment()
            MathGrade3Type.FRACTION -> UiFractionProblemFragment()

            MathGrade3Type.PERIMETER -> UiFindPerimeterFragment()
            MathGrade3Type.AREA -> UiFindAreaFragment()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
            putExtra(SubjectConstants.EXTRA_GRADE, 3)
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

        view.findViewById<TextView>(R.id.titleText).text = "Are you sure?"
        view.findViewById<TextView>(R.id.messageText).text = "If you exit, you will lose all progress for this lesson."

        view.findViewById<Button>(R.id.noButton).apply {
            text = "CONTINUE"
            setOnClickListener { dialog.dismiss() }
        }

        view.findViewById<Button>(R.id.finishButton).apply {
            text = "EXIT"
            setOnClickListener {
                finish()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    updateUIForConnectivity(isConnected)
                }
            }
        }
    }

    private fun updateUIForConnectivity(isConnected: Boolean) {
        if (isConnected) {
            binding.internetConnection.visibility = View.GONE
            binding.offlineContainer.visibility = View.GONE
            binding.topBarContainer.visibility = View.VISIBLE
            binding.questionFragmentContainer.visibility = View.VISIBLE
            binding.btnBackground.visibility = View.VISIBLE
            if (isResultCurrentlyVisible) {
                binding.stateContainer.visibility = View.VISIBLE
                binding.correctMrSquare.visibility = if (isCorrectAnswerShowing) View.VISIBLE else View.INVISIBLE
            }
        } else {
            binding.internetConnection.visibility = View.VISIBLE
            binding.offlineContainer.visibility = View.VISIBLE
            binding.topBarContainer.visibility = View.GONE
            binding.questionFragmentContainer.visibility = View.GONE
            binding.btnBackground.visibility = View.GONE
            binding.stateContainer.visibility = View.GONE
            binding.correctMrSquare.visibility = View.GONE
        }
    }
}