# 3. Deferred findings

Date: 2026-08-22
Status: living document — appended to by Tasks 9, 12, 13 and 15 of this
refactor cycle as they complete

This records what the current refactor cycle deliberately leaves alone:
problems found during the documentation and characterization survey that are
real, but that this cycle does not fix. It does **not** list the four defects
already scheduled for a fix within this same cycle — `GET /api/auth/me`
leaking the password hash (Task 7), the commented-out room-ownership check in
`GameRoomController.deleteRoom` (Task 8), the end-of-life
`spring-security-oauth2` dependency (Task 12), and the frontend reading
`err.error.message` against an API that sends `{"error": "..."}`
(Task 11) — those are in progress, not deferred.

## Carried over from `TODO.md`

`TODO.md` held three short items plus some unrelated AI-assistant suggestion
text that was never an actual task; the suggestions are dropped, not carried
here. Of the three real items:

- **"API delete room"** — already implemented. `DELETE /api/rooms/{roomId}/delete`
  exists in `GameRoomController` and is wired up from the waiting-room UI.
  Dropped as done (its remaining defect, the missing ownership check, is
  tracked as Task 8 of this cycle, not here).
- **"Room name input"** — already implemented. The waiting-room UI has a text
  input bound to `newRoomName`, and `createRoom()` sends it as
  `CreateRoomRequestDTO.name`. Dropped as done.
- **"room.playerMAx"** — still open. `GameRoom.maxPlayer` is a real, persisted
  column, populated on every room (`GameRoomService.createRoom` sets it to
  `game.GameRoom.MAX_PLAYERS`), and displayed in the waiting-room UI
  (`{{ room.playerIds.length }}/{{ room.maxPlayer }}`) — but nothing reads it
  back. The player cap that actually gates joining and the `WAITING` →
  `STARTED` transition is the hardcoded constant `game.GameRoom.MAX_PLAYERS`
  (`2`), checked in `GameRoomService.joinRoom` and again, independently and
  inconsistently (a literal `2` instead of the same constant), a few lines
  above it in the same method. There is no way today to create a room with a
  cap other than two players, despite the column and the UI both suggesting
  otherwise. Not addressed by this cycle; left as a product/behavior decision
  for whoever picks it up, since fixing it changes room-creation behavior
  rather than just cleaning up code.

## Dead schema

`database/init.sql` deliberately excludes two tables that still exist in the
live production database: `game_sessions` and `game_session_players`. Per the
file's own header comment, both are backed by no entity — leftovers from when
the runtime class now called `GameRoom` was itself called `GameSession`,
before that earlier rename. `ddl-auto: update` never drops a table, which is
how they survived unnoticed until the schema was regenerated from a live
`pg_dump` (`docs/decisions/0001-schema-of-record.md`). They are not recreated
on a fresh environment, but dropping them from an already-running production
database is a manual, destructive DDL operation against live data and is
explicitly out of scope for this cycle.

## Other unused, but present, schema and code

Found during the same survey, not tracked by any task in this cycle:

- **`SessionEntity` / `sessions` table / `SessionRepository`** are fully
  defined but never referenced from anywhere else in `backend/src` —
  authentication is stateless JWT throughout, and nothing ever constructs a
  `SessionEntity`. Left in place; removing it is a small, low-risk cleanup
  that simply wasn't in scope for this pass.
- **`Leaderboard` entity / `leaderboard` table** is likewise fully defined and
  migrated, but no code path in `backend/src` writes to it. The actual
  leaderboard served at `GET /api/leaderboard` is read from `PlayerEntity`
  (`players`) instead. Left as is; consolidating onto one of the two is a
  product decision (is `leaderboard` meant to diverge from `players`, e.g. by
  being resettable per season?) that this cycle does not make.
- **`GameResult` entity / `game_results` table** is defined, migrated, and
  linked from `GameRoom` and `User`, but nothing in `backend/src` currently
  persists a `GameResult` row — final scores go to `PlayerEntity` only (see
  `docs/design.md`, Scoring). Left as is for the same reason as `Leaderboard`.

## Frontend auth guard returns a UrlTree, not `false`

Task 4's characterization spec for `authGuard` (`frontend/src/app/guards/auth.guard.spec.ts`)
was drafted assuming the guard denies access by calling `router.navigate(['/auth/login'])`
and returning `false`, mirroring the common Angular guard pattern. The actual
implementation (`frontend/src/app/guards/auth.guard.ts`) instead returns
`router.createUrlTree(['/auth/login'])` — a `UrlTree`, which Angular's router
treats as a redirect instruction — and never calls `router.navigate` at all.
Functionally this behaves the same for real navigation (the visitor still ends
up redirected to `/auth/login`), but it is a different mechanism than the
initial assumption, and a spy on `router.navigate` would never fire. The spec
was corrected to assert on the returned `UrlTree` (`result instanceof UrlTree`
and `result.toString() === '/auth/login'`) instead. Not a defect — left as is,
noted here as a divergence between assumed and actual behavior.

## Naming

Task 13 of this cycle renames the runtime `game/GameRoom` class to
`GameSession` and kebab-cases the `waitingRoom` component folder, but
deliberately leaves the frontend route path itself as `/waitingRoom`, because
renaming a live, potentially bookmarked URL needs a redirect and that is out
of scope here. That reasoning, and the URL rename it defers, is recorded by
Task 13 when it lands.

## Unauthenticated SSE stream: resolved by Task 6

Task 5's characterization spec for the security boundary
(`backend/src/test/java/ch/multispace/backend/security/SecurityBoundaryTest.java`)
was drafted assuming `GET /api/rooms/stream` without a token returns
`4xx`, mirroring the other protected endpoints. `/api/rooms/stream` is
`permitAll` in `SecurityConfig` (an `EventSource` in the browser cannot send
an `Authorization` header), so the unauthenticated request is not stopped by
Spring Security — it reaches `GameRoomController.streamRooms`. At that point
in the cycle there was no `@ControllerAdvice` in this codebase to translate a
thrown exception into an HTTP response, so the bare
`RuntimeException("Missing token for SSE")` it threw was not merely a 500
status: under `MockMvc`, `mockMvc.perform(...)` itself threw a
`jakarta.servlet.ServletException` wrapping that `RuntimeException`, rather
than yielding a captured response with a status code. Task 5 left the test
asserting on that thrown exception as as-is behavior, deferring the real fix.

Task 6 resolved this. `GameRoomController.streamRooms` now throws
`UnauthorizedException("Missing token for SSE")`, and the new
`GlobalExceptionHandler` (`@RestControllerAdvice`) translates it into a
genuine `401 Unauthorized` response. `theSseStreamRejectsAMissingToken` was
updated in the same commit to assert `status().isUnauthorized()` directly,
and no longer needs `assertThrows`/`ServletException`.

## `RoomsEventBroadcaster` still broadcasts entities, not DTOs (Task 9)

Task 9 moved `GameRoomController`'s REST handlers (`listRooms`, `createRoom`,
`joinRoom`, `getRoom`) onto the new `GameRoomDto`, so the JSON returned by
those endpoints no longer exposes the JPA entity directly. `RoomsEventBroadcaster`
was not touched: `broadcastRoomCreated`, `broadcastRoomUpdated` and
`broadcastRoomStarted` still take a `ch.multispace.backend.model.GameRoom` and
serialize it straight onto the `/api/rooms/stream` SSE channel. `host` carries
`@JsonIgnore`, so the entity's sensitive relation still doesn't leak over SSE,
but the SSE payload shape now diverges from the REST payload shape (for
example, it has no `hostUsername`) where before Task 9 they matched by
construction. Converting the broadcaster onto `GameRoomDto` was out of scope
for this task; left for whoever next touches `RoomsEventBroadcaster`.

## `listRooms` N+1 when building `GameRoomDto` (Task 9, observed not fixed)

`GameRoomDto.from` reads `room.getHost().getUser().getUsername()`. Both
`GameRoom.host` (`@ManyToOne`) and `PlayerEntity.user` (`@OneToOne`) default to
eager fetching, so no `LazyInitializationException` was observed in the test
suite. But eager-by-default does not mean fetched-in-one-query: with SQL
logging turned on for a local run of `GameRoomControllerTest`,
`GameRoomService.listOpenRooms()` issues one `select ... from game_rooms where
status=?`, followed by one additional `select ... from players join users ...
where id=?` per room in the result set to resolve each room's host — a classic
N+1. It wasn't observable before Task 9 because `listRooms` returned entities
directly and Jackson lazily triggered the same joins during serialization
outside of any query-count assertion; Task 9 just made the pattern explicit
inside `GameRoomDto.from`. Not fixed here per the task brief — the fix is a
fetch-join or `@EntityGraph` on `GameRoomRepository.findByStatus`, a separate,
low-risk follow-up.

## Removed: spring-security-oauth2 (Task 12)

`org.springframework.security.oauth:spring-security-oauth2:2.5.2.RELEASE` is the
retired Spring Security OAuth project. It reached end of life in 2022, is pinned
here to a 2021 release, and sits inside a Spring Boot 3.5.7 application that
never calls it. It receives no security patches. The application authenticates
with jjwt (`io.jsonwebtoken`) in `security/JwtService.java`, not with OAuth2.
A grep search for oauth/OAuth references in `backend/src` produced no output,
confirming the dependency is unused and safe to remove:

```
$ grep -rn "oauth\|OAuth" backend/src
(no output)
```
