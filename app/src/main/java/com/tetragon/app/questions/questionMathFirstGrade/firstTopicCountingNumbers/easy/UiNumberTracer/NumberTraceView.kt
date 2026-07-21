package com.tetragon.app.questions.questionMathFirstGrade.firstTopicCountingNumbers.easy.UiNumberTracer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.tetragon.app.R
import kotlin.math.hypot

class NumberTraceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colorTrackBg = ContextCompat.getColor(context, R.color.gray_2)
    private val colorFill = ContextCompat.getColor(context, R.color.text_color)
    private val colorBlue2 = ContextCompat.getColor(context, R.color.blue_2)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colorTrackBg
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colorFill
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colorBlue2
        pathEffect = DashPathEffect(floatArrayOf(16f, 18f), 0f)
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorBlue2
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colorBlue2
    }

    // Custom paint configured to give the inner knob arrow its thick, rounded look
    private val knobArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val trackPath = Path()
    private val guidePath = Path()
    private val fillPath = Path()
    private var pathPoints = listOf<PointF>()

    // Indices in pathPoints where a new stroke (contour) begins.
    // Used to support multi-stroke glyphs like "4" where the pen lifts.
    private val contourStartIndices = mutableSetOf<Int>()

    private var trackWidth = 70f
    private var knobRadius = 65f
    private var currentProgressIndex = 0
    private var isDragging = false
    private var isCompleted = false
    private var currentKnobPos = PointF()
    private var currentNumber = 1

    var onTraceProgressListener: ((percent: Float) -> Unit)? = null

    val currentFilledPercentage: Float
        get() {
            if (pathPoints.isEmpty()) return 0f
            return (currentProgressIndex.toFloat() / (pathPoints.size - 1).toFloat()) * 100f
        }

    fun setTargetNumber(number: Int) {
        this.currentNumber = number
        isCompleted = false
        currentProgressIndex = 0
        generatePointsForNumber()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        trackWidth = w * 0.12f
        knobRadius = trackWidth * 0.90f

        trackPaint.strokeWidth = trackWidth
        fillPaint.strokeWidth = trackWidth
        guidePaint.strokeWidth = trackWidth * 0.12f
        arrowPaint.strokeWidth = trackWidth * 0.08f

        // Match the arrow stroke proportions precisely to your visual sample
        knobArrowPaint.strokeWidth = knobRadius * 0.28f

        generatePointsForNumber()
    }

    private fun generatePointsForNumber() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val numberPath = Path()

        when (currentNumber) {
            1 -> {
                numberPath.moveTo(w * 0.23f, h * 0.35f)
                numberPath.lineTo(w * 0.54f, h * 0.16f)
                numberPath.lineTo(w * 0.54f, h * 0.84f)
            }
            2 -> {
                numberPath.moveTo(w * 0.28f, h * 0.38f)
                numberPath.cubicTo(w * 0.22f, h * 0.11f, w * 0.74f, h * 0.11f, w * 0.74f, h * 0.35f)
                numberPath.cubicTo(w * 0.74f, h * 0.48f, w * 0.55f, h * 0.64f, w * 0.27f, h * 0.84f)
                numberPath.lineTo(w * 0.74f, h * 0.84f)
            }
            3 -> {
                numberPath.moveTo(w * 0.29f, h * 0.28f)
                numberPath.cubicTo(w * 0.27f, h * 0.12f, w * 0.71f, h * 0.12f, w * 0.71f, h * 0.32f)
                numberPath.cubicTo(w * 0.71f, h * 0.47f, w * 0.51f, h * 0.49f, w * 0.43f, h * 0.49f)
                numberPath.moveTo(w * 0.43f, h * 0.49f)
                numberPath.cubicTo(w * 0.59f, h * 0.49f, w * 0.73f, h * 0.53f, w * 0.73f, h * 0.69f)
                numberPath.cubicTo(w * 0.73f, h * 0.86f, w * 0.27f, h * 0.86f, w * 0.26f, h * 0.70f)
            }
            4 -> {
                numberPath.moveTo(w * 0.31f, h * 0.22f)   // top of diagonal
                numberPath.lineTo(w * 0.23f, h * 0.58f)   // down-left
                numberPath.lineTo(w * 0.68f, h * 0.58f)   // across to the crossing
                numberPath.lineTo(w * 0.68f, h * 0.22f)   // UP the vertical to the top
                numberPath.lineTo(w * 0.68f, h * 0.84f)   // DOWN to the bottom
            }
            5 -> {
                numberPath.moveTo(w * 0.69f, h * 0.18f)
                numberPath.lineTo(w * 0.36f, h * 0.18f)
                numberPath.lineTo(w * 0.34f, h * 0.47f)
                numberPath.cubicTo(w * 0.41f, h * 0.44f, w * 0.74f, h * 0.44f, w * 0.74f, h * 0.66f)
                numberPath.cubicTo(w * 0.74f, h * 0.86f, w * 0.27f, h * 0.86f, w * 0.26f, h * 0.70f)
            }
            6 -> {
                numberPath.moveTo(w * 0.68f, h * 0.16f)
                numberPath.cubicTo(w * 0.58f, h * 0.16f, w * 0.33f, h * 0.34f, w * 0.33f, h * 0.58f)
                numberPath.cubicTo(w * 0.33f, h * 0.78f, w * 0.74f, h * 0.78f, w * 0.74f, h * 0.58f)
                numberPath.cubicTo(w * 0.74f, h * 0.39f, w * 0.36f, h * 0.39f, w * 0.33f, h * 0.58f)
            }
            7 -> {
                numberPath.moveTo(w * 0.24f, h * 0.18f)
                numberPath.lineTo(w * 0.76f, h * 0.18f)
                numberPath.lineTo(w * 0.35f, h * 0.84f)
            }
            8 -> {
                numberPath.moveTo(w * 0.50f, h * 0.49f)
                numberPath.cubicTo(w * 0.21f, h * 0.44f, w * 0.21f, h * 0.15f, w * 0.50f, h * 0.15f)
                numberPath.cubicTo(w * 0.79f, h * 0.15f, w * 0.79f, h * 0.44f, w * 0.50f, h * 0.49f)
                numberPath.cubicTo(w * 0.18f, h * 0.54f, w * 0.18f, h * 0.85f, w * 0.50f, h * 0.85f)
                numberPath.cubicTo(w * 0.82f, h * 0.85f, w * 0.82f, h * 0.54f, w * 0.50f, h * 0.49f)
            }
            9 -> {
                numberPath.moveTo(w * 0.67f, h * 0.36f)
                numberPath.cubicTo(w * 0.67f, h * 0.16f, w * 0.25f, h * 0.16f, w * 0.25f, h * 0.36f)
                numberPath.cubicTo(w * 0.25f, h * 0.56f, w * 0.67f, h * 0.56f, w * 0.67f, h * 0.36f)
                numberPath.lineTo(w * 0.67f, h * 0.62f)
                numberPath.cubicTo(w * 0.67f, h * 0.83f, w * 0.28f, h * 0.85f, w * 0.27f, h * 0.72f)
            }
        }

        val pm = PathMeasure(numberPath, false)
        val tempPoints = mutableListOf<PointF>()
        val pos = FloatArray(2)
        contourStartIndices.clear()

        do {
            // Record where this stroke begins in the flattened point list.
            contourStartIndices.add(tempPoints.size)

            val length = pm.length
            val step = 4f
            var distance = 0f
            while (distance <= length) {
                pm.getPosTan(distance, pos, null)
                tempPoints.add(PointF(pos[0], pos[1]))
                distance += step
            }
        } while (pm.nextContour())

        pathPoints = tempPoints

        if (pathPoints.isNotEmpty()) {
            currentKnobPos.set(pathPoints.first())
            buildPathsFromPoints()
        }
    }

    private fun buildPathsFromPoints() {
        trackPath.reset()
        guidePath.reset()
        if (pathPoints.isEmpty()) return

        trackPath.moveTo(pathPoints.first().x, pathPoints.first().y)
        guidePath.moveTo(pathPoints.first().x, pathPoints.first().y)

        for (i in 1 until pathPoints.size) {
            // Start of a new stroke: lift the pen instead of drawing a bridge line.
            if (i in contourStartIndices) {
                trackPath.moveTo(pathPoints[i].x, pathPoints[i].y)
                guidePath.moveTo(pathPoints[i].x, pathPoints[i].y)
            } else {
                trackPath.lineTo(pathPoints[i].x, pathPoints[i].y)
                guidePath.lineTo(pathPoints[i].x, pathPoints[i].y)
            }
        }
    }

    private fun updateFillPath() {
        fillPath.reset()
        if (pathPoints.isEmpty() || currentProgressIndex == 0) return

        fillPath.moveTo(pathPoints.first().x, pathPoints.first().y)
        for (i in 1..currentProgressIndex) {
            // Don't fill across the gap between separate strokes.
            if (i in contourStartIndices) {
                fillPath.moveTo(pathPoints[i].x, pathPoints[i].y)
            } else {
                fillPath.lineTo(pathPoints[i].x, pathPoints[i].y)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pathPoints.isEmpty()) return

        // 1. Grey track background
        canvas.drawPath(trackPath, trackPaint)

        // 2. Blue dotted guide line
        canvas.drawPath(guidePath, guidePaint)

        // 3. Arrow at the final target end point
        drawTerminalArrow(canvas)

        // 4. Draw user progress line over track
        updateFillPath()
        canvas.drawPath(fillPath, fillPaint)

        // 5. Blue slider knob
        canvas.drawCircle(currentKnobPos.x, currentKnobPos.y, knobRadius, knobPaint)

        // 6. Draw the clean, thick white path tracking arrow inside the knob
        drawKnobDirectionalArrow(canvas)
    }

    private fun drawTerminalArrow(canvas: Canvas) {
        if (pathPoints.size < 2) return
        val lastPoint = pathPoints.last()
        val prevPoint = pathPoints[pathPoints.size - 2]

        val angle = Math.atan2((lastPoint.y - prevPoint.y).toDouble(), (lastPoint.x - prevPoint.x).toDouble()).toFloat()

        canvas.save()
        canvas.translate(lastPoint.x, lastPoint.y)
        canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat() - 90f)

        val arrowPath = Path().apply {
            moveTo(0f, trackWidth * 0.16f)
            lineTo(-trackWidth * 0.18f, -trackWidth * 0.16f)
            lineTo(trackWidth * 0.18f, -trackWidth * 0.16f)
            close()
        }
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restore()
    }

    private fun drawKnobDirectionalArrow(canvas: Canvas) {
        // Removed 'isCompleted' check so the arrow stays visible at the end of the track
        if (pathPoints.isEmpty()) return

        // Sample points slightly ahead to get smooth direction orientation
        val targetLookahead = minOf(currentProgressIndex + 6, pathPoints.size - 1)

        // Avoid pointing across a stroke gap: if the look-ahead point belongs to a
        // different stroke than the knob, clamp it to the end of the current stroke.
        val safeLookahead = run {
            var idx = targetLookahead
            for (j in (currentProgressIndex + 1)..targetLookahead) {
                if (j in contourStartIndices) {
                    idx = j - 1
                    break
                }
            }
            idx.coerceAtLeast(currentProgressIndex)
        }

        // If we are at the absolute end (or clamped to it), look backward slightly
        // to maintain final correct orientation angle.
        val angleDeg = if (safeLookahead == currentProgressIndex && currentProgressIndex > 0) {
            val pastIndex = (currentProgressIndex - 6).coerceAtLeast(0)
            val past = pathPoints[pastIndex]
            val origin = pathPoints[currentProgressIndex]
            val angleRad = Math.atan2((origin.y - past.y).toDouble(), (origin.x - past.x).toDouble())
            Math.toDegrees(angleRad).toFloat()
        } else {
            val origin = pathPoints[currentProgressIndex]
            val future = pathPoints[safeLookahead]
            val angleRad = Math.atan2((future.y - origin.y).toDouble(), (future.x - origin.x).toDouble())
            Math.toDegrees(angleRad).toFloat()
        }

        canvas.save()
        canvas.translate(currentKnobPos.x, currentKnobPos.y)
        canvas.rotate(angleDeg + 90f) // Formats direction along the forward vector

        val r = knobRadius
        val innerArrowPath = Path().apply {
            // Draws the outer arrowhead wing shape
            moveTo(-r * 0.35f, r * 0.05f)
            lineTo(0f, -r * 0.30f)
            lineTo(r * 0.35f, r * 0.05f)

            // Middle line starting right at the top tip apex (0, -r*0.30f)
            // and extending significantly down to (0, r*0.45f) to make it longer
            moveTo(0f, -r * 0.30f)
            lineTo(0f, r * 0.45f)
        }

        canvas.drawPath(innerArrowPath, knobArrowPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isCompleted || pathPoints.isEmpty()) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val distanceToKnob = hypot(x - currentKnobPos.x, y - currentKnobPos.y)
                if (distanceToKnob <= knobRadius * 1.6f) {
                    isDragging = true
                } else if (tryStartNextContour(x, y)) {
                    // User lifted the pen and re-grabbed the start of the next stroke.
                    isDragging = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    processDragMovement(x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    /**
     * When the knob sits at the end of one stroke and the next stroke starts
     * somewhere else (e.g. the vertical bar of "4"), allow the user to grab the
     * start of that next stroke and continue. Only fires once the current stroke
     * is fully traced, so the completion percentage stays meaningful.
     */
    private fun tryStartNextContour(touchX: Float, touchY: Float): Boolean {
        val nextIndex = currentProgressIndex + 1
        if (nextIndex >= pathPoints.size || nextIndex !in contourStartIndices) return false

        val nextStart = pathPoints[nextIndex]
        if (hypot(touchX - nextStart.x, touchY - nextStart.y) <= knobRadius * 1.8f) {
            currentProgressIndex = nextIndex
            currentKnobPos.set(nextStart)
            invalidate()

            onTraceProgressListener?.invoke(currentFilledPercentage)

            if (currentProgressIndex == pathPoints.size - 1) {
                isCompleted = true
                isDragging = false
            }
            return true
        }
        return false
    }

    private fun processDragMovement(touchX: Float, touchY: Float) {
        // Handle crossing a stroke gap first (multi-stroke glyphs like "4").
        if (tryStartNextContour(touchX, touchY)) return

        val lookAheadLimit = minOf(currentProgressIndex + 15, pathPoints.size - 1)
        var closestIndex = currentProgressIndex
        var minDistance = Float.MAX_VALUE

        for (i in currentProgressIndex..lookAheadLimit) {
            // Never let the closest-point search jump across a stroke boundary;
            // that transition is handled explicitly by tryStartNextContour.
            if (i > currentProgressIndex && i in contourStartIndices) break

            val pt = pathPoints[i]
            val dist = hypot(touchX - pt.x, touchY - pt.y)
            if (dist < minDistance) {
                minDistance = dist
                closestIndex = i
            }
        }

        if (minDistance < trackWidth * 1.6f && closestIndex >= currentProgressIndex) {
            currentProgressIndex = closestIndex
            currentKnobPos.set(pathPoints[currentProgressIndex])
            invalidate()

            onTraceProgressListener?.invoke(currentFilledPercentage)

            if (currentProgressIndex == pathPoints.size - 1) {
                isCompleted = true
                isDragging = false
            }
        }
    }
}