package com.tetragon.app.utils

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.tetragon.app.R

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private var drawPath = Path()
    private var drawPaint = Paint()
    private var drawCanvas: Canvas? = null
    private var canvasBitmap: Bitmap? = null

    // Set your desired internal gray color here
    private val canvasBackgroundColor = ContextCompat.getColor(context, R.color.gray_3)

    var onDrawingChangedListener: (() -> Unit)? = null

    init {
        setupPaint()
    }

    private fun setupPaint() {
        // Now using your text_color from res/values/colors.xml
        val strokeColor = ContextCompat.getColor(context, R.color.text_color)

        drawPaint.color = strokeColor
        drawPaint.isAntiAlias = true
        drawPaint.strokeWidth = 30f
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeJoin = Paint.Join.ROUND
        drawPaint.strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            canvasBitmap?.eraseColor(canvasBackgroundColor) // Set Gray background
            drawCanvas = Canvas(canvasBitmap!!)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvasBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
        canvas.drawPath(drawPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> drawPath.moveTo(x, y)
            MotionEvent.ACTION_MOVE -> drawPath.lineTo(x, y)
            MotionEvent.ACTION_UP -> {
                drawCanvas?.drawPath(drawPath, drawPaint)
                drawPath.reset()
                onDrawingChangedListener?.invoke()
            }
        }
        invalidate()
        return true
    }

    fun clearCanvas() {
        canvasBitmap?.eraseColor(canvasBackgroundColor) // Reset to Gray
        drawPath.reset()
        invalidate()
    }

    fun getBitmap(): Bitmap? = canvasBitmap
}