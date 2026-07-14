package com.gridee.parking.data.model

data class AppConfigResponse(
    var data: AppRemoteConfig? = null,
    var message: String? = null,
    var success: Boolean = false,
    var status: Int = 0,
    var error: String? = null,
    var timestamp: Long? = null
)

data class AppRemoteConfig(
    var id: String? = "DEFAULT",
    var schemaVersion: Int = 1,
    var cacheTtlSeconds: Long = 900,
    var features: RemoteFeatureFlags = RemoteFeatureFlags(),
    var versions: RemoteAppVersions = RemoteAppVersions(),
    var financial: RemoteFinancialSettings = RemoteFinancialSettings(),
    var booking: RemoteBookingSettings = RemoteBookingSettings(),
    var notification: RemoteNotificationSettings = RemoteNotificationSettings(),
    var platform: RemotePlatformSettings = RemotePlatformSettings(),
    var home: RemoteHomeSettings = RemoteHomeSettings(),
    var customSettings: Map<String, Any> = emptyMap(),
    var lastUpdatedAt: Long? = null,
    var createdAt: Long? = null,
    var updatedBy: String? = null,
    var description: String? = null
)

data class RemoteFeatureFlags(
    var appleSignInEnabled: Boolean = true,
    var googleSignInEnabled: Boolean = true,
    var emailSignInEnabled: Boolean = true,
    var walletFeatureEnabled: Boolean = true,
    var bookingFeatureEnabled: Boolean = true,
    var notificationsEnabled: Boolean = true,
    var pushNotificationsEnabled: Boolean = true,
    var emailNotificationsEnabled: Boolean = false,
    var sequentialBookingEnabled: Boolean = true,
    var multipleBookingsAllowed: Boolean = true,
    var maintenanceMode: Boolean = false,
    var maintenanceTitle: String = "Scheduled maintenance",
    var maintenanceMessage: String = "Gridee is temporarily unavailable. Please try again later.",
    var locationTrackingEnabled: Boolean = true,
    var rateLimitingEnabled: Boolean = true,
    var adMobEnabled: Boolean = false,
    var rewardsEnabled: Boolean = true,
    var featureToggleMap: Map<String, Boolean> = emptyMap()
)

data class RemoteAppVersions(
    var minIOSVersion: String = "1.0.0",
    var latestIOSVersion: String = "1.0.0",
    var forceIOSUpdate: Boolean = false,
    var recommendedIOSUpdate: Boolean = false,
    var iosUpdateMessage: String = "Please update to the latest version",
    var minAndroidVersion: String = "1.0.0",
    var latestAndroidVersion: String = "1.0.0",
    var minAndroidVersionCode: Int = 1,
    var latestAndroidVersionCode: Int = 1,
    var forceAndroidUpdate: Boolean = false,
    var recommendedAndroidUpdate: Boolean = false,
    var androidUpdateMessage: String = "Please update to the latest version",
    var iosAppStoreUrl: String = "",
    var androidPlayStoreUrl: String = "",
    var webVersion: String = "1.0.0"
)

data class RemoteFinancialSettings(
    var welcomeBonusAmount: Double = 50.0,
    var minWalletTopUpAmount: Double = 100.0,
    var maxWalletTopUpAmount: Double = 50000.0,
    var lateCheckoutPenaltyPerMin: Double = 2.0,
    var lateCheckoutGracePeriodMinutes: Double = 10.0,
    var maxLateCheckoutPenaltyPerMin: Double = 5.0,
    var penaltyEscalationIntervalMins: Int = 10,
    var noShowDeduction: Double = 1.0,
    var cancellationRefundFullRefundHours: Double = 2.0,
    var cancellationRefundPartialRefundHours: Double = 1.0,
    var cancellationRefundPartialPercentage: Double = 50.0,
    var razorpayTaxPercentage: Double = 0.0,
    var customCharges: Map<String, Double> = emptyMap()
)

data class RemoteBookingSettings(
    var maxConcurrentBookingsPerUser: Int = 2,
    var allowSequentialBookings: Boolean = true,
    var maxSequentialBookings: Int = 2,
    var autoActivateNextBooking: Boolean = true,
    var maxBookingDurationHours: Int = 24,
    var minBookingDurationMinutes: Int = 30,
    var noShowGraceMinutes: Int = 120,
    var maxPricingPerHour: Double = 100.0,
    var bookingValidationRules: Map<String, Any> = emptyMap()
)

data class RemoteNotificationSettings(
    var sendCheckInReminders: Boolean = true,
    var checkInReminderMinutesBefore: Int = 15,
    var sendCheckOutReminders: Boolean = true,
    var checkOutReminderMinutesAfter: Int = 30,
    var sendPenaltyNotifications: Boolean = true,
    var sendBookingConfirmation: Boolean = true,
    var sendCancellationNotification: Boolean = true,
    var notificationProvider: String = "FCM",
    var maxNotificationsPerUser: Int = 10
)

data class RemotePlatformSettings(
    var apiVersion: String = "v1",
    var debugMode: Boolean = false,
    var rateLimitPerMinute: Int = 40,
    var rateLimitPerHour: Int = 1000,
    var environment: String = "PRODUCTION",
    var timezone: String = "Asia/Kolkata",
    var currency: String = "INR",
    var currencySymbol: String = "₹",
    var enableCors: Boolean = true,
    var enableSwagger: Boolean = false,
    var requestTimeoutSeconds: Int = 30,
    var externalServiceUrls: Map<String, String> = emptyMap()
)

data class RemoteHomeSettings(
    var showSlotFilters: Boolean = true
)
