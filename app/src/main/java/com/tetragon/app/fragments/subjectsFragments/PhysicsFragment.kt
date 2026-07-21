package com.tetragon.app.fragments.subjectsFragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.tetragon.app.MainActivity
import com.tetragon.app.R

class PhysicsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_physics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup listeners for visible physics grade buttons
        setupGradeButton(view.findViewById(R.id.class_btn_7), 7)

        /* UNCOMMENT THIS BLOCK TO RESTORE GRADES 8-11
        setupGradeButton(view.findViewById(R.id.class_btn_8), 8)
        setupGradeButton(view.findViewById(R.id.class_btn_9), 9)
        setupGradeButton(view.findViewById(R.id.class_btn_10), 10)
        setupGradeButton(view.findViewById(R.id.class_btn_11), 11)
        */
    }

    /**
     * Helper function to reduce repetitive code for grade navigation.
     * Mirrors the logic used in MathFragment.
     */
    private fun setupGradeButton(button: FrameLayout?, grade: Int) {
        button?.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", grade)
                putExtra("SELECTED_SUBJECT", "PHYSICS")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)

            // Standard transition for choosing a grade
            (requireActivity() as? Activity)?.overridePendingTransition(
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }
    }
}