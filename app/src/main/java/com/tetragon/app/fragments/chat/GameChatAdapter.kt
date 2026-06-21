package com.tetragon.app.fragments.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R
import com.tetragon.app.fragments.MessageKeys

class GameChatAdapter(private val currentUserId: String) :
    RecyclerView.Adapter<GameChatAdapter.ChatViewHolder>() {

    private val messagesList = ArrayList<GameMessage>()

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_OTHER = 2
    }

    fun submitList(newMessages: List<GameMessage>) {
        messagesList.clear()
        messagesList.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messagesList[position].senderId == currentUserId) VIEW_TYPE_ME else VIEW_TYPE_OTHER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_ME) {
            R.layout.item_chat_bubble_me
        } else {
            R.layout.item_chat_bubble_other
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messagesList[position])
    }

    override fun getItemCount(): Int = messagesList.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSender: TextView? = itemView.findViewById(R.id.tvSenderName)
        private val tvBody: TextView = itemView.findViewById(R.id.tvMessageBody)

        fun bind(message: GameMessage) {
            tvBody.text = MessageKeys.resolve(itemView.context, message.messageText)
            tvSender?.text = message.senderName
        }
    }
}