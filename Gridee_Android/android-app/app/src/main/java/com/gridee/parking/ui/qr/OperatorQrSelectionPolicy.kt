package com.gridee.parking.ui.qr

internal sealed class OperatorQrSelection {
    object None : OperatorQrSelection()
    data class Accepted(val value: String) : OperatorQrSelection()
    object Ambiguous : OperatorQrSelection()
}

/** Fails closed when the operator frame contains more than one distinct accepted QR payload. */
internal object OperatorQrSelectionPolicy {
    fun select(acceptedValues: Iterable<String?>): OperatorQrSelection {
        val distinctValues = acceptedValues.asSequence()
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(2)
            .toList()
        return when (distinctValues.size) {
            0 -> OperatorQrSelection.None
            1 -> OperatorQrSelection.Accepted(distinctValues.single())
            else -> OperatorQrSelection.Ambiguous
        }
    }
}
