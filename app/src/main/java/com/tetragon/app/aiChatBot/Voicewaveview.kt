package com.tetragon.app.aiChatBot

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.tetragon.app.R
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Audio-reactive bar visualizer for the chat voice mode.
 *
 * Three modes:
 *  - IDLE: bars pulse gently in a sine wave so the user knows it's alive
 *  - USER_SPEAKING: bars react to real microphone RMS, colored as the user color
 *  - BOT_SPEAKING: bars wave rhythmically (no real audio data from TTS), colored as bot color
 *
 * Feed it RMS values from your SpeechRecognizer's onRmsChanged() callback while
 * the user is speaking.
 */
class VoiceWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { IDLE, USER_SPEAKING, BOT_SPEAKING }

    private val barCount = 5
    private val barWidthDp = 6f
    private val barGapDp = 8f
    private val cornerRadiusDp = 3f
    private val minBarHeightFraction = 0.18f   // bars never fully collapse
    private val maxBarHeightFraction = 1.0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Current animated heights (0..1) and target heights (0..1)
    private val currentHeights = FloatArray(barCount) { minBarHeightFraction }
    private val targetHeights = FloatArray(barCount) { minBarHeightFraction }

    // Smoothing factor — how fast bars catch up to their target each frame
    private val smoothing = 0.25f

    private var mode: Mode = Mode.IDLE
    private var animationStartMs: Long = 0L

    // Drives continuous redraws while voice mode is on
    private val ticker: ValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            updateForCurrentMode()
            invalidate()
        }
    }

    private val rect = RectF()
    private val userColor by lazy { ContextCompat.getColor(context, R.color.blue_2) }
    private val botColor by lazy {
        // Fall back to a softer blue if a dedicated bot color isn't defined
        try {
            ContextCompat.getColor(context, R.color.blue_2)
        } catch (_: Exception) {
            0xFF4FC3F7.toInt()
        }
    }
    private val idleColor by lazy { ContextCompat.getColor(context, R.color.gray_1) }

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        animationStartMs = System.currentTimeMillis()
        if (newMode == Mode.IDLE) {
            // soften back down
            for (i in targetHeights.indices) targetHeights[i] = minBarHeightFraction
        }
        if (!ticker.isRunning) ticker.start()
        invalidate()
    }

    fun start() {
        if (!ticker.isRunning) ticker.start()
    }

    fun stop() {
        if (ticker.isRunning) ticker.cancel()
        for (i in currentHeights.indices) {
            currentHeights[i] = minBarHeightFraction
            targetHeights[i] = minBarHeightFraction
        }
        mode = Mode.IDLE
        invalidate()
    }

    /**
     * Push a microphone RMS value (typically -2..10 from SpeechRecognizer).
     * Only meaningful while in USER_SPEAKING mode.
     */
    fun onRmsChanged(rmsdB: Float) {
        if (mode != Mode.USER_SPEAKING) return
        // Normalize roughly: clamp -2..10 → 0..1, with a floor so bars never fully die.
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        // Distribute energy across bars with slight per-bar randomness so it looks organic.
        // Middle bars get the most amplitude.
        val emphasis = floatArrayOf(0.55f, 0.85f, 1.0f, 0.85f, 0.55f)
        for (i in 0 until barCount) {
            val jitter = 0.85f + Random.nextFloat() * 0.3f  // 0.85..1.15
            targetHeights[i] = (minBarHeightFraction + normalized * emphasis[i] * jitter)
                .coerceIn(minBarHeightFraction, maxBarHeightFraction)
        }
    }

    private fun updateForCurrentMode() {
        when (mode) {
            Mode.IDLE -> {
                // Slow breathing wave
                val t = (System.currentTimeMillis() - animationStartMs) / 1000f
                for (i in 0 until barCount) {
                    val phase = i * 0.5f
                    val wave = (sin(t * 1.8f + phase) + 1f) / 2f  // 0..1
                    targetHeights[i] = minBarHeightFraction + wave * 0.18f
                }
            }
            Mode.BOT_SPEAKING -> {
                // Lively but synthetic wave — bot has no real RMS to feed us
                val t = (System.currentTimeMillis() - animationStartMs) / 1000f
                for (i in 0 until barCount) {
                    val phase = i * 0.7f
                    val wave = abs(sin(t * 4.5f + phase))  // 0..1
                    val secondary = abs(sin(t * 2.3f + phase * 1.3f)) * 0.4f
                    targetHeights[i] = (minBarHeightFraction + (wave * 0.55f + secondary) * 0.8f)
                        .coerceIn(minBarHeightFraction, maxBarHeightFraction)
                }
            }
            Mode.USER_SPEAKING -> {
                // Targets are set externally via onRmsChanged.
                // Add a slow decay so bars sink back during silence between words.
                for (i in 0 until barCount) {
                    targetHeights[i] = (targetHeights[i] * 0.94f)
                        .coerceAtLeast(minBarHeightFraction)
                }
            }
        }

        // Ease current toward target
        for (i in 0 until barCount) {
            currentHeights[i] += (targetHeights[i] - currentHeights[i]) * smoothing
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barWidthPx = barWidthDp * resources.displayMetrics.density
        val barGapPx = barGapDp * resources.displayMetrics.density
        val cornerPx = cornerRadiusDp * resources.displayMetrics.density

        val totalWidth = barCount * barWidthPx + (barCount - 1) * barGapPx
        val startX = (width - totalWidth) / 2f
        val centerY = height / 2f
        val maxHalf = height / 2f * 0.9f  // leave a little padding

        paint.color = when (mode) {
            Mode.IDLE -> idleColor
            Mode.USER_SPEAKING -> userColor
            Mode.BOT_SPEAKING -> botColor
        }

        for (i in 0 until barCount) {
            val h = currentHeights[i] * maxHalf
            val left = startX + i * (barWidthPx + barGapPx)
            rect.set(left, centerY - h, left + barWidthPx, centerY + h)
            canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ticker.cancel()
    }
}