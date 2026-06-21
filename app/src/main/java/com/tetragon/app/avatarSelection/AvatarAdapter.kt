package com.tetragon.app.avatarSelection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R

class AvatarAdapter(
    private val defaultAvatars: List<Int>,
    private val specialAvatars: List<Int>,
    private val specialTypes: List<AvatarType>,
    private var selectedIndex: Int = -1,
    private val onAvatarSelected: (avatarType: AvatarType, actualIndex: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class AvatarType {
        DEFAULT,
        TESTER,
        FUTURE_SPECIAL
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_AVATAR = 1
    }

    private val items = mutableListOf<AdapterItem>()

    init {
        buildFlattenedList()
    }

    private fun buildFlattenedList() {
        items.clear()

        // 1. Default Section
        items.add(AdapterItem.Header(R.string.default_avatars))
        defaultAvatars.forEachIndexed { index, resId ->
            items.add(AdapterItem.AvatarItem(resId, AvatarType.DEFAULT, index))
        }

        // 2. Special Section (Combines tester and dynamic emails)
        if (specialAvatars.isNotEmpty()) {
            items.add(AdapterItem.Header(R.string.special_avatars))
            specialAvatars.forEachIndexed { index, resId ->
                items.add(AdapterItem.AvatarItem(resId, specialTypes[index], index))
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AdapterItem.Header -> TYPE_HEADER
            is AdapterItem.AvatarItem -> TYPE_AVATAR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_avatar_header, parent, false))
        } else {
            AvatarViewHolder(inflater.inflate(R.layout.item_avatar, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AdapterItem.Header -> {
                (holder as HeaderViewHolder).headerTitle.setText(item.titleResId)
            }
            is AdapterItem.AvatarItem -> {
                val avatarHolder = holder as AvatarViewHolder
                avatarHolder.image.setImageResource(item.resId)

                avatarHolder.container.isSelected = (selectedIndex == position)

                avatarHolder.container.setOnClickListener {
                    val previous = selectedIndex

                    // FIXED: Using 'position' from onBindViewHolder parameter signatures
                    // avoids Unresolved Reference and prevents view recycling tracking bugs.
                    selectedIndex = position

                    notifyItemChanged(previous)
                    notifyItemChanged(selectedIndex)

                    onAvatarSelected(item.type, item.actualIndex)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun isHeaderPosition(position: Int): Boolean {
        return items.getOrNull(position) is AdapterItem.Header
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerTitle: TextView = view.findViewById(R.id.section_header_title)
    }

    class AvatarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: FrameLayout = view.findViewById(R.id.avatar_item_container)
        val image: ImageView = view.findViewById(R.id.avatar_image)
    }

    sealed interface AdapterItem {
        data class Header(val titleResId: Int) : AdapterItem
        data class AvatarItem(val resId: Int, val type: AvatarType, val actualIndex: Int) : AdapterItem
    }
}