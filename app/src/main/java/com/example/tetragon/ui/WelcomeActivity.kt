package com.example.tetragon.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.R
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.databinding.ActivityWelcomeBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import kotlinx.coroutines.launch

class WelcomeActivity : BaseActivity() {
    private lateinit var binding: ActivityWelcomeBinding

    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeConnectivity()

        binding.startEnabledBtn.setOnClickListener {
            showLoadingState(isStart = true)
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }

        binding.loginEnabledBtn.setOnClickListener {
            showLoadingState(isStart = false)
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }
    }

    private fun setupUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }
    }

    @SuppressLint("RepeatOnLifecycleWrongUsage")
    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    if (isConnected) {
                        binding.internetConnection.visibility = View.GONE
                        binding.offlineContainer.visibility = View.GONE
                        binding.welcomeMrSquare.visibility = View.VISIBLE
                        binding.tetragonText.visibility = View.VISIBLE
                        binding.tetragonDescriptionText.visibility = View.VISIBLE
                        enableButtons(true)
                    } else {
                        binding.internetConnection.visibility = View.VISIBLE
                        binding.offlineContainer.visibility = View.VISIBLE
                        binding.welcomeMrSquare.visibility = View.GONE
                        binding.tetragonText.visibility = View.GONE
                        binding.tetragonDescriptionText.visibility = View.GONE
                        enableButtons(false)
                    }
                }
            }
        }
    }

    private fun enableButtons(enable: Boolean) {
        if (enable) {
            binding.startEnabledBtnContainer.visibility = View.VISIBLE
            binding.startDisabledBtnContainer.visibility = View.GONE
            binding.loginEnabledBtnContainer.visibility = View.VISIBLE
            binding.loginDisabledBtnContainer.visibility = View.GONE
        } else {
            binding.startEnabledBtnContainer.visibility = View.GONE
            binding.startDisabledBtnContainer.visibility = View.VISIBLE
            binding.startDisabledBtn.text = "START"
            binding.loginEnabledBtnContainer.visibility = View.GONE
            binding.loginDisabledBtnContainer.visibility = View.VISIBLE
            binding.loginDisabledBtn.text = "LOGIN"
        }
    }

    private fun showLoadingState(isStart: Boolean) {
        // Disable both button sets to prevent double-clicks
        binding.startEnabledBtnContainer.visibility = View.GONE
        binding.loginEnabledBtnContainer.visibility = View.GONE
        binding.startDisabledBtnContainer.visibility = View.VISIBLE
        binding.loginDisabledBtnContainer.visibility = View.VISIBLE

        if (isStart) {
            binding.startDisabledBtn.text = "LOADING.."
            binding.loginDisabledBtn.text = "LOGIN"
        } else {
            binding.loginDisabledBtn.text = "LOADING.."
            binding.startDisabledBtn.text = "START"
        }
    }
}