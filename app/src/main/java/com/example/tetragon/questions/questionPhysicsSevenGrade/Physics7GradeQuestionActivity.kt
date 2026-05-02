package com.example.tetragon.questions.questionPhysicsSevenGrade

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
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
import com.example.tetragon.databinding.ActivityQuestionQctivityBinding
import com.example.tetragon.questions.FiveCorrectAnswerFragment
import com.example.tetragon.questions.SubjectConstants
import com.example.tetragon.questions.XpGainedActivity
import com.example.tetragon.questions.questionPhysicsSevenGrade.firstTopicLength.UiLengthFragment
import com.example.tetragon.questions.questionPhysicsSevenGrade.firstTopicLength.UiLiteresFragment
import com.example.tetragon.questions.questionPhysicsSevenGrade.firstTopicLength.UiMassFragment
import com.example.tetragon.questions.questionPhysicsSevenGrade.firstTopicLength.UiTemperatureFragment
import com.example.tetragon.questions.questionPhysicsSevenGrade.secondTopicDensity.UiDensityFragment
import com.example.tetragon.questions.questionPhysicsSevenGrade.secondTopicDensity.UiFindMassFragment
import com.example.tetragon.questions.questionPhysicsSevenGrade.thirdTopic.UiSimpleMachinesFragment
import com.example.tetragon.questions.questionPhysicsSevenGrade.thirdTopic.UiWorkFragment
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class Physics7GradeQuestionActivity : BaseActivity() {

    private lateinit var binding: ActivityQuestionQctivityBinding
    private lateinit var selectedTopic: PhysicsGrade7Topic

    private var lastType: PhysicsGrade7Type? = null

    var totalXp: Int = 0
    private var correctAnswersCount = 0

    var isResultCurrentlyVisible: Boolean = false
    var isCorrectAnswerShowing: Boolean = false

    private val viewModel: ConnectivityViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConnectivityViewModel(
                    AndroidConnectivityObserver(applicationContext)
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Rive.init(this)

        binding = ActivityQuestionQctivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

            window.navigationBarColor =
                ContextCompat.getColor(this, R.color.white)
        }

        binding.exitBtn.setOnClickListener { showQuitBottomSheet() }

        onBackPressedDispatcher.addCallback(this) {
            showQuitBottomSheet()
        }

        val topicName = intent.getStringExtra("TOPIC_KEY")
            ?: throw IllegalArgumentException("TOPIC_KEY missing")

        selectedTopic = PhysicsGrade7Topic.valueOf(topicName)

        observeConnectivity()

        if (savedInstanceState == null) {
            showRandomQuestion()
        }
    }

    // ✅ CORRECT ANSWER HANDLING
    fun handleCorrectAnswer() {
        correctAnswersCount++
    }

    fun checkAndTriggerMilestone(): Boolean {
        if (correctAnswersCount >= 5) {
            correctAnswersCount = 0
            triggerMilestone()
            return true
        }
        return false
    }

    private fun triggerMilestone() {

        isResultCurrentlyVisible = false

        binding.stateContainer.visibility = View.GONE
        binding.correctMrSquare.visibility = View.GONE
        binding.btnBackground.visibility = View.GONE

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
            .replace(R.id.questionFragmentContainer, FiveCorrectAnswerFragment())
            .commit()

        lifecycleScope.launch {
            kotlinx.coroutines.delay(2500)

            binding.btnBackground.visibility = View.VISIBLE
            showRandomQuestion()
        }
    }

    fun getCorrectAnswersCount(): Int = correctAnswersCount

    fun playSuccessAnimation() {
        binding.correctMrSquare.apply {
            // 1. Randomly decide which animation file to load
            val randomResource = if ((0..1).random() == 0) {
                R.raw.mr_square_correct
            } else {
                R.raw.mr_square_correct_2
            }

            // 2. Assign the chosen resource to the RiveAnimationView
            setRiveResource(randomResource)

            // 3. Make it visible and play the state machine
            visibility = View.VISIBLE
            fireState("State Machine 1", "play")
        }
    }

    fun hideSuccessAnimation() {
        binding.correctMrSquare.apply {
            // ✅ Trigger the exit transition in your State Machine
            fireState("State Machine 1", "finish")

            // Optional: If you want to be 100% sure it stops drawing
            stop()

            visibility = View.INVISIBLE
        }
    }

    // ✅ TYPES PER TOPIC
    private fun getTypesForTopic(topic: PhysicsGrade7Topic): List<PhysicsGrade7Type> {
        return when (topic) {
            PhysicsGrade7Topic.SI_UNITS -> listOf(
                PhysicsGrade7Type.LENGTH,
                PhysicsGrade7Type.TEMPERATURE,
                PhysicsGrade7Type.KETTLE_LITRES,
                PhysicsGrade7Type.MASS
            )

            PhysicsGrade7Topic.DENSITY -> listOf(
                PhysicsGrade7Type.DENSITY,
                PhysicsGrade7Type.DENSITY_MASS
            )

            PhysicsGrade7Topic.SIMPLE_MACHINES -> listOf(
                PhysicsGrade7Type.MECHANICAL_ADVANTAGE,
                PhysicsGrade7Type.WORK
            )
        }
    }

    // ✅ MAIN QUESTION LOADER
    fun showRandomQuestion() {

        isResultCurrentlyVisible = false
        hideSuccessAnimation()
        binding.correctMrSquare.visibility = View.INVISIBLE

        val available = getTypesForTopic(selectedTopic).toMutableList()
        lastType?.let { available.remove(it) }

        val nextType = available.random()
        lastType = nextType

        val fragment = when (nextType) {

            PhysicsGrade7Type.LENGTH -> UiLengthFragment()
            PhysicsGrade7Type.TEMPERATURE -> UiTemperatureFragment()
            PhysicsGrade7Type.KETTLE_LITRES -> UiLiteresFragment()
            PhysicsGrade7Type.MASS -> UiMassFragment()

            PhysicsGrade7Type.DENSITY -> UiDensityFragment()
            PhysicsGrade7Type.DENSITY_MASS -> UiFindMassFragment()

            PhysicsGrade7Type.MECHANICAL_ADVANTAGE -> UiSimpleMachinesFragment()
            PhysicsGrade7Type.WORK -> UiWorkFragment()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.questionFragmentContainer, fragment)
            .commit()
    }

    // ✅ PROGRESS BAR
    fun incrementProgress(): Boolean {

        val progressBar = binding.progressBar
        val increment = progressBar.max / 10

        val newProgress =
            (progressBar.progress + increment).coerceAtMost(progressBar.max)

        // ✅ TRUE smooth animation (native)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            progressBar.setProgress(newProgress, true)
        } else {
            // fallback for old devices
            val animator = android.animation.ObjectAnimator.ofInt(
                progressBar,
                "progress",
                progressBar.progress,
                newProgress
            )
            animator.duration = 500
            animator.interpolator =
                android.view.animation.DecelerateInterpolator()
            animator.start()
        }

        return newProgress >= progressBar.max
    }

    // ✅ FINISH
    fun navigateToXpGained() {
        val intent = Intent(this, XpGainedActivity::class.java).apply {
            putExtra(SubjectConstants.EXTRA_XP, totalXp)
            putExtra(SubjectConstants.EXTRA_TOPIC, selectedTopic.name)
            putExtra(SubjectConstants.EXTRA_GRADE, 7) // Grade 7
            putExtra(SubjectConstants.EXTRA_SUBJECT, SubjectConstants.SUBJECT_PHYSICS)
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        finish()
    }

    // ✅ EXIT DIALOG
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

    // ✅ INTERNET
    private fun observeConnectivity() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                viewModel.isConnected.collect { isConnected ->

                    if (isConnected) {

                        binding.internetConnection.visibility = View.GONE
                        binding.offlineContainer.visibility = View.GONE
                        binding.topBarContainer.visibility = View.VISIBLE
                        binding.questionFragmentContainer.visibility = View.VISIBLE
                        binding.btnBackground.visibility = View.VISIBLE

                        if (isResultCurrentlyVisible) {

                            binding.stateContainer.visibility = View.VISIBLE

                            binding.correctMrSquare.visibility =
                                if (isCorrectAnswerShowing)
                                    View.VISIBLE
                                else
                                    View.INVISIBLE
                        } else {

                            binding.stateContainer.visibility = View.INVISIBLE
                            binding.correctMrSquare.visibility = View.INVISIBLE
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
        }
    }
}