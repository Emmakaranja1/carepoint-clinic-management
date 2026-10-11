# Postman — CarePoint API

## Setup

1. Postman → **Import** → select `CarePoint API.postman_collection.json`.
2. Collection variables are preset for local dev — `baseUrl` =
   `http://localhost:8080`. Adjust `patientEmail` / `patientPassword` if you
   use different demo credentials.
3. Start the backend: `cd backend && ./mvnw spring-boot:run` (add
   `-Dspring-boot.run.profiles=seed` for fictional data).

## How the session works

`Login` sets two `HttpOnly` cookies — `cp_access` (15 min JWT) and
`cp_refresh` (30 days). Postman stores them automatically, so `Me`,
`Refresh session` and `Logout` need no manual token handling. To inspect
them: **Cookies** (under the Send button) → `localhost`.

## Testing notes

- Every mutating request needs `X-Requested-With: XMLHttpRequest`
  (pre-set). The "missing CSRF header" request shows the 403 without it.
- `Refresh session` rotates the refresh token — the previous one becomes
  invalid immediately. Replaying an old refresh token returns `401
  TOKEN_INVALID` and revokes the whole token family (theft detection).
- `Login` is rate-limited (5/min per IP by default) — repeated rapid runs
  will surface `429 RATE_LIMITED`.
- Errors share one shape: `{code, message, fields?}` — e.g.
  `EMAIL_TAKEN`, `INVALID_CREDENTIALS`, `VALIDATION_FAILED`.
