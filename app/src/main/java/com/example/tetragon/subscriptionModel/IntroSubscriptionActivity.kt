package com.example.tetragon.subscriptionModel

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.tetragon.R

class IntroSubscriptionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_intro_subscription)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- View Bindings ---
        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val btnSubscribe = findViewById<Button>(R.id.subscribe_enabled_btn)
        val enabledContainer = findViewById<FrameLayout>(R.id.subscribe_enabled_btn_container)
        val disabledContainer = findViewById<FrameLayout>(R.id.subscribe_disabled_btn_container)

        // --- Handle Exit Button Click ---
        btnClose.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // --- Handle Subscribe Button Click ---
        btnSubscribe.setOnClickListener {
            // 1. Show Disabled (Loading) State
            enabledContainer.visibility = View.INVISIBLE
            disabledContainer.visibility = View.VISIBLE

            // 2. Navigate to SubscriptionModelActivity
            val intent = Intent(this, SubscriptionModelActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset the button state if the user comes back to this screen
        findViewById<FrameLayout>(R.id.subscribe_enabled_btn_container).visibility = View.VISIBLE
        findViewById<FrameLayout>(R.id.subscribe_disabled_btn_container).visibility = View.INVISIBLE
    }
}