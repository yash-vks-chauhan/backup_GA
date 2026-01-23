package com.gridee.parking.data.api

import com.gridee.parking.config.ApiConfig
import com.gridee.parking.GrideeApplication
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.model.ParkingSpotDeserializer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import com.google.gson.GsonBuilder

object ApiClient {
    // Dynamic BASE_URL from ApiConfig
    private val BASE_URL = ApiConfig.BASE_URL
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val httpClient = OkHttpClient.Builder()
        // Attach JWT token first so logs show it
        .addInterceptor(JwtAuthInterceptor(GrideeApplication.instance.applicationContext))
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            
            val request = requestBuilder.build()
            
            println("ApiClient: Environment: ${ApiConfig.getEnvironmentInfo()}")
            println("ApiClient: Making request to: ${request.url}")
            println("ApiClient: Request headers: ${request.headers}")
            
            try {
                val response = chain.proceed(request)
                println("ApiClient: Response code: ${response.code}")
                println("ApiClient: Response message: ${response.message}")
                
                // Log raw response body for parking-spots endpoints
                if (request.url.encodedPath.contains("parking-spots")) {
                    val responseBody = response.peekBody(Long.MAX_VALUE).string()
                    println("ApiClient: Raw response body (first 500 chars): ${responseBody.take(500)}")
                }
                
                response
            } catch (e: Exception) {
                println("ApiClient: Network error: ${e.message}")
                throw e
            }
        }
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    // Use a lenient Gson to tolerate slightly malformed JSON from backend
    private val gson = GsonBuilder()
        .setLenient()
        // Tolerate legacy spot payloads (boolean available, missing status, lotName-only)
        .registerTypeAdapter(ParkingSpot::class.java, ParkingSpotDeserializer())
        .create()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        // Handle plain text/primitive responses (e.g., OTP endpoints) first
        .addConverterFactory(ScalarsConverterFactory.create())
        // Then JSON via a lenient Gson to avoid strict parsing failures
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
