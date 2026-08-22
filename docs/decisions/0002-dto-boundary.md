# 2. Persistence entities are not serialized to clients

Date: 2026-08-22
Status: accepted, implemented by Tasks 6 and 7 of this refactor cycle

## Context

Most controller methods in `GameRoomController` and `AuthController` return
JPA entities (`GameRoom`, `User`) directly as `ResponseEntity` bodies, relying
on field-level `@JsonIgnore` annotations to keep sensitive or internal state
out of the JSON response. That mechanism has already failed once, in
production: `AuthController.getCurrentUser` (`GET /api/auth/me`) returns the
`User` entity, and `User.password` — the BCrypt hash of the user's password —
carries no `@JsonIgnore`, unlike the entity's other sensitive field,
`playerProfile`, which does. Every call to `/me` therefore serializes the
caller's password hash into the response body sent to the browser. Nothing
about this is visible from the controller method signature; it can only be
found by reading the entity and noticing which annotation is missing, and
the same failure mode is latent everywhere else an entity is returned
directly — the boundary depends entirely on nobody forgetting an annotation
on a class whose primary job is mapping to a database table, not describing
an API contract.

## Evidence

Before the fix, `AuthControllerTest.meNeverReturnsThePasswordHash` was run
against `GET /api/auth/me` for a freshly registered user and observed this
response body, captured verbatim from the test failure output:

```json
{"id":"29d0f870-1c2d-44d9-80d7-3f2c78a7da67","email":"user1@example.com","username":"user1","password":"$2a$10$UqHQqZVk4uKUiONOZlwsAubynQSChq25dDIx/lbsNu9UXPFcwUa5C","createdAt":"2026-08-22T11:25:58.687383+02:00"}
```

The `password` field holds a live BCrypt hash (`$2a$10$...`) for the
registered account, sent verbatim to the client. The test failed with:

```
java.lang.AssertionError: Expected no value at JSON path "$.password" but found: '$2a$10$UqHQqZVk4uKUiONOZlwsAubynQSChq25dDIx/lbsNu9UXPFcwUa5C'
```

## Decision

Persistence entities are not serialized to API clients. Response shapes are
explicit DTOs (or DTO-shaped records, following the pattern already
established by `dtos/CreateRoomRequestDTO` and `dtos/LeaderboardRowDto`),
constructed deliberately from the entity rather than the entity being handed
to Jackson directly. `/me` moves to a `UserDto` with a static
`UserDto.from(User user)` factory that never touches `password`. This is
implemented in the current refactor cycle: Task 6 introduces the shared
error-response and exception-handling shapes at the same boundary, and Task 7
applies the DTO to `AuthController.getCurrentUser` specifically, fixing the
password-hash leak by construction rather than by remembering to annotate the
right field.

## Consequences

An endpoint's response shape is now defined by its DTO, not by whatever
fields the backing entity happens to have — adding a field to an entity for
persistence reasons no longer changes what a client receives. The cost is
boilerplate: every response type that used to be "the entity" now needs an
explicit DTO and a mapping step. This decision does not, by itself, migrate
every remaining entity-returning endpoint (for example `GameRoomController`
still returns `GameRoom` entities directly in several methods); it establishes
the rule and fixes the one confirmed live leak. Extending it to the rest of
the API surface is future work, not committed to by this cycle.
