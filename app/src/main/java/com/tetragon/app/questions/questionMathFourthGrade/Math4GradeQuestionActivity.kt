package com.tetragon.app.questions.questionMathFourthGrade

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
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityMath4GradeQuestionBinding // Ensure this XML exists or matches your naming
import com.tetragon.app.questions.FiveCorrectAnswerFragment
import com.tetragon.app.questions.SubjectConstants
import com.tetragon.app.questions.XpGainedActivity
import com.tetragon.app.questions.questionMathFourthGrade.fifthTopic.UiMixedNumbersFragment
import com.tetragon.app.questions.questionMathFourthGrade.firstTopic.UiComplexAdditionFragment
import com.tetragon.app.questions.questionMathFourthGrade.firstTopic.UiComplexSubtractionFragment
import com.tetragon.app.questions.questionMathFourthGrade.fourthTopic.UiFractionArithmeticsFragment
import com.tetragon.app.questions.questionMathFourthGrade.thirdTopic.UiColumnDivisionFragment
import com.tetragon.app.questions.questionMathFourthGrade.secondTopic.UiColumnMultiplicationFragment
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Math4GradeQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivityMath4GradeQuestionBinding
    private lateinit var selectedTopic: MathGrade4Topic
    private var lastQuestionType: MathGrade4Type? = null

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
        binding = ActivityMath4GradeQuestionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Light Navigation Bar for consistency
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

        val topicName = intent.getStringExtra("TOPIC_KEY")
            ?: throw IllegalArgumentException("TOPIC_KEY missing")

        selectedTopic = MathGrade4Topic.valueOf(topicName)

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

    private fun getTypesForTopic(topic: MathGrade4Topic): List<MathGrade4Type> {
        return when (topic) {
            MathGrade4Topic.COMPLEX_ARITHMETICS -> listOf(
                MathGrade4Type.ADDITION,
                MathGrade4Type.SUBTRACTION,
            )

            MathGrade4Topic.COLUMN_MULTIPLICATION -> listOf(
                MathGrade4Type.MULTIPLICATION
            )

            MathGrade4Topic.COLUMN_DIVISION -> listOf(
                MathGrade4Type.DIVISION,
            )

            MathGrade4Topic.FRACTION_ARITHMETICS -> listOf(
                MathGrade4Type.FRACTION,
            )

            MathGrade4Topic.MIXED_NUMBERS -> listOf(
                MathGrade4Type.MIXED_NUMBERS_ARITHMETICS
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
            MathGrade4Type.ADDITION -> UiComplexAdditionFragment()
            MathGrade4Type.SUBTRACTION -> UiComplexSubtractionFragment()

            MathGrade4Type.MULTIPLICATION -> UiColumnMultiplicationFragment()

            MathGrade4Type.DIVISION -> UiColumnDivisionFragment()

            MathGrade4Type.FRACTION -> UiFractionArithmeticsFragment()

            MathGrade4Type.MIXED_NUMBERS_ARITHMETICS -> UiMixedNumbersFragment()
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
            putExtra(SubjectConstants.EXTRA_GRADE, 4) // Fixed for Grade 4
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

        // Localized Title and Message
        view.findViewById<TextView>(R.id.titleText).text = getString(R.string.quit_title)
        view.findViewById<TextView>(R.id.messageText).text = getString(R.string.quit_message)

        // Localized "CONTINUE" button
        view.findViewById<Button>(R.id.noButton).apply {
            text = getString(R.string.continue_text)
            setOnClickListener { dialog.dismiss() }
        }

        // Localized "EXIT" button
        view.findViewById<Button>(R.id.finishButton).apply {
            text = getString(R.string.exit_btn)
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