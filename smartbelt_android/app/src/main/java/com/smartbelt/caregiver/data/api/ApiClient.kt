//Menyimpan Alamat Server (cukup ganti IP di 1 tempat jika server pindah).
//Kirim & Terima Data (mengantar pesan dari HP Android ke Server Backend dan mengambil jawabannya).
//Penerjemah Data (mengubah format data JSON dari server menjadi data yang siap dipakai HP Android).
package com.smartbelt.caregiver.data.api

import android.content.Context
import com.smartbelt.caregiver.data.pref.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String? = null

    fun getApiService(context: Context): ApiService {
        val sessionManager = SessionManager(context)
        val baseUrl = sessionManager.getBaseUrl()

        if (retrofit == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val authInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val token = sessionManager.getAuthToken()

                val requestBuilder = originalRequest.newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("ngrok-skip-browser-warning", "true")

                if (!token.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                chain.proceed(requestBuilder.build())
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        return retrofit!!.create(ApiService::class.java)
    }

    fun resetClient() {
        retrofit = null
        currentBaseUrl = null
    }
}
