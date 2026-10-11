<div align="center">

# 🏥 CarePoint

**Appointment management for small independent clinics and specialist practices**

![Status](https://img.shields.io/badge/status-in%20development-blue)
![Backend](https://img.shields.io/badge/backend-Spring%20Boot%203%20%C2%B7%20Java%2021-6DB33F?logo=springboot&logoColor=white)
![Frontend](https://img.shields.io/badge/frontend-Next.js%20%C2%B7%20TypeScript-000000?logo=nextdotjs&logoColor=white)
![Styling](https://img.shields.io/badge/ui-Tailwind%20%C2%B7%20Zustand-38BDF8?logo=tailwindcss&logoColor=white)
![Database](https://img.shields.io/badge/database-PostgreSQL%2016-4169E1?logo=postgresql&logoColor=white)
![Storage](https://img.shields.io/badge/media-Cloudflare%20R2-F38020?logo=cloudflare&logoColor=white)
![CI](https://img.shields.io/badge/ci-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)
![Data](https://img.shields.io/badge/demo%20data-fictional%20only-important)

</div>

> **Status:** design complete, implementation in progress milestone by
> milestone. Sections describing unimplemented work are marked *planned*.
> Everything runs locally with Docker Compose and fictional seed data — no
> cloud account required.

## Table of contents

1. [The problem](#1-the-problem)
2. [What it does](#2-what-it-does)
3. [User journeys](#3-user-journeys)
4. [Architecture](#4-architecture)
5. [Database design](#5-database-design)
6. [Search, pagination and performance](#6-search-pagination-and-performance)
7. [Security model](#7-security-model)
8. [Media storage](#8-media-storage)
9. [Local development](#9-local-development)
10. [Environment variables](#10-environment-variables)
11. [Testing strategy](#11-testing-strategy)
12. [CI and quality gates](#12-ci-and-quality-gates)
13. [Deployment](#13-deployment)
14. [Troubleshooting](#14-troubleshooting)
15. [Roadmap and known limitations](#15-roadmap-and-known-limitations)
16. [Contributing](#16-contributing)
17. [License and acknowledgements](#17-license-and-acknowledgements)

---

## 1. The problem

Small clinics still run their diaries on phone calls and paper books:

- **Double-booking** when two people touch the schedule at once.
- **No self-service** — every booking or cancellation costs staff time.
- **No visibility** — "how full is next week?" and "what's our no-show
  rate?" require manual counting.
- **Existing tools are hospital-grade**: heavy and expensive for a
  three-doctor practice.

CarePoint is scoped to what a small practice actually runs on — discovery,
availability, booking, cancellation, schedules, a receptionist desk, and
basic reporting — with **correctness (zero double-bookings) and tenant
isolation treated as headline engineering properties, not afterthoughts**.

## 2. What it does

**Core feature set** *(planned)*:

- 🩺 Public doctor discovery with specialty filtering
- 🗓️ Real availability computed from weekly schedules, time-off and bookings
- 👤 Patient accounts: register, log in, book, view, cancel within policy
- 🕐 Doctor schedules: weekly windows + dated time-off
- 🖥️ Receptionist desk: agenda, book-on-behalf, reschedule, status management
- 📊 Clinic reporting: appointment counts, utilization, no-show rate
- 🖼️ Staff-managed media: doctor photos and clinic logos via object storage

**Deliberately out of scope** — *decided, not omitted:*

- Medical records, diagnoses, prescriptions, clinical notes
- Real payments, insurance processing, SMS OTP/MFA
- Video consultations or video upload/transcoding (external embeds only)
- Patient file uploads, waitlists, appointment reminders

## 3. User journeys

```mermaid
journey
    title Patient: book an appointment
    section Discover
      Open clinic page: 5: Patient
      Filter by specialty: 4: Patient
      Open doctor profile: 4: Patient
    section Book
      Pick a slot in the availability grid: 5: Patient
      Log in or register: 3: Patient
      Confirm with visit reason: 5: Patient
    section Manage
      View upcoming appointments: 5: Patient
      Cancel inside the clinic's cutoff window: 4: Patient
```

```mermaid
journey
    title Receptionist: run the day's book
    section Morning
      Open today's agenda: 5: Receptionist
      Filter by doctor and status: 4: Receptionist
    section Desk work
      Book for a walk-in or phone patient: 4: Receptionist
      Reschedule (atomic cancel + rebook): 5: Receptionist
      Mark completed / no-show: 4: Receptionist
```

```mermaid
journey
    title Doctor and clinic admin
    section Doctor
      Define weekly schedule and slot length: 4: Doctor
      Block a time-off range: 4: Doctor
      Review own agenda: 5: Doctor
    section Admin
      Manage staff roles per clinic: 4: Admin
      Upload clinic logo and doctor photos: 4: Admin
      Review utilization and no-show report: 4: Admin
```

## 4. Architecture

A **modular monolith**: one Spring Boot deployable with enforced internal
module boundaries — the correctness-critical parts (booking, scheduling)
share one transaction boundary, while modules stay cleanly separable.

```mermaid
flowchart LR
    subgraph Browser
        P[Patient] & S[Staff]
    end
    subgraph Frontend host
        NX[Next.js app<br/>/api/* → same-origin proxy]
    end
    subgraph Backend host
        BE[Spring Boot modular monolith]
    end
    PG[(PostgreSQL)]
    R2[(Object storage<br/>public media)]

    P & S --> NX --> BE
    BE --> PG
    BE -->|mints bounded presigned PUTs| R2
    P & S -.->|media GETs direct — backend never proxies bytes| R2
```

**Module map** (boundaries verified by architecture tests):

| Module | Owns |
| --- | --- |
| `auth` | registration, login, token lifecycle, security config |
| `users` | accounts, clinic memberships, role grants |
| `clinics` | clinic profile, timezone, cancellation policy |
| `catalog` | specialties, doctor profiles, public discovery reads |
| `scheduling` | weekly schedules, time-off, availability engine |
| `booking` | appointment lifecycle, cancellation, desk operations |
| `media` | object metadata, upload authorization pipeline |
| `reporting` | read-only aggregates |
| `common` | error envelope, `Clock`, config, `StorageService` port |

## 5. Database design

```mermaid
erDiagram
    CLINICS ||--o{ CLINIC_MEMBERSHIPS : has
    USERS ||--o{ CLINIC_MEMBERSHIPS : joins
    CLINICS ||--o{ DOCTOR_PROFILES : employs
    DOCTOR_PROFILES ||--o{ DOCTOR_SCHEDULES : defines
    DOCTOR_PROFILES ||--o{ DOCTOR_TIME_OFF : blocks
    DOCTOR_PROFILES ||--o{ APPOINTMENTS : hosts
    USERS ||--o{ APPOINTMENTS : books
    SPECIALTIES ||--o{ DOCTOR_PROFILES : categorizes
    CLINICS ||--o{ MEDIA_OBJECTS : owns
    USERS ||--o{ REFRESH_TOKENS : holds
```

The interesting parts:

- **Double-booking is prevented by the database, not the application.** A
  partial GiST exclusion constraint —
  `EXCLUDE USING gist (doctor_id WITH =, slot WITH &&) WHERE (status='BOOKED')`
  — rejects any overlapping booking inside the insert transaction. Losers get
  `409 SLOT_TAKEN`. Application-level availability checks exist for friendly
  UX; the constraint owns correctness. Reschedules run as cancel + insert in
  a single transaction, so a slot swap is atomic.
- **Tenant isolation is structural.** Every tenant-scoped table carries
  `clinic_id`, and composite foreign keys make cross-clinic references
  *uninsertable* — e.g. an appointment can only reference a
  `doctor_profile` belonging to the same clinic.
- **Indexes are tied to real queries**: GiST `(doctor_id, slot)` for
  availability overlap probes; btree `(clinic_id, lower(slot))` for agenda
  scans; `(patient_id, lower(slot))` for a patient's own history.
- **UTC everywhere** (`timestamptz`); each clinic declares an IANA timezone
  used for schedule math and display.
- Migrations are versioned and forward-only.

## 6. Search, pagination and performance

Measured, not asserted:

- **No unbounded lists** — every collection endpoint paginates (offset
  pagination, stable `ORDER BY …, id` tiebreak, enforced max page sizes).
- **Slim projections** — list endpoints return card-level DTOs, never full
  entities; a fixed **query budget per endpoint** is asserted in tests so
  N+1 regressions fail CI.
- **Availability** is computed (schedule − time-off − booked), not stored —
  no second source of truth for "what's free". The overlap probe rides the
  GiST range index.
- **Caching is conservative**: short HTTP caching on public reads only;
  operational views (agenda, my appointments) are always fresh; the booking
  path never trusts cached availability.
- **Evidence rule**: index or query changes ship with `EXPLAIN ANALYZE`
  output against a large seeded dataset; load numbers are recorded with the
  environment they were measured on — local figures are never quoted as
  production SLOs.
- Cursor pagination and full-text search are documented as *future*
  options with explicit adoption thresholds — not premature additions.

## 7. Security model

- **Auth**: short-lived access tokens + rotating refresh tokens with
  reuse detection (stolen-token replay revokes the whole family); sessions
  delivered as `HttpOnly; Secure; SameSite=Lax` cookies through a
  same-origin proxy. Passwords hashed with Argon2id.
- **Authorization is server-side only.** Staff endpoints require a
  membership matching the `clinicId` in the path — cross-tenant access
  returns `404`, so existence isn't leaked. Patients can only touch their
  own rows. Privileged roles are granted exclusively through admin
  endpoints; the registration payload has no role field to abuse.
- **Rate limiting**: tight limits on login/register (credential stuffing),
  booking (slot hoarding), availability (scraping) and uploads.
- **Hygiene**: parameterized queries only; Bean Validation at every API
  boundary; a stable error-code envelope (`SLOT_TAKEN`,
  `CANCELLATION_WINDOW_PASSED`, `RATE_LIMITED`, …) with no stack leakage;
  structured logs carrying IDs, never tokens, bodies or free text.
- **Audit**: appointment mutations and role grants write append-only
  audit events.
- **Honest boundary**: this is a portfolio/demo system running on
  fictional data. It does **not** claim HIPAA, GDPR, or any other
  healthcare-regulation compliance — that requires a formal assessment
  before real patient data is ever involved.

## 8. Media storage

Bytes live in object storage (Cloudflare R2, S3-compatible); PostgreSQL
stores only object keys and metadata.

```mermaid
sequenceDiagram
    participant Staff as Staff browser
    participant API as Backend
    participant R2 as Object storage
    participant DB as Postgres

    Staff->>API: POST /media/uploads (clinic-scoped authz)
    API->>DB: media row (PENDING)
    API-->>Staff: presigned PUT (type + size bounded)
    Staff->>R2: PUT bytes — direct, backend never proxies
    Staff->>API: POST /media/{id}/confirm
    API->>R2: verify object + magic-byte check
    API->>DB: status = ACTIVE (else object deleted)
```

- A small `StorageService` abstraction keeps the app provider-independent;
  a local-disk implementation powers dev/test so **no cloud account is
  needed to run or demo the project**.
- Public media (photos, logos) uses a public bucket with immutable,
  server-generated keys; any future private files would live in a separate
  bucket behind short-lived presigned URLs.
- Uploads are allow-listed image types, size-capped, magic-byte verified,
  and orphaned uploads are reaped automatically.
- Storage credentials exist only on the server. Demo imagery is fictional.

## 9. Local development

**Prerequisites:** Docker + Docker Compose · JDK 21 · Node 20+

```bash
git clone <repo> && cd carepoint
cp .env.example .env                  # fill in local values
docker compose up -d postgres         # database only, or `up` for everything

cd backend && ./mvnw spring-boot:run  # API on :8080
cd frontend && npm ci && npm run dev  # UI on :3000

./scripts/seed.sh                     # fictional clinics, doctors, schedules
```

Media works fully offline — `STORAGE_PROVIDER=local` writes to a local
directory; switching to R2 is an env-var change, not a code change.

## 10. Environment variables

| Variable | Purpose | Example |
| --- | --- | --- |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | Postgres connection | `jdbc:postgresql://localhost:5432/carepoint` |
| `DB_POOL_MAX` | connection-pool ceiling | `10` |
| `JWT_SIGNING_KEY` | token signing secret | `change-me-32+-chars` |
| `CORS_ALLOWED_ORIGINS` | explicit origin allow-list | `http://localhost:3000` |
| `STORAGE_PROVIDER` | `local` or `r2` | `local` |
| `R2_*` | bucket, keys, public base URL — **server only** | `<from provider>` |
| `LOCAL_STORAGE_DIR` | dev media root | `./var/media` |
| `SPRING_PROFILES_ACTIVE` | `local` / `ci` / `staging` / `prod` | `local` |

`.env` is gitignored; `.env.example` carries names and placeholders only.
No real credentials are ever committed.

## 11. Testing strategy

| Layer | Tooling | Proves |
| --- | --- | --- |
| Unit | JUnit 5 | slot math, cutoff rules, status transitions |
| Integration | Testcontainers — **real Postgres, real migrations, no DB mocks** | constraints, transactions, JPA mappings |
| Authorization | parameterized API matrix — every role × endpoint × clinic | cross-tenant access is impossible |
| Concurrency | multi-threaded booking test | N simultaneous bookings → **exactly one** succeeds |
| Architecture | ArchUnit | module boundaries hold |
| Query budget | statement counters | N+1 regressions fail CI |
| Upload security | integration tests | spoofed MIME, oversize, orphan cleanup |
| Frontend | Vitest + Testing Library | forms, guards, error/empty/loading states |
| Accessibility | axe-core in E2E | WCAG 2.1 AA on patient flows |
| E2E | Playwright | full book / cancel / desk flows |
| Performance | k6 + `EXPLAIN ANALYZE` + Lighthouse CI | evidence, not claims |

## 12. CI and quality gates

GitHub Actions, all required before merge:

- **Backend** — compile, full test suite (Testcontainers incl. the
  concurrency test), migration run on ephemeral Postgres
- **Frontend** — lint, typecheck, tests, production build, bundle-size
  report, Lighthouse CI on key flows
- **Security** — dependency review, vulnerability scan, secret scan
- **E2E** — containerized stack + Playwright

`main` is protected: PRs only, green checks required, no direct pushes.
Conventional Commits; one issue → one branch → one PR.

## 13. Deployment

Designed to run a full demo at **$0/month** on current free tiers — and to
run entirely locally via Docker Compose as a permanent fallback:

| Layer | Approach | Note |
| --- | --- | --- |
| Frontend | Vercel free tier | `/api/*` rewrite proxies to the backend |
| Backend | Render free container | sleeps when idle — first request may take ~a minute |
| Database | Neon free Postgres | scales to zero; resumes in <1 s |
| Media | Cloudflare R2 free tier | 10 GB + free egress |

Free tiers are for demonstration only — they sleep, pause, and carry no
durability guarantees. Any real deployment gets its own hardened
configuration review first.

## 14. Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| First request takes ~1 min | Free-tier cold start — expected |
| `extension "btree_gist" does not exist` | migrations didn't run — check Flyway history |
| `409 SLOT_TAKEN` on a visibly free slot | constraint is authoritative — inspect `appointments` for overlapping BOOKED rows |
| Uploads fail locally | `STORAGE_PROVIDER` should be `local` |
| `FATAL: too many connections` | `DB_POOL_MAX` above the DB provider's limit |
| Availability wrong around DST | clinic timezone misconfigured — slots compute in clinic-local time |

## 15. Roadmap and known limitations

**Delivery plan:** scaffold → schema + seed → auth → discovery →
availability → booking → schedules + media → receptionist desk →
reporting → hardening. Small PRs, each with acceptance criteria and tests.

**Known limitations (deliberate):**

- Demo hosting sleeps/pauses — a free-tier reality, not a bug.
- Single-region, single-instance; rate limits are per-instance.
- Reports are aggregates only; no data export.
- Tenant isolation is enforced in schema + service + tests; Postgres
  row-level security is a documented upgrade path.
- Fictional data only; not assessed or certified for real patient data.

## 16. Contributing

This is currently a solo portfolio project. Conventions: Conventional
Commits, small reviewable PRs, tests required for behavior changes, and
**never commit secrets or real patient data**.

## 17. License and acknowledgements

- License: TBD — will be decided before any public distribution.
- All names, photos, logos and data in the demo are **fictional**.
- Built with Spring Boot, Next.js, PostgreSQL, Cloudflare R2, and a lot of
  respect for database constraints.
