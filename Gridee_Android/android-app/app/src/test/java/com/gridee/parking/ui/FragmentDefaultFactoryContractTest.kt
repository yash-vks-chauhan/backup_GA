package com.gridee.parking.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import com.gridee.parking.ui.bottomsheet.AddVehicleBottomSheet
import com.gridee.parking.ui.bottomsheet.BookingPassDetailsDialog
import com.gridee.parking.ui.bottomsheet.BookingQrPassBottomSheet
import com.gridee.parking.ui.bottomsheet.EditPhotoBottomSheet
import com.gridee.parking.ui.bottomsheet.FullPageBottomSheetFragment
import com.gridee.parking.ui.bottomsheet.LogoutConfirmationBottomSheet
import com.gridee.parking.ui.bottomsheet.ParkingSpotBottomSheet
import com.gridee.parking.ui.bottomsheet.PartnerReferralBottomSheet
import com.gridee.parking.ui.bottomsheet.ProfilePageBottomSheet
import com.gridee.parking.ui.bottomsheet.RewardBottomSheet
import com.gridee.parking.ui.bottomsheet.SelectVehicleBottomSheet
import com.gridee.parking.ui.bottomsheet.UniversalBottomSheet
import com.gridee.parking.ui.bottomsheet.VehicleOptionsBottomSheet
import com.gridee.parking.ui.bottomsheet.WelcomeGiftBottomSheet
import com.gridee.parking.ui.discovery.ParkingListFragment
import com.gridee.parking.ui.discovery.ParkingMapFragment
import com.gridee.parking.ui.fragments.BookingsFragmentNew
import com.gridee.parking.ui.fragments.HomeFragment
import com.gridee.parking.ui.fragments.ProfileFragment
import com.gridee.parking.ui.fragments.WalletFragmentNew
import com.gridee.parking.ui.profile.TicketDetailsBottomSheet
import com.gridee.parking.ui.wallet.PaymentOutcomeBottomSheet
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android's default [FragmentFactory] recreates fragments by calling a public no-argument
 * constructor. Keep every concrete production Fragment in this inventory: adding a required
 * constructor parameter makes process restoration fail before the screen can recover its state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class FragmentDefaultFactoryContractTest {

    private val fragmentClasses = listOf<Class<out Fragment>>(
        AddVehicleBottomSheet::class.java,
        BookingPassDetailsDialog::class.java,
        BookingQrPassBottomSheet::class.java,
        EditPhotoBottomSheet::class.java,
        FullPageBottomSheetFragment::class.java,
        LogoutConfirmationBottomSheet::class.java,
        ParkingSpotBottomSheet::class.java,
        PartnerReferralBottomSheet::class.java,
        ProfilePageBottomSheet::class.java,
        RewardBottomSheet::class.java,
        SelectVehicleBottomSheet::class.java,
        UniversalBottomSheet::class.java,
        VehicleOptionsBottomSheet::class.java,
        WelcomeGiftBottomSheet::class.java,
        ParkingListFragment::class.java,
        ParkingMapFragment::class.java,
        BookingsFragmentNew::class.java,
        HomeFragment::class.java,
        ProfileFragment::class.java,
        WalletFragmentNew::class.java,
        TicketDetailsBottomSheet::class.java,
        PaymentOutcomeBottomSheet::class.java,
    )

    @Test
    fun `every concrete production fragment can be recreated by the default factory`() {
        val factory = FragmentFactory()
        val classLoader = requireNotNull(javaClass.classLoader)

        fragmentClasses.forEach { fragmentClass ->
            val recreated = factory.instantiate(classLoader, fragmentClass.name)
            assertEquals(fragmentClass, recreated.javaClass)
        }
    }

    @Test
    fun `factory contract inventory includes every concrete production fragment declaration`() {
        val declaration = Regex(
            """(?m)^\s*(?!abstract\s+)(?:(?:public|internal|private|protected)\s+)?(?:open\s+)?class\s+([A-Za-z0-9_]+)\b""",
        )
        val fragmentSupertypeMarkers = listOf(
            ": Fragment(",
            ": DialogFragment(",
            ": BottomSheetDialogFragment(",
            ": BaseTabFragment<",
            ": FullPageBottomSheetFragment(",
        )
        val discoveredNames = findAppModuleRoot()
            .resolve("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val source = file.readText()
                declaration.findAll(source).mapNotNull { match ->
                    // Kotlin permits the ':' and supertype on following lines. Inspect the full
                    // class header rather than one source line so a formatting-only change cannot
                    // let a new Fragment escape this restoration contract.
                    val headerEnd = source.indexOf('{', startIndex = match.range.first)
                        .takeIf { it >= 0 }
                        ?: source.length
                    val header = source.substring(match.range.first, headerEnd)
                        .replace(Regex("\\s+"), " ")
                    match.groupValues[1].takeIf {
                        fragmentSupertypeMarkers.any { marker -> header.contains(marker) }
                    }
                }
            }
            .toSet()

        assertEquals(fragmentClasses.mapTo(mutableSetOf()) { it.simpleName }, discoveredNames)
    }

    private fun findAppModuleRoot(): File {
        val workingDirectory = System.getProperty("user.dir") ?: "."
        var cursor: File? = File(workingDirectory).absoluteFile
        while (true) {
            val current = cursor ?: break
            val candidates = listOf(
                current,
                current.resolve("app"),
                current.resolve("Gridee_Android/android-app/app"),
            )
            candidates.firstOrNull {
                it.resolve("src/main/java").isDirectory &&
                    (it.resolve("build.gradle").isFile || it.resolve("build.gradle.kts").isFile)
            }?.let { return it }
            cursor = current.parentFile
        }
        error("Unable to locate the Android app module from $workingDirectory")
    }
}
