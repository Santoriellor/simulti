# Design

## Domain model

Five persisted entities carry the game's state, plus one that is defined but
currently unused.

**`User`** (`model/User.java`, table `users`) holds login credentials: `email`
and `username` (both unique), a BCrypt `password` hash, and `createdAt`. It is
the root identity — everything else hangs off a `User` by reference.

**`PlayerEntity`** (table `players`) is the game profile: a one-to-one
extension of `User` (`user_id`, unique, `@JsonIgnore`d back-reference) carrying
`totalScore`, `gamesPlayed` and `highScore`. `AuthController` and
`GameRoomController` both lazily create a `PlayerEntity` for a `User` the
first time one is needed (on room creation or join), rather than at
registration time.

**`GameRoom`** (table `game_rooms`) is a waiting-room / lobby record: a
`roomId`, a `roomName`, a `host` (`PlayerEntity`, `@JsonIgnore`d), the list of
`playerIds` currently in the room, a `wave` counter, `startedAt` /`endedAt`
timestamps, and a `maxPlayer` cap. Its `status` field is an **unconstrained
`String`** — there is no enum, no database check constraint, nothing that
prevents an arbitrary value being written. The only two values the code
itself ever assigns are `"WAITING"` and `"STARTED"` (`GameRoomService`), and
because those literals appear inconsistently cased in different call sites,
`GameRoomController.joinRoom` compares the room's status with
`"STARTED".equalsIgnoreCase(room.getStatus())` rather than `.equals(...)`,
and the frontend lower-cases the value again on the way in
(`waitingRoom.component.ts`). Nothing currently enforces that `status` can
only ever hold one of these two strings.

**`GameResult`** (table `game_results`) is one finished game's outcome for one
player: a link to the `GameRoom` it was played in, a link to the `User`, and
`finalScore`, `waveReached`, `enemiesKilled`, `createdAt`.

**`Leaderboard`** (table `leaderboard`) is a per-user aggregate — `totalScore`,
`gamesPlayed`, `highScore`, `updatedAt` — separate from `PlayerEntity`, which
tracks the same three numbers. `ScoreController` and `ScoreService` in fact
read and write `players`, not `leaderboard`
(`PlayerRepository.listHighScores()`); no code path in `backend/src` currently
writes to the `Leaderboard` entity or its table.

There is also a sixth entity, **`SessionEntity`** (table `sessions`), modeling
a server-side session token with an expiry. `SessionRepository` exists, but
nothing in `backend/src` ever constructs a `SessionEntity` or calls its
repository — authentication is stateless JWT throughout
(`SecurityConfig.SessionCreationPolicy.STATELESS`), and this entity appears to
be a leftover from before that decision was made.

## Room lifecycle

A room is created via `POST /api/rooms` with a name, starts in status
`WAITING`, and is listed to other players by `GET /api/rooms`
(`GameRoomService.listOpenRooms()`, which queries `WAITING` rooms only) and
pushed live to already-connected clients as a `room.created` SSE event.
Joining (`POST /api/rooms/{roomId}/join`) appends the caller's player ID to
`playerIds`; once the room reaches its player cap the status flips to
`STARTED` and a `room.started` event is broadcast alongside the `room.updated`
one. The player cap used for this transition is the constant
`game.GameRoom.MAX_PLAYERS` (currently `2`), not the room's own persisted
`maxPlayer` column — the column exists and is always set (to that same
constant, by `GameRoomService.createRoom`) but nothing reads it back to decide
how many players a given room should hold, so a per-room player cap is not
actually configurable despite the field's presence.

Gameplay itself happens off the `GameRoom` entity entirely, inside the
in-memory `game/GameRoom` simulation reached over the gameplay WebSocket (see
`docs/architecture.md`). When a player disconnects or quits,
`GameWebSocketHandler` removes them from the simulation and then reconciles
the persisted `GameRoom`: if the simulation room is now empty, the persisted
row is deleted and a `room.deleted` event is broadcast (after final scores are
persisted, once per room); otherwise the player is removed from the persisted
`playerIds` and, if the room is now under capacity, its status is reset to
`WAITING`. A room can also be deleted directly through
`DELETE /api/rooms/{roomId}/delete`.

## Scoring

When an in-memory room empties out, `GameWebSocketHandler` takes a snapshot of
final scores per user (`room.getScoresSnapshotUuidMap()`) and hands it to
`ScoreService.persistRoomScores`, which — for each user — loads or creates
their `PlayerEntity` and adds the game's score to `totalScore`, increments
`gamesPlayed`, and raises `highScore` if the new score beats it. This mutates
`PlayerEntity` only; it does not create a `GameResult` row or write to
`Leaderboard`. The public leaderboard (`GET /api/leaderboard`, served by
`ScoreController` → `ScoreService.listHighScores()` →
`PlayerRepository.listHighScores()`) is therefore read directly off `players`,
joined to `users` for the display name — `game_results` and `leaderboard` are
populated schema with no code path currently writing to them.

## Authentication model

Registration (`POST /api/auth/register`) creates a `User` with a BCrypt
password hash and an empty `PlayerEntity`, then returns a signed JWT. Login
(`POST /api/auth/login`) authenticates the credentials through Spring
Security's `AuthenticationManager` and returns a JWT containing the user's
email as subject and their `userId` as an extra claim
(`JwtService.generateTokenForWebSocket`, used for both HTTP and WebSocket
tokens). Tokens are signed HS256 with a secret read from `jwt.secret`
(`JWT_SECRET`); `JwtService.validateKeyOnStartup` refuses to start the
application if that secret is missing, is the placeholder value baked into
the source, or is under 32 bytes — a real key is a hard requirement, not a
default-and-warn.

`JwtAuthenticationFilter` authenticates ordinary HTTP requests from the
`Authorization` header; the WebSocket handshake and the SSE stream each
validate the token themselves, out of band from Spring Security's filter
chain, for the reasons described in `docs/architecture.md`. `GET /api/auth/me`
returns the caller's own `User` record so the frontend can show who is logged
in — today it returns the `User` entity directly, and because
`User.password` carries no `@JsonIgnore` (unlike `User.playerProfile`, which
does), every call to `/me` serializes the caller's BCrypt password hash into
the JSON response. This is the motivating example for
`docs/decisions/0002-dto-boundary.md`.
