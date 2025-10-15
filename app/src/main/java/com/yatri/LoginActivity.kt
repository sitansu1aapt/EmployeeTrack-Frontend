package com.yatri

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.datastore.preferences.core.edit
import com.google.firebase.messaging.FirebaseMessaging
import com.yatri.net.Network
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.create
import kotlin.coroutines.resume

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val acType = findViewById<MaterialAutoCompleteTextView>(R.id.acIdentifierType)
        val etIdentifier = findViewById<EditText>(R.id.etIdentifier)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        val typeItems = listOf("Employee ID", "Email", "Phone")
        acType.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, typeItems))
        acType.setText(typeItems.first(), false)

        // Set default values for Employee ID and Password
        etIdentifier.setText("EMP800")
        etPassword.setText("Pass1234")

        val authApi = Network.retrofit.create<AuthApi>()
        val usersApi = Network.retrofit.create<UsersApi>()
        btnLogin.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val selected = acType.text?.toString() ?: "Employee ID"
                    val identifierType = when (selected) {
                        "Employee ID" -> "empId"
                        "Email" -> "email"
                        else -> "phone"
                    }
                    val identifier = etIdentifier.text?.toString()?.trim().orEmpty()
                    val password = etPassword.text?.toString()?.trim().orEmpty()
                    if (identifier.isEmpty() || password.isEmpty()) {
                        Toast.makeText(this@LoginActivity, "Enter credentials", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val request = LoginRequest(
                        organizationId = 1,
                        identifierType = identifierType,
                        empId = if (identifierType == "empId") identifier else null,
                        email = if (identifierType == "email") identifier else null,
                        phone = if (identifierType == "phone") identifier else null,
                        password = password
                    )

                    val env = authApi.login(request)
                    if (env.status != null && env.status != "success") {
                        error(env.message ?: "Login failed")
                    }
                    val resp = env.data ?: error(env.message ?: "Invalid login response")
                    val token = resp.token?.authToken ?: error("Invalid login response")

                    saveAuth(token)
                    TokenStore.token = token

                    val fcm = getFcmTokenOrNull()
                    if (!fcm.isNullOrEmpty()) {
                        runCatching {
                            usersApi.updateFcmToken(FcmTokenBody(fcm))
                            android.util.Log.d("LoginActivity", "FCM token updated successfully: $fcm")
                        }.onFailure {
                            android.util.Log.e("LoginActivity", "Failed to update FCM token", it)
                        }
                    }

                    Toast.makeText(this@LoginActivity, "Login success", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, EmployeeActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.util.Log.e("Login", "login error", e)
                    Toast.makeText(this@LoginActivity, e.message ?: "Login failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun saveAuth(token: String) {
        applicationContext.dataStore.edit {
            it[PrefKeys.AUTH_TOKEN] = token
        }
    }

    private suspend fun getFcmTokenOrNull(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    }
}

@Serializable
data class LoginRequest(
    val organizationId: Int,
    val identifierType: String,
    val empId: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val password: String
)

@Serializable
data class TokenDTO(val authToken: String)

@Serializable
data class RoleDTO(val role_id: Int, val role_name: String)

@Serializable
data class UserDTO(val id: String? = null)

@Serializable
data class LoginResponse(val token: TokenDTO? = null, val roles: List<RoleDTO> = emptyList(), val user: UserDTO? = null)

@Serializable
data class LoginEnvelope(val status: String? = null, val data: LoginResponse? = null, val message: String? = null)

@Serializable
data class FcmTokenBody(val fcm_token: String)

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginEnvelope
}

interface UsersApi {
    @PUT("users/me/fcm-token")
    suspend fun updateFcmToken(@Body body: FcmTokenBody)
}
