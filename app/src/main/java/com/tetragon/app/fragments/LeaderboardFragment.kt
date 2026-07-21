package com.tetragon.app.fragments

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tetragon.app.R
import com.tetragon.app.utils.leaderboardUtils.LeaderboardAdapter
import com.tetragon.app.utils.leaderboardUtils.LeaderboardUser
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tetragon.app.otherProfile.OtherUserProfileActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class LeaderboardFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var positionText: TextView
    private lateinit var monthText: TextView
    private lateinit var daysText: TextView

    private lateinit var headerTextContainer: LinearLayout
    private lateinit var leaderboardImage: ImageView
    private lateinit var leaderboardCard: MaterialCardView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private val db = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().getReference("status")

    private val usersList = mutableListOf<LeaderboardUser>()
    private var leaderboardAdapter: LeaderboardAdapter? = null
    private var leaderboardListener: ListenerRegistration? = null

    private var activeCountdownTimer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_leaderboard, container, false)

        recycler = view.findViewById(R.id.leaderboardRecycler)
        positionText = view.findViewById(R.id.positionText)
        monthText = view.findViewById(R.id.monthText)
        daysText = view.findViewById(R.id.daysText)

        headerTextContainer = view.findViewById(R.id.headerTextContainer)
        leaderboardImage = view.findViewById(R.id.leaderboardImage)
        leaderboardCard = view.findViewById(R.id.leaderboardCard)
        loadingLayout = view.findViewById(R.id.loadingLayout)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)

        recycler.layoutManager = LinearLayoutManager(requireContext())

        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        leaderboardAdapter = LeaderboardAdapter(usersList, currentUserEmail) { selectedUser ->
            val intent = android.content.Intent(requireContext(), OtherUserProfileActivity::class.java)
            val explicitRank = usersList.indexOf(selectedUser) + 1
            intent.putExtra("USER_DATA", selectedUser)
            intent.putExtra("USER_RANK", explicitRank)
            startActivity(intent)
        }
        recycler.adapter = leaderboardAdapter

        context?.let { ctx ->
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(ctx, R.color.blue_2))
        }

        swipeRefreshLayout.setOnRefreshListener {
            updateMonthUI()
            loadLeaderboard()
        }

        updateMonthUI()
        loadLeaderboard()

        return view
    }

    private fun updateMonthUI() {
        val calendar = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val monthName = monthFormat.format(calendar.time).uppercase()
        monthText.text = monthName

        activeCountdownTimer?.cancel()

        val targetCalendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val msLeft = targetCalendar.timeInMillis - calendar.timeInMillis
        val twentyFourHoursInMs = TimeUnit.HOURS.toMillis(24)

        if (msLeft <= twentyFourHoursInMs && msLeft > 0) {
            activeCountdownTimer = object : CountDownTimer(msLeft, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    if (!isAdded) return
                    val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                    daysText.text = getString(R.string.countdown_format, hours, minutes, seconds)
                }

                override fun onFinish() {
                    if (!isAdded) return
                    daysText.text = getString(R.string.countdown_format, 0, 0, 0)
                }
            }.start()
        } else {
            val today = calendar.get(Calendar.DAY_OF_MONTH)
            val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val daysLeft = maxDays - today

            daysText.text = if (daysLeft == 1) {
                getString(R.string.days_left_singular)
            } else {
                getString(R.string.days_left_plural, daysLeft)
            }
        }
    }

    private fun loadLeaderboard() {
        if (!swipeRefreshLayout.isRefreshing) {
            loadingLayout.visibility = View.VISIBLE
            headerTextContainer.visibility = View.INVISIBLE
            leaderboardImage.visibility = View.INVISIBLE
            positionText.visibility = View.INVISIBLE
            leaderboardCard.visibility = View.INVISIBLE
        }

        // Fetch master system configuration month context first
        db.collection("system").document("leaderboard").get()
            .addOnSuccessListener { task ->
                if (!isAdded) return@addOnSuccessListener
                val globalMonthKey = task.getString("lastMonth") ?: ""
                executeLeaderboardQuery(globalMonthKey)
            }
            .addOnFailureListener {
                if (isAdded) showMainContent()
            }
    }

    private fun executeLeaderboardQuery(globalMonthKey: String) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        val localMonthKey = SimpleDateFormat("yyyy-MM", Locale.ENGLISH).format(Date())

        leaderboardListener?.remove()

        leaderboardListener = db.collection("users")
            .orderBy("monthlyXP", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { result, error ->
                if (error != null || result == null || !isAdded) {
                    if (isAdded) { showMainContent() }
                    return@addSnapshotListener
                }

                val tempUsers = mutableListOf<LeaderboardUser>()

                for (document in result) {
                    val lbUser = document.toObject(LeaderboardUser::class.java)
                    lbUser.uid = document.id

                    // Check if user record profile has synced with current system month
                    val userLastResetMonth = document.getString("lastResetMonth") ?: ""

                    // If month flipped or this specific document's last reset timestamp is outdated, visual wipe to 0 XP
                    if (globalMonthKey != localMonthKey || (userLastResetMonth.isNotEmpty() && userLastResetMonth != globalMonthKey)) {
                        lbUser.monthlyXP = 0L
                    }
                    tempUsers.add(lbUser)
                }

                // Re-sort locally because visual elements may have shifted to 0 XP
                tempUsers.sortByDescending { it.monthlyXP }

                var myRank = 0
                for ((index, lbUser) in tempUsers.withIndex()) {
                    if (lbUser.email == currentUserEmail) {
                        myRank = index + 1
                        break
                    }
                }

                rtdb.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!isAdded) return

                        for (lbUser in tempUsers) {
                            val state = snapshot.child(lbUser.uid).child("state").getValue(String::class.java)
                            lbUser.isOnline = (state == "online")
                        }

                        usersList.clear()
                        usersList.addAll(tempUsers)

                        updateHeaderUI(myRank)
                        leaderboardAdapter?.notifyDataSetChanged()

                        showMainContent()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        if (isAdded) { showMainContent() }
                    }
                })
            }
    }

    private fun showMainContent() {
        loadingLayout.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false

        headerTextContainer.visibility = View.VISIBLE
        leaderboardImage.visibility = View.VISIBLE
        positionText.visibility = View.VISIBLE
        leaderboardCard.visibility = View.VISIBLE
    }

    private fun updateHeaderUI(myRank: Int) {
        context?.let { ctx ->
            if (myRank > 0) {
                val rankString = getString(R.string.rank_status_format, myRank)
                val spannable = SpannableString(rankString)
                val blueColor = ContextCompat.getColor(ctx, R.color.blue_2)
                val startOfRank = rankString.indexOf(myRank.toString())

                if (startOfRank != -1) {
                    spannable.setSpan(
                        ForegroundColorSpan(blueColor),
                        startOfRank,
                        rankString.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                positionText.text = spannable
            } else {
                positionText.text = getString(R.string.start_gaining_xp)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        leaderboardListener?.remove()
        activeCountdownTimer?.cancel()
    }
}