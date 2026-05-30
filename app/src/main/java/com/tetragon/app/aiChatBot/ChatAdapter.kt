package com.tetragon.app.aiChatBot

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatAdapter(val chatList: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2
        private const val TYPE_THINKING = 3
    }

    override fun getItemViewType(position: Int): Int {
        val msg = chatList[position]
        return when {
            msg.isThinking -> TYPE_THINKING
            msg.isUser -> TYPE_USER
            else -> TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_THINKING -> ThinkingViewHolder(inflater.inflate(R.layout.item_thinking, parent, false))
            TYPE_USER -> UserVH(inflater.inflate(R.layout.item_chat_user, parent, false))
            else -> AIVH(inflater.inflate(R.layout.item_chat_ai, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = chatList[position]
        when (holder) {
            is UserVH -> holder.tv.text = msg.text
            is AIVH -> {
                if (!msg.isAnimated && msg.text != "Mr. Square is thinking...") {
                    typeWriteText(holder.tv, msg)
                } else {
                    holder.tv.text = msg.text
                }
            }
            is ThinkingViewHolder -> holder.start()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ThinkingViewHolder) holder.stop()
    }

    private fun typeWriteText(textView: TextView, message: ChatMessage) {
        val text = message.text
        textView.text = ""
        message.isAnimated = true
        MainScope().launch {
            for (i in text.indices) {
                textView.append(text[i].toString())
                delay(30)
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

    class ThinkingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dot1 = view.findViewById<View>(R.id.dot1)
        private val dot2 = view.findViewById<View>(R.id.dot2)
        private val dot3 = view.findViewById<View>(R.id.dot3)
        private val animators = mutableListOf<ValueAnimator>()

        fun start() {
            stop()
            animators += createDotAnimator(dot1, 0L)
            animators += createDotAnimator(dot2, 150L)
            animators += createDotAnimator(dot3, 300L)
        }

        fun stop() {
            animators.forEach { it.cancel() }
            animators.clear()
        }

        private fun createDotAnimator(dot: View, startDelay: Long): ValueAnimator {
            return ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                duration = 900L
                this.startDelay = startDelay
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val v = it.animatedValue as Float
                    dot.translationY = -8f * v
                    dot.scaleX = 0.7f + 0.5f * v
                    dot.scaleY = 0.7f + 0.5f * v
                    dot.alpha = 0.4f + 0.6f * v
                }
                start()
            }
        }
    }
}