package com.example.tetragon.ui.uiSettings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityEmailChangeBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth

class EmailShowActivity : BaseActivity() {

    private lateinit var binding: ActivityEmailChangeBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // Show currently authenticated user's email
        val user = auth.currentUser
        binding.email.text = user?.email ?: "No email"

    }

}
