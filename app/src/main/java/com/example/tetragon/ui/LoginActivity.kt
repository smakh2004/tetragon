package com.example.tetragon.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tetragon.MainActivity
import com.example.tetragon.utils.registrationUtils.PasswordToggleHelper
import com.example.tetragon.R
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.databinding.ActivityLoginBinding
import com.example.tetragon.utils.registrationUtils.DeviceUtils
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    private val db = FirebaseFirestore.getInstance()
    private var isLoginInProgress = false

    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ConnectivityViewModel::class.java)) {
                    return ConnectivityViewModel(
                        AndroidConnectivityObserver(applicationContext)
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { // API 27+
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white) // optional: set nav bar color
        }

        observeConnectivity()

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkFields()
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        binding.emailEditText.addTextChangedListener(textWatcher)
        binding.passwordEditText.addTextChangedListener(textWatcher)


        // Set up password eye toggle functionality
        PasswordToggleHelper.attach(
            binding.passwordEditText,
            R.drawable.ic_eye_open,
            R.drawable.ic_eye_closed
        )

        binding.exitBtn.setOnClickListener {
            startActivity(Intent(this, WelcomeActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        binding.continueEnabledBtn.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString().trim()

            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, getString(R.string.enter_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (TextUtils.isEmpty(password)) {
                Toast.makeText(this, getString(R.string.enter_password), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Start login
            isLoginInProgress = true
            binding.continueEnabledBtnContainer.visibility = View.INVISIBLE
            binding.continueDisabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtn.text = getString(R.string.loading_caps) // Translated

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    isLoginInProgress = false

                    if (task.isSuccessful) {
                        val uid = auth.currentUser!!.uid
                        val deviceId = DeviceUtils.getDeviceId(this)

                        db.collection("users")
                            .document(uid)
                            .update("activeDeviceId", deviceId)
                            .addOnSuccessListener {
                                Toast.makeText(this, getString(R.string.login_successful), Toast.LENGTH_SHORT).show() // Translated
                                startActivity(Intent(this, MainActivity::class.java))
                                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                                finish()
                            }
                    } else {
                        binding.continueEnabledBtnContainer.visibility = View.VISIBLE
                        binding.continueDisabledBtnContainer.visibility = View.INVISIBLE
                        binding.continueDisabledBtn.text = getString(R.string.continue_text) // Translated
                        Toast.makeText(this, getString(R.string.auth_failed), Toast.LENGTH_SHORT).show() // Translated
                    }
                }
        }
    }

    private fun checkFields() {
        if (isLoginInProgress) return // prevent enabling button during login

        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        val isValid = email.isNotEmpty() && password.isNotEmpty()

        if (isValid) {
            binding.continueEnabledBtnContainer.visibility = View.VISIBLE
            binding.continueDisabledBtnContainer.visibility = View.INVISIBLE
        } else {
            binding.continueEnabledBtnContainer.visibility = View.INVISIBLE
            binding.continueDisabledBtnContainer.visibility = View.VISIBLE
        }
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->

                    // Show/hide offline banner
                    binding.internetConnection.visibility = if (isConnected) View.GONE else View.VISIBLE

                    // Enable/disable all interactive elements
                    binding.emailEditText.isEnabled = isConnected
                    binding.passwordEditText.isEnabled = isConnected
                    binding.continueEnabledBtn.isEnabled = isConnected

                    // Adjust continue button UI
                    if (isConnected) {
                        checkFields()
                    } else {
                        binding.continueEnabledBtnContainer.visibility = View.INVISIBLE
                        binding.continueDisabledBtnContainer.visibility = View.VISIBLE
                        binding.continueDisabledBtn.text = getString(R.string.continue_text)
                    }
                }
            }
        }
    }
}