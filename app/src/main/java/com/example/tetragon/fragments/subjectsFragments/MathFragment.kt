package com.example.tetragon.fragments.subjectsFragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.tetragon.MainActivity
import com.example.tetragon.R

class MathFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_math, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup listeners for all grade buttons
        setupGradeButton(view.findViewById(R.id.class_btn_1), 1)
        setupGradeButton(view.findViewById(R.id.class_btn_2), 2)
        setupGradeButton(view.findViewById(R.id.class_btn_3), 3)
        setupGradeButton(view.findViewById(R.id.class_btn_4), 4)
        setupGradeButton(view.findViewById(R.id.class_btn_5), 5)
        setupGradeButton(view.findViewById(R.id.class_btn_6), 6)
        setupGradeButton(view.findViewById(R.id.class_btn_7), 7)
        setupGradeButton(view.findViewById(R.id.class_btn_8), 8)
        setupGradeButton(view.findViewById(R.id.class_btn_9), 9)
        setupGradeButton(view.findViewById(R.id.class_btn_10), 10)
        setupGradeButton(view.findViewById(R.id.class_btn_11), 11)
    }

    /**
     * Helper function to reduce repetitive code for grade navigation.
     */
    private fun setupGradeButton(button: FrameLayout?, grade: Int) {
        button?.setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", grade)
                putExtra("SELECTED_SUBJECT", "MATH")
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