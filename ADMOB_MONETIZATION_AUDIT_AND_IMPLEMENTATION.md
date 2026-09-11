# Gridee Android AdMob Monetization Audit and Implementation Plan

> Audit date: 2026-08-22  
> Application: Gridee Android  
> Purpose: diagnose the falling blended eCPM, protect policy compliance and user trust, and provide an implementation and experimentation plan for sustainable advertising revenue.

## Document status

This is the working source of truth for AdMob monetization changes in Gridee. Update the checkboxes, measurements, decisions, and results as work is completed.

The audit is based on:

- The AdMob “last 28 days versus previous 28 days” screenshot supplied during the audit.
- The Android application in `Gridee_Android/android-app`.
- The checked-in backend copy in `Gridee_Android/repo`.
- The live `https://www.gridee.in/app-ads.txt` file.
- Official Google Mobile Ads and AdMob documentation linked at the end of this document.

No implementation changes were made as part of the original audit.

## Executive summary

The advertising business is currently earning more money, even though the blended eCPM is lower.

The main diagnosis is **format-mix dilution**:

- Requests increased much faster than revenue.
- Approximately 80% of current impressions are low-eCPM native impressions.
- Rewarded and interstitial eCPMs remain much higher than native eCPM.
- The overall eCPM therefore fell because the average now contains many more native impressions.

The other major findings are:

1. The estimated show rate fell from roughly 78% to roughly 62%. Many matched ads appear to be loaded but never displayed.
2. Native inventory dominates volume but currently uses a narrow layout, a restrictive portrait media request, and an application context that may weaken Meta mediation.
3. The rewarded-wallet implementation is a severe unit-economics, abuse, and possible AdMob policy risk.
4. Impression-level revenue measurement is incomplete for rewarded and interstitial ads.
5. Meta and Unity are producing little meaningful auction pressure.
6. Debug/test traffic safeguards need improvement.
7. **The ad business is running at a negative gross margin.** Rewarded wallet credits cost roughly 50 times what the impressions earn. Measured against total ad revenue, the business is not making $41 per period; it is losing approximately $1,100. See "Ad business profit and loss."
8. **Two out of three sessions are completely unmonetized.** Coverage, not price, is the largest revenue lever available. See "Coverage: impressions per session."
9. **The prior-period baseline is not a valid comparison.** A 7.5K-impression period has no statistical claim on a 59K-impression period. See "Baseline validity."
10. **Direct-sold local inventory is absent from the strategy.** The application already contains custom-campaign infrastructure that can plausibly be sold well above the network rate. See "Strategy: direct-sold inventory and the house-ad waterfall."

The goal must not be “keep eCPM fixed.” eCPM is an auction price affected by user geography, advertiser demand, seasonality, consent, format, placement, retention, and competition. The correct goal is:

> Maximize sustainable ad revenue per session and per active user while protecting retention, policy compliance, application stability, and user trust.

### Reading order

This document was originally organised around the eCPM question. That question is now answered and closed: format-mix dilution explains it, and the answer implies no urgent work. Read the document in this order instead.

1. **Ad business profit and loss** — the reward benefit currently destroys more value than the entire ad stack creates. Nothing below it matters until this is contained.
2. **Coverage: impressions per session** — where the revenue actually is.
3. **Initiative sizing and priority order** — what to build, in what order, with expected value attached.
4. **Strategy: direct-sold inventory and the house-ad waterfall** — the medium-term business, as distinct from ad operations.
5. Everything else, as reference material for the work those four sections select.

## Screenshot baseline

### Overall comparison

| Metric | Current 28 days | Estimated previous 28 days | Change |
|---|---:|---:|---:|
| Estimated earnings | $41.43 | $15.75 | +163.09% |
| Requests | 100K | 9.6K | +931.19% |
| Impressions | 59K | 7.5K | +688.60% |
| Match rate | 94.54% | approximately 99.89% | -5.35 percentage points |
| Blended eCPM | $0.70 | $2.10 | -66.64% |
| Ads ARPU | $0.006 | effectively $0.000 at displayed precision | increased |
| Active users | 4.94K | not available | — |
| Sessions per active user | 12.1 | not available | — |
| Average session duration | 1 minute 19 seconds | not available | — |
| Ad exposure per session | 33.20% | not available | — |

The previous-period figures above are inferred from the current values and the absolute changes shown in the screenshot. Use exported AdMob data for exact analysis.

### Baseline validity

The prior period served 9.6K requests and 7.5K impressions. The current period served 100K and 59K. This is not a period-over-period comparison of a stable system; it is a comparison between a pre-rollout state and a launched state.

Consequences that must be stated explicitly, because the headline number is otherwise misleading:

- A 7.5K-impression sample carries wide confidence intervals and was almost certainly dominated by a single high-value format. Its $2.10 blended eCPM is closer to a rewarded-only eCPM than to a portfolio price.
- The match-rate decline from 99.89% to 94.54% is the expected consequence of requesting ten times the volume across placements with thinner demand. It is not a defect and needs no remediation.
- “Blended eCPM fell 66%” is an artefact of changing the denominator's composition. It is not a finding, and it should not appear on any dashboard or in any status report.

**Action:** remove blended application eCPM from all internal dashboards. Retain per-ad-unit and per-country eCPM as diagnostics only. Replace the headline metric with ad revenue per 1,000 sessions.

### Current network and format mix

| Source and format | Impressions | eCPM | Approximate revenue |
|---|---:|---:|---:|
| AdMob native advanced | 43.8K | $0.33 | $14.45 |
| AdMob rewarded | 9.63K | $2.38 | $22.92 |
| Meta native advanced | 3.2K | $0.16 | $0.51 |
| AdMob interstitial | 1.53K | $1.57 | $2.40 |
| Unity bidding interstitial | 375 | $1.27 | $0.48 |

Approximate grouped result:

- Native: 47K impressions, about $14.97 revenue, weighted eCPM about $0.32.
- Rewarded plus interstitial: 11.5K impressions, about $25.80 revenue, weighted eCPM about $2.24.

Native represents approximately 80% of all impressions but only around 36% of revenue. This explains most of the blended eCPM change.

### Current top ad units by revenue

| Ad unit | Format | Revenue |
|---|---|---:|
| `Rewarded_Wallet_Coins` | Rewarded | $23.32 |
| `android_native` | Native advanced | $13.04 |
| `Booking_Transition_Interstitial` | Interstitial | $3.16 |
| `Booking QR Native` | Native advanced | $1.91 |

## Ad business profit and loss

This section did not exist in the original audit. It is the most important section in the document.

### The reward benefit costs more than the inventory earns

Every rewarded impression triggers a ₹10 wallet credit. Every rewarded impression earns approximately ₹0.20 gross.

```text
rewarded impressions in period            = 9,630
gross rewarded revenue                    = $22.92
wallet credit issued per completed reward  = ₹10.00
wallet liability created                  = 9,630 * ₹10 = ₹96,300
wallet liability in USD at ₹84/USD         ≈ $1,146
```

Set against the whole ad stack, not just rewarded:

| Line | Amount |
|---|---:|
| Total gross ad revenue (all formats) | $41.43 |
| Wallet liability created by rewarded | −$1,146 |
| **Net contribution** | **≈ −$1,105** |

The application does not have a falling-eCPM problem. It has a negative-gross-margin problem that an eCPM chart happened to surface.

### Qualifications on the loss figure

These reduce the magnitude but do not change the sign. Measure each before finalising the number.

- **Claim rate.** Not every rewarded impression necessarily results in a completed view and a credited reward. Instrument the rewarded funnel (see the impression-level analytics section) and replace the 100% assumption with the measured rate.
- **Breakage.** Wallet balance becomes a real cost only when it is spent on parking. Some balance is never redeemed. Measure historical redemption rate on `AD_TOP_UP` credits and apply it.
- **Marginal cost of a redeemed credit.** A ₹10 credit spent on Gridee parking costs the business the margin on ₹10 of parking, not ₹10 of cash, if the inventory would otherwise have gone unsold. Model this properly with finance before settling on the true cost per reward.
- **Exchange rate.** ₹84/USD is illustrative. Use the actual rate.

Even under favourable assumptions on all four — say a 60% claim rate and 50% redemption at 60% marginal cost — the cost per rewarded impression remains several times the revenue it generates.

### The cost is unbounded, not just high

The audit's original framing was that the client controls the reward amount. Verification against the backend shows the amount is now capped, and that the more severe exposure lies elsewhere.

**Verified backend state** (`WalletController.java`):

- `assertSelfOrAdmin` is enforced, so a caller can only credit their own wallet.
- `MAX_CLIENT_AD_TOP_UP_AMOUNT = 10.0` caps a single call.
- The transaction type is constrained to `AD_TOP_UP`.

**What is still missing, and is the actual hole:**

- No server-side daily, weekly, or lifetime cap per user.
- No idempotency key, nonce, or replay protection.
- No proof that an advertisement was watched. The endpoint is a plain authenticated top-up; nothing ties it to an impression.
- No rewarded server-side verification anywhere in the backend.
- No wallet-specific rate limit. `RateLimitingFilter` applies a generic 200 requests per minute per IP.

```text
worst case for one authenticated user with a scripted client:
  200 requests/minute * ₹10 = ₹2,000/minute
                            = ₹120,000/hour
```

The per-call cap does not bound the loss. Repetition does. This is a live production money leak, and it should be treated as an incident rather than as a monetization backlog item.

### Required immediate containment

Ordered by how quickly it can ship.

- [ ] Disable the wallet-credit reward via remote kill switch, or reduce the credit to a value at or below measured net revenue per completed view.
- [ ] Add a wallet-specific rate limit far below the generic 200/minute.
- [ ] Add a server-side daily cap per authenticated user on `AD_TOP_UP`.
- [ ] Query production for existing abuse: users with anomalous `AD_TOP_UP` counts, and total `AD_TOP_UP` value issued to date versus total rewarded revenue earned to date.
- [ ] Only then proceed to the SSV and ledger design already specified in the rewarded section.

### Acceptance criteria

- Net contribution per rewarded impression is positive under measured claim and redemption rates.
- No authenticated user can exceed the server-side daily cap by any client-side means.
- Historical `AD_TOP_UP` issuance has been audited and any abuse quantified.

## Coverage: impressions per session

This section did not exist in the original audit. Coverage, not price, is the largest available revenue lever.

### Derived coverage figures

```text
sessions in period          = 4,940 active users * 12.1 sessions = 59,774
impressions in period       = 59,000
impressions per session     = 59,000 / 59,774 = 0.99
ad exposure per session     = 33.20%  (reported directly by AdMob)
sessions showing any ad     ≈ 59,774 * 0.332 = 19,845
impressions per monetized session ≈ 59,000 / 19,845 = 2.97
ad revenue per 1,000 sessions = $41.43 / 59,774 * 1,000 = $0.69
```

Two facts follow, and they reframe the priority order in the rest of this document:

1. **Approximately 67% of sessions serve no advertisement at all.**
2. **Ad revenue per 1,000 sessions is $0.69.** A comparable India-traffic utility application typically runs $1.50–$4.00. The gap is coverage, not price.

### Why refresh tuning cannot fix this

Average session duration is 79 seconds. The Home native refresh interval is 90 seconds (`NATIVE_AD_VISIBLE_REFRESH_MS`), and it counts visible time only.

The refresh therefore almost never fires in a typical session. Every Home session yields at most one native impression regardless of the interval chosen. Refresh tuning is effectively inert on this traffic and should be deprioritised accordingly, not merely sequenced later.

### Where the unmonetized sessions are

Before building new placements, establish which sessions currently serve nothing. Break sessions down by:

- Destination reached — Home only, Home plus Bookings, booking pass or QR, wallet, profile, support.
- Session duration bucket.
- Whether an ad-eligible placement was ever visible.
- Whether consent permitted a request.
- Whether a placement was visible but the request failed, was cancelled, or was destroyed before impression.

The show-rate work already specified in this document addresses the last case. The first case — sessions that never reach an ad-eligible surface at all — is not addressed anywhere in the original audit and is likely the larger bucket.

### Targets

These are directional engineering targets, to be revised once the breakdown above exists.

| Metric | Current | Near-term target | Notes |
|---|---:|---:|---|
| Impressions per session | 0.99 | 1.6 | Via coverage and show rate, not faster refresh |
| Ad exposure per session | 33.2% | 55% | New eligible surfaces on high-dwell screens |
| Ad revenue per 1,000 sessions | $0.69 | $1.50 | The headline metric; replaces blended eCPM |
| Impressions per monetized session | 2.97 | ≤ 3.5 | A ceiling, not a target — see the ad-load policy |

Coverage must expand by making more sessions eligible, never by increasing density inside sessions that are already monetized. The second approach trades retention for impressions and is explicitly out of policy.

### High-dwell surfaces worth evaluating

The booking pass and QR screens are where the user physically waits, which makes them the highest-quality dwell inventory in the application. A full-width rail already exists on the booking pass. Evaluate, with retention and funnel guardrails:

- The booking pass and QR screen, already partially built.
- The post-booking confirmation state.
- Wallet and transaction history, which are browse-oriented rather than task-critical.

Never on: payment entry, active check-in, deep-link intents, or first launch.

## Initiative sizing and priority order

This section did not exist in the original audit. Every item elsewhere in this document is a checkbox with no expected value attached, which makes the plan impossible to prioritise from. The estimates below are rough and should be refreshed once impression-level analytics land, but the ordering they produce is robust.

### Estimated impact

Baseline for comparison: $41.43 gross revenue and roughly −$1,105 net contribution per 28 days.

| Initiative | Mechanism | Est. gross impact | Est. effort | Priority |
|---|---|---:|---|---|
| Contain the reward benefit | Removes ≈$1,146 of liability | **+$1,100 net** | Days | **P0** |
| Rebuild Home native placement | Width, aspect ratio, and asset completeness | +$21 (+50%) est. | 1–2 weeks | **P1** |
| Expand coverage to high-dwell surfaces | 0.99 → 1.6 impressions/session | +$25 (+60%) | 2–4 weeks | **P1** |
| Register test devices, lock debug probe | Removes invalid-traffic risk | Protects all revenue | Days | **P1** |
| Paid-event listeners on all formats | Enables every decision below | Measurement only | Days | **P1** |
| Activity context for native loaders | Unblocks Meta native demand | +$1–3 | Days | P2 |
| Intent-aware interstitial preload | Show rate 62% → 78% | +$5 (+12%) | ~1 week | P2 |
| Direct-sold local campaigns | Contracted CPM above the ₹28 network rate | Unquantified; est. 4–18× on filled inventory | Quarter+ | **Parallel track** |
| Add a mediation network | Marginal auction pressure | +$1–4 | 1–2 weeks | P3 |
| eCPM floor experiments | Price optimisation | Unknown, likely small | 2+ weeks | P3 |
| Refresh interval tuning | Inert at 79s sessions | ≈$0 | — | **Do not do** |
| App-open ads | 0.43 sessions/user/day | Negative on retention | — | **Do not do** |
| Increase rewarded volume | +$13 revenue, +₹54,000 liability | **Net negative** | — | **Do not do until redesigned** |

### What this ordering contradicts

Stated plainly so the change is deliberate rather than accidental:

- The original "Recommended experiment order" leads with the native layout experiment. Correct as an *experiment*, but it must not precede reward containment, which is worth roughly fifty times more and is a live loss.
- The original phase plan treats show rate as P1 and coverage not at all. Show rate is worth about +12%; coverage is worth about +60%.
- Growing rewarded volume is an intuitive revenue move and is currently value-destroying. It is listed here explicitly so nobody proposes it.

### Sizing discipline going forward

Every future item added to this document must carry an estimated revenue impact, an estimated engineering cost, and the measurement that will confirm or refute the estimate. Items without all three do not enter the plan.

## Metric definitions and formulas

Use these definitions consistently in application dashboards and experiment reports.

### eCPM

```text
eCPM = estimated revenue / impressions * 1,000
```

eCPM is a price/yield metric. It is not the business outcome by itself.

### Match rate

```text
match rate = matched requests / total requests
```

A matched request means an ad was returned. It does not guarantee that the ad became visible.

### Show rate

```text
show rate = impressions / matched requests
```

Current estimate:

```text
matched requests = 100,000 * 94.54% = 94,540
show rate = 59,000 / 94,540 = 62.4%
```

Previous-period estimate:

```text
matched requests = 9,600 * 99.89% = 9,589
show rate = 7,500 / 9,589 = 78.2%
```

Approximately 35.5K current matched ads did not become recorded impressions. This is an estimate, not proof of one specific bug. Break it down by ad unit and format before drawing a final conclusion.

### Revenue per 1,000 sessions

```text
ad revenue per 1,000 sessions = ad revenue / sessions * 1,000
```

This should be a primary product KPI because it accounts for both price and the number of ads actually shown during use.

### Ads ARPDAU and ads ARPU

```text
ads ARPDAU = daily ad revenue / daily active users
ads ARPU = period ad revenue / active users in the same period
```

### Guardrail metrics

Every monetization experiment must also track:

- D1, D7, and D30 retention when enough data exists.
- Sessions per active user.
- Average session duration.
- Booking funnel completion.
- Rewarded opt-in and completion rates.
- Crash-free users and ANR rate.
- Ad-related support complaints.

## What is already implemented well

Preserve these behaviors unless an experiment proves a better approach:

- Google Mobile Ads SDK and User Messaging Platform are integrated.
- Consent is refreshed and ad requests are gated through `AdConsentManager`.
- Privacy options are exposed to users.
- Home and Booking QR native placements have distinct ad-unit IDs.
- Native ads are destroyed through lifecycle cleanup.
- MediaView/video assets are supported.
- Booking interstitials are placed at a natural transition rather than during typing or navigation.
- Home native refresh is visible-only and set to 90 seconds, above Google’s 60-second minimum.
- Native impression-level paid-event analytics have been started.
- The live `app-ads.txt` contains the Google publisher line.

## Codebase map

| Area | Location | Why it matters |
|---|---|---|
| Ad dependencies | `Gridee_Android/android-app/app/build.gradle` | GMA SDK, UMP, Meta and Unity versions |
| Central ad IDs/loading | `.../utils/AdMobManager.kt` | Rewarded/interstitial IDs, cache and test-device configuration |
| Consent | `.../utils/AdConsentManager.kt` | UMP refresh, request gating and privacy choices |
| Application preload | `.../GrideeApplication.kt` | Interstitial preloading on activity resume |
| Home native renderer | `.../ui/ads/AdMobNativeAdCardView.kt` | Native request, context and asset rendering |
| Booking QR renderer | `.../ui/ads/BookingQrNativeAdView.kt` | Booking native request and rendering |
| Home native lifecycle | `.../ui/fragments/HomeFragment.kt` | Native setup and 90-second refresh |
| Home native layout | `.../res/layout/view_admob_native_card.xml` | Media, CTA and typography sizes |
| Main container layout | `.../res/layout/activity_main_container.xml` | Floating native placement size/position |
| Booking interstitial show | `.../ui/fragments/BookingsFragmentNew.kt` | Natural booking-transition placement |
| Rewarded flow | `.../ui/bottomsheet/RewardBottomSheet.kt` | Client reward callback and wallet credit |
| Reward state | `.../utils/DailyRewardState.kt` | Current repeatability/daily-display state |
| Ad analytics | `.../utils/AdRevenueAnalytics.kt` | Native lifecycle and paid-event logging |
| Remote configuration | `.../data/model/AppRemoteConfig.kt` | Current global ad/reward switches |
| Debug ad probing | `.../src/debug/.../AdFormatProbeReceiver.kt` | Production-unit testing risk |
| Debug receivers | `.../src/debug/AndroidManifest.xml` | Exported debug components |
| Checked-in wallet API | `Gridee_Android/repo/.../WalletController.java` | Client-controlled amount risk |
| Checked-in wallet service | `Gridee_Android/repo/.../WalletService.java` | Wallet credit implementation |

Paths abbreviated with `...` are under `Gridee_Android/android-app/app/src/main/java/com/gridee/parking` unless the row specifies another root.

## P0: rewarded-wallet safety, economics, and policy

### Confirmed implementation concern

`RewardBottomSheet.kt` rewards through the client callback and calls a generic wallet top-up using an amount of `10.0`.

**Updated 2026-08-23.** `DailyRewardState.kt` previously documented itself as "an honest once-a-day attention nudge, not a hard lock", and the reward was genuinely unbounded per user per day. It now enforces `DAILY_REWARD_CAP = 3` completed rewards per local day, gated on a `rewardedDailyCap` remote switch. This is a client-side pacing and price control and **is not** the server-side cap required below — clearing app data resets it, and the endpoint remains unbounded to anything that is not the app.

**Correction to the original audit.** The original text stated that the backend accepts any positive amount from the request. Verification against `WalletController.java` shows this is no longer accurate: `assertSelfOrAdmin` is enforced, the transaction type is constrained to `AD_TOP_UP`, and `MAX_CLIENT_AD_TOP_UP_AMOUNT = 10.0` caps a single call.

The real exposure is that the call is **unbounded in repetition** rather than unbounded in amount, and that nothing ties the credit to a watched advertisement. The full analysis, the worst-case figure, and the containment steps are in "Ad business profit and loss," which supersedes this subsection.

The production backend must still be independently verified because the repository copy may not match production.

### Unit economics

At a $2.38 rewarded eCPM:

```text
revenue per rewarded impression = $2.38 / 1,000 = $0.00238
```

At an illustrative exchange rate of ₹84 per USD:

```text
gross ad revenue per completed impression ≈ ₹0.20
```

A ₹10 wallet reward would therefore be roughly 50 times the gross ad revenue before taxes, invalid traffic adjustments, fees, or incomplete views. Recalculate this using the actual exchange rate, completion rate, and net revenue before choosing any benefit value.

Extended to the period total, this ratio produces roughly ₹96,300 (≈$1,146) of wallet liability against $41.43 of total ad revenue across all formats. See "Ad business profit and loss" for the full statement, the qualifications that reduce its magnitude, and the containment sequence.

### Policy concern

AdMob prohibits direct monetary rewards. Indirect/nonmonetary rewards must comply with Google’s requirements, including being usable within the publisher’s platform and non-transferable. A wallet balance that pays for real-world parking may be cash-like or monetary. Treat this as a policy blocker until reviewed and confirmed compliant.

### Required design

- [ ] Pause or feature-flag the wallet-credit reward until policy and economics are approved.
- [ ] Replace cash-like credit with a clearly compliant, non-transferable in-app benefit if needed.
- [ ] Enable AdMob rewarded server-side verification.
- [ ] Generate an opaque authenticated user identifier for `userId`.
- [ ] Add a cryptographically random nonce or signed claim identifier to `customData`.
- [ ] Validate the SSV signature against Google’s published public keys.
- [ ] Reject stale timestamps.
- [ ] Verify the expected ad-unit ID, reward type, and server-owned reward amount.
- [ ] Deduplicate the AdMob `transaction_id` with a unique database constraint.
- [ ] Enforce daily/user/device cooldowns on the server.
- [ ] Never accept reward amount, wallet amount, entitlement, or final user identity from Android.
- [ ] Make the server ledger append-only and auditable.
- [ ] Define retry and reconciliation behavior for delayed SSV callbacks.
- [ ] Alert on duplicate attempts, abnormal claim rates, and user/device farms.

### Suggested reward transaction record

```text
ad_reward_transaction
  id
  admob_transaction_id UNIQUE
  authenticated_user_id
  ad_unit_id
  reward_type
  reward_amount_from_server_policy
  custom_data_hash
  callback_timestamp
  signature_key_id
  verification_status
  credited_at
  rejection_reason
  created_at
```

### Acceptance criteria

- No client request can choose the wallet or reward amount.
- Replaying the same callback cannot create a second reward.
- A user exceeding the server limit receives no additional entitlement.
- Invalid signatures, stale callbacks and unknown ad units are rejected.
- The reward is demonstrably compliant with current AdMob policy.
- The expected cost per reward is below the approved business threshold.

## P0: test traffic and invalid-traffic prevention

### Confirmed implementation concern

`AdMobManager.kt` currently has an empty `TEST_DEVICE_IDS` list. The debug ad-format probe uses production ad-unit IDs. Developer clicks or repeated production requests can create invalid-traffic risk.

### Required changes

- [ ] Register every developer, tester and CI/device-lab device as a GMA test device.
- [ ] Use Google test ad units for generic UI development where possible.
- [ ] Enable Meta and Unity test mode for mediated validation.
- [ ] Prevent debug builds from requesting production units unless the device is explicitly registered.
- [ ] Make debug receivers non-exported or remove them from builds shared outside the team.
- [ ] Add a visible debug-only indicator showing test mode and response source.
- [ ] Use Ad Inspector for in-context and single-ad-source tests.
- [ ] Document that team members must never click live advertisements.
- [ ] Review the AdMob Policy Center and invalid-traffic adjustments weekly.

### Acceptance criteria

- Every internal device returns a visible test-ad label.
- Production ad-unit probing cannot be remotely triggered from another application.
- Each mediated source passes an Ad Inspector single-source test.
- Release builds contain no debug receiver or ad-probe entry point.

## P1: native placement redesign

### Confirmed findings

- Home native ads are the dominant impression source.
- The request prefers portrait media (`NATIVE_MEDIA_ASPECT_RATIO_PORTRAIT`), which narrows the creative pool.

**Measured layout values.** The original audit described the placement qualitatively. The actual measurements are worse than that description implies, and they move this from an optimisation to a compliance concern.

| Element | Current value | Requirement or guidance | Status |
|---|---:|---|---|
| Card frame (`activity_main_container.xml`) | 120dp × 213dp | — | Narrow floating card |
| MediaView (`view_admob_native_card.xml`) | 120dp × 213dp | ≥ 120×120dp for video | Meets minimum, **zero margin on width** |
| Headline text | 11sp | 14–16sp readable | Below |
| Advertiser text | 8sp | 14–16sp readable | **Well below** |
| Attribution / "Ad" label | 9sp | Must be clearly legible | **Below** |
| Call to action | 28dp high | ≥ 48dp touch target | **Below minimum** |

**Correction.** An earlier revision of this table recorded the MediaView as 120×92dp and concluded it failed the video minimum. That was a misreading: the 92dp element in `view_admob_native_card.xml` is the bottom scrim gradient, and the MediaView is `match_parent` in both axes, so it fills the full 120×213dp card. Video demand is **not** excluded by size. The width sits exactly on the 120dp floor with no margin across densities, font scales, and display-size settings, which is a robustness concern rather than a present violation.

Consequences that do hold:

- 8sp and 9sp text is below accessibility minimums and below native-policy legibility guidance. The attribution label in particular must be clearly legible.
- A 28dp call to action inside a floating card sitting over the main content area is an accidental-click hazard as well as an engagement problem.
- The card is 120dp wide on a ~390dp viewport. Roughly 31% of available width is the dominant constraint on what a creative can express and therefore on what advertisers will bid.
- `NATIVE_MEDIA_ASPECT_RATIO_PORTRAIT` restricts the eligible creative pool to portrait media only.

### Unbound native assets

`AdMobNativeAdCardView.bind()` assigns only `headlineView`, `advertiserView`, `callToActionView`, and `mediaView`. The following `NativeAdView` asset slots are never populated:

```text
iconView        bodyView        starRatingView
priceView       storeView
```

Several demand sources weight asset completeness when bidding, and some creatives require an icon or body slot to serve at all. This is a demand-eligibility gap independent of layout size, and it is cheap to close.

A $0.33 eCPM is the expected market price for a 120dp-wide portrait unit with an incomplete asset set. This is a placement problem, not an auction problem, and no amount of mediation or floor work will address it.

### Proposed Home native variant

Build a new full-width, inline placement in the normal Home content flow:

- Use a distinct ad-unit ID for the new layout and experiment.
- Use a 16:9 or flexible media region.
- Keep MediaView comfortably above the minimum size; target at least 128dp in both dimensions where relevant.
- Prefer landscape or “any” media orientation to increase eligible demand.
- Render headline, advertiser, body, icon, CTA, star rating and price/store assets when present.
- Use readable 14–16sp supporting text and a CTA at least 48dp high.
- Keep “Ad” or “Sponsored” visible.
- Keep AdChoices visible and unobstructed.
- Separate the ad from navigation, parking actions, QR controls and other tappable UI.
- Do not make the whole card accidentally clickable outside registered native assets.
- Enable both image and video demand in the AdMob UI.
- Preserve loading placeholders without causing layout jumps.
- Destroy the old NativeAd before attaching a replacement.

### Lifecycle and refresh requirements

- [ ] Request only while Home is the resumed, foreground, actually visible destination.
- [ ] Do not request just because a hidden fragment was created.
- [ ] Cancel/destroy pending or loaded ads when the view is destroyed.
- [ ] Keep the initial 90-second refresh for the control variant.
- [ ] Test 180–300 seconds only as a separate experiment.
- [ ] Never refresh faster than 60 seconds.
- [ ] Do not refresh while the application is backgrounded or the placement is obscured.
- [ ] Record the lifetime from load to impression or destruction.

Because average session duration is 79 seconds, most sessions will not reach a 90-second refresh. Shortening refresh is therefore unlikely to be the main eCPM solution and can worsen quality or policy risk.

### Native experiment definition

| Cohort | Layout | Media orientation | Refresh |
|---|---|---|---:|
| Control | Existing floating portrait card | Portrait | 90 seconds |
| Treatment | Full-width inline card | Landscape/any | 90 seconds |

Primary outcome: native revenue per 1,000 eligible Home sessions.

Secondary outcomes:

- Native eCPM by source and country.
- Match rate and show rate.
- Click-through rate as a diagnostic, not a target to manipulate.
- Home exit rate.
- Booking-start and booking-completion rates.
- D1/D7 retention.
- Native validator warnings.

### Acceptance criteria

- Native Validator reports no layout or asset-policy problems.
- The layout passes small phones, tablets, landscape, large font and display scaling.
- AdChoices and attribution remain visible in every state.
- Treatment improves revenue per eligible Home session without material retention or funnel regression.

## P1: Activity context for mediated native ads

### Confirmed finding

Both native renderers build `AdLoader` with `context.applicationContext`. Meta documents an adapter failure when an Activity context is required.

### Required change

- [ ] Resolve the hosting Activity from the view context.
- [ ] Use the Activity to build/load native ads.
- [ ] Do not retain the Activity beyond the view/fragment lifecycle.
- [ ] Skip and log the request if no valid, non-finishing Activity is available.
- [ ] Verify Meta native demand through Ad Inspector after the change.

### Acceptance criteria

- No Meta adapter “Activity context” failures appear in logs or Ad Inspector.
- No Activity leak is reported by memory tooling.
- Meta native eligible requests, bids and impressions can be measured before and after rollout.

## P1: improve show rate and request quality

### Diagnosis

The estimated overall show rate is approximately 62.4%, down from roughly 78.2%. High match rate with lower show rate often means the application loads ads too early, loads for a placement the user never reaches, replaces ads before impression, or leaves the screen before showing.

The current application preloads an interstitial on `MainContainerActivity` resume. The ad is shown only on a booking-status transition, so users without an imminent transition may generate a matched request that never becomes an impression.

### Required changes

- [x] Preload booking interstitial only for users with an active/pending booking or a likely upcoming transition. **Shipped 2026-08-23.** The unconditional `preloadInterstitial` on every `MainContainerActivity` resume is gone. `GrideeApplication` now calls `AdMobManager.notifyHostResumed`, which services a queued transition but never requests. Requests come from `BookingsFragmentNew.syncTransitionAdWarmUp`, driven by the same `hasWatchableBooking()` predicate the transition watch already used, plus any transition still owed an ad.
- [x] Keep a warm cache to avoid latency when a transition is likely. **Shipped 2026-08-23.** A two-deep buffer via the GMA `InterstitialAdPreloader` (present in the bundled 25.4.0), consulted before the single-slot cache at show time. Two slots because a session can legitimately owe two ads back to back — a check-out on one booking and a cancellation on another.
- [x] Add a cache timestamp and discard full-screen ads before the one-hour expiration limit. Already present as `INTERSTITIAL_MAX_AGE_MS` (55 minutes); the discard is now counted as `cache_expired`.
- [ ] Do not wait five seconds during a critical transition for a cold load. **Deliberately unchanged.** `renderBookings()` is held while a transition is owed an ad, so widening or narrowing this window changes how long the user sees their pre-transition state. The 5-second cap stays; the warm buffer is what stops it being exercised.
- [x] If no warm ad is available, continue the user flow immediately or use a very short approved threshold. Every terminal path calls back, and the bounded 5-second window is the threshold.
- [ ] Request native ads only when their host placement is visible.
- [ ] Do not request a replacement while a valid ad is still visible.
- [x] Log why every loaded-but-unshown ad was destroyed or expired. **Shipped 2026-08-23, booking interstitial only.** `booking_interstitial_discarded` carries `discard_reason` and the transition it was owed to. Reasons emitted: `host_activity_gone`, `no_fill_within_window`, `queue_busy`, `superseded`, `ads_not_permitted`, `flag_off`, `consent_denied`, `cache_expired`, `show_failed`, `show_threw`.

Suggested destruction reasons:

```text
screen_left
view_destroyed
app_backgrounded
replaced_by_refresh
expired
consent_changed
feature_disabled
load_succeeded_after_request_cancelled
unknown
```

### Acceptance criteria

- Show rate is available separately for every ad unit and format.
- Loaded-but-unshown reasons account for at least 95% of discarded loaded ads.
- Overall show rate trends toward 75–80% without delaying booking actions.
- Revenue per 1,000 sessions improves; a higher show rate alone is not sufficient.

## P1: complete impression-level revenue analytics

### Confirmed finding

`AdRevenueAnalytics.kt` records native load/impression/click/paid events, but rewarded and interstitial placements do not have equivalent `OnPaidEventListener` coverage.

**Update 2026-08-23.** The booking-transition interstitial now has full coverage: `booking_interstitial_load`, `_preloaded`, `_impression`, `_click`, `_paid` and `_discarded`. Impression and paid events carry a `fill_path` of `preload_buffer` or `just_in_time`, so the warm buffer can be measured against the cold path directly. **Rewarded still has none** — it remains the last format with no paid-event listener.

### Required event coverage

Implement one common event pipeline for native, interstitial and rewarded formats.

Suggested fields:

```text
event_name
event_timestamp
anonymous_user_id
session_id
placement_id
ad_unit_id
ad_format
ad_source_name
ad_source_id
adapter_class_name
mediation_group_or_experiment
response_id
revenue_micros
currency_code
revenue_precision
load_latency_ms
request_to_impression_ms
cache_age_ms
error_domain
error_code
error_message_sanitized
consent_can_request_ads
privacy_options_required
country
app_version
android_version
device_class
experiment_cohort
discard_reason
```

Required lifecycle events:

```text
ad_eligible
ad_request_started
ad_loaded
ad_load_failed
ad_show_requested
ad_show_failed
ad_impression
ad_paid
ad_clicked
ad_dismissed
ad_reward_earned_client
ad_reward_verified_server
ad_destroyed_without_impression
```

### Data-quality rules

- Send paid-event data immediately; do not wait for a later analytics batch that may never run.
- Revenue precision must be retained.
- Never mix currencies without converting through a documented daily exchange-rate table.
- Deduplicate events using a stable event ID.
- Do not send PII in analytics or SSV custom data.
- Validate that summed ILRD approximately reconciles with AdMob reporting; small delays/differences are expected.

### Required dashboards

Create these views for 1-day, 7-day and 28-day windows:

1. Revenue, requests, matched requests, impressions, match rate, show rate and eCPM by ad unit.
2. The same metrics by country, source, format and application version.
3. Revenue per session and revenue per active user by experiment cohort.
4. Request-to-load and load-to-impression latency percentiles.
5. Loaded-without-impression counts grouped by discard reason.
6. Rewarded funnel: eligible → offer viewed → requested → loaded → shown → impression → completed → SSV verified → credited.
7. Booking funnel with and without an interstitial exposure.

### Acceptance criteria

- All production ad formats emit paid events.
- Ad source and response ID are available for debugging.
- Product decisions can be made using revenue/session and retention, not only AdMob’s blended eCPM.
- Application totals can be reconciled against the AdMob report within an agreed tolerance.

## P1: mediation, adapters, and auction competition

### Current dependencies observed during the audit

- Google Mobile Ads SDK: `25.4.0`.
- Meta adapter: `6.22.0.0`.
- Unity Ads SDK: `4.19.0`.
- Unity adapter: `4.19.0.0`.
- User Messaging Platform: `4.0.0`.

Dependency versions become stale. Check Google’s mediation guides and release notes immediately before upgrading. At audit time, the Unity documentation included a `4.19.0.1` patch for Unity SDK `4.19.0`; use the latest mutually certified pair rather than independently choosing versions.

### Required mediation review

- [ ] Upgrade Unity to the latest compatible, Google-certified SDK/adapter pair.
- [ ] Confirm Meta mapping for native, interstitial and rewarded placements where intended.
- [ ] Confirm Unity mapping for each supported format.
- [ ] Verify application IDs, placement IDs, credentials and bidding agreements.
- [ ] Verify that mediation groups include the intended countries and formats.
- [ ] Check adapter initialization status at application startup.
- [ ] Run an Ad Inspector single-source test for every network/format combination.
- [ ] Check bid participation, bid rate, win rate, impressions and revenue—not impressions alone.
- [ ] Investigate why Meta and Unity currently provide little volume.
- [ ] Add at most one new network per experiment.

Potential networks must be selected from Google’s current supported-network list and evaluated against top user countries. For India-heavy traffic, candidates may include InMobi, AppLovin, Liftoff/VMAP, Mintegral or ironSource, but this is not a recommendation to install all of them. Compare format support, bidding availability, SDK size, privacy requirements, payment terms and observed incremental revenue.

### New-network acceptance criteria

- It adds incremental revenue rather than only replacing AdMob wins.
- It passes privacy/consent review.
- It passes single-source testing.
- SDK size, startup, crash, ANR and memory effects are acceptable.
- It improves revenue/session in a controlled cohort.

## Demand ceiling by geography

This section did not exist in the original audit. It exists to prevent a quarter being spent chasing a price that is not available.

The console runbook asks whether traffic expanded into countries with lower advertiser demand. That is the right question, but the plan must also state the answer's implication, which is a hard ceiling.

For India-dominant traffic:

- Native inventory realistically prices at roughly **$0.30–0.70** eCPM with a good placement. The current $0.33 sits at the bottom of that band because of the layout, and a well-built full-width unit should reach the upper half of it.
- Rewarded and interstitial price meaningfully higher, which is why format mix matters more than network count.
- **Adding mediation networks will not produce a $2 native eCPM.** The demand does not exist at that price for this geography.

Planning consequences:

- [ ] Confirm the actual country mix from exported AdMob data before accepting the assumption above.
- [ ] Set per-country eCPM expectations explicitly so that hitting the ceiling is recognised as success rather than treated as an unsolved problem.
- [ ] Treat network additions as marginal auction pressure worth low single-digit dollars, not as a strategy.
- [ ] Where higher-value geographies exist in the mix, segment them and report them separately rather than letting them hide inside a blended average.

The route to materially higher revenue per impression is not a better network stack. It is direct-sold inventory, covered in the next section.

## P1: `app-ads.txt` and seller authorization

### Current state

The live file contains:

```text
google.com, pub-5268197817154713, DIRECT, f08c47fec0942fa0
```

This is the Google seller line. The file did not contain Meta or Unity entries at audit time.

### Required actions

- [ ] Confirm that the Google Play developer website URL resolves to `www.gridee.in` or the exact domain hosting the file.
- [ ] Confirm that AdMob reports the application as authorized after crawling.
- [ ] Obtain the exact authorized seller records from Meta and Unity dashboards/documentation.
- [ ] Add those exact lines; never guess publisher/account IDs.
- [ ] Add entries for every future mediation partner.
- [ ] Validate syntax and remove obsolete or unauthorized sellers.
- [ ] Recheck after website/developer-profile changes.

### Acceptance criteria

- AdMob reports authorized seller status.
- Every active demand partner’s required seller line is present and valid.
- No line references an account not controlled by Gridee.

## P1: privacy and consent

### Current strengths

`AdConsentManager` refreshes consent information, gates requests and exposes privacy options.

### Required verification

- [ ] Request consent-information updates on every application process start as required by UMP guidance.
- [ ] Request ads only after `canRequestAds()` is true.
- [ ] Verify the consent form and privacy-options entry point on EEA/UK test geography.
- [ ] Add Meta, Unity and all other partners to the relevant EEA/UK and US-state privacy configurations.
- [ ] Verify Google Additional Consent behavior required by Meta.
- [ ] Update the Play Data Safety form and privacy policy when SDKs or data collection change.
- [ ] Verify age treatment and under-age-of-consent configuration for the actual audience.
- [ ] Ensure analytics and SSV custom data contain no PII.

### Acceptance criteria

- EEA/UK users see the expected consent flow.
- Privacy options are reachable after the initial decision.
- No ad request occurs before consent eligibility is known.
- Each mediated partner receives the consent signals required by its official integration guide.

## P1: remote configuration and emergency controls

### Current state

The model currently has broad `adMob` and `rewards` booleans. Add typed controls rather than relying only on an unvalidated generic settings map.

**Update 2026-08-23.** Two of the recommended fields now exist as typed flags on `RemoteFeatureFlags`, both defaulting to true:

- `bookingTransitionInterstitialEnabled` — pulls the booking-transition placement on its own, without taking the natives down with it. Gates on top of `adMobEnabled`, never instead of it. Enforced at queue time and at show time, and both paths still fire the terminal callback so a disabled placement lets the transition through exactly as a no-fill does.
- `bookingTransitionPreloadBufferEnabled` — pulls the warm preload buffer alone. The placement keeps serving; it falls back to the just-in-time load that predated the buffer. This is the surgical control for `InterstitialAdPreloader` being a new SDK surface in a live app.

Both are operable **with no backend deploy**: `RemoteConfigManager.featureToggleOverride` consults the free-form `featureToggleMap` before the typed flags, and key normalisation strips case, dashes, underscores and a trailing `Enabled`. Setting `bookingTransitionInterstitialEnabled: false` in that map is sufficient.

The remaining fields below are still outstanding.

### Recommended fields

```text
ads_enabled
home_native_enabled
booking_qr_native_enabled
booking_transition_interstitial_enabled
rewarded_enabled

home_native_refresh_seconds
home_native_min_session_number
native_layout_variant

interstitial_preload_policy
interstitial_cooldown_seconds
interstitial_max_per_session
interstitial_max_per_day

rewarded_daily_limit
rewarded_cooldown_seconds
rewarded_benefit_variant

app_open_enabled
app_open_min_completed_sessions
app_open_cooldown_seconds

ad_experiment_id
ad_experiment_allocation_percent
```

### Safety requirements

- Use conservative local defaults.
- Validate bounds before applying remote numbers.
- Cache the last known valid configuration.
- Log the effective configuration with the experiment cohort.
- Include kill switches for every placement.
- Do not let Remote Config choose a wallet amount accepted directly by the backend.

## Strategy: direct-sold inventory and the house-ad waterfall

This section did not exist in the original audit, which treated monetization as an ad-operations problem. It is the largest strategic omission in the document.

### The asset the network auction cannot price

Gridee knows, at impression time, that a specific user has just parked at a specific location, at a known time of day, with a known expected dwell duration, and is physically standing near that location with the application open.

AdMob currently pays **$0.33 per thousand native impressions, or roughly ₹28 CPM** at ₹84/USD.

A local advertiser — the mall's food court, a nearby service centre, a retailer in the same complex — is buying something different from display reach. For them this is footfall acquisition against a known location with a measurable conversion window, and that is normally priced well above remnant display.

**This gap is a hypothesis, not a measured figure, and the document should not pretend otherwise.** Comparable hyper-local direct inventory commonly transacts somewhere in the ₹100–500 CPM range, which would be roughly **4× to 18×** the current native rate. The honest position is that the multiple is unknown until Gridee sells one campaign and observes the price a real advertiser accepts.

What makes the direction defensible even without that number:

- The ceiling on network native for this geography is roughly $0.70 CPM (≈₹59), established in "Demand ceiling by geography." Direct pricing has no equivalent ceiling because it is negotiated against the advertiser's conversion value, not an auction clearing price.
- Direct revenue carries no revenue share to a network.
- The same impression can be sold direct or filled by network, so direct fill is strictly incremental to the extent it clears above the network rate.

The first campaign sold settles the multiple with real data. Until then, treat this section as the highest-expected-value hypothesis in the document rather than as a quantified initiative.

### The infrastructure already exists

This is not a greenfield proposal. The application already contains:

| Component | Location |
|---|---|
| Custom ad model | `.../data/model/CustomAd.kt` |
| Custom ad renderer | `.../ui/ads/CustomAdBannerView.kt` |
| Campaign policy for booking QR | `.../ui/ads/BookingQrCampaignPolicy.kt` |
| Event deck adapter | `.../ui/ads/EventDeckAdapter.kt` |
| Campaigns API | Backend campaigns endpoints |

The booking pass rail already renders AdMob as one page with campaigns behind it. The correct model inverts that precedence.

### The waterfall model

Direct-sold inventory fills first. The network fills what direct sales cannot.

```text
1. Direct-sold campaign  — location- and time-targeted, contracted CPM
2. House promotion       — Gridee's own offers, retention, feature adoption
3. AdMob / mediation     — remnant fill at market rate
```

AdMob's role changes from "the monetization strategy" to "the price floor under unsold inventory," which is what it should be for any publisher with a differentiated audience.

### What this requires that does not exist yet

- [ ] Campaign targeting by parking location, location cluster, and time of day.
- [ ] Contracted CPM or flat-rate pricing per campaign, with an inventory forecast so slots can be sold before they are served.
- [ ] Impression and click reporting an advertiser will accept as an invoice basis.
- [ ] Waterfall precedence logic: attempt direct, fall through to house, fall through to network, with no blank state at any step.
- [ ] Frequency capping across direct and network so total ad load stays inside the policy defined in "Product-side monetization levers."
- [ ] A sales motion. This is the genuinely hard part and it is not an engineering problem — it needs an owner outside the application team.

### Sequencing

Run this as a **parallel track with its own owner**, not as a phase in the AdMob plan. It operates on a quarter-plus horizon while the P0 and P1 work operates on a weeks horizon, and interleaving them will starve one or both.

A reasonable first proof: one location, one advertiser, one campaign, manually trafficked, measured against what AdMob would have earned on the same impressions. That comparison produces the real multiple and replaces the estimate above. The bar for continuing is simple — the campaign must clear meaningfully above ₹28 CPM on the inventory it displaces.

### Acceptance criteria

- Direct-sold revenue per impression is reported separately from network revenue per impression.
- The waterfall never renders a blank placement when direct and house inventory are unavailable.
- Total ad load remains within the ad-load policy regardless of how the waterfall fills.
- The one-location proof produces a defensible CPM comparison against network fill on identical inventory.

## P2: AdMob console optimization runbook

### Report breakdowns

Before changing floors or adding SDKs, export at least 28 days and break down:

- Ad unit.
- Format.
- Country.
- Ad source.
- Mediation group.
- Application version.
- Platform/OS version when available.
- Date.

Questions to answer:

1. Did eCPM fall within the same ad unit and country, or only in the blended total?
2. Did traffic expand into countries with lower advertiser demand?
3. Did a new application version change requests, show rate or format mix?
4. Did consented/non-personalized traffic mix change?
5. Did one demand source stop bidding or lose mappings?
6. Is the fall weekday/weekend or seasonal?
7. Did policy, serving restrictions or invalid-traffic adjustments appear?

### Blocking controls

- Review blocked categories, advertiser URLs and sensitive categories.
- Keep blocks that are necessary for safety, law, brand or product requirements.
- Avoid broad revenue-motivated blocking because fewer eligible advertisers usually means less auction competition.
- Record the date and reason for every blocking change.

### eCPM floors

Do not use a high global manual floor simply to make the reported eCPM appear stable. A floor can raise the eCPM of served impressions while reducing match rate, impressions and total revenue.

For the high-volume native unit:

- [ ] Use a separate mediation A/B experiment.
- [ ] Test Google-optimized “All prices” against the most appropriate optimized floor variant.
- [ ] Use country-specific treatment only when traffic is sufficient.
- [ ] Change no other mediation variable during the test.
- [ ] Run for at least two weeks and until the console reports adequate confidence.
- [ ] Decide using total revenue, revenue/session, retention and show rate.

The native unit exceeds Google’s 10K-request threshold for mediation A/B testing. Other low-volume units may need more time.

### High-engagement ads

Review high-engagement-ad settings for interstitial and rewarded inventory. Longer creatives may improve auction value but can harm user experience. Test only with retention and booking-funnel guardrails.

## P2: app-open ad decision

Do not prioritize app-open ads now.

The screenshot shows 12.1 sessions per active user over 28 days, or about 0.43 sessions per active user per day. Google notes that app-open ads work best for applications opened frequently, such as more than once every four hours.

If app-open ads are tested later:

- Show only to returning users after several successful sessions.
- Show only on a real loading/foreground transition.
- Never interrupt first launch, consent, booking, payment or deep-link intent.
- Never show immediately before or after another full-screen ad.
- Use a conservative cooldown such as once per day initially.
- Add a remote kill switch.
- Measure churn, session abandonment and booking conversion.

## Product-side monetization levers

This section did not exist in the original audit, which scoped monetization to advertising only. Advertising is one lever among several, and treating it as the whole strategy produces the pressure to over-monetize that the guardrails elsewhere in this document exist to resist.

### Ad-free as a paid or earned state

- [ ] Evaluate removing advertising for users above a defined value threshold — a subscription tier, or organically for high-frequency paying users.
- [ ] Model the trade directly: a user generating $0.006 in ad ARPU per period is trivially outbid by almost any direct payment. If ads suppress conversion or retention even slightly among paying users, showing them ads is value-destroying.
- [ ] Segment ad exposure reporting by user value so this trade can be measured rather than assumed.

This is also the honest answer to the rewarded-ads problem. If the objective is to give users wallet value, selling wallet value is a better business than buying it from advertisers at a fifty-fold loss.

### Ad-load policy

Adopt an explicit ceiling as product policy, so that coverage growth cannot silently become density growth.

```text
maximum full-screen advertisements per session      = 1
minimum interval between full-screen advertisements = one full session
maximum total impressions per monetized session     = 3.5 (rolling average)
surfaces permanently excluded                       = payment entry,
                                                      active check-in,
                                                      deep-link intents,
                                                      first launch,
                                                      consent flow
```

- [ ] Encode these as remote-configurable bounds with conservative local defaults.
- [ ] Enforce them across direct, house, and network inventory jointly rather than per source.
- [ ] Treat a breach as a defect, not as an experiment result.

### Non-advertising revenue context

Advertising currently contributes $41.43 per 28 days against a −$1,105 net position. The engineering effort required to make it materially positive should be weighed against the same effort applied to booking conversion, pricing, or retention. This document should not be read as an argument that advertising is where the leverage is; it is an argument that advertising should at minimum stop losing money.

## Experiment framework

### Rules

1. Define the hypothesis before implementation.
2. Change one major variable per experiment.
3. Randomize users, not sessions, and keep assignments stable.
4. Exclude internal/test devices.
5. Define primary outcome and guardrails before looking at results.
6. Run for at least two weeks when seasonality/weekday effects matter.
7. Do not stop early only because eCPM temporarily rises.
8. Keep only variants that improve business outcomes without unacceptable user harm.

### Experiment record template

```markdown
### EXP-XXX: Name

- Owner:
- Start date:
- End date:
- Hypothesis:
- Eligibility:
- Control:
- Treatment:
- Allocation:
- Primary metric:
- Guardrails:
- Minimum runtime/sample:
- Result:
- Decision:
- Follow-up:
```

### Recommended experiment order

Revised against the sizing table. Reward containment is not an experiment and is not listed here — it ships unconditionally before any of this begins.

1. Home native full-width layout versus existing floating layout.
2. Coverage expansion onto one high-dwell surface versus current placements.
3. Intent-aware interstitial preload versus resume preload.
4. Add one bidding source versus current mediation stack.
5. Google-optimized floor experiment for high-volume native.

**Removed from the order:** native refresh 90 seconds versus 180–300 seconds. At a 79-second average session the refresh does not fire, so the experiment cannot produce a measurable difference. Reinstate it only if average session duration rises above roughly 150 seconds.

Do not run the new native layout and new floor on the same users at the same time unless using a properly designed factorial experiment.

## Implementation phases

Phases 0 through 4 are the AdMob track. The direct-sold track defined in "Strategy: direct-sold inventory and the house-ad waterfall" runs in parallel with a separate owner and is not sequenced against these phases.

### Phase 0: immediate risk containment

Treat this as an incident, not a sprint item. Nothing in later phases starts until Phase 0 closes.

- [ ] Feature-flag/pause the wallet-credit rewarded flow.
- [ ] Add a wallet-specific rate limit well below the generic 200 requests/minute.
- [ ] Add a server-side daily `AD_TOP_UP` cap per authenticated user.
- [ ] Audit historical `AD_TOP_UP` issuance for abuse and quantify total value issued against total rewarded revenue earned.
- [ ] Confirm rewarded-ad policy with the appropriate Google/AdMob support channel.
- [ ] Register all test devices and lock down debug receivers.
- [ ] Review Policy Center and invalid-traffic adjustments.
- [ ] Verify production backend behavior against the checked-in copy.

### Phase 1: measurement foundation

- [x] Add rewarded and interstitial paid-event listeners. **Both shipped 2026-08-23.**
- [ ] Add common lifecycle events and discard reasons. **Booking interstitial and rewarded shipped 2026-08-23;** natives outstanding.
- [ ] Build unit/country/source/version dashboards.
- [ ] Record revenue/session and active-user metrics.
- [ ] Add stable experiment cohort assignment.

### Phase 2: implementation corrections

- [ ] Use Activity context for both native loaders.
- [ ] Make native requests destination/lifecycle aware.
- [x] Make interstitial preload booking-intent aware. **Shipped 2026-08-23.**
- [x] Add full-screen ad cache timestamps/expiration. Already present; expiry discards are now counted.
- [ ] Add typed Remote Config controls. **`rewardedDailyCapEnabled` added 2026-08-23;** the rest outstanding.
- [x] Add a client-side daily cap on completed rewards. **Shipped 2026-08-23.** Three per local day, remotely disablable. Not a substitute for the server cap in Phase 0.
- [ ] Upgrade and verify the Unity SDK/adapter pair.
- [ ] Complete partner `app-ads.txt` entries.

### Phase 3: monetization experiments

- [ ] Launch the native layout experiment.
- [ ] Break sessions down to identify which currently serve no advertisement.
- [ ] Launch coverage expansion onto one high-dwell surface, with retention and booking-funnel guardrails.
- [ ] Add/test one demand partner.
- [ ] Run a separate optimized-floor experiment.

### Phase 4: rewarded redesign

- [ ] Finalize a compliant benefit.
- [ ] Implement SSV and the backend ledger.
- [ ] Add server limits and fraud alerts.
- [ ] Test retries, duplicates and delayed callbacks.
- [ ] Relaunch to a small cohort only after security/policy approval.

## Release and QA checklist

### Functional

- [ ] Consent/no-consent paths work in configured test geographies.
- [ ] Privacy-options form can be reopened.
- [ ] Every ad format loads and displays using Google test ads.
- [ ] Every mediation source passes single-source testing.
- [ ] Booking continues immediately when no interstitial is available.
- [ ] Rotation, background/foreground and navigation do not duplicate full-screen ads.
- [ ] Native ads are destroyed on view teardown.
- [ ] Reward cannot be claimed twice through navigation, rotation or replay.

### Visual/native policy

- [ ] Native Validator passes Home and Booking QR placements.
- [ ] MediaView, AdChoices and attribution remain visible.
- [ ] CTA and text are readable with maximum supported font scaling.
- [ ] Ads cannot be confused with navigation, booking or payment controls.
- [ ] No accidental-click hotspots or overlapping views exist.

### Performance

- [ ] SDK initialization does not materially regress startup.
- [ ] No Activity/NativeAd leaks.
- [ ] No increase in crash/ANR rate.
- [ ] Network calls stop when placements are ineligible.
- [ ] Layout is tested on low-memory devices and slow networks.

### Analytics

- [ ] Request/load/show/impression/paid/click/dismiss events reconcile.
- [ ] Revenue micros, currency and precision are correct.
- [ ] Test devices are excluded from business dashboards.
- [ ] Experiment assignment is stable.
- [ ] Server reward verification can be joined to the client funnel.

## Ownership, cadence, and north-star metric

This section did not exist in the original audit. A checklist with no named owner does not survive contact with a sprint.

### North-star metric

> **Ad revenue per 1,000 sessions, net of reward cost.**

Currently $0.69 gross and negative net. This single number replaces blended eCPM in every report, review, and dashboard.

Supporting metrics, in order: net contribution per rewarded impression, impressions per session, ad exposure per session, show rate by unit. Blended eCPM is retired.

### Owners

Fill these in. An unassigned row is an unstarted row.

| Track | Owner | Review cadence |
|---|---|---|
| P0 reward containment | | Daily until closed |
| Native placement rebuild | | Weekly |
| Coverage expansion | | Weekly |
| Measurement and dashboards | | Weekly |
| Policy, consent, `app-ads.txt` | | Monthly |
| Direct-sold campaigns | | Weekly, separate track |

### Cadence

- **Daily** until reward containment ships, then stop the daily.
- **Weekly** review against the weekly checklist and template already defined below, with a named presenter.
- **Monthly** review of the sizing table: re-estimate impact from measured data and re-order if the numbers have moved.
- **Per release** confirmation that the QA checklist was executed, not just present.

### Decision authority

- Anything that changes user-visible ad load requires product sign-off, not engineering sign-off alone.
- Anything that changes reward value or reward eligibility requires finance sign-off on the unit economics.
- Anything with a policy dimension requires explicit confirmation before rollout, not after.

## Weekly diagnosis checklist

Use the same checklist every week to avoid reacting to a single noisy eCPM graph.

- [ ] Compare 7-day and 28-day revenue, not only today versus yesterday.
- [ ] Compare each ad unit separately.
- [ ] Compare top countries and shifts in geography mix.
- [ ] Review format-mix percentages.
- [ ] Review match rate and show rate by unit.
- [ ] Review requests that loaded but never impressed.
- [ ] Review mediation bid participation and wins.
- [ ] Review consent rates and privacy errors.
- [ ] Review Policy Center and invalid-traffic adjustments.
- [ ] Review application-version changes.
- [ ] Review retention, bookings and support complaints.
- [ ] Record seasonality, holidays and major advertiser events.

### Weekly report template

```markdown
## Week of YYYY-MM-DD

- Revenue:
- Revenue per 1,000 sessions:
- Ads ARPDAU/ARPU:
- Requests / match rate / show rate:
- Impressions by format:
- eCPM by top ad unit and country:
- Top mediation changes:
- Retention and booking guardrails:
- Policy/invalid-traffic status:
- Releases/experiments active:
- Diagnosis:
- Actions for next week:
```

## Decision rules

- A higher eCPM with lower total revenue is not automatically a win.
- More impressions with lower retention are not automatically a win.
- A network that wins impressions but adds no incremental revenue is not automatically useful.
- A higher show rate achieved by delaying critical user flows is not acceptable.
- Any monetary/policy/security uncertainty in rewarded ads blocks rollout.
- Country and ad-unit eCPM are more actionable than a blended application eCPM.
- Revenue per session or per user is the primary outcome; eCPM is diagnostic.
- Gross revenue is not the outcome; net of reward and incentive cost is the outcome.
- Growing the volume of a format with negative unit economics makes the business worse, not better.
- Coverage growth is legitimate; density growth inside already-monetized sessions is not.
- An initiative without an estimated revenue impact and an estimated cost does not enter the plan.

## Target outcomes

These are directional engineering targets, not revenue guarantees:

- Return the ad business to a positive net contribution by containing reward cost. This precedes every target below.
- Raise ad revenue per 1,000 sessions from $0.69 toward $1.50 without raising density inside already-monetized sessions.
- Raise impressions per session from 0.99 toward 1.6 and ad exposure per session from 33.2% toward 55%.
- Restore overall show rate toward 75–80% by avoiding requests unlikely to display.
- Obtain complete impression-level revenue coverage for all formats.
- Eliminate client-authoritative wallet rewards.
- Pass Native Validator on all native placements and form factors.
- Increase the number of demand sources that genuinely bid on eligible inventory.
- Improve revenue per 1,000 sessions without harming D1/D7 retention or booking completion.
- Maintain zero unresolved AdMob policy or invalid-traffic warnings.

## Official references

### Revenue and reporting

- [Why eCPM fluctuates](https://support.google.com/admob/answer/15337570?hl=en)
- [Match rate and show rate](https://support.google.com/admob/answer/13547458?hl=en)
- [Optimize low show rate](https://support.google.com/admob/checklist/10424733?hl=en)
- [Impression-level ad revenue](https://developers.google.com/admob/android/impression-level-ad-revenue)

### Native and refresh

- [Native ads overview](https://support.google.com/admob/answer/6239795)
- [Native full-screen guidance](https://developers.google.com/admob/android/native/full-screen)
- [Automatic refresh guidance](https://support.google.com/admob/answer/2936217?hl=en)

### Rewarded ads

- [Rewarded-ad policies](https://support.google.com/admob/answer/7313578?hl=en)
- [Rewarded server-side verification](https://developers.google.com/admob/android/ssv)

### Mediation and testing

- [Choose mediation networks](https://developers.google.com/admob/android/choose-networks)
- [AdMob bidding overview](https://support.google.com/admob/answer/11555701?hl=en)
- [Meta mediation guide](https://developers.google.com/admob/android/mediation/meta)
- [Unity mediation guide](https://developers.google.com/admob/android/mediation/unity)
- [Ad Inspector and test ad units](https://developers.google.com/admob/android/ad-inspector/test-ad-units)
- [Mediation A/B testing](https://support.google.com/admob/answer/9572326?hl=en)
- [eCPM floors](https://support.google.com/admob/answer/3418058?hl=en)

### Privacy, authorization, and additional formats

- [App-ads.txt for Android](https://developers.google.com/admob/android/next-gen/app-ads)
- [App-open ad guidance](https://support.google.com/admob/answer/9341964?hl=en)

## Change log

| Date | Change | Author |
|---|---|---|
| 2026-08-22 | Initial audit and implementation plan created | Codex |
| 2026-08-23 | Added per-placement kill switches `bookingTransitionInterstitialEnabled` and `bookingTransitionPreloadBufferEnabled`, both default-true and operable through the existing `featureToggleMap` with no backend deploy. | Claude |
| 2026-08-23 | Rewarded now joins the in-flight UMP consent request instead of failing closed. `AdConsentManager.canRequestAds` stays false until the once-per-process refresh returns, so a cold-start tap on the reward coin was answered with "Rewards are temporarily unavailable" — on the highest-eCPM placement, for a race the user had no part in. Both natives already waited this way. Console side, `Rewarded_Wallet_Coins` now carries four bidding sources (AdMob, InMobi Exchange, Meta Audience Network, Unity Ads), Meta added at zero APK cost because its adapter was already bundled; waterfall is empty by design. Android change built and unit-tested, not device-verified. | Claude |
| 2026-08-23 | Rewarded wallet-coin placement instrumented and capped, Android only. Added `setOnPaidEventListener`, impression, click, earned, load and discard telemetry under a new `rewarded_wallet_coins` placement, each event carrying the winning `ad_source` and the `home`/`wallet` entry point — `rewarded_earned` against `rewarded_impression` is the first real measurement of claim rate, which every cost-per-reward figure in this document had assumed to be 100%. Added `DailyRewardState.DAILY_REWARD_CAP = 3` completed rewards per local day behind a `rewardedDailyCap` remote switch, and a 55-minute expiry guard on the held ad. No backend change; the server-side cap and SSV in Phase 0 remain open. Built and unit-tested, **not yet verified on a device**. | Claude |
| 2026-08-23 | Booking-transition interstitial reworked: intent-driven warm-up replaces the per-resume preload, two-deep preload buffer with backoff re-arm, and full impression/paid/discard telemetry for the placement. Android only — no backend change. Not yet verified on a device, and the mediation work below it (mapping Meta's interstitial placement onto this unit) is still outstanding console work. | Claude |
| 2026-08-22 | Verified all code claims against `android-app` and `gridee_backend`. Added: baseline validity, ad business profit and loss, coverage/impressions per session, initiative sizing and priority order, demand ceiling by geography, direct-sold inventory strategy, product-side monetization levers, ownership and cadence. Corrected the stale backend top-up claim, replaced the qualitative native layout findings with measured values, revised the experiment order and phases against sizing, and retired blended eCPM as a reporting metric. | Claude |

