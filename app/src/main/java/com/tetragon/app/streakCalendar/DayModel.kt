package com.tetragon.app.streakCalendar

data class DayModel(
    val dayNumber: String,
    val isStreakActive: Boolean = false,
    val isEmpty: Boolean = false
)