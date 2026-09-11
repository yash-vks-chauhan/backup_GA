package com.gridee.parking.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Locks the organization -> location -> lot -> policy -> spot discovery contract. */
class MultiTenantParkingApiContractTest {

    @Test
    fun organizationAndLocationEndpointsMatchBackendContract() {
        val organizations = ApiService::class.java.methods.single { it.name == "getOrganizations" }
        val locations = ApiService::class.java.methods.single { it.name == "getLocations" }

        assertEquals(
            "api/v1/organizations",
            requireNotNull(organizations.getAnnotation(GET::class.java)).value,
        )
        assertEquals(
            "api/v1/locations",
            requireNotNull(locations.getAnnotation(GET::class.java)).value,
        )
        assertTrue(locations.hasQuery("organizationId"))
    }

    @Test
    fun lotListCarriesBothTenantScopeParameters() {
        val method = ApiService::class.java.methods.single { it.name == "getParkingLotsByType" }

        assertEquals(
            "api/parking-lots",
            requireNotNull(method.getAnnotation(GET::class.java)).value,
        )
        assertTrue(method.hasQuery("organizationId"))
        assertTrue(method.hasQuery("locationId"))
    }

    @Test
    fun policyAndSpotEndpointsAreScopedByTheSameLotPath() {
        val policy = ApiService::class.java.methods.single { it.name == "getLotBookingPolicy" }
        val spots = ApiService::class.java.methods.single { it.name == "getParkingSpotsForLot" }

        assertEquals(
            "api/parking-lots/{lotId}/booking-policy",
            requireNotNull(policy.getAnnotation(GET::class.java)).value,
        )
        assertEquals(
            "api/parking-lots/{lotId}/spots",
            requireNotNull(spots.getAnnotation(GET::class.java)).value,
        )
        assertTrue(policy.hasPath("lotId"))
        assertTrue(spots.hasPath("lotId"))
    }

    private fun java.lang.reflect.Method.hasQuery(name: String): Boolean =
        parameterAnnotations.flatten().filterIsInstance<Query>().any { it.value == name }

    private fun java.lang.reflect.Method.hasPath(name: String): Boolean =
        parameterAnnotations.flatten().filterIsInstance<Path>().any { it.value == name }
}
