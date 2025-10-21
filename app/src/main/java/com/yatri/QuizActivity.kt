package com.yatri

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yatri.localization.LocalizationManager

class QuizActivity : AppCompatActivity() {
    
    private lateinit var tvQuestion: TextView
    private lateinit var tvTimer: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnOption1: Button
    private lateinit var btnOption2: Button
    private lateinit var btnOption3: Button
    private lateinit var btnOption4: Button
    private lateinit var btnSubmit: Button
    
    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 15000 // 15 seconds
    private var selectedAnswer: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize localization
        LocalizationManager.initialize(this)
        
        setContentView(R.layout.activity_quiz)
        
        initializeViews()
        setupQuiz()
        startTimer()
    }
    
    private fun initializeViews() {
        tvQuestion = findViewById(R.id.tvQuestion)
        tvTimer = findViewById(R.id.tvTimer)
        progressBar = findViewById(R.id.progressBar)
        btnOption1 = findViewById(R.id.btnOption1)
        btnOption2 = findViewById(R.id.btnOption2)
        btnOption3 = findViewById(R.id.btnOption3)
        btnOption4 = findViewById(R.id.btnOption4)
        btnSubmit = findViewById(R.id.btnSubmit)
        
        // Setup option button click listeners
        btnOption1.setOnClickListener { selectAnswer("Nepal") }
        btnOption2.setOnClickListener { selectAnswer("India") }
        btnOption3.setOnClickListener { selectAnswer("China") }
        btnOption4.setOnClickListener { selectAnswer("Pakistan") }
        
        btnSubmit.setOnClickListener { submitAnswer() }
    }
    
    private fun setupQuiz() {
        tvQuestion.text = getString(R.string.quiz_question_mount_everest)
        btnOption1.text = getString(R.string.quiz_option_nepal)
        btnOption2.text = getString(R.string.quiz_option_india)
        btnOption3.text = getString(R.string.quiz_option_china)
        btnOption4.text = getString(R.string.quiz_option_pakistan)
        btnSubmit.text = getString(R.string.quiz_submit)
        
        // Initialize progress bar
        progressBar.max = 100
        progressBar.progress = 100
        updateTimerDisplay()
    }
    
    private fun startTimer() {
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 100) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerDisplay()
            }
            
            override fun onFinish() {
                timeLeftInMillis = 0
                updateTimerDisplay()
                // Auto submit when time runs out
                submitAnswer()
            }
        }.start()
    }
    
    private fun updateTimerDisplay() {
        val seconds = (timeLeftInMillis / 1000).toInt()
        tvTimer.text = getString(R.string.time_left, seconds)
        
        // Update progress bar - progress decreases as time runs out
        val progress = ((timeLeftInMillis.toFloat() / 15000f) * 100).toInt()
        progressBar.progress = progress
        
        // Change color based on time remaining
        when {
            seconds <= 5 -> {
                tvTimer.setTextColor(ContextCompat.getColor(this, R.color.error))
                // Update progress bar color to red for low time
                updateProgressBarColor(R.color.error)
            }
            seconds <= 10 -> {
                tvTimer.setTextColor(ContextCompat.getColor(this, R.color.warning))
                // Update progress bar color to orange for medium time
                updateProgressBarColor(R.color.warning)
            }
            else -> {
                tvTimer.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                // Update progress bar color to primary for normal time
                updateProgressBarColor(R.color.primary)
            }
        }
    }
    
    private fun updateProgressBarColor(colorRes: Int) {
        // Set the appropriate drawable based on color
        val drawableRes = when (colorRes) {
            R.color.error -> R.drawable.circular_progress_error
            R.color.warning -> R.drawable.circular_progress_warning
            else -> R.drawable.circular_progress_normal
        }
        progressBar.progressDrawable = ContextCompat.getDrawable(this, drawableRes)
    }
    
    private fun selectAnswer(answer: String) {
        selectedAnswer = answer
        
        // Reset all buttons
        resetOptionButtons()
        
        // Highlight selected button
        when (answer) {
            "Nepal" -> {
                btnOption1.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnOption1.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "India" -> {
                btnOption2.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnOption2.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "China" -> {
                btnOption3.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnOption3.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            "Pakistan" -> {
                btnOption4.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnOption4.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }
        
        // Enable submit button
        btnSubmit.isEnabled = true
        btnSubmit.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
    }
    
    private fun resetOptionButtons() {
        val defaultColor = ContextCompat.getColor(this, R.color.option_button_bg)
        val defaultTextColor = ContextCompat.getColor(this, R.color.text_primary)
        
        btnOption1.setBackgroundColor(defaultColor)
        btnOption1.setTextColor(defaultTextColor)
        btnOption2.setBackgroundColor(defaultColor)
        btnOption2.setTextColor(defaultTextColor)
        btnOption3.setBackgroundColor(defaultColor)
        btnOption3.setTextColor(defaultTextColor)
        btnOption4.setBackgroundColor(defaultColor)
        btnOption4.setTextColor(defaultTextColor)
    }
    
    private fun submitAnswer() {
        countDownTimer?.cancel()
        
        // Show result or move to next question
        val isCorrect = selectedAnswer == "Nepal"
        
        // Update UI to show correct answer
        showCorrectAnswer()
        
        // You can add navigation to next question or show results here
    }
    
    private fun showCorrectAnswer() {
        // Highlight correct answer in green
        btnOption1.setBackgroundColor(ContextCompat.getColor(this, R.color.success))
        btnOption1.setTextColor(ContextCompat.getColor(this, R.color.white))
        
        // Highlight wrong selected answer in red if different
        if (selectedAnswer != "Nepal") {
            when (selectedAnswer) {
                "India" -> {
                    btnOption2.setBackgroundColor(ContextCompat.getColor(this, R.color.error))
                    btnOption2.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
                "China" -> {
                    btnOption3.setBackgroundColor(ContextCompat.getColor(this, R.color.error))
                    btnOption3.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
                "Pakistan" -> {
                    btnOption4.setBackgroundColor(ContextCompat.getColor(this, R.color.error))
                    btnOption4.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
