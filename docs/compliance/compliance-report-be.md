## Compliance report: BE, 2026-10-02

Scope: `wingmark-backend` (Spring Boot API, the admin panel it serves, and its emails).
Branch: `compliance/backend-audit`. Each status is based on the code, plus read-only checks against
production (Atlas counts; response headers from `wingmark-backend.onrender.com`).
Tests: 202 passing.

| ID | Item | Status | Evidence / changes | Follow-up |
|---|---|---|---|---|
| C-01 | Privacy policy | Needs human | Data inventory below. Policy versions are now served by `GET /api/legal` (`LegalController`, `LegalProperties`). | Write the policy from the inventory below (legal review); set `PRIVACY_VERSION`/`PRIVACY_URL`. |
| C-02 | Terms of service | Fixed | Signup must accept the current terms version once published (`ConsentService.requireAcceptedAtSignup`, `400 TERMS_NOT_ACCEPTED`). Acceptance is stored per user and version (`consents`: type, version, createdAt). Login/refresh return `pendingConsents` when the version changes, and `POST /api/users/{id}/consents` records re-acceptance. | Write the terms (legal review); set `TERMS_VERSION`/`TERMS_URL`. Enforcement only starts once the version is set. FE work below. |
| C-03 | Refund policy | N/A | No payments, purchases or subscriptions anywhere in the code. | |
| C-04 | Cookie policy | Pass | The server sets no cookies: stateless JWT, no session (`SecurityConfig`). Verified on production: no `Set-Cookie` on health, species, avatars, admin, Swagger, badges or login responses. | For the cookie/storage table: the admin panel stores its JWTs in browser `localStorage` (`static/admin/api.js`). The iOS app's storage is FE. |
| C-05 | Cookie consent banner | Pass | No non-essential cookies, no analytics, no server-side tracking (no analytics or tracking dependency in `pom.xml`). | |
| C-06 | Form consents | Fixed | Consent records are persisted (user, type, version, timestamp; append-only history, `GET /api/users/{id}/consents`). Consent is validated server-side and never inferred from a missing field: the version must match exactly. Marketing consent: N/A, because the app sends no marketing email. | IP/user agent are deliberately not stored (data minimization); add them only if legal asks. |
| C-07 | No unnecessary data collected | Fixed | (1) Verification links and reset codes are logged only at DEBUG; logging defaults to INFO (`application.yml` `LOG_LEVEL`) and recipient emails are masked in every log line (`EmailServiceImpl`). Before this, any SMTP failure wrote account-takeover secrets to the production logs. (2) Removed the never-used `RefreshToken.deviceInfo` (0 production docs used it). (3) Expired refresh/reset/verification tokens are deleted by a TTL index on `expiresAt`. (4) No API returns another user's personal data (ownership-scoped queries; the full user list is admin-only). | Retention periods for accounts, inactive users and the deletion audit log need a decision. `User.lastLoginAt` is written but never read; keep it for an inactive-account policy or drop it. `BirdLog.detectedSpeciesId`/`detectionConfidence` are reserved for ML and unused (0 docs). |
| C-08 | Third-party SDK audit | Needs human | Integration list below. There are no unused SDKs or dependencies and nothing sends full user objects to third parties. | Confirm DPAs and data regions for MongoDB Atlas, Render and the SMTP provider. |
| C-09 | No dark patterns | Pass | Account deletion is one authenticated call with password confirmation (`DELETE /api/users/{id}`), no harder than signup. No subscriptions, no scarcity or urgency data. | |
| C-10 | No hidden fees | N/A | No prices, fees or payments. | |
| C-11 | No fake reviews | N/A | No reviews, ratings or testimonials. The only counts are badges and admin metrics, computed from real data (`BadgeServiceImpl`, `AdminMetricsServiceImpl`); no seed data. | |
| C-12 | No unsupported claims | Pass | BE-owned copy is the three email templates (`EmailServiceImpl`), which make no claims. Species texts are factual, admin-entered content. | |
| C-13 | Image alt text | N/A (FE) | Emails contain no images. The admin panel is internal. | |
| C-14 | Color contrast | Fixed | The email footer was `#888` on white (≈3.5:1); changed to `#595959` (≈7:1). | |
| C-15 | Keyboard navigation | N/A (FE) | | |
| C-16 | Business details | Fixed / Needs human | Emails carry a legal-details footer from `BUSINESS_LEGAL_NAME`/`BUSINESS_ADDRESS`/`BUSINESS_CONTACT_EMAIL` (`BusinessProperties`). It stays empty until set, so no placeholder ever reaches a real email. No invoices exist. | Provide the legal name, address and contact email and set them on Render. |
| C-17 | Age consent / children's data | Needs human | No age check exists. A birding and pet app can attract minors. | Decide the minimum age (and parental consent if below it). Then the BE can store DOB or an age confirmation and enforce it at signup; no BE code was added without that decision. |
| C-18 | Unsubscribe link in emails | N/A | No marketing email. All three emails (verification, reset code, deletion confirmation) are transactional and contain no marketing content. | Add RFC 8058 unsubscribe headers if marketing email is ever introduced. |
| C-19 | Font and image licenses | Fixed / Needs human | (1) iNaturalist photo candidates now exclude "all rights reserved" photos (`INaturalistServiceImpl`). (2) Species images now store and return `licenseCode`, `attribution` and `sourceUrl`; the admin panel sends them when attaching a candidate and shows each image's license. (3) The admin panel uses system fonts only. (4) Bird-sound recordings already pass `recordist` and `licenseUrl` through from Xeno-canto. | The one production species image (Pacific Parrotlet, uploaded 2026-09-30) has no license recorded: confirm you own it or replace it. Many Xeno-canto recordings are CC BY-NC(-SA); confirm the app is non-commercial or filter by license. The FE must display attributions (below). |
| C-20 | Data deletion requests | Fixed | Self-service and admin deletion cascade through every collection, including consents and uploaded photos in GridFS (`AccountDeletionServiceImpl`). Added: a personal-data-free audit record (`account_deletions`: opaque id, SELF/ADMIN, requested/completed time), a confirmation email to the former address, and a data export (`GET /api/users/{id}/export`, right of access and portability). | Deletion requests by email aren't supported; only in-app or via an admin. Third parties that can't be automated: SMTP provider send logs and Atlas backups (retention unknown). |

### Data inventory (for C-01)

| Collection | Personal data | Purpose | Kept |
|---|---|---|---|
| `users` | email, password hash (bcrypt), username, first/last name (optional), profile picture (avatar key or own upload), favorite species, role, email-verified flag, `lastLoginAt`, `walkthroughSeenAt` (whether the in-app intro was seen), `tokenVersion` | Account, login, profile | Until account deletion |
| `user_settings` | unit preference, locale | App preferences | Until account deletion |
| `bird_logs` | **precise latitude/longitude** of sightings, free-text location name and note, photo URL, custom name, species, observed time | The core diary and map | Until the log or the account is deleted |
| `uploads.files/.chunks` (GridFS) | Photos (EXIF/GPS stripped, ≤1600px) | Log photos, custom avatars | Until account deletion, unless another account references the same file |
| `user_badges` | Badge progress | Gamification | Until account deletion |
| `consents` | Accepted document type and version, time | Proof of acceptance | Until account deletion |
| `refresh_tokens` | Token hash, expiry, revocation | Sessions | Auto-deleted at expiry (30 days) |
| `password_reset_tokens` | Salted hash of the 6-digit code, attempt count | Password reset | Auto-deleted at expiry (15 min) |
| `email_verification_tokens` | Token hash | Email verification | Auto-deleted at expiry (24 h) |
| `account_deletions` | Opaque former user id only | Deletion audit trail | **No period defined** (needs human) |
| Server logs (Render) | Masked emails, user ids, request paths on errors | Operations | Render's log retention |

### Third parties (for C-08)

| Service | Purpose | User data it receives | Status |
|---|---|---|---|
| MongoDB Atlas | Database (all data above) | Everything stored | In use. DPA and region: needs human. |
| Render | Hosting (Frankfurt per `render.yaml`) | All API traffic, logs | In use. DPA: needs human. |
| SMTP provider (`SMTP_HOST`) | Transactional email | Email address, email content (verification links, reset codes) | In use. Provider, DPA and send-log retention: needs human. |
| Xeno-canto | Bird sound lookup | Species scientific name only; no user data | In use |
| iNaturalist | Admin photo search | Species scientific name only (admin-triggered); no user data | In use |

There's no analytics, error tracking, AI, CRM, payments or CDN.

### Needs human
- C-01 / C-02: write the Privacy Policy and Terms (legal review), publish them, then set `PRIVACY_VERSION`/`PRIVACY_URL`/`TERMS_VERSION`/`TERMS_URL` on Render. Setting a version starts enforcement at signup and prompts existing users.
- C-07: retention periods for user data, inactive accounts and `account_deletions`. Decide whether to keep `lastLoginAt` and the reserved ML fields.
- C-08: DPAs and data regions for MongoDB Atlas, Render and the SMTP provider; name the SMTP provider.
- C-16: legal name, address and contact email, set as `BUSINESS_*` on Render.
- C-17: minimum age and parental-consent rule.
- C-19: confirm ownership or license of the existing Pacific Parrotlet species image; confirm the non-commercial status of Xeno-canto recordings.
- C-20: Atlas backup retention; the SMTP provider's log retention.

### Needs human – FE (iOS)
- **Signup:** fetch `GET /api/legal`. For each non-null version, show an unticked "I accept the [Terms]/[Privacy Policy]" checkbox linking to its URL, and send `acceptedTermsVersion` / `acceptedPrivacyVersion` in `POST /api/auth/register`. Handle `400 TERMS_NOT_ACCEPTED` / `PRIVACY_NOT_ACCEPTED`.
- **Re-acceptance:** when a login/refresh/change-password response has a non-empty `pendingConsents`, show a blocking "Our terms changed" screen. Accept with `POST /api/users/{id}/consents` `{type, version}` for each entry until it returns `pendingConsents: []`.
- **Settings → "Download my data":** `GET /api/users/{id}/export` (JSON file, use a share sheet).
- **Attribution:** under every species image with a non-null `licenseCode`, show its `attribution` (link to `sourceUrl`). For bird sounds, show `recordist` and link `licenseUrl`.
- **Account deletion:** the server now emails a confirmation; the app can say "We've emailed you a confirmation".
- **Privacy and Terms links** reachable from Settings (URLs from `GET /api/legal`).

### Placeholders added
- None. Unknown business and legal facts are configuration that stays empty and shows nothing until set (`BUSINESS_*`, `TERMS_*`, `PRIVACY_*`), so no placeholder can reach a real user.

### Destructive change to confirm before deploying
- The TTL index on token `expiresAt` makes MongoDB delete already-expired tokens on first deploy. On production right now that's **6 expired email-verification tokens** (verified read-only). They can't be used anymore, but this deletes production records.
