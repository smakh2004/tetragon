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
class PhysicsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_physics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inside onViewCreated of PhysicsFragment
        view.findViewById<FrameLayout>(R.id.class_btn_7).setOnClickListener {
            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                putExtra("SELECTED_GRADE", 7)
                putExtra("SELECTED_SUBJECT", "PHYSICS") // Identify the subject
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            (requireActivity() as? Activity)?.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

}