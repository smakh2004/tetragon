package com.example.tetragon.subscriptionModel

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivitySubscriptionModelBinding

class SubscriptionModelActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySubscriptionModelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        enableEdgeToEdge()
        binding = ActivitySubscriptionModelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.correctMrSquare2.visibility = View.VISIBLE
        binding.correctMrSquare2.fireState("State Machine 1", "play")

        binding.btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        binding.containerAnnual.setOnClickListener {
            // UI Logic
            binding.containerAnnual.setBackgroundResource(R.drawable.bg_subscription_selected)
            binding.containerMonthly.setBackgroundResource(R.drawable.bg_subscription_unselected)

            binding.correctMrSquare2.visibility = View.VISIBLE
            binding.correctMrSquare2.fireState("State Machine 1", "play")
            // Rive Logic: Fire "finish" when moving away from monthly
            binding.correctMrSquare.visibility = View.GONE
            binding.correctMrSquare.fireState("State Machine 1", "finish")
        }

        binding.containerMonthly.setOnClickListener {
            // UI Logic
            binding.containerAnnual.setBackgroundResource(R.drawable.bg_subscription_unselected)
            binding.containerMonthly.setBackgroundResource(R.drawable.bg_subscription_selected)

            binding.correctMrSquare2.visibility = View.GONE
            binding.correctMrSquare2.fireState("State Machine 1", "finish")
            // Rive Logic: Fire "play" when monthly is selected
            binding.correctMrSquare.visibility = View.VISIBLE
            binding.correctMrSquare.fireState("State Machine 1", "play")
        }
    }
}