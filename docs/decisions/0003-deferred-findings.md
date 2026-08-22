# 3. Deferred findings

Date: 2026-08-22
Status: living document — appended to by Tasks 1, 2, 3, 4, 6, 9, 11, 12, 13
and 15 of this refactor cycle as they complete

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

## Kept the runtime GameRoom class off the URL rename (Task 13)

Task 13 renamed `backend/src/main/java/ch/multispace/backend/game/GameRoom.java`
to `GameSession.java` (the in-memory simulation state driven by `GameLoop`),
leaving `model/GameRoom.java` (the JPA entity named for the `game_rooms`
table) untouched. It also moved the frontend `waitingRoom/` component folder
to kebab-case `waiting-room/`, matching every other component folder.

The route path `waitingRoom` (registered in `app.routes.ts`, and linked from
`home.component.html` and `space-invaders.component.ts`) was deliberately
left unchanged. The folder rename is purely internal -- it only affects an
import path -- so it is free. The URL is user-visible: it may be bookmarked,
and changing it without a redirect silently breaks any saved or hardcoded
link. Renaming the path safely needs a redirect from the old path to the new
one, which is a behavior change and out of scope for a pure-rename task.
Deferred to whoever next touches routing.

## Stale `logging.file.name` comment in `backend/Dockerfile` (Task 1)

The runtime stage comment says "application.yml sets `logging.file.name=logs/app.log`,
which resolves under this working directory" and creates `/app/logs` for it.
`application.yml`'s `logging:` block only sets `logging.level`; there is no
`logging.file.name` key anywhere in the backend. The application logs to
stdout, which is what the container runtime actually captures. Left as a
stale comment plus a harmless unused directory — correcting or removing it is
a documentation-only change with no runtime effect, out of scope here.

## Redundant status assertion in `AuthControllerTest` (Task 2)

`meReturnsTheIdentityOfTheBearer` asserts `status().isOk()` via MockMvc and
then also asserts `assertEquals(200, result.getResponse().getStatus())` on
the same result — the second check can never disagree with the first. Left
as harmless redundancy in a characterization test rather than trimmed, since
touching test assertions was out of scope for the task that wrote it.

## `joinRoom` on a full room returns an opaque empty-body 400 (Task 3)

`GameRoomController.joinRoom` falls back to `ResponseEntity.badRequest().build()`
whenever `GameRoomService.joinRoom` returns an empty `Optional` — which
happens both when the room doesn't exist and when it's already full. The
client gets a bare 400 with no body in either case, indistinguishable from
each other or from any other empty-Optional outcome. Left as is: giving this
a real error shape means threading a reason out of the service layer instead
of an `Optional`, which is a small API change out of scope for a
characterization/DTO task.

## `as never` cast in `auth.service.spec.ts` (Task 4)

The login spec asserts the emitted user with
`expect(emitted).toEqual({...} as never)` rather than typing the literal
against `User` directly. It compiles and passes, but the cast silences any
type-checking on the expected shape rather than proving it matches. Left as
is; retyping it against `User` is a small, low-risk cleanup that wasn't in
scope for the characterization task that wrote it.

## Logout test doesn't assert navigation (Task 4)

`clears the token and the current user on logout` in `auth.service.spec.ts`
calls `service.logout()`, which internally calls
`router.navigate(['/auth/login'])`, under `TestBed` configured with
`provideRouter([])` — a router with no routes at all. The test passes only
because an unmatched navigation doesn't throw; it never asserts that
navigation to `/auth/login` was actually attempted (e.g. via a spy on
`router.navigate`). Left as is: it characterizes the token/state-clearing
behavior it names, just not the redirect, and tightening it wasn't in scope
for Task 4.

## `TokenResponse` made `public` unnecessarily (Task 6)

`AuthController.TokenResponse` is declared `public static class`, but it is
only ever constructed and returned from within `AuthController` itself in
the same package — it compiles equally well package-private. Left `public`;
narrowing its visibility is a no-behavior-change cleanup that wasn't in
scope for Task 6.

## `handleUnexpected` will log SSE client disconnects as errors (Task 6)

`GlobalExceptionHandler.handleUnexpected` catches `Exception.class` as a
catch-all and logs every instance at `ERROR` with a full stack trace. When a
browser tab with an open `/api/rooms/stream` `EventSource` is closed, Spring
MVC's async dispatch throws `AsyncRequestNotUsableException` because the
client is gone — a routine, expected event, not a bug. That exception isn't
handled by any more specific `@ExceptionHandler` here, so it falls through
to `handleUnexpected` and logs an `ERROR`-level stack trace on every tab
close. Left as is: adding a specific, quiet handler for this one exception
type is a small follow-up, not addressed by Task 6.

## Two error shapes on the API, and `ErrorResponse`'s javadoc is now inaccurate (Task 6)

`GlobalExceptionHandler`'s own handlers return `{"error": "..."}` for every
exception the application throws itself. But framework-level 4xx responses —
malformed JSON, a wrong HTTP verb, an unsupported content type, an unmapped
route — are handled by `ResponseEntityExceptionHandler`'s inherited handlers
(deliberately not overridden; see the class javadoc) and return RFC 7807
`application/problem+json` bodies instead, with a different shape entirely.
This means `ErrorResponse`'s javadoc, "The single error shape returned by
every failing endpoint", is no longer accurate — there are two shapes,
selected by which layer catches the failure. Additionally, the framework's
own 404 for an unmapped path leaks an internal detail into the response body,
e.g. `"No static resource api/does-not-exist."`, wording that assumes
static-resource handling and exposes the unmatched path back to the caller.
Unifying both onto one response shape (translating the framework's
`ErrorResponseException`s in `GlobalExceptionHandler` instead of inheriting
their defaults) is a real fix, but changes response bodies API clients may
already depend on, so it's deferred rather than done inside Task 6.

## Redundant `userDetails == null` guard in `deleteRoom` (Task 9)

`GameRoomController.deleteRoom` still checks `if (userDetails == null) throw
new UnauthorizedException(...)` before calling
`playerProvisioningService.forPrincipal(userDetails)`, even though
`forPrincipal` (introduced by Task 9) is itself null-safe and throws its own
`UnauthorizedException` when the principal is missing. The explicit guard is
now dead weight — both paths produce the same outcome. Left in place as
harmless duplication; removing it is a trivial cleanup that wasn't the focus
of Task 9's DTO/service work.

## `GameRoom.status` is an unconstrained `String`

`GameRoom.status` is a plain `String` column, not an enum, and every read of
it compares against literal state names case-insensitively (e.g.
`"STARTED".equalsIgnoreCase(room.getStatus())` in
`GameRoomController.joinRoom`). Nothing in the entity or the database
constrains it to the actual set of valid states, so a typo or an
unanticipated value would compile and persist without any error, only
surfacing later as a status nobody's comparisons match. Left as is — turning
it into an enum (and deciding how to store it: `@Enumerated(STRING)` against
the existing column, no schema change needed) is a real improvement but
touches every read and write site and wasn't in scope for this cycle.

## Repeated `form.get('email')?.errors?.[...]` lookups in auth templates (Task 11)

`login.component.html` and `register.component.html` both call
`form.get('email')` three separate times (once for the wrapping `@if`, once
per error key inside it) instead of hoisting it to a single template
reference or a component getter. Task 11's substantive work was typing the
reactive form and its component logic against a real interface; cleaning up
this template repetition was left alone as a separate, cosmetic concern.
