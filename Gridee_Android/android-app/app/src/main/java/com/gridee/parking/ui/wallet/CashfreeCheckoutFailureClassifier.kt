package com.gridee.parking.ui.wallet

import java.util.Locale

/**
 * Converts Cashfree's callback fields into stable, non-sensitive failure categories.
 *
 * Raw gateway messages are deliberately not shown to users or written to Logcat: they can change
 * between SDK releases and may contain payment-specific details. The categories below are enough
 * to distinguish a user cancellation from common configuration/order failures that can prevent
 * checkout from opening at all.
 */
internal object CashfreeCheckoutFailureClassifier {

    enum class Kind {
        CANCELLED,
        UNTRUSTED_INSTALLER,
        INVALID_SESSION,
        INVALID_CALLING_CONTEXT,
        INACTIVE_ORDER,
        GATEWAY_UNAVAILABLE,
        OTHER,
    }

    data class Diagnostic(
        val kind: Kind,
        val supportCode: String,
    )

    fun classify(
        status: String?,
        code: String?,
        type: String?,
        message: String?,
    ): Kind = diagnose(status, code, type, message).kind

    fun diagnose(
        status: String?,
        code: String?,
        type: String?,
        message: String?,
    ): Diagnostic {
        val details = listOf(status, code, type, message)
            .joinToString(" ") { it.orEmpty() }
            .lowercase(Locale.ROOT)

        val kind = when {
            CANCELLATION_MARKERS.any(details::contains) -> Kind.CANCELLED
            UNTRUSTED_INSTALLER_MARKERS.any(details::contains) -> Kind.UNTRUSTED_INSTALLER
            INVALID_SESSION_MARKERS.any(details::contains) -> Kind.INVALID_SESSION
            INVALID_CONTEXT_MARKERS.any(details::contains) -> Kind.INVALID_CALLING_CONTEXT
            INACTIVE_ORDER_MARKERS.any(details::contains) -> Kind.INACTIVE_ORDER
            GATEWAY_UNAVAILABLE_MARKERS.any(details::contains) -> Kind.GATEWAY_UNAVAILABLE
            else -> Kind.OTHER
        }

        return Diagnostic(
            kind = kind,
            // Cashfree's free-form message is intentionally excluded. Only its short enum-like
            // fields are surfaced, after strict normalization, so logs/UI cannot leak order or
            // payment-session details.
            supportCode = listOf(code, type, status)
                .mapNotNull(::normalizeDiagnosticField)
                .distinct()
                .take(MAX_SUPPORT_CODE_PARTS)
                .joinToString("/")
                .ifEmpty { "CF_${kind.name}" },
        )
    }

    private val CANCELLATION_MARKERS = listOf("cancel", "user_dropped", "aborted")

    private val UNTRUSTED_INSTALLER_MARKERS = listOf(
        "installer_package_not_approved",
        "trusted source",
        "package_not_approved",
        "app_not_approved",
        "reftype_not_approved",
    )

    private val INVALID_SESSION_MARKERS = listOf(
        "payment_session_id_invalid",
        "invalid payment session",
        "invalid_session",
        "sdk_token_invalid",
        "sdk_token_unknown",
        "order_token_invalid",
    )

    private val INVALID_CONTEXT_MARKERS = listOf("wrong_calling_context")

    private val INACTIVE_ORDER_MARKERS = listOf(
        "order_inactive",
        "order_id_invalid",
        "order_expired",
        "order_id_voided",
        "order_already_paid",
    )

    private val GATEWAY_UNAVAILABLE_MARKERS = listOf(
        "payment_gateway_inactive",
        "order_pay_failed",
        "api_request_timeout",
        "paymentform_form_creation_failed",
        "invalid_web_data",
    )

    private fun normalizeDiagnosticField(value: String?): String? {
        val normalized = value
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.uppercase(Locale.ROOT)
            ?.map { character ->
                if (character.isLetterOrDigit() || character == '_' || character == '-' ||
                    character == '.'
                ) {
                    character
                } else {
                    '_'
                }
            }
            ?.joinToString("")
            ?.replace(REPEATED_UNDERSCORES, "_")
            ?.trim('_')
            ?.take(MAX_SUPPORT_CODE_PART_LENGTH)
            ?.takeIf(String::isNotEmpty)
        return normalized
    }

    private val REPEATED_UNDERSCORES = Regex("_+")
    private const val MAX_SUPPORT_CODE_PARTS = 2
    private const val MAX_SUPPORT_CODE_PART_LENGTH = 48
}
