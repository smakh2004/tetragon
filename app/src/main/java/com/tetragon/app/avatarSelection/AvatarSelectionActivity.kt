package com.tetragon.app.avatarSelection

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.ViewModelBooleanProperty
import app.rive.runtime.kotlin.core.ViewModelColorProperty
import app.rive.runtime.kotlin.core.ViewModelNumberProperty
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity

class AvatarSelectionActivity : BaseActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var riveView: RiveAnimationView
    private lateinit var optionsRecycler: RecyclerView
    private lateinit var swatchRecycler: RecyclerView
    private lateinit var partColorRecycler: RecyclerView
    private lateinit var gridAndPalette: View

    private val numberProperties = mutableMapOf<String, ViewModelNumberProperty>()
    private var hatBooleanProperty: ViewModelBooleanProperty? = null
    private var backgroundColorProperty: ViewModelColorProperty? = null
    private val colorProperties = mutableMapOf<String, ViewModelColorProperty>()

    private companion object {
        const val BACKGROUND_KEY = "background"
    }

    // Suggested palettes per part
    private val skinTones = listOf(
        "#FFE0BD", "#FFCD94", "#F1C27D", "#E0AC69", "#C68642", "#A0673B", "#8D5524", "#5C3A21"
    ).map { Color.parseColor(it) }

    private val hairColors = listOf(
        "#1C1C1C", "#3B2219", "#5A3825", "#7B4B2A", "#A9744F", "#C9A66B", "#E6C68C", "#B00000", "#9E9E9E", "#F5F5F5"
    ).map { Color.parseColor(it) }

    private val clothColors = listOf(
        "#E53935", "#FB8C00", "#FDD835", "#43A047", "#00ACC1", "#1E88E5", "#8E24AA", "#EC407A", "#6D4C41", "#546E7A", "#FFFFFF", "#212121"
    ).map { Color.parseColor(it) }

    private val capColors = clothColors
    private val mustacheColors = hairColors
    private val glassColors = listOf(
        "#212121", "#5A3825", "#8B0000", "#1E3A5F", "#7B7B7B", "#C0A062", "#2E2E2E"
    ).map { Color.parseColor(it) }

    private val categories = listOf(
        AvatarCategory(
            "Face", "face", 8, R.drawable.face_selected, R.drawable.face_unselected,
            optionDrawables = listOf(
                R.drawable.face_one, R.drawable.face_two, R.drawable.face_three, R.drawable.face_four,
                R.drawable.face_five, R.drawable.face_six, R.drawable.face_seven, R.drawable.face_eight
            ),
            colorPropertyName = "skinColor", colorPalette = skinTones
        ),
        AvatarCategory(
            "Hair", "hair", 9, R.drawable.hair_selected, R.drawable.hair_unselected,
            optionDrawables = listOf(
                R.drawable.hair_one, R.drawable.hair_two, R.drawable.hair_three, R.drawable.hair_four,
                R.drawable.hair_five, R.drawable.hair_six, R.drawable.hair_seven, R.drawable.hair_eight,
                R.drawable.hair_nine
            ),
            colorPropertyName = "hairColor", colorPalette = hairColors
        ),
        AvatarCategory(
            "Glasses", "glasses", 4, R.drawable.glasses_selected, R.drawable.glasses_unselected,
            optionDrawables = listOf(
                R.drawable.glasses_one, R.drawable.glasses_two, R.drawable.glasses_three, R.drawable.glasses_four
            ),
            colorPropertyName = "glassColor", colorPalette = glassColors
        ),
        AvatarCategory(
            "Hat", "hat", 6, R.drawable.hat_selected, R.drawable.hat_unselected,
            optionDrawables = listOf(
                R.drawable.hat_one, R.drawable.hat_two, R.drawable.hat_three,
                R.drawable.hat_four, R.drawable.hat_five, R.drawable.hat_six
            ),
            colorPropertyName = "capColor", colorPalette = capColors
        ),
        AvatarCategory(
            "Mustache", "mustache", 6, R.drawable.mustache_selected, R.drawable.mustache_unselected,
            optionDrawables = listOf(
                R.drawable.mustache_one, R.drawable.mustache_two, R.drawable.mustache_three,
                R.drawable.mustache_four, R.drawable.mustache_five, R.drawable.mustache_six
            ),
            colorPropertyName = "mustacheColor", colorPalette = mustacheColors
        ),
        AvatarCategory(
            "Body", "body", 4, R.drawable.body_selected, R.drawable.body_unselected,
            optionDrawables = listOf(
                R.drawable.body_one, R.drawable.body_two, R.drawable.body_three, R.drawable.body_four
            ),
            colorPropertyName = "clothColor", colorPalette = clothColors
        ),
        AvatarCategory("Background", BACKGROUND_KEY, 0, R.drawable.background_selected, R.drawable.background_unselected)
    )

    private var currentCategoryIndex = 0

    private val selectedValues = mutableMapOf<String, Int>().apply {
        categories.forEach { if (it.inputKey != BACKGROUND_KEY) put(it.inputKey, 1) }
    }

    private val selectedColors = mutableMapOf<String, Int>()

    private val backgroundColors = listOf(
        "#00AEEF", "#4CD964", "#FFCC00", "#FF9500", "#FF3B30", "#AF52DE", "#FF2D95", "#8E8E93"
    ).map { Color.parseColor(it) }
    private var selectedBackgroundColor: Int = backgroundColors.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Rive.init(applicationContext)
        } catch (e: Exception) {
            Log.e("AvatarSelection", "Rive initialization error: ${e.message}")
        }

        setContentView(R.layout.activity_avatar_selection)

        riveView = findViewById(R.id.avatar_rive_view)
        val closeBtn = findViewById<ImageView>(R.id.close_btn)
        val saveBtn = findViewById<TextView>(R.id.save_btn)
        val categoryRecycler = findViewById<RecyclerView>(R.id.category_recycler_view)
        optionsRecycler = findViewById(R.id.options_recycler_view)
        swatchRecycler = findViewById(R.id.color_swatches)
        partColorRecycler = findViewById(R.id.part_color_recycler)
        gridAndPalette = findViewById(R.id.grid_and_palette)

        closeBtn.setOnClickListener { finish() }

        val categoryAdapter = CategoryAdapter(categories, currentCategoryIndex) { position ->
            currentCategoryIndex = position
            updateOptionsGrid(optionsRecycler)
        }
        categoryRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        categoryRecycler.adapter = categoryAdapter

        optionsRecycler.layoutManager = GridLayoutManager(this, 2)
        updateOptionsGrid(optionsRecycler)

        riveView.post {
            setupRiveViewModel()
            loadSavedAvatarConfig(optionsRecycler)
        }

        saveBtn.setOnClickListener {
            val uid = auth.currentUser?.uid ?: return@setOnClickListener
            val loadingOverlay = findViewById<FrameLayout?>(R.id.loadingOverlayContainer)
            loadingOverlay?.visibility = View.VISIBLE

            val payload = HashMap<String, Any>(selectedValues)
            payload["backgroundColor"] = String.format("#%06X", 0xFFFFFF and selectedBackgroundColor)
            selectedColors.forEach { (propName, color) ->
                payload[propName] = String.format("#%06X", 0xFFFFFF and color)
            }

            db.collection("users").document(uid)
                .update("avatarConfig", payload)
                .addOnSuccessListener { finish() }
                .addOnFailureListener { loadingOverlay?.visibility = View.GONE }
        }
    }

    private fun setupRiveViewModel() {
        try {
            val file = riveView.controller.file ?: return
            val vm = file.getViewModelByName("ViewModel1") ?: return
            val vmi = vm.createDefaultInstance()
            riveView.controller.stateMachines.firstOrNull()?.viewModelInstance = vmi

            categories.forEach { category ->
                if (category.inputKey == BACKGROUND_KEY) return@forEach
                vmi.getNumberProperty(category.inputKey)?.let { prop ->
                    numberProperties[category.inputKey] = prop
                }
            }

            hatBooleanProperty = vmi.getBooleanProperty("hatOn")
            backgroundColorProperty = vmi.getColorProperty("backgroundColor")
            backgroundColorProperty?.value = selectedBackgroundColor

            categories.forEach { category ->
                val propName = category.colorPropertyName ?: return@forEach
                vmi.getColorProperty(propName)?.let { prop ->
                    colorProperties[propName] = prop
                    val chosen = selectedColors[propName] ?: category.colorPalette?.firstOrNull()
                    if (chosen != null) {
                        selectedColors[propName] = chosen
                        prop.value = chosen
                    }
                }
            }

            categories.forEach { category ->
                if (category.inputKey == BACKGROUND_KEY) return@forEach
                updateRiveProperty(category.inputKey, selectedValues[category.inputKey] ?: 1)
            }

        } catch (e: Exception) {
            Log.e("AvatarSelection", "Error setting up Rive ViewModel: ${e.message}")
        }
    }

    private fun updateOptionsGrid(optionsRecycler: RecyclerView) {
        val category = categories[currentCategoryIndex]

        if (category.inputKey == BACKGROUND_KEY) {
            gridAndPalette.visibility = View.GONE
            swatchRecycler.visibility = View.VISIBLE
            setupSwatches()
            return
        }

        gridAndPalette.visibility = View.VISIBLE
        swatchRecycler.visibility = View.GONE

        val selectedValue = selectedValues[category.inputKey] ?: 1
        optionsRecycler.adapter = OptionsAdapter(
            category.optionsCount, selectedValue, category.optionDrawables
        ) { number ->
            selectedValues[category.inputKey] = number
            updateRiveProperty(category.inputKey, number)
        }

        val propName = category.colorPropertyName
        val palette = category.colorPalette
        if (propName != null && palette != null) {
            partColorRecycler.visibility = View.VISIBLE
            if (partColorRecycler.layoutManager == null) {
                partColorRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            }
            val current = selectedColors[propName] ?: palette.first()
            partColorRecycler.adapter = PartColorAdapter(palette, current) { color ->
                selectedColors[propName] = color
                colorProperties[propName]?.value = color
            }
        } else {
            partColorRecycler.visibility = View.GONE
        }
    }

    private fun setupSwatches() {
        if (swatchRecycler.layoutManager == null) {
            swatchRecycler.layoutManager = GridLayoutManager(this, 4)
        }
        val existing = swatchRecycler.adapter as? SwatchAdapter
        if (existing == null) {
            swatchRecycler.adapter = SwatchAdapter(backgroundColors, selectedBackgroundColor) { color ->
                selectedBackgroundColor = color
                backgroundColorProperty?.value = color
            }
        } else {
            existing.updateSelected(selectedBackgroundColor)
        }
    }

    private fun updateRiveProperty(key: String, number: Int) {
        numberProperties[key]?.value = number.toFloat()
        if (key == "hat") {
            hatBooleanProperty?.value = number > 1
        }
    }

    private fun loadSavedAvatarConfig(optionsRecycler: RecyclerView) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val config = snapshot.get("avatarConfig") as? Map<*, *>

                categories.forEach { category ->
                    if (category.inputKey == BACKGROUND_KEY) return@forEach
                    val key = category.inputKey
                    val rawVal = (config?.get(key) as? Number)?.toInt() ?: 1
                    val savedVal = rawVal.coerceIn(1, category.optionsCount)
                    selectedValues[key] = savedVal
                    updateRiveProperty(key, savedVal)
                }

                (config?.get("backgroundColor") as? String)?.let { hex ->
                    runCatching { Color.parseColor(hex) }.getOrNull()?.let {
                        selectedBackgroundColor = it
                        backgroundColorProperty?.value = it
                    }
                }

                categories.forEach { category ->
                    val propName = category.colorPropertyName ?: return@forEach
                    val hex = config?.get(propName) as? String
                    val color = hex?.let { runCatching { Color.parseColor(it) }.getOrNull() }
                        ?: category.colorPalette?.firstOrNull()
                    if (color != null) {
                        selectedColors[propName] = color
                        colorProperties[propName]?.value = color
                    }
                }

                updateOptionsGrid(optionsRecycler)
            }
            .addOnFailureListener {
                categories.forEach { category ->
                    if (category.inputKey == BACKGROUND_KEY) return@forEach
                    updateRiveProperty(category.inputKey, 1)
                }
                updateOptionsGrid(optionsRecycler)
            }
    }

    override fun onResume() {
        super.onResume()
        riveView.play()
    }

    override fun onPause() {
        super.onPause()
        riveView.pause()
    }

    data class AvatarCategory(
        val name: String,
        val inputKey: String,
        val optionsCount: Int,
        @DrawableRes val selectedIcon: Int,
        @DrawableRes val unselectedIcon: Int,
        val optionDrawables: List<Int>? = null,
        val colorPropertyName: String? = null,
        val colorPalette: List<Int>? = null
    )

    // Category Tabs Icon Adapter
    private inner class CategoryAdapter(
        private val list: List<AvatarCategory>,
        private var selectedPos: Int,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        private val cellWidth = (96 * resources.displayMetrics.density).toInt()

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val iconView: ImageView = v.findViewById(R.id.category_icon)
            val indicatorView: View = v.findViewById(R.id.category_indicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_tab, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            val isSelected = (position == selectedPos)

            holder.itemView.layoutParams = holder.itemView.layoutParams.apply { width = cellWidth }

            holder.iconView.setImageResource(if (isSelected) item.selectedIcon else item.unselectedIcon)

            holder.indicatorView.setBackgroundColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    if (isSelected) R.color.blue_2 else android.R.color.transparent
                )
            )

            holder.itemView.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION && pos != selectedPos) {
                    val prev = selectedPos
                    selectedPos = pos
                    notifyItemChanged(prev)
                    notifyItemChanged(selectedPos)
                    onSelect(selectedPos)
                }
            }
        }

        override fun getItemCount(): Int = list.size
    }

    // Options Cube Grid Adapter
    private inner class OptionsAdapter(
        private val count: Int,
        private var selectedValue: Int,
        private val optionDrawables: List<Int>?,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<OptionsAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val container: FrameLayout = v.findViewById(R.id.cube_container)
            val numberText: TextView = v.findViewById(R.id.cube_number_text)
            val image: ImageView = v.findViewById(R.id.cube_image)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_option_cube, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val optionValue = position + 1
            val isSelected = (optionValue == selectedValue)

            val drawable = optionDrawables?.getOrNull(position)
            if (drawable != null) {
                holder.image.visibility = View.VISIBLE
                holder.numberText.visibility = View.GONE
                holder.image.setImageResource(drawable)
            } else {
                holder.image.visibility = View.GONE
                holder.numberText.visibility = View.VISIBLE
                holder.numberText.text = optionValue.toString()
            }

            holder.container.setBackgroundResource(
                if (isSelected) R.drawable.bg_cube_selected else R.drawable.bg_cube_unselected
            )

            holder.itemView.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val clickedValue = pos + 1
                    if (clickedValue != selectedValue) {
                        val prevValue = selectedValue
                        selectedValue = clickedValue
                        notifyItemChanged(prevValue - 1)
                        notifyItemChanged(selectedValue - 1)
                        onSelect(selectedValue)
                    }
                }
            }
        }

        override fun getItemCount(): Int = count
    }

    // Background Color Swatch Adapter (grid)
    private inner class SwatchAdapter(
        private val colors: List<Int>,
        private var selectedColor: Int,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<SwatchAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ring: View = v.findViewById(R.id.swatch_ring)
            val circle: View = v.findViewById(R.id.swatch_color)
        }

        fun updateSelected(color: Int) {
            selectedColor = color
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_color_swatch, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val color = colors[position]
            val bg = holder.circle.background
            if (bg is GradientDrawable) { bg.mutate(); (bg as GradientDrawable).setColor(color) }
            else { bg?.mutate(); bg?.setTint(color) }

            holder.ring.visibility = if (color == selectedColor) View.VISIBLE else View.INVISIBLE

            holder.itemView.setOnClickListener {
                val prev = colors.indexOf(selectedColor)
                selectedColor = color
                if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
                notifyItemChanged(position)
                onSelect(color)
            }
        }

        override fun getItemCount(): Int = colors.size
    }

    // Per-part Color Strip Adapter (horizontal)
    private inner class PartColorAdapter(
        private val colors: List<Int>,
        private var selectedColor: Int,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<PartColorAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val ring: View = v.findViewById(R.id.swatch_ring)
            val circle: View = v.findViewById(R.id.swatch_color)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_part_color, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val color = colors[position]
            val bg = holder.circle.background
            if (bg is GradientDrawable) { bg.mutate(); (bg as GradientDrawable).setColor(color) }
            else { bg?.mutate(); bg?.setTint(color) }

            holder.ring.visibility = if (color == selectedColor) View.VISIBLE else View.INVISIBLE

            holder.itemView.setOnClickListener {
                val prev = colors.indexOf(selectedColor)
                selectedColor = color
                if (prev != RecyclerView.NO_POSITION) notifyItemChanged(prev)
                notifyItemChanged(position)
                onSelect(color)
            }
        }

        override fun getItemCount(): Int = colors.size
    }
}