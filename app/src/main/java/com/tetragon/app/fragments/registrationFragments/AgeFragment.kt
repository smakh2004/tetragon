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

class AgeFragment : Fragment() {

    private lateinit var ageEditText: EditText

    companion object {
        private const val MIN_AGE = 4
        private const val MAX_AGE = 100
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_age, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ageEditText = view.findViewById(R.id.ageEditText)

        // Restore what the user already typed. Without this, stepping back to this screen
        // showed an empty field with the Continue button permanently disabled, because the
        // TextWatcher only fires on a change.
        val savedAge = (activity as? RegisterActivity)?.userData?.age.orEmpty()
        if (savedAge.isNotEmpty()) {
            ageEditText.setText(savedAge)
            ageEditText.setSelection(savedAge.length)
        }
        validate(ageEditText.text?.toString().orEmpty())

        ageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validate(s?.toString().orEmpty())
            }
        })
    }

    fun setControlsEnabled(enabled: Boolean) {
        if (::ageEditText.isInitialized) ageEditText.isEnabled = enabled
    }

    private fun validate(raw: String) {
        val activity = activity as? RegisterActivity ?: return
        val value = raw.trim()
        val age = value.toIntOrNull()
        val isValid = value.isNotEmpty() && age != null && age in MIN_AGE..MAX_AGE

        activity.updateAge(if (isValid) value else "")
        activity.setContinueButtonEnabled(isValid)
    }
}