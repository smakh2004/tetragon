package com.tetragon.app.streakCalendar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R

class DaysAdapter(private val days: List<DayModel>) : RecyclerView.Adapter<DaysAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tvDayNumber)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val day = days[position]
        if (day.isEmpty) {
            holder.tvDay.visibility = View.INVISIBLE
        } else {
            holder.tvDay.visibility = View.VISIBLE
            holder.tvDay.text = day.dayNumber

            if (day.isStreakActive) {
                holder.tvDay.setBackgroundResource(R.drawable.bg_active)
                holder.tvDay.setTextColor(Color.parseColor("#5E6D83"))
            } else {
                holder.tvDay.setBackgroundResource(R.drawable.bg_inactive)
                holder.tvDay.setTextColor(Color.parseColor("#D3D3D3"))
            }
        }
    }

    override fun getItemCount() = days.size
}