package com.tetragon.app.questions.questionMathFifthGrade.firstTopic.easy

import android.media.MediaPlayer
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tetragon.app.R
import com.tetragon.app.questions.questionMathFifthGrade.Math5GradeQuestionActivity
import com.tetragon.app.questions.questionMathFifthGrade.MathGrade5Type
import java.text.NumberFormat
import kotlin.random.Random

class UiWriteWithDigitsFragment : Fragment(R.layout.fragment_ui_write_with_digits) {

    // Fragment UI Views
    private lateinit var tvInstruction: TextView
    private lateinit var editText: EditText

    // Shared Activity Views
    private lateinit var checkBtn: Button
    private lateinit var checkBtnBack: View
    private lateinit var btnBack: View
    private lateinit var seeBtn: FrameLayout
    private lateinit var seeEnabledButton: Button
    private lateinit var stateAnswer: TextView
    private lateinit var answer: TextView
    private lateinit var stateContainer: FrameLayout
    private lateinit var circleState: ImageView

    // Question State Tracking
    private var targetNumber: Long = 0
    private var targetWords: String = ""
    private var userEnteredText: String = ""

    private var isAnswerChecked = false
    private var isIncorrectAttempt = false
    private var isFirstAttempt = true
    private var isSolutionShown = false
    private var mediaPlayer: MediaPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupKeyboard(view)
        setupInitialButtonState()
        generateProblem()
    }

    private fun initViews(view: View) {
        tvInstruction = view.findViewById(R.id.tvInstruction)
        editText = view.findViewById(R.id.editText)

        val activity = requireActivity() as Math5GradeQuestionActivity

        // Force native cursor visible while blocking native software input frameworks
        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        editText.viewTreeObserver.addOnPreDrawListener {
            editText.showSoftInputOnFocus = false
            true
        }

        // Shared Parent Activity Layout View Binds
        checkBtn = activity.findViewById(R.id.check_enabled_btn)
        checkBtnBack = activity.findViewById(R.id.check_enabled_button_background)
        btnBack = activity.findViewById(R.id.btnBackground)
        seeBtn = activity.findViewById(R.id.see_btn_container)
        seeEnabledButton = activity.findViewById(R.id.see_enabled_btn)
        stateAnswer = activity.findViewById(R.id.stateAnswer)
        answer = activity.findViewById(R.id.answer)
        stateContainer = activity.findViewById(R.id.stateContainer)
        circleState = activity.findViewById(R.id.circleState)

        checkBtn.setOnClickListener {
            if (!isAnswerChecked) {
                checkAnswer()
            } else {
                if (isIncorrectAttempt) {
                    resetForTryAgain()
                } else {
                    if (checkBtn.text == getString(R.string.btn_finish)) {
                        activity.navigateToXpGained()
                    } else {
                        val isMilestoneActive = activity.checkAndTriggerMilestone()
                        if (!isMilestoneActive) {
                            resetUIForNext()
                            activity.showRandomQuestion()
                        }
                    }
                }
            }
        }
    }

    private fun setupKeyboard(view: View) {
        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        buttonIds.forEach { id ->
            view.findViewById<Button>(id).setOnClickListener {
                if (isAnswerChecked) return@setOnClickListener

                if (userEnteredText == "0") {
                    userEnteredText = ""
                }

                if (userEnteredText.length < 12) {
                    userEnteredText += (it as Button).text.toString()
                    updateEditTextDisplay()
                    toggleCheckButtonState()
                }
            }
        }

        view.findViewById<Button>(R.id.btnDel).setOnClickListener {
            if (isAnswerChecked) return@setOnClickListener
            if (userEnteredText.isNotEmpty()) {
                userEnteredText = userEnteredText.dropLast(1)
                updateEditTextDisplay()
                toggleCheckButtonState()
            }
        }
    }

    private fun updateEditTextDisplay() {
        if (userEnteredText.isEmpty()) {
            editText.setText("")
            editText.hint = getString(R.string.hint_input_example)
        } else {
            try {
                val parsed = userEnteredText.toLong()
                val currentLocale = resources.configuration.locales[0]
                val formatted = NumberFormat.getNumberInstance(currentLocale).format(parsed)
                editText.setText(formatted)
            } catch (e: Exception) {
                editText.setText(userEnteredText)
            }
        }

        if (editText.isCursorVisible) {
            editText.requestFocus()
            editText.setSelection(editText.text.length)
        }
    }

    private fun toggleCheckButtonState() {
        if (userEnteredText.isNotEmpty()) {
            enableCheckButton()
        } else {
            disableCheckButton()
        }
    }

    private fun generateProblem() {
        targetNumber = Random.nextLong(10_000, 9_999_999)

        val currentLanguage = resources.configuration.locales[0].language
        targetWords = convertNumberToLocalizedWords(targetNumber, currentLanguage)

        val prefix = "${getString(R.string.prefix_write_with_digits)} "
        val fullText = "$prefix$targetWords"

        val builder = SpannableStringBuilder(fullText)
        val textColorHex = ContextCompat.getColor(requireContext(), R.color.text_color)
        val blue2ColorHex = ContextCompat.getColor(requireContext(), R.color.blue_2)

        // 1. Color structural instruction prefix in text_color
        builder.setSpan(
            ForegroundColorSpan(textColorHex),
            0,
            prefix.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // 2. Set complete mathematical words chunk to blue_2 default profile
        builder.setSpan(
            ForegroundColorSpan(blue2ColorHex),
            prefix.length,
            fullText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // 3. Extract joining structural keywords (like 'and') back into text_color context matching current language rules
        val andWord = getString(R.string.num_and)
        if (andWord.isNotEmpty()) {
            val regex = Regex("\\b$andWord\\b")
            regex.findAll(targetWords).forEach { matchResult ->
                val wordStart = prefix.length + matchResult.range.first
                val wordEnd = prefix.length + matchResult.range.last + 1

                builder.setSpan(
                    ForegroundColorSpan(textColorHex),
                    wordStart,
                    wordEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        tvInstruction.text = builder
        resetFragmentState()
    }

    private fun checkAnswer() {
        isAnswerChecked = true
        editText.isCursorVisible = false
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = true

        stateContainer.visibility = View.VISIBLE
        val userAnswerLong = userEnteredText.toLongOrNull() ?: -1L

        if (userAnswerLong == targetNumber) {
            playSound(R.raw.correct)
            activity.isCorrectAnswerShowing = true
            activity.playSuccessAnimation()

            val isFinished = activity.incrementProgress()
            if (isFirstAttempt) {
                activity.totalXp += MathGrade5Type.WRITE_WITH_DIGITS.xp
            }
            activity.handleCorrectAnswer()
            isIncorrectAttempt = false
            checkBtn.text = if (isFinished) getString(R.string.btn_finish) else getString(R.string.btn_continue)
            showCorrectState()
        } else {
            playSound(R.raw.wrong)
            isIncorrectAttempt = true
            activity.handleIncorrectAnswer() // FIX: Corrected framework engine progression call
            checkBtn.text = getString(R.string.btn_try_again)
            showIncorrectState()
            setupSeeSolution()
        }
        isFirstAttempt = false
    }

    private fun showCorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_3))
        circleState.setImageResource(R.drawable.correct_tick_icon)
        stateAnswer.text = getString(R.string.state_correct)

        val currentLocale = resources.configuration.locales[0]
        val formattedTarget = NumberFormat.getNumberInstance(currentLocale).format(targetNumber)
        answer.text = getString(R.string.label_answer, formattedTarget)
        answer.visibility = View.VISIBLE
        applyButtonColors(R.color.green_1, R.color.green_2, R.color.green_4)
        enableCheckButton()
    }

    private fun showIncorrectState() {
        stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_4))
        circleState.setImageResource(R.drawable.wrong_circle)
        stateAnswer.text = getString(R.string.state_incorrect)
        seeEnabledButton.text = getString(R.string.btn_see_solution)
        answer.visibility = View.GONE
        seeBtn.visibility = View.VISIBLE
        applyButtonColors(R.color.red_1, R.color.red_2, R.color.red_4)
        enableCheckButton()
    }

    private fun setupSeeSolution() {
        seeEnabledButton.setOnClickListener {
            isSolutionShown = true
            seeBtn.visibility = View.GONE
            stateAnswer.text = getString(R.string.state_solution)

            val currentLocale = resources.configuration.locales[0]
            val formattedTarget = NumberFormat.getNumberInstance(currentLocale).format(targetNumber)
            answer.text = getString(R.string.label_answer, formattedTarget)
            answer.visibility = View.VISIBLE
            checkBtn.text = getString(R.string.btn_continue)

            userEnteredText = targetNumber.toString()
            editText.isCursorVisible = false
            updateEditTextDisplay()

            circleState.setImageResource(R.drawable.solution_lamp_icon)
            isIncorrectAttempt = false
            checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black_3)
            checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.black_2)
            btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
            stateContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_2))
        }
    }

    private fun resetFragmentState() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isCorrectAnswerShowing = false // FIX: Safely handles lingering visibility states on consecutive problem distributions

        userEnteredText = ""
        editText.isCursorVisible = true
        updateEditTextDisplay()

        isAnswerChecked = false
        isIncorrectAttempt = false
        isFirstAttempt = true
        isSolutionShown = false

        stateContainer.visibility = View.INVISIBLE
        seeBtn.visibility = View.GONE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        disableCheckButton()
    }

    private fun resetForTryAgain() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = false

        isAnswerChecked = false
        isIncorrectAttempt = false
        userEnteredText = ""
        editText.isCursorVisible = true
        updateEditTextDisplay()

        seeBtn.visibility = View.GONE
        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        disableCheckButton()
        stateContainer.visibility = View.INVISIBLE
        answer.visibility = View.VISIBLE
    }

    private fun resetUIForNext() {
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.isResultCurrentlyVisible = false
        activity.hideSuccessAnimation()

        stateContainer.visibility = View.INVISIBLE
        stateAnswer.text = ""
        answer.text = ""
        answer.visibility = View.VISIBLE
        seeBtn.visibility = View.GONE

        checkBtn.text = getString(R.string.btn_check)
        setupInitialButtonState()
        disableCheckButton()
    }

    private fun convertNumberToLocalizedWords(number: Long, languageCode: String): String {
        return when (languageCode) {
            "ru" -> convertToRussianWords(number)
            "uz" -> convertToUzbekWords(number)
            else -> convertToEnglishWords(number)
        }
    }

    private fun convertToEnglishWords(number: Long): String {
        if (number == 0L) return getString(R.string.num_zero)

        val units = arrayOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
        val teens = arrayOf("ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

        fun convertLessThanOneThousand(num: Int): String {
            var current: String
            if (num % 100 < 10) {
                current = units[num % 10]
                if (num % 100 > 0 && num / 100 > 0) current = "${getString(R.string.num_and)} $current"
            } else if (num % 100 < 20) {
                current = teens[num % 10]
                if (num / 100 > 0) current = "${getString(R.string.num_and)} $current"
            } else {
                val unitPart = units[num % 10]
                current = tens[(num % 100) / 10]
                if (unitPart.isNotEmpty()) current = "$current-$unitPart"
                if (num / 100 > 0) current = "${getString(R.string.num_and)} $current"
            }
            if (num / 100 == 0) return current

            val hundredPart = "${units[num / 100]} ${getString(R.string.num_hundred)}"
            return if (current.isEmpty()) hundredPart else "$hundredPart $current"
        }

        var result = ""
        var remaining = number

        if (remaining / 1_000_000 > 0) {
            val millions = (remaining / 1_000_000).toInt()
            result += "${convertLessThanOneThousand(millions)} ${getString(R.string.num_million)} "
            remaining %= 1_000_000
        }

        if (remaining / 1_000 > 0) {
            val thousands = (remaining / 1_000).toInt()
            result += "${convertLessThanOneThousand(thousands)} ${getString(R.string.num_thousand)} "
            remaining %= 1_000
        }

        if (remaining > 0) {
            result += convertLessThanOneThousand(remaining.toInt())
        }

        return result.trim().replace(Regex("\\s+"), " ")
    }

    private fun convertToRussianWords(number: Long): String {
        if (number == 0L) return getString(R.string.num_zero)

        val unitsM = arrayOf("", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
        val unitsF = arrayOf("", "одна", "две", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
        val teens = arrayOf("десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать")
        val tens = arrayOf("", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто")
        val hundreds = arrayOf("", "сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот")

        fun getRussianPlural(num: Int, form1: String, form2: String, form5: String): String {
            val n10 = num % 10
            val n100 = num % 100
            return if (n10 == 1 && n100 != 11) form1
            else if (n10 in 2..4 && n100 !in 12..14) form2
            else form5
        }

        fun convertChunk(num: Int, isFeminine: Boolean): String {
            var current = ""
            val h = num / 100
            val t = (num % 100) / 10
            val u = num % 10

            if (h > 0) current += hundreds[h] + " "

            if (t == 1) {
                current += teens[u]
            } else {
                if (t > 1) current += tens[t] + " "
                if (u > 0) current += if (isFeminine) unitsF[u] else unitsM[u]
            }
            return current.trim()
        }

        var result = ""
        var remaining = number

        if (remaining / 1_000_000 > 0) {
            val millions = (remaining / 1_000_000).toInt()
            val millionText = getRussianPlural(millions, "миллион", "миллиона", "миллионов")
            result += "${convertChunk(millions, false)} $millionText "
            remaining %= 1_000_000
        }

        if (remaining / 1_000 > 0) {
            val thousands = (remaining / 1_000).toInt()
            val thousandText = getRussianPlural(thousands, "тысяча", "тысячи", "тысяч")
            result += "${convertChunk(thousands, true)} $thousandText "
            remaining %= 1_000
        }

        if (remaining > 0) {
            result += convertChunk(remaining.toInt(), false)
        }

        return result.trim().replace(Regex("\\s+"), " ")
    }

    private fun convertToUzbekWords(number: Long): String {
        if (number == 0L) return getString(R.string.num_zero)

        val units = arrayOf("", "bir", "ikki", "uch", "to\'rt", "besh", "olti", "yetti", "sakkiz", "to\'qqiz")
        val tens = arrayOf("", "o\'n", "yigirma", "o\'ttiz", "qirq", "ellik", "oltmish", "yetmish", "sakkison", "to\'qson") // FIX: Aligns with exact array specifications

        fun convertChunkUz(num: Int): String {
            var current = ""
            val h = num / 100
            val t = (num % 100) / 10
            val u = num % 10

            if (h > 0) current += "${units[h]} ${getString(R.string.num_hundred)} "
            if (t > 0) current += "${tens[t]} "
            if (u > 0) current += "${units[u]} "
            return current.trim()
        }

        var result = ""
        var remaining = number

        if (remaining / 1_000_000 > 0) {
            val millions = (remaining / 1_000_000).toInt()
            result += "${convertChunkUz(millions)} ${getString(R.string.num_million)} "
            remaining %= 1_000_000
        }

        if (remaining / 1_000 > 0) {
            val thousands = (remaining / 1_000).toInt()
            result += "${convertChunkUz(thousands)} ${getString(R.string.num_thousand)} "
            remaining %= 1_000
        }

        if (remaining > 0) {
            result += convertChunkUz(remaining.toInt())
        }

        return result.trim().replace(Regex("\\s+"), " ")
    }

    private fun setupInitialButtonState() {
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_2)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.blue_1)
    }

    private fun enableCheckButton() {
        checkBtn.isEnabled = true
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.VISIBLE
        checkBtnBack.visibility = View.VISIBLE
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.INVISIBLE
    }

    private fun disableCheckButton() {
        checkBtn.isEnabled = false
        val activity = requireActivity() as Math5GradeQuestionActivity
        activity.findViewById<FrameLayout>(R.id.check_disabled_btn_container).visibility = View.VISIBLE
        checkBtnBack.visibility = View.INVISIBLE
        activity.findViewById<FrameLayout>(R.id.check_enabled_btn_container).visibility = View.INVISIBLE
    }

    private fun applyButtonColors(buttonColor: Int, backColor: Int, backgroundColor: Int) {
        checkBtn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), buttonColor)
        checkBtnBack.backgroundTintList = ContextCompat.getColorStateList(requireContext(), backColor)
        btnBack.setBackgroundColor(ContextCompat.getColor(requireContext(), backgroundColor))
    }

    private fun playSound(soundResId: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(requireContext(), soundResId)
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroyView()
    }
}