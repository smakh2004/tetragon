package com.tetragon.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.rive.runtime.kotlin.core.Rive
import com.tetragon.app.databinding.ActivityMainBinding
import com.tetragon.app.fragments.*
import com.tetragon.app.connectivityCheck.ConnectivityViewModel
import com.tetragon.app.connectivityCheck.AndroidConnectivityObserver
import com.tetragon.app.connectivityCheck.userPresenceUtils.UserPresenceHelper
import com.tetragon.app.questions.StreakManager
import com.tetragon.app.ui.LoginActivity
import com.tetragon.app.ui.WelcomeActivity
import com.tetragon.app.utils.AppUpdateManager
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.tetragon.app.utils.registrationUtils.DeviceUtils
import com.tetragon.app.gameModel.GradeManager
import com.tetragon.app.reward.MonthlyRewardActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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
    private var updateListener: ListenerRegistration? = null
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        try {
            Rive.init(this)
        } catch (e: Exception) {
            // Context already running
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        observeConnectivity()

        binding.bottomNavigationView.itemIconTintList = null
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val currentId = binding.bottomNavigationView.selectedItemId
            if (item.itemId != currentId) {
                when (item.itemId) {
                    R.id.home -> replaceFragment(HomeFragment())
                    R.id.mini_games -> replaceFragment(MiniGamesFragment())
                    R.id.leaderboard -> replaceFragment(LeaderboardFragment())
                    R.id.profile -> replaceFragment(ProfileFragment())
                    R.id.shop -> replaceFragment(ShopFragment())
                }
            }
            true
        }
    }

    override fun onStart() {
        super.onStart()

        val currentUser = auth.currentUser

        if (currentUser == null || !currentUser.isEmailVerified) {
            auth.signOut()
            val intent = Intent(this, WelcomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
            return
        }

        checkMonthlyReset(currentUser.email)
        StreakManager.checkAndResetIfMissed()
        UserPresenceHelper.startTracking()
        startSessionListener()

        // Live-listens for manual Firestore edits to system/appConfig and
        // shows the update dialog immediately without needing a restart.
        updateListener = AppUpdateManager.attachUpdateListener(this)

        setMiniGamesVisible(true)
    }

    // --- SECURE TOTAL DB OVERWRITE LOGIC ---
    private fun checkMonthlyReset(currentUserEmail: String?) {
        if (currentUserEmail == null) return

        val monthKey = SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(Date())
        val metaRef = db.collection("system").document("leaderboard")

        metaRef.get().addOnSuccessListener { metaDoc ->
            if (!metaDoc.exists()) {
                metaRef.set(mapOf("lastMonth" to monthKey, "winnerEmails" to emptyList<String>()))
                return@addOnSuccessListener
            }

            val lastMonth = metaDoc.getString("lastMonth") ?: ""
            val savedWinners = metaDoc.get("winnerEmails") as? List<String> ?: emptyList()

            // CASE 1: The month flipped. This device forces a total data sweep across all users
            if (lastMonth != monthKey) {
                db.collection("users")
                    .orderBy("monthlyXP", Query.Direction.DESCENDING)
                    .limit(10).get().addOnSuccessListener { topSnapshot ->

                        val docs = topSnapshot.documents
                        val maxXP = docs.firstOrNull()?.getLong("monthlyXP") ?: 0L

                        val winnerEmails = if (maxXP > 0) {
                            docs.filter { it.getLong("monthlyXP") == maxXP }.mapNotNull { it.getString("email") }
                        } else {
                            emptyList()
                        }

                        // Wipe entire database system in paginated blocks before saving the confirmation metadata
                        wipeAllUsersXpStepByStep(null, monthKey) {
                            val globalUpdate = mapOf(
                                "lastMonth" to monthKey,
                                "winnerEmails" to winnerEmails
                            )

                            metaRef.set(globalUpdate).addOnSuccessListener {
                                if (winnerEmails.contains(currentUserEmail)) {
                                    claimReward(metaRef, currentUserEmail, winnerEmails)
                                }
                            }
                        }
                    }
            }
            // CASE 2: The global configurations are set, evaluate destination logic directly
            else if (savedWinners.contains(currentUserEmail)) {
                claimReward(metaRef, currentUserEmail, savedWinners)
            }
        }
    }

    // Paginated client processor sweeps database elements step-by-step
    private fun wipeAllUsersXpStepByStep(
        lastProcessedDoc: DocumentSnapshot?,
        currentMonthKey: String,
        onComplete: () -> Unit
    ) {
        var baseQuery = db.collection("users").limit(200)
        if (lastProcessedDoc != null) {
            baseQuery = baseQuery.startAfter(lastProcessedDoc)
        }

        baseQuery.get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                onComplete()
                return@addOnSuccessListener
            }

            val batch = db.batch()
            for (doc in snapshot.documents) {
                // Wipe every single profile structure fields down to 0
                batch.update(doc.reference, mapOf(
                    "monthlyXP" to 0,
                    "lastResetMonth" to currentMonthKey
                ))
            }

            batch.commit().addOnSuccessListener {
                val lastDoc = snapshot.documents.last()
                // Recurse to handle next 200 documents
                wipeAllUsersXpStepByStep(lastDoc, currentMonthKey, onComplete)
            }.addOnFailureListener {
                // Fallback escape safety structure
                onComplete()
            }
        }.addOnFailureListener {
            onComplete()
        }
    }

    private fun claimReward(metaRef: com.google.firebase.firestore.DocumentReference, email: String, currentWinners: List<String>) {
        val updatedWinners = currentWinners.toMutableList()
        updatedWinners.remove(email)

        metaRef.update("winnerEmails", updatedWinners).addOnSuccessListener {
            val intent = Intent(this, MonthlyRewardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }
    }

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
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val activeDeviceId = snapshot.getString("activeDeviceId")
                if (activeDeviceId != null && activeDeviceId != currentDeviceId) {
                    showSessionExpiredDialog()
                    return@addSnapshotListener
                }

                val isSubscribed = snapshot.getBoolean("subscription") == true
                val expiry = snapshot.getTimestamp("subscriptionUntil")
                val now = Date()

                if (isSubscribed && expiry != null && expiry.toDate().before(now)) {
                    db.collection("users").document(uid).update(
                        mapOf(
                            "subscription" to false,
                            "planType" to "free"
                        )
                    )
                }
            }
    }

    private fun showSessionExpiredDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.session_expired))
            .setMessage(getString(R.string.session_expired_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.refresh)) { _, _ ->
                auth.signOut()
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionListener?.remove()
        updateListener?.remove()
    }

    override fun onResume() {
        super.onResume()
        setMiniGamesVisible(true)
        // Re-show the update dialog if the user is still behind the current
        // version (e.g. came back from the Play Store without updating).
        AppUpdateManager.recheckAndShowIfNeeded(this)
    }
}