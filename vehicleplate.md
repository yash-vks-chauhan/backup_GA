# Indian Vehicle Number Plate Research

Researched on: 2026-04-14

This document summarizes the current Indian vehicle registration-mark and number-plate rules that matter for app validation, OCR normalization, and storage.

## Executive summary

The current validator in this repo assumes one narrow format:

- `^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$`
- hard-coded length: `9-10`

That is too strict for India.

Indian vehicle registrations are **not** safely limited to one `9/10` character pattern. In practice, your app must handle:

- regular private/non-transport registrations
- regular transport registrations
- Bharat Series (`BH`)
- electric vehicles
- rent-a-cab / self-drive rental vehicles
- temporary registrations
- dealer trade registrations
- diplomatic / consular / UN / other mission-linked registrations
- defence vehicles
- vintage vehicles
- legacy and state-specific series variations

The biggest implementation mistake is this:

- **plate color/category and plate text are not always the same thing**

Examples:

- An EV usually has the **same registration text pattern** as a normal vehicle; the EV distinction is often the **green plate background**, not a unique alphanumeric syntax.
- A self-drive rental vehicle is identified by **yellow text on black background**, not by a completely different number string.
- A transport vehicle and a non-transport vehicle may have similar registration text, but different plate colors.

So the app should validate the **text format** separately from the **plate class**.

## What the law says at a high level

Under the Central Motor Vehicles Rules, plate display is governed mainly by rule 50 and related notifications.

Key official points:

- HSRP is mandatory under rule 50 for motor vehicles.
- Plates carry `IND`, hologram/security features, laser branding, and a third registration mark sticker.
- Standard display colors are:
  - transport: black text on yellow background
  - other cases: black text on white background
- Battery-operated vehicles use a green background:
  - transport EV: yellow text on green background
  - non-transport EV: white text on green background
- Dealer/trade vehicles use white on red.
- Temporary registration uses red on yellow.
- Rent-a-cab/self-drive vehicles use yellow on black.
- BH-series has its own defined text format.
- Vintage vehicles have a defined `VA` format.
- Diplomatic / consular / UN / related classes have separate special-mark rules.
- Defence vehicles use a completely different military-style registration syntax.

## Plate types you should know

### 1. Regular registration: non-transport

Typical visible example:

- `MH 12 AB 1234`
- `DL 01 C 1234`

Color:

- black text on white background

Important points:

- This is the most common civilian format.
- The text is not always exactly 9 or 10 characters once spaces are removed.
- Legacy and state-issued variations exist.
- The final numeric block is often four digits, but the overall registration text should not be treated as fixed-length.

Implementation note:

- This should be the default category for normal app users.

### 2. Regular registration: transport/commercial

Typical visible example:

- same core text style as regular registration

Color:

- black text on yellow background

Important points:

- The text structure can look very similar to non-transport.
- The differentiator is mainly the background color.

Implementation note:

- Do not assume you can infer transport vs non-transport from the entered string alone.

### 3. Bharat Series (`BH`)

Official format:

- `YY BH #### X`
- `YY BH #### XX`

Where:

- `YY` = last two digits of year of registration
- `BH` = Bharat Series code
- `####` = `0001` to `9999`
- suffix letter(s) progress as `A, B, C ...` and then `AA, AB ... ZZ`, excluding `I` and `O`

Examples:

- `22 BH 1234 A`
- `24 BH 0099 AA`

Color:

- black on white background

Important points:

- This is a real, current, official format.
- Your current validator rejects this completely.
- BH can apply to eligible owners, and later rules/advisories also cover conversion from regular series in eligible cases.

Normalized length:

- usually `9` or `10` without spaces if suffix is one letter
- usually `10` or `11` without spaces if suffix is two letters

Implementation note:

- This should be explicitly supported.

### 4. Battery-operated vehicle (EV)

Official distinction:

- **Non-transport EV**: white text on green background
- **Transport EV**: yellow text on green background

Important points:

- The registration text may still be regular-series text or BH-series text.
- The EV status is primarily identified by **plate background**, not a unique string pattern.

Implementation note:

- If the app needs to know whether a vehicle is EV, do not derive it only from the typed registration number.
- Capture EV as:
  - a separate user-selected vehicle type, or
  - a backend flag, or
  - a color-aware OCR/classifier result when scanning a real plate image

### 5. Rent-a-cab / self-drive rental

Official distinction:

- yellow text on black background

Important points:

- Again, the text itself may still look like a standard registration mark.
- The category is identified by plate styling/background.

Implementation note:

- Do not try to infer this from the alphanumeric string alone.

### 6. Temporary registration

Official example format from rule 53C:

- `T` = temporary certificate
- `MM` = month of issue
- `YY` = year of issue
- `SS` = state code
- `1234` = serial number
- trailing alphabet(s), excluding `O` and `I`

Example:

- `T 08 26 KA 1234 AB`

Color:

- red text on yellow background

Important points:

- Temporary registration is now explicitly structured in the rules.
- This is not the same as a permanent regular registration.

Implementation note:

- If your parking app allows brand-new vehicles before final registration, support this pattern.
- If not, reject it with a clear message instead of misclassifying it as an invalid normal number.

### 7. Dealer / trade certificate vehicles

Official distinction:

- white text on red background

Important points:

- These are vehicles in the possession of dealers.
- This is not a normal consumer-owned permanent registration.

Implementation note:

- Usually not needed for normal user account vehicle storage.
- If encountered in operator flows, mark as a separate category.

### 8. Diplomatic / consular / UN / related mission classes

Official classes include special registration marks around:

- `CD`
- `CC`
- `UN`
- related mission-linked special categories such as `IOD` / `IOC` as seen in later notification tables

Color classes in official rules/notifications include:

- deep blue background with white or yellow text depending on class
- light green background with white text for certain home-based non-diplomatic categories

Important points:

- These are real special registrations.
- They do not follow your current private-vehicle regex.

Implementation note:

- For a consumer parking app, these should usually go to:
  - manual review
  - operator override
  - or a dedicated special-pattern parser

### 9. Defence vehicles

Official rule 74 format is different from civilian plates.

It includes:

- a group of figures
- followed by a single capital letter
- followed by a broad arrow
- followed by up to six figures
- followed by a capital letter or group of letters

Important points:

- This is a completely different registration system.
- A civilian regex should not try to force-match this.

Implementation note:

- Support only if your product actually needs it.
- Otherwise route to manual handling.

### 10. Vintage vehicles

Official fresh-registration format:

- `XX VA YY ####`

Where:

- `XX` = state code
- `VA` = vintage
- `YY` = two-letter series
- `####` = `0001` to `9999`

Example:

- `KA VA AB 1234`

Important points:

- Already-registered vintage vehicles may retain their original registration mark.
- Fresh vintage registrations get the `VA` format.

Implementation note:

- If you want full India support, add this format.

## There is no safe single “9-digit or 10-digit” rule

This is the core conclusion for the app.

What is true:

- many modern civilian registrations normalize to `9` or `10` characters
- BH also often lands around `9` to `11`
- temporary can be longer
- vintage can differ
- diplomatic / defence / special marks differ

What is false:

- “All Indian vehicle numbers are 9 or 10 characters”

So the validator must become **category-aware**, not length-only.

## Practical format model for the app

For this app, use the following model.

### Category A: supported for normal users

- `REGULAR`
- `BH`
- `TEMPORARY` if you want to allow very new vehicles
- `VINTAGE` if you want true India-wide support

### Category B: special/admin/manual

- `TRADE`
- `DIPLOMATIC_OR_CONSULAR`
- `DEFENCE`
- other mission-linked special classes

### Category C: plate-style-only metadata, not string-only metadata

- `EV`
- `TRANSPORT`
- `RENT_A_CAB`

These should be stored as **separate attributes**, because text alone is not enough.

## Recommended validation strategy

### 1. Normalize first

Before validation:

- trim whitespace
- uppercase
- remove spaces
- remove hyphens
- remove dots/slashes users might type casually
- preserve the original raw input separately for audit/debugging if needed

Suggested normalization:

```kotlin
fun normalizePlate(input: String): String =
    input
        .trim()
        .uppercase()
        .replace(Regex("[\\s\\-./]"), "")
```

### 2. Parse by category, in order

Recommended order:

1. `BH`
2. `TEMPORARY`
3. `VINTAGE`
4. `SPECIAL_DIPLOMATIC`
5. `DEFENCE`
6. `TRADE`
7. `REGULAR`

Reason:

- the more specific formats should be matched before the generic regular-expression fallback

### 3. Use separate regexes, not one global regex

Suggested Kotlin starting point:

```kotlin
enum class PlateType {
    REGULAR,
    BH,
    TEMPORARY,
    VINTAGE,
    SPECIAL,
    UNKNOWN
}

object IndianVehiclePlateParser {
    private val bh = Regex("""^\d{2}BH\d{4}[A-HJ-NP-Z]{1,2}$""")
    private val temporary = Regex("""^T\d{2}\d{2}[A-Z]{2}\d{4}[A-HJ-NP-Z]{1,2}$""")
    private val vintage = Regex("""^[A-Z]{2}VA[A-Z]{2}\d{4}$""")

    // Deliberately lenient: regular Indian civilian marks are not safely reducible
    // to one exact 9-10 char shape across legacy/current/state variations.
    private val regular = Regex("""^[A-Z]{2}\d{1,2}[A-Z]{1,3}\d{1,4}$""")

    fun parse(raw: String): PlateType {
        val value = normalizePlate(raw)
        return when {
            bh.matches(value) -> PlateType.BH
            temporary.matches(value) -> PlateType.TEMPORARY
            vintage.matches(value) -> PlateType.VINTAGE
            regular.matches(value) -> PlateType.REGULAR
            else -> PlateType.UNKNOWN
        }
    }
}
```

Notes:

- The `regular` regex above is intentionally more permissive than your current validator.
- That is on purpose.
- India has legacy/current/state-series variability, so the app should prefer **not rejecting valid plates**.

### 4. Keep strictness where it actually helps

Good strictness:

- explicit BH support
- explicit temporary support
- explicit vintage support
- normalization before save/search
- duplicate detection on normalized value

Bad strictness:

- rejecting everything that is not exactly `XX00XX0000`
- assuming all valid plates are `9-10` chars
- assuming EV/commercial/rental can be derived from the text alone

### 5. Add OCR-aware correction carefully

If plate scan is used, consider position-aware OCR correction:

- in digit-only slots: `O -> 0`, `I/L -> 1`, `S -> 5`, `B -> 8`, `Z -> 2`
- in letter-only slots: reverse-map only when OCR confidence is low

Do not blindly rewrite characters globally.

### 6. Store parsed metadata separately

Recommended stored fields:

- `plateRaw`
- `plateNormalized`
- `plateType`
- `isEv` or `fuelType`
- `isTransport`
- `isRental`
- `validationVersion`

This will help future migrations if rules evolve.

## What should change in this codebase

Current validator:

- [`Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/VehicleNumberValidator.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/VehicleNumberValidator.kt)

Current impacted call sites:

- [`Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/auth/AddVehicleViewModel.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/auth/AddVehicleViewModel.kt)
- [`Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/bottomsheet/AddVehicleBottomSheet.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/bottomsheet/AddVehicleBottomSheet.kt)
- [`Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/bottomsheet/EditVehicleBottomSheet.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/bottomsheet/EditVehicleBottomSheet.kt)
- [`Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/bottomsheet/SelectVehicleBottomSheet.kt`](Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/bottomsheet/SelectVehicleBottomSheet.kt)

### Recommended refactor

Replace the current validator with:

- `normalize(input)`
- `parse(input): PlateType`
- `isSupportedForUser(input): Boolean`
- `getError(input): String?`

Suggested behavior:

- accept `REGULAR`
- accept `BH`
- optionally accept `TEMPORARY`
- optionally accept `VINTAGE`
- reject `SPECIAL` for self-service user flow, but show a useful message

Example error strategy:

- `UNKNOWN`: "Enter a valid Indian vehicle registration number"
- `SPECIAL`: "This special registration type needs manual verification"

## UX recommendations

- Placeholder text should not show only one example.
- Use multiple examples:
  - `MH12AB1234`
  - `22BH1234A`
  - `T0826KA1234AB`
  - `KAVAAB1234`
- On validation failure, say “Indian vehicle registration number” instead of “vehicle number must be 9-10 characters”.
- If the user says a vehicle is electric/commercial/rental, store that separately from the registration number.

## HSRP and physical plate details that matter

Rule 50 and MoRTH HSRP guidance require features such as:

- `IND` on the left
- chromium-based hologram
- laser-branded identification number
- embossed characters
- tamper-resistant fixing
- third registration mark sticker on windshield

Plate sizes mentioned in the rules include:

- two and three wheelers: `200 x 100 mm`
- passenger cars / LMV: `340 x 200 mm` or `500 x 120 mm`
- medium/heavy/trailer: `340 x 200 mm`
- tractors and power tillers have separate special sizes

Implementation note:

- These physical rules matter for real-plate scanning and OCR framing.
- They do **not** change the stored registration string.

## State/UT code note

The official state-code appendix exists, but older compendia and later amendments do not always appear in one clean place.

Officially confirmed recent changes found during research:

- `DD` for Dadra and Nagar Haveli and Daman and Diu from S.O. 295(E), effective 2020-01-26
- `LA` for Ladakh from S.O. 4262(E), 2019-11-25
- `TG` for Telangana from S.O. 1306(E), 2024-03-12

Implementation recommendation:

- Do **not** block users with a hard-coded state-code allow-list unless you plan to keep that list versioned and updated.
- If you do use a code list, keep it in remote config or a versioned data file, not buried in a single regex.

## Bottom line for this app

You should not validate Indian vehicle numbers as only:

- exactly `9-10` characters
- exactly `XX00XX0000`

You should validate them as:

- a set of official Indian registration categories
- with normalization first
- explicit support for `BH`
- optional support for `TEMPORARY` and `VINTAGE`
- separate metadata for EV/commercial/rental
- manual-review handling for diplomatic/defence/trade/special cases

## Sources

Primary sources used:

1. Central Motor Vehicles Rules, 1989 consolidated text on India Code, including rule 50, rule 74, rule 76, rule 77:
   - https://upload.indiacode.nic.in/showfile?actid=AC_CEN_30_42_00009_198859_1517807326286&filename=Cmvr1989.pdf&type=rule
2. Consolidated CMVR compendium with Appendix XIII state-code table and progression rules:
   - https://upload.indiacode.nic.in/showfile?actid=AC_BR_59_851_00005_00005_1712231779316&filename=cmvr-1989.pdf&type=rule
3. Bharat Series notification G.S.R. 594(E), 2021:
   - https://upload.indiacode.nic.in/showfile?actid=AC_CEN_30_42_00009_198859_1517807326286&filename=20th_gsr_594%28e%29_26_.08.2021_bh_series_registration_mark_rules.pdf&type=notification
4. BH-series amendment / transfer-related notification G.S.R. 879(E), 2022:
   - https://morth.nic.in/sites/default/files/notifications_document/MVL-23rd-GSR879%28E%29-dated-14%20Dec%202022-BH%20series_0.pdf
5. MoRTH advisory on BH implementation for vehicles already in regular series, dated 2024-03-18:
   - https://morth.nic.in/sites/default/files/comprehensive_compendium_circular/44-Advisory%20to%20TC%20and%20Principal%20Secretaries%20dt.%2018.03.2024%20reg%20BH%20Series%20implementation.pdf
6. Battery-operated vehicle registration mark notification G.S.R. 749(E), 2018:
   - https://morth.nic.in/sites/default/files/notifications_document/Notification_no_G_S_R_749E_dated_07_08_2018_regarding_Background_colour_of_registration_plates_for_Electric_vehicles.pdf
7. Parivahan page for battery-operated registration marks:
   - https://parivahan.gov.in/parivahan/en/content/registration-mark-battery-operated-vehicles
8. MoRTH notification table on alphanumeric/background colors of registration marks S.O. 2339(E), 2020:
   - https://morth.nic.in/sites/default/files/notifications_document/mark-compressed_0.pdf
9. MoRTH HSRP page:
   - https://morth.nic.in/en/high-security-registration-plates
10. Temporary certificate Form 23B on Parivahan:
   - https://parivahan.gov.in/sites/default/files/DownloadForm/cmvr/FORM-23B.pdf
11. Parivahan reassignment guidance:
   - https://parivahan.gov.in/en/content/reassignment
12. Parivahan diplomatic vehicles guidance:
   - https://parivahan.gov.in/parivahan/en/content/diplomatic-vehicles
13. Road Transport Year Book entry covering vintage vehicle registration format:
   - https://morth.nic.in/sites/default/files/RTH-Road-Transport-Year-Book-2020-21%20%26%202021-22.pdf
14. MoRTH S.O. 295(E) for `DD`:
   - https://morth.nic.in/sites/default/files/notifications_document/SO%20295.pdf
15. MoRTH S.O. 4262(E) for `LA`:
   - https://morth.nic.in/en/so-4262e-regarding-registration-mark-ladakh-la
16. MoRTH S.O. 1306(E) for `TG`:
   - https://morth.nic.in/sites/default/files/notifications_document/8-SO%201306%28E%29%2012%20March%202024%20TG%20registration%20mark%20Telangana.pdf

## Confidence notes

- High confidence:
  - HSRP requirements
  - transport/non-transport colors
  - EV green plate rules
  - BH format
  - temporary format
  - defence special format
  - diplomatic/consular special handling
  - vintage `VA` format
- Medium confidence:
  - exact strict regex that should be used for every regular civilian legacy/state edge case
  - any permanently hard-coded all-India state-code allow-list without a maintenance process

For app validation, the right engineering choice is to be **strict on known special formats** and **lenient on general regular plates**.
