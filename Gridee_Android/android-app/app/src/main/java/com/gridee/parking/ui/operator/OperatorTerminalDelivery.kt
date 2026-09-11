package com.gridee.parking.ui.operator

/**
 * Owns the hand-off of one completed operator mutation to a visible UI consumer.
 *
 * Completion has no wall-clock expiry: a result stays claimable until the UI explicitly
 * acknowledges it by clearing the retained operation. If a consumer leaves before that
 * acknowledgement (background, rotation, or Activity hand-off), releasing it also releases its
 * claim so the next visible operator surface can present the same terminal result.
 */
internal class OperatorTerminalDelivery {
    private var completed = false
    private var registeredConsumerId: Long? = null
    private var claimedConsumerId: Long? = null

    val isClaimed: Boolean
        get() = claimedConsumerId != null

    fun markCompleted() {
        completed = true
    }

    fun register(consumerId: Long) {
        registeredConsumerId = consumerId
    }

    fun release(consumerId: Long) {
        if (registeredConsumerId != consumerId) return
        registeredConsumerId = null
        if (claimedConsumerId == consumerId) {
            claimedConsumerId = null
        }
    }

    fun claim(consumerId: Long): Boolean {
        if (!completed || registeredConsumerId != consumerId || claimedConsumerId != null) {
            return false
        }
        claimedConsumerId = consumerId
        return true
    }
}
