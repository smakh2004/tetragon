package com.tetragon.app.fragments.registrationFragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.tetragon.app.R
import com.tetragon.app.ui.RegisterActivity

class FullNameFragment : Fragment() {

    private lateinit var firstNameEditText: EditText
    private lateinit var secondNameEditText: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_full_name, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firstNameEditText = view.findViewById(R.id.firstNameEditText)
        secondNameEditText = view.findViewById(R.id.lastNameEditText)

        // Create a TextWatcher that checks both fields
        val watcher = object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {}
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                onTextPresent()
            }
        }

        firstNameEditText.addTextChangedListener(watcher)
        secondNameEditText.addTextChangedListener(watcher)
    }

    private fun onTextPresent() {
        val first = firstNameEditText.text.toString()
        val second = secondNameEditText.text.toString()

        val activity = activity as? RegisterActivity
        activity?.userData?.firstName = first
        activity?.userData?.lastName = second

        activity?.setContinueButtonEnabled(first.isNotEmpty() && second.isNotEmpty())
    }
}
