# Android Font & Component Audit

## Scope
- Layout files scanned: 84
- Values files scanned: colors.xml, dimens.xml, styles_shapes.xml, themes.xml, strings.xml
- Includes are not expanded; components list is based on tags in each layout file.
- Compose UI is not included in this XML-based audit.

## Font resources
- res/font: inter.xml, inter_bold.ttf, inter_medium.xml, inter_regular.xml, inter_semibold.xml
- Theme defaults:
  - Theme.Gridee: sans-serif
  - Theme.Gridee.Auth: sans-serif
  - Theme.Gridee.NoActionBar: sans-serif
  - Theme.Gridee.Search: sans-serif

## Fonts used across layouts (explicit)
- @font/inter_bold: 12 layout(s)
- @font/inter_medium: 12 layout(s)
- @font/inter_regular: 7 layout(s)
- @font/inter_semibold: 7 layout(s)
- monospace: 2 layout(s)
- sans-serif: 33 layout(s)
- sans-serif-black: 3 layout(s)
- sans-serif-medium: 29 layout(s)

## Per-layout details
### Gridee_Android/android-app/app/src/main/res/layout/activity_account_settings.xml (Activity)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Components: LinearLayout, TextView, androidx.constraintlayout.widget.ConstraintLayout, com.airbnb.lottie.LottieAnimationView, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/activity_add_phone.xml (Activity)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox
- Components: ProgressBar, ScrollView, TextView, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_add_vehicle.xml (Activity)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox
- Components: ProgressBar, ScrollView, TextView, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_booking_confirmation.xml (Activity)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Unresolved style refs: @style/Widget.Material3.Button.OutlinedButton, @style/Widget.Material3.Button.TextButton
- Components: ImageButton, ImageView, LinearLayout, ScrollView, TextView, View, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/activity_booking_flow.xml (Activity)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Unresolved style refs: @style/Widget.Material3.Button.OutlinedButton
- Components: ImageButton, ImageView, LinearLayout, ScrollView, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/activity_email_verification.xml (Activity)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.Material3.Button.OutlinedButton, @style/Widget.Material3.Button.TextButton
- Components: ProgressBar, ScrollView, TextView, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/activity_forgot_password.xml (Activity)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Unresolved style refs: @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox
- Components: LinearLayout, ProgressBar, TextView, androidx.constraintlayout.widget.ConstraintLayout, com.airbnb.lottie.LottieAnimationView, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_help_support.xml (Activity)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Components: LinearLayout, TextView, androidx.constraintlayout.widget.ConstraintLayout, com.airbnb.lottie.LottieAnimationView, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/activity_jwt_test.xml (Activity)
- Direct fontFamily: monospace, sans-serif-black, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): monospace, sans-serif-black, sans-serif-medium
- Unresolved style refs: @style/Widget.Material3.Button.OutlinedButton, @style/Widget.Material3.Button.TextButton, @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox
- Components: FrameLayout, ImageButton, LinearLayout, ProgressBar, TextView, View, androidx.core.widget.NestedScrollView, com.google.android.material.button.MaterialButton, com.google.android.material.card.MaterialCardView, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_login.xml (Activity)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.Material3.Button.OutlinedButton, @style/Widget.Material3.Button.TextButton, @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox
- Components: LinearLayout, ProgressBar, ScrollView, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_main.xml (Activity)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: ScrollView, TextView, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.floatingactionbutton.FloatingActionButton, com.gridee.parking.ui.components.CustomBottomNavigation

### Gridee_Android/android-app/app/src/main/res/layout/activity_main_container.xml (Activity)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: FrameLayout, androidx.constraintlayout.widget.ConstraintLayout, com.gridee.parking.ui.components.CustomBottomNavigation

### Gridee_Android/android-app/app/src/main/res/layout/activity_notifications.xml (Activity)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Components: LinearLayout, TextView, androidx.constraintlayout.widget.ConstraintLayout, com.airbnb.lottie.LottieAnimationView, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/activity_operator_dashboard.xml (Activity)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Unresolved style refs: @style/Widget.MaterialComponents.Button, @style/Widget.MaterialComponents.Button.TextButton
- Components: EditText, FrameLayout, ImageView, LinearLayout, ProgressBar, TextView, View, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout, androidx.core.widget.NestedScrollView, androidx.swiperefreshlayout.widget.SwipeRefreshLayout, com.google.android.material.button.MaterialButton, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/activity_parking_details.xml (Activity)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: ImageButton, LinearLayout, ScrollView, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_parking_discovery.xml (Activity)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: Button, EditText, FrameLayout, ImageButton, ImageView, LinearLayout, ProgressBar, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.floatingactionbutton.FloatingActionButton, com.gridee.parking.ui.components.CustomBottomNavigation

### Gridee_Android/android-app/app/src/main/res/layout/activity_parking_lot_selection.xml (Activity)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: ImageButton, ImageView, LinearLayout, ProgressBar, TextView, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView

### Gridee_Android/android-app/app/src/main/res/layout/activity_parking_spot_selection.xml (Activity)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: ImageButton, ImageView, LinearLayout, ProgressBar, TextView, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView

### Gridee_Android/android-app/app/src/main/res/layout/activity_payment.xml (Activity)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: ImageButton, ImageView, LinearLayout, ProgressBar, RelativeLayout, ScrollView, TextView, androidx.appcompat.widget.Toolbar, com.google.android.material.appbar.AppBarLayout, com.google.android.material.button.MaterialButton, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/activity_privacy_settings.xml (Activity)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: Button, ImageButton, ImageView, LinearLayout, ScrollView, TextView, androidx.appcompat.widget.SwitchCompat, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_profile.xml (Activity)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Unresolved style refs: ?attr/materialButtonOutlinedStyle
- Components: Button, ImageButton, ImageView, LinearLayout, ProgressBar, ScrollView, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView, com.gridee.parking.ui.components.CustomBottomNavigation

### Gridee_Android/android-app/app/src/main/res/layout/activity_qr_scanner.xml (Activity)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: FrameLayout, ImageButton, ImageView, LinearLayout, ProgressBar, TextView, View, androidx.camera.view.PreviewView, com.journeyapps.barcodescanner.DecoratedBarcodeView

### Gridee_Android/android-app/app/src/main/res/layout/activity_registration.xml (Activity)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.Material3.Button.TextButton, @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox, @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox.ExposedDropdownMenu
- Components: LinearLayout, ProgressBar, ScrollView, TextView, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.MaterialAutoCompleteTextView, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_search.xml (Activity)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Components: AutoCompleteTextView, ImageView, LinearLayout, ProgressBar, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/activity_splash.xml (Activity)
- Direct fontFamily: sans-serif-black
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-black
- Components: ImageView, TextView, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_transaction_history.xml (Activity)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Components: HorizontalScrollView, ImageView, LinearLayout, ProgressBar, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/activity_transaction_history_new.xml (Activity)
- Direct fontFamily: sans-serif-black, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-black, sans-serif-medium
- Unresolved style refs: @style/Widget.MaterialComponents.Button.OutlinedButton
- Components: FrameLayout, HorizontalScrollView, ImageView, LinearLayout, ProgressBar, TextView, androidx.appcompat.widget.Toolbar, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView, com.google.android.material.appbar.AppBarLayout, com.google.android.material.button.MaterialButton, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/activity_vehicles.xml (Activity)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: TextView, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/activity_welcome.xml (Activity)
- Direct fontFamily: @font/inter_bold
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold
- Unresolved style refs: @style/Widget.Material3.Button.OutlinedButton, @style/Widget.Material3.Button.TextButton
- Components: FrameLayout, LinearLayout, ProgressBar, ScrollView, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_add_vehicle.xml (BottomSheet)
- Direct fontFamily: @font/inter_bold, @font/inter_medium, sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold, @font/inter_medium, sans-serif
- Unresolved style refs: @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox
- Components: ImageView, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_booking_detail.xml (BottomSheet)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.MaterialComponents.Button.OutlinedButton
- Components: Button, ImageButton, LinearLayout, ProgressBar, TextView, View, androidx.cardview.widget.CardView, androidx.core.widget.NestedScrollView

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_booking_details.xml (BottomSheet)
- Direct fontFamily: monospace
- Font via style/textAppearance: none
- Fonts used (explicit): monospace
- Components: Button, ImageView, LinearLayout, TextView, View, androidx.cardview.widget.CardView

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_booking_filters.xml (BottomSheet)
- Direct fontFamily: @font/inter_bold, @font/inter_medium, @font/inter_semibold
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold, @font/inter_medium, @font/inter_semibold
- Unresolved style refs: @style/Widget.MaterialComponents.Button.TextButton
- Components: HorizontalScrollView, ImageView, LinearLayout, RadioButton, Space, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, androidx.core.widget.NestedScrollView, com.google.android.material.button.MaterialButton, com.google.android.material.card.MaterialCardView, com.google.android.material.chip.ChipGroup

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_booking_overview.xml (BottomSheet)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.Material3.Button.TonalButton
- Components: FrameLayout, ImageButton, ImageView, LinearLayout, ScrollView, TextView, View, com.google.android.material.button.MaterialButton, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_edit_photo.xml (BottomSheet)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: ImageView, LinearLayout, TextView, View, androidx.appcompat.widget.AppCompatButton, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_edit_vehicle.xml (BottomSheet)
- Direct fontFamily: @font/inter_bold, @font/inter_medium, sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold, @font/inter_medium, sans-serif
- Unresolved style refs: @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox
- Components: ImageView, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_operator_menu.xml (BottomSheet)
- Direct fontFamily: @font/inter_bold, @font/inter_medium, @font/inter_semibold
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold, @font/inter_medium, @font/inter_semibold
- Components: FrameLayout, ImageView, LinearLayout, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, androidx.core.widget.NestedScrollView, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_operator_menu_v2.xml (BottomSheet)
- Direct fontFamily: @font/inter_medium, @font/inter_regular, @font/inter_semibold
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_medium, @font/inter_regular, @font/inter_semibold
- Components: ImageView, LinearLayout, TextView, View, com.airbnb.lottie.LottieAnimationView, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_parking_spot.xml (BottomSheet)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Unresolved style refs: @style/Widget.Material3.Button.OutlinedButton
- Components: ImageView, LinearLayout, TextView, View, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout, androidx.core.widget.NestedScrollView, com.google.android.material.button.MaterialButton, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_payment_method_filter.xml (BottomSheet)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.MaterialComponents.Button, @style/Widget.MaterialComponents.CompoundButton.RadioButton
- Components: LinearLayout, RadioGroup, TextView, View, com.google.android.material.button.MaterialButton, com.google.android.material.radiobutton.MaterialRadioButton

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_profile_page.xml (BottomSheet)
- Direct fontFamily: @font/inter_bold, @font/inter_medium, @font/inter_regular, @font/inter_semibold, sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold, @font/inter_medium, @font/inter_regular, @font/inter_semibold, sans-serif, sans-serif-medium
- Components: FrameLayout, ImageView, LinearLayout, ProgressBar, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, androidx.core.widget.NestedScrollView, com.airbnb.lottie.LottieAnimationView, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_top_up.xml (BottomSheet)
- Direct fontFamily: @font/inter_bold, @font/inter_medium, @font/inter_semibold
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold, @font/inter_medium, @font/inter_semibold
- Unresolved style refs: @style/Widget.MaterialComponents.Button.OutlinedButton
- Components: Button, ImageView, LinearLayout, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, androidx.core.widget.NestedScrollView, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_universal.xml (BottomSheet)
- Direct fontFamily: @font/inter_regular, @font/inter_semibold
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_regular, @font/inter_semibold
- Components: ImageView, LinearLayout, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, androidx.core.widget.NestedScrollView, com.airbnb.lottie.LottieAnimationView, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_vehicle_options.xml (BottomSheet)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Components: ImageView, LinearLayout, TextView, View, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_vehicle_scan_success.xml (BottomSheet)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Unresolved style refs: @style/Widget.MaterialComponents.Button.TextButton, @style/Widget.MaterialComponents.TextInputLayout.FilledBox
- Components: LinearLayout, ProgressBar, ScrollView, TextView, View, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/bottom_sheet_vehicle_scan_timeout.xml (BottomSheet)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Unresolved style refs: @style/Widget.MaterialComponents.Button, @style/Widget.MaterialComponents.Button.TextButton, @style/Widget.MaterialComponents.TextInputLayout.FilledBox
- Components: ImageView, LinearLayout, TextView, com.google.android.material.button.MaterialButton, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/custom_bottom_navigation.xml (Custom)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: FrameLayout, ImageView, LinearLayout, TextView, View

### Gridee_Android/android-app/app/src/main/res/layout/custom_notification.xml (Custom)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: ImageView, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/dialog_extend_booking.xml (Dialog)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.Material3.Button.TextButton
- Components: Button, DatePicker, LinearLayout, TextView, TimePicker

### Gridee_Android/android-app/app/src/main/res/layout/dialog_logout_confirmation.xml (Dialog)
- Direct fontFamily: @font/inter_medium, @font/inter_regular, @font/inter_semibold
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_medium, @font/inter_regular, @font/inter_semibold
- Unresolved style refs: @style/Widget.MaterialComponents.Button, @style/Widget.MaterialComponents.Button.OutlinedButton
- Components: LinearLayout, TextView, com.google.android.material.button.MaterialButton

### Gridee_Android/android-app/app/src/main/res/layout/dialog_spot_selection.xml (Dialog)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Unresolved style refs: @style/Widget.MaterialComponents.Button, @style/Widget.MaterialComponents.Button.TextButton
- Components: Button, ImageView, LinearLayout, ProgressBar, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView

### Gridee_Android/android-app/app/src/main/res/layout/dialog_vehicle_selection.xml (Dialog)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Unresolved style refs: @style/Widget.Material3.Button, @style/Widget.Material3.Button.TextButton
- Components: Button, ImageView, LinearLayout, TextView, androidx.cardview.widget.CardView, androidx.recyclerview.widget.RecyclerView

### Gridee_Android/android-app/app/src/main/res/layout/fab_book_parking.xml (Fab)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: androidx.coordinatorlayout.widget.CoordinatorLayout, com.google.android.material.floatingactionbutton.FloatingActionButton

### Gridee_Android/android-app/app/src/main/res/layout/fragment_bookings_new.xml (Fragment)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: FrameLayout, ImageView, LinearLayout, TextView, View, androidx.appcompat.widget.Toolbar, androidx.constraintlayout.widget.ConstraintLayout, androidx.coordinatorlayout.widget.CoordinatorLayout, androidx.recyclerview.widget.RecyclerView, androidx.swiperefreshlayout.widget.SwipeRefreshLayout, com.google.android.material.appbar.AppBarLayout, com.google.android.material.appbar.CollapsingToolbarLayout, com.google.android.material.card.MaterialCardView, include

### Gridee_Android/android-app/app/src/main/res/layout/fragment_home.xml (Fragment)
- Direct fontFamily: @font/inter_bold
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold
- Components: ImageView, ProgressBar, ScrollView, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView, com.airbnb.lottie.LottieAnimationView

### Gridee_Android/android-app/app/src/main/res/layout/fragment_main_page.xml (Fragment)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: Button, LinearLayout, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/fragment_parking_list.xml (Fragment)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: ImageView, LinearLayout, TextView, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView

### Gridee_Android/android-app/app/src/main/res/layout/fragment_parking_map.xml (Fragment)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: LinearLayout, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/fragment_profile.xml (Fragment)
- Direct fontFamily: @font/inter_bold, @font/inter_medium, @font/inter_regular
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold, @font/inter_medium, @font/inter_regular
- Components: FrameLayout, ImageView, LinearLayout, TextView, View, androidx.appcompat.widget.Toolbar, androidx.cardview.widget.CardView, androidx.coordinatorlayout.widget.CoordinatorLayout, androidx.core.widget.NestedScrollView, com.google.android.material.appbar.AppBarLayout, com.google.android.material.appbar.CollapsingToolbarLayout

### Gridee_Android/android-app/app/src/main/res/layout/fragment_wallet.xml (Fragment)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Unresolved style refs: @style/Widget.Material3.Button.OutlinedButton
- Components: Button, ImageView, LinearLayout, ProgressBar, ScrollView, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout, androidx.recyclerview.widget.RecyclerView

### Gridee_Android/android-app/app/src/main/res/layout/fragment_wallet_new.xml (Fragment)
- Direct fontFamily: @font/inter_bold, @font/inter_medium, @font/inter_regular
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold, @font/inter_medium, @font/inter_regular
- Components: ImageView, LinearLayout, TextView, androidx.cardview.widget.CardView, androidx.compose.ui.platform.ComposeView, androidx.constraintlayout.widget.ConstraintLayout, androidx.core.widget.NestedScrollView, androidx.recyclerview.widget.RecyclerView, androidx.swiperefreshlayout.widget.SwipeRefreshLayout, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/item_add_vehicle.xml (Item)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: ImageView, LinearLayout, TextView

### Gridee_Android/android-app/app/src/main/res/layout/item_availability_summary.xml (Item)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Components: TextView, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/item_booking.xml (Item)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: ImageView, LinearLayout, ProgressBar, TextView, View, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/item_booking_active_pass.xml (Item)
- Direct fontFamily: @font/inter_bold, @font/inter_medium
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold, @font/inter_medium
- Unresolved style refs: @style/Widget.MaterialComponents.Button.OutlinedButton, @style/Widget.MaterialComponents.Button.UnelevatedButton
- Components: FrameLayout, ImageView, LinearLayout, ProgressBar, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.button.MaterialButton, com.gridee.parking.ui.views.TicketView

### Gridee_Android/android-app/app/src/main/res/layout/item_booking_compact.xml (Item)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: ImageView, LinearLayout, TextView, View, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/item_booking_fixed.xml (Item)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: LinearLayout, TextView, androidx.cardview.widget.CardView

### Gridee_Android/android-app/app/src/main/res/layout/item_booking_modern_fixed.xml (Item)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: LinearLayout, TextView, androidx.cardview.widget.CardView

### Gridee_Android/android-app/app/src/main/res/layout/item_dropdown_menu.xml (Item)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: TextView

### Gridee_Android/android-app/app/src/main/res/layout/item_operator_activity.xml (Item)
- Direct fontFamily: @font/inter_medium, @font/inter_regular
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_medium, @font/inter_regular
- Components: LinearLayout, TextView, View

### Gridee_Android/android-app/app/src/main/res/layout/item_parking_lot.xml (Item)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: LinearLayout, TextView, androidx.cardview.widget.CardView

### Gridee_Android/android-app/app/src/main/res/layout/item_parking_option.xml (Item)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Components: LinearLayout, TextView, View

### Gridee_Android/android-app/app/src/main/res/layout/item_parking_spot.xml (Item)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: ImageView, LinearLayout, RatingBar, TextView, androidx.cardview.widget.CardView, androidx.constraintlayout.widget.ConstraintLayout

### Gridee_Android/android-app/app/src/main/res/layout/item_parking_spot_home.xml (Item)
- Direct fontFamily: @font/inter_bold
- Font via style/textAppearance: none
- Fonts used (explicit): @font/inter_bold
- Components: FrameLayout, ImageView, LinearLayout, TextView, View, androidx.constraintlayout.widget.ConstraintLayout, com.google.android.material.card.MaterialCardView

### Gridee_Android/android-app/app/src/main/res/layout/item_parking_spot_selection.xml (Item)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: ImageView, LinearLayout, TextView, androidx.cardview.widget.CardView

### Gridee_Android/android-app/app/src/main/res/layout/item_transaction.xml (Item)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: FrameLayout, ImageView, LinearLayout, TextView, com.gridee.parking.ui.views.RelativeTimeTextView

### Gridee_Android/android-app/app/src/main/res/layout/item_vehicle.xml (Item)
- Direct fontFamily: sans-serif, sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif, sans-serif-medium
- Components: ImageView, LinearLayout, TextView

### Gridee_Android/android-app/app/src/main/res/layout/item_vehicle_input.xml (Item)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Unresolved style refs: @style/Widget.MaterialComponents.TextInputLayout.OutlinedBox
- Components: LinearLayout, com.google.android.material.textfield.TextInputEditText, com.google.android.material.textfield.TextInputLayout

### Gridee_Android/android-app/app/src/main/res/layout/item_vehicle_profile.xml (Item)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: ImageView, LinearLayout, TextView, androidx.cardview.widget.CardView

### Gridee_Android/android-app/app/src/main/res/layout/item_vehicle_selection.xml (Item)
- Direct fontFamily: sans-serif
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif
- Components: ImageView, LinearLayout, RadioButton, TextView, androidx.cardview.widget.CardView

### Gridee_Android/android-app/app/src/main/res/layout/item_wallet_transaction_header.xml (Item)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: TextView

### Gridee_Android/android-app/app/src/main/res/layout/segment_bookings_ios.xml (Segment)
- Direct fontFamily: sans-serif-medium
- Font via style/textAppearance: none
- Fonts used (explicit): sans-serif-medium
- Components: FrameLayout, LinearLayout, TextView, View

### Gridee_Android/android-app/app/src/main/res/layout/test_booking_card.xml (Test)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: LinearLayout, TextView, androidx.cardview.widget.CardView

### Gridee_Android/android-app/app/src/main/res/layout/window_parking_dropdown.xml (Window)
- Direct fontFamily: none
- Font via style/textAppearance: none
- Fonts used (explicit): none (falls back to theme defaults)
- Components: androidx.cardview.widget.CardView, androidx.recyclerview.widget.RecyclerView

