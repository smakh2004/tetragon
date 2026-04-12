package com.example.tetragon.ui.uiSettings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.tetragon.R
import com.example.tetragon.databinding.ActivityNameChangeBinding
import com.example.tetragon.utils.languageChangeUtils.BaseActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NameChangeActivity : BaseActivity() {
    private lateinit var binding: ActivityNameChangeBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNameChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backBtn.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        binding.saveEnabledBtn.setOnClickListener {

            val firstName = binding.firstNameEditText.text.toString().trim()
            val lastName = binding.lastNameEditText.text.toString().trim()

            // ✅ Disable button while operation is in progress
            binding.saveEnabledBtnContainer.visibility = View.INVISIBLE
            binding.saveDisabledBtnContainer.visibility = View.VISIBLE
            binding.saveDisabledBtn.text = "SAVING.."

            if (firstName.isEmpty() || lastName.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = auth.currentUser?.uid ?: return@setOnClickListener

            val updates = hashMapOf<String, Any>(
                "firstName" to firstName,
                "lastName" to lastName
            )

            firestore.collection("users")
                .document(userId)
                .update(updates)
                .addOnSuccessListener {
                    Toast.makeText(this, "Name updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update name", Toast.LENGTH_SHORT).show()
                    // ✅ Disable button while operation is in progress
                    binding.saveEnabledBtnContainer.visibility = View.VISIBLE
                    binding.saveDisabledBtnContainer.visibility = View.INVISIBLE
                    binding.saveDisabledBtn.text = "SAVE"
                }
        }

        setupTextWatcher()
    }

    private fun setupTextWatcher() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                checkFields()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.firstNameEditText.addTextChangedListener(watcher)
        binding.lastNameEditText.addTextChangedListener(watcher)
    }

    private fun checkFields() {
        val first = binding.firstNameEditText.text.toString().trim()
        val last = binding.lastNameEditText.text.toString().trim()

        val enabled = first.isNotEmpty() && last.isNotEmpty()

        binding.saveEnabledBtnContainer.visibility =
            if (enabled) View.VISIBLE else View.INVISIBLE

        binding.saveDisabledBtnContainer.visibility =
            if (enabled) View.INVISIBLE else View.VISIBLE
    }

}