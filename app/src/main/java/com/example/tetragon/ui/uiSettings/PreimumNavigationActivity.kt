package com.example.tetragon.ui.uiSettings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityPreimumNavigationBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity

class PreimumNavigationActivity : BaseActivity() {
    private lateinit var binding: ActivityPreimumNavigationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreimumNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Back button
        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

    }
}