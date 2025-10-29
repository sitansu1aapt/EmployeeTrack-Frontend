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
                    android.util.Log.w("Network", "Received 401 Unauthorized - clearing token")
                    // Clear the token from memory
                    TokenStore.token = null
                    // Note: DataStore will be cleared when user manually logs out or app restarts
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


