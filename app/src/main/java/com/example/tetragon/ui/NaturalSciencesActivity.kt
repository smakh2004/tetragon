package com.example.tetragon.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tetragon.R
import com.example.tetragon.fragments.subjectsFragments.MathFragment
import com.example.tetragon.fragments.subjectsFragments.PhysicsFragment
import com.example.tetragon.gameModel.GradeManager
import com.example.tetragon.utils.languageChangeUtils.BaseActivity

class NaturalSciencesActivity : BaseActivity() {

    // Define colors (Match these to your colors.xml if possible)
    private val activeColor = Color.parseColor("#21CBF3")
    private val inactiveColor = Color.parseColor("#C4C4C4")
    private val inactiveBar = Color.parseColor("#F0F0F0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_natural_sciences)

        val tabMath = findViewById<LinearLayout>(R.id.tabMath)
        val tabPhysics = findViewById<LinearLayout>(R.id.tabPhysics)
        val btnClose = findViewById<ImageView>(R.id.exit_btn)

        // --- NEW LOGIC: Load the last saved subject ---
        val lastSubject = GradeManager.getSubject(this)
        val isMath = lastSubject == "MATH"

        // Set the selection based on saved data
        selectTab(isMath)

        tabMath.setOnClickListener {
            GradeManager.saveChoice(this, GradeManager.getGrade(this), "MATH")
            selectTab(isMath = true)
        }

        tabPhysics.setOnClickListener {
            GradeManager.saveChoice(this, GradeManager.getGrade(this), "PHYSICS")
            selectTab(isMath = false)
        }

        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun selectTab(isMath: Boolean) {
        // --- 1. Update Math UI ---
        val imgMath = findViewById<ImageView>(R.id.imgMath)
        val txtMath = findViewById<TextView>(R.id.textMath)
        val indMath = findViewById<View>(R.id.indicatorMath)

        imgMath.setImageResource(if (isMath) R.drawable.ic_math_selected else R.drawable.ic_math_unselected)
        txtMath.setTextColor(if (isMath) activeColor else inactiveColor)
        indMath.setBackgroundColor(if (isMath) activeColor else inactiveBar)

        // --- 2. Update Physics UI ---
        val imgPhysics = findViewById<ImageView>(R.id.imgPhysics)
        val txtPhysics = findViewById<TextView>(R.id.textPhysics)
        val indPhysics = findViewById<View>(R.id.indicatorPhysics)

        imgPhysics.setImageResource(if (!isMath) R.drawable.ic_physics_selected else R.drawable.ic_physics_unselected)
        txtPhysics.setTextColor(if (!isMath) activeColor else inactiveColor)
        indPhysics.setBackgroundColor(if (!isMath) activeColor else inactiveBar)

        // --- 3. Fragment Transaction ---
        val fragment: Fragment = if (isMath) MathFragment() else PhysicsFragment()

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            // Optional: adds a smooth fade transition
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .commit()
    }
}