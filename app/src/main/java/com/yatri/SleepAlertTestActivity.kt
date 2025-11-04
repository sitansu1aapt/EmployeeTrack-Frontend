package com.yatri

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.yatri.localization.LocalizationManager

class SleepAlertTestActivity : AppCompatActivity() {
    
    private lateinit var tvStatus: TextView
    private lateinit var btnTestSound: Button
    private lateinit var btnTestNotification: Button
    private lateinit var btnCheckSettings: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize localization
        LocalizationManager.initialize(this)
        
        setContentView(R.layout.activity_sleep_alert_test)
        
        initializeViews()
        setupListeners()
        checkInitialStatus()
    }
    
    private fun initializeViews() {
        tvStatus = findViewById(R.id.tvStatus)
        btnTestSound = findViewById(R.id.btnTestSound)
        btnTestNotification = findViewById(R.id.btnTestNotification)
        btnCheckSettings = findViewById(R.id.btnCheckSettings)
    }
    
    private fun setupListeners() {
        btnTestSound.setOnClickListener {
            SleepAlertSoundTester.testSleepAlertSound(this)
        }
        
        btnTestNotification.setOnClickListener {
            testSleepAlertNotification()
        }
        
        btnCheckSettings.setOnClickListener {
            SleepAlertSoundTester.checkAudioSettings(this)
        }
    }
    
    private fun checkInitialStatus() {
        val notificationManager = NotificationManagerCompat.from(this)
        val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
        
        val statusText = buildString {
            appendLine("Sleep Alert Sound Test")
            appendLine("======================")
            appendLine("Notifications Enabled: $areNotificationsEnabled")
            
            // Check if custom sound file exists
            val resourceId = resources.getIdentifier("sleep_alert", "raw", packageName)
            appendLine("Custom Sound File: ${if (resourceId != 0) "Found" else "Not Found"}")
            
            // Check notification channel
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                val channel = nm.getNotificationChannel("alerts_v3")
                appendLine("Notification Channel: ${if (channel != null) "Found (${channel.importance})" else "Not Found"}")
                if (channel != null) {
                    appendLine("Channel Sound: ${channel.sound}")
                    appendLine("Channel Vibration: ${channel.shouldVibrate()}")
                }
            }
        }
        
        tvStatus.text = statusText
    }
    
    private fun testSleepAlertNotification() {
        // Initialize the new sleep alert system
        SleepAlertManager.initialize(this)
        
        // Test the new full-screen sleep alert
        SleepAlertManager.testSleepAlert(this)
        
        android.widget.Toast.makeText(this, "Full-screen sleep alert triggered!", android.widget.Toast.LENGTH_LONG).show()
    }
}







