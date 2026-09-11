package com.gridee.parking.ui.wallet

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletTopUpLateOutcomeContractTest {

    @Test
    fun `checkout launches after the host is fully resumed`() {
        val source = walletTopUpSource()
        val postResume = source
            .substringAfter("override fun onPostResume()")
            .substringBefore("private fun startCheckout")

        assertTrue(postResume.contains("if (!checkoutLaunched)"))
        assertTrue(postResume.contains("paymentGateway?.let(::startCheckout)"))
        assertFalse(source.substringBefore("override fun onPostResume()").contains("startCheckout(gateway)"))
    }

    @Test
    fun `cashfree callbacks do not open the payment outcome sheet`() {
        val source = walletTopUpSource()

        assertFalse(source.contains("PaymentOutcomeBottomSheet"))
        assertFalse(source.contains("showOutcome("))
        assertFalse(source.contains("STATE_PENDING_OUTCOME"))
        assertFalse(source.contains("override fun onPause()"))
        assertFalse(source.contains("RETURN_STATUS_CHECK_DELAY_MS"))
    }

    @Test
    fun `sdk failures use one safety check while verify gets the bounded settling window`() {
        val source = walletTopUpSource()
        val reconciliation = source
            .substringAfter("private fun reconcile(")
            .substringBefore("private fun onTopUpConfirmed")

        assertTrue(reconciliation.contains("CompletionTrigger.VERIFY ->"))
        assertTrue(reconciliation.contains("PaymentStatusCoordinator.verify(reconcileOrderId)"))
        assertTrue(reconciliation.contains("CompletionTrigger.CANCELLED, CompletionTrigger.SDK_FAILURE ->"))
        assertTrue(reconciliation.contains("withTimeoutOrNull(FAILURE_SAFETY_CHECK_TIMEOUT_MS)"))
        assertTrue(reconciliation.contains("PaymentStatusCoordinator.checkOnce(reconcileOrderId)"))
        assertTrue(reconciliation.contains("R.string.payment_pending_confirmation"))
        assertTrue(reconciliation.contains("checkoutFailureMessage(failureDiagnostic)"))
        assertTrue(reconciliation.contains("finally"))
        assertTrue(reconciliation.contains("reconciling = false"))
    }

    @Test
    fun `checkout failures use an in-app dialog with a safe diagnostic instead of a toast`() {
        val source = walletTopUpSource()

        assertTrue(source.contains("CashfreeCheckoutFailureClassifier.diagnose("))
        assertTrue(source.contains("R.string.payment_cashfree_error_with_support_code"))
        assertTrue(source.contains("MaterialAlertDialogBuilder(this)"))
        assertFalse(source.contains("Toast.makeText"))
    }

    private fun walletTopUpSource(): String = findAppModuleRoot()
        .resolve("src/main/java/com/gridee/parking/ui/wallet/WalletTopUpActivity.kt")
        .readText()

    private fun findAppModuleRoot(): File {
        val workingDirectory = System.getProperty("user.dir") ?: "."
        var cursor: File? = File(workingDirectory).absoluteFile
        while (true) {
            val current = cursor ?: break
            listOf(current, current.resolve("app"), current.resolve("Gridee_Android/android-app/app"))
                .firstOrNull {
                    it.resolve("src/main/java").isDirectory &&
                        (it.resolve("build.gradle").isFile || it.resolve("build.gradle.kts").isFile)
                }
                ?.let { return it }
            cursor = current.parentFile
        }
        error("Unable to locate the Android app module from $workingDirectory")
    }
}
