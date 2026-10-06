# wingmark-backend

Backend API for **<a href="https://github.com/bilgesucakir/wingmark">Wingmark</a>**, a personal bird-logging iOS app. Log a bird sighting
(photo, species, life stage, gender, location, notes), browse it on a map, look it up
in a species guide (with photos and live call/song recordings), and earn badges as you
go. Personal-only for now: every user sees just their own logs, never anyone else's.

## Tech stack

- Java 21, Spring Boot 3.3.4
- Spring Web MVC + Spring Security (JWT, stateless)
- Spring Data MongoDB
- MongoDB (local instance by default, or point it at MongoDB Atlas) — see [Database](#database)
- springdoc-openapi (Swagger UI)
- Lombok
- Xeno-canto API v3 (bird sound recordings) and iNaturalist API (photo curation)

## Getting started

### Requirements

- Java 21
- Maven

### Configuration

Copy `.env.example` to `.env` and fill in what you need:

```bash
cp .env.example .env
```

The only thing most people need to set is `XENO_CANTO_API_KEY` (free, register at
https://xeno-canto.org/account) if you want the species-guide sound endpoint to work.
Everything else has a working default for local development.

`.env` is loaded automatically at startup (via `spring-dotenv`) and is gitignored —
never commit it.

Without `SMTP_HOST`/`SMTP_USERNAME`/`SMTP_PASSWORD` set, registration still works but
emails fail to send (logged as an error, without the secret). To still get the
verification link or reset code locally, run with `LOG_LEVEL=DEBUG`: they're then logged
at DEBUG. Logging defaults to INFO, so they're never written in production - never set
`LOG_LEVEL=DEBUG` there. See
[`EmailServiceImpl`](src/main/java/com/wingmark/backend/service/impl/EmailServiceImpl.java).
Any SMTP provider works (a Gmail app password, Mailtrap/Ethereal for testing,
SendGrid/Mailgun/SES's SMTP relay, etc). Also set `APP_BASE_URL` to wherever this
backend is actually reachable (defaults to `http://localhost:8080`) - it's baked into
the verification link.

### Run it

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

### Run the tests

```bash
mvn test
```

## Deploying (Render)

A `render.yaml` [Blueprint](https://render.com/docs/blueprint-spec) is included at the repo
root, building the app from the `Dockerfile` (Java isn't one of Render's native runtimes,
so it always deploys as a container). To deploy:

1. In the Render dashboard: **New > Blueprint**, point it at this repo/branch.
2. Render reads `render.yaml` and creates one web service (`wingmark-backend`) with a
   1GB persistent disk mounted at `/data` for uploaded photos, and a health check at
   `/actuator/health`.
3. Before the first deploy succeeds, set these env vars on the service (Render prompts
   for any `sync: false` var from the blueprint, but confirm they're filled in):
   - `DB_CONNECTION_STRING` — required. Render has no managed MongoDB, so point this at
     an Atlas cluster (or any other reachable instance), same format as local dev.
   - `SMTP_HOST` / `SMTP_USERNAME` / `SMTP_PASSWORD` / `MAIL_FROM` — required for real
     accounts to work at all: registration silently fails to deliver the verification
     email without these, and `login()` rejects unverified accounts, so **every new
     user would be locked out with no visible error**. See "Configuration" above for
     provider options; `MAIL_FROM` must be an address your provider is authorized to
     send from.
   - `BUSINESS_LEGAL_NAME` / `BUSINESS_ADDRESS` / `BUSINESS_CONTACT_EMAIL` — your legal
     sender details, shown in every email's footer once `BUSINESS_LEGAL_NAME` is set.
     Empty by default; nothing is shown until you fill them in.
   - `TERMS_VERSION` / `TERMS_URL` / `PRIVACY_VERSION` / `PRIVACY_URL` — set these once
     the documents are published; see [Legal](#legal-apilegal).
   - `LOG_LEVEL` — leave unset (INFO). **Never set `DEBUG` in production**: at DEBUG,
     undelivered verification links and reset codes are logged.
   - `CORS_ALLOWED_ORIGINS` — leave unset. Only needed if a browser app on another origin
     must call the API; the iOS app and the same-origin admin panel don't need it.
   - `XENO_CANTO_API_KEY` — optional, only needed for the sound-recordings endpoint.
   - `JWT_SECRET` is generated automatically by Render on first deploy; everything else
     (`SMTP_PORT`, port, JWT expirations) is already wired to sensible
     defaults in `render.yaml`/`application.yml`.
4. After the first deploy, confirm the assigned URL matches `APP_BASE_URL` in
   `render.yaml` (`https://wingmark-backend.onrender.com` by default) - Render appends
   a random suffix if that name is already taken elsewhere. Update the env var in the
   dashboard if it differs; it's baked into every verification-email link.

Uploaded images are stored **in MongoDB** (GridFS: the `uploads.files` / `uploads.chunks`
collections), not on disk - so every instance (Render, a laptop, tests) sees the same
photos through the same database, and nothing is lost on deploy. Images are downscaled to
at most 1600px on the longest side, so each is typically a few hundred KB; keep an eye on
Atlas storage (the free tier is 512MB).

**Running locally:** don't copy the production `.env` as-is - it points the database at
Atlas (so local testing writes to production). Override it when starting, e.g.
`DB_CONNECTION_STRING=mongodb://localhost:27017/wingmark mvn spring-boot:run`.

### Inactive account cleanup

Accounts with no activity for two years are deleted after a warning email. **Off by default.**

- Activity is a login, a token refresh, or creating or changing a bird log. Admins are never deleted.
- A warning email is sent 7 days before the two-year mark; the account is deleted at two years only if it was warned at least 7 days earlier and there has been no activity since. Deletion uses the normal account deletion (audit record initiator `INACTIVITY`).
- Settings: `INACTIVITY_CLEANUP_ENABLED` (default `false`), `INACTIVITY_DRY_RUN` (default `true`: only logs who would be warned or deleted; set `false` for real), `INACTIVITY_AFTER_DAYS` (730), `INACTIVITY_WARN_DAYS_BEFORE` (7), `INACTIVITY_MAX_WARNINGS_PER_RUN` (50, respects the daily mail cap), `INACTIVITY_MAX_DELETIONS_PER_RUN` (20), `INACTIVITY_CRON` (daily 03:30 UTC).
- Turn it on only when the privacy policy that announces it is published: first `ENABLED=true` with the dry run, check the logs, then `INACTIVITY_DRY_RUN=false`.

### Abandoned signups

Signups whose email address is never verified can be deleted after a number of days. **Off by default.**

- Only accounts that are unverified and never logged in, older than `UNVERIFIED_OLDER_THAN_DAYS` (default 7), and not admins. Deletion uses the normal account deletion (audit initiator `UNVERIFIED`) and **sends no email**, because the address was never confirmed.
- Settings: `UNVERIFIED_CLEANUP_ENABLED` (default `false`), `UNVERIFIED_DRY_RUN` (default `true`: only logs), `UNVERIFIED_OLDER_THAN_DAYS` (7), `UNVERIFIED_MAX_PER_RUN` (100), `UNVERIFIED_CRON` (daily 04:15 UTC).
- Turn it on only while verification emails are being delivered, or real people who never received their link would be deleted. Run with the dry run first.

Signup and resend limits: at most 5 signups per hour and 20 per day per client address; a repeated resend-verification request within 60 seconds for the same account is ignored (same 202 response).

## Database

Uses **MongoDB**. By default it connects to a local instance at
`mongodb://localhost:27017/wingmark` — you need Mongo running locally (or override
`DB_CONNECTION_STRING` to point elsewhere) for the app itself to start. Tests don't need this:
they spin up an embedded in-memory MongoDB automatically.

To point at MongoDB Atlas (or any other instance), override this env var:

```
DB_CONNECTION_STRING=mongodb+srv://<user>:<password>@<cluster-host>/wingmark?retryWrites=true&w=majority
```

Entities map straight to collections (`users`, `bird_logs`, `species`, `species_images`,
`badges`, `user_badges`, `refresh_tokens`, `password_reset_tokens`, `user_settings`) with
plain UUID references between them (e.g. `BirdLog.userId`, `BirdLog.speciesId`) rather
than joins — the same shape the JPA entities had, so no relation modeling was needed for
the switch.

### Becoming an admin

There's no self-service "become admin" flow by design — admin-only endpoints (species
and badge catalog management, photo curation) are meant to be operated by whoever runs
the backend, not by app users. To promote an account:

1. Register normally to create the account.
2. Connect to the database (e.g. `mongosh "mongodb://localhost:27017/wingmark"`, or
   MongoDB Compass/Atlas UI) and run (verifying the email too, if you haven't clicked
   the link):
   ```js
   db.users.updateOne({ email: "you@example.com" }, { $set: { role: "ADMIN", emailVerified: true } })
   ```
3. Log in. The role is read from the database on every request, so an already
   logged-in session picks it up immediately (the admin panel reads the role from the
   token, though, so log in again there).

## API documentation

Interactive Swagger UI is available at `http://localhost:8080/swagger-ui.html` while
the app is running (raw OpenAPI JSON at `/v3/api-docs`).

Deployed: <https://wingmark-backend.onrender.com/swagger-ui.html>

## Endpoints

All endpoints are prefixed with `/api`. 🔒 = requires `Authorization: Bearer <token>`.
🛡️ = requires the caller's account to have the `ADMIN` role (also requires 🔒).

Every table below is followed by a collapsible **Examples** block with the actual
request body (where one exists) and response body, as real JSON. Fields shown as
`null` are legitimately optional; UUIDs/tokens/timestamps in the examples are
placeholders, not real values.

Any error response (4xx/5xx) uses this shape, regardless of endpoint:

```json
{
  "timestamp": "2026-09-20T08:12:45Z",
  "status": 404,
  "error": "Not Found",
  "code": "NOT_FOUND",
  "message": "Species not found with id: 9c858901-8a57-4791-81fe-4c455b099bc9",
  "path": "/api/species/9c858901-8a57-4791-81fe-4c455b099bc9"
}
```

`code` is a stable, machine-readable identifier - **branch on `code`, not on `message`**
(messages are for humans and may change). `validationErrors` is only present for
request-body validation failures (`VALIDATION_FAILED`), as a `{"fieldName": "message"}` map.
A `406` (client refuses JSON) has no body at all.

| Code | Status | When |
|------|--------|------|
| `VALIDATION_FAILED` | 400 | Request-body field validation failed; see `validationErrors` |
| `MALFORMED_REQUEST` | 400 | Unparseable JSON, or a non-multipart request to the upload endpoint |
| `INVALID_PARAMETER` | 400 | Bad/missing query or path param (wrong type, unknown enum, `limit` out of range) |
| `BAD_REQUEST` | 400 | Other invalid values (e.g. a species/badge name without an `en` translation) |
| `OBSERVED_AT_IN_FUTURE` | 400 | A bird log's `observedAt` is more than 5 minutes in the future |
| `SAME_PASSWORD` | 400 | Reset/change password to the current password |
| `WEAK_PASSWORD` | 400 | Password contains the email/username, or is a well-known password (see [Password rules](#password-rules)) |
| `PASSWORD_BREACHED` | 400 | Password appears in a known data breach (Have I Been Pwned) - ask for a different one |
| `INVALID_PROFILE_PICTURE` | 400 | `profilePicture` isn't null, a preset avatar key, or an existing upload |
| `INVALID_BOUNDS` | 400 | Map box out of range or `minLat > maxLat` |
| `INVALID_OR_EXPIRED_CODE` | 400 | Wrong, expired, used, or burned (5 wrong guesses) password-reset code |
| `INVALID_FILE` | 400 | Empty upload, or not a decodable image |
| `UNAUTHENTICATED` | 401 | No/invalid/expired access token - or its account was deleted, un-verified, or had all sessions ended (password change/reset, logout-all) |
| `INVALID_CREDENTIALS` | 401 | Wrong email or password on login |
| `INVALID_OR_EXPIRED_TOKEN` | 401 | Bad refresh token, or bad email-verification link |
| `FORBIDDEN` | 403 | Authenticated but not allowed (e.g. non-admin on an admin endpoint) |
| `EMAIL_NOT_VERIFIED` | 403 | Login/refresh before the email is verified - show the "check your inbox" screen |
| `WRONG_PASSWORD` | 403 | Wrong current password on change-password or delete-account |
| `CANNOT_MODIFY_SELF` | 403/409 | Admin deleting (403) or demoting/un-verifying (409) their own account |
| `NOT_FOUND` | 404 | Resource in the URL doesn't exist or isn't yours; also unknown endpoints |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method for the path |
| `CONFLICT` | 409 | Other conflicts (e.g. duplicate species scientific name) |
| `EMAIL_TAKEN` / `USERNAME_TAKEN` | 409 | Register with an email/username already in use |
| `LAST_ADMIN` | 409 | The only admin tried to delete their own account |
| `FILE_TOO_LARGE` | 413 | Upload over 10MB |
| `REQUEST_TOO_LARGE` | 413 | Non-upload request body over 1MB |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Upload that isn't JPEG/PNG, or a request body in an unsupported content type |
| `INVALID_REFERENCE` | 422 | Body points at a species that doesn't exist (`speciesId`, `favoriteSpeciesId`) |
| `RATE_LIMITED` | 429 | Too many attempts - wait `Retry-After` seconds (see [Rate limits](#rate-limits)) |
| `EXTERNAL_SERVICE_ERROR` | 502 | Xeno-canto / iNaturalist failed or isn't configured |
| `INTERNAL_ERROR` | 500 | Unexpected server error (logged server-side) |

Status codes used across the API:

| Status | When |
|--------|------|
| `400 Bad Request` | Validation failure, malformed JSON, bad query/path param, or an invalid value such as a future `observedAt` |
| `401 Unauthorized` | Missing/invalid/expired access token - **or** a token whose account has since been deleted or un-verified, or whose sessions were ended by a password change/reset or logout-all (checked on every request) |
| `403 Forbidden` | Authenticated but not allowed: non-admin on an admin endpoint, unverified email on login/refresh, wrong password on change-password/account deletion |
| `404 Not Found` | The resource in the URL doesn't exist - or belongs to another user (never 403, so ids aren't confirmed) |
| `409 Conflict` | Duplicate email/username/scientific name, or a state conflict (the last admin deleting themselves, an admin demoting/un-verifying themselves) |
| `413 Payload Too Large` | Upload over 10MB, or any other request body over 1MB |
| `415 Unsupported Media Type` | Upload that isn't a JPEG/PNG image |
| `422 Unprocessable Entity` | The URL was found but the body points at something that doesn't exist, e.g. an unknown `speciesId` / `favoriteSpeciesId` |
| `429 Too Many Requests` | Rate limit hit (`RATE_LIMITED`); the `Retry-After` header says how many seconds to wait |

#### Password rules

Enforced by the server on register, reset-password and change-password; the app should
mirror them for instant feedback, but the server's check is the one that counts:
- 10-72 characters, with at least one letter and one digit (`400 VALIDATION_FAILED`).
  72 is bcrypt's limit, since it ignores anything longer.
- Must not contain the email's name part or the username (`400 WEAK_PASSWORD`).
- Must not be a well-known password like `Password123` (`400 WEAK_PASSWORD`).
- Must not appear in a known data breach (`400 PASSWORD_BREACHED`). This is checked with Have I
  Been Pwned's k-anonymity range API: only the first 5 characters of the password's SHA-1
  leave the server. If the API is down, the check is skipped rather than blocking signups.

Existing passwords keep working; the rules apply when a password is set.

#### Rate limits

Per account (email or user) **and** per client IP, counted whether or not the attempt succeeds.
The same limits apply whether or not the email exists:

| Endpoint | Limit |
|---|---|
| `POST /api/auth/login` | 10 per account and 50 per IP / 15 min |
| `POST /api/auth/register` | 10 per IP / hour |
| `POST /api/auth/forgot-password`, `/resend-verification-email` | 3 per email and 20 per IP / hour (each) |
| `POST /api/auth/reset-password` | 10 per account and 30 per IP / 15 min (plus the 5-wrong-guesses limit per code) |
| `POST /api/auth/refresh` | 120 per IP / 15 min |
| `POST /api/users/{id}/password`, `DELETE /api/users/{id}` | 10 per user / 15 min |
| Everything under `/api/` | 600 per IP / minute |

Over the limit → `429 RATE_LIMITED` with `Retry-After`. Counters are in memory, which is
right for one instance; more instances would need a shared store (e.g. Redis). All limits
are configurable under `wingmark.rate-limit.*`.

### Auth (`/api/auth`)

| Method | Path                          | Auth | Description                                                          |
|--------|-------------------------------|:----:|-------------------------------------------------------------------------|
| POST   | `/register`                   |      | Create an unverified account and email a verification link. **No tokens** |
| POST   | `/login`                      |      | Log in, returns a fresh token pair. Rejected (403) until the email is verified |
| POST   | `/refresh`                    |      | Exchange a refresh token for a new token pair. Rejected (403) if the email is no longer verified |
| POST   | `/logout`                     |      | Revoke one refresh token                                                 |
| POST   | `/logout-all`                 | 🔒  | Sign out everywhere: revoke every refresh token and invalidate every issued access token |
| POST   | `/forgot-password`            |      | Email a 6-digit reset code (if the email exists)                         |
| POST   | `/reset-password`             |      | Consume the emailed code to set a new password (signs out every session)  |
| GET    | `/verify-email?token=`        |      | Consumes the link from the verification email (opened in a browser, not called by the app) |
| POST   | `/resend-verification-email`  |      | Re-sends the verification email for a given address; no-ops if unknown/already verified |

Registration does **not** return tokens: the new account can't use the API at all until
its email is verified. The app should show a "check your email" screen after signup,
then call `/login` once the link has been clicked - `/login` (and `/refresh`) answer 403
until then. Access tokens are also re-checked against the database on every request, so
a token stops working immediately if its account is deleted or un-verified, and role
changes apply on the next request rather than when the token expires. The verification token expires after
24h; `resend-verification-email` is deliberately unauthenticated (same shape as
`forgot-password`, taking just an email) rather than requiring a token, since an
unverified account that lost its session (app reinstalled, storage cleared, or its
refresh token itself expired/was revoked before the link was used) has no other way to
prove it owns the address and get back in.

<details>
<summary><strong>Examples</strong></summary>

**POST `/register`** → `201 Created`

Request:
```json
{
  "email": "amelia@example.com",
  "password": "correcthorse8",
  "username": "amelia_birds",
  "firstName": "Amelia",
  "lastName": "Rivera",
  "acceptedTermsVersion": "2026-10-01",
  "acceptedPrivacyVersion": "2026-10-01",
  "confirmedAge13": true
}
```

`confirmedAge13` is **required and must be `true`**: the user confirms they are at least the minimum age
(13, `minimumAge` in [`GET /api/legal`](#legal-apilegal)). Missing or `false` gives `400 AGE_NOT_CONFIRMED` and
nothing is created. The confirmation is stored as a consent record of type `AGE` whose version is the minimum age.

`acceptedTermsVersion` / `acceptedPrivacyVersion` are the exact versions from
[`GET /api/legal`](#legal-apilegal) that the user ticked "I accept" for. They're **required
once that document is published** (`400 TERMS_NOT_ACCEPTED` / `PRIVACY_NOT_ACCEPTED`
otherwise, and nothing is created) and ignored while it isn't. Each acceptance is stored
as a consent record (type, version, timestamp).

Response:
```json
{
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "email": "amelia@example.com",
  "username": "amelia_birds",
  "emailVerified": false,
  "message": "Account created. Check your inbox for a verification link, then log in."
}
```

`409` if the email or username is taken.

**POST `/login`** → `200 OK` (`401` wrong email/password, `403` email not verified yet)

Request:
```json
{
  "email": "amelia@example.com",
  "password": "correcthorse8"
}
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIz...",
  "refreshToken": "8f14e45fceea167a5a36dedd4bea2543",
  "expiresInMs": 900000,
  "pendingConsents": []
}
```

`pendingConsents` lists legal documents (`TERMS`, `PRIVACY`) whose **current** version the
user hasn't accepted yet, e.g. after the terms changed. When it's non-empty, show the
acceptance screen and call `POST /api/users/{id}/consents`. It's always empty while no
document is published. `/refresh` and change-password return the same shape.

**POST `/refresh`** → `200 OK`

Request:
```json
{ "refreshToken": "8f14e45fceea167a5a36dedd4bea2543" }
```

Response: same shape as `/login` (a new token pair, old refresh token revoked). `401` if
the refresh token is unknown/expired/revoked or its account was deleted; `403` if the
account's email is no longer verified.

**POST `/logout`** → `204 No Content`

Request: same body as `/refresh`. No response body.

**POST `/logout-all`** → `204 No Content`. No request body, no response body. Every
refresh token is revoked **and** every access token issued so far (including the one
used for this call) stops working immediately.

**POST `/forgot-password`** → `202 Accepted`

Request:
```json
{ "email": "amelia@example.com" }
```

No response body. If the account exists, a **6-digit code** is emailed (valid 15 minutes,
single use). Requesting again replaces the previous code; a request within 60 seconds of
the last code sends nothing. The response is `202` in every case, so it never reveals
whether the email is registered.

**POST `/reset-password`** → `204 No Content`

Request:
```json
{
  "email": "amelia@example.com",
  "code": "482913",
  "newPassword": "newSecret9"
}
```

No response body. Every existing session (refresh tokens and access tokens) is signed
out; log in with the new password. Errors: `400 INVALID_OR_EXPIRED_CODE` (wrong, expired
or already-used code, or unknown email - deliberately indistinguishable; the code is
burned after 5 wrong guesses, so request a new one), `400 SAME_PASSWORD`,
`400 VALIDATION_FAILED` (code not 6 digits, weak password).

**GET `/verify-email?token=b1946ac9...`** → `200 OK`, `Content-Type: text/html`. No
JSON - it's an HTML page meant to be opened directly from the email link:
```html
<p>Your email is verified. You can close this page and log in.</p>
```

**POST `/resend-verification-email`** → `202 Accepted`

Request:
```json
{ "email": "amelia@example.com" }
```

No response body.

</details>

### Users (`/api/users/{userId}`) — 🔒, `userId` must be the caller's own id

| Method | Path         | Description                                  |
|--------|--------------|-----------------------------------------------|
| GET    | ``           | Get the caller's profile                       |
| PUT    | ``           | Update the caller's profile                    |
| GET    | `/settings`  | Get the caller's app settings                  |
| PUT    | `/settings`  | Update the caller's app settings               |
| POST   | `/password`  | Change the caller's password (current password required); returns a fresh token pair |
| GET    | `/consents`  | The caller's legal-document acceptances (type, version, acceptedAt), oldest first |
| POST   | `/consents`  | Accept the current version of a document: `{"type":"TERMS","version":"2026-10-01"}` → `{"pendingConsents":[...]}`; `400 CONSENT_VERSION_MISMATCH` for any other version |
| GET    | `/export`    | Download everything Wingmark holds about the caller (profile, settings, every bird log, badge progress, consent history) as `wingmark-data-export.json` |
| DELETE | ``           | Permanently delete the caller's account and all of its data (password required) |

A `favoriteSpeciesId` that doesn't exist is rejected with `422 INVALID_REFERENCE`. A
`userId` other than the caller's own returns `404`.

`profilePicture` must be one of:
- `null` (or blank) - no picture;
- a preset avatar key from [`GET /api/avatars`](#avatars-apiavatars) (`avatar-1` ... `avatar-12`) - the image ships in the app;
- a `/uploads/...` URL returned by `POST /api/uploads/photo` (relative, exactly as returned) that still exists.

Anything else is `400 INVALID_PROFILE_PICTURE`. Re-sending the value already stored is
always accepted.

`favoriteSpeciesName` is resolved server-side from `Accept-Language` on every request
(same mechanism as `speciesCommonName` on bird logs) - it is not stored, only
`favoriteSpeciesId` is.

<details>
<summary><strong>Examples</strong></summary>

**GET `` (profile)** → `200 OK`

No request body.

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "email": "amelia@example.com",
  "username": "amelia_birds",
  "firstName": "Amelia",
  "lastName": "Rivera",
  "profilePicture": "avatar-3",
  "favoriteSpeciesId": "9c858901-8a57-4791-81fe-4c455b099bc9",
  "favoriteSpeciesName": "House Sparrow",
  "role": "USER",
  "emailVerified": true,
  "createdAt": "2026-01-14T10:32:00Z"
}
```

**PUT `` (update profile)** → `200 OK`

Request:
```json
{
  "firstName": "Amelia",
  "lastName": "Rivera",
  "profilePicture": "avatar-3",
  "favoriteSpeciesId": "9c858901-8a57-4791-81fe-4c455b099bc9"
}
```

Response: same shape as GET above.

**GET `/settings`** → `200 OK`

No request body.

```json
{ "unitPreference": "METRIC", "locale": "en" }
```

**PUT `/settings`** → `200 OK`

Request:
```json
{ "unitPreference": "IMPERIAL", "locale": "en" }
```

Response: same shape as GET above.

**POST `/password` (change password)** → `200 OK`

Request:
```json
{ "currentPassword": "correcthorse8", "newPassword": "newSecret9" }
```

Response: a fresh token pair (same shape as `/api/auth/login`) - store it, since every
other session, **including the access token used for this call**, is signed out.
Errors: `403 WRONG_PASSWORD`, `400 SAME_PASSWORD`, `400 VALIDATION_FAILED`
(`newPassword` must follow the [password rules](#password-rules): `400 WEAK_PASSWORD` /
`PASSWORD_BREACHED` too). A "your password was changed" email goes to the account, as it
does after a reset.

**DELETE `` (delete own account)** → `204 No Content`

Request:
```json
{ "password": "correcthorse8" }
```

Deletes the account and everything tied to it: bird logs, badge progress, settings,
refresh / password-reset / verification tokens, and uploaded photos (the profile
picture and log photos - unless another account or a species image still points at the
same file). Every token the user holds stops working immediately. `403` if the password
is wrong, `409` if the caller is the only admin left. Cannot be undone.

</details>

### Admin - Users (`/api/admin/users`) — 🛡️, all endpoints

| Method | Path      | Description                                                              |
|--------|-----------|-----------------------------------------------------------------------------|
| GET    | ``        | Get every registered user account                                          |
| PUT    | `/{id}`   | Update a user's name, role, email-verified flag, and favorite species       |
| DELETE | `/{id}`   | Delete a user account and all of its data (an admin cannot delete their own account this way) |

Role and verification changes take effect on the user's very next request. An admin
can't change their own role or un-verify themselves (`409`), since that would lock their
own session out; `favoriteSpeciesId` pointing at no species is `422`.

<details>
<summary><strong>Examples</strong></summary>

**GET ``** → `200 OK`. No request body.

```json
[
  {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "email": "amelia@example.com",
    "username": "amelia_birds",
    "firstName": "Amelia",
    "lastName": "Rivera",
    "profilePicture": "avatar-3",
    "favoriteSpeciesId": "9c858901-8a57-4791-81fe-4c455b099bc9",
    "favoriteSpeciesName": "House Sparrow",
    "role": "USER",
    "emailVerified": true,
    "createdAt": "2026-01-14T10:32:00Z"
  }
]
```

**PUT `/{id}`** → `200 OK`

Request:
```json
{
  "firstName": "Amelia",
  "lastName": "Rivera",
  "role": "ADMIN",
  "emailVerified": true,
  "favoriteSpeciesId": "9c858901-8a57-4791-81fe-4c455b099bc9",
  "profilePicture": "avatar-3"
}
```

A **full replacement**: omitted `firstName`, `lastName`, `favoriteSpeciesId` or
`profilePicture` are cleared, so send the current values for anything you're not
changing. `profilePicture` follows the same rules as the user's own profile update (set it
to `null` to remove an inappropriate photo).

Response: a single user object, same shape as one entry of the GET list above.

**DELETE `/{id}`** → `204 No Content`. No request or response body. Removes the same
data as the self-service account deletion above, and the user is signed out
immediately. `403` when targeting your own account.

</details>

### Admin - Metrics (`/api/admin/metrics`) — 🛡️

| Method | Path | Description                                                                 |
|--------|------|---------------------------------------------------------------------------------|
| GET    | ``   | Aggregate usage metrics for the admin panel's Metrics tab, computed fresh on every call (no caching - re-calling this endpoint *is* the "recalculate" action) |

`favoriteSpecies` and `badgeCompletions` names are resolved from `Accept-Language`, same
as elsewhere. `topRegions` groups bird logs into a ~11km latitude/longitude grid cell
(`"{lat}, {lng}"`, both rounded to 1 decimal) rather than the free-text `locationName`
field, since that's whatever the user typed (e.g. "home") and isn't a reliable place
label. `localeUsage.locale` is `"en"`/`"tr"`/another 2-letter code, or `"unset"` if the
user never saved a language in their settings.

<details>
<summary><strong>Examples</strong></summary>

**GET ``** → `200 OK`. No request body.

```json
{
  "totalUsers": 42,
  "favoriteSpecies": [
    { "speciesId": "9c858901-8a57-4791-81fe-4c455b099bc9", "speciesName": "House Sparrow", "userCount": 12 },
    { "speciesId": "1b2c3d4e-5f6a-7b8c-9d0e-1f2a3b4c5d6e", "speciesName": "European Robin", "userCount": 7 }
  ],
  "badgeCompletions": [
    { "badgeId": "72cf1811-0625-4933-afc3-b18f4bc18805", "badgeName": "First Flight", "earnedCount": 30 },
    { "badgeId": "5d443444-2256-4385-a6a5-8b8094c435f2", "badgeName": "Rare Sighting", "earnedCount": 0 }
  ],
  "topRegions": [
    { "region": "40.8, -74.0", "logCount": 58 },
    { "region": "37.8, -122.5", "logCount": 21 }
  ],
  "localeUsage": [
    { "locale": "unset", "userCount": 30 },
    { "locale": "en", "userCount": 8 },
    { "locale": "tr", "userCount": 4 }
  ],
  "calculatedAt": "2026-09-20T08:12:45Z"
}
```

</details>

### Bird Logs (`/api/bird-logs`) — 🔒 unless noted

| Method | Path              | Auth | Description                                                      |
|--------|-------------------|:----:|--------------------------------------------------------------------|
| GET    | ``                | 🛡️  | Get every log across every user, filterable/sortable (see below) - there's no per-user viewing feature for this yet, so it's admin-only for now |
| GET    | `/user/{userId}`  | 🔒*  | Get all of this user's own logs, filterable/sortable (see below) - admins can pass any user's id, for the admin panel's user detail view |
| GET    | `/location`       |      | Get the caller's logs inside the visible map region, filterable and capped (see below) |
| GET    | `/{id}`           |      | Get one of the caller's logs by id                                  |
| POST   | ``                |      | Create a log (re-evaluates badge progress)                          |
| PUT    | `/{id}`           |      | Update a log (re-evaluates badge progress)                          |
| DELETE | `/{id}`           |      | Delete a log (re-evaluates badge progress)                          |

\* `userId` must be the caller's own id, unless the caller is an admin.

**GET `` and GET `/user/{userId}` both take the same optional query params:**

| Param           | Values                     | Effect                                                    |
|-----------------|----------------------------|------------------------------------------------------------|
| `hasSpecies`    | `true` \| `false`          | Only logs with/without a species selected. Omit for no filter. |
| `gender`        | `MALE` \| `FEMALE` \| `UNKNOWN` | Only logs with this gender. Omit for no filter. Exact case required. |
| `lifeStage`     | `ADULT` \| `BABY` \| `UNKNOWN`  | Only logs with this life stage. Omit for no filter. Exact case required. |
| `sortDirection` | `ASC` \| `DESC`            | Sort by `observedAt`. Defaults to `DESC` (most recent first) if omitted. Exact case required. |

All four can be combined, e.g. `GET /api/bird-logs/user/{userId}?hasSpecies=false&gender=FEMALE&sortDirection=ASC`.

**GET `/location`** - call it whenever the visible map region changes:

| Param | Values | Effect |
|-------|--------|--------|
| `minLat`, `maxLat` | `-90` .. `90`, `minLat <= maxLat` | Required. Latitude range of the box. |
| `minLng`, `maxLng` | `-180` .. `180` | Required. If `minLng > maxLng` the box **crosses the antimeridian** (e.g. `minLng=170&maxLng=-170`). |
| `hasSpecies`, `gender`, `lifeStage` | as above | Optional, same filters as the diary list. |
| `limit` | `1` .. `1000`, default `500` | Maximum logs returned, most recently observed first. |

The body is a plain array (same shape as below). The response header
**`X-Result-Truncated: true`** means more logs matched than `limit` - zoom in or raise
the limit; `false` means you have everything in the box. Out-of-range bounds or
`minLat > maxLat` are `400 INVALID_BOUNDS`; a bad `limit` is `400 INVALID_PARAMETER`.

<details>
<summary><strong>Examples</strong></summary>

**GET ``, GET `/user/{userId}`, GET `/location`** → `200 OK`, each returning an array of
this shape. No request body.

```json
[
  {
    "id": "b1e2c3d4-1234-4a5b-8c9d-0e1f2a3b4c5d",
    "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "speciesId": "9c858901-8a57-4791-81fe-4c455b099bc9",
    "speciesCommonName": "House Sparrow",
    "speciesStatus": "CONFIDENT",
    "pet": false,
    "customName": null,
    "lifeStage": "ADULT",
    "gender": "MALE",
    "photoUrl": "/uploads/abc123.jpg",
    "note": "Singing on the fence at sunrise",
    "latitude": 40.7829,
    "longitude": -73.9654,
    "locationName": "Central Park",
    "observedAt": "2026-09-18T06:45:00Z",
    "visibility": "PRIVATE",
    "createdAt": "2026-09-18T06:47:12Z"
  }
]
```

**GET `/{id}`** → `200 OK`. No request body. Response: a single object, same shape as
one array entry above.

**POST ``** → `201 Created`

Request:
```json
{
  "speciesId": "9c858901-8a57-4791-81fe-4c455b099bc9",
  "speciesStatus": "CONFIDENT",
  "pet": false,
  "customName": null,
  "lifeStage": "ADULT",
  "gender": "MALE",
  "photoUrl": "/uploads/abc123.jpg",
  "note": "Singing on the fence at sunrise",
  "latitude": 40.7829,
  "longitude": -73.9654,
  "locationName": "Central Park",
  "observedAt": "2026-09-18T06:45:00Z"
}
```

`observedAt` is when the bird was actually seen (ISO-8601; an offset like
`2026-09-18T09:45:00+03:00` also works and is stored as UTC). A `speciesId` that
doesn't exist is rejected with `422`. `observedAt` is optional - omit it
and the upload time is used. A time more than 5 minutes in the future is rejected with
`400 Bad Request`.

Response: same shape as one GET entry above.

**PUT `/{id}`** → `200 OK`. Request: same shape as POST, except that omitting
`observedAt` keeps the log's existing sighting time rather than resetting it. Response:
same shape as GET.

**DELETE `/{id}`** → `204 No Content`. No request or response body.

</details>

### Species guide (`/api/species`)

| Method | Path                      | Auth | Description                                                                 |
|--------|---------------------------|:----:|-------------------------------------------------------------------------------|
| GET    | ``                        |      | Paginated species list (`?page=`/`?size=`), optional `?search=` by common name; returns `{content: [...], page: {size, number, totalElements, totalPages}}` |
| GET    | `/{id}`                   |      | Get one species by id, with its reference images                               |
| GET    | `/{id}/sound`             |      | Live-fetch call/song recordings from Xeno-canto, capped at 5 per species        |
| POST   | ``                        | 🛡️  | Create a species                                                               |
| PUT    | `/{id}`                   | 🛡️  | Update a species                                                               |
| DELETE | `/{id}`                   | 🛡️  | Delete a species (and its images)                                              |
| POST   | `/{id}/images`            | 🛡️  | Attach a curated reference image (life stage + gender)                         |
| GET    | `/{id}/photo-candidates`  | 🛡️  | **Curation tool** — searches iNaturalist for candidate photos (`?lifeStage=ADULT\|BABY&gender=MALE\|FEMALE\|NOT_APPLICABLE`) to review and save via the endpoint above. Does not save anything itself. |
| DELETE | `/{id}/images/{imageId}`  | 🛡️  | Remove a reference image                                                       |

**Sorting** the list uses the standard `?sort=<field>,<asc|desc>` param:

| `?sort=` value            | Sorts by                          |
|----------------------------|------------------------------------|
| `commonName.en,asc`        | Common name (English), A → Z       |
| `commonName.tr,asc`        | Common name (Turkish), A → Z       |
| `scientificName,asc`       | Scientific/species name, A → Z     |

Any of these also accepts `desc` for the reverse order (e.g. `commonName.tr,desc`). Multiple
`?sort=` params can be combined for a tiebreaker, same as any Spring Data `Pageable`.

<details>
<summary><strong>Examples</strong></summary>

**GET ``** → `200 OK`. No request body.

```json
{
  "content": [
    {
      "id": "9c858901-8a57-4791-81fe-4c455b099bc9",
      "commonName": { "en": "House Sparrow", "tr": "Serçe" },
      "scientificName": "Passer domesticus",
      "family": "Passeridae",
      "order": "Passeriformes",
      "description": { "en": "A small, plump bird common in urban areas." },
      "lifespan": { "en": "3 years average" },
      "diet": { "en": "Seeds, grains, insects" },
      "habitat": { "en": "Urban and suburban areas" },
      "sizeDescription": { "en": "14-18 cm" },
      "conservationStatus": { "en": "Least Concern" },
      "nativeRange": { "en": "Europe, Asia, North Africa" },
      "images": [
        {
          "id": "1a2b3c4d-1234-4a5b-8c9d-0e1f2a3b4c5d",
          "lifeStage": "ADULT",
          "gender": "MALE",
          "imageUrl": "/uploads/sparrow-male.jpg",
          "caption": "Adult male"
        }
      ]
    }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 143, "totalPages": 8 }
}
```

**GET `/{id}`** → `200 OK`. No request body. Response: a single species object, same
shape as one `content` entry above.

**GET `/{id}/sound`** → `200 OK`. No request body.

```json
[
  {
    "id": "XC123456",
    "recordingUrl": "https://xeno-canto.org/123456/download",
    "type": "song",
    "quality": "A",
    "recordist": "Jane Birder",
    "licenseUrl": "https://creativecommons.org/licenses/by-nc-sa/4.0/"
  }
]
```

**POST ``** → `201 Created`

Request:
```json
{
  "commonName": { "en": "House Sparrow", "tr": "Serçe" },
  "scientificName": "Passer domesticus",
  "family": "Passeridae",
  "order": "Passeriformes",
  "description": { "en": "A small, plump bird common in urban areas." },
  "lifespan": { "en": "3 years average" },
  "diet": { "en": "Seeds, grains, insects" },
  "habitat": { "en": "Urban and suburban areas" },
  "sizeDescription": { "en": "14-18 cm" },
  "conservationStatus": { "en": "Least Concern" },
  "nativeRange": { "en": "Europe, Asia, North Africa" }
}
```

Response: same shape as one `content` entry above, with `"images": []`.

**PUT `/{id}`** → `200 OK`. Request: same shape as POST. Response: same shape as GET.

**DELETE `/{id}`** → `204 No Content`. No request or response body.

**POST `/{id}/images`** → `201 Created`

Request:
```json
{
  "lifeStage": "ADULT",
  "gender": "MALE",
  "imageUrl": "/uploads/sparrow-male.jpg",
  "caption": "Adult male",
  "licenseCode": null,
  "attribution": null,
  "sourceUrl": null
}
```

Response:
```json
{
  "id": "1a2b3c4d-1234-4a5b-8c9d-0e1f2a3b4c5d",
  "lifeStage": "ADULT",
  "gender": "MALE",
  "imageUrl": "/uploads/sparrow-male.jpg",
  "caption": "Adult male",
  "licenseCode": null,
  "attribution": null,
  "sourceUrl": null
}
```

For a third-party photo (an iNaturalist candidate), send its `licenseCode` (e.g.
`cc-by`), `attribution` and `sourceUrl` (the observation page). Every species image in the
API carries these three fields; **the app must show the attribution under photos that have
a `licenseCode`**, since Creative Commons licenses require crediting the author. They're null
for photos you uploaded yourself. Photo candidates only include openly licensed
photos; "all rights reserved" photos are filtered out.

**GET `/{id}/photo-candidates?lifeStage=ADULT&gender=MALE`** → `200 OK`. No request body.

```json
[
  {
    "observationId": "123456789",
    "photoUrl": "https://static.inaturalist.org/photos/123456/medium.jpg",
    "licenseCode": "cc-by-nc",
    "attribution": "(c) Jane Birder, some rights reserved",
    "observationUrl": "https://www.inaturalist.org/observations/123456789"
  }
]
```

**DELETE `/{id}/images/{imageId}`** → `204 No Content`. No request or response body.

</details>

### Badges (`/api/badges`)

| Method | Path              | Auth | Description                                                     |
|--------|-------------------|:----:|---------------------------------------------------------------------|
| GET    | `/catalog`        |      | Get every badge definition (name, icon, criteria)                    |
| GET    | `/user/{userId}`  | 🔒*  | Get every badge with this user's progress/earned status (admins can pass any user's id) |
| POST   | ``                | 🛡️  | Create a new badge definition                                        |
| PUT    | `/{id}`           | 🛡️  | Update an existing badge definition                                  |
| DELETE | `/{id}`           | 🛡️  | Delete a badge definition                                            |

\* `userId` must be the caller's own id, unless the caller is an admin.

#### Badge criteria types

A badge's `criteriaType` decides how its progress is computed against a user's bird
logs; `criteriaValue` is the target the progress must reach to be earned. A few types
also need extra parameters in `criteriaMetadata`:

| Criteria type          | Progress = ...                                          | `criteriaMetadata` |
|-------------------------|----------------------------------------------------------|---------------------|
| `TOTAL_LOGS`             | Total bird logs                                           | —                    |
| `UNIQUE_SPECIES`         | Distinct species logged                                    | —                    |
| `BABY_LOGS`              | Logs with life stage `BABY`                                | —                    |
| `UNKNOWN_SPECIES_LOGS`   | Logs with no species selected                               | —                    |
| `PET_LOGS`               | Logs marked as a pet                                        | —                    |
| `SPECIES_IN_RADIUS`      | Max distinct species clustered within a radius               | `{ "radiusMeters": <number> }` (default 5000) |
| `SIGHTINGS_IN_RADIUS`    | Max raw sightings (any species) clustered within a radius     | `{ "radiusMeters": <number> }` (default 5000) |
| `SPECIES_LOGS`           | Logs of one specific species                                 | `{ "speciesId": "<uuid>" }` |
| `FAVORITE_SPECIES_LOGS`  | Logs of the user's favorite species; 0 without one. `/user/{userId}` omits these badges for users who have no favorite species | — |
| `SAME_GENUS_SPECIES`     | Max distinct species logged within one genus (pet logs count), the first word of the scientific name, case-insensitive | optional `{ "genus": "Passer" }`: only that genus counts; a blank or non-text value is rejected with 400 |

The admin panel's badge form exposes all of these, including the species picker for
`SPECIES_LOGS`, the radius field for the two `*_IN_RADIUS` types and the optional genus
field for `SAME_GENUS_SPECIES`.

<details>
<summary><strong>Examples</strong></summary>

**GET `/catalog`** → `200 OK`. No request body.

```json
[
  {
    "id": "72cf1811-0625-4933-afc3-b18f4bc18805",
    "name": { "en": "First Flight", "tr": "İlk Uçuş" },
    "description": { "en": "Log your first bird sighting" },
    "icon": "first-flight",
    "criteriaType": "TOTAL_LOGS",
    "criteriaValue": 1,
    "criteriaMetadata": null,
    "tier": "BRONZE",
    "displayOrder": 1
  }
]
```

**GET `/user/{userId}`** → `200 OK`. No request body.

```json
[
  {
    "badgeId": "72cf1811-0625-4933-afc3-b18f4bc18805",
    "badgeName": "First Flight",
    "badgeIcon": "first-flight",
    "earned": true,
    "earnedAt": "2026-09-18T06:47:12Z",
    "progress": 1,
    "targetValue": 1
  }
]
```

**POST ``** → `201 Created`

Request:
```json
{
  "name": { "en": "First Flight", "tr": "İlk Uçuş" },
  "description": { "en": "Log your first bird sighting" },
  "icon": "first-flight",
  "criteriaType": "TOTAL_LOGS",
  "criteriaValue": 1,
  "criteriaMetadata": null,
  "tier": "BRONZE",
  "displayOrder": 1
}
```

`displayOrder` is optional (0 or more). `/catalog` and `/user/{userId}` list badges by it, lowest first; badges without one come last.

Response: same shape as one `/catalog` entry above.

**PUT `/{id}`** → `200 OK`. Request: same shape as POST. Response: same shape as GET.

**DELETE `/{id}`** → `204 No Content`. No request or response body.

</details>

### Uploads (`/api/uploads`) — 🔒

| Method | Path      | Description                                                              |
|--------|-----------|-----------------------------------------------------------------------------|
| POST   | `/photo`  | Upload a JPEG or PNG (multipart `file`, max 10MB), returns its URL for a log's `photoUrl` or a custom `profilePicture`. Re-encoded server-side to strip EXIF metadata (including GPS). |

<details>
<summary><strong>Examples</strong></summary>

**POST `/photo`** → `201 Created`. Request is `multipart/form-data`, not JSON - one
part named `file` (the image). Response:

```json
{ "url": "/uploads/3f2b9c1e-6d0a-4f7e-9b1a-2c4d5e6f7a8b.jpg" }
```

The URL is always **relative** to this backend (`/uploads/<uuid>.<ext>`). Store it as-is
in `photoUrl` / `profilePicture`, and prefix the backend's base URL only when loading
the image (e.g. `https://wingmark-backend.onrender.com/uploads/...`). `GET /uploads/{file}`
is public, returns the image with a long-lived immutable `Cache-Control`, and `404 NOT_FOUND`
if it doesn't exist. Images are stored in the database and downscaled to at most 1600px on
the longest side.

Only `image/jpeg` and `image/png` are accepted - anything else (including HEIC and WebP)
is `415 UNSUPPORTED_MEDIA_TYPE`, so on iOS convert with `UIImage.jpegData(...)` first. An
empty or undecodable image is `400 INVALID_FILE`, over 10MB is `413 FILE_TOO_LARGE`, and a
non-multipart request is `400 MALFORMED_REQUEST`.

</details>

### Legal (`/api/legal`)

| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| GET    | ``   |      | Current Terms of Service / Privacy Policy versions and URLs |

```json
{ "termsVersion": "2026-10-01", "termsUrl": "https://...", "privacyVersion": "2026-10-01", "privacyUrl": "https://...", "minimumAge": 13 }
```

`minimumAge` is the age users confirm at signup. Existing users without that confirmation get `AGE` in their
`pendingConsents` on their next login and clear it with `POST /api/users/{id}/consents {"type":"AGE","version":"13"}`.

A `null` version means that document isn't published yet, so no acceptance is needed.
Versions are set by the operator with `TERMS_VERSION` / `TERMS_URL` / `PRIVACY_VERSION` /
`PRIVACY_URL`. Setting or bumping one makes signup require it and puts it in existing
users' `pendingConsents` on their next login.

### Avatars (`/api/avatars`)

| Method | Path | Auth | Description |
|--------|------|:----:|-------------|
| GET    | ``   |      | List the preset avatar keys a `profilePicture` may be set to |

<details>
<summary><strong>Examples</strong></summary>

**GET ``** → `200 OK`. No request body. Public.

```json
[ { "key": "avatar-1" }, { "key": "avatar-2" }, "...", { "key": "avatar-12" } ]
```

Only keys are served - the images ship inside the app, named by key. New avatars are
only ever appended; an existing key is never renamed or removed.

</details>

## Design notes

- **Ownership scoping, not just permission checks**: bird logs and user profiles are
  looked up by `(id, ownerId)` in the same query, not fetched then checked — so another
  user's id returns a plain 404, never a 403 that would confirm it exists.
- **Tokens are re-checked against the database**: the JWT filter loads the user on every
  authenticated request, so deleting or un-verifying an account, or changing its role,
  takes effect immediately instead of when the 15-minute access token expires. Each
  access token also carries the user's `tokenVersion`; password change/reset and
  logout-all bump it, which ends every already-issued session at once.
- **Password reset uses a short emailed code, not a link**: the app just needs a text
  field, no universal links. A 6-digit code is only safe with limits, so it expires in 15
  minutes, is single use, is burned after 5 wrong guesses, and is hashed with the user's
  id (codes can repeat across users).
- **Legacy timestamps are backfilled on startup**: documents saved before auditing worked
  have no `createdAt` (and very old bird logs no `observedAt`). `LegacyTimestampBackfill`
  fills `createdAt` from the earliest timestamp the document already has (or now), and a
  bird log's `observedAt` from its `createdAt` - only where missing, so it's safe to rerun.
- **Account deletion is a full cascade**: the user document is removed first (cutting
  off access), then everything keyed by their id, then their uploaded files - skipping
  any file another account or a species image still references, since upload URLs aren't
  owner-tagged.
  Consent records go too. What remains is a single `account_deletions` audit entry
  (opaque user id, SELF/ADMIN, requested/completed times, no personal data), and the
  former account holder gets a confirmation email.
- **Expired tokens delete themselves**: refresh, password-reset and email-verification
  tokens have a TTL index on `expiresAt`, so MongoDB removes each one once it can no
  longer be used, instead of keeping them forever.
- **Hardening for App Store review** (`docs/appstore/05-backend-tasks.md`):
  - rate limits and the password rules above;
  - "password changed" emails after a reset or change;
  - a strict Content-Security-Policy on the admin panel (no inline scripts or styles);
  - CORS closed to other origins;
  - non-upload bodies capped at 1MB;
  - every admin change logged as `ADMIN_AUDIT admin=<id> <METHOD> <path> -> <status>`;
  - the species search escapes its input before it reaches `$regex`;
  - a deployed server (non-localhost `APP_BASE_URL`) refuses to start on the public
    development `JWT_SECRET`.
- **Badges recompute on every log change**: create/update/delete a bird log and every
  badge's progress is recalculated for that user in the same request — no background
  job, no separate "sync" step.
- **Species photos and sounds are handled differently on purpose**: sounds are
  fetched live from Xeno-canto on every request, since any call/song recording for a
  species is as good as any other. Photos are curated once by an admin (via the
  iNaturalist-backed `photo-candidates` endpoint) and stored, since a specific
  crowd-sourced photo needs a human to check it's actually a good, correctly-labeled
  picture before it goes in the guide.
