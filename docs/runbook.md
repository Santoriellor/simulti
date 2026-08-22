# Runbook

## Where the logs are

Neither container writes application logs to a file that this stack captures.
`backend`'s `application.yml` sets only log *levels* (`root: INFO`,
`org.springframework.security: INFO`) and no `logging.file.name`, so despite a
comment in `backend/Dockerfile` claiming otherwise, the backend logs to
stdout, and `docker compose logs backend` (or `docker logs simulti_backend`)
is where they are. `frontend` is nginx serving static files with no custom
`access_log`/`error_log` directives in `nginx/nginx.conf`, so it uses the base
image's defaults; `docker compose logs frontend` is the equivalent for it.
`docker compose logs -f` follows all three services' output together.

## Backing up `pgdata`

The only durable state is the named volume `pgdata`, mounted into the `db`
service. With the stack running:

```bash
docker compose exec db pg_dump -U "$POSTGRES_USER" -d simulti > backup.sql
```

(`POSTGRES_USER` comes from `/srv/secrets/simulti/db.env` on the VPS.) For a
full binary copy of the volume instead of a logical dump, stop the stack
first so nothing is writing to it, then archive the volume's contents (for
example with a throwaway container that mounts `pgdata` read-only and tars
it out) before starting the stack again.

## Restoring

Restoring `database/init.sql` alone only creates the schema — it never
touches an existing `pgdata` volume (see `docs/technical.md`, Schema changes).
To restore data:

```bash
docker compose exec -T db psql -U "$POSTGRES_USER" -d simulti < backup.sql
```

against a database that already has the matching schema applied. To restore
onto a completely fresh environment, start from an empty `pgdata` volume so
`init.sql` runs and creates the schema, then load the data dump on top.

## Backend refuses to start

`application.yml` sets `hibernate.ddl-auto: validate`, so Hibernate checks the
live schema against the entity mappings at startup and refuses to boot if
they disagree. A startup failure naming a specific column or table is a real
schema drift finding, not a bug in Hibernate's check — something changed one
side (an entity, or the live database) without updating the other. The fix is
to hand-write the matching `ALTER` against the live database and make the
equivalent edit to `database/init.sql` in the same commit. **Never** set
`ddl-auto` back to `update` to make the failure go away — that is what
originally let the schema drift far enough that `init.sql` had to be
regenerated from a live `pg_dump` (see the header comment in
`database/init.sql`), and it silently accumulates dead schema (the
`game_sessions` / `game_session_players` tables noted there are a direct
result of it).

## Certificate or hostname problems

This project is served at `simulti.santoriello.ch`. Traefik is the shared
edge for every live project on the VPS; when a request's `Host` header does
not match any router Traefik knows about, Traefik answers with its default,
self-signed certificate rather than refusing the connection. `curl` reports
that mismatch as **exit code 60** (SSL certificate problem). Seeing exit 60
against this hostname is a symptom of a *missing or misconfigured router* —
check that `docker-compose.yml`'s `traefik.http.routers.simulti-*` labels are
present and that the `backend`/`frontend` containers are actually attached to
`proxy-network` — not evidence of an expired or broken TLS certificate for
the real hostname. Confirm which certificate is actually being served with:

```bash
curl -vI https://simulti.santoriello.ch 2>&1 | grep -i "subject\|issuer"
```

A `subject` that is not `simulti.santoriello.ch` confirms the default-cert
case above rather than a Let's Encrypt renewal failure.
