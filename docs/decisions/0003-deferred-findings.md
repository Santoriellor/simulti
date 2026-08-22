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

## Naming

Task 13 of this cycle renames the runtime `game/GameRoom` class to
`GameSession` and kebab-cases the `waitingRoom` component folder, but
deliberately leaves the frontend route path itself as `/waitingRoom`, because
renaming a live, potentially bookmarked URL needs a redirect and that is out
of scope here. That reasoning, and the URL rename it defers, is recorded by
Task 13 when it lands.
