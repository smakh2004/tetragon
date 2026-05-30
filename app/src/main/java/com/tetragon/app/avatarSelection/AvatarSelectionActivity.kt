package com.tetragon.app.avatarSelection

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.R
import com.tetragon.app.utils.languageChangeUtils.BaseActivity

class AvatarSelectionActivity : BaseActivity() {
    private var selectedAvatarName: String? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 1. Permanent tester emails (Strict exclusive access to "avatar_testers")
    private val avatarTesters = listOf(
        "mirmuminovshaxzod@gmail.com",
        "bexruznasrullaev7787@gmail.com",
        "tvmedia07@gmail.com",
        "hafizibrohim2002@gmail.com",
        "mail@ziyodov.uz",
        "smakh04@bk.ru",
    )

    // 2. HARDCODED SPECIAL USERS LIST (Add future emails here manually)
    private val specialUsers = listOf(
        "future_user1@gmail.com",
        "another_special_user@gmail.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_avatar_selection)

        // UI Components
        val recycler = findViewById<RecyclerView>(R.id.avatar_recycler_view)
        val enabledBtnContainer = findViewById<FrameLayout>(R.id.continue_enabled_btn_container)
        val disabledBtnContainer = findViewById<FrameLayout>(R.id.continue_disabled_btn_container)
        val saveBtn = findViewById<Button>(R.id.continue_enabled_btn)
        val disabledBtnText = findViewById<TextView>(R.id.continue_disabled_btn)
        val topBarLowerDivider = findViewById<View>(R.id.top_bar_lower_divider)
        val bottomDivider = findViewById<View>(R.id.bottom_divider)
        val loadingOverlay = findViewById<FrameLayout>(R.id.loadingOverlayContainer)

        // Find standard default avatar drawables
        val defaultAvatarIds = mutableListOf<Int>()
        var i = 1
        while (true) {
            val resId = resources.getIdentifier("avatar_$i", "drawable", packageName)
            if (resId == 0) break
            defaultAvatarIds.add(resId)
            i++
        }

        // Setup Layout Manager
        val gridLayoutManager = GridLayoutManager(this, 3)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val adapter = recycler.adapter as? AvatarAdapter ?: return 1
                return if (adapter.isHeaderPosition(position)) 3 else 1
            }
        }
        recycler.layoutManager = gridLayoutManager
        recycler.itemAnimator = null

        // Get sanitized user email
        val userEmail = auth.currentUser?.email?.lowercase()?.trim() ?: ""

        // Prepare lists for special items
        val specialAvatarIds = mutableListOf<Int>()
        val specialAvatarTypes = mutableListOf<AvatarAdapter.AvatarType>()

        // Check permanent tester condition locally
        val isTester = avatarTesters.contains(userEmail)
        if (isTester) {
            val testerResId = resources.getIdentifier("avatar_testers", "drawable", packageName)
            if (testerResId != 0) {
                specialAvatarIds.add(testerResId)
                specialAvatarTypes.add(AvatarAdapter.AvatarType.TESTER)
            }
        }

        // Check standard hardcoded special user condition locally (NO DATABASE CHECK)
        val isSpecialUser = specialUsers.contains(userEmail)
        if (isSpecialUser) {
            val specialResId = resources.getIdentifier("avatar_special", "drawable", packageName)
            if (specialResId != 0) {
                specialAvatarIds.add(specialResId)
                specialAvatarTypes.add(AvatarAdapter.AvatarType.FUTURE_SPECIAL)
            }
        }

        // Initialize adapter synchronously with locally matched data structures
        initAdapter(recycler, defaultAvatarIds, specialAvatarIds, specialAvatarTypes, enabledBtnContainer, disabledBtnContainer)
        updateBottomDividerVisibility(recycler, bottomDivider)

        // Hide loading overlay instantly since processing is complete
        loadingOverlay.visibility = View.GONE

        // --- SCROLL LOGIC FOR DYNAMIC DIVIDER VISIBILITY ---
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                topBarLowerDivider.visibility = if (recyclerView.computeVerticalScrollOffset() > 0) View.VISIBLE else View.INVISIBLE
                bottomDivider.visibility = if (recyclerView.canScrollVertically(1)) View.VISIBLE else View.INVISIBLE
            }
        })

        // Save Action
        saveBtn.setOnClickListener {
            selectedAvatarName?.let { name ->
                enabledBtnContainer.visibility = View.GONE
                disabledBtnContainer.visibility = View.VISIBLE
                disabledBtnText.text = getString(R.string.saving_caps)

                db.collection("users").document(auth.currentUser?.uid ?: "")
                    .update("avatarName", name)
                    .addOnSuccessListener { finish() }
                    .addOnFailureListener {
                        disabledBtnText.text = getString(R.string.save)
                        disabledBtnContainer.visibility = View.GONE
                        enabledBtnContainer.visibility = View.VISIBLE
                    }
            }
        }

        findViewById<ImageView>(R.id.close_btn).setOnClickListener { finish() }
    }

    private fun initAdapter(
        recycler: RecyclerView,
        defaultIds: List<Int>,
        specialIds: List<Int>,
        specialTypes: List<AvatarAdapter.AvatarType>,
        enabledBtn: FrameLayout,
        disabledBtn: FrameLayout
    ) {
        recycler.adapter = AvatarAdapter(defaultIds, specialIds, specialTypes) { avatarType, index ->
            selectedAvatarName = when (avatarType) {
                AvatarAdapter.AvatarType.DEFAULT -> "avatar_${index + 1}"
                AvatarAdapter.AvatarType.TESTER -> "avatar_testers"
                AvatarAdapter.AvatarType.FUTURE_SPECIAL -> "avatar_special"
            }

            disabledBtn.visibility = View.GONE
            enabledBtn.visibility = View.VISIBLE
        }
    }

    private fun updateBottomDividerVisibility(recycler: RecyclerView, bottomDivider: View) {
        recycler.post {
            if (!isFinishing) {
                bottomDivider.visibility = if (recycler.canScrollVertically(1)) View.VISIBLE else View.INVISIBLE
            }
        }
    }
}