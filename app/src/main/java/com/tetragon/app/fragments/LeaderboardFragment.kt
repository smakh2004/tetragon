package com.tetragon.app.fragments

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tetragon.app.R
import com.tetragon.app.utils.leaderboardUtils.LeaderboardAdapter
import com.tetragon.app.utils.leaderboardUtils.LeaderboardUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LeaderboardFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var positionText: TextView
    private lateinit var monthText: TextView
    private lateinit var daysText: TextView

    private val db = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().getReference("status")
    private val usersList = mutableListOf<LeaderboardUser>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_leaderboard, container, false)

        recycler = view.findViewById(R.id.leaderboardRecycler)
        positionText = view.findViewById(R.id.positionText)
        monthText = view.findViewById(R.id.monthText)
        daysText = view.findViewById(R.id.daysText)

        recycler.layoutManager = LinearLayoutManager(requireContext())

        updateMonthUI()
        loadLeaderboard()

        return view
    }

    private fun updateMonthUI() {
        val calendar = Calendar.getInstance()

        // 1. Use default locale to get translated month names automatically
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val monthName = monthFormat.format(calendar.time).uppercase()
        monthText.text = monthName

        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysLeft = maxDays - today

        // 2. Localized Days Left
        daysText.text = if (daysLeft == 1) {
            getString(R.string.days_left_singular)
        } else {
            getString(R.string.days_left_plural, daysLeft)
        }
    }

    private fun loadLeaderboard() {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

        // Real-time listener for XP rankings
        db.collection("users")
            .orderBy("monthlyXP", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { result, error ->
                if (error != null || result == null || !isAdded) return@addSnapshotListener

                val tempUsers = mutableListOf<LeaderboardUser>()
                var myRank = 0

                for ((index, document) in result.withIndex()) {
                    val lbUser = document.toObject(LeaderboardUser::class.java)
                    tempUsers.add(lbUser)

                    if (lbUser.email == currentUserEmail) {
                        myRank = index + 1
                    }
                }

                // Fetch Online Status from RTDB
                rtdb.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (lbUser in tempUsers) {
                            val state = snapshot.child(lbUser.uid).child("state").getValue(String::class.java)
                            lbUser.isOnline = (state == "online")
                        }

                        usersList.clear()
                        usersList.addAll(tempUsers)

                        updateHeaderUI(myRank)
                        recycler.adapter = LeaderboardAdapter(usersList, currentUserEmail)
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
            }
    }

    private fun updateHeaderUI(myRank: Int) {
        context?.let { ctx ->
            if (myRank > 0) {
                // 3. Localized Rank String: "You are in 5-place"
                val rankString = getString(R.string.rank_status_format, myRank)
                val spannable = SpannableString(rankString)
                val blueColor = ContextCompat.getColor(ctx, R.color.blue_2)

                // Find the start of the number to begin coloring
                val startOfRank = rankString.indexOf(myRank.toString())

                if (startOfRank != -1) {
                    spannable.setSpan(
                        ForegroundColorSpan(blueColor),
                        startOfRank,
                        rankString.length, // Colors the number and everything after it
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                positionText.text = spannable
            } else {
                // 4. Localized "Start to gain XP!"
                positionText.text = getString(R.string.start_gaining_xp)
            }
        }
    }
}