package com.yatri.sleep

import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
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
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.create

class QuestionActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var selectedOption: String? = null
    private var hasSubmitted = false
    private var selectedButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val sessionId = intent.getStringExtra("session_id") ?: ""
        val questionId = intent.getStringExtra("question_id") ?: ""
        val questionText = intent.getStringExtra("question_text") ?: "Sleep alert"
        val optionsJson = intent.getStringExtra("options") ?: "[]"
        val duration = intent.getIntExtra("duration_seconds", 30)

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
                tvTimer.text = "Time left: ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() { submit(sessionId, questionId, selectedOption, duration) }
        }.start()

        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            android.util.Log.d("QuestionActivity", "Submit clicked with selectedOption=$selectedOption")
            submit(sessionId, questionId, selectedOption, duration)
        }
    }

    private fun submit(sessionId: String, questionId: String, optionId: String?, duration: Int) {
        if (hasSubmitted) return
        hasSubmitted = true
        scope.launch(Dispatchers.IO) {
            try {
                val api = Network.retrofit.create<SleepApi>()
                api.submitAnswer(
                    SleepAnswerBody(
                        question_id = questionId,
                        selected_option_id = optionId,
                        session_id = sessionId,
                        duration_taken_seconds = duration
                    )
                )
            } catch (_: Exception) { }
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
    suspend fun submitAnswer(@Body body: SleepAnswerBody)
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


