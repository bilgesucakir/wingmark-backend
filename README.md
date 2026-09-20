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
  "message": "Species 9c858901-8a57-4791-81fe-4c455b099bc9 not found",
  "path": "/api/species/9c858901-8a57-4791-81fe-4c455b099bc9",
  "validationErrors": null
}
```

`validationErrors` is only populated for request-body validation failures (400), as a
`{"fieldName": "message"}` map.

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
  "lastName": "Rivera"
}
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIz...",
  "refreshToken": "8f14e45fceea167a5a36dedd4bea2543",
  "expiresInMs": 900000
}
```

**POST `/login`** → `200 OK`

Request:
```json
{
  "email": "amelia@example.com",
  "password": "correcthorse8"
}
```

Response: same shape as `/register`.

**POST `/refresh`** → `200 OK`

Request:
```json
{ "refreshToken": "8f14e45fceea167a5a36dedd4bea2543" }
```

Response: same shape as `/register` (a new token pair, old refresh token revoked).

**POST `/logout`** → `204 No Content`

Request: same body as `/refresh`. No response body.

**POST `/logout-all`** → `204 No Content`. No request body, no response body.

**POST `/forgot-password`** → `202 Accepted`

Request:
```json
{ "email": "amelia@example.com" }
```

No response body.

**POST `/reset-password`** → `204 No Content`

Request:
```json
{
  "token": "b1946ac92492d2347c6235b4d2611184",
  "newPassword": "newSecret9"
}
```

No response body.

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

</details>

### Admin - Users (`/api/admin/users`) — 🛡️, all endpoints

| Method | Path      | Description                                                              |
|--------|-----------|-----------------------------------------------------------------------------|
| GET    | ``        | Get every registered user account                                          |
| PUT    | `/{id}`   | Update a user's name, role, email-verified flag, and favorite species       |
| DELETE | `/{id}`   | Delete a user account (an admin cannot delete their own account this way)   |

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
  "favoriteSpeciesId": "9c858901-8a57-4791-81fe-4c455b099bc9"
}
```

Response: a single user object, same shape as one entry of the GET list above.

**DELETE `/{id}`** → `204 No Content`. No request or response body.

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
| GET    | `/location`       |      | Get the caller's logs within a lat/lng box (`minLat/maxLat/minLng/maxLng`), for the map view |
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
    "photoUrl": "https://wingmark-backend.onrender.com/uploads/abc123.jpg",
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
  "photoUrl": "https://wingmark-backend.onrender.com/uploads/abc123.jpg",
  "note": "Singing on the fence at sunrise",
  "latitude": 40.7829,
  "longitude": -73.9654,
  "locationName": "Central Park"
}
```

Response: same shape as one GET entry above.

**PUT `/{id}`** → `200 OK`. Request: same shape as POST. Response: same shape as GET.

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
          "imageUrl": "https://wingmark-backend.onrender.com/uploads/sparrow-male.jpg",
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
  "imageUrl": "https://wingmark-backend.onrender.com/uploads/sparrow-male.jpg",
  "caption": "Adult male"
}
```

Response:
```json
{
  "id": "1a2b3c4d-1234-4a5b-8c9d-0e1f2a3b4c5d",
  "lifeStage": "ADULT",
  "gender": "MALE",
  "imageUrl": "https://wingmark-backend.onrender.com/uploads/sparrow-male.jpg",
  "caption": "Adult male"
}
```

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

The admin panel's badge form exposes all of these, including the species picker for
`SPECIES_LOGS` and the radius field for the two `*_IN_RADIUS` types.

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
    "tier": "BRONZE"
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
  "tier": "BRONZE"
}
```

Response: same shape as one `/catalog` entry above.

**PUT `/{id}`** → `200 OK`. Request: same shape as POST. Response: same shape as GET.

**DELETE `/{id}`** → `204 No Content`. No request or response body.

</details>

### Uploads (`/api/uploads`) — 🔒

| Method | Path      | Description                                                              |
|--------|-----------|-----------------------------------------------------------------------------|
| POST   | `/photo`  | Upload a photo (multipart `file`), returns its URL for use as a log's `photoUrl`. Re-encoded server-side to strip EXIF metadata (including GPS). |

<details>
<summary><strong>Examples</strong></summary>

**POST `/photo`** → `201 Created`. Request is `multipart/form-data`, not JSON - one
part named `file` (the image). Response:

```json
{ "url": "https://wingmark-backend.onrender.com/uploads/abc123.jpg" }
```

</details>

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
