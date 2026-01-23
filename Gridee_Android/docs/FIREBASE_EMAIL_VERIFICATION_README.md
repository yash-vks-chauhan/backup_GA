# Firebase Email Verification + Forgot Password (Safe Integration Guide)

This repo currently supports:
- Email/password auth using local `passwordHash` (BCrypt) via `/api/auth/register` + `/api/auth/login`
- Google sign-in via `/api/auth/google`

The safest way to add **email verification** and **forgot password** without impacting bookings/payments/etc is:
- Use **Firebase Authentication** for email/password + verification + password reset (client-side)
- Keep the backend as-is for all business APIs, and add a small **“Firebase ID token → backend JWT”** exchange

This avoids the broken split where Firebase resets a password but the backend still checks the old local `passwordHash`.

---

## Target Outcome

After implementation:
- New users register with Firebase email/password
- Firebase sends verification email (link-based)
- App blocks login until `emailVerified == true`
- “Forgot password” uses Firebase reset email
- Backend issues its normal JWT **only after** verifying a Firebase ID token

Existing endpoints keep working:
- `/api/auth/register` + `/api/auth/login` remain unchanged for legacy users (optional; you can deprecate later)
- `/api/auth/google` remains unchanged

---

## Part A — Firebase Console Setup (Do This First)

1. Firebase Console → Authentication → Sign-in method:
   - Enable **Email/Password**
2. Firebase Console → Authentication → Templates:
   - Configure **Email address verification**
   - Configure **Password reset**
3. Firebase Console → Project settings → Service accounts:
   - Generate a **service account JSON** (keep secret; don’t commit)

Notes:
- Firebase verification/reset emails are sent by Firebase only when using the **Firebase client SDK** (Android) or the Firebase Auth REST API.
- The Firebase Admin SDK can verify tokens and manage users, but it does **not** send emails by itself.

---

## Part B — Android/App Flow (Firebase SDK)

### 1) Register + Send Verification Email

1. Call `createUserWithEmailAndPassword(email, password)`
2. Immediately call `currentUser.sendEmailVerification()`
3. Show “Check your email” UI, with:
   - “Open email app”
   - “Resend verification”

### 2) Resend Verification

1. User signs in (or you keep the user session from registration)
2. Call `currentUser.sendEmailVerification()` again

### 3) Login (Block if Not Verified)

1. Call `signInWithEmailAndPassword(email, password)`
2. Call `currentUser.reload()`
3. If `!currentUser.isEmailVerified()`:
   - block login
   - show “Verify your email” UI
4. If verified:
   - call `currentUser.getIdToken(true)`
   - send that `idToken` to backend to exchange for your backend JWT (see Part C)

### 4) Forgot Password

Use Firebase directly:
- `sendPasswordResetEmail(email)`

No backend email service is required for forgot password if you use Firebase for email/password auth.

---

## Part C — Backend Changes (Minimal, Auth-Only)

You will add new endpoints under `/api/auth/firebase/*` and keep the rest of the backend unchanged.

### 0) Decide How You’ll Handle Legacy Users

Recommended rollout:
- Keep existing `/api/auth/register` + `/api/auth/login` for existing users
- New app versions use Firebase email auth + the new `/api/auth/firebase/*` endpoints
- Later you can migrate/disable legacy registration/login when ready

### 1) Add Firebase Admin SDK Dependency (Gradle)

Update `build.gradle` and add:
- `implementation 'com.google.firebase:firebase-admin:9.2.0'`

### 2) Add Firebase Admin Initialization

Create:
- `src/main/java/com/parking/app/config/FirebaseConfig.java`

Initialize Firebase using either:
- `firebase.credentials.json` (recommended for production env var)
- `firebase.credentials.path` (local dev)

### 3) Add User Fields (Mongo)

Update `src/main/java/com/parking/app/model/Users.java`:
- Add `firebaseUid` (unique+sparse)
- For Firebase email users, set:
  - `authProvider = "FIREBASE_EMAIL"` (recommended to distinguish from legacy local email auth)
  - `passwordHash = null`
  - `emailVerified` synced from Firebase token claims

### 4) Add DTOs

Create:
- `src/main/java/com/parking/app/dto/FirebaseTokenExchangeRequestDto.java`
  - `idToken` (string, required)

Optionally (if you want the backend to create the Mongo user record during registration):
- `src/main/java/com/parking/app/dto/FirebaseRegisterRequestDto.java`
  - `idToken`, `name`, `phone`, `vehicleNumbers`, `parkingLotName`

### 5) Add a Firebase Token Verification Service

Create:
- `src/main/java/com/parking/app/service/FirebaseTokenService.java`

Responsibilities:
- Verify `idToken` using `FirebaseAuth.verifyIdToken(idToken)`
- Extract:
  - `uid`
  - `email`
  - `email_verified` (claim)

### 6) Add Backend Endpoints

Update `src/main/java/com/parking/app/controller/AuthController.java` (or create a new controller):

#### Endpoint A: Exchange Firebase ID Token → Backend JWT

`POST /api/auth/firebase/exchange`
- Input: `{ "idToken": "..." }`
- Backend:
  - verify token
  - require `email` present
  - require `email_verified == true`
  - upsert/load `Users` by email
  - set `firebaseUid`, `emailVerified=true`, `authProvider="FIREBASE_EMAIL"`
  - generate backend JWT via `JwtUtil.generateToken(user.getId(), user.getRole())`
- Output: same style as your existing login response (`AuthResponseDto`)

This is the “gateway” that lets Firebase-authenticated users use the rest of your backend safely.

#### Endpoint B (Optional): Create Mongo User Record at Registration Time

If you want the backend to store profile fields immediately on register:

`POST /api/auth/firebase/register`
- Input includes `idToken` + profile fields
- Backend verifies token (even if not verified yet), then:
  - create `Users` in Mongo with `emailVerified=false`, `authProvider="FIREBASE_EMAIL"`
  - return `requiresVerification=true` and **no backend JWT**

If you don’t need profile fields before verification, you can skip this and only create the Mongo user after exchange.

### 7) Rate Limiting (Recommended)

Update `src/main/java/com/parking/app/config/RateLimitingFilter.java` to treat:
- `/api/auth/firebase/exchange`
- `/api/auth/firebase/register` (if added)

like login endpoints for brute-force protection.

### 8) Configuration (Properties / Env Vars)

Update:
- `src/main/resources/application-prod.properties`
- `src/main/resources/application-local.properties`

Add:
```properties
firebase.credentials.json=${FIREBASE_CREDENTIALS_JSON:}
firebase.credentials.path=${FIREBASE_CREDENTIALS_PATH:}
```

---

## Recommended Rollout Plan

1. Implement Firebase in Android and confirm:
   - verification email arrives
   - “forgot password” works
2. Implement backend:
   - Firebase Admin init
   - `/api/auth/firebase/exchange`
3. Update Android login flow:
   - after verification, exchange `idToken` for backend JWT
4. Keep legacy `/api/auth/login` working until you decide to migrate.

---

## Common Gotchas (Avoid These)

- Do not use Firebase Admin “generate verification link” unless you also have an email-sending system.
- Don’t mix “Firebase password reset” with backend BCrypt login. Pick one source of truth for passwords.
- Ensure you only issue backend JWT after `email_verified == true` (for Firebase email users).

