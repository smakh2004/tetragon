package com.tetragon.app.ui.uiCashStorm

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityUiCashStormBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.soundUtils.SoundManager
import com.google.android.material.bottomsheet.BottomSheetDialog

class UiCashStormActivity : BaseActivity() {
    private lateinit var binding: ActivityUiCashStormBinding
    private lateinit var controller: ProfitStormController

    private var selectedOption = 0
    private var isNavigatingToResult = false

    private lateinit var countdownOverlay: FrameLayout
    private lateinit var countdownText: TextView
    private lateinit var afterCountdownImage: ImageView

    private lateinit var timerPlayer: MediaPlayer
    private lateinit var finishPlayer: MediaPlayer
    private lateinit var wrongPlayer: MediaPlayer
    private lateinit var correctPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUiCashStormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        initSounds()
        initController()
        initButtons()

        binding.signFlag.setOnClickListener { showQuitBottomSheet() }
        onBackPressedDispatcher.addCallback(this) { showQuitBottomSheet() }

        startCountdownOverlay(3)
    }

    private fun initViews() {
        countdownOverlay = binding.countdownOverlay
        countdownText = binding.countdownText
        afterCountdownImage = binding.afterCountdownImage
    }

    private fun initController() {
        controller = ProfitStormController(
            onProblemChanged = { problem ->
                resetSelection()
                binding.totalBalanceText.text = "$${problem.baseAmount}"

                // --- DYNAMIC CASH IMAGE LOGIC ---
                when {
                    problem.baseAmount < 100 -> binding.mainCashIcon.setImageResource(R.drawable.one_cash)
                    problem.baseAmount < 500 -> binding.mainCashIcon.setImageResource(R.drawable.two_cash)
                    else -> binding.mainCashIcon.setImageResource(R.drawable.three_cash)
                }
                // --------------------------------

                val card1Text = binding.optionCard1.getChildAt(0) as TextView
                val card2Text = binding.optionCard2.getChildAt(0) as TextView

                card1Text.text = formatSignToBlue(problem.option1Text)
                card2Text.text = formatSignToBlue(problem.option2Text)
            },
            onScoreChanged = { score ->
                binding.scoreText.text = score.toString()
                playCorrectSound()
            },
            onMistakeChanged = { count ->
                updateMistakesUI(count)
                if (count > 0) playWrongSound()
            },
            onCountdownTick = { sec -> binding.timerText.text = formatTime(sec) },
            onQuizFinished = { score -> navigateToResult(score) }
        )
    }

    private fun formatSignToBlue(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder(text)
        val color = ContextCompat.getColor(this, R.color.blue_2)
        // Identify math signs including the fraction slash and the multiplier prefix
        val targetSigns = charArrayOf('+', '-', '×', '%', '/')

        text.forEachIndexed { index, char ->
            if (char in targetSigns) {
                builder.setSpan(
                    ForegroundColorSpan(color),
                    index,
                    index + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }

    private fun initButtons() {
        binding.optionCard1.setOnClickListener { selectCard(1) }
        binding.optionCard2.setOnClickListener { selectCard(2) }
        binding.enabledButton.setOnClickListener { controller.checkAnswer(selectedOption) }
    }

    private fun startCountdownOverlay(seconds: Int) {
        countdownOverlay.visibility = View.VISIBLE
        afterCountdownImage.visibility = View.GONE
        countdownText.visibility = View.VISIBLE

        controller.startCountdown(seconds,
            tick = { sec ->
                countdownText.text = sec.toString()
                playTimerSound()
            },
            finish = {
                countdownText.visibility = View.GONE
                afterCountdownImage.visibility = View.VISIBLE
                playFinishSound()

                afterCountdownImage.postDelayed({
                    countdownOverlay.visibility = View.GONE
                    controller.generateProblem()
                    controller.startQuizTimer(180)
                }, 1000)
            }
        )
    }

    private fun selectCard(index: Int) {
        selectedOption = index
        binding.optionCard1.setBackgroundResource(R.drawable.answer_default_box)
        binding.optionCard2.setBackgroundResource(R.drawable.answer_default_box)
        val selected = if (index == 1) binding.optionCard1 else binding.optionCard2
        selected.setBackgroundResource(R.drawable.answer_blue_box)
        binding.enabledButtonFrame.visibility = View.VISIBLE
        binding.disabledButtonFrame.visibility = View.INVISIBLE
    }

    private fun resetSelection() {
        selectedOption = 0
        binding.optionCard1.setBackgroundResource(R.drawable.answer_default_box)
        binding.optionCard2.setBackgroundResource(R.drawable.answer_default_box)
        binding.enabledButtonFrame.visibility = View.INVISIBLE
        binding.disabledButtonFrame.visibility = View.VISIBLE
    }

    private fun updateMistakesUI(count: Int) {
        val indicators = listOf(binding.mistake1, binding.mistake2, binding.mistake3)
        indicators.forEachIndexed { i, img ->
            img.setImageResource(if (i < count) R.drawable.wrong_circle else R.drawable.circle_empty)
        }
    }

    private fun formatTime(seconds: Int) = String.format("%d:%02d", seconds / 60, seconds % 60)

    private fun initSounds() {
        timerPlayer = MediaPlayer.create(this, R.raw.start)
        finishPlayer = MediaPlayer.create(this, R.raw.finish)
        wrongPlayer = MediaPlayer.create(this, R.raw.wrong)
        correctPlayer = MediaPlayer.create(this, R.raw.sound_cash)
    }

    private fun playTimerSound() {
        if (SoundManager.isSoundEnabled(this)) {
            timerPlayer.seekTo(0); timerPlayer.start()
        }
    }

    private fun playFinishSound() {
        if (SoundManager.isSoundEnabled(this)) {
            finishPlayer.seekTo(0); finishPlayer.start()
        }
    }

    private fun playCorrectSound() {
        if (SoundManager.isSoundEnabled(this)) {
            correctPlayer.seekTo(0); correctPlayer.start()
        }
    }

    private fun playWrongSound() {
        if (SoundManager.isSoundEnabled(this)) {
            wrongPlayer.seekTo(0); wrongPlayer.start()
        }
    }

    private fun showQuitBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_quit, null)
        dialog.setContentView(view)
        view.findViewById<Button>(R.id.finishButton).setOnClickListener {
            controller.cancelQuizTimer()
            navigateToResult(controller.getCurrentScore())
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun navigateToResult(score: Int) {
        if (isNavigatingToResult || isFinishing) return
        isNavigatingToResult = true
        controller.cancelQuizTimer()
        val intent = Intent(this, ResultCashStormActivity::class.java).apply { putExtra("score", score) }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerPlayer.release()
        finishPlayer.release()
        wrongPlayer.release()
        correctPlayer.release()
        controller.cancelQuizTimer()
    }
}