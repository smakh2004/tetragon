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
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_math, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<FrameLayout>(R.id.class_btn_1).setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.putExtra("SELECTED_GRADE", 1)
            intent.putExtra("SELECTED_SUBJECT", "MATH")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            (requireActivity() as? Activity)?.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        view.findViewById<FrameLayout>(R.id.class_btn_2).setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.putExtra("SELECTED_GRADE", 2)
            intent.putExtra("SELECTED_SUBJECT", "MATH")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            (requireActivity() as? Activity)?.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        view.findViewById<FrameLayout>(R.id.class_btn_3).setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.putExtra("SELECTED_GRADE", 3)
            intent.putExtra("SELECTED_SUBJECT", "MATH")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            (requireActivity() as? Activity)?.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

}