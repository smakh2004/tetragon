package com.example.tetragon.utils.registrationUtils

import android.annotation.SuppressLint
import android.text.InputType
import android.view.MotionEvent
import android.widget.EditText

object PasswordToggleHelper {

    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        editText: EditText,
        eyeOpenIcon: Int,
        eyeClosedIcon: Int
    ) {
        var isPasswordVisible = false

        editText.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {

                val drawableEndIndex = 2
                val drawable = editText.compoundDrawables[drawableEndIndex]

                if (drawable != null) {
                    val touchX = event.x
                    val drawableStart =
                        editText.width - editText.paddingEnd - drawable.bounds.width()

                    if (touchX >= drawableStart) {

                        val currentTypeface = editText.typeface

                        if (isPasswordVisible) {
                            editText.inputType =
                                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            editText.setCompoundDrawablesWithIntrinsicBounds(
                                0, 0, eyeOpenIcon, 0
                            )
                        } else {
                            editText.inputType =
                                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            editText.setCompoundDrawablesWithIntrinsicBounds(
                                0, 0, eyeClosedIcon, 0
                            )
                        }

                        editText.typeface = currentTypeface
                        editText.setSelection(editText.text.length)

                        isPasswordVisible = !isPasswordVisible

                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }
}