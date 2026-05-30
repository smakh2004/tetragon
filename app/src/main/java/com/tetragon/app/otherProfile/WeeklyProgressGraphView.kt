package com.tetragon.app.otherProfile

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.tetragon.app.R

class WeeklyProgressGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val graphColor = Color.parseColor("#40C4FF")
    private val gridLineColor = Color.parseColor("#E0E0E0")
    private val axisTextColor = Color.parseColor("#9E9E9E")
    private val bottomDotColor = Color.parseColor("#9E9E9E")

    private var dataPoints = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private var maxDataValue = 100f
    private var localizedDayLabels = arrayOf("", "", "", "", "", "", "")

    // Load Inter Font
    private val interTypeface = ResourcesCompat.getFont(context, R.font.inter_semibold)

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gridLineColor; strokeWidth = 3f; style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = graphColor; strokeWidth = 6f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = graphColor; style = Paint.Style.FILL }
    private val baselineDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bottomDotColor; style = Paint.Style.FILL }

    // Apply Inter font to text paints
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = axisTextColor; textSize = dpToPx(12f); textAlign = Paint.Align.RIGHT
        typeface = interTypeface
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = axisTextColor; textSize = dpToPx(12f); textAlign = Paint.Align.CENTER
        typeface = interTypeface
    }

    fun setData(weeklyValues: FloatArray, labels: Array<String>) {
        if (weeklyValues.size == 7) {
            this.dataPoints = weeklyValues
            this.localizedDayLabels = labels
            val highestValue = weeklyValues.maxOrNull() ?: 0f
            this.maxDataValue = if (highestValue > 0f) highestValue * 1.15f else 100f

            this.alpha = 0f
            this.animate().alpha(1f).setDuration(500).start()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val paddingLeft = dpToPx(45f); val paddingRight = dpToPx(20f)
        val paddingTop = dpToPx(25f); val paddingBottom = dpToPx(35f)
        val graphWidth = width - paddingLeft - paddingRight
        val graphHeight = height - paddingTop - paddingBottom

        // Draw Grid Lines
        val yLevels = floatArrayOf(maxDataValue, maxDataValue * 0.66f, maxDataValue * 0.33f, 0f)
        for (level in yLevels) {
            val y = paddingTop + ((1f - (level / maxDataValue)) * graphHeight)
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
            canvas.drawText(level.toInt().toString(), paddingLeft - dpToPx(10f), y + dpToPx(4f), textPaint)
        }

        val stepX = graphWidth / (dataPoints.size - 1)
        val pointsX = FloatArray(dataPoints.size) { i -> paddingLeft + (i * stepX) }
        val pointsY = FloatArray(dataPoints.size) { i -> paddingTop + ((1f - (dataPoints[i] / maxDataValue)) * graphHeight) }

        // Draw Baseline and Labels
        val baseLineY = paddingTop + graphHeight
        for (i in dataPoints.indices) {
            canvas.drawCircle(pointsX[i], baseLineY, dpToPx(5f), baselineDotPaint)
            if (i < localizedDayLabels.size) canvas.drawText(localizedDayLabels[i], pointsX[i], height - dpToPx(10f), labelPaint)
        }

        // Draw Connection Path
        val path = Path().apply {
            moveTo(pointsX[0], pointsY[0])
            for (i in 1 until dataPoints.size) lineTo(pointsX[i], pointsY[i])
        }
        canvas.drawPath(path, linePaint)

        // Draw Data Points
        for (i in dataPoints.indices) canvas.drawCircle(pointsX[i], pointsY[i], dpToPx(6f), dotPaint)
    }

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density
}