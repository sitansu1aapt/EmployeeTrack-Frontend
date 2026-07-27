package com.yatri.net

import kotlinx.serialization.json.Json
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
// import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.yatri.AppConfig
import com.yatri.TokenStore
import okhttp3.Interceptor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.yatri.AppContext
import com.yatri.UserContext
import com.yatri.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object Network {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .apply {
            // Commented out logging interceptor for release builds
            // if (AppConfig.LOG_HTTP) {
            //     val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            //     addInterceptor(logging)
            // }
            addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val tok = TokenStore.token
                android.util.Log.d("Network", "Using token: $tok")
                if (tok.isNullOrBlank()) {
                    android.util.Log.e("Network", "NO TOKEN: Not adding Authorization header!")
                    return@Interceptor chain.proceed(req)
                }
                val authReq = req.newBuilder()
                    .addHeader("Authorization", "Bearer $tok")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()
                android.util.Log.d("Network", "Request headers: ${authReq.headers}")
                val startNs = System.nanoTime()
                val response = chain.proceed(authReq)
                val durationMs = ((System.nanoTime() - startNs) / 1_000_000L).toInt()
                try {
                    val ctx = AppContext.context
                    val cm = ctx?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val net = cm?.activeNetwork
                    val caps = net?.let { cm.getNetworkCapabilities(it) }
                    val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    val connection = when {
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "vpn"
                        else -> "unknown"
                    }
                    val bundle = android.os.Bundle().apply {
                        putString("url", authReq.url.encodedPath)
                        putString("method", authReq.method)
                        putInt("status_code", response.code)
                        putInt("duration_ms", durationMs)
                        putString("connection", connection)
                        putString("connected", connected.toString())
                        // user context
                        putString("user_id", UserContext.userId)
                        putString("user_email", UserContext.userEmail)
                        putString("user_name", UserContext.userName)
                        putString("role", UserContext.roleName)
                        putString("organization_id", UserContext.orgId)
                        putString("site_id", UserContext.siteId)
                        putString("department_id", UserContext.deptId)
                    }
                    Firebase.analytics.logEvent("api_call", bundle)
                    if (response.code >= 400) {
                        val errorSnippet = try { response.peekBody(1024).string().take(200) } catch (e: Exception) { null }
                        val eb = android.os.Bundle().apply {
                            putString("url", authReq.url.encodedPath)
                            putString("method", authReq.method)
                            putInt("status_code", response.code)
                            putInt("duration_ms", durationMs)
                            putString("error_body", errorSnippet)
                            putString("connection", connection)
                            putString("connected", connected.toString())
                            // user context
                            putString("user_id", UserContext.userId)
                            putString("user_email", UserContext.userEmail)
                            putString("user_name", UserContext.userName)
                            putString("role", UserContext.roleName)
                            putString("organization_id", UserContext.orgId)
                            putString("site_id", UserContext.siteId)
                            putString("department_id", UserContext.deptId)
                            // Handset hardware telemetry for online diagnosis
                            putString("device_model", android.os.Build.MODEL)
                            putString("device_manufacturer", android.os.Build.MANUFACTURER)
                            putString("device_brand", android.os.Build.BRAND)
                            putString("android_sdk", android.os.Build.VERSION.SDK_INT.toString())
                            putString("android_release", android.os.Build.VERSION.RELEASE)
                        }
                        Firebase.analytics.logEvent("api_error", eb)
                    }
                } catch (_: Exception) { }
                response
            })
            .addInterceptor(Interceptor { chain ->
                val response = chain.proceed(chain.request())
                // Check for 401 Unauthorized response
                if (response.code == 401) {
                    android.util.Log.w("Network", "Received 401 Unauthorized - clearing token and DataStore")
                    // Clear the token from memory
                    TokenStore.token = null
                    // Asynchronously clear DataStore from disk to prevent persistent auto-login loop
                    try {
                        val ctx = AppContext.context
                        if (ctx != null) {
                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    ctx.dataStore.edit { it.clear() }
                                    android.util.Log.i("Network", "DataStore cleared successfully on 401")
                                } catch (e: Exception) {
                                    android.util.Log.e("Network", "Error clearing DataStore on 401: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Network", "Failed to clear DataStore on 401", e)
                    }
                }
                response
            })
        }
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.API_BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .client(client)
        .build()
}


