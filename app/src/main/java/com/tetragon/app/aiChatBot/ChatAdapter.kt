package com.tetragon.app.aiChatBot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatAdapter(val chatList: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val USER = 1
    private val AI = 2

    override fun getItemViewType(position: Int): Int {
        return if (chatList[position].isUser) USER else AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == USER) {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_user, parent, false)
            UserVH(v)
        } else {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_ai, parent, false)
            AIVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = chatList[position]

        if (holder is UserVH) {
            holder.tv.text = msg.text
        } else if (holder is AIVH) {
            if (!msg.isAnimated && msg.text != "Mr. Square is thinking...") {
                typeWriteText(holder.tv, msg)
            } else {
                holder.tv.text = msg.text
            }
        }
    }

    private fun typeWriteText(textView: TextView, message: ChatMessage) {
        val text = message.text
        textView.text = ""
        message.isAnimated = true // Mark as animated immediately

        // Use a scope that matches the View's lifecycle
        MainScope().launch {
            for (i in text.indices) {
                textView.append(text[i].toString())
                delay(30) // Speed of typing in milliseconds
            }
        }
    }

    override fun getItemCount() = chatList.size

    class UserVH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvMessage)
    }

    class AIVH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvMessage)
    }
}