package com.example.tetragon.ui.uiSettings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityAccountBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity

class AccountActivity : BaseActivity() {
    private lateinit var binding: ActivityAccountBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.name.setOnClickListener {
            startActivity(Intent(this, NameChangeActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.age.setOnClickListener {
            startActivity(Intent(this, AgeShowActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.password.setOnClickListener {
            startActivity(Intent(this, PasswordChangeActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.emailAddress.setOnClickListener {
            startActivity(Intent(this, EmailShowActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.deleteAccount.setOnClickListener {
            startActivity(Intent(this, DeleteAccountActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

    }
}