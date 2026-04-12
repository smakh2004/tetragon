package com.example.tetragon

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.startup.AppInitializer
import app.rive.runtime.kotlin.RiveInitializer
import app.rive.runtime.kotlin.core.Rive
import com.example.tetragon.databinding.ActivityMainBinding
import com.example.tetragon.fragments.*
import com.example.tetragon.connectivityCheck.ConnectivityViewModel
import com.example.tetragon.connectivityCheck.AndroidConnectivityObserver
import com.example.tetragon.connectivityCheck.userPresenceUtils.UserPresenceHelper
import com.example.tetragon.questions.StreakManager
import com.example.tetragon.reward.BagTapActivity
import com.example.tetragon.ui.LoginActivity
import com.example.tetragon.ui.WelcomeActivity
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.example.tetragon.utils.registrationUtils.DeviceUtils
import com.example.tetragon.gameModel.GradeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val db = FirebaseFirestore.getInstance()
    private var sessionListener: ListenerRegistration? = null
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val viewModel: ConnectivityViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ConnectivityViewModel::class.java)) {
                    return ConnectivityViewModel(AndroidConnectivityObserver(applicationContext)) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Rive.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.navigationBarColor = ContextCompat.getColor(this, R.color.white)
        }

        AppInitializer.getInstance(applicationContext).initializeComponent(RiveInitializer::class.java)

        replaceFragment(HomeFragment())
        observeConnectivity()

        binding.bottomNavigationView.itemIconTintList = null
        binding.bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.home -> replaceFragment(HomeFragment())
                R.id.mini_games -> replaceFragment(MiniGamesFragment())
                R.id.leaderboard -> replaceFragment(LeaderboardFragment())
                R.id.profile -> replaceFragment(ProfileFragment())
                R.id.shop -> replaceFragment(ShopFragment())
            }
            true
        }
    }

    override fun onStart() {
        super.onStart()

        val currentUser = auth.currentUser

        // STRICT CHECK: No user OR Unverified email
        if (currentUser == null || !currentUser.isEmailVerified) {
            auth.signOut() // Clear the session
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // If we reach here, the user is valid and verified.
        checkMonthlyReset(currentUser.email)
        StreakManager.checkAndResetIfMissed()
        UserPresenceHelper.startTracking()
        startSessionListener()

        val lastSubject = GradeManager.getSubject(this)
        setMiniGamesVisible(lastSubject != "PHYSICS")
    }

    // --- MONTHLY RESET LOGIC START ---
    private fun checkMonthlyReset(currentUserEmail: String?) {
        if (currentUserEmail == null) return

        val monthKey = SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(Date())
        val metaRef = db.collection("system").document("leaderboard")

        metaRef.get().addOnSuccessListener { metaDoc ->
            if (!metaDoc.exists()) {
                // Initialize the system document if it's missing
                metaRef.set(mapOf("lastMonth" to monthKey, "winnerEmails" to emptyList<String>()))
                return@addOnSuccessListener
            }

            val lastMonth = metaDoc.getString("lastMonth") ?: ""
            val savedWinners = metaDoc.get("winnerEmails") as? List<String> ?: emptyList()

            // CASE A: New month detected - Reset scores and identify all winners
            if (lastMonth != monthKey) {
                db.collection("users")
                    .orderBy("monthlyXP", Query.Direction.DESCENDING)
                    .limit(1).get().addOnSuccessListener { topSnapshot ->
                        val maxXP = topSnapshot.documents.firstOrNull()?.getLong("monthlyXP") ?: 0L

                        if (maxXP > 0) {
                            db.collection("users").whereEqualTo("monthlyXP", maxXP).get()
                                .addOnSuccessListener { winnersSnapshot ->
                                    val winnerEmails = winnersSnapshot.documents.mapNotNull { it.getString("email") }

                                    db.collection("users").get().addOnSuccessListener { allUsers ->
                                        val batch = db.batch()
                                        for (doc in allUsers.documents) {
                                            batch.update(doc.reference, "monthlyXP", 0)
                                        }

                                        batch.update(metaRef, "lastMonth", monthKey)
                                        batch.update(metaRef, "winnerEmails", winnerEmails)

                                        batch.commit().addOnSuccessListener {
                                            if (winnerEmails.contains(currentUserEmail)) {
                                                claimReward(metaRef, currentUserEmail, winnerEmails)
                                            }
                                        }
                                    }
                                }
                        } else {
                            // No one had XP, just update the date
                            metaRef.update("lastMonth", monthKey)
                        }
                    }
            }
            // CASE B: Reset already done by someone else, check if I am a winner
            else if (savedWinners.contains(currentUserEmail)) {
                claimReward(metaRef, currentUserEmail, savedWinners)
            }
        }
    }

    private fun claimReward(metaRef: com.google.firebase.firestore.DocumentReference, email: String, currentWinners: List<String>) {
        val updatedWinners = currentWinners.toMutableList()
        updatedWinners.remove(email)

        // Update DB first to prevent multiple reward triggers
        metaRef.update("winnerEmails", updatedWinners).addOnSuccessListener {
            val intent = Intent(this, BagTapActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish() // Close MainActivity so the winner is forced into the Reward screen
        }
    }
    // --- MONTHLY RESET LOGIC END ---

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, fragment)
            .commit()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntentExtras()
    }

    private fun processIntentExtras() {
        val grade = intent.getIntExtra("SELECTED_GRADE", -1)
        val subject = intent.getStringExtra("SELECTED_SUBJECT") ?: "MATH"

        if (grade != -1) {
            GradeManager.saveChoice(this, grade, subject)
            binding.bottomNavigationView.selectedItemId = R.id.home
            val homeFragment = HomeFragment().apply {
                arguments = Bundle().apply {
                    putInt("target_grade", grade)
                    putString("target_subject", subject)
                }
            }
            replaceFragment(homeFragment)
        }
    }

    fun setMiniGamesVisible(isVisible: Boolean) {
        val menu = binding.bottomNavigationView.menu
        val miniGamesItem = menu.findItem(R.id.mini_games)
        miniGamesItem?.isVisible = isVisible
    }

    fun setBottomNavigationEnabled(enabled: Boolean) {
        val menu = binding.bottomNavigationView.menu
        for (i in 0 until menu.size()) {
            menu.getItem(i).isEnabled = enabled
        }
    }

    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { isConnected ->
                    if (isConnected) {
                        binding.internetConnection.visibility = View.GONE
                        binding.frameLayout.visibility = View.VISIBLE
                        binding.offlineContainer.visibility = View.GONE
                    } else {
                        binding.internetConnection.visibility = View.VISIBLE
                        binding.offlineContainer.visibility = View.VISIBLE
                        binding.frameLayout.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun startSessionListener() {
        val uid = auth.currentUser?.uid ?: return
        val currentDeviceId = DeviceUtils.getDeviceId(this)

        sessionListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                val activeDeviceId = snapshot?.getString("activeDeviceId")
                if (activeDeviceId != null && activeDeviceId != currentDeviceId) {
                    showSessionExpiredDialog()
                }
            }
    }

    private fun showSessionExpiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Session Expired")
            .setMessage("Your account was opened on another device.")
            .setCancelable(false)
            .setPositiveButton("Refresh") { _, _ ->
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        val currentSubject = GradeManager.getSubject(this)
        setMiniGamesVisible(currentSubject != "PHYSICS")
    }
}