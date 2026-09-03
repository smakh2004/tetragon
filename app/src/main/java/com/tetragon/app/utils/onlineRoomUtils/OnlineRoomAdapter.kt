package com.tetragon.app.utils.onlineRoomUtils

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.kotlin.RiveAnimationView
import com.tetragon.app.R

class OnlineRoomAdapter(
    private val rooms: List<OnlineRoom>,
    private val onJoinClick: (OnlineRoom) -> Unit,
    private val onCancelClick: (OnlineRoom) -> Unit
) : RecyclerView.Adapter<OnlineRoomAdapter.RoomViewHolder>() {

    companion object {
        private const val AVATAR_VIEW_MODEL_NAME = "ViewModel1"
        private const val MAX_RIVE_ATTEMPTS = 60
        private const val RIVE_RETRY_DELAY_MS = 50L

        private val AVATAR_NUMBERS =
            listOf("face", "hair", "glasses", "hat", "mustache", "body")

        private val AVATAR_COLORS = listOf(
            "skinColor", "hairColor", "glassColor", "capColor",
            "mustacheColor", "clothColor", "backgroundColor", "eyebrowColor", "eyeColor"
        )

        /**
         * Creates a fresh ViewModel1 instance for this avatar view and writes the user's
         * saved avatarConfig into it. Retries while the .riv is still loading.
         */
        fun applyAvatar(
            riveView: RiveAnimationView,
            config: Map<*, *>?,
            firstName: String,
            attempt: Int = 0
        ) {
            riveView.post {
                val controller = riveView.controller
                val file = controller.file
                val stateMachine = controller.stateMachines.firstOrNull()

                if (file == null || stateMachine == null) {
                    if (attempt < MAX_RIVE_ATTEMPTS) {
                        riveView.postDelayed(
                            { applyAvatar(riveView, config, firstName, attempt + 1) },
                            RIVE_RETRY_DELAY_MS
                        )
                    }
                    return@post
                }

                runCatching {
                    val vm = file.getViewModelByName(AVATAR_VIEW_MODEL_NAME) ?: return@post
                    val vmi = vm.createDefaultInstance()

                    controller.activeArtboard?.viewModelInstance = vmi
                    controller.stateMachines.forEach { it.viewModelInstance = vmi }

                    runCatching { vmi.getStringProperty("firstName")?.value = firstName }

                    AVATAR_NUMBERS.forEach { key ->
                        val value = (config?.get(key) as? Number)?.toFloat() ?: 1f
                        runCatching { vmi.getNumberProperty(key)?.value = value }
                    }

                    val hatValue = (config?.get("hat") as? Number)?.toInt() ?: 1
                    runCatching { vmi.getBooleanProperty("hatOn")?.value = (hatValue > 1) }

                    AVATAR_COLORS.forEach { propName ->
                        val hex = config?.get(propName) as? String ?: return@forEach
                        val colorInt =
                            runCatching { Color.parseColor(hex) }.getOrNull() ?: return@forEach
                        runCatching { vmi.getColorProperty(propName)?.value = colorInt }
                    }
                }
            }
        }
    }

    inner class RoomViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: View = itemView.findViewById(R.id.roomCard)
        val avatar: RiveAnimationView = itemView.findViewById(R.id.roomAvatarRive)
        val actionBtn: TextView = itemView.findViewById(R.id.roomActionBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_online_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun getItemCount(): Int = rooms.size

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = rooms[position]
        val context = holder.itemView.context

        applyAvatar(holder.avatar, room.avatarConfig, room.ownerName)

        if (room.isMine) {
            holder.card.setBackgroundResource(R.drawable.bg_room_card_mine)
            holder.actionBtn.setBackgroundResource(R.drawable.bg_cancel_button)
            holder.actionBtn.text = context.getString(R.string.cancel)
            holder.actionBtn.setTextColor(context.resources.getColor(R.color.gray_1, null))
            holder.actionBtn.setOnClickListener { onCancelClick(room) }
        } else {
            holder.card.setBackgroundResource(R.drawable.bg_room_card)
            holder.actionBtn.setBackgroundResource(R.drawable.bg_join_button)
            holder.actionBtn.text = context.getString(R.string.join)
            holder.actionBtn.setTextColor(context.resources.getColor(R.color.blue_2, null))
            holder.actionBtn.setOnClickListener { onJoinClick(room) }
        }
    }
}