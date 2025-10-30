package com.yatri.sleep

import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.app.KeyguardManager
import android.view.WindowManager
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yatri.R
import com.yatri.net.Network
import com.yatri.dataStore
import com.yatri.PrefKeys
import com.yatri.TokenStore
import com.yatri.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.create

class QuestionActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var selectedOption: String? = null
    private var hasSubmitted = false
    private var selectedButton: Button? = null
    private var initialDuration: Int = 30
    private var currentTimer: Int = 30
    private var alarmPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Acquire wake lock FIRST before anything else to wake the screen
        try {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager?.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Yatri:SleepAlert"
            )
            wakeLock?.acquire(30000) // Wake for 30 seconds
            android.util.Log.d("QuestionActivity", "Wake lock acquired")
        } catch (e: Exception) {
            android.util.Log.e("QuestionActivity", "Failed to acquire wake lock", e)
        }
        
        setContentView(R.layout.activity_question)

        // Ensure auth token is available for API (load from DataStore if app was cold-started)
        scope.launch(Dispatchers.IO) {
            runCatching { applicationContext.dataStore.data.first()[PrefKeys.AUTH_TOKEN] }
                .onSuccess { tok -> if (!tok.isNullOrEmpty()) TokenStore.token = tok }
        }

        // Bring screen up over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Request keyguard dismissal so UI is immediately interactable on lock screen
        try {
            val kgm = getSystemService(KeyguardManager::class.java)
            kgm?.requestDismissKeyguard(this, null)
        } catch (_: Exception) { }

        // Start continuous alarm sound similar to React Native behavior
        startAlarmSound()

        val sessionId = intent.getStringExtra("session_id") ?: ""
        val questionId = intent.getStringExtra("question_id") ?: ""
        val questionText = intent.getStringExtra("question_text") ?: "Sleep alert"
        val optionsJson = intent.getStringExtra("options") ?: "[]"
        val duration = intent.getIntExtra("duration_seconds", 30)
        
        // Store initial duration for calculation
        initialDuration = duration
        currentTimer = duration

        findViewById<TextView>(R.id.tvQuestion).text = questionText
        val options = parseOptions(optionsJson)
        val container = findViewById<LinearLayout>(R.id.containerOptions)
        if (options.isEmpty()) {
            android.util.Log.w("QuestionActivity", "No options parsed from payload: $optionsJson")
        } else {
            options.forEachIndexed { index, opt ->
                val btn = Button(this).apply {
                    text = opt.text
                    isAllCaps = false
                    isClickable = true
                    isFocusable = true
                    background = resources.getDrawable(R.drawable.bg_option_default, theme)
                    setOnClickListener {
                        android.util.Log.d("QuestionActivity", "Option clicked index=$index id=${opt.id} text='${opt.text}'")
                        selectedOption = opt.id
                        // visual selection highlight
                        selectedButton?.background = resources.getDrawable(R.drawable.bg_option_default, theme)
                        background = resources.getDrawable(R.drawable.bg_option_selected, theme)
                        selectedButton = this
                    }
                }
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = (12 * resources.displayMetrics.density).toInt()
                container.addView(btn, lp)
            }
        }

        val tvTimer = findViewById<TextView>(R.id.tvTimer)
        object : CountDownTimer((duration * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                currentTimer = (millisUntilFinished / 1000).toInt()
                tvTimer.text = "Time left: ${currentTimer}s"
            }
            override fun onFinish() { 
                currentTimer = 0
                stopAlarmSound()
                submit(sessionId, questionId, selectedOption) 
            }
        }.start()

        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            android.util.Log.d("QuestionActivity", "Submit clicked with selectedOption=$selectedOption")
            stopAlarmSound()
            submit(sessionId, questionId, selectedOption)
        }
    }

    private fun startAlarmSound() {
        try {
            // Prefer custom sleep_alert sound if present
            val resId = resources.getIdentifier("sleep_alert", "raw", packageName)
            val uri: Uri = if (resId != 0) {
                Uri.parse("android.resource://$packageName/$resId")
            } else {
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
            }

            val player = MediaPlayer()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                player.setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
            }
            player.isLooping = true
            player.setDataSource(this, uri)
            player.setVolume(1f, 1f)
            player.prepare()
            player.start()
            alarmPlayer = player
            android.util.Log.d("QuestionActivity", "Alarm sound started (looping)")
        } catch (e: Exception) {
            android.util.Log.e("QuestionActivity", "Failed to start alarm sound: ${e.message}")
        }
    }

    private fun stopAlarmSound() {
        try {
            alarmPlayer?.let { p ->
                if (p.isPlaying) p.stop()
                p.reset()
                p.release()
            }
        } catch (_: Exception) { }
        alarmPlayer = null
    }

    override fun onDestroy() {
        stopAlarmSound()
        // Release wake lock
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            android.util.Log.e("QuestionActivity", "Failed to release wake lock", e)
        }
        super.onDestroy()
    }

    private fun submit(sessionId: String, questionId: String, optionId: String?) {
        if (hasSubmitted) return
        hasSubmitted = true
        
        // Calculate duration taken (time elapsed)
        val durationTakenSeconds = initialDuration - currentTimer
        
        android.util.Log.d("QuestionActivity", "=== SUBMIT ANSWER API CALL ===")
        android.util.Log.d("QuestionActivity", "Question ID: $questionId")
        android.util.Log.d("QuestionActivity", "Selected Option ID: $optionId")
        android.util.Log.d("QuestionActivity", "Session ID: $sessionId")
        android.util.Log.d("QuestionActivity", "Duration Taken: ${durationTakenSeconds}s")
        android.util.Log.d("QuestionActivity", "Initial Duration: ${initialDuration}s")
        android.util.Log.d("QuestionActivity", "Current Timer: ${currentTimer}s")
        
        scope.launch(Dispatchers.IO) {
            try {
                val api = Network.retrofit.create<SleepApi>()
                val requestBody = SleepAnswerBody(
                    question_id = questionId,
                    selected_option_id = optionId,
                    session_id = sessionId,
                    duration_taken_seconds = durationTakenSeconds
                )
                
                val fullUrl = "${AppConfig.API_BASE_URL}sleep-tracking/submit-answer"
                android.util.Log.d("QuestionActivity", "Making API call to: $fullUrl")
                android.util.Log.d("QuestionActivity", "Method: POST")
                android.util.Log.d("QuestionActivity", "Request body: $requestBody")
                
                val response = api.submitAnswer(requestBody)
                
                android.util.Log.d("QuestionActivity", "=== SUBMIT ANSWER API RESPONSE ===")
                android.util.Log.d("QuestionActivity", "URL: $fullUrl")
                android.util.Log.d("QuestionActivity", "Status Code: ${response.code()}")
                
                if (response.isSuccessful) {
                    android.util.Log.d("QuestionActivity", "Status: success")
                    android.util.Log.d("QuestionActivity", "Submit answer completed successfully")
                } else {
                    android.util.Log.e("QuestionActivity", "Status: error")
                    android.util.Log.e("QuestionActivity", "HTTP Error Code: ${response.code()}")
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("QuestionActivity", "Error Response Body: $errorBody")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("QuestionActivity", "=== SUBMIT ANSWER ERROR ===")
                android.util.Log.e("QuestionActivity", "Error Type: ${e.javaClass.simpleName}")
                android.util.Log.e("QuestionActivity", "Error Message: ${e.message}")
                android.util.Log.e("QuestionActivity", "Error Details:", e)
                
                // Check if it's an HTTP error
                if (e is retrofit2.HttpException) {
                    android.util.Log.e("QuestionActivity", "HTTP Error Code: ${e.code()}")
                    try {
                        val errorBody = e.response()?.errorBody()?.string()
                        android.util.Log.e("QuestionActivity", "Error Response Body: $errorBody")
                    } catch (ex: Exception) {
                        android.util.Log.e("QuestionActivity", "Failed to read error body: ${ex.message}")
                    }
                }
            }
            
            launch(Dispatchers.Main) {
                Toast.makeText(this@QuestionActivity, "Submitted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

@Serializable
data class Option(val id: String, val text: String)

@Serializable
data class SleepAnswerBody(
    val question_id: String,
    val selected_option_id: String? = null,
    val session_id: String,
    val duration_taken_seconds: Int
)

interface SleepApi {
    @POST("sleep-tracking/submit-answer")
    suspend fun submitAnswer(@Body body: SleepAnswerBody): Response<Unit>
}

private fun parseOptions(json: String): List<Option> {
    return try {
        val el: JsonElement = Json.parseToJsonElement(json)
        if (el is JsonArray) {
            el.mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val text = obj["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
                Option(id = id, text = text)
            }
        } else emptyList()
    } catch (e: Exception) {
        android.util.Log.e("QuestionActivity", "Failed to parse options", e)
        emptyList()
    }
}


