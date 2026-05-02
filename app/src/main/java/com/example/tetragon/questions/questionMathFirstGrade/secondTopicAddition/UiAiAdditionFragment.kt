package com.example.tetragon.questions.questionMathFirstGrade.secondTopicAddition

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.questions.questionMathFirstGrade.Math1GradeQuestionActivity
import com.example.tetragon.questions.questionMathFirstGrade.MathGrade1Type
import com.example.tetragon.utils.DrawingView
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.random.Random

class UiAiAdditionFragment : Fragment(R.layout.fragment_ui_ai_addition) {

    private lateinit var drawingView: DrawingView
    private lateinit var drawingOverlay: View
    private lateinit var tvQuestion: TextView
    private var module: Module? = null

    private lateinit var boxIndicator: ImageView
    private lateinit var tvAnswerOverlay: TextView
    private lateinit var undoBtn: TextView

    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView

    private var num1 = 0
    private var num2 = 0
    private var correctAnswer = 0

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true

    private var mediaPlayer: MediaPlayer? = null
    private var pencilMediaPlayer: MediaPlayer? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as Math1GradeQuestionActivity

        tvQuestion = view.findViewById(R.id.tvQuestion)
        drawingView = view.findViewById(R.id.drawingView)
        drawingOverlay = view.findViewById(R.id.drawingOverlay)
        boxIndicator = view.findViewById(R.id.boxIndicator)
        tvAnswerOverlay = view.findViewById(R.id.tvAnswerOverlay)
        undoBtn = view.findViewById(R.id.undoBtn)

        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)

        drawingView.clipToOutline = true

        pencilMediaPlayer = MediaPlayer.create(requireContext(), R.raw.pencil).apply {
            isLooping = true
        }

        try {
            val modelPath = assetFilePath("digit_detection.ptl")
            module = LiteModuleLoader.load(modelPath)
        } catch (e: IOException) {
            Log.e("PyTorch", "Error loading model", e)
        }

        drawingView.onDrawingChangedListener = {
            if (!isAnswerChecked) {
                enableCheckButton()
            }
        }

        drawingView.setOnTouchListener { _, event ->
            if (!drawingView.isEnabled) return@setOnTouchListener false

            drawingView.onTouchEvent(event)
            if (!isAnswerChecked) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> startPencilSound()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopPencilSound()
                }
            }
            true
        }

        undoBtn.setOnClickListener {
            if (drawingView.isEnabled) {
                drawingView.clearCanvas()
                tvAnswerOverlay.visibility = View.GONE
                boxIndicator.setImageResource(R.drawable.answer_blue_box)
                if (!isAnswerChecked) {
                    disableCheckButton()
                }
            }
        }

        generateProblem()
        setupCheckButton()
    }

    private fun startPencilSound() {
        if (pencilMediaPlayer?.isPlaying == false) {
            pencilMediaPlayer?.start()
        }
    }

    private fun stopPencilSound() {
        if (pencilMediaPlayer?.isPlaying == true) {
            pencilMediaPlayer?.pause()
            pencilMediaPlayer?.seekTo(0)
        }
    }

    private fun generateProblem() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.stateContainer).visibility = View.GONE

        num1 = Random.nextInt(1, 6)
        num2 = Random.nextInt(0, 4)
        correctAnswer = num1 + num2
        tvQuestion.text = "$num1 + $num2 ="

        drawingView.clearCanvas()
        drawingView.isEnabled = true
        drawingOverlay.visibility = View.GONE

        tvAnswerOverlay.visibility = View.GONE
        boxIndicator.setImageResource(R.drawable.answer_blue_box)

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        seeBtn.visibility = View.GONE

        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun checkAnswer(stateContainer: FrameLayout, circleState: ImageView) {
        stopPencilSound()
        val predictedDigit = getAiPrediction()

        if (predictedDigit == -1) {
            // Using localized string for the toast
            Toast.makeText(context, getString(R.string.toast_clear_number), Toast.LENGTH_SHORT).show()
            return
        }

        drawingView.isEnabled = false
        drawingOverlay.visibility = View.VISIBLE

        tvAnswerOverlay.text = predictedDigit.toString()
        tvAnswerOverlay.visibility = View.VISIBLE

        isAnswerChecked = true
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = true
        stateContainer.visibility = View.VISIBLE

        if (predictedDigit == correctAnswer) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()
            boxIndicator.setImageResource(R.drawable.answer_correct_box)

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade1Type.BASIC_AI_ADDITION.xp
            }
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState(stateContainer, circleState)
        } else {
            playSound(R.raw.wrong)
            boxIndicator.setImageResource(R.drawable.answer_incorrect_box)
            isIncorrectAttempt = true
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState(stateContainer, circleState)
            setupSeeSolution(stateContainer, circleState)
        }
        isFirstAttempt = false
    }

    private fun setupCheckButton() {
        checkBtn.setOnClickListener {
            val activity = requireActivity() as Math1GradeQuestionActivity
            val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)
            val circleState = activity.findViewById<ImageView>(R.id.circleState)

            if (!isAnswerChecked) {
                checkAnswer(stateContainer, circleState)
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    if (checkBtn.text == getString(R.string.btn_finish)) {
                        activity.navigateToXpGained()
                    } else {
                        val isMilestoneActive = activity.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            resetUIForNext()
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
    }

    private fun showCorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)
        answer.text = getString(R.string.label_answer, correctAnswer.toString())
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
        enableCheckButton()
    }

    private fun showIncorrectState(stateContainer: FrameLayout, circleState: ImageView) {
        drawingView.isEnabled = false
        drawingOverlay.visibility = View.VISIBLE

        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
        enableCheckButton()
    }

    private fun setupSeeSolution(stateContainer: FrameLayout, circleState: ImageView) {
        seeEnabledButton.setOnClickListener {
            seeBtn.visibility = View.GONE
            drawingView.clearCanvas()
            drawingView.isEnabled = false
            drawingOverlay.visibility = View.VISIBLE

            tvAnswerOverlay.text = correctAnswer.toString()
            tvAnswerOverlay.visibility = View.VISIBLE

            stateAnswer.text = getString(R.string.state_solution)
            answer.text = getString(R.string.label_answer, correctAnswer.toString())
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)
            boxIndicator.setImageResource(R.drawable.answer_solution_box)
            circleState.setImageResource(R.drawable.solution_lamp_icon)

            isAnswerChecked = true
            isIncorrectAttempt = false

            applyButtonColors(R.color.black_3, R.color.black_2, R.color.gray_2)
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            enableCheckButton()
        }
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        val stateContainer = activity.findViewById<FrameLayout>(R.id.stateContainer)

        isAnswerChecked = false
        isIncorrectAttempt = false

        drawingView.clearCanvas()
        drawingView.isEnabled = true
        drawingOverlay.visibility = View.GONE

        tvAnswerOverlay.visibility = View.GONE
        boxIndicator.setImageResource(R.drawable.answer_blue_box)

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.visibility = View.VISIBLE
        answer.text = ""

        checkBtn.text = getString(R.string.btn_check)
        disableCheckButton()
        setupInitialButtonState()
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()

        requireActivity().findViewById<FrameLayout>(R.id.stateContainer).visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)

        setupInitialButtonState()
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        checkBtnBack.visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math1GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
        checkBtnBack.visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
        checkBtn.isEnabled = false
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun getAiPrediction(): Int {
        val originalBitmap = drawingView.getBitmap() ?: return -1
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 28, 28, true)
        val outBuffer = Tensor.allocateFloatBuffer(28 * 28)
        val pixels = IntArray(28 * 28)
        scaledBitmap.getPixels(pixels, 0, 28, 0, 0, 28, 28)

        var inkPixelCount = 0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xff
            val invertedNormalized = (255 - r) / 255.0f
            if (invertedNormalized > 0.1f) inkPixelCount++
            outBuffer.put(invertedNormalized)
        }

        if (inkPixelCount < 6) return -1

        val module = module ?: return -1
        val inputTensor = Tensor.fromBlob(outBuffer, longArrayOf(1, 1, 28, 28))
        val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray

        var maxScore = -Float.MAX_VALUE
        var maxIdx = -1
        for (i in scores.indices) {
            if (scores[i] > maxScore) {
                maxScore = scores[i]
                maxIdx = i
            }
        }
        return maxIdx
    }

    @Throws(IOException::class)
    private fun assetFilePath(assetName: String): String {
        val file = File(requireContext().filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath
        requireContext().assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
            return file.absolutePath
        }
    }

    private fun playSound(soundResId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        pencilMediaPlayer?.release()
        mediaPlayer = null
        pencilMediaPlayer = null
    }
}