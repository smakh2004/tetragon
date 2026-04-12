package com.example.tetragon.gameModel

import android.content.Context

object GradeManager {
    private const val PREF_NAME = "tetragon_prefs"
    private const val KEY_SELECTED_GRADE = "selected_grade"
    private const val KEY_SELECTED_SUBJECT = "selected_subject" // New Key

    fun saveChoice(context: Context, grade: Int, subject: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_SELECTED_GRADE, grade)
            .putString(KEY_SELECTED_SUBJECT, subject)
            .apply()
    }

    fun getGrade(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SELECTED_GRADE, 1)
    }

    fun getSubject(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_SUBJECT, "MATH") ?: "MATH" // Default to MATH
    }
}