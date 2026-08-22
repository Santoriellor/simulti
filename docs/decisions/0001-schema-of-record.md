# 1. `database/init.sql` is the schema of record

Date: 2026-08-22
Status: accepted (already implemented, on `main`)

## Context

The backend used to run with `spring.jpa.hibernate.ddl-auto: update`.
Hibernate would create and alter tables on its own to match the entities,
directly against the production database, on every startup. This let the
schema drift for as long as the entities and the database happened to agree
from Hibernate's point of view: `database/init.sql`, meant to describe that
same schema for bootstrapping a fresh environment, silently fell out of date
and was eventually missing entire tables (`game_rooms`, `game_room_players`,
`players`) that existed only because Hibernate had created them live. A
migration written against the stale `init.sql` would not have reproduced the
real production schema.

## Decision

`ddl-auto` is `validate`. Hibernate checks the live schema against the entity
mappings at startup and refuses to boot if they disagree, but it never
creates or alters a table itself. `database/init.sql` — regenerated from a
`pg_dump --schema-only` of the live production database on 2026-08-18, not
hand-written — is the schema of record. It applies only to a brand-new, empty
Postgres data volume; Postgres never runs `docker-entrypoint-initdb.d` scripts
against an existing volume, so editing this file never mutates a live
database.

## Consequences

Entity changes and schema changes are now coupled by policy, not just by
convention: changing an entity's mapping and changing `database/init.sql` are
required to happen in the same commit (`docs/technical.md`, Schema changes),
and bringing an already-running environment's database in line with that
change is always a manual, hand-written `ALTER`, applied out of band from any
deploy. A startup failure that names a column or table is no longer a bug to
route around — it is the schema-drift finding itself, and reverting to
`ddl-auto: update` to silence it is explicitly the thing this decision exists
to prevent (`docs/runbook.md`, Backend refuses to start).

The regenerated `init.sql` also fixed the file's scope going forward: two
tables visible in production, `game_sessions` and `game_session_players`, are
deliberately excluded from it because no entity backs them any more — they
are dead schema left over from before the runtime class was renamed from
`GameSession` to `GameRoom`. They are not recreated on a fresh environment.
See `docs/decisions/0003-deferred-findings.md` for their disposition.
