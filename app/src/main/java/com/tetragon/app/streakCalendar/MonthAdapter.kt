package com.tetragon.app.streakCalendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R

class MonthAdapter(private val months: List<MonthModel>) : RecyclerView.Adapter<MonthAdapter.MonthViewHolder>() {

    class MonthViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMonthName: TextView = view.findViewById(R.id.tvMonthName)
        val rvDaysGrid: RecyclerView = view.findViewById(R.id.rvDaysGrid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_month, parent, false)
        return MonthViewHolder(view)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        val month = months[position]
        holder.tvMonthName.text = month.monthName

        holder.rvDaysGrid.layoutManager = GridLayoutManager(holder.itemView.context, 7)
        holder.rvDaysGrid.adapter = DaysAdapter(month.days)
    }

    override fun getItemCount() = months.size
}