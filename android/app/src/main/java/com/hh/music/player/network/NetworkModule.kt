package com.hh.music.player.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkModule {

    /**
     * Base URL of the backend Node/Express proxy.
     *
     * Emulators:    use http://10.0.2.2:3000/api/ to reach host localhost:3000
     * Real devices: use your PC LAN IP, e.g. http://192.168.x.x:3000/api/
     *               (update via app Settings when switching networks)
     *
     * Run the server with:  cd server && npm start
     */
    @Volatile var BASE_URL: String = "http://10.0.2.2:3000/api/"
        set(value) {
            field = value.trimEnd('/').let { if (it.endsWith("/api")) it else "$it/api/" }
            _api = null  // force rebuild on next access
        }

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Volatile private var _api: HHMusicApi? = null

    val api: HHMusicApi
        get() {
            val existing = _api
            if (existing != null) return existing
            return synchronized(this) {
                _api ?: Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(HHMusicApi::class.java)
                    .also { _api = it }
            }
        }
}
