package com.gridee.parking.data.api

import com.gridee.parking.data.model.AuthRequest
import com.gridee.parking.data.model.AuthResponse
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.FirebaseTokenExchangeRequest
import com.gridee.parking.data.model.ParkingLot
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.model.User
import com.gridee.parking.data.model.UserRegistration
import com.gridee.parking.data.model.UpdateUserRequest
import com.gridee.parking.data.model.WalletDetails
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.data.model.PaymentInitiateRequest
import com.gridee.parking.data.model.PaymentInitiateResponse
import com.gridee.parking.data.model.PaymentCallbackRequest
import com.gridee.parking.data.model.PaymentCallbackResponse
import com.gridee.parking.data.model.TopUpRequest
import com.gridee.parking.data.model.TopUpResponse
import com.gridee.parking.data.model.QrValidationResult
import com.gridee.parking.data.model.CheckInRequest
import com.gridee.parking.data.model.CreateBookingRequest
import com.gridee.parking.data.model.DeviceTokenRegisterRequest
import com.gridee.parking.data.model.DeviceTokenUnregisterRequest
import com.gridee.parking.data.model.SpotAvailabilityInfo
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    
    // ========== Authentication Endpoints ==========
    
    @POST("api/auth/login")
    suspend fun authLogin(@Body request: AuthRequest): Response<AuthResponse>

    @POST("api/auth/firebase/exchange")
    suspend fun exchangeFirebaseToken(@Body request: FirebaseTokenExchangeRequest): Response<AuthResponse>
    
    // ========== User Management Endpoints ==========
    
    @POST("api/auth/register")
    suspend fun registerUser(@Body user: UserRegistration): Response<AuthResponse>
    
    @POST("api/users/login")
    suspend fun loginUser(@Body credentials: Map<String, String>): Response<User>
    
    @POST("api/users/social-signin")
    suspend fun socialSignIn(@Body credentials: Map<String, String>): Response<AuthResponse>

    // New AuthController endpoint for Google Sign-In
    @POST("api/auth/google")
    suspend fun googleSignIn(@Body credentials: Map<String, String>): Response<AuthResponse>

    // OAuth2 user info
    @GET("api/oauth2/user")
    suspend fun getOAuth2User(): Response<Map<String, Any>>

    // ========== Notification Token Endpoints ==========

    @POST("api/notifications/tokens")
    suspend fun registerNotificationToken(
        @Header("Authorization") authHeader: String,
        @Body request: DeviceTokenRegisterRequest
    ): Response<Void>

    @HTTP(method = "DELETE", path = "api/notifications/tokens", hasBody = true)
    suspend fun unregisterNotificationToken(
        @Header("Authorization") authHeader: String,
        @Body request: DeviceTokenUnregisterRequest
    ): Response<Void>

    @GET("api/users/{id}")
    suspend fun getUserById(@Path("id") userId: String): Response<User>
    
    @PUT("api/users/{id}")
    suspend fun updateUser(@Path("id") userId: String, @Body user: UpdateUserRequest): Response<Void>
    
    // Parking lots and spots endpoints
    @GET("api/parking-lots")
    suspend fun getParkingLots(): Response<List<ParkingLot>>
    
    @GET("api/parking-lots/list/by-names")
    suspend fun getParkingLotNames(): Response<List<String>>

    // ADMIN-only on backend; avoid using from app for regular users
    @GET("api/parking-spots")
    suspend fun getParkingSpots(): Response<List<ParkingSpot>>

    // Correct backend path for by-lot spots
    @GET("api/parking-spots/lot/{lotId}")
    suspend fun getParkingSpotsByLot(@Path("lotId") lotId: String): Response<List<ParkingSpot>>

    // Single spot by ID (non-admin)
    @GET("api/parking-spots/id/{id}")
    suspend fun getParkingSpotById(@Path("id") id: String): Response<ParkingSpot>
    
    // Backend endpoints for user bookings list/history
    @GET("api/bookings/{userId}/all")
    suspend fun getUserBookings(@Path("userId") userId: String): Response<List<Booking>>
    
    @GET("api/bookings/{userId}/all/history")
    suspend fun getUserBookingHistory(@Path("userId") userId: String): Response<List<Booking>>
    
    // Booking creation endpoints
    // Create booking with JSON body per backend DTO
    @POST("api/bookings/{userId}/create")
    suspend fun createBooking(
        @Path("userId") userId: String,
        @Body request: CreateBookingRequest
    ): Response<Booking>

    @POST("api/bookings/{userId}/{bookingId}/cancel")
    suspend fun cancelBooking(
        @Path("userId") userId: String,
        @Path("bookingId") bookingId: String
    ): Response<Void>
    
    // Wallet endpoints
    @GET("api/users/{userId}/wallet")
    suspend fun getWalletDetails(@Path("userId") userId: String): Response<WalletDetails>
    
    @GET("api/users/{userId}/wallet/transactions")
    suspend fun getWalletTransactions(@Path("userId") userId: String): Response<List<WalletTransaction>>
    
    @POST("api/users/{userId}/wallet/topup")
    suspend fun topUpWallet(
        @Path("userId") userId: String,
        @Body request: TopUpRequest
    ): Response<TopUpResponse>

    // Payments (Razorpay)
    @POST("api/payments/initiate")
    suspend fun initiatePayment(@Body request: PaymentInitiateRequest): Response<PaymentInitiateResponse>

    @POST("api/payments/callback")
    suspend fun paymentCallback(@Body payload: PaymentCallbackRequest): Response<PaymentCallbackResponse>
    
    // OTP endpoints
    @POST("api/otp/generate")
    suspend fun generateOtp(@Query("key") phoneNumber: String): Response<String>
    
    @POST("api/otp/validate")
    suspend fun validateOtp(
        @Query("key") phoneNumber: String,
        @Query("otp") otp: String
    ): Response<Boolean>

    // ========== QR CHECK-IN/OUT ENDPOINTS ==========

    // User check-in with CheckInRequest body
    @POST("api/bookings/{userId}/checkin/{bookingId}")
    suspend fun checkInBooking(
        @Path("userId") userId: String,
        @Path("bookingId") bookingId: String,
        @Body request: CheckInRequest
    ): Response<Booking>

    // User check-out with CheckInRequest body
    @POST("api/bookings/{userId}/checkout/{bookingId}")
    suspend fun checkOutBooking(
        @Path("userId") userId: String,
        @Path("bookingId") bookingId: String,
        @Body request: CheckInRequest
    ): Response<Booking>
    
    // ========== Operator Check-In/Out Endpoints ==========
    
    /**
     * Operator check-in (no userId/bookingId required)
     * POST /api/operator/bookings/checkin
     */
    @POST("api/operator/bookings/checkin")
    suspend fun operatorCheckIn(
        @Body request: CheckInRequest
    ): Response<Booking>

    /**
     * Operator check-out (no userId/bookingId required)
     * POST /api/operator/bookings/checkout
     */
    @POST("api/operator/bookings/checkout")
    suspend fun operatorCheckOut(
        @Body request: CheckInRequest
    ): Response<Booking>
    
    // Get booking by ID (for refreshing data)
    @GET("api/bookings/{userId}/{bookingId}")
    suspend fun getBookingById(
        @Path("userId") userId: String,
        @Path("bookingId") bookingId: String
    ): Response<Booking>

    // Get penalty info (real-time)
    @GET("api/bookings/{userId}/{bookingId}/penalty")
    suspend fun getPenaltyInfo(
        @Path("userId") userId: String,
        @Path("bookingId") bookingId: String
    ): Response<Double>

    // Extend booking end time
    @PUT("api/bookings/{userId}/{bookingId}/extend")
    suspend fun extendBooking(
        @Path("userId") userId: String,
        @Path("bookingId") bookingId: String,
        @Body request: Map<String, String>
    ): Response<Booking>

    // Update booking status (e.g., to ACTIVE/CANCELLED) when needed
    // ADMIN-only on backend; do not use for regular users
    @PUT("api/admin/bookings/{userId}/{bookingId}")
    suspend fun updateBookingStatus(
        @Path("userId") userId: String,
        @Path("bookingId") bookingId: String,
        @Body body: Map<String, String>
    ): Response<Booking>

    // Price breakup for a booking
    @GET("api/bookings/{userId}/{bookingId}/priceBreakup")
    suspend fun getBookingPriceBreakup(
        @Path("userId") userId: String,
        @Path("bookingId") bookingId: String
    ): Response<Map<String, Any>>

    // Availability by time window
    @GET("api/parking-spots/available")
    suspend fun getAvailableSpots(
        @Query("lotId") lotId: String,
        @Query("startTime") startTime: String,
        @Query("endTime") endTime: String
    ): Response<List<SpotAvailabilityInfo>>
}
