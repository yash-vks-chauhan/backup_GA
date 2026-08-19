# Dead Code / Unused Asset Inventory — gridee-android

## STATUS: Wave 1 COMPLETE (2026-08-16) — verified by clean build + 71 unit tests + device install.
Removed: 29 Kotlin files, 33 layouts, 2 stale manifest entries.
Wave 2 (drawables/anim/strings) NOT started — sections 6-8 below still open.

### Landmine found during Wave 1 — read before doing Wave 2
`EdgeToEdgeUtils.kt` was on the delete list and BROKE THE BUILD. It declares two
unused *classes* but also holds a top-level extension fun `configureEdgeToEdge()`
used by GrideeApplication + BaseActivity + BaseActivityWithBottomNav.
`FontScale.kt` has the identical shape (`withClampedFontScale`).
=> Name-based dead-code analysis MUST also extract top-level fun/val, not just
   class/object/interface. Both files are correctly RETAINED.

Generated 2026-08-16. Scope: Gridee_Android/android-app (main app) + repo-level cruft.
Method: transitive reachability from AndroidManifest + res/values themes.
NOTE: `minifyEnabled false` in app/build.gradle — none of this is stripped by R8. It all ships.

## 1. [DONE] Empty files (0 bytes) — deleted
- [ ] app/src/main/java/com/example/edge2edge/Theme.kt
- [ ] app/src/main/java/com/example/edge2edge/EdgeToEdgeActivity.kt
- [ ] app/src/main/java/com/gridee/parking/demo/BottomNavDemoActivity.kt
- [ ] app/src/main/java/com/gridee/parking/ui/adapters/Booking.kt
- [ ] app/src/main/java/com/gridee/parking/ui/utils/WindowInsetsHelper.kt

## 2. [DONE] Stale AndroidManifest entries — removed
- [ ] `.ui.profile.EditProfileActivity` — no such file
- [ ] `.ui.wallet.WalletActivity` — no such file

## 3. Manifest-declared but never launched (demo/test screens)
These are only "alive" because the manifest names them; nothing starts them.
- [ ] ui/MainComposeActivity.kt — intent-filter commented out, 0 launch sites
      (keep ui/MainViewModel.kt — HomeFragment uses it independently)
- [ ] ui/compose/EdgeToEdgeComposeActivity.kt — 0 launch sites
- [ ] ui/demo/EdgeToEdgeDemoActivity.kt — 0 launch sites, "Test activity" comment
- [ ] ui/auth/JwtTestActivity.kt (+ layout activity_jwt_test.xml) — 0 launch sites

## 4. [DONE] Unreachable Kotlin files — 29 deleted (EdgeToEdgeUtils.kt RETAINED, see landmine above)
- [ ] com/example/edge2edge/EdgeToEdgeActivity.kt
- [ ] com/example/edge2edge/Theme.kt
- [ ] com/gridee/parking/data/model/BookingRequest.kt
- [ ] com/gridee/parking/data/model/ExtendBookingRequest.kt
- [ ] com/gridee/parking/demo/BottomNavDemoActivity.kt
- [ ] com/gridee/parking/ui/adapters/Booking.kt
- [ ] com/gridee/parking/ui/adapters/MainPageFragment.kt
- [ ] com/gridee/parking/ui/adapters/MainPagerAdapter.kt
- [ ] com/gridee/parking/ui/adapters/StickyHeaderItemDecoration.kt
- [ ] com/gridee/parking/ui/bookings/BookingAdapter.kt
- [ ] com/gridee/parking/ui/bookings/BookingDetailBottomSheet.kt
- [ ] com/gridee/parking/ui/bookings/BookingsViewModel.kt
- [ ] com/gridee/parking/ui/bottomsheet/DeleteVehicleConfirmationBottomSheet.kt
- [ ] com/gridee/parking/ui/bottomsheet/EditVehicleBottomSheet.kt
- [ ] com/gridee/parking/ui/bottomsheet/PaymentMethodFilterBottomSheet.kt
- [ ] com/gridee/parking/ui/bottomsheet/SelectSpotBottomSheet.kt
- [ ] com/gridee/parking/ui/components/GlassSegmentedControl.kt
- [ ] com/gridee/parking/ui/fragments/TopUpBottomSheetFragment.kt
- [ ] com/gridee/parking/ui/fragments/WalletFragment.kt
- [ ] com/gridee/parking/ui/models/UIModels.kt
- [ ] com/gridee/parking/ui/profile/VehicleAdapter.kt
- [ ] com/gridee/parking/ui/utils/BounceEdgeEffectFactory.kt
- [KEPT — live top-level fun] com/gridee/parking/ui/utils/EdgeToEdgeUtils.kt
- [ ] com/gridee/parking/ui/utils/IOSSnapHelper.kt
- [ ] com/gridee/parking/ui/utils/WindowInsetsHelper.kt
- [ ] com/gridee/parking/ui/views/FlowLayout.kt
- [ ] com/gridee/parking/ui/wallet/TransactionAdapter.kt
- [ ] com/gridee/parking/ui/wallet/WalletViewModel.kt
- [ ] com/gridee/parking/utils/AppleSignInManager.kt
- [ ] com/gridee/parking/utils/DebugHelper.kt

## 5. [DONE] Unused layouts — 33 deleted
- [ ] activity_main
- [ ] activity_transaction_history_new
- [ ] activity_vehicles
- [ ] bottom_sheet_booking_detail
- [ ] bottom_sheet_booking_details
- [ ] bottom_sheet_delete_vehicle_confirmation
- [ ] bottom_sheet_edit_vehicle
- [ ] bottom_sheet_operator_menu
- [ ] bottom_sheet_operator_menu_v2
- [ ] bottom_sheet_payment_method_filter
- [ ] bottom_sheet_spot_filter
- [ ] bottom_sheet_vehicle_scan_timeout
- [ ] dialog_logout_confirmation
- [ ] dialog_manual_vehicle_entry
- [ ] fab_book_parking
- [ ] fragment_main_page
- [ ] fragment_wallet
- [ ] item_booking_compact
- [ ] item_booking_detail_row
- [ ] item_booking_fixed
- [ ] item_booking_modern_fixed
- [ ] item_booking_today_summary
- [ ] item_dropdown_menu
- [ ] item_operator_activity
- [ ] item_parking_option
- [ ] item_parking_spot_selection
- [ ] item_timeline_row
- [ ] item_vehicle_input
- [ ] item_vehicle_profile
- [ ] nav_footer_operator
- [ ] nav_header_operator
- [ ] test_booking_card
- [ ] window_parking_dropdown

## 6. Unused anim (27) / animator (9) / menu (3) / color selectors (6) / raw (4)
=== DEAD ANIM: 27 / 47 ===
  balance_bounce
  balance_update
  button_rotate_press
  button_rotate_release
  date_cell_scale
  emphasized_decelerate_interpolator
  emphasized_interpolator
  item_rise_in
  layout_rise_in
  search_bar_enter
  search_bar_enter_minimal
  search_bar_focus_minimal
  search_bar_press
  search_bar_press_minimal
  search_bar_press_scale
  search_bar_release
  search_bar_release_minimal
  search_bar_release_scale
  search_bar_unfocus_minimal
  slide_in_bottom
  slide_in_right
  slide_out_right
  tab_slide_in_left
  tab_slide_in_right
  tab_slide_out_left
  tab_slide_out_right
  wallet_card_entrance

=== DEAD ANIMATOR: 9 / 11 ===
  anim_slash_draw
  anim_slash_hide
  booking_card_state_list
  button_scale_animator
  card_press_scale
  search_bar_state_animator
  segment_press_scale
  segment_release_scale
  segment_state_list_animator

=== DEAD MENU: 3 / 3 ===
  activity_operator_drawer
  vehicle_options_default_menu
  vehicle_options_menu

=== DEAD COLOR-SELECTOR: 6 / 13 ===
  partner_segment_chip_text
  radio_button_black
  radio_button_black_selector
  search_hint_text_selector
  search_icon_tint_selector
  segment_button_text_selector

=== DEAD RAW: 4 / 4 ===
  operator_avatar
  popicons_ostrich
  premium_crown
  user_avatar


## 7. Unused drawables — 352 of 674
Full list in dead_drawables.txt. Bulk is abandoned design iterations:
search bar variants (~18), segment/segmented control (~20), status pill/dot (~25),
ultra_* (~8), ios_* (~6), bottom_nav_* (~5), scanner_v2_* colors, partner flow icons.

## 8. Unused values resources
- 82 unused strings in values/strings.xml (of 851) — note translations in values-hi/bn/ta/te/ml mirror these
- 92 unused colors in values/colors.xml (of 319)
- 26 unused dimens (of 49)

## 9. Unused API surface
- [ ] ApiService.getLotBookingPolicy() — 0 callers
- [ ] ApiService.isParkingSpotAvailableForLot() — 0 callers
- [ ] ParkingRepository.getOperatorParkingSpots() — 0 callers
- [ ] ParkingRepository.getParkingLotNames() — 0 callers
- [ ] data/model/BookingRequest.kt — 0 references
- [ ] data/model/ExtendBookingRequest.kt — 0 references

## 10. Gradle deps only used by dead code
Compose IS still needed (WalletFragmentNew uses a ComposeView for the Lottie empty state
via ui/compose/DotLottieAnimation.kt). But these are used by nothing live:
- [ ] androidx.navigation:navigation-compose:2.7.5 — 0 imports anywhere
- [ ] androidx.compose.runtime:runtime-livedata — 0 imports anywhere
- [ ] androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0 — 0 imports anywhere
- [ ] androidx.compose.material3:material3 — only imported by the 4 dead Compose files

## 11. Repo-level cruft (outside the app module)
TRACKED in git — safe to remove after review:
- [ ] Gridee_Android/repo/ — full duplicate Spring Boot backend (com.parking.app), superseded by gridee_backend/
- [ ] src/main/java/com/parking/app/service/booking/BookingQueryService.java — orphan, 1 file, same dead package
- [ ] untitled/ — IntelliJ scratch project (Main.java + .iml)
- [ ] app/ (repo root) — only build.gradle + .DS_Store, not part of any settings.gradle
- [ ] .tmp-arm64.apk — 22-byte truncated stub
- [ ] backfill-parking-spot-lotid.js, verify-parking-data.js — one-off backend migration scripts

UNTRACKED but eating disk (already gitignored):
- [ ] Gridee_Android/kotlin-compiler-2.2.20.zip — 75 MB
- [ ] Gridee_Android/main.jar — 4.9 MB
- [ ] apks/ — 79 MB of old AABs/APKs
- [ ] logs/ — 89 MB
- [ ] bin/ — 1.3 MB stale backend build output
