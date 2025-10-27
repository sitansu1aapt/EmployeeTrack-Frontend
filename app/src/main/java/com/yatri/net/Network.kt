package com.yatri.net

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
// import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.yatri.AppConfig
import com.yatri.TokenStore
import okhttp3.Interceptor

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
                chain.proceed(authReq)
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


