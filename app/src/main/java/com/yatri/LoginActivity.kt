package com.yatri

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.CheckBox
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
import com.yatri.localization.LocalizationManager
import retrofit2.http.PUT
import retrofit2.create
import java.util.Locale
import kotlin.coroutines.resume

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize localization
        LocalizationManager.initialize(this)
        
        val acLanguage = findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.acLanguage)
        val languageOptions = listOf(getString(R.string.odia), getString(R.string.english))
        val languageAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languageOptions)
        acLanguage.setAdapter(languageAdapter)
        acLanguage.setText(getString(R.string.select_language), false)
        acLanguage.setOnItemClickListener { _, _, position, _ ->
            val langCode = if (position == 0) "or" else "en"
            LocalizationManager.setLanguage(this, langCode)
            recreate()
        }

        val acType = findViewById<MaterialAutoCompleteTextView>(R.id.acIdentifierType)
        val etIdentifier = findViewById<EditText>(R.id.etIdentifier)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvWelcome = findViewById<android.widget.TextView>(R.id.tvWelcome)
        val tvSignIn = findViewById<android.widget.TextView>(R.id.tvSignIn)
        val cbRemember = findViewById<CheckBox>(R.id.cbRemember)
        val tvForgot = findViewById<android.widget.TextView>(R.id.tvForgot)

        tvWelcome.text = getString(R.string.welcome_back)
        tvSignIn.text = getString(R.string.sign_in_to_continue)
        // cbRemember doesn't need text setting as it's handled in XML
        tvForgot.text = getString(R.string.forgot_password)
        etIdentifier.hint = getString(R.string.enter_employee_id)
        etPassword.hint = getString(R.string.enter_password)
        btnLogin.text = getString(R.string.login)

        val typeItems = listOf(getString(R.string.employee_id), getString(R.string.email), getString(R.string.phone_number))
        acType.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, typeItems))
        acType.setText(typeItems.first(), false)

        // Set default values for Employee ID and Password
        etIdentifier.setText("EMP1118")
        etPassword.setText("Password123")

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

                    // Log login request details
                    val loginUrl = "${AppConfig.API_BASE_URL}auth/login"
                    android.util.Log.d("LoginActivity", "=== LOGIN API REQUEST ===")
                    android.util.Log.d("LoginActivity", "URL: $loginUrl")
                    android.util.Log.d("LoginActivity", "Method: POST")
                    android.util.Log.d("LoginActivity", "Organization ID: ${request.organizationId}")
                    android.util.Log.d("LoginActivity", "Identifier Type: ${request.identifierType}")
                    android.util.Log.d("LoginActivity", "Identifier: $identifier")
                    android.util.Log.d("LoginActivity", "Password: ${"*".repeat(password.length)}")
                    
                    val env = authApi.login(request)
                    
                    // Log login response details
                    android.util.Log.d("LoginActivity", "=== LOGIN API RESPONSE ===")
                    android.util.Log.d("LoginActivity", "Status: ${env.status}")
                    android.util.Log.d("LoginActivity", "Message: ${env.message}")
                    android.util.Log.d("LoginActivity", "Data: ${env.data}")
                    android.util.Log.d("LoginActivity", "Token: ${env.data?.token?.authToken?.take(30)}...")
                    android.util.Log.d("LoginActivity", "User: ${env.data?.user}")
                    android.util.Log.d("LoginActivity", "Roles: ${env.data?.roles}")
                    if (env.status != null && env.status != "success") {
                        error(env.message ?: "Login failed")
                    }
                    val resp = env.data ?: error(env.message ?: "Invalid login response")
                    val token = resp.token?.authToken ?: error("Invalid login response")

                    // IMPORTANT: Set TokenStore.token FIRST before any API calls
                    TokenStore.token = token
                    android.util.Log.d("LoginActivity", "Token set in TokenStore: ${token.take(20)}...")
                    
                    // Save token to DataStore
                    saveAuth(token)

                    // Now update FCM token - this API call will use the token from TokenStore
                    val fcm = getFcmTokenOrNull()
                    if (!fcm.isNullOrEmpty()) {
                        val fcmUrl = "${AppConfig.API_BASE_URL}users/me/fcm-token"
                        android.util.Log.d("LoginActivity", "Updating FCM token at URL: $fcmUrl")
                        android.util.Log.d("LoginActivity", "FCM token: ${fcm.take(50)}...")
                        runCatching {
                            usersApi.updateFcmToken(FcmTokenBody(fcm))
                            android.util.Log.d("LoginActivity", "FCM token updated successfully")
                        }.onFailure {
                            android.util.Log.e("LoginActivity", "Failed to update FCM token", it)
                        }
                    } else {
                        android.util.Log.w("LoginActivity", "FCM token is null or empty, skipping update")
                    }

                    // Persist simple header fields for Profile
                    applicationContext.dataStore.edit {
                        val displayName = resp.user?.full_name
                            ?: resp.user?.email
                            ?: resp.user?.id
                            ?: "Employee"
                        it[PrefKeys.USER_NAME] = displayName
                        if (!resp.roles.isNullOrEmpty()) {
                            it[PrefKeys.ACTIVE_ROLE_ID] = resp.roles.first().role_id.toString()
                            it[PrefKeys.ACTIVE_ROLE_NAME] = resp.roles.first().role_name
                        }
                    }
                    Toast.makeText(this@LoginActivity, "Login success", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, EmployeeActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.util.Log.e("LoginActivity", "=== LOGIN ERROR ===")
                    android.util.Log.e("LoginActivity", "Error Type: ${e.javaClass.simpleName}")
                    android.util.Log.e("LoginActivity", "Error Message: ${e.message}")
                    android.util.Log.e("LoginActivity", "Error Details:", e)
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
data class UserDTO(
    val id: String? = null,
    val full_name: String? = null,
    val email: String? = null
)

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
