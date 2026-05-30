package com.tetragon.app.fragments

import android.os.Bundle
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
import java.util.Locale

class LeaderboardFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var positionText: TextView
    private lateinit var monthText: TextView
    private lateinit var daysText: TextView

    private lateinit var headerTextContainer: LinearLayout
    private lateinit var leaderboardImage: ImageView
    private lateinit var leaderboardCard: MaterialCardView
    private lateinit var loadingLayout: LinearLayout

    private val db = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().getReference("status")

    private val usersList = mutableListOf<LeaderboardUser>()
    // Kept as a reference so we don't clear scroll states by constantly re-instantiating it
    private var leaderboardAdapter: LeaderboardAdapter? = null

    private var leaderboardListener: ListenerRegistration? = null

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

        recycler.layoutManager = LinearLayoutManager(requireContext())

        // FIXED: Initialize the adapter explicitly exactly ONCE right here
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        leaderboardAdapter = LeaderboardAdapter(usersList, currentUserEmail) { selectedUser ->
            val intent = android.content.Intent(requireContext(), OtherUserProfileActivity::class.java)

            // Safe fallback evaluation for rank layout processing
            val explicitRank = usersList.indexOf(selectedUser) + 1

            intent.putExtra("USER_DATA", selectedUser)
            intent.putExtra("USER_RANK", explicitRank)
            startActivity(intent)
        }
        recycler.adapter = leaderboardAdapter

        updateMonthUI()
        loadLeaderboard()

        return view
    }

    private fun updateMonthUI() {
        val calendar = Calendar.getInstance()

        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val monthName = monthFormat.format(calendar.time).uppercase()
        monthText.text = monthName

        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val daysLeft = maxDays - today

        daysText.text = if (daysLeft == 1) {
            getString(R.string.days_left_singular)
        } else {
            getString(R.string.days_left_plural, daysLeft)
        }
    }

    private fun loadLeaderboard() {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

        loadingLayout.visibility = View.VISIBLE
        headerTextContainer.visibility = View.INVISIBLE
        leaderboardImage.visibility = View.INVISIBLE
        positionText.visibility = View.INVISIBLE
        leaderboardCard.visibility = View.INVISIBLE

        leaderboardListener = db.collection("users")
            .orderBy("monthlyXP", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { result, error ->
                if (error != null || result == null || !isAdded) {
                    if (isAdded) { showMainContent() }
                    return@addSnapshotListener
                }

                val tempUsers = mutableListOf<LeaderboardUser>()
                var myRank = 0

                for ((index, document) in result.withIndex()) {
                    val lbUser = document.toObject(LeaderboardUser::class.java)
                    lbUser.uid = document.id

                    tempUsers.add(lbUser)

                    if (lbUser.email == currentUserEmail) {
                        myRank = index + 1
                    }
                }

                rtdb.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!isAdded) return

                        for (lbUser in tempUsers) {
                            val state = snapshot.child(lbUser.uid).child("state").getValue(String::class.java)
                            lbUser.isOnline = (state == "online")
                        }

                        // FIXED: Mutate underlying dataset fields directly instead of wiping view states
                        usersList.clear()
                        usersList.addAll(tempUsers)

                        updateHeaderUI(myRank)

                        // FIXED: Notify existing UI setup cleanly rather than overriding it
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
    }
}