package com.yatri

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationManager
import android.app.NotificationChannel
import android.graphics.Color

object SleepAlertSoundTester {
    private const val TAG = "SleepAlertSoundTester"
    
    fun testSleepAlertSound(context: Context) {
        Log.d(TAG, "Testing sleep alert sound...")
        
        // Check notification permissions
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled")
            Toast.makeText(context, "Notifications are disabled. Please enable them for sleep alerts.", Toast.LENGTH_LONG).show()
            return
        }
        
        // Test custom sound file
        testCustomSound(context)
        
        // Test default alarm sound
        testDefaultAlarmSound(context)
        
        // Test notification channel
        testNotificationChannel(context)
    }
    
    private fun testCustomSound(context: Context) {
        try {
            val resourceId = context.resources.getIdentifier("sleep_alert", "raw", context.packageName)
            if (resourceId != 0) {
                val uri = Uri.parse("android.resource://${context.packageName}/$resourceId")
                Log.d(TAG, "Custom sleep alert sound found: $uri")
                
                val mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, uri)
                    prepare()
                    start()
                }
                
                Toast.makeText(context, "Custom sleep alert sound played", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Custom sleep alert sound played successfully")
                
            } else {
                Log.w(TAG, "Custom sleep alert sound file not found")
                Toast.makeText(context, "Custom sleep alert sound file not found. Using default.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing custom sleep alert sound: ${e.message}")
            Toast.makeText(context, "Error playing custom sound: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun testDefaultAlarmSound(context: Context) {
        try {
            val defaultUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            Log.d(TAG, "Testing default alarm sound: $defaultUri")
            
            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, defaultUri)
                prepare()
                start()
            }
            
            Toast.makeText(context, "Default alarm sound played", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Default alarm sound played successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error playing default alarm sound: ${e.message}")
            Toast.makeText(context, "Error playing default sound: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun testNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Check if our channel exists
            val channel = notificationManager.getNotificationChannel("alerts_v3")
            if (channel != null) {
                Log.d(TAG, "Notification channel found: ${channel.id}, importance: ${channel.importance}")
                Log.d(TAG, "Channel sound: ${channel.sound}")
                Log.d(TAG, "Channel vibration: ${channel.shouldVibrate()}")
                Log.d(TAG, "Channel lights: ${channel.shouldShowLights()}")
                
                Toast.makeText(context, "Notification channel: ${channel.name} (Importance: ${channel.importance})", Toast.LENGTH_LONG).show()
            } else {
                Log.w(TAG, "Notification channel 'alerts_v3' not found")
                Toast.makeText(context, "Notification channel not found", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun checkAudioSettings(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        Log.d(TAG, "Audio settings:")
        Log.d(TAG, "Alarm volume: ${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}/${audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)}")
        Log.d(TAG, "Ringer mode: ${audioManager.ringerMode}")
        Log.d(TAG, "Do not disturb mode: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audioManager.isStreamMute(AudioManager.STREAM_ALARM) else "N/A"}")
        
        Toast.makeText(context, "Alarm volume: ${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}/${audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)}", Toast.LENGTH_LONG).show()
    }
}

