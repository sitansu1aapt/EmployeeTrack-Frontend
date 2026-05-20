package com.yatri

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.ListenerRegistration
import com.yatri.chat.FirebaseChatBootstrap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

/**
 * Employee ↔ admin/supervisor chat aligned with yatriApp [ChatDetailScreen]:
 * REST [messageAPI] for history/send/read, Firestore listener when [firestore_conversation_id] is known.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: FloatingActionButton
    private lateinit var btnBack: ImageButton
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    private var receiverUserId: String = ""
    private var currentUserId: String = ""
    private var firestoreConversationId: String? = null
    private var firestoreRegistration: ListenerRegistration? = null

    /** REST poll while screen visible — covers Firestore issues; interval matches typical UX. */
    private val restPollRunnable = object : Runnable {
        override fun run() {
            if (isFinishing || receiverUserId.isEmpty()) return
            loadInitialMessages()
            handler.postDelayed(this, 12_000L)
        }
    }

    companion object {
        private const val TAG = "ChatActivity"
        private const val LOG_BODY_MAX = 4000

        /** Logs full REST context for debugging (never logs the JWT). */
        private fun logApiFailure(
            operation: String,
            url: String,
            httpCode: Int,
            responseBody: String,
            tokenPresent: Boolean,
            receiverUserId: String,
            currentUserId: String,
        ) {
            val sample =
                if (responseBody.length > LOG_BODY_MAX) {
                    responseBody.take(LOG_BODY_MAX) + "…(truncated)"
                } else {
                    responseBody
                }
            Log.e(
                TAG,
                buildString {
                    append("[")
                    append(operation)
                    append("] FAILED\n")
                    append("  url=")
                    append(url)
                    append("\n  httpCode=")
                    append(httpCode)
                    append("\n  receiverUserId=")
                    append(receiverUserId)
                    append(" currentUserId=")
                    append(currentUserId)
                    append("\n  tokenPresent=")
                    append(tokenPresent)
                    append("\n  responseBody=")
                    append(sample)
                },
            )
        }

        private fun logApiRequest(
            operation: String,
            url: String,
            receiverUserId: String,
            currentUserId: String,
            tokenPresent: Boolean,
        ) {
            Log.d(
                TAG,
                "[$operation] request url=$url receiverUserId=$receiverUserId " +
                    "currentUserId=$currentUserId tokenPresent=$tokenPresent",
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnBack = findViewById(R.id.btnBack)

        receiverUserId = intent.getStringExtra(EXTRA_RECEIVER_USER_ID).orEmpty()
        if (receiverUserId.isEmpty()) {
            val legacy = intent.getIntExtra("adminId", -1)
            if (legacy != -1) receiverUserId = legacy.toString()
        }

        val adminName = intent.getStringExtra("adminName") ?: "Department Admin"
        val adminRole = intent.getStringExtra("adminRole") ?: "Supervisor"

        findViewById<TextView>(R.id.tvChatTitle).text = adminName
        findViewById<TextView>(R.id.tvChatSubtitle).text = adminRole

        currentUserId = UserContext.userId ?: loadUserIdFromDataStore()
        if (currentUserId.isEmpty()) {
            TokenStore.token?.let { JwtPayload.userId(it) }?.let { currentUserId = it }
        }

        adapter = ChatAdapter(messages)
        rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvMessages.adapter = adapter

        btnBack.setOnClickListener { finish() }

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty() && receiverUserId.isNotEmpty()) {
                performSendMessage(text)
                etMessage.text.clear()
            }
        }

        if (receiverUserId.isEmpty()) {
            Toast.makeText(this, "No contact selected for messaging", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadInitialMessages()
        markConversationAsRead()
    }

    private fun loadUserIdFromDataStore(): String =
        try {
            runBlocking { applicationContext.dataStore.data.first()[PrefKeys.USER_ID] ?: "" }
        } catch (_: Exception) {
            ""
        }

    override fun onResume() {
        super.onResume()
        if (receiverUserId.isNotEmpty()) {
            markConversationAsRead()
            handler.removeCallbacks(restPollRunnable)
            handler.postDelayed(restPollRunnable, 12_000L)
        }
    }

    override fun onPause() {
        handler.removeCallbacks(restPollRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(restPollRunnable)
        firestoreRegistration?.remove()
        firestoreRegistration = null
        super.onDestroy()
    }

    private fun loadInitialMessages() {
        thread {
            try {
                val url = "${AppConfig.API_BASE_URL}messages/$receiverUserId?limit=50&offset=0"
                val token = TokenStore.token
                val tokenPresent = !token.isNullOrBlank()
                logApiRequest("GET messages", url, receiverUserId, currentUserId, tokenPresent)
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    logApiFailure(
                        "GET messages",
                        url,
                        resp.code,
                        body,
                        tokenPresent,
                        receiverUserId,
                        currentUserId,
                    )
                    handler.post {
                        Toast.makeText(this@ChatActivity, "Could not load messages (${resp.code})", Toast.LENGTH_LONG).show()
                    }
                    return@thread
                }
                Log.d(TAG, "[GET messages] ok httpCode=${resp.code} bytes=${body.length}")
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: JSONObject()
                // Empty array is valid — backend still returns firestore_conversation_id for the listener
                val arr = data.optJSONArray("messages") ?: JSONArray()
                val parsed = mutableListOf<ChatMessage>()
                var convId: String? = firestoreConversationId
                val fromApi = data.optString("firestore_conversation_id", "")
                if (fromApi.isNotEmpty()) convId = fromApi
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    parsed.add(parseBackendMessage(o))
                    if (convId.isNullOrEmpty()) {
                        val f = o.optString("firestore_conversation_id", "")
                        if (f.isNotEmpty()) convId = f
                    }
                }
                handler.post {
                    firestoreConversationId = convId ?: firestoreConversationId
                    adapter.replaceAll(parsed)
                    if (parsed.isNotEmpty()) {
                        rvMessages.scrollToPosition(parsed.size - 1)
                    }
                    maybeStartFirestoreListener()
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadInitialMessages", e)
                handler.post {
                    Toast.makeText(this@ChatActivity, "Failed to load messages", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseBackendMessage(o: JSONObject): ChatMessage {
        val sender = o.optString("sender_user_id", "")
        val msgId = o.optString("message_id", "")
        val content = o.optString("content", "")
        val deleted = o.optBoolean("is_deleted", false)
        val createdAt = o.optString("created_at", "")
        val ts = parseIsoTimestamp(createdAt)
        val me = currentUserId.isNotEmpty() && sender == currentUserId
        return ChatMessage(
            id = msgId.ifEmpty { "m_${System.nanoTime()}" },
            text = content,
            isSentByMe = me,
            timestamp = ts,
            isDeleted = deleted,
        )
    }

    private fun parseIsoTimestamp(iso: String): Long {
        if (iso.isEmpty()) return System.currentTimeMillis()
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" to TimeZone.getTimeZone("UTC"),
            "yyyy-MM-dd'T'HH:mm:ss'Z'" to TimeZone.getTimeZone("UTC"),
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" to null,
            "yyyy-MM-dd HH:mm:ss" to null,
        )
        for ((pattern, tz) in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (tz != null) sdf.timeZone = tz
                sdf.parse(iso)?.time?.let { return it }
            } catch (_: Exception) { }
        }
        return System.currentTimeMillis()
    }

    private fun maybeStartFirestoreListener() {
        val convId = firestoreConversationId
        if (convId.isNullOrEmpty()) return
        if (firestoreRegistration != null) return

        thread {
            val ok = FirebaseChatBootstrap.ensureSignedIn()
            if (!ok) {
                Log.w(TAG, "Firebase sign-in failed; chat works via REST only")
                return@thread
            }
            val uid = currentUserId.ifEmpty {
                loadUserIdFromDataStore().also { currentUserId = it }
            }.ifEmpty {
                TokenStore.token?.let { JwtPayload.userId(it) }.orEmpty().also { if (it.isNotEmpty()) currentUserId = it }
            }
            if (uid.isEmpty()) {
                Log.w(TAG, "No current user id; Firestore bubble alignment may be wrong")
            }
            handler.post {
                firestoreRegistration?.remove()
                firestoreRegistration = FirebaseChatBootstrap.listenMessages(
                    conversationId = convId,
                    currentUserId = uid,
                    onUpdate = { list ->
                        adapter.replaceAll(list)
                        if (list.isNotEmpty()) {
                            rvMessages.scrollToPosition(list.size - 1)
                        }
                    },
                    onError = { e ->
                        Log.e(TAG, "Firestore listener", e)
                        loadInitialMessages()
                    },
                )
            }
        }
    }

    private fun performSendMessage(text: String) {
        thread {
            try {
                val url = "${AppConfig.API_BASE_URL}messages/send"
                val token = TokenStore.token
                val tokenPresent = !token.isNullOrBlank()
                logApiRequest("POST messages/send", url, receiverUserId, currentUserId, tokenPresent)
                val json = JSONObject().apply {
                    put("receiver_id", receiverUserId)
                    put("content", text)
                }
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    logApiFailure(
                        "POST messages/send",
                        url,
                        resp.code,
                        body,
                        tokenPresent,
                        receiverUserId,
                        currentUserId,
                    )
                    handler.post {
                        Toast.makeText(this@ChatActivity, "Could not send message (${resp.code})", Toast.LENGTH_LONG).show()
                        etMessage.setText(text)
                    }
                    return@thread
                }
                Log.d(TAG, "[POST messages/send] ok httpCode=${resp.code}")
                val jsonResp = JSONObject(body)
                val data = jsonResp.optJSONObject("data")
                if (data != null) {
                    val f = data.optString("firestore_conversation_id", "")
                    if (f.isNotEmpty()) {
                        firestoreConversationId = f
                    }
                }
                handler.post {
                    firestoreConversationId?.let { maybeStartFirestoreListener() }
                    loadInitialMessages()
                }
            } catch (e: Exception) {
                Log.e(TAG, "performSendMessage", e)
                handler.post {
                    Toast.makeText(this@ChatActivity, "Send failed", Toast.LENGTH_SHORT).show()
                    etMessage.setText(text)
                }
            }
        }
    }

    private fun markConversationAsRead() {
        thread {
            try {
                val url = "${AppConfig.API_BASE_URL}messages/$receiverUserId/read"
                val token = TokenStore.token
                val tokenPresent = !token.isNullOrBlank()
                logApiRequest("PUT messages/.../read", url, receiverUserId, currentUserId, tokenPresent)
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .put("".toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        logApiFailure(
                            "PUT messages/read",
                            url,
                            resp.code,
                            body,
                            tokenPresent,
                            receiverUserId,
                            currentUserId,
                        )
                    } else {
                        Log.d(TAG, "[PUT messages/read] ok httpCode=${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "markConversationAsRead", e)
            }
        }
    }
}

const val EXTRA_RECEIVER_USER_ID = "receiverUserId"
