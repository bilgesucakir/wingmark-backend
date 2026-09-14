# wingmark-backend

Backend API for **<a href="https://github.com/bilgesucakir/wingmark">Wingmark</a>**, a personal bird-logging iOS app. Log a bird sighting
(photo, species, life stage, gender, location, notes), browse it on a map, look it up
in a species guide (with photos and live call/song recordings), and earn badges as you
go. Personal-only for now: every user sees just their own logs, never anyone else's.

## Tech stack

- Java 21, Spring Boot 3.3.4
- Spring Web MVC + Spring Security (JWT, stateless)
- Spring Data JPA (Hibernate) + Flyway migrations
- H2 in-memory database by default — **temporary**, see [Database](#database)
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

### Run it

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080`.

### Run the tests

```bash
mvn test
```

## Database

> **⚠️ Temporary**: this project is currently wired to an **in-memory H2 database** for
> local development convenience only. This is a deliberate, temporary choice while the
> service's scope and feature set are still being worked out — **it will be switched to
> a real database later**, once that's settled. Don't treat H2 as the intended
> production setup.

Defaults to an **in-memory H2 database** (`jdbc:h2:mem:wingmark`) — zero setup, but all
data resets every time the app restarts.

To switch to Postgres (or anything else) later, override these env vars — no code or
migration changes needed, since the Flyway migrations are written in cross-compatible
SQL:

```
DB_URL=jdbc:postgresql://localhost:5432/wingmark
DB_USERNAME=wingmark
DB_PASSWORD=...
DB_DRIVER=org.postgresql.Driver
```

### Browsing the database

The H2 web console is available at `http://localhost:8080/h2-console` while the app is
running:

- JDBC URL: `jdbc:h2:mem:wingmark`
- User: `sa`
- Password: (blank)

### Seed data

Three migrations run automatically on startup:

- `V1__init.sql` — schema
- `V2__seed_badges.sql` — 10 starter badges (total logs, unique species, baby logs,
  unknown-species logs, pet logs, species-in-radius)
- `V3__seed_species.sql` — 3 example species (House Sparrow, European Robin, Mallard)
  with a few reference images each

### Becoming an admin

There's no self-service "become admin" flow by design — admin-only endpoints (species
and badge catalog management, photo curation) are meant to be operated by whoever runs
the backend, not by app users. To promote an account:

1. Register/log in normally to create the account.
2. Open the H2 console (see above) and run:
   ```sql
   UPDATE USERS SET ROLE='ADMIN' WHERE EMAIL='you@example.com';
   ```
3. Log in again — the role is baked into the JWT at login time, so you need a fresh
   token after the change.

## API documentation

Interactive Swagger UI is available at `http://localhost:8080/swagger-ui.html` while
the app is running (raw OpenAPI JSON at `/v3/api-docs`).

## Endpoints

All endpoints are prefixed with `/api`. 🔒 = requires `Authorization: Bearer <token>`.
🛡️ = requires the caller's account to have the `ADMIN` role (also requires 🔒).

### Auth (`/api/auth`) — all public

| Method | Path                    | Description                                      |
|--------|-------------------------|---------------------------------------------------|
| POST   | `/register`             | Create an account, returns a token pair            |
| POST   | `/login`                | Log in, returns a fresh token pair                 |
| POST   | `/refresh`              | Exchange a refresh token for a new token pair       |
| POST   | `/logout`               | Revoke one refresh token                           |
| POST   | `/logout-all`           | 🔒 Revoke every refresh token for the caller        |
| POST   | `/forgot-password`      | Issue a password-reset token (if the email exists)  |
| POST   | `/reset-password`       | Consume a reset token to set a new password         |

### Users (`/api/users/{userId}`) — 🔒, `userId` must be the caller's own id

| Method | Path         | Description                                  |
|--------|--------------|-----------------------------------------------|
| GET    | ``           | Get the caller's profile                       |
| PUT    | ``           | Update the caller's profile                    |
| GET    | `/settings`  | Get the caller's app settings                  |
| PUT    | `/settings`  | Update the caller's app settings               |

### Bird Logs (`/api/bird-logs`) — 🔒 unless noted

| Method | Path              | Auth | Description                                                      |
|--------|-------------------|:----:|--------------------------------------------------------------------|
| GET    | ``                | 🛡️  | Get every log across every user (there's no per-user viewing feature for this yet, so it's admin-only for now) |
| GET    | `/user/{userId}`  | 🔒*  | Get all of this user's own logs, most recent first                  |
| GET    | `/location`       |      | Get the caller's logs within a lat/lng box (`minLat/maxLat/minLng/maxLng`), for the map view |
| GET    | `/{id}`           |      | Get one of the caller's logs by id                                  |
| POST   | ``                |      | Create a log (re-evaluates badge progress)                          |
| PUT    | `/{id}`           |      | Update a log (re-evaluates badge progress)                          |
| DELETE | `/{id}`           |      | Delete a log (re-evaluates badge progress)                          |

\* `userId` must be the caller's own id.

### Species guide (`/api/species`)

| Method | Path                      | Auth | Description                                                                 |
|--------|---------------------------|:----:|-------------------------------------------------------------------------------|
| GET    | ``                        |      | Get all species, optional `?search=` by common name                            |
| GET    | `/{id}`                   |      | Get one species by id, with its reference images                               |
| GET    | `/{id}/sound`             |      | Live-fetch call/song recordings from Xeno-canto                                |
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
| GET    | `/user/{userId}`  | 🔒*  | Get every badge with this user's progress/earned status              |
| POST   | ``                | 🛡️  | Create a new badge definition                                        |
| DELETE | `/{id}`           | 🛡️  | Delete a badge definition                                            |

\* `userId` must be the caller's own id.

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

## TODO

- English / Turkish language support (i18n)
- Basic admin panel (static HTML/CSS/JS, no framework needed): log in, search species,
  browse `photo-candidates` results visually, and save the chosen one via
  `POST /{id}/images` — replaces guessing at photo URLs/filenames by hand
