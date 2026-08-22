# Technical

## Prerequisites

- Docker and Docker Compose, for running the whole stack the way it runs in
  production.
- For backend work outside a container: JDK 25 and Maven (the repository does
  not ship a working `mvnw` for this environment — the Dockerfile notes that
  the wrapper's bootstrap extraction fails on the deployment host's kernel, so
  a real `mvn` on `PATH` is assumed).
- For frontend work outside a container: Node 20 and the Angular CLI
  (`frontend/package.json` targets `@angular/core ^20.3.0`).
- PostgreSQL 16, if running the backend against a real database instead of
  the Docker Compose stack.

## Local development

```bash
docker compose up -d --build
```

builds and starts all three services (`db`, `backend`, `frontend`) as they run
in production, including the Postgres healthcheck gating and the startup
ordering described in `docs/architecture.md`. This is the path documented in
`README.md` and the one to reach for by default.

Two things to know before running either side outside Docker:

- The backend needs `POSTGRES_USER`, `POSTGRES_PASSWORD` and `JWT_SECRET` set
  in its environment before it will start at all — `JwtService` fails startup
  deliberately (see `docs/design.md`, Authentication model) if the secret is
  missing, is short, or is still the placeholder value in the source.
- The frontend's only environment file, `frontend/src/environments/environment.ts`,
  is hard-coded to `production: true` and to the live API and WebSocket URLs
  (`https://simulti.santoriello.ch`). There is no `environment.development.ts`
  and `angular.json` configures no file replacement for it, so `ng serve`
  against a locally-run backend requires either editing this file locally
  (and not committing the change) or running the full Docker Compose stack,
  which serves the built bundle behind nginx instead of `ng serve`.

## Configuration

Every environment variable read by `backend/src/main/resources/application.yml`:

| Variable | Used for |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL for the Postgres connection (defaults to `jdbc:postgresql://localhost:5432/simulti` outside Docker Compose). |
| `POSTGRES_USER` | Database username; no default, required. |
| `POSTGRES_PASSWORD` | Database password; no default, required. |
| `JWT_SECRET` | HS256 signing key for issued JWTs; no default — see Authentication model in `docs/design.md` for the startup checks around it. |
| `JWT_EXPIRATION_MS` | Token lifetime in milliseconds; defaults to `14400000` (4 hours). |

`app.frontend-url` (an `application.yml` key, not read from the process
environment directly, though it can be overridden the same way any Spring
property can) is required by two config classes that are otherwise unrelated:
`CorsConfig` uses it as the sole allowed CORS origin, and `WebSocketConfig`
uses it as the sole allowed origin for the gameplay WebSocket handshake. In
`application.yml` it is set to `https://simulti.santoriello.ch`.

## Secrets

Production reads `JWT_SECRET` from `/srv/secrets/simulti/jwt.env` and the
database credentials (`POSTGRES_USER`, `POSTGRES_PASSWORD`, and whatever else
`db.env` defines) from `/srv/secrets/simulti/db.env` on the VPS. Both files
are referenced by `env_file:` entries in `docker-compose.yml` for the `db` and
`backend` services and are not part of this repository — there is nothing to
copy into the repo to run in production, and neither file should ever be
committed.

## CI/CD

`.github/workflows/deploy.yml` runs on every push to `main`. A `test` job runs
first: `mvn -B test` for the backend and `npx ng test --watch=false` for the
frontend. A `deploy` job depends on `test` passing (`needs: test`) and is
additionally guarded to only run on `refs/heads/main`. Deploy copies the
repository to the VPS with `rsync` (excluding `.git`, `.github`,
`node_modules` and `backend/target`) and then runs
`docker compose up -d --build` over SSH. The backend's own Docker build also
runs the Maven test suite as part of `docker compose build`
(`backend/Dockerfile` deliberately does not pass `-DskipTests` — a failing
test is meant to fail the image build, which fails the deploy), so a broken
backend suite blocks a deploy twice over: once in the CI job, once again
during the image build on the VPS.

## Schema changes

`database/init.sql` is the schema of record (`docs/decisions/0001-schema-of-record.md`).
The rule, verbatim from `database/init.sql`'s own header comment: **change an
entity, change this file in the same commit, and hand-write the `ALTER` for
environments that already exist.** `application.yml`'s own comment above
`ddl-auto: validate` states the complementary rule: a startup failure naming a
column is the finding itself, and the fix is an `ALTER` plus a matching edit
to `init.sql` — never reverting `ddl-auto` back to `update`.

`init.sql` only applies to a brand-new, empty Postgres data volume (Postgres
only runs files under `/docker-entrypoint-initdb.d` when the data directory is
empty); it never runs against — and therefore never mutates — an existing
database. Bringing a live environment's schema in line with a changed entity
is always a manual, hand-written `ALTER`, applied out of band.

## Formatting

Backend formatting is `spotless-maven-plugin`, configured in `backend/pom.xml`
to run `google-java-format` in `AOSP` style (4-space indentation, matching
this codebase) with unused imports removed:

```bash
mvn spotless:apply
```

Frontend formatting is Prettier, a declared devDependency
(`frontend/package.json`, pinned exact version) configured by
`frontend/.prettierrc`:

```bash
npx prettier --write "src/**/*.{ts,html,css}"
```

Neither command should ever change behaviour — only whitespace, line
wrapping, and import order. Run both before committing source changes.

Each fresh clone needs a one-time local git config so that `git blame` skips
past the formatting sweep commit and attributes lines to whoever last changed
their meaning:

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```
