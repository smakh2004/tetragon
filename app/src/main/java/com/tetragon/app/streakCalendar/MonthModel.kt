package com.tetragon.app.streakCalendar

data class MonthModel(
    val monthName: String,
    val monthInt: Int,
    val year: Int,
    val days: List<DayModel>
)