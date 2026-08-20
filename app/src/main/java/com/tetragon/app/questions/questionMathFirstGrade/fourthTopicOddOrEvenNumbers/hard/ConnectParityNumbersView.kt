package com.tetragon.app.questions.questionMathFirstGrade.fourthTopicOddOrEvenNumbers.hard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.tetragon.app.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.random.Random

/**
 * "Connect numbers from 1 to N" — matches the reference design:
 *
 * - A 4-column grid pinned to the BOTTOM of the view (3 rows for N=9).
 * - Cells hold 1..N shuffled so that consecutive numbers are always grid
 *   neighbours (8-direction), plus distractor numbers > N (e.g. 12, 13, 16)
 *   in the remaining cells.
 * - The user connects circles by dragging or tapping. Only NEIGHBOUR cells can
 *   be linked (so lines stay short like the design), any route is allowed,
 *   no revisiting, and a link that would cross an existing line is rejected.
 * - While connecting, everything is BLUE (light fill, stroke, number, line).
 *   After checking, the fragment calls markCorrect() -> green or
 *   markIncorrect() -> red. Same visual, different colour.
 * - Touching the previous circle removes the last link (backtrack).
 */
class ConnectParityNumbersView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private class Node(val number: Int, val row: Int, val col: Int) {
        var cx = 0f
        var cy = 0f
    }

    private val cols = 4
    private var rows = 3

    private val colorText = ContextCompat.getColor(context, R.color.text_color)
    private val colorRing = ContextCompat.getColor(context, R.color.gray_2)
    private val colorWhite = ContextCompat.getColor(context, R.color.white)

    private val colorActive = ContextCompat.getColor(context, R.color.blue_2)
    private val colorCorrect = ContextCompat.getColor(context, R.color.green_1)
    private val colorIncorrect = ContextCompat.getColor(context, R.color.red_2)

    /** Path colour: blue while playing, green/red after check, gray for solution. */
    private var stateColor = colorActive

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorWhite
    }
    private val fillTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = colorRing
    }
    private val ringStatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorText
        typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.inter_semibold)
    }

    private var nodes = listOf<Node>()
    private val connected = mutableListOf<Node>()

    private var radius = 60f
    private var maxNumber = 9
    // The ordered numbers that must be connected (e.g. 2,4,6,8,10 or 1,3,5,7,9).
    private var sequence: List<Int> = (1..maxNumber).toList()
    private var locked = false

    private var dragging = false
    private var fingerX = 0f
    private var fingerY = 0f

    var onProgress: ((connectedCount: Int) -> Unit)? = null
    var onComplete: (() -> Unit)? = null

    val connectedCount: Int get() = connected.size
    val targetCount: Int get() = maxNumber

    /** True when the connected circles are exactly 1,2,...,N in order. */
    fun isCorrectSequence(): Boolean {
        if (connected.size != sequence.size) return false
        for (i in connected.indices) if (connected[i].number != sequence[i]) return false
        return true
    }

    fun isComplete(): Boolean = isCorrectSequence()

    fun setTargetNumber(n: Int) {
        setSequence((1..n.coerceIn(2, 12)).toList())
    }

    /** Connect these numbers, in this order (e.g. listOf(2,4,6,8,10)). */
    fun setSequence(seq: List<Int>) {
        sequence = seq
        maxNumber = seq.size            // path length = how many circles to link
        locked = false
        stateColor = colorActive
        connected.clear()
        generateBoard()
        invalidate()
    }

    /** Clear connections but keep the same board (used for "try again"). */
    fun clearConnections() {
        connected.clear()
        locked = false
        dragging = false
        stateColor = colorActive
        invalidate()
    }

    fun setLocked(value: Boolean) {
        locked = value
    }

    fun markCorrect() {
        stateColor = colorCorrect
        invalidate()
    }

    fun markIncorrect() {
        stateColor = colorIncorrect
        invalidate()
    }

    /** Show the correct 1..N path in gray (used by "see solution"). */
    fun completePath() {
        connected.clear()
        for (num in sequence) {
            nodes.firstOrNull { it.number == num }?.let { connected.add(it) }
        }
        dragging = false
        stateColor = colorRing
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (nodes.isEmpty()) generateBoard() else layoutNodes()
    }

    // ---- board generation --------------------------------------------------

    private fun generateBoard() {
        if (width == 0 || height == 0) return

        rows = ceil((maxNumber + 3) / cols.toFloat()).toInt().coerceAtLeast(3)
        val totalCells = rows * cols

        val cells = placeSequenceByWalk(totalCells)

        // Distractors = every number NOT in the target sequence, drawn from 1..(cells).
        // This makes the board show a natural range of numbers (the non-target parity, etc.).
        val pool = (1..totalCells).filter { it !in sequence }.shuffled().iterator()

        val built = ArrayList<Node>(totalCells)
        for (cell in 0 until totalCells) {
            val r = cell / cols
            val c = cell % cols
            val step = cells[cell]                 // 1..sequence.size on the path, else 0
            val value = if (step > 0) sequence[step - 1] else pool.next()
            built.add(Node(value, r, c))
        }
        nodes = built
        connected.clear()
        layoutNodes()
    }

    private fun placeSequenceByWalk(totalCells: Int): IntArray {
        val result = IntArray(totalCells)
        val dirs = listOf(
            -1 to -1, -1 to 0, -1 to 1,
            0 to -1, 0 to 1,
            1 to -1, 1 to 0, 1 to 1
        )

        repeat(400) {
            result.fill(0)
            val visited = HashSet<Int>()
            val path = ArrayList<Int>(maxNumber)

            var cur = Random.nextInt(totalCells)
            visited.add(cur)
            path.add(cur)

            while (path.size < maxNumber) {
                val r = path.last() / cols
                val c = path.last() % cols
                val options = dirs.mapNotNull { (dr, dc) ->
                    val nr = r + dr
                    val nc = c + dc
                    if (nr !in 0 until rows || nc !in 0 until cols) null
                    else {
                        val cell = nr * cols + nc
                        if (cell in visited) null
                        else if (crossesDiagonal(path, r, c, nr, nc)) null
                        else cell
                    }
                }
                if (options.isEmpty()) break
                cur = options.random()
                visited.add(cur)
                path.add(cur)
            }

            if (path.size == maxNumber) {
                path.forEachIndexed { i, cell -> result[cell] = i + 1 }
                return result
            }
        }

        for (n in 1..maxNumber) {
            val r = (n - 1) / cols
            val c0 = (n - 1) % cols
            val c = if (r % 2 == 0) c0 else cols - 1 - c0
            result[r * cols + c] = n
        }
        return result
    }

    private fun crossesDiagonal(path: List<Int>, r: Int, c: Int, nr: Int, nc: Int): Boolean {
        if (abs(nr - r) != 1 || abs(nc - c) != 1) return false
        val a = r * cols + nc
        val b = nr * cols + c
        for (i in 0 until path.size - 1) {
            val p = path[i]
            val q = path[i + 1]
            if ((p == a && q == b) || (p == b && q == a)) return true
        }
        return false
    }

    private fun layoutNodes() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f || nodes.isEmpty()) return

        val cellW = w / cols
        val cellH = cellW.coerceAtMost(h / rows)
        radius = minOf(cellW, cellH) * 0.40f

        ringPaint.strokeWidth = radius * 0.07f
        ringStatePaint.strokeWidth = radius * 0.07f
        linePaint.strokeWidth = radius * 0.14f
        numberPaint.textSize = radius * 0.92f

        val gridTop = h - rows * cellH
        for (node in nodes) {
            node.cx = (node.col + 0.5f) * cellW
            node.cy = gridTop + (node.row + 0.5f) * cellH
        }
    }

    // ---- drawing -----------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (nodes.isEmpty()) return

        linePaint.color = stateColor
        ringStatePaint.color = stateColor
        fillTintPaint.color = (stateColor and 0x00FFFFFF) or 0x33000000

        // Lines between connected circles
        for (i in 0 until connected.size - 1) {
            val a = connected[i]
            val b = connected[i + 1]
            canvas.drawLine(a.cx, a.cy, b.cx, b.cy, linePaint)
        }
        // Trailing line to finger while dragging
        if (dragging && connected.isNotEmpty() && stateColor == colorActive) {
            val head = connected.last()
            canvas.drawLine(head.cx, head.cy, fingerX, fingerY, linePaint)
        }

        val connectedSet = connected.toHashSet()
        for (node in nodes) {
            canvas.drawCircle(node.cx, node.cy, radius, circlePaint)

            if (connectedSet.contains(node)) {
                canvas.drawCircle(node.cx, node.cy, radius, fillTintPaint)
                canvas.drawCircle(node.cx, node.cy, radius, ringStatePaint)
                // Use default text_color when in solution mode (stateColor == colorRing)
                numberPaint.color = if (stateColor == colorRing) colorText else stateColor
            } else {
                canvas.drawCircle(node.cx, node.cy, radius, ringPaint)
                numberPaint.color = colorText
            }
            val baseline = node.cy - (numberPaint.descent() + numberPaint.ascent()) / 2f
            canvas.drawText(node.number.toString(), node.cx, baseline, numberPaint)
        }
    }

    // ---- interaction -------------------------------------------------------

    private fun nodeAt(x: Float, y: Float): Node? {
        var best: Node? = null
        var bestDist = radius * 1.15f
        for (node in nodes) {
            val d = hypot(x - node.cx, y - node.cy)
            if (d <= bestDist) {
                bestDist = d
                best = node
            }
        }
        return best
    }

    private fun areNeighbours(a: Node, b: Node): Boolean =
        abs(a.row - b.row) <= 1 && abs(a.col - b.col) <= 1 && a !== b

    private fun triggerVibration() {
        performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    private fun tryConnect(node: Node, isTapDown: Boolean = false) {
        if (connected.isEmpty()) {
            connected.add(node)
            triggerVibration()
            afterConnectChange()
            return
        }

        val head = connected.last()

        // Allow changing the first selected node on tap down or before a second node is linked
        if (connected.size == 1) {
            if (node === head) return
            if (isTapDown || !areNeighbours(head, node)) {
                connected[0] = node
                triggerVibration()
                afterConnectChange()
                return
            }
        }

        if (node === head) return

        // Backtrack: touching the previous circle removes the head
        if (connected.size >= 2 && node === connected[connected.size - 2]) {
            connected.removeAt(connected.size - 1)
            triggerVibration()
            afterConnectChange()
            return
        }
        if (connected.contains(node)) return          // no revisiting
        if (!areNeighbours(head, node)) return        // neighbour cells only

        // Reject a link that would cross an existing line
        if (abs(node.row - head.row) == 1 && abs(node.col - head.col) == 1) {
            val cornerA = nodeAtCell(head.row, node.col)
            val cornerB = nodeAtCell(node.row, head.col)
            if (cornerA != null && cornerB != null && linkExists(cornerA, cornerB)) return
        }

        connected.add(node)
        triggerVibration()
        afterConnectChange()
    }

    private fun nodeAtCell(row: Int, col: Int): Node? =
        nodes.firstOrNull { it.row == row && it.col == col }

    private fun linkExists(a: Node, b: Node): Boolean {
        for (i in 0 until connected.size - 1) {
            val p = connected[i]
            val q = connected[i + 1]
            if ((p === a && q === b) || (p === b && q === a)) return true
        }
        return false
    }

    private fun afterConnectChange() {
        onProgress?.invoke(connected.size)
        if (isComplete()) {
            dragging = false
            onComplete?.invoke()
        }
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (locked || nodes.isEmpty()) return false

        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                nodeAt(x, y)?.let { tryConnect(it, isTapDown = true) }
                dragging = true
                fingerX = x; fingerY = y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                fingerX = x; fingerY = y
                nodeAt(x, y)?.let { tryConnect(it, isTapDown = false) }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
            }
        }
        return true
    }
}