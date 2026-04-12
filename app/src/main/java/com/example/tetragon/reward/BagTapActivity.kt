package com.example.tetragon.reward

import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.MainActivity
import com.example.tetragon.R
import com.example.tetragon.utils.languageChangeUtils.BaseActivity

class BagTapActivity : BaseActivity() {

    private lateinit var riveBag: RiveAnimationView
    private lateinit var disabledContainer: FrameLayout
    private lateinit var enabledContainer: FrameLayout
    private lateinit var continueBtn: Button

    private var openSound: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Rive audio engine
        Rive.init(this)

        setContentView(R.layout.activity_bag_tap)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        // Initialize Views
        riveBag = findViewById(R.id.bag)
        disabledContainer = findViewById(R.id.continue_disabled_btn_container)
        enabledContainer = findViewById(R.id.continue_enabled_btn_container)
        continueBtn = findViewById(R.id.continue_enabled_btn)

        // Initialize sound
        openSound = MediaPlayer.create(this, R.raw.bag_opened)

        // Button starts disabled
        disabledContainer.visibility = View.VISIBLE
        enabledContainer.visibility = View.INVISIBLE

        setupRiveListener()

        continueBtn.setOnClickListener {
            // 1. Create intent to go to MainActivity
            val intent = Intent(this, MainActivity::class.java)

            // 2. Clear the backstack so the user can't "Go Back" to the reward screen
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(intent)

            // 3. Optional: Custom transition
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)

            // 4. Close this activity
            finish()
        }
    }

    override fun onResume() {
        super.onResume()

        // Reset and reload Rive so sound event fires again
        loadRive()
    }

    private fun loadRive() {
        riveBag.reset()

        riveBag.setRiveResource(
            R.raw.bag,
            stateMachineName = "State Machine 1",
            autoplay = true
        )
    }

    private fun setupRiveListener() {
        riveBag.registerListener(object : RiveFileController.Listener {

            override fun notifyStateChanged(stateMachineName: String, stateName: String) {

                if (stateName == "Open") {

                    // Play bag opening sound
                    openSound?.seekTo(0)
                    openSound?.start()

                    startButtonTimer()
                }
            }

            override fun notifyPlay(animation: PlayableInstance) {}

            override fun notifyLoop(animation: PlayableInstance) {}

            override fun notifyPause(animation: PlayableInstance) {}

            override fun notifyStop(animation: PlayableInstance) {}
        })
    }

    private fun startButtonTimer() {
        Handler(Looper.getMainLooper()).postDelayed({

            if (!isFinishing) {
                disabledContainer.visibility = View.INVISIBLE
                enabledContainer.visibility = View.VISIBLE
            }

        }, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()

        openSound?.release()
        openSound = null
    }
}