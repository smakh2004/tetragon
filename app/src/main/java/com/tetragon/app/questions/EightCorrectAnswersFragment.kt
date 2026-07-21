package com.tetragon.app.questions

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tetragon.app.R
import app.rive.runtime.kotlin.RiveAnimationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EightCorrectAnswersFragment : Fragment(R.layout.fragment_eight_correct_answers) {

    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textView = view.findViewById<View>(R.id.eightCorrectText)
        val riveAnimationView = view.findViewById<RiveAnimationView>(R.id.topic8Correct)

        // Initially hide the text
        textView.alpha = 0f

        viewLifecycleOwner.lifecycleScope.launch {
            // wait 0.5 seconds before showing text, playing sound, AND starting animation
            delay(500)

            // 1. Fire up the Rive runtime animation engine cleanly
            riveAnimationView.play()
            // Alternative input trigger if you need to fire a specific state transition:
            // riveAnimationView.fireState("State Machine 1", "play")

            // 2. Fade in text
            textView.animate()
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            // 3. Play milestone sound
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
        mediaPlayer?.release()
        mediaPlayer = null
    }
}