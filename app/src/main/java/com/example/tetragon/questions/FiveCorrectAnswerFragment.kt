package com.example.tetragon.questions

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.tetragon.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FiveCorrectAnswerFragment : Fragment(R.layout.fragment_five_correct_answer) {

    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textView = view.findViewById<View>(R.id.fiveCorrectText)

        // Initially hide the text
        textView.alpha = 0f

        viewLifecycleOwner.lifecycleScope.launch {

            // wait 1 second before showing text and playing sound
            delay(500)

            // Fade in text
            textView.animate()
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            // Play sound
            mediaPlayer = MediaPlayer.create(requireContext(), R.raw.five_correct_answer_sound)
            mediaPlayer?.start()

            // Stay visible for 1 second
            delay(1000)

            // Fade out text
            textView.animate()
                .alpha(0f)
                .setDuration(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Release MediaPlayer to avoid memory leaks
        mediaPlayer?.release()
        mediaPlayer = null
    }
}