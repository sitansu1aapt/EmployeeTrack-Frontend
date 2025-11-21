package com.yatri

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.media.AudioManager
import android.app.NotificationManager as SysNm
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.yatri.databinding.ActivityNotificationTestBinding

class NotificationTestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotificationTestBinding
    private var lastChannelIdForTest: String? = null

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updateNotificationStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }

        binding.btnRequestNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppNotificationSettings()
            }
        }
        binding.btnFixBattery.setOnClickListener { openBatteryOptimizationSettings() }
        binding.btnBatterySaver.setOnClickListener { openBatterySaverSettings() }
        binding.btnSoundSettings.setOnClickListener { openSoundSettings() }
        binding.btnSendTest.setOnClickListener { sendFullScreenTest() }

        // Initial evaluation
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        // Refresh in case the user changed settings
        updateBatteryStatus()
        updateNotificationStatus()
        updatePowerSaverStatus()
        updateSoundStatus()
    }

    private fun refreshAll() {
        updateNetworkStatus()
        updateNotificationStatus()
        updateBatteryStatus()
        updatePowerSaverStatus()
        updateSoundStatus()
        // Initial state for result row
        setRow(binding.rowResult.icon, binding.rowResult.title, binding.rowResult.subtitle,
            false, "Test Notification Result", "Awaiting test")
    }

    private fun updateNetworkStatus() {
        val ok = isNetworkAvailable()
        setRow(binding.rowNetwork.icon, binding.rowNetwork.title, binding.rowNetwork.subtitle,
            ok, "Network Connectivity", if (ok) "Connected" else "No connection")
    }

    private fun updateNotificationStatus() {
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        setRow(binding.rowNotif.icon, binding.rowNotif.title, binding.rowNotif.subtitle,
            enabled, "Notifications Settings", if (enabled) "Notifications are enabled" else "Notifications are blocked")
        binding.btnRequestNotif.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun updateBatteryStatus() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoring = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
        val ok = ignoring
        setRow(binding.rowBattery.icon, binding.rowBattery.title, binding.rowBattery.subtitle,
            ok, "Battery optimization", if (ok) "Battery optimization is disabled" else "Please disable battery optimization")
        binding.btnFixBattery.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun updatePowerSaverStatus() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val powerSaveOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) pm.isPowerSaveMode else false
        val ok = !powerSaveOn
        setRow(
            binding.rowPowerSaver.icon,
            binding.rowPowerSaver.title,
            binding.rowPowerSaver.subtitle,
            ok,
            "Power Saving",
            if (ok) "Power saver is off" else "Please turn off Battery Saver / Power Saver of your phone"
        )
        binding.btnBatterySaver.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun updateSoundStatus() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringModeOk = audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
        val notifVol = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        val notifMax = audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
        val volumeOk = notifVol >= (notifMax * 0.3).toInt()
        val dndOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as SysNm
            nm.currentInterruptionFilter == SysNm.INTERRUPTION_FILTER_ALL
        } else true
        val ok = ringModeOk && volumeOk && dndOk
        val msg = when {
            !ringModeOk -> "Ringer is silent or vibrate"
            !dndOk -> "Do Not Disturb may block sound"
            !volumeOk -> "Volume levels are low"
            else -> "Volume levels are high enough"
        }
        setRow(binding.rowSound.icon, binding.rowSound.title, binding.rowSound.subtitle, ok, "Sound Settings", msg)
        binding.btnSoundSettings.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun setRow(
        icon: ImageView,
        title: TextView,
        subtitle: TextView,
        ok: Boolean,
        titleText: String,
        subText: String
    ) {
        title.text = titleText
        subtitle.text = subText
        icon.setImageResource(if (ok) R.drawable.ic_check_in else R.drawable.ic_warning)
        icon.imageTintList = android.content.res.ColorStateList.valueOf(
            android.graphics.Color.parseColor(if (ok) "#2ecc71" else "#e67e22")
        )
        icon.setBackgroundResource(if (ok) R.drawable.bg_circle_green else R.drawable.bg_circle_red)
        val subColor = if (ok) getColorCompat(R.color.text_secondary) else Color.parseColor("#E74C3C")
        subtitle.setTextColor(subColor)
    }

    private fun openAppNotificationSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun openBatterySaverSettings() {
        try {
            startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Fallback to power usage summary (not part of Settings constants on all SDKs)
            try { startActivity(Intent("android.intent.action.POWER_USAGE_SUMMARY")) } catch (_: Exception) { }
        }
    }

    private fun openSoundSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        } catch (_: Exception) { }
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            android.widget.Toast.makeText(this, "Battery optimization already disabled", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        // 1) Open the general Battery optimization list (all apps view)
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) { /* fallthrough */ }
        // 2) Fallback: show the app-specific request dialog for this app
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        } catch (_: Exception) { /* fallthrough */ }
        // 3) OEM-specific pages
        if (openOemAutoStartSettings()) return
        // 4) Final fallback: app details
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) { }
    }

    private fun openOemAutoStartSettings(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val tried = arrayListOf<Intent>()
        fun tryStart(intent: Intent): Boolean {
            return try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: Exception) { false }
        }
        when {
            manufacturer.contains("xiaomi") -> {
                tried += Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                tried += Intent("miui.intent.action.OP_AUTO_START")
            }
            manufacturer.contains("oppo") -> {
                tried += Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                tried += Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            }
            manufacturer.contains("vivo") -> {
                tried += Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                tried += Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            }
            manufacturer.contains("huawei") -> {
                tried += Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            }
            manufacturer.contains("samsung") -> {
                tried += Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))
            }
            manufacturer.contains("oneplus") || manufacturer.contains("realme") -> {
                tried += Intent().setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            }
        }
        for (i in tried) if (tryStart(i)) return true
        return false
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun sendFullScreenTest() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = ensureAlertChannel(manager)
        lastChannelIdForTest = channelId

        val intent = Intent(this, FullScreenTestActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(this, 201, intent, piFlags)

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Test Notification")
            .setContentText("This is a test to verify full-screen alerts")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        manager.notify(9998, notif)
        showResultSheet()
    }

    private fun showResultSheet() {
        val dialog = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.dialog_test_result, null)
        dialog.setContentView(v)
        v.findViewById<View>(R.id.btnYes)?.setOnClickListener {
            // Mark success in the list and show Continue
            setRow(binding.rowResult.icon, binding.rowResult.title, binding.rowResult.subtitle,
                true, "Successfully received the notification", "")
            showContinueSheet()
            dialog.dismiss()
        }
        v.findViewById<View>(R.id.btnSound)?.setOnClickListener {
            showSoundIssueSheet()
            dialog.dismiss()
        }
        v.findViewById<View>(R.id.btnNo)?.setOnClickListener {
            openAppNotificationSettings()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showSoundIssueSheet() {
        val dialog = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.dialog_sound_issue, null)
        dialog.setContentView(v)
        v.findViewById<View>(R.id.btnResetDefault)?.setOnClickListener {
            val ok = resetAlertChannelSoundToDefault()
            android.widget.Toast.makeText(
                this,
                if (ok) "Notification sound reset to default" else "Open channel settings to change sound",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            if (!ok) openChannelSettings()
            dialog.dismiss()
        }
        v.findViewById<View>(R.id.btnOpenChannel)?.setOnClickListener {
            openChannelSettings()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showContinueSheet() {
        val dialog = BottomSheetDialog(this)
        val v = layoutInflater.inflate(R.layout.dialog_continue, null)
        dialog.setContentView(v)
        v.findViewById<View>(R.id.btnContinue)?.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialog.show()
    }
    private fun openChannelSettings() {
        val channelId = lastChannelIdForTest ?: run {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureAlertChannel(nm)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                }
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        openAppNotificationSettings()
    }
}

// Separate simple full-screen activity used by the test notification
class FullScreenTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_test)
        findViewById<View>(R.id.btnDismiss)?.setOnClickListener { finish() }
    }
}

// simple context color helper
private fun Context.getColorCompat(resId: Int): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        resources.getColor(resId, theme)
    } else {
        @Suppress("DEPRECATION")
        resources.getColor(resId)
    }
}

// Local copy of notification channel setup to avoid cross-file visibility issues
private fun Context.ensureAlertChannel(nm: NotificationManager): String {
    val channelId = "alerts_v5"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val existing = nm.getNotificationChannel(channelId)
        if (existing == null) {
            val soundUri = try {
                val resourceId = resources.getIdentifier("sleep_alert", "raw", packageName)
                if (resourceId != 0) {
                    Uri.parse("android.resource://$packageName/$resourceId")
                } else {
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                }
            } catch (_: Exception) {
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            }
            val ch = NotificationChannel(
                channelId,
                "Critical Sleep Alerts",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800)
                description = "Critical alerts and test notifications"
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(soundUri, attrs)
                setBypassDnd(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }
    return channelId
}

// Try to reset the alert channel sound to device default by deleting and recreating the channel.
private fun Context.resetAlertChannelSoundToDefault(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "alerts_v5"
    return try {
        // Delete and recreate with default notification sound
        nm.deleteNotificationChannel(channelId)
        val soundUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
        val ch = NotificationChannel(
            channelId,
            "Critical Sleep Alerts",
            NotificationManager.IMPORTANCE_MAX
        ).apply {
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800)
            description = "Critical alerts and test notifications"
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(soundUri, attrs)
            setBypassDnd(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
        true
    } catch (_: Exception) {
        false
    }
}


