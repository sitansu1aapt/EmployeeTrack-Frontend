package com.yatri.chat

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.yatri.AppConfig
import com.yatri.ChatMessage
import com.yatri.TokenStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Date

/**
 * Mirrors yatriApp [firebaseAuthService.ensureFirebaseBootstrap] + [firestoreChatService.listenMessages].
 */
object FirebaseChatBootstrap {

    private const val TAG = "FirebaseChat"
    private val http = OkHttpClient()

    /**
     * POST /auth/firebase-custom-token then sign in with Firebase Auth.
     * Returns true if a Firebase user session exists afterward.
     */
    fun ensureSignedIn(): Boolean {
        val jwt = TokenStore.token
        if (jwt.isNullOrBlank()) {
            Log.w(TAG, "No backend JWT for Firebase bootstrap")
            return false
        }
        val current = FirebaseAuth.getInstance().currentUser
        if (current != null) return true

        return try {
            val req = Request.Builder()
                .url("${AppConfig.API_BASE_URL}auth/firebase-custom-token")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $jwt")
                .header("Accept", "application/json")
                .build()
            val resp = http.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.e(TAG, "firebase-custom-token HTTP ${resp.code}: ${body.take(200)}")
                return false
            }
            val json = JSONObject(body)
            val data = json.optJSONObject("data")
            val customToken = when {
                data != null && data.has("firebase_token") -> data.optString("firebase_token")
                json.has("firebase_token") -> json.optString("firebase_token")
                data != null && data.has("customToken") -> data.optString("customToken")
                else -> ""
            }
            if (customToken.isBlank()) {
                Log.e(TAG, "firebase token missing in response")
                return false
            }
            Tasks.await(FirebaseAuth.getInstance().signInWithCustomToken(customToken))
            FirebaseAuth.getInstance().currentUser != null
        } catch (e: Exception) {
            Log.e(TAG, "ensureSignedIn failed", e)
            false
        }
    }

    private fun mapFirestoreDoc(doc: DocumentSnapshot, currentUserId: String): ChatMessage {
        val data = doc.data ?: return ChatMessage(
            id = doc.id,
            text = "",
            isSentByMe = false,
            timestamp = System.currentTimeMillis(),
            isDeleted = true,
        )
        val senderUserId = (data["senderUserId"] ?: data["sender_user_id"] ?: "").toString()
        val content = (data["content"] ?: "").toString()
        val isDeleted = data["isDeleted"] as? Boolean == true || data["is_deleted"] as? Boolean == true
        val createdAt = data["createdAt"] ?: data["created_at"]
        val ts = when (createdAt) {
            is com.google.firebase.Timestamp -> createdAt.toDate().time
            is Date -> createdAt.time
            else -> System.currentTimeMillis()
        }
        val messageId = (data["messageId"] ?: data["message_id"] ?: doc.id).toString()
        return ChatMessage(
            id = messageId,
            text = content,
            isSentByMe = senderUserId == currentUserId,
            timestamp = ts,
            isDeleted = isDeleted,
        )
    }

    fun listenMessages(
        conversationId: String,
        currentUserId: String,
        onUpdate: (List<ChatMessage>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration? {
        if (FirebaseAuth.getInstance().currentUser == null) {
            onError(IllegalStateException("Firebase not signed in"))
            return null
        }
        val query = FirebaseFirestore.getInstance()
            .collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        return query.addSnapshotListener { snapshot, e ->
            if (e != null) {
                onError(e)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            val list = snapshot.documents.map { mapFirestoreDoc(it, currentUserId) }
            onUpdate(list)
        }
    }
}
