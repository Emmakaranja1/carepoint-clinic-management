# CarePoint — frontend

Next.js (App Router) client for the CarePoint clinic appointment platform.
Fictional demo data only — a portfolio build, not a live clinic system.

## Stack

- **Next.js 15** (App Router) · **React 19** · **TypeScript strict**
- **Tailwind CSS 4**
- **Zustand** — session/client state
- **React Hook Form + Zod** — forms
- **Vitest** — unit tests

## Run it

```bash
# terminal 1 — backend (needs Postgres: deploy/docker-compose.yml)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed

# terminal 2 — frontend
cd frontend && npm ci && npm run dev
```

Open http://localhost:3000.

## How the frontend talks to the backend

**Same-origin proxy (ADR-0002).** The browser only ever calls `/api/v1/*` on
`localhost:3000`; `next.config.ts` rewrites those to the backend
(default `http://localhost:8080`, override with `API_PROXY_TARGET`).
This keeps the httpOnly cookie session working with **no CORS** and no
tokens in JavaScript.

- Session: `cp_access` (15 min) + `cp_refresh` (30 d) httpOnly cookies —
  set/read automatically; the client never handles tokens.
- CSRF: mutating requests carry `X-Requested-With: XMLHttpRequest` —
  `src/lib/api.ts` adds it.
- Errors: backend returns `{code, message, fields?}` — surfaced as
  `ApiError` with `code`/`fields` for form mapping.
- `src/lib/api.ts` is the **only** place `fetch()` may be called.
- `useSession` (`src/features/auth/session.ts`) hydrates from
  `GET /auth/me` on mount; `clinicRoles` empty ⇒ patient.
- Auth screens live under `(auth)`: `/register` → `/verify-email`
  (OTP) → `/login`, plus `/forgot-password` → `/reset-password`.
- Google sign-in renders only when `NEXT_PUBLIC_GOOGLE_CLIENT_ID`
  is set (same client id as the backend's `GOOGLE_CLIENT_ID`);
  hidden otherwise — a client id is public, not a secret.

## Checks (same as CI)

```bash
npm run lint        # eslint
npm run typecheck   # tsc --noEmit
npm test            # vitest
npm run build       # next build
```

## Layout

```
src/
  app/            routes (App Router)
  features/       feature slices — auth/session today; discovery,
                  booking, desk, admin to follow
  lib/            api client (fetch wrapper — the only fetch site)
```

## Conventions

- Server components by default; `"use client"` only where state/events
  are needed.
- Forms: React Hook Form + Zod resolver; map `ApiError.fields` onto
  inputs.
- Role checks in the UI are **UX only** — the backend enforces
  everything server-side.
- No real patient data, ever. Demo/fictional content only.
