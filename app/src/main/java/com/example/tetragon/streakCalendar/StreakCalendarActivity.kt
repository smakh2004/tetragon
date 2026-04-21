package com.example.tetragon.streakCalendar

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tetragon.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class StreakCalendarActivity : AppCompatActivity() {

    private lateinit var rvCalendar: RecyclerView
    private lateinit var tvStreakCount: TextView
    private lateinit var ivStreakIcon: ImageView
    private lateinit var tvYearLabel: TextView
    private lateinit var nestedScrollView: NestedScrollView

    private var displayedYear = Calendar.getInstance().get(Calendar.YEAR)
    private var currentStreakMap: Map<String, Boolean> = emptyMap()

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_streak_calendar)

        // Handle System Bars (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Views
        rvCalendar = findViewById(R.id.rvCalendar)
        tvStreakCount = findViewById(R.id.tvStreakCount)
        ivStreakIcon = findViewById(R.id.ivStreakIcon)
        tvYearLabel = findViewById(R.id.tvYearLabel)
        nestedScrollView = findViewById(R.id.nestedScrollView)

        // Exit Button
        findViewById<ImageView>(R.id.btnExit).setOnClickListener { finish() }

        // Year Navigation
        findViewById<ImageView>(R.id.btnPrevYear).setOnClickListener {
            displayedYear--
            updateCalendarUI()
        }

        findViewById<ImageView>(R.id.btnNextYear).setOnClickListener {
            displayedYear++
            updateCalendarUI()
        }

        rvCalendar.layoutManager = LinearLayoutManager(this)

        fetchFirestoreData()
    }

    private fun fetchFirestoreData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val maxStreak = doc.getLong("maxStreak") ?: 0
                currentStreakMap = doc.get("weeklyStreakDays") as? Map<String, Boolean> ?: emptyMap()

                // Update Streak Text
                tvStreakCount.text = maxStreak.toString()

                // Toggle Icon based on streak value
                if (maxStreak == 0L) {
                    ivStreakIcon.setImageResource(R.drawable.streak_null)
                } else {
                    ivStreakIcon.setImageResource(R.drawable.streak)
                }

                updateCalendarUI()
            }
        }
    }

    private fun updateCalendarUI() {
        tvYearLabel.text = displayedYear.toString()
        generateCalendarData(currentStreakMap)
    }

    private fun generateCalendarData(streakMap: Map<String, Boolean>) {
        val monthsList = mutableListOf<MonthModel>()
        val calendar = Calendar.getInstance()
        val realCurrentYear = calendar.get(Calendar.YEAR)
        val realCurrentMonth = calendar.get(Calendar.MONTH)

        for (monthIndex in 0 until 12) {
            val monthCal = Calendar.getInstance().apply {
                set(displayedYear, monthIndex, 1)
            }
            val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(monthCal.time)
            val days = getDaysForMonth(monthIndex, displayedYear, streakMap)
            monthsList.add(MonthModel(monthName, monthIndex, displayedYear, days))
        }

        val adapter = MonthAdapter(monthsList)
        rvCalendar.adapter = adapter

        // Handle Centered Automatic Scrolling
        if (displayedYear == realCurrentYear) {
            rvCalendar.post {
                rvCalendar.postDelayed({
                    val viewHolder = rvCalendar.findViewHolderForAdapterPosition(realCurrentMonth)
                    if (viewHolder != null) {
                        // 1. Get the top position of the month relative to the RecyclerView
                        val itemTop = viewHolder.itemView.top

                        // 2. Get the height of the month item
                        val itemHeight = viewHolder.itemView.height

                        // 3. Get the height of the visible scroll area
                        val scrollHeight = nestedScrollView.height

                        // 4. Calculate target: ItemTop + Offset to Recycler - (Half Scroll Height - Half Item Height)
                        // This math places the middle of the item in the middle of the scroll view
                        val centerOffset = (scrollHeight / 2) - (itemHeight / 2)
                        val yScrollTarget = (itemTop + rvCalendar.top) - centerOffset

                        // Scroll to the calculated position
                        nestedScrollView.scrollTo(0, yScrollTarget)
                    }
                }, 100)
            }
        } else {
            nestedScrollView.scrollTo(0, 0)
        }
    }

    private fun getDaysForMonth(month: Int, year: Int, streakMap: Map<String, Boolean>): List<DayModel> {
        val list = mutableListOf<DayModel>()
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)

        val firstDay = cal.get(Calendar.DAY_OF_WEEK)
        // Offset for Monday-start (Monday=0, Sunday=6)
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
}