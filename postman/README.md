# Postman — CarePoint API

## Setup

1. Postman → **Import** → select `CarePoint API.postman_collection.json`.
2. Collection variables are preset for local dev — `baseUrl` =
   `http://localhost:8080`. Adjust `patientEmail` / `patientPassword` if you
   use different demo credentials.
3. Start the backend: `cd backend && ./mvnw spring-boot:run` (add
   `-Dspring-boot.run.profiles=seed` for fictional data).

## How the session works

`Login` (or `Verify email`, or `Google sign-in`) sets two `HttpOnly` cookies —
`cp_access` (15 min JWT) and `cp_refresh` (30 days). Postman stores them
automatically, so `Me`, `Refresh session` and `Logout` need no manual token
handling. To inspect them: **Cookies** (under the Send button) → `localhost`.

## Email verification & OTP

Registration does **not** log you in — the account starts unverified and
login returns `403 EMAIL_NOT_VERIFIED` until the emailed code is confirmed.

- With the default `MAIL_PROVIDER=log`, codes are printed to the Spring Boot
  console (`MAIL(log-provider) to=... subject=...`). With `MAIL_PROVIDER=smtp`
  they arrive as real email — see `.env.example` for the SMTP variables.
- Code rules: 6 digits, valid **2 minutes**, **4** wrong tries kill it, and
  only **4** sends are allowed inside the resend cooldown window — a 5th
  request returns `429 RATE_LIMITED`.
- Paste the code into the `{{otpCode}}` collection variable before running
  `Verify email (OTP)` or `Reset password`.

## Password reset flow

`Forgot password` → always `204` (no account enumeration) → a `PASSWORD_RESET`
code is mailed if the account exists → `Reset password` with that code sets a
new password and **revokes every live session**. Log in again afterwards.

## Testing notes

- Every request ships a `pm.test` assertion (status + error code where
  applicable) — check the **Test Results** tab after each send, or run the
  whole collection with the **Runner**.
- Every mutating request needs `X-Requested-With: XMLHttpRequest`
  (pre-set). The "missing CSRF header" request shows the 403 without it.
- `Refresh session` rotates the refresh token — the previous one becomes
  invalid immediately. Replaying an old refresh token returns `401
  TOKEN_INVALID` and revokes the whole token family (theft detection).
- `Login` is rate-limited (5/min per IP by default) — repeated rapid runs
  will surface `429 RATE_LIMITED`.
- Password policy (checked per-rule, errors list the unmet ones): ≥8
  characters, an uppercase letter, a lowercase letter, a digit.
- `Google sign-in` needs `GOOGLE_CLIENT_ID` configured and a real Google
  ID token from a frontend sign-in — it is not testable standalone.
- **Booking folder order matters**: run `Book appointment` first — its
  pre-request script computes next-Monday slot variables
  (`slotStart`/`slotEnd`) and a persistent `{{bookingIdemKey}}`, and its
  test saves `{{appointmentId}}` for the replay/get/cancel requests. The
  doctor needs a schedule covering that slot (run `Set doctor schedule`
  in the Scheduling folder first; defaults assume Monday 09:00–12:00 UTC
  at 30-minute slots).
- Booking errors: `409 SLOT_TAKEN` (lost the race), `409 INVALID_SLOT`
  (not a schedule-grid slot, or beyond the clinic's lookahead window),
  `409 IDEMPOTENCY_KEY_REUSED`, `409 CANCELLATION_WINDOW_PASSED`,
  `400 VALIDATION_FAILED` (past slot / end before start). Booking writes
  are rate-limited per IP (10/min default).
- **Receptionist desk** requests need a staff session — sign in as the
  invited receptionist (or clinic admin) first; `Doctor agenda (me)`
  needs the doctor's own session. Desk bookings accept `patientId` or
  inline `patient{}` (creates a claimable GUEST record); the desk
  bypasses the patient cancellation cutoff and can mark COMPLETED /
  NO_SHOW once the slot has started.
- **Media folder order matters**: `Initiate upload` (staff session,
  `{{clinicId}}`/`{{doctorId}}` from earlier folders) saves
  `{{uploadUrl}}`/`{{mediaId}}`/`{{mediaPublicUrl}}` → `Upload bytes`
  sends the file **binary** via the presigned URL (Body → binary, a PNG
  under 1 MB — the declared ceiling; no cookies/CSRF header needed) →
  `Confirm upload` verifies magic bytes server-side and activates the
  media → `Fetch public image` then returns it anonymously. Confirming
  bytes whose real type differs from the declared `contentType` fails
  `400` and deletes the object.
- Errors share one shape: `{code, message, fields?}` — e.g.
  `EMAIL_TAKEN`, `INVALID_CREDENTIALS`, `VALIDATION_FAILED`,
  `OTP_INVALID`, `EMAIL_NOT_VERIFIED`, `GOOGLE_TOKEN_INVALID`.
