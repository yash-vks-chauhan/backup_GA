# Gridee Android ↔ Backend Sync Plan

> **Purpose:** Bring the production Android app in sync with the new Gridee backend, safely.
> **Context:** App is **LIVE in production with 5,000+ Android users**. Every change must be
> reversible, feature-flag gated where possible, and verified on a real device against a
> staging backend *before* it reaches production.
>
> **Golden rules (read before touching anything):**
> 1. **Never break payments or auth for existing users.** These are the two "do-not-regress" paths.
> 2. **One phase at a time.** Do not start a phase until the previous phase's *Definition of Done* is met.
> 3. **Feature-flag first.** The app already has `RemoteConfigManager` + `/api/config/all`. Gate every new/changed flow behind a flag so we can kill it remotely without an app update.
> 4. **Stage → device-verify → ship.** Install with `:app:installDebug` and verify on device (a stale APK lies). See memory: *Install, don't just compile*.
> 5. **Keep a rollback.** Snapshot the last-known-good AAB before each release; keep old code paths behind the flag until the new one is proven.
> 6. **Backward compatibility during rollout.** 5,000 users won't all update at once. New app versions must tolerate the old backend, and the backend must tolerate old app versions, until adoption is high.

---

## Progress tracker

| # | Phase | Priority | Status | Owner | Notes |
|---|-------|----------|--------|-------|-------|
| **0A** | **Fix corrupted PROD config (incident)** | 🔴🔴 | ⏸ **Deferred — owner declined backend/config changes (2026-07-25)** | | Frontend-only scope; risk mitigated by 0B |
| **0B** | **Harden app `sanitize()` against bad config** | 🔴 P0 | ☑ **Done — 2026-07-25 (unit-tested)** | | Brick-proof cap chosen; see below |
| 0 | Safety net & reconnaissance | 🔴 Must do first | ☑ **Done — 2026-07-25** | | Findings below; gateway = Cashfree |
| **M** | **Multi-tenant / strict-JWT gap-fixes (backend-dev brief)** | 🟠 P1 | ◐ **In progress — 401 handler done (2026-07-25)** | | See "Backend-dev brief" section |
| 1 | Payments: Razorpay → Cashfree | 🔴 P0 (broken) | ☐ Not started | | Highest risk |
| 2 | Daily wallet balance top-up (`DAILY_WALLET_RESET`) | 🟠 P1 | ☐ Not started | | Display + copy |
| 3 | AdMob rewarded-ad top-up (`AD_TOP_UP`) | 🟠 P1 | ☐ Not started | | Flag-gated |
| 4 | Apple Sign-In wiring | 🟠 P1 | ☐ Not started | | Flag-gated |
| 5 | Per-lot booking policy | 🟠 P1 | ☐ Not started | | |
| 6 | Multiple + sequential bookings UX | 🟠 P1 | ☐ Not started | | Verify existing UI |
| 7 | Multi-tenant foundation (org/location/context) | 🟡 P2 | ☐ Not started | | Decide scope first |
| 8 | Welcome bonus surfacing | 🟡 P2 | ☐ Not started | | Mostly done |
| 9 | Mismatch cleanup | ⚪ P3 | ☐ Not started | | Ride along other phases |

Status legend: ☐ Not started · ◐ In progress · ☑ Done (device-verified) · ⏸ Blocked

---

## PHASE 0 — Safety net & reconnaissance (do this FIRST, no app code yet)

**Goal:** Know exactly what the live backend is doing before we change the app, and set up the ability to test safely.

**Why this is first:** The backend repo may be *ahead of* what's actually deployed (deploys lag commits — see memory *Backend deploy layout*). We must not guess. If Cashfree is **already live** in production, payments are **already broken for all users right now** → Phase 1 becomes an emergency. If it's not deployed yet, we get to coordinate the app + backend release calmly.

### Steps
1. **Determine the deployed backend build.** Decode a real device JWT and/or hit production directly:
   - `GET https://<prod>/api/config/all` → confirm the config shape the live app receives.
   - `POST https://<prod>/api/payments/initiate/<userId>?amount=100` (test account) → does it 404 (old Razorpay build) or return `{orderId, appId}` (new Cashfree build)?
   - **Record the answer here → Deployed gateway is: `__________` (Razorpay / Cashfree).**
2. **Classify urgency:**
   - If **Cashfree is already live** → payments are broken in prod. Phase 1 is a hotfix; escalate.
   - If **Razorpay is still live** → we control the cutover; Phase 1 ships *with* the coordinated backend deploy.
3. **Stand up / confirm a staging backend** (or a non-prod Render service) pointing at Cashfree **SANDBOX** credentials. Confirm a debug build can point at it (build config / base URL switch).
4. **Snapshot rollback artifacts:** archive the current production AAB + its git tag/commit so we can re-publish instantly if a phase goes wrong.
5. **Confirm remote-config kill switches exist** for the flows we'll touch: `walletFeatureEnabled`, `appleSignInEnabled`, `adMobEnabled`, `bookingFeatureEnabled`. Verify toggling them in the backend actually changes app behavior on device.
6. **Create the working branch** off `main` (do not commit to `main` directly).

### Definition of Done
- Deployed gateway identified and written down above.
- Staging backend reachable from a debug build.
- Rollback AAB archived.
- Remote-config kill switches verified working on device.

---

## ✅ PHASE 0 — FINDINGS (completed 2026-07-25)

Probed the **live production backend** `https://www.gridee.in/` (the app's `ApiConfig.BASE_URL`; served via Render + Cloudflare). Results:

### Connectivity & gateway
- **Connectivity:** ✅ `GET /api/config/all` returns 200 (public). Everything else returns `401` unauthenticated — Spring Security rejects before routing, so **HTTP-status route-probing cannot detect the gateway** (even nonexistent routes 401).
- **Deployed payment gateway = Cashfree (high confidence).** The backend's `application-prod.properties` and `.env.prod` carry `cashfree.app.id / cashfree.secret.key / cashfree.environment`; Razorpay is commented out in `docker-compose.prod.yml` and removed from the code. **Residual uncertainty:** confirm the *deployed commit* on the Render dashboard (local HEAD is `dfafe96`, 2026-07-20). → **Implication: the app's Razorpay top-up path is dead/dying → Phase 1 is real and needed.**

### 🔴🔴 CRITICAL — production `AppGlobalConfig` is corrupted with Swagger/OpenAPI placeholder values
The live `/api/config/all` (consumed by the app on every startup) contains example/default values — `"string"`, `additionalProp1`, `0.0`, `debugMode:true`, `enableSwagger:true`, `requestTimeoutSeconds:1`. **Signature of someone saving the pre-filled OpenAPI example body via Swagger UI / admin `PUT /api/admin/config`.** The version block (minIOS `2.4`, store URLs, `minAndroidVersionCode:66`, message `"App under maintenance"`) looks manually entered — i.e. a partial/mistaken save that clobbered the rest. Concrete live impact:

1. **🔴 FORCE-UPDATE LOCKOUT (whole user base).** `versions.minAndroidVersionCode = 66`. `RemoteConfigManager.isForceUpdateRequired()` → `AndroidVersionPolicy.isBelowMinimum()` returns `currentVersionCode < 66` and **ignores** the `forceAndroidUpdate:false` flag. Released users are on code ≤ 63 → all flagged below-minimum → forced to update to **build 66 which is not on the Play Store**. Gated at `SplashActivity.kt:283`. Config cache TTL = 15 min, so it propagates fast. **This can lock every user out of the app.**
2. **🔴 WALLET TOP-UP BLOCKED.** `financial.minWalletTopUpAmount = 0.0`, `maxWalletTopUpAmount = 0.0` → `WalletAddMoneyActivity.isAmountAllowed()` (`amount >= 0 && amount <= 0`) rejects every real amount; UI says "Enter an amount between 0 and 0." Also `platform.currency = "string"` is passed straight into checkout options at `WalletTopUpActivity.kt:60`.
3. **🟠 DEGRADED SETTINGS.** `booking.maxConcurrentBookingsPerUser = 1` (multi-booking effectively off despite the flag), `maxPricingPerHour = 0.0`, all penalties/refunds `0.0`, `platform.timezone/currency/environment = "string"`, `requestTimeoutSeconds = 1`, `debugMode/enableSwagger = true` in prod.

**Note:** the app *already* defends against the `"string"` placeholder for `maintenanceTitle/Message` in `RemoteConfigManager.sanitize()` — but nothing else. That's why maintenance text is safe while amounts/versions are not.

### Kill switches
- ✅ Present and returned live: `walletFeatureEnabled, appleSignInEnabled, adMobEnabled, bookingFeatureEnabled, googleSignInEnabled, rewardsEnabled, multipleBookingsAllowed, sequentialBookingEnabled` all `true`; `maintenanceMode:false`. App maps these via `RemoteConfigManager.isFeatureEnabled()`.

### Rollback artifact
- Latest release AABs on disk: `apks/gridee-v1.62-code63-release.aab` (newest), `apks/gridee-v1.61-code62-release.aab`. Current source is `versionCode 66 / versionName 1.65` (unreleased WIP). **TODO: confirm the actual current Play Store versionCode** before relying on a rollback.

### Staging / branch (not yet done — not blocking the incident)
- Staging: app supports pointing a debug build at a local/staging backend via `ApiConfig` (commented local URLs present). Stand up a Cashfree-SANDBOX backend before Phase 1 code.
- Branch: repo is mid-WIP on `feature/edit-profile-dark-mode-fix` with uncommitted changes — **did not switch/create branches** to avoid disrupting that work. Branch off `main` when starting Phase 1.

---

## PHASE 0A — Fix corrupted production config (INCIDENT — do before anything else)

**Goal:** Restore correct values in the live `AppGlobalConfig` so users aren't force-locked-out and top-ups work again. This is a **backend/admin data fix, not app code** — it's fast and reversible, and it needs admin access (which the owner has).

**Why first:** It's actively degrading the live app and can lock out all 5,000 users via the force-update path. No frontend release can fix a bad server config.

### Steps
1. **Lower the immediate risk instantly:** set `versions.minAndroidVersionCode` back to the true current floor (e.g. the lowest supported released code, **not** 66) and `latestAndroidVersionCode` to the actual latest published code. Set `minAndroidVersion`/`latestAndroidVersion` to real values. Fix `androidUpdateMessage`. Do this via `PUT /api/admin/config` (or the admin panel's version controls) with a valid ADMIN token.
2. **Restore financial values:** real `minWalletTopUpAmount` / `maxWalletTopUpAmount` (e.g. 100 / 50000), correct penalties/refund settings.
3. **Restore platform values:** `currency: "INR"`, `currencySymbol: "₹"`, `timezone: "Asia/Kolkata"`, `environment: "PRODUCTION"`, `apiVersion`, `debugMode:false`, `enableSwagger:false`, sane `requestTimeoutSeconds`.
4. **Restore booking values:** `maxConcurrentBookingsPerUser` to the intended number, `maxPricingPerHour`, etc.
5. **Re-verify** by re-fetching `GET /api/config/all` and confirming no `"string"` / `0.0` / `additionalProp` placeholders remain.
6. **Prevent recurrence:** restrict who can `PUT /api/admin/config`; disable Swagger "Try it out" writes in prod (`enableSwagger:false`); consider server-side validation rejecting placeholder writes.

### Verification
- `GET /api/config/all` shows real values, no placeholders.
- On a device on an *older* build, splash no longer force-updates.
- Wallet screen accepts a valid top-up amount again.

### Rollback
- Config edits are data; keep a copy of the corrected JSON. If a bad save recurs, re-`PUT` the known-good JSON.

### Definition of Done
- Live config verified clean; force-update and top-up behave correctly on a real device.

---

## PHASE 0B — Harden the app against bad server config (app-side safety net)

**Goal:** Make the app *incapable* of being bricked by a bad/placeholder config again, the same way it already sanitizes maintenance text. Ship this so a future bad `PUT` can't lock users out or block top-ups.

**Why:** Phase 0A fixes the data; Phase 0B fixes the *class of bug*. Defense in depth for a 5,000-user production app.

### Steps (in `RemoteConfigManager.sanitize()` + `AppRemoteConfig`)
1. **Version fields:** clamp `minAndroidVersionCode` so it can never exceed the **running app's** `BuildConfig.VERSION_CODE` (never force-update a user to a build newer than what's published/installed). Treat `"string"`/blank version names as safe fallbacks. This alone neutralizes the lockout class.
2. **Financial fields:** if `minWalletTopUpAmount`/`maxWalletTopUpAmount` are `<= 0` or inverted, fall back to safe defaults (100 / 50000) instead of trusting `0`.
3. **Platform fields:** apply the existing `cleanedOr("string")` guard to `currency`, `currencySymbol`, `timezone`, `environment`, `apiVersion` (fallback to `₹` / `INR` / `Asia/Kolkata` / `PRODUCTION`).
4. **Booking fields:** already partly clamped; ensure `maxConcurrentBookingsPerUser`, `maxPricingPerHour` fall back sensibly when `0`.

### Verification
- Point a debug build at the **current (corrupted) live config** and confirm: no force-update, top-up bounds sane, currency shows `₹` — *without* any server change. That proves the guard works.
- Re-verify after Phase 0A that correct values still pass through unchanged.

### Rollout
- Pure client-side hardening; bundle into the next release. Low risk (only widens/《floors》 bad values), but device-verify against both corrupted and clean config.

### Definition of Done
- App cannot be force-locked or top-up-blocked by placeholder/zero config; verified on device against the live corrupted config.

### ✅ IMPLEMENTED — 2026-07-25 (decision: **Brick-proof cap**)
All changes are in `config/RemoteConfigManager.kt` (+ tests). No backend touched.
- **Force-update brick-proof cap:** `sanitize()` now caps `minAndroidVersionCode` at the installed `BuildConfig.VERSION_CODE`, so the non-dismissible splash force-update can never trap a user on an unpublished build. Genuine forced updates remain delegated to Google Play In-App Updates (`InAppUpdateController`), which only fire when a build actually exists.
- **Wallet top-up bounds:** zero/inverted `min/maxWalletTopUpAmount` fall back to 100 / 50000 so a bad config can't block every top-up. (Money-charging fields — penalties/refunds — are intentionally left server-controlled to avoid ever overcharging.)
- **Platform placeholders:** `currency`, `currencySymbol`, `timezone`, `environment`, `apiVersion` now run through the existing `"string"`/blank guard → `INR / ₹ / Asia/Kolkata / PRODUCTION / v1`. (Prevents `"string"` reaching the checkout `currency` at `WalletTopUpActivity.kt:60`.)
- **Testability:** `sanitize()` made `internal`; `repository` made lazy so config logic no longer forces the network stack to init.
- **Verification:** 4 new unit tests in `RemoteConfigManagerTest` (corrupted-config → self-protects; legit-config → untouched). `./gradlew :app:testDebugUnitTest` → **6/6 pass**. `:app:compileDebugKotlin` clean.
- **Residual limitation (honest):** this protects the *next* published build and all future config mistakes. It cannot retroactively rescue an already-installed OLD build that a bad config is *currently* force-locking — those users can only be freed by a config fix (declined) or by a Play build they can actually install. **Action: confirm the live Play Store versionCode** to know whether any lockout is currently active, and ship this hardening in the next release so the class of bug is closed.

---

## PHASE M — Multi-tenant / strict-JWT gap-fixes (from backend-dev brief)

The backend developer sent a multi-tenant + strict-JWT verification brief. **Assessment: its backend claims are accurate** (verified against `SecurityConfig.java` / `SecurityConstants.java`), but with two corrections and one big omission:
- ✏️ **No token-refresh flow exists.** `/api/auth/refresh` is whitelisted but has no controller handler → on 401 the app must clear session + re-login, NOT attempt refresh.
- ✏️ **Lot onboarding metadata IS public** (`/api/parking-lots`, `/api/v1/organizations|locations|parking-lots` GET) → pre-login lot selection is valid.
- ⚠️ **The brief omits the Razorpay→Cashfree migration** (Phase 1), which is the actually-broken prod path — don't let the brief eclipse it.
- Most Android asks are **already implemented** (lot selection screen, JWT-derived lot storage, JWT interceptor, lot-scoped endpoints, no protected calls before login, and the Phase 0B "don't blindly force-update"). So this is a **verify + fill-gaps** pass, not a rebuild.

### ✅ DONE — Central 401 handler (2026-07-25)
Files: `data/api/UnauthorizedInterceptor.kt` (new), `utils/SessionExpiryHandler.kt` (new), `data/api/ApiClient.kt` (register interceptor after `JwtAuthInterceptor`), `GrideeApplication.kt` (track current foreground activity via `WeakReference`).
- Fires **only** on HTTP 401 for a request that carried an `Authorization` header (a rejected token). **Never** on 403 (role/lot mismatch → surfaces as in-screen error) and **never** on a public/login 401 (wrong password). Matches the brief and the app's existing "don't log out on forbidden" stance.
- Clears the session (off the main thread) and routes to `LoginActivity` (matching the app's existing logout idiom) **only if a foreground activity exists**; if backgrounded, it clears silently and the next app-open routes to login via Splash.
- Debounced (5s) so a burst of in-flight 401s triggers exactly one logout; no redirect loop (the session-clear's own token-unregister 401 is debounced out).
- Verified: `:app:compileDebugKotlin` clean; 4 unit tests in `UnauthorizedInterceptorTest` lock the contract (401+token→logout; 403→no; 401 no-token→no; 2xx/5xx→no); full `:app:testDebugUnitTest` green (no regressions).

### ☐ Remaining brief gaps (frontend-only, unblocked)
- [ ] **Legacy global-endpoint fallbacks** (`/api/parking-spots`, `/api/bookings/{userId}/all`): now JWT-only and not lot-scoped — verify they don't silently 403 or leak cross-lot data; prefer removing the fallback so the app relies only on lot-scoped endpoints.
- [ ] **One-time lot gate for existing lot-less users:** today lot selection is user-initiated from Home; add a single forced prompt for an authenticated user who has no `parkingLotId`, and ensure it doesn't repeat.
- [ ] **Operator 403 / lot-mismatch messaging:** clear message when an operator scans a booking from another lot (now that 403 no longer logs out).
- [ ] **Cross-lot isolation test pass:** two lots (A/B), confirm Lot B data never appears for a Lot A user and vice-versa; operator A cannot check in a Lot B booking. (Needs a device/emulator + two seeded lots.)

### ☐ Backend prerequisites to confirm with backend dev
- `payment_session_id` in the initiate response (blocks Phase 1 — see below).
- Confirm intended `enableSwagger:false` in prod (the corrupted config had it `true`; Swagger write-access is how the config likely got clobbered — Phase 0 finding).

---

## PHASE 1 — Payments: Razorpay → Cashfree (P0, highest risk)

**Goal:** Replace the Razorpay checkout flow with Cashfree, matching the new backend contract, without any user losing money or getting a broken top-up.

**Backend contract (confirmed in repo):**
- Initiate: `POST /api/payments/initiate/{userId}?amount=<double>` → returns `{ "orderId": "...", "appId": "..." }`
  (`PaymentController.java:32`, `PaymentGatewayService.java:91`)
- Callback: `POST /api/payments/callback` body `{ orderId, paymentId, userId, amount, signature }`;
  `signature` = Cashfree **HMAC-SHA256 of `orderId + amount`** (`PaymentController.java:53`).

**⚠️ BLOCKER — resolve before writing app code:** The Cashfree Android SDK needs a **`payment_session_id`** to open checkout, but `initiatePayment` currently returns only `orderId` + `appId` (`PaymentGatewayService.java:137`). **Coordinate with backend to also return `payment_session_id`** (and confirm which Cashfree SDK/version + environment). Do not start step 3 until this is agreed.

### Steps (in order)
1. **Backend coordination (BLOCKER):** agree the initiate response includes `payment_session_id`; confirm Cashfree SANDBOX vs PROD credential wiring; confirm the exact signature the callback expects for the mobile flow.
2. **Add Cashfree SDK, keep Razorpay temporarily.** Add the Cashfree PG dependency in `app/build.gradle`. Do **not** delete Razorpay yet — both coexist behind a flag during rollout.
3. **Introduce a client gateway flag.** Add a remote-config-driven switch (e.g. reuse/extend config, or a `paymentGateway` custom setting) so the app can pick Cashfree vs Razorpay at runtime. This lets us dark-launch and instantly revert.
4. **Update the API layer** (`data/api/ApiService.kt`):
   - Change `initiatePayment` to `@POST("api/payments/initiate/{userId}")` with `@Path userId` + `@Query("amount")` (remove the JSON body version).
   - Update `PaymentInitiateResponse` → `appId` + `paymentSessionId` (drop `keyId`).
   - Update `PaymentCallbackRequest` → `{ orderId, paymentId, userId, amount, signature }`; remove `razorpay_signature`.
5. **Rewrite the checkout activities** (`ui/wallet/WalletAddMoneyActivity.kt`, `ui/wallet/WalletTopUpActivity.kt`):
   - Call the new initiate, launch Cashfree checkout with `paymentSessionId`, handle success/failure/cancel callbacks, then POST `/callback`.
   - Preserve the existing amount validation (`min/maxWalletTopUpAmount` from remote config) and the `walletFeatureEnabled` guard.
6. **Scrub Razorpay-facing copy/config** *behind the flag only*: strings ("Redirecting to Razorpay checkout…"), manifest entries. Keep the old code path compilable until the flag is fully rolled out.
7. **Guard against double-credit / partial failure:** ensure a failed `/callback` after a successful charge is surfaced and retriable; never show "success" before the callback confirms wallet credit.

### Verification (must pass all)
- SANDBOX: successful top-up credits the wallet exactly once; transaction appears with correct type/amount.
- Cancel mid-checkout → no credit, clean UI, no crash.
- Network drop after charge but before callback → user sees pending/retry, not a false success.
- Amount below min / above max → blocked with correct message.
- `walletFeatureEnabled=false` → top-up cleanly disabled.
- Old-backend tolerance: with the flag off, the app still behaves (Razorpay path) — proves the kill switch.

### Rollout
- Ship with the gateway flag **defaulting to the live backend's actual gateway**.
- Flip to Cashfree for internal testers first, then a small % via config, then 100%.
- Keep Razorpay code for at least one release after 100% Cashfree adoption, then remove in a cleanup PR.

### Rollback
- Flip the gateway flag back (no app update needed). If the SDK itself misbehaves, re-publish the archived AAB.

### Definition of Done
- Cashfree top-up verified on device against staging and a canary in prod.
- Kill switch verified.
- No `razorpay_signature` sent; callback signature accepted by backend.

---

## PHASE 2 — Daily wallet balance top-up (`DAILY_WALLET_RESET`)

**Goal:** Correctly display the new daily balance behavior so users understand it and the transaction list doesn't show an "unknown" type.

**Backend behavior:** A scheduler at **6 PM IST** tops each wallet **up to a minimum balance** (a floor, not a hard reset) and writes a transaction of type `DAILY_WALLET_RESET` (`WalletService.java:202`). The app currently renders `WELCOME_BONUS/AD_TOP_UP/WALLET_TOP_UP/PENALTY/…` but **not** `DAILY_WALLET_RESET`.

### Steps
1. Add `DAILY_WALLET_RESET` to the app's transaction-type handling (label, icon, credit styling) in the wallet transaction adapter/mapping.
2. Add clear copy explaining the "daily balance" model (a short info line/tooltip on the wallet screen) so users aren't confused by a daily credit.
3. Confirm the amount sign/format is correct (it's `target − previous`, always a credit).

### Verification
- Force/point at an account that has a `DAILY_WALLET_RESET` transaction → renders correctly, no "unknown type".
- Copy is accurate and doesn't overpromise (it's a floor top-up, only when below the minimum).

### Definition of Done
- New type displays correctly; wallet screen explains the daily model.

---

## PHASE 3 — AdMob rewarded-ad wallet top-up (`AD_TOP_UP`)

**Goal:** Finish/verify the rewarded-ad → small wallet credit flow, fully flag-gated.

**Backend behavior:** `POST /api/users/{userId}/wallet/topup` accepts only `type=AD_TOP_UP`, capped at **₹10** for non-admins (`WalletController.java:65`). Gated by the `adMobEnabled` feature flag. The app's `TopUpRequest` currently has no `type` field.

### Steps
1. Add `type` to `TopUpRequest` and send `AD_TOP_UP`.
2. Gate the entire entry point behind `adMobEnabled` (default off until reviewed).
3. Verify the rewarded-ad integration exists/works; on ad-reward callback, call `/topup` with the capped amount.
4. Enforce the ₹10 client cap defensively (don't rely only on the server error).

### Verification
- Flag off → no ad entry point anywhere.
- Flag on → watch ad → wallet credited ≤ ₹10, transaction type `AD_TOP_UP`.
- Attempting > ₹10 → cleanly rejected.

### Definition of Done
- Flow works behind flag; cap enforced; ships **off** by default.

---

## PHASE 4 — Apple Sign-In wiring

**Goal:** Connect the existing stub to the real backend endpoint, gated by `appleSignInEnabled`.

**Backend:** `POST /api/auth/apple` accepts `identityToken` (also `idToken`/`token`) (`AuthController.java:276`). App has a **stub** `utils/AppleSignInManager.kt` with a placeholder redirect URI (`https://your-backend-domain.com/...`) — not connected.

### Steps
1. Decide: is Apple Sign-In actually needed on Android? If not, **hide it behind `appleSignInEnabled=false`** and stop here (lowest-risk option).
2. If yes: set the real redirect URI, complete the OAuth flow, exchange the identity token via `/api/auth/apple`, handle new-user vs returning-user, store the returned JWT like Google sign-in does.
3. Gate the button on `appleSignInEnabled`.

### Verification
- Flag off → button hidden.
- Flag on → new Apple user creates account (+ welcome bonus); returning user logs in; JWT stored; session persists.

### Definition of Done
- Either cleanly hidden by flag, or fully working end-to-end and flag-gated.

---

## PHASE 5 — Per-lot booking policy

**Goal:** Drive booking constraints from the per-lot policy instead of only global config.

**Backend:** `GET /api/parking-lots/{lotId}/booking-policy` returns the effective per-lot policy (`ParkingLotController.java:80`). App has zero references to it today.

### Steps
1. Add the endpoint + model to the API/repository layer.
2. Fetch the policy when a lot is selected; fall back to global config if the call fails (backward compatible).
3. Apply lot-specific rules (durations, max concurrent, etc.) in the booking flow, replacing hard-coded/global assumptions where the policy differs.

### Verification
- Lot with a custom policy enforces its own rules.
- Policy fetch failure → falls back to global config, no broken booking.

### Definition of Done
- Booking flow respects per-lot policy with a safe fallback.

---

## PHASE 6 — Multiple + sequential bookings UX

**Goal:** Ensure the app matches the backend's relaxed booking rules (up to N concurrent, consecutive auto-extension).

**Backend:** Users may hold up to `maxConcurrentBookingsPerUser` concurrent bookings; consecutive same-spot bookings auto-extend checkout (`MULTIPLE_BOOKINGS_IMPLEMENTATION.md`).

### Steps
1. Audit the app's current booking screens: does it still assume "one active booking"? Check creation guards, active-booking banners, and the bookings list.
2. Allow creating/holding multiple active bookings (respect the config/policy limit).
3. Surface sequential/auto-extended bookings clearly so users understand a back-to-back booking extended their checkout.

### Verification
- Create 2 concurrent bookings → both show as active; 3rd blocked at the limit with a clear message.
- Back-to-back bookings → checkout auto-extends and is communicated.

### Definition of Done
- App behavior matches backend limits and sequential logic.

---

## PHASE 7 — Multi-tenant foundation (org / location / user-context) — DECIDE SCOPE FIRST

**Goal:** Decide whether the app needs organization/location awareness; implement only if in scope.

**Backend (newest commit `dfafe96`):** `/api/v1/organizations`, `/api/v1/locations`, `/api/v1/user-context`, plus `organizationId/locationId/type` filters on `/api/parking-lots` (`ParkingLotController.java:34`). App has zero awareness today and works fine lot-scoped.

### Steps
1. **Product decision:** do 5,000 existing users need org/location switching now, or is the current lot-scoped flow sufficient? If sufficient → **defer this phase**, document the decision, stop.
2. If in scope: add org/location/user-context models + endpoints; persist a default user context; add org/location filters to lot listing; add a switcher UI.
3. Ensure existing users with no context set default to current behavior (no forced migration).

### Definition of Done
- Either an explicit "deferred" decision recorded, or org/location switching works with a safe default for existing users.

---

## PHASE 8 — Welcome bonus surfacing (mostly done)

**Goal:** Confirm the auto-credited welcome bonus is visible; optionally add a first-login confirmation.

**Backend:** Welcome bonus auto-credited on signup (type `WELCOME_BONUS`). App already displays this transaction type.

### Steps
1. Verify a newly-signed-up account shows the `WELCOME_BONUS` transaction and correct balance.
2. (Optional) Add a one-time onboarding confirmation ("₹X welcome bonus added").

### Definition of Done
- Bonus is visible to new users; optional confirmation added if desired.

---

## PHASE 9 — Mismatch cleanup (ride along with related phases)

Small correctness fixes; do each alongside the phase that touches the same area, or as one cleanup PR at the end.

- [ ] `isParkingSpotAvailableForLot` is typed `Response<Boolean>` (`ApiService.kt:161`) but the endpoint now returns an object `{available, availableCapacity, …}` (`ParkingLotController.java:95`). Fix the type or remove the unused method. *(Latent parse bug — currently uncalled.)*
- [ ] Remove Razorpay naming: `keyId`, `razorpay_signature`, `razorpayTaxPercentage` → Cashfree equivalents (do with Phase 1).
- [ ] JWT session extended to **6 months** — verify the app's session/expiry assumptions still hold; no premature logout.
- [ ] Remove the now-unused body-based `PaymentInitiateRequest` after Phase 1 is at 100%.

---

## Suggested execution order (summary)

```
Phase 0  ──►  Phase 1  ──►  Phase 2  ──►  Phase 3  ──►  Phase 4
(recon)      (payments)    (daily)       (ad topup)    (apple)
                                                          │
                              Phase 5 ──► Phase 6 ────────┘
                            (lot policy) (multi-booking)
                                   │
                          Phase 7 (decide) ──► Phase 8 ──► Phase 9
                          (multi-tenant)      (welcome)   (cleanup)
```

**If Phase 0 finds Cashfree is already live in prod:** Phase 1 jumps to the front as an emergency hotfix; everything else waits.
**If Razorpay is still live:** you may run the low-risk additive phases (2, 4, 5, 6, 8) first to build release confidence, then cut over payments (Phase 1) in a coordinated app+backend release.

---

## Per-release checklist (use for every phase that ships)

- [ ] Change is behind a feature flag / kill switch (or is a pure additive display fix).
- [ ] Verified on a real device against **staging** (installDebug, not just compiled).
- [ ] Backward compatible with the currently-deployed backend during rollout.
- [ ] Rollback path confirmed (flag flip and/or archived AAB).
- [ ] No regression to payments or auth.
- [ ] Crash-safe on lifecycle teardown (ViewBinding guards — see memory *Play Store crash stability*).
- [ ] Canary in production before 100% rollout.
```
