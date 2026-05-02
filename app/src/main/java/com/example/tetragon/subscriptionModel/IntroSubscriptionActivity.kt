package com.example.tetragon.subscriptionModel

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.tetragon.R
import com.example.tetragon.utils.languageChangeUtils.BaseActivity

class IntroSubscriptionActivity : BaseActivity() {
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
        val tvLoading = findViewById<TextView>(R.id.subscribe_disabled_btn)

        // --- FORCE TRANSLATION APPLY ---
        // Sometimes XML doesn't update immediately if the view is inside nested containers
        btnSubscribe.text = getString(R.string.subscribe_for_1_33)
        tvLoading.text = getString(R.string.loading_caps)

        // --- Handle Exit Button Click ---
        btnClose.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // --- Handle Subscribe Button Click ---
        btnSubscribe.setOnClickListener {
            enabledContainer.visibility = View.INVISIBLE
            disabledContainer.visibility = View.VISIBLE

            val intent = Intent(this, SubscriptionModelActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset state and re-apply text to ensure it's translated
        val enabledContainer = findViewById<FrameLayout>(R.id.subscribe_enabled_btn_container)
        val disabledContainer = findViewById<FrameLayout>(R.id.subscribe_disabled_btn_container)
        val btnSubscribe = findViewById<Button>(R.id.subscribe_enabled_btn)

        enabledContainer.visibility = View.VISIBLE
        disabledContainer.visibility = View.INVISIBLE
        btnSubscribe.text = getString(R.string.subscribe_for_1_33)
    }
}