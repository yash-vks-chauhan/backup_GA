package com.gridee.parking.data.repository

import com.gridee.parking.data.api.ApiService
import com.gridee.parking.data.model.AppConfigResponse
import com.gridee.parking.data.model.AppRemoteConfig
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

class RemoteConfigRepositoryTest {

    @Before
    fun resetSharedCache() {
        RemoteConfigRepository.invalidateCache()
    }

    @After
    fun clearSharedCache() {
        RemoteConfigRepository.invalidateCache()
    }

    @Test
    fun `failed forced fetch does not return previously cached config as fresh`() = runBlocking {
        val expected = AppRemoteConfig(id = "fresh-config")
        var nextResponse: Response<AppConfigResponse> = Response.success(
            AppConfigResponse(data = expected, success = true, status = 200)
        )
        val apiService = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getAppConfig" -> nextResponse
                "toString" -> "RemoteConfigApiServiceTestDouble"
                else -> error("Unexpected API call: ${method.name}")
            }
        } as ApiService
        val repository = RemoteConfigRepository(apiService)

        assertEquals(expected, repository.fetchAppConfig(forceRefresh = true))

        nextResponse = Response.error(503, "peak unavailable".toResponseBody(null))
        assertNull(repository.fetchAppConfig(forceRefresh = true))
    }
}
