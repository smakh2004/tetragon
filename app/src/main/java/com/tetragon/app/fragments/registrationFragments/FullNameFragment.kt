package com.tetragon.app.fragments.registrationFragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.ui.RegisterActivity

class FullNameFragment : Fragment() {

    private lateinit var firstNameEditText: EditText
    private lateinit var secondNameEditText: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_full_name, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firstNameEditText = view.findViewById(R.id.firstNameEditText)
        secondNameEditText = view.findViewById(R.id.lastNameEditText)

        // Repopulate on back navigation (and after a Google sign-in prefill).
        val activity = activity as? RegisterActivity
        val savedFirst = activity?.userData?.firstName.orEmpty()
        val savedLast = activity?.userData?.lastName.orEmpty()

        if (savedFirst.isNotEmpty()) {
            firstNameEditText.setText(savedFirst)
            firstNameEditText.setSelection(savedFirst.length)
        }
        if (savedLast.isNotEmpty()) {
            secondNameEditText.setText(savedLast)
            secondNameEditText.setSelection(savedLast.length)
        }
        onTextPresent()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = onTextPresent()
        }

        firstNameEditText.addTextChangedListener(watcher)
        secondNameEditText.addTextChangedListener(watcher)
    }

    private fun onTextPresent() {
        val first = firstNameEditText.text.toString().trim()
        val second = secondNameEditText.text.toString().trim()

        val activity = activity as? RegisterActivity ?: return
        activity.userData.firstName = first
        activity.userData.lastName = second

        activity.setContinueButtonEnabled(first.isNotEmpty() && second.isNotEmpty())
    }
}