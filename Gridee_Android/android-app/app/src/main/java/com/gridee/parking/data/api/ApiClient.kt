package com.gridee.parking.data.api

import com.gridee.parking.config.ApiConfig
import com.gridee.parking.GrideeApplication
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.IOException

/**
 * A request may be replayed against the fallback host only when HTTP defines it as a safe read.
 * In particular, a lost response to a mutation must never cause the app to send that mutation
 * a second time to another host.
 */
internal object FallbackHostPolicy {
    fun canReplay(method: String): Boolean {
        return method.equals("GET", ignoreCase = true) ||
            method.equals("HEAD", ignoreCase = true)
    }
}

object ApiClient {
    // Dynamic BASE_URL from ApiConfig
    private val BASE_URL = ApiConfig.BASE_URL
    
    private val httpClient = OkHttpClient.Builder()
        .eventListenerFactory(
            PrivacySafeNetworkTimingEventListener.Factory(
                GrideeApplication.instance.applicationContext
            )
        )
        // Attach JWT before the remaining interceptors. Direct HTTP request/response logging is
        // intentionally absent so headers, URLs, bodies, and user identifiers cannot reach Logcat.
        .addInterceptor(JwtAuthInterceptor(GrideeApplication.instance.applicationContext))
        // Catch dead sessions: a 401 on an authenticated request clears the session and
        // routes to login. Must come right after JwtAuthInterceptor so the request it
        // inspects already carries the Authorization header.
        .addInterceptor(UnauthorizedInterceptor(GrideeApplication.instance.applicationContext))
        .addInterceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            
            val request = requestBuilder.build()
            
            try {
                val response = chain.proceed(request)
                response
            } catch (e: IOException) {
                val fallbackRequest = request
                    .takeIf { FallbackHostPolicy.canReplay(it.method) }
                    ?.toFallbackRequest()
                if (fallbackRequest != null) {
                    return@addInterceptor chain.proceed(fallbackRequest)
                }
                throw e
            }
        }
        // Mutations such as booking, payment initiation, wallet credit and operator actions must
        // never be replayed automatically after an ambiguous connection failure. Safe GET/HEAD
        // fallback is handled explicitly by the interceptor above and repository backoff/cache.
        .retryOnConnectionFailure(false)
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
