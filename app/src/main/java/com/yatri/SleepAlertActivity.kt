package com.yatri

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yatri.localization.LocalizationManager
import java.text.SimpleDateFormat
import java.util.*

class SleepAlertActivity : AppCompatActivity() {
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var tvAlertMessage: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var btnDismiss: Button
    private lateinit var btnSnooze: Button
    
    companion object {
        private const val TAG = "SleepAlertActivity"
        const val EXTRA_ALERT_MESSAGE = "alert_message"
        const val EXTRA_ALERT_TYPE = "alert_type"
        
        fun createIntent(context: Context, message: String, alertType: String = "SLEEP_ALERT"): Intent {
            return Intent(context, SleepAlertActivity::class.java).apply {
                putExtra(EXTRA_ALERT_MESSAGE, message)
                putExtra(EXTRA_ALERT_TYPE, alertType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_NO_HISTORY
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize localization
        LocalizationManager.initialize(this)
        
        Log.d(TAG, "SleepAlertActivity created")
        
        // Setup window flags for showing over lock screen
        setupWindowFlags()
        
        setContentView(R.layout.activity_sleep_alert)
        
        initializeViews()
        setupAlert()
        acquireWakeLock()
        startAlarmSound()
    }
    
    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // For Android 8.1 and above
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            // For older Android versions
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        
        // Additional flags for full-screen experience
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        // Hide system UI for immersive experience
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
    
    private fun initializeViews() {
        tvAlertMessage = findViewById(R.id.tvAlertMessage)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        btnDismiss = findViewById(R.id.btnDismiss)
        btnSnooze = findViewById(R.id.btnSnooze)
        
        btnDismiss.setOnClickListener { dismissAlert() }
        btnSnooze.setOnClickListener { snoozeAlert() }
    }
    
    private fun setupAlert() {
        val alertMessage = intent.getStringExtra(EXTRA_ALERT_MESSAGE) ?: "🚨 SLEEP ALERT 🚨"
        val alertType = intent.getStringExtra(EXTRA_ALERT_TYPE) ?: "SLEEP_ALERT"
        
        tvAlertMessage.text = alertMessage
        
        // Update current time
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tvCurrentTime.text = "Current Time: $currentTime"
        
        Log.d(TAG, "Alert setup: $alertMessage (Type: $alertType)")
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP or 
                PowerManager.ON_AFTER_RELEASE,
                "Yatri:SleepAlert"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes timeout
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }
    
    private fun startAlarmSound() {
        try {
            // Try custom sleep alert sound first
            val soundUri = getAlarmSoundUri()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                
                // Set to maximum volume for alarm stream
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                
                setDataSource(this@SleepAlertActivity, soundUri)
                isLooping = true
                prepare()
                start()
            }
            
            Log.d(TAG, "Alarm sound started: $soundUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm sound", e)
        }
    }
    
    private fun getAlarmSoundUri(): Uri {
        return try {
            val resourceId = resources.getIdentifier("sleep_alert", "raw", packageName)
            if (resourceId != 0) {
                Uri.parse("android.resource://$packageName/$resourceId")
            } else {
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get custom alarm sound, using default", e)
            android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        }
    }
    
    private fun dismissAlert() {
        Log.d(TAG, "Alert dismissed by user")
        stopAlarmSound()
        releaseWakeLock()
        finish()
    }
    
    private fun snoozeAlert() {
        Log.d(TAG, "Alert snoozed by user")
        stopAlarmSound()
        releaseWakeLock()
        
        // Schedule snooze (5 minutes)
        SleepAlertManager.scheduleSnoozeAlert(this, 5 * 60 * 1000L)
        finish()
    }
    
    private fun stopAlarmSound() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            Log.d(TAG, "Alarm sound stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm sound", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.apply {
                if (isHeld) {
                    release()
                }
            }
            wakeLock = null
            Log.d(TAG, "Wake lock released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        releaseWakeLock()
        Log.d(TAG, "SleepAlertActivity destroyed")
    }
    
    override fun onBackPressed() {
        // Prevent back button from dismissing the alert
        // User must explicitly dismiss or snooze
        Log.d(TAG, "Back button pressed - ignored")
    }
}