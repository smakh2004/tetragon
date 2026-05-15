package com.tetragon.app.ui.uiSettings

import android.os.Bundle
import android.widget.Toast
import com.tetragon.app.R
import com.tetragon.app.databinding.ActivityAgeChangeBinding
import com.tetragon.app.utils.languageChangeUtils.BaseActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class AgeShowActivity : BaseActivity() {
    private lateinit var binding: ActivityAgeChangeBinding
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgeChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        loadCalculatedAge()
    }

    private fun loadCalculatedAge() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val docRef = db.collection("users").document(userId)

        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // 1. Get Base Age (Handles both String and Number types safely)
                    val baseAge = when (val ageValue = document.get("age")) {
                        is Long -> ageValue.toInt()
                        is String -> ageValue.toIntOrNull() ?: 0
                        else -> 0
                    }

                    // 2. Get Registration Date
                    val registeredAt = document.getTimestamp("registeredAt")

                    if (registeredAt != null) {
                        val finalAge = calculateCurrentAge(baseAge, registeredAt)
                        binding.age.text = finalAge.toString()
                    } else {
                        // Fallback if registeredAt is missing
                        binding.age.text = baseAge.toString()
                    }
                } else {
                    Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Logic: Current Age = Base Age + (Current Year - Registration Year)
     * We then subtract 1 if the "anniversary" hasn't happened yet this year.
     */
    private fun calculateCurrentAge(baseAge: Int, registeredAt: Timestamp): Int {
        val regDate = Calendar.getInstance().apply { time = registeredAt.toDate() }
        val today = Calendar.getInstance()

        // Calculate years passed since registration
        var yearsPassed = today.get(Calendar.YEAR) - regDate.get(Calendar.YEAR)

        // Check if the registration anniversary has occurred yet this year
        if (today.get(Calendar.DAY_OF_YEAR) < regDate.get(Calendar.DAY_OF_YEAR)) {
            yearsPassed--
        }

        // Return initial age + years that have actually passed
        return baseAge + yearsPassed
    }
}