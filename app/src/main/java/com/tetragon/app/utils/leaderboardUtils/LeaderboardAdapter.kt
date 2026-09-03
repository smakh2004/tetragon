package com.tetragon.app.utils.leaderboardUtils

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.kotlin.RiveAnimationView
import com.tetragon.app.R

class LeaderboardAdapter(
    private val users: List<LeaderboardUser>,
    private val currentUserEmail: String?,
    /** Rank shown for item 0. The leaderboard list starts at 4th place. */
    private val rankOffset: Int = 1,
    private val onUserClick: (LeaderboardUser) -> Unit
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    private val avatarNumberKeys = listOf("face", "hair", "glasses", "hat", "mustache", "body")

    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val rowRoot: View = v.findViewById(R.id.rowRoot)
        val rankText: TextView = v.findViewById(R.id.rankText)
        val rankImage: ImageView = v.findViewById(R.id.rankImage)
        val avatarRive: RiveAnimationView = v.findViewById(R.id.avatarRive)
        val avatarFallback: ImageView = v.findViewById(R.id.avatarFallback)
        val onlineDot: View = v.findViewById(R.id.onlineStatusDot)
        val nameText: TextView = v.findViewById(R.id.nameText)
        val xpText: TextView = v.findViewById(R.id.xpText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = users[position]
        val rank = position + rankOffset
        val ctx = holder.itemView.context

        holder.nameText.text = user.firstName
        holder.xpText.text = "${user.monthlyXP} XP"
        holder.onlineDot.visibility = if (user.isOnline) View.VISIBLE else View.GONE

        // ----- Rank: medal image for top 3, number otherwise -----
        val medal = when (rank) {
            1 -> R.drawable.ic_gold
            2 -> R.drawable.ic_silver
            3 -> R.drawable.ic_bronze
            else -> null
        }
        if (medal != null) {
            holder.rankImage.visibility = View.VISIBLE
            holder.rankImage.setImageResource(medal)
            holder.rankText.visibility = View.INVISIBLE
        } else {
            holder.rankImage.visibility = View.GONE
            holder.rankText.visibility = View.VISIBLE
            holder.rankText.text = rank.toString()
        }

        // ----- Current-user highlight -----
        val isCurrentUser = currentUserEmail != null && user.email == currentUserEmail
        holder.rowRoot.setBackgroundColor(
            if (isCurrentUser) ContextCompat.getColor(ctx, R.color.blue_4)
            else Color.TRANSPARENT
        )

        // ----- Avatar: Rive if config exists, else avatar_1 -----
        val config = user.avatarConfig
        if (config != null) {
            holder.avatarRive.visibility = View.VISIBLE
            holder.avatarFallback.visibility = View.GONE
            applyConfigToRive(holder.avatarRive, config)
        } else {
            holder.avatarRive.visibility = View.GONE
            holder.avatarFallback.visibility = View.VISIBLE
            holder.avatarFallback.setImageResource(R.drawable.avatar_1)
        }

        holder.itemView.setOnClickListener { onUserClick(user) }
    }

    private fun applyConfigToRive(rive: RiveAnimationView, config: Map<*, *>) {
        rive.post {
            try {
                val file = rive.controller.file ?: return@post
                val vm = file.getViewModelByName("ViewModel1") ?: return@post
                val vmi = vm.createDefaultInstance()
                rive.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

                avatarNumberKeys.forEach { key ->
                    val value = (config[key] as? Number)?.toInt() ?: 1
                    vmi.getNumberProperty(key)?.value = value.toFloat()
                    if (key == "hat") {
                        vmi.getBooleanProperty("hatOn")?.value = value > 1
                    }
                }

                (config["backgroundColor"] as? String)?.let { hex ->
                    runCatching { Color.parseColor(hex) }.getOrNull()?.let { color ->
                        vmi.getColorProperty("backgroundColor")?.value = color
                    }
                }

                // >>> ADD HERE: per-part colors
                val colorProps = listOf("skinColor", "hairColor", "glassColor", "capColor", "mustacheColor", "clothColor")
                colorProps.forEach { propName ->
                    (config[propName] as? String)?.let { hex ->
                        runCatching { Color.parseColor(hex) }.getOrNull()?.let { c ->
                            vmi.getColorProperty(propName)?.value = c
                        }
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("LeaderboardAdapter", "Rive config error: ${e.message}")
            }
        }
    }

    override fun getItemCount(): Int = users.size
}