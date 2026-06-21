package com.tetragon.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.databinding.ActivityWelcomeBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import kotlinx.coroutines.launch

class WelcomeActivity : BaseActivity() {
    private lateinit var binding: ActivityWelcomeBinding
    private var currentLanguageCode: String? = null

    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Rive.init(this)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Capture language state from the global application context configuration
        currentLanguageCode = applicationContext.resources.configuration.locales[0]?.language

        observeConnectivity()

        // --- RIVE ANIMATION INITIAL FIRE STATE ON SCREEN OPEN ---
        binding.correctMrSquare.visibility = View.VISIBLE
        binding.correctMrSquare.fireState("State Machine 1", "play")

        binding.startEnabledBtn.setOnClickListener {
            showLoadingState(isStart = true)
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        binding.loginEnabledBtn.setOnClickListener {
            showLoadingState(isStart = false)
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    override fun onResume() {
        super.onResume()

        // Check if the locale config was updated globally while this activity was hidden
        val dynamicLanguageCode = applicationContext.resources.configuration.locales[0]?.language
        if (currentLanguageCode != null && currentLanguageCode != dynamicLanguageCode) {
            // Recreate the activity window cleanly to instantly rebind updated localized assets
            finish()
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            return
        }

        // Resume Rive animation playback if returning to an active internet state
        if (viewModel.isConnected.value == true) {
            binding.welcomeRiveView.play()
        }

        setupUI()
        enableButtons(viewModel.isConnected.value == true)
    }

    override fun onPause() {
        super.onPause()
        // Pausing the state machine runtime when user leaves the screen
        binding.welcomeRiveView.pause()
    }

    private fun setupUI() {
        binding.tetragonText.text = getString(R.string.learn_natural_sciences)
        binding.tetragonDescriptionText.text = getString(R.string.transform_the_learning_process_into_an_interactive_and_fun_experience)
        binding.offlineText.text = getString(R.string.you_are_offline)

        if (viewModel.isConnected.value == true) {
            binding.startEnabledBtn.text = getString(R.string.start_text)
            binding.loginEnabledBtn.text = getString(R.string.login_text)
        } else {
            binding.startDisabledBtn.text = getString(R.string.start_text)
            binding.loginDisabledBtn.text = getString(R.string.login_text)
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

                        binding.welcomeScreenCard.visibility = View.VISIBLE
                        binding.welcomeRiveView.visibility = View.VISIBLE
                        binding.correctMrSquare.visibility = View.VISIBLE
                        binding.textContentWrapper.visibility = View.VISIBLE
                        enableButtons(true)

                        binding.welcomeRiveView.play()
                        binding.correctMrSquare.fireState("State Machine 1", "play")
                    } else {
                        binding.internetConnection.visibility = View.VISIBLE
                        binding.offlineContainer.visibility = View.VISIBLE

                        binding.welcomeScreenCard.visibility = View.GONE
                        binding.welcomeRiveView.visibility = View.GONE
                        binding.correctMrSquare.visibility = View.GONE
                        binding.textContentWrapper.visibility = View.GONE
                        enableButtons(false)

                        binding.welcomeRiveView.pause()
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
            binding.loginEnabledBtnContainer.visibility = View.GONE
            binding.loginDisabledBtnContainer.visibility = View.VISIBLE
        }
        setupUI()
    }

    private fun showLoadingState(isStart: Boolean) {
        binding.startEnabledBtnContainer.visibility = View.GONE
        binding.loginEnabledBtnContainer.visibility = View.GONE
        binding.startDisabledBtnContainer.visibility = View.VISIBLE
        binding.loginDisabledBtnContainer.visibility = View.VISIBLE

        if (isStart) {
            binding.startDisabledBtn.text = getString(R.string.loading_caps)
            binding.loginDisabledBtn.text = getString(R.string.login_text)
        } else {
            binding.loginDisabledBtn.text = getString(R.string.loading_caps)
            binding.startDisabledBtn.text = getString(R.string.start_text)
        }
    }
}