package com.tetragon.app.fragments.registrationFragments

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.databinding.FragmentNotificationPermissionBinding
import com.tetragon.app.notificationsLogic.NotificationHelper
import com.tetragon.app.ui.RegisterActivity

class NotificationPermissionFragment : Fragment() {

    private var _binding: FragmentNotificationPermissionBinding? = null
    private val binding get() = _binding!!

    // Prevents double taps / double navigation if the user hits Allow/Don't Allow twice fast,
    // or if the system permission callback fires after a UI click already advanced the flow.
    private var choiceAlreadyHandled = false

    // System Permission Launcher for Android 13+ (Mirrors SettingsActivity logic exactly)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Fragment may have been detached by the time the system dialog result comes back
        // (e.g. user backgrounded the app or navigated away). Guard against crashing on
        // requireContext()/Toast calls in that case.
        if (!isAdded) return@registerForActivityResult

        if (isGranted) {
            updateNotificationState(true)
        } else {
            updateNotificationState(false)
            Toast.makeText(requireContext(), getString(R.string.notif_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        choiceAlreadyHandled = false

        // Keep the host activity's global Continue button DISABLED on this step.
        // This screen must be advanced via the Allow / Don't Allow buttons, which run the
        // permission logic and persist the choice. Enabling the global Continue button here
        // let the user skip straight past that logic (no permission requested, preference
        // never saved), so we deliberately leave it disabled.
        (activity as? RegisterActivity)?.setContinueButtonEnabled(false)

        // Maps your custom layout dialog buttons to permission logic actions
        binding.dialogAllowBtn.setOnClickListener {
            checkAndRequestNotificationPermission()
        }

        binding.dialogDonotAllowBtn.setOnClickListener {
            updateNotificationState(false)
        }

        val bounce = AnimationUtils.loadAnimation(requireContext(), R.anim.bounce_arrow)
        binding.arrowPointer.startAnimation(bounce)
    }

    private fun checkAndRequestNotificationPermission() {
        if (!isAdded) return
        val context = requireContext()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                updateNotificationState(true)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            updateNotificationState(true)
        }
    }

    private fun updateNotificationState(enabled: Boolean) {
        // Block re-entry: this function can be reached from a button tap AND from the
        // async permission-result callback. Without this guard both could fire and try
        // to advance the registration flow twice.
        if (choiceAlreadyHandled) return
        choiceAlreadyHandled = true

        if (!isAdded) return
        val context = requireContext()

        val sharedPrefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

        // Persist local selection preference state to SharedPreferences
        sharedPrefs.edit().putBoolean("notifications_enabled", enabled).apply()

        val parentActivity = activity as? RegisterActivity

        if (enabled) {
            safelyScheduleNotifications(context)
            parentActivity?.fireMrSquareAnimation("yahoo")
        } else {
            safelyCancelNotifications(context)
        }

        // Advance the registration flow via a direct, safe call instead of simulating a
        // button click on a view that might be hidden or (if an id ever changes) missing.
        parentActivity?.advanceToNextStep()
    }

    /**
     * Wraps notification scheduling so a platform-level failure (e.g. exact-alarm
     * restrictions on Android 12+, or a misbehaving NotificationHelper) can never crash
     * registration. Falls back to skipping the schedule rather than taking down the app.
     */
    private fun safelyScheduleNotifications(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                    // No permission for exact alarms on Android 12+. Still let
                    // NotificationHelper run in case it internally falls back to an
                    // inexact schedule; if it throws, we catch it below.
                }
            }
            NotificationHelper.scheduleDailyNotification(context)
        } catch (e: SecurityException) {
            // Missing SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM on this device/OS version.
            // Fail silently rather than crashing registration over a non-critical feature.
        } catch (e: Exception) {
            // Any other unexpected failure from NotificationHelper — never let it crash flow.
        }
    }

    private fun safelyCancelNotifications(context: Context) {
        try {
            NotificationHelper.cancelNotifications(context)
        } catch (e: Exception) {
            // Non-critical — ignore.
        }
    }

    override fun onDestroyView() {
        _binding?.arrowPointer?.clearAnimation()
        super.onDestroyView()
        _binding = null
    }
}