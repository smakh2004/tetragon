package com.tetragon.app.utils.leaderboardUtils

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R

class LeaderboardAdapter(
    private val users: List<LeaderboardUser>,
    private val currentEmail: String?, // Pass current user email here
    private val onItemClick: (LeaderboardUser) -> Unit
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rankText: TextView = view.findViewById(R.id.rankText)
        val rankImage: ImageView = view.findViewById(R.id.rankImage)
        val name: TextView = view.findViewById(R.id.nameText)
        val xp: TextView = view.findViewById(R.id.xpText)
        val onlineStatusDot: View = view.findViewById(R.id.onlineStatusDot)

        // ADDED: Reference for the avatar ImageView
        val avatar: ImageView = view.findViewById(R.id.avatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.iteam_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = users.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        val rank = position + 1
        val context = holder.itemView.context
        val isHighlighted = user.email == currentEmail

        holder.name.text = "${user.firstName}"
        holder.xp.text = context.getString(R.string.xp_format, user.monthlyXP.toInt())

        // ADDED: Avatar Logic
        val avatarName = user.avatarName ?: "player_icon"
        val resId = context.resources.getIdentifier(avatarName, "drawable", context.packageName)
        if (resId != 0) {
            holder.avatar.setImageResource(resId)
        } else {
            holder.avatar.setImageResource(R.drawable.avatar_1)
        }

        // Toggle the Green Dot
        if (user.isOnline) {
            holder.onlineStatusDot.visibility = View.VISIBLE
        } else {
            holder.onlineStatusDot.visibility = View.GONE
        }

        // 1. Highlight Name: Always blue_1 if it's the current user
        if (isHighlighted) {
            holder.name.setTextColor(ContextCompat.getColor(context, R.color.blue_2))
        } else {
            // Set your default text color for other users
            holder.name.setTextColor(ContextCompat.getColor(context, R.color.text_color))
        }

        // 2. Handle Rank View, Rank Text Color, and XP Color
        when (rank) {
            1 -> {
                holder.rankImage.visibility = View.VISIBLE
                holder.rankText.visibility = View.GONE
                holder.rankImage.setImageResource(R.drawable.ic_gold)

                if (isHighlighted) {
                    holder.xp.setTextColor(ContextCompat.getColor(context, R.color.blue_2))
                } else {
                    holder.xp.setTextColor(Color.parseColor("#FFC107"))
                }
            }
            2 -> {
                holder.rankImage.visibility = View.VISIBLE
                holder.rankText.visibility = View.GONE
                holder.rankImage.setImageResource(R.drawable.ic_silver)

                if (isHighlighted) {
                    holder.xp.setTextColor(ContextCompat.getColor(context, R.color.blue_2))
                } else {
                    holder.xp.setTextColor(Color.parseColor("#90A4AE"))
                }
            }
            3 -> {
                holder.rankImage.visibility = View.VISIBLE
                holder.rankText.visibility = View.GONE
                holder.rankImage.setImageResource(R.drawable.ic_bronze)

                if (isHighlighted) {
                    holder.xp.setTextColor(ContextCompat.getColor(context, R.color.blue_2))
                } else {
                    holder.xp.setTextColor(Color.parseColor("#A1887F"))
                }
            }
            else -> {
                holder.rankImage.visibility = View.GONE
                holder.rankText.visibility = View.VISIBLE
                holder.rankText.text = rank.toString()

                // Logic for Rank 4+
                if (isHighlighted) {
                    // If it's the current user and not top 3, make Rank and XP blue_1
                    holder.rankText.setTextColor(ContextCompat.getColor(context, R.color.blue_2))
                    holder.xp.setTextColor(ContextCompat.getColor(context, R.color.blue_2))
                } else {
                    // Default colors for everyone else
                    holder.rankText.setTextColor(ContextCompat.getColor(context, R.color.text_color))
                    holder.xp.setTextColor(ContextCompat.getColor(context, R.color.gray_1))
                }
            }
        }

        // 3. Highlight Background
        if (isHighlighted) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.blue_4))
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }

        holder.itemView.setOnClickListener {
            onItemClick(user)
        }
    }
}