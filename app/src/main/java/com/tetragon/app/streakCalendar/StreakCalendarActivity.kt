package com.tetragon.app.streakCalendar

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StreakCalendarActivity : BaseActivity() {

    private lateinit var rvCalendar: RecyclerView
    private lateinit var tvStreakCount: TextView
    private lateinit var ivStreakIcon: ImageView
    private lateinit var tvYearLabel: TextView
    private lateinit var nestedScrollView: NestedScrollView
    private lateinit var btnPrevYear: ImageView
    private lateinit var btnNextYear: ImageView

    // --- HOISTED LOADING SYSTEM ---
    private lateinit var loadingOverlayContainer: FrameLayout

    private var displayedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var currentStreakMap: Map<String, Boolean> = emptyMap()

    // Single reusable adapter — we update its data instead of recreating it
    private lateinit var monthAdapter: MonthAdapter
    private val monthsList = mutableListOf<MonthModel>()

    // Pending scroll runnable so we can cancel it if user taps again
    private var pendingScrollRunnable: Runnable? = null

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_streak_calendar)

        // Initialize Views
        rvCalendar = findViewById(R.id.rvCalendar)
        tvStreakCount = findViewById(R.id.tvStreakCount)
        ivStreakIcon = findViewById(R.id.ivStreakIcon)
        tvYearLabel = findViewById(R.id.tvYearLabel)
        nestedScrollView = findViewById(R.id.nestedScrollView)
        loadingOverlayContainer = findViewById(R.id.loadingOverlayContainer)
        btnPrevYear = findViewById(R.id.btnPrevYear)
        btnNextYear = findViewById(R.id.btnNextYear)

        // Exit Button
        findViewById<ImageView>(R.id.btnExit).setOnClickListener { finish() }

        // Year Navigation — safe against rapid taps
        btnPrevYear.setOnClickListener {
            displayedYear--
            updateCalendarUI()
        }

        btnNextYear.setOnClickListener {
            displayedYear++
            updateCalendarUI()
        }

        // Set up the adapter ONCE
        rvCalendar.layoutManager = LinearLayoutManager(this)
        monthAdapter = MonthAdapter(monthsList)
        rvCalendar.adapter = monthAdapter

        // Show loading display layout immediately before fetching data
        loadingOverlayContainer.visibility = View.VISIBLE
        fetchFirestoreData()
    }

    private fun fetchFirestoreData() {
        val uid = auth.currentUser?.uid ?: run {
            loadingOverlayContainer.visibility = View.GONE
            return
        }

        db.collection("users").document(uid).get().addOnCompleteListener { task ->
            // Safety guard check if context background instance is destroyed during async call
            if (isDestroyed || isFinishing) return@addOnCompleteListener

            if (task.isSuccessful) {
                val doc = task.result
                if (doc != null && doc.exists()) {
                    val currentStreakValue = doc.getLong("streak") ?: 0
                    currentStreakMap = doc.get("weeklyStreakDays") as? Map<String, Boolean> ?: emptyMap()

                    // Update Streak Text with the current streak
                    tvStreakCount.text = currentStreakValue.toString()

                    // Toggle Icon based on streak value
                    if (currentStreakValue == 0L) {
                        ivStreakIcon.setImageResource(R.drawable.streak_null)
                    } else {
                        ivStreakIcon.setImageResource(R.drawable.streak)
                    }

                    updateCalendarUI()
                }
            }

            // Hide the full screen loader overlay layer instantly once complete
            loadingOverlayContainer.visibility = View.GONE
        }
    }

    private fun updateCalendarUI() {
        tvYearLabel.text = displayedYear.toString()
        generateCalendarData(currentStreakMap)
    }

    private fun generateCalendarData(streakMap: Map<String, Boolean>) {
        // Cancel any pending scroll from a previous (possibly rapid) year change
        pendingScrollRunnable?.let {
            rvCalendar.removeCallbacks(it)
            nestedScrollView.removeCallbacks(it)
        }
        pendingScrollRunnable = null

        val calendar = Calendar.getInstance()
        val realCurrentYear = calendar.get(Calendar.YEAR)
        val realCurrentMonth = calendar.get(Calendar.MONTH)

        // Build the new list off to the side
        val newMonths = ArrayList<MonthModel>(12)
        for (monthIndex in 0 until 12) {
            val monthCal = Calendar.getInstance().apply {
                set(displayedYear, monthIndex, 1)
            }
            val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(monthCal.time)
            val days = getDaysForMonth(monthIndex, displayedYear, streakMap)
            newMonths.add(MonthModel(monthName, monthIndex, displayedYear, days))
        }

        // Defer the data swap so we never mutate during an in-flight layout pass
        val applyData = Runnable {
            if (isDestroyed || isFinishing) return@Runnable
            if (rvCalendar.isComputingLayout) {
                // Try again on the next frame if RV is still mid-layout
                rvCalendar.post {
                    if (isDestroyed || isFinishing) return@post
                    swapMonthData(newMonths)
                    scheduleCenterScroll(realCurrentYear, realCurrentMonth)
                }
                return@Runnable
            }
            swapMonthData(newMonths)
            scheduleCenterScroll(realCurrentYear, realCurrentMonth)
        }

        if (rvCalendar.isComputingLayout || rvCalendar.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            rvCalendar.post(applyData)
        } else {
            applyData.run()
        }
    }

    private fun swapMonthData(newMonths: List<MonthModel>) {
        monthsList.clear()
        monthsList.addAll(newMonths)
        monthAdapter.notifyDataSetChanged()
    }

    private fun scheduleCenterScroll(realCurrentYear: Int, realCurrentMonth: Int) {
        if (displayedYear == realCurrentYear) {
            val scrollRunnable = Runnable {
                if (isDestroyed || isFinishing) return@Runnable
                val viewHolder = rvCalendar.findViewHolderForAdapterPosition(realCurrentMonth)
                if (viewHolder != null) {
                    val itemTop = viewHolder.itemView.top
                    val itemHeight = viewHolder.itemView.height
                    val scrollHeight = nestedScrollView.height

                    val centerOffset = (scrollHeight / 2) - (itemHeight / 2)
                    val yScrollTarget = (itemTop + rvCalendar.top) - centerOffset

                    nestedScrollView.scrollTo(0, yScrollTarget.coerceAtLeast(0))
                }
                pendingScrollRunnable = null
            }
            pendingScrollRunnable = scrollRunnable
            rvCalendar.postDelayed(scrollRunnable, 100)
        } else {
            nestedScrollView.scrollTo(0, 0)
        }
    }

    private fun getDaysForMonth(month: Int, year: Int, streakMap: Map<String, Boolean>): List<DayModel> {
        val list = mutableListOf<DayModel>()
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)

        val firstDay = cal.get(Calendar.DAY_OF_WEEK)
        val offset = if (firstDay == Calendar.SUNDAY) 6 else firstDay - 2

        // Add empty cells for padding
        for (i in 0 until offset) {
            list.add(DayModel("", isEmpty = true))
        }

        // Add actual days
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..daysInMonth) {
            val dateKey = String.format("%d-%02d-%02d", year, month + 1, i)
            val isActive = streakMap[dateKey] == true
            list.add(DayModel(i.toString(), isStreakActive = isActive))
        }
        return list
    }

    override fun onDestroy() {
        // Clean up any pending scroll callbacks to avoid leaks / crashes after finish
        pendingScrollRunnable?.let {
            rvCalendar.removeCallbacks(it)
            nestedScrollView.removeCallbacks(it)
        }
        pendingScrollRunnable = null
        super.onDestroy()
    }
}