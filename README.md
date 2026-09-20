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
the verification email fails to send silently (logged as an error, with the raw link
included so you can still click through it locally) - see
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
   - `XENO_CANTO_API_KEY` — optional, only needed for the sound-recordings endpoint.
   - `JWT_SECRET` is generated automatically by Render on first deploy; everything else
     (`UPLOAD_DIR`, `SMTP_PORT`, port, JWT expirations) is already wired to sensible
     defaults in `render.yaml`/`application.yml`.
4. After the first deploy, confirm the assigned URL matches `APP_BASE_URL` in
   `render.yaml` (`https://wingmark-backend.onrender.com` by default) - Render appends
   a random suffix if that name is already taken elsewhere. Update the env var in the
   dashboard if it differs; it's baked into every verification-email link.

The `/uploads/**` static route serves whatever `UPLOAD_DIR` points at, so on Render it
reads from the mounted disk (`/data/uploads`) — without a disk, uploaded photos would be
wiped on every deploy/restart, since Render's container filesystem is otherwise ephemeral.

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

1. Register/log in normally to create the account.
2. Connect to the database (e.g. `mongosh "mongodb://localhost:27017/wingmark"`, or
   MongoDB Compass/Atlas UI) and run:
   ```js
   db.users.updateOne({ email: "you@example.com" }, { $set: { role: "ADMIN" } })
   ```
3. Log in again — the role is baked into the JWT at login time, so you need a fresh
   token after the change.

## API documentation

Interactive Swagger UI is available at `http://localhost:8080/swagger-ui.html` while
the app is running (raw OpenAPI JSON at `/v3/api-docs`).

Deployed: <https://wingmark-backend.onrender.com/swagger-ui.html>

## Endpoints

All endpoints are prefixed with `/api`. 🔒 = requires `Authorization: Bearer <token>`.
🛡️ = requires the caller's account to have the `ADMIN` role (also requires 🔒).

### Auth (`/api/auth`)

| Method | Path                          | Auth | Description                                                          |
|--------|-------------------------------|:----:|-------------------------------------------------------------------------|
| POST   | `/register`                   |      | Create an account, returns a token pair, and emails a verification link  |
| POST   | `/login`                      |      | Log in, returns a fresh token pair. Rejected (403) until the email is verified |
| POST   | `/refresh`                    |      | Exchange a refresh token for a new token pair                            |
| POST   | `/logout`                     |      | Revoke one refresh token                                                 |
| POST   | `/logout-all`                 | 🔒  | Revoke every refresh token for the caller                                |
| POST   | `/forgot-password`            |      | Issue a password-reset token (if the email exists)                        |
| POST   | `/reset-password`             |      | Consume a reset token to set a new password                               |
| GET    | `/verify-email?token=`        |      | Consumes the link from the verification email (opened in a browser, not called by the app) |
| POST   | `/resend-verification-email`  |      | Re-sends the verification email for a given address; no-ops if unknown/already verified |

Registration still returns a usable token pair immediately (so the app can show a
"check your email" screen right after signup), but a subsequent `/login` is rejected
with 403 until that account's email is verified. The verification token expires after
24h; `resend-verification-email` is deliberately unauthenticated (same shape as
`forgot-password`, taking just an email) rather than requiring a token, since an
unverified account that lost its session (app reinstalled, storage cleared, or its
refresh token itself expired/was revoked before the link was used) has no other way to
prove it owns the address and get back in.

### Users (`/api/users/{userId}`) — 🔒, `userId` must be the caller's own id

| Method | Path         | Description                                  |
|--------|--------------|-----------------------------------------------|
| GET    | ``           | Get the caller's profile                       |
| PUT    | ``           | Update the caller's profile                    |
| GET    | `/settings`  | Get the caller's app settings                  |
| PUT    | `/settings`  | Update the caller's app settings               |

### Admin - Users (`/api/admin/users`) — 🛡️, all endpoints

| Method | Path      | Description                                                              |
|--------|-----------|-----------------------------------------------------------------------------|
| GET    | ``        | Get every registered user account                                          |
| PUT    | `/{id}`   | Update a user's name, role and email-verified flag                          |
| DELETE | `/{id}`   | Delete a user account (an admin cannot delete their own account this way)   |

### Bird Logs (`/api/bird-logs`) — 🔒 unless noted

| Method | Path              | Auth | Description                                                      |
|--------|-------------------|:----:|--------------------------------------------------------------------|
| GET    | ``                | 🛡️  | Get every log across every user (there's no per-user viewing feature for this yet, so it's admin-only for now) |
| GET    | `/user/{userId}`  | 🔒*  | Get all of this user's own logs, most recent first (admins can pass any user's id, for the admin panel's user detail view) |
| GET    | `/location`       |      | Get the caller's logs within a lat/lng box (`minLat/maxLat/minLng/maxLng`), for the map view |
| GET    | `/{id}`           |      | Get one of the caller's logs by id                                  |
| POST   | ``                |      | Create a log (re-evaluates badge progress)                          |
| PUT    | `/{id}`           |      | Update a log (re-evaluates badge progress)                          |
| DELETE | `/{id}`           |      | Delete a log (re-evaluates badge progress)                          |

\* `userId` must be the caller's own id, unless the caller is an admin.

### Species guide (`/api/species`)

| Method | Path                      | Auth | Description                                                                 |
|--------|---------------------------|:----:|-------------------------------------------------------------------------------|
| GET    | ``                        |      | Paginated species list (`?page=`/`?size=`/`?sort=`), optional `?search=` by common name; returns a Spring `Page` envelope (`content`, `totalPages`, `totalElements`, ...) |
| GET    | `/{id}`                   |      | Get one species by id, with its reference images                               |
| GET    | `/{id}/sound`             |      | Live-fetch call/song recordings from Xeno-canto, capped at 5 per species        |
| POST   | ``                        | 🛡️  | Create a species                                                               |
| PUT    | `/{id}`                   | 🛡️  | Update a species                                                               |
| DELETE | `/{id}`                   | 🛡️  | Delete a species (and its images)                                              |
| POST   | `/{id}/images`            | 🛡️  | Attach a curated reference image (life stage + gender)                         |
| GET    | `/{id}/photo-candidates`  | 🛡️  | **Curation tool** — searches iNaturalist for candidate photos (`?lifeStage=ADULT\|BABY&gender=MALE\|FEMALE\|NOT_APPLICABLE`) to review and save via the endpoint above. Does not save anything itself. |
| DELETE | `/{id}/images/{imageId}`  | 🛡️  | Remove a reference image                                                       |

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

The admin panel's badge form exposes all of these, including the species picker for
`SPECIES_LOGS` and the radius field for the two `*_IN_RADIUS` types.

### Uploads (`/api/uploads`) — 🔒

| Method | Path      | Description                                                              |
|--------|-----------|-----------------------------------------------------------------------------|
| POST   | `/photo`  | Upload a photo (multipart `file`), returns its URL for use as a log's `photoUrl`. Re-encoded server-side to strip EXIF metadata (including GPS). |

## Design notes

- **Ownership scoping, not just permission checks**: bird logs and user profiles are
  looked up by `(id, ownerId)` in the same query, not fetched then checked — so another
  user's id returns a plain 404, never a 403 that would confirm it exists.
- **Badges recompute on every log change**: create/update/delete a bird log and every
  badge's progress is recalculated for that user in the same request — no background
  job, no separate "sync" step.
- **Species photos and sounds are handled differently on purpose**: sounds are
  fetched live from Xeno-canto on every request, since any call/song recording for a
  species is as good as any other. Photos are curated once by an admin (via the
  iNaturalist-backed `photo-candidates` endpoint) and stored, since a specific
  crowd-sourced photo needs a human to check it's actually a good, correctly-labeled
  picture before it goes in the guide.

## Exploratory notes

Not decided, not integrated — just findings worth keeping so they aren't re-derived
later. `BirdLog` already has `detectedSpeciesId`/`detectionConfidence` columns reserved
for this, but no detection service exists yet.

### Auto species detection from photo — model evaluation (2026-09-15)

Tested [`chriamue/bird-species-classifier`](https://huggingface.co/chriamue/bird-species-classifier)
on Hugging Face (EfficientNet, 525 classes, ~96.8% reported validation accuracy) as a
candidate for auto-suggesting a species from a bird-log photo. Might be used for that
eventually — **not decided yet**.

- Tested against 4 photos: 2x Pacific Parrotlet, 1x African Goose, 1x sparrow. The
  sparrow classified correctly; both parrotlets and the goose did not.
- Misclassifications are a **dataset-coverage** issue, not a bug: the model's full
  525-label list doesn't include "Pacific Parrotlet" or "African Goose" at all (it has
  things like Alexandrine Parakeet, Golden Parakeet, African Pygmy Goose, Egyptian
  Goose, Snow Goose, etc.), so on those photos it silently falls back to the
  nearest-looking known class (Alexandrine Parakeet, a swan-type guess) instead of
  reporting "unknown." Sparrows worked because sparrow species are actually in the
  training set.
- Takeaway: this model is solid *within* its known 525 species, but has no
  out-of-vocabulary/"unknown" fallback — it always returns its closest guess. If this
  model is adopted, that needs to be handled explicitly (e.g. a confidence threshold, or
  restricting suggestions to species already in Wingmark's own guide) rather than
  trusting the raw top-1 label.
