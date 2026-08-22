# Architecture

## Overview

space-multi is a two-player, real-time Space Invaders game. An Angular 20
single-page application talks to a Spring Boot 3.5 backend (JDK 25) over REST,
Server-Sent Events and WebSocket, backed by a single PostgreSQL 16 database.
The whole stack is built into containers by a shared `docker-compose.yml` and
served in production behind a Traefik edge at `simulti.santoriello.ch`.

The backend is a conventional layered application — controllers, services,
Spring Data JPA repositories, JPA entities — plus a second, unrelated layer
that has nothing to do with persistence: an in-memory game simulation that
runs a 60 Hz loop for whichever rooms currently have players connected over
WebSocket.

## Components

**Backend** (`backend/src/main/java/ch/multispace/backend/`)

| Package | Responsibility |
|---|---|
| `controllers` | Three `@RestController` classes: `AuthController` (`/api/auth`), `GameRoomController` (`/api/rooms`), `ScoreController` (`/api/leaderboard`). |
| `services` | `AuthService` — registration, login, JWT issuance — and `PlayerProvisioningService`, which resolves the `PlayerEntity` behind an authenticated principal, creating it on first use. |
| `game` | `GameLoop`, `GameRoomService`, and the in-memory simulation class `game/GameSession` (see Runtime session vs. persisted room, below). |
| `score` | `ScoreService` — turns a finished room's final scores into persisted player stats. |
| `ws` | `GameWebSocketHandler` and `JwtHandshakeInterceptor` — the gameplay WebSocket. |
| `events` | `RoomsEventBroadcaster` — the waiting-room SSE fan-out. |
| `security` | `JwtService`, `JwtAuthenticationFilter`, `UserDetailsServiceImpl`. |
| `config` | `SecurityConfig`, `CorsConfig`, `WebSocketConfig`. |
| `model` | JPA entities: `User`, `PlayerEntity`, `GameRoom`, `GameResult`, `Leaderboard`, `SessionEntity`. |
| `repositories` | Spring Data JPA repositories, one per entity above. |
| `dtos` | The response/request shapes controllers actually expose: `UserDto`, `GameRoomDto`, `LeaderboardRowDto`, `CreateRoomRequestDTO` and the shared `ErrorResponse` used by `GlobalExceptionHandler`. Controllers construct these from entities rather than serializing entities directly — see `docs/decisions/0002-dto-boundary.md`. |
| `exceptions` | `NotFoundException`, `ForbiddenException`, `UnauthorizedException` — meaningful exceptions controllers and services throw — plus `GlobalExceptionHandler`, the single `@RestControllerAdvice` that turns them into `ErrorResponse` bodies with the right HTTP status. |

**Frontend** (`frontend/src/app/`) is an Angular 20 standalone-component
application: authentication (`auth/login`, `auth/register`), a home screen, a
`waitingRoom` lobby component, the gameplay canvas component, and a
leaderboard view, wired together with `HttpClient`, `EventSource` and the
native `WebSocket` API — no third-party HTTP or state library.

## Request flow

The frontend calls the backend at `${apiUrl}/...` (`/api/...` in production,
proxied there by Traefik — see Deployment topology). Every authenticated
request carries `Authorization: Bearer <jwt>`, attached by an HTTP
interceptor. `JwtAuthenticationFilter` reads that header on the way in,
validates the token with `JwtService`, and populates the Spring Security
context so `@AuthenticationPrincipal` resolves in controllers.
`SecurityConfig` permits `/api/auth/register`, `/api/auth/login`, `/ws/**` and
`/api/rooms/stream` without authentication; everything else requires a valid
token.

A typical session: `POST /api/auth/register` or `/login` returns a JWT →
`GET /api/rooms` lists open (`WAITING`) rooms, or `POST /api/rooms` creates
one → `POST /api/rooms/{roomId}/join` adds the caller's player to it → the
frontend opens the gameplay WebSocket, passing the room ID so the server can
place the connection into the correct in-memory session.

## WebSocket and SSE flow

Two independent real-time mechanisms exist, for two different purposes:

- **Gameplay** runs over a plain WebSocket handled by `ws/GameWebSocketHandler`
  at `/ws/space-invaders`. Authentication happens once, at handshake time, in
  `ws/JwtHandshakeInterceptor`: it reads the JWT from the `token` query
  parameter (falling back to an `Authorization` header), validates it, extracts
  `userId` and `email`, and stores them as WebSocket session attributes. A
  connection that fails this check is refused before `GameWebSocketHandler`
  ever sees it. Once connected, the handler joins the player into a
  `game/GameSession` simulation instance (`GameLoop.getOrCreate`), and from then
  on the client sends `{"type":"input", ...}` messages and receives periodic
  `{"type":"state", ...}` broadcasts describing the frame.
- **Waiting-room updates** run over Server-Sent Events at
  `GET /api/rooms/stream`, fed by `events/RoomsEventBroadcaster`, which holds
  the list of open `SseEmitter`s and pushes `room.created` / `room.updated` /
  `room.started` / `room.deleted` events whenever `GameRoomController` or
  `GameWebSocketHandler` changes room state. This endpoint accepts its JWT as
  a **query parameter** (`?token=...`) as well as an `Authorization` header,
  because the browser's `EventSource` API has no way to set custom request
  headers — a query parameter is the only way an SSE client can authenticate
  itself at all. The endpoint is listed as `permitAll()` in `SecurityConfig`
  and instead validates the token itself, by hand, inside
  `GameRoomController.streamRooms`.

## Persistence

PostgreSQL 16, one database (`simulti`). `database/init.sql` is the schema of
record — see `docs/decisions/0001-schema-of-record.md` for why, and
`docs/technical.md` for the resulting rule about coupling entity and schema
changes. `application.yml` sets `spring.jpa.hibernate.ddl-auto: validate`:
Hibernate checks the live schema against the entity mappings at startup and
refuses to boot on a mismatch, but it never creates or alters a table itself.

**Runtime session vs. persisted room.** Two related but distinct classes exist
for a room: `model/GameRoom` is the JPA entity backing the `game_rooms`
table — the persisted lobby record with `playerIds`, `status` and
`maxPlayer`. `game/GameSession` is a different, unrelated class: the
in-memory simulation state that `GameLoop` ticks 60 times a second while
players are connected over WebSocket. Nothing links them beyond sharing a
room ID. They were once both named `GameRoom`, which was a genuine naming
collision and a reading hazard; Task 13 of this refactor cycle renamed the
runtime class to `GameSession` specifically to remove that collision, so the
name `GameRoom` now refers only to the persisted entity.

## Deployment topology

Three containers, defined in the repository's single `docker-compose.yml`:
`db` (`postgres:16`, with `database/init.sql` mounted read-only into
`/docker-entrypoint-initdb.d` so it only applies on a brand-new, empty data
volume), `backend` (built from `backend/Dockerfile`, a multi-stage Maven build
producing a JRE-only runtime image, running as uid 1000), and `frontend`
(built from `frontend/Dockerfile`, an Angular production build served by
`nginxinc/nginx-unprivileged`, running as uid 101 on port 8080). `backend`
waits for `db`'s healthcheck (`pg_isready`) before starting, and `frontend`
waits for `backend`'s healthcheck (a raw TCP connect to port 8080, since the
image ships no actuator endpoint) before starting.

nginx (`nginx/nginx.conf`) serves the built Angular bundle and falls back
every unmatched path to `index.html` for client-side routing; it does not
itself proxy anything. Routing to the backend is done one layer up, by the
shared Traefik edge, using rules attached as Docker labels on the `backend`
service: requests to `simulti.santoriello.ch` whose path starts with `/api` or
`/ws` are routed straight to `backend`; every other request on that hostname
goes to `frontend`. Traefik terminates TLS (`certresolver=le`) and applies the
shared `security-headers` and `gzip-compress` middlewares from its file
provider to the HTTP routes — deliberately not to the WebSocket route, since
compression is pointless on a WebSocket stream and HSTS is already set for the
hostname by the other routers on it.
