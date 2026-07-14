package com.gridee.parking.data.api

import com.gridee.parking.config.ApiConfig
import com.gridee.parking.BuildConfig
import com.gridee.parking.GrideeApplication
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

object ApiClient {
    // Dynamic BASE_URL from ApiConfig
    private val BASE_URL = ApiConfig.BASE_URL
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val httpClient = OkHttpClient.Builder()
        // Attach JWT token before request logging so auth is present but never printed in release.
        .addInterceptor(JwtAuthInterceptor(GrideeApplication.instance.applicationContext))
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            
            val request = requestBuilder.build()
            
            debugLog("ApiClient: Environment: ${ApiConfig.getEnvironmentInfo()}")
            debugLog("ApiClient: Making request to: ${request.url}")
            
            try {
                val response = chain.proceed(request)
                debugLog("ApiClient: Response code: ${response.code}")
                debugLog("ApiClient: Response message: ${response.message}")
                
                // Log raw response body for parking-spots endpoints
                if (BuildConfig.DEBUG && request.url.encodedPath.contains("parking-spots")) {
                    val responseBody = response.peekBody(Long.MAX_VALUE).string()
                    debugLog("ApiClient: Raw response body (first 500 chars): ${responseBody.take(500)}")
                }
                
                response
            } catch (e: Exception) {
                debugLog("ApiClient: Network error: ${e.message}")
                val fallbackRequest = request.toFallbackRequest()
                if (fallbackRequest != null) {
                    debugLog("ApiClient: Retrying request with fallback host: ${fallbackRequest.url}")
                    return@addInterceptor chain.proceed(fallbackRequest)
                }
                throw e
            }
        }
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val gson = BackendGsonFactory.gson

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        // Handle plain text/primitive responses (e.g., OTP endpoints) first
        .addConverterFactory(ScalarsConverterFactory.create())
        // Then JSON via a lenient Gson to avoid strict parsing failures
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) {
            println(message)
        }
    }

    private fun Request.toFallbackRequest(): Request? {
        val fallbackBaseUrl = ApiConfig.FALLBACK_BASE_URL.toHttpUrlOrNull() ?: return null
        if (url.host == fallbackBaseUrl.host) return null

        val fallbackUrl = url.newBuilder()
            .scheme(fallbackBaseUrl.scheme)
            .host(fallbackBaseUrl.host)
            .port(fallbackBaseUrl.port)
            .build()

        return newBuilder()
            .url(fallbackUrl)
            .build()
    }
}
