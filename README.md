[![Deploy](https://github.com/Santoriellor/simulti/actions/workflows/deploy.yml/badge.svg)](https://github.com/Santoriellor/simulti/actions/workflows/deploy.yml)

# simulti

A real-time, two-player Space Invaders game: an Angular frontend and a Spring
Boot backend, connected by REST, Server-Sent Events and WebSocket, backed by
PostgreSQL. Live at <https://simulti.santoriello.ch>.

## Running it locally

```bash
docker compose up -d --build
```

builds and starts the whole stack the way it runs in production. See
[`docs/technical.md`](docs/technical.md) for prerequisites and the caveats
around running the frontend or backend outside Docker.

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — components, request flow,
  WebSocket/SSE, persistence, deployment topology.
- [`docs/design.md`](docs/design.md) — domain model, room lifecycle, scoring,
  authentication.
- [`docs/technical.md`](docs/technical.md) — configuration, environment
  variables, secrets, CI/CD, schema changes.
- [`docs/runbook.md`](docs/runbook.md) — logs, backups, restore, common
  incidents.
- [`docs/decisions/`](docs/decisions/) — architecture decision records,
  including problems found but deliberately not fixed yet.
