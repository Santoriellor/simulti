[![Deploy](https://github.com/Santoriellor/simulti/actions/workflows/deploy.yml/badge.svg)](https://github.com/Santoriellor/simulti/actions/workflows/deploy.yml)

# simulti

Multiplayer Space Invaders. Angular frontend, Spring Boot backend with WebSocket support,
PostgreSQL.

Live: <https://simulti.santoriello.ch>

## Layout

| Path | Purpose |
|---|---|
| `frontend/` | Angular client |
| `backend/` | Spring Boot API and WebSocket endpoint (`/ws`) |
| `database/init.sql` | Schema bootstrap, mounted read-only into Postgres |

## Running locally

```bash
docker compose up -d --build
```

Production reads its secrets from `/srv/secrets/simulti/` on the VPS; there is nothing to copy
into this repository.

## Deployment

Pushing to `main` rsyncs the project to the VPS and rebuilds. Traefik routes
`/api` and `/ws` to the backend and everything else to the frontend.

Backend and frontend run unprivileged (uid 1000 and uid 101). Every service declares a
healthcheck, and the backend waits for Postgres to report healthy before starting.

## Tests

`backend/src/test/` has two JUnit classes and `frontend/` one Angular spec.
**The backend suite currently errors** — `BackendApplicationTests.contextLoads` has no test
datasource — which is why the Docker build still passes `-DskipTests`. Repairing it is tracked
separately.
