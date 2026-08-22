# space-multi Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Document space-multi to the estate standard, pin its current behaviour with tests, fix four defects found during the survey, and refactor the code onto one consistent set of patterns without changing its stack.

**Architecture:** Spring Boot REST + WebSocket backend behind nginx, Angular 20 standalone SPA, PostgreSQL 16 with `database/init.sql` as the schema of record and Hibernate in `validate` mode. Work proceeds in four phases — document, characterize, refactor, verify — where the characterization suite is what makes the refactor phase safe. The refactor introduces a DTO layer at the API boundary so persistence entities stop being serialized to clients, and centralizes error translation in one `@RestControllerAdvice` so controllers stop throwing bare `RuntimeException` for control flow.

**Tech Stack:** Java 25, Spring Boot (Spring Security, Spring Data JPA, WebSocket), Lombok, JUnit 5, MockMvc, H2 (test), Angular 20, RxJS 7, Jasmine, Karma, Maven, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-08-22-estate-refactor-design.md` (committed by Task 1)

## Global Constraints

- **Stack does not change.** No framework, build-tool or language migration. Angular stays Angular 20, Spring Boot stays Spring Boot, PostgreSQL stays PostgreSQL. (Spec D1)
- **Documentation file set is fixed and named exactly:** `README.md`, `docs/architecture.md`, `docs/design.md`, `docs/technical.md`, `docs/runbook.md`, `docs/decisions/NNNN-*.md`. No `CLAUDE.md`. (Spec D2)
- **Characterization tests assert current behaviour, never desired behaviour.** (Spec D3)
- **Security defects are the sole exception:** they are fixed TDD-style against the corrected behaviour, never pinned. (Spec D8)
- **The formatter sweep is one commit containing formatting only**, and its SHA is appended to `.git-blame-ignore-revs`. (Spec D4)
- **`database/init.sql` is the schema of record.** `ddl-auto` stays `validate`. Any entity change requires a matching edit to `init.sql` in the same commit plus a hand-written `ALTER` for existing environments. Never set `ddl-auto` back to `update`.
- **`deploy.yml` gates deploy on tests.** A red suite blocks deployment of a live site. Never commit a failing or flaky test to `main`.
- **Java formatting:** Spotless with google-java-format, 4-space indent preserved via `.editorconfig` where it already exists.
- **Branch:** all work happens on `refactor/space-multi`; merges to `main` are performed by the reviewing session, not by the executor. (Spec D6)

---

## File Structure

**Created — documentation**

| File | Responsibility |
|---|---|
| `docs/architecture.md` | Components, boundaries, request and WebSocket data flow, deployment topology |
| `docs/design.md` | Domain model (User, PlayerEntity, GameRoom, GameResult), room lifecycle, game loop intent |
| `docs/technical.md` | Build, run, environment variables, secrets layout, CI, schema-of-record rule |
| `docs/runbook.md` | Logs, backup and restore of `pgdata`, common incidents |
| `docs/decisions/0001-schema-of-record.md` | Records the existing `ddl-auto: validate` decision |
| `docs/decisions/0002-dto-boundary.md` | Why entities stop crossing the API boundary |
| `docs/decisions/0003-deferred-findings.md` | Known problems deliberately not fixed in this cycle |

**Created — backend production code**

| File | Responsibility |
|---|---|
| `dtos/UserDto.java` | Public shape of a user: id, username, email. No password field exists to leak. |
| `dtos/GameRoomDto.java` | Public shape of a room: roomId, roomName, status, maxPlayer, wave, playerIds, hostUsername |
| `dtos/ErrorResponse.java` | One error shape for every failure: `{ "error": "..." }` |
| `exceptions/NotFoundException.java` | Thrown when an entity lookup fails; translated to 404 |
| `exceptions/ForbiddenException.java` | Thrown when an authenticated caller lacks rights; translated to 403 |
| `exceptions/UnauthorizedException.java` | Thrown when credentials are missing or unusable; translated to 401 |
| `exceptions/GlobalExceptionHandler.java` | `@RestControllerAdvice`; the only place HTTP status is chosen for failures |
| `services/PlayerProvisioningService.java` | Resolves the `PlayerEntity` for an authenticated principal, creating it if absent. Removes duplicated find-or-create logic from the controller. |

**Modified — backend**

| File | Change |
|---|---|
| `model/User.java` | `@JsonIgnore` on `password` as defence in depth |
| `controllers/AuthController.java` | Returns `UserDto`; throws typed exceptions instead of catching and mapping inline |
| `controllers/GameRoomController.java` | Returns `GameRoomDto`; delegates player resolution; restores the ownership check; drops direct repository and `JwtService` injection |
| `services/AuthService.java` | Unchanged behaviour; exceptions move to `exceptions/` |
| `pom.xml` | Adds the Spotless plugin; removes the end-of-life `spring-security-oauth2` dependency |

**Modified — frontend**

| File | Change |
|---|---|
| `app/auth/auth.module.ts`, `app/auth/auth-routing.module.ts` | Deleted — dead code, referenced only by each other |
| `app/services/auth.service.ts` | `register()` parses JSON instead of `responseType: 'text'`; `jwtDecode` result typed |
| `environments/environment.ts` | Becomes the development configuration (localhost, `production: false`) |
| `environments/environment.production.ts` | New — the current production values |
| `angular.json` | File replacement wiring the production environment into the production build |
| `app/auth/login/login.component.ts`, `app/auth/register/register.component.ts` | Typed non-nullable forms; reads the error message the API actually sends |
| `.prettierrc` | Prettier settings, moved out of `package.json` unchanged |

**Created — tests**

| File | Responsibility |
|---|---|
| `backend/src/test/java/.../controllers/AuthControllerTest.java` | Characterizes register, login and `/me` |
| `backend/src/test/java/.../controllers/GameRoomControllerTest.java` | Characterizes list, create, join, get, delete |
| `backend/src/test/java/.../security/SecurityBoundaryTest.java` | Characterizes which endpoints require authentication |
| `frontend/src/app/services/auth.service.spec.ts` | Characterizes token storage, `currentUser$`, expiry handling |
| `frontend/src/app/guards/auth.guard.spec.ts` | Characterizes redirect-when-unauthenticated |
| `frontend/src/app/interceptors/auth.interceptor.fn.spec.ts` | Characterizes Authorization header attachment |
| `frontend/src/app/auth/login/login.component.spec.ts` | Covers the login failure message and form validation |

**Created — repository root**

| File | Responsibility |
|---|---|
| `.git-blame-ignore-revs` | Lists the formatting sweep commit so `git blame` skips it |

---

## Phase A — Document

### Task 1: Documentation set and ADRs

**Files:**
- Create: `docs/architecture.md`, `docs/design.md`, `docs/technical.md`, `docs/runbook.md`
- Create: `docs/decisions/0001-schema-of-record.md`, `docs/decisions/0002-dto-boundary.md`, `docs/decisions/0003-deferred-findings.md`
- Create: `docs/superpowers/specs/2026-08-22-estate-refactor-design.md`
- Modify: `README.md`
- Delete: `TODO.md` (its live items move into `docs/decisions/0003-deferred-findings.md`)

**Interfaces:**
- Consumes: nothing.
- Produces: `docs/decisions/0003-deferred-findings.md`, appended to by Tasks 9, 12, 13 and 15.

- [ ] **Step 1: Create the branch**

```bash
git checkout -b refactor/space-multi
```

- [ ] **Step 2: Copy the estate spec into the repository**

```bash
mkdir -p docs/superpowers/specs
cp "C:/Users/Maria/AppData/Local/Temp/claude/C--Users-Maria-Desktop-Dev-space-multi/1814e037-1eae-4438-a5b2-96a101fd483d/scratchpad/2026-08-22-estate-refactor-design.md"    docs/superpowers/specs/
```

That path is a session scratchpad and is not durable. If the file is gone, ask
for the estate design document before continuing — every constraint in this plan
derives from it, and guessing at them is worse than waiting.

- [ ] **Step 3: Read TODO.md and record what is still true**

Run: `cat TODO.md`

Every item that is still an open problem is copied into
`docs/decisions/0003-deferred-findings.md` under a heading saying why it is not
being fixed in this cycle. Items already done are dropped.

- [ ] **Step 4: Write `docs/architecture.md`**

Required sections, in this order: Overview; Components; Request flow; WebSocket
and SSE flow; Persistence; Deployment topology.

Facts that must appear, all verified during the survey:

- Three REST controllers: `AuthController` (`/api/auth`), `GameRoomController` (`/api/rooms`), `ScoreController` (`/api/leaderboard`).
- Real-time uses two separate mechanisms: a WebSocket handler (`ws/GameWebSocketHandler`) for gameplay, authenticated at handshake by `ws/JwtHandshakeInterceptor`; and an SSE stream (`GET /api/rooms/stream`) for waiting-room updates, fed by `events/RoomsEventBroadcaster`.
- The SSE endpoint accepts its token as a **query parameter** as well as an `Authorization` header, because `EventSource` cannot set headers.
- Game simulation lives in `game/GameLoop` and `game/GameRoom`, which is a different class from the JPA entity `model/GameRoom`. State that this collision exists and is resolved in Task 11.
- PostgreSQL 16; `database/init.sql` is the schema of record; Hibernate runs `ddl-auto: validate`.
- nginx serves the built Angular bundle and proxies `/api`.

- [ ] **Step 5: Write `docs/design.md`**

Required sections: Domain model; Room lifecycle; Scoring; Authentication model.

The domain model section documents `User` (credentials), `PlayerEntity` (game
profile, one-to-one with `User`), `GameRoom` (a lobby holding `playerIds`, a
`status` string and a `maxPlayer` cap), `GameResult` (a finished game's score)
and `Leaderboard`. State explicitly that `GameRoom.status` is an unconstrained
`String` whose known values are `WAITING` and `STARTED`, and that
`GameRoomController` compares it with `equalsIgnoreCase`.

- [ ] **Step 6: Write `docs/technical.md`**

Required sections: Prerequisites; Local development; Configuration; Secrets;
CI/CD; Schema changes.

Configuration must list every environment variable read by
`backend/src/main/resources/application.yml`: `SPRING_DATASOURCE_URL`,
`POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS`, and
the `app.frontend-url` key required by both `CorsConfig` and `WebSocketConfig`.

The secrets section records that production reads `JWT_SECRET` from
`/srv/secrets/simulti/jwt.env` and database credentials from
`/srv/secrets/simulti/db.env` on the VPS.

The schema changes section states the rule verbatim: change an entity, change
`database/init.sql` in the same commit, and hand-write the `ALTER` for
environments that already exist.

- [ ] **Step 7: Write `docs/runbook.md`**

Required sections: Where the logs are; Backing up `pgdata`; Restoring;
Backend refuses to start; Certificate or hostname problems.

The "backend refuses to start" section must say that with `ddl-auto: validate`
a startup failure naming a column is a genuine schema drift finding, and the
fix is an `ALTER` plus a matching `init.sql` edit — never reverting to
`ddl-auto: update`.

The hostname section must record that this project is served at
`simulti.santoriello.ch`, and that traefik answers unmatched hostnames with a
default certificate, which curl reports as exit 60 — a symptom of a missing
router, not of a broken certificate.

- [ ] **Step 8: Write the three ADRs**

`0001-schema-of-record.md` records the already-made decision that `init.sql` is
authoritative and `ddl-auto` is `validate`, with the consequence that entity and
schema changes are coupled.

`0002-dto-boundary.md` records the decision Tasks 6 and 7 implement: persistence
entities are not serialized to clients. Context is the `/me` password-hash leak.

`0003-deferred-findings.md` records what this cycle deliberately leaves alone,
including surviving `TODO.md` items and the dead `game_sessions` and
`game_session_players` tables noted in `database/init.sql`.

- [ ] **Step 9: Rewrite `README.md` as an entry point**

It states what the project is in two sentences, gives the shortest path to
running it locally, and links to each of the four `docs/` files. Detail lives in
`docs/`, not in the README.

- [ ] **Step 10: Verify no production code changed**

Run: `git status --porcelain`
Expected: only files under `docs/`, plus `README.md` and the deleted `TODO.md`.
If anything under `backend/src` or `frontend/src` appears, revert it — this
phase changes no code.

- [ ] **Step 11: Commit**

```bash
git add docs README.md
git rm TODO.md
git commit -m "docs: document architecture, design, technical detail and runbook"
```

---

## Phase B — Characterize

### Task 2: Backend characterization — authentication endpoints

**Files:**
- Create: `backend/src/test/java/ch/multispace/backend/controllers/AuthControllerTest.java`
- Test: the file above

**Interfaces:**
- Consumes: the existing test harness — `backend/src/test/resources/application.yml` already configures H2 in PostgreSQL mode with `ddl-auto: create-drop`, a test JWT secret and `app.frontend-url`. Do not modify it.
- Produces: the `registerAndGetToken` helper pattern reused by Task 3.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/ch/multispace/backend/controllers/AuthControllerTest.java`:

```java
package ch.multispace.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Characterization tests: these assert what the API does today, so that the
 * refactor can be shown not to change it. Where a test documents behaviour we
 * intend to change on purpose, it says so and names the task that changes it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static int counter = 0;

    /** Registers a fresh user and returns its JWT. */
    private String registerAndGetToken() throws Exception {
        String unique = "user" + (++counter);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@example.com","username":"%s","password":"Passw0rd!"}
                                """.formatted(unique, unique)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @Test
    void registerReturnsAToken() throws Exception {
        String token = registerAndGetToken();
        assertTrue(token != null && !token.isBlank(), "register must return a non-blank token");
    }

    @Test
    void registerRejectsDuplicateEmailWith400() throws Exception {
        String body = """
                {"email":"dupe@example.com","username":"dupe1","password":"Passw0rd!"}
                """;
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"dupe@example.com","username":"dupe2","password":"Passw0rd!"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"login@example.com","username":"loginuser","password":"Passw0rd!"}
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"login@example.com","password":"wrong"}
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void meReturnsTheIdentityOfTheBearer() throws Exception {
        String token = registerAndGetToken();
        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.email").exists())
                .andReturn();
        assertEquals(200, result.getResponse().getStatus());
    }
}
```

Note what this file deliberately does **not** assert: that `/me` returns a
`password` field. It does today, and Task 6 removes it. Pinning it would make
the deploy gate defend the leak.

- [ ] **Step 2: Run the tests to see them pass or fail honestly**

Run: `cd backend && mvn -B test -Dtest=AuthControllerTest`

Expected: all five pass. These characterize existing behaviour, so passing on
the first run is the correct outcome — it confirms the harness works and the
assertions match reality.

If `meRequiresAuthentication` fails because an unauthenticated `/me` returns
500 rather than a 4xx, that is a finding: record it in
`docs/decisions/0003-deferred-findings.md`, change the assertion to
`status().is5xxServerError()` so it documents the truth, and note that Task 8
changes it.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/ch/multispace/backend/controllers/AuthControllerTest.java
git commit -m "test: characterize the authentication endpoints"
```

### Task 3: Backend characterization — room endpoints

**Files:**
- Create: `backend/src/test/java/ch/multispace/backend/controllers/GameRoomControllerTest.java`
- Test: the file above

**Interfaces:**
- Consumes: the `registerAndGetToken` pattern from Task 2, repeated here rather than shared, so each test file stands alone.
- Produces: the failing-by-design delete test that Task 7 turns green.

- [ ] **Step 1: Write the test**

Create `backend/src/test/java/ch/multispace/backend/controllers/GameRoomControllerTest.java`:

```java
package ch.multispace.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GameRoomControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static int counter = 0;

    private String registerAndGetToken() throws Exception {
        String unique = "room" + (++counter);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s@example.com","username":"%s","password":"Passw0rd!"}
                                """.formatted(unique, unique)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String createRoom(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("roomId").asText();
    }

    @Test
    void listingRoomsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void createdRoomIsReturnedWithItsIdentityAndStatus() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(post("/api/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alpha"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").exists())
                .andExpect(jsonPath("$.roomName").value("Alpha"))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void aCreatedRoomAppearsInTheOpenRoomList() throws Exception {
        String token = registerAndGetToken();
        String roomId = createRoom(token, "Bravo");

        MvcResult result = mockMvc.perform(get("/api/rooms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains(roomId), "the open room list must contain the room just created");
    }

    @Test
    void fetchingAnUnknownRoomReturns404() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(get("/api/rooms/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSecondPlayerCanJoinAnOpenRoom() throws Exception {
        String hostToken = registerAndGetToken();
        String roomId = createRoom(hostToken, "Charlie");

        String joinerToken = registerAndGetToken();
        mockMvc.perform(post("/api/rooms/" + roomId + "/join")
                        .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId));
    }

    @Test
    void theHostCanDeleteItsOwnRoom() throws Exception {
        String token = registerAndGetToken();
        String roomId = createRoom(token, "Delta");

        mockMvc.perform(delete("/api/rooms/" + roomId + "/delete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // and it is gone afterwards
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `cd backend && mvn -B test -Dtest=GameRoomControllerTest`

Expected: all six pass.

If `fetchingAnUnknownRoomReturns404` fails, read the actual status before
changing anything. `getRoom` returns `ResponseEntity.notFound()` so 404 is
expected; a 500 instead means an exception escaped, which is a finding for
Task 8 — record it and assert the real status.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/ch/multispace/backend/controllers/GameRoomControllerTest.java
git commit -m "test: characterize the room endpoints"
```

### Task 4: Frontend characterization — auth service, guard and interceptor

**Files:**
- Create: `frontend/src/app/services/auth.service.spec.ts`
- Create: `frontend/src/app/guards/auth.guard.spec.ts`
- Create: `frontend/src/app/interceptors/auth.interceptor.fn.spec.ts`
- Test: the three files above

**Interfaces:**
- Consumes: the provider pattern already used in `frontend/src/app/app.spec.ts` — `provideHttpClient`, `provideHttpClientTesting`, `provideRouter([])`.
- Produces: nothing later tasks import.

- [ ] **Step 1: Read the units under test**

Run:

```bash
cat frontend/src/app/guards/auth.guard.ts frontend/src/app/interceptors/auth.interceptor.fn.ts
```

Write the assertions below against what these files actually do. If the guard
redirects somewhere other than `/auth/login`, or the interceptor uses a header
name other than `Authorization`, correct the specs to match reality — these are
characterization tests.

- [ ] **Step 2: Write the auth service spec**

Create `frontend/src/app/services/auth.service.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('reports an absent token as not authenticated', () => {
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('reports a malformed token as not authenticated', () => {
    localStorage.setItem('auth_token', 'not-a-jwt');
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('reports an expired token as not authenticated', () => {
    const expired = makeJwt({ exp: Math.floor(Date.now() / 1000) - 60 });
    localStorage.setItem('auth_token', expired);
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('reports an unexpired token as authenticated', () => {
    const valid = makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 });
    localStorage.setItem('auth_token', valid);
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('stores the token on login and then loads the profile', () => {
    let emitted: unknown = null;
    service.currentUser$.subscribe((u) => (emitted = u));

    service.login('a@example.com', 'pw').subscribe();

    const loginReq = httpMock.expectOne(`${environment.apiUrl}/auth/login`);
    expect(loginReq.request.method).toBe('POST');
    loginReq.flush({ token: makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 }) });

    const meReq = httpMock.expectOne(`${environment.apiUrl}/auth/me`);
    expect(meReq.request.method).toBe('GET');
    meReq.flush({ id: 'u1', username: 'alice', email: 'a@example.com' });

    expect(localStorage.getItem('auth_token')).not.toBeNull();
    expect(emitted).toEqual({ id: 'u1', username: 'alice', email: 'a@example.com' } as never);
  });

  it('clears the token and the current user on logout', () => {
    localStorage.setItem('auth_token', makeJwt({ exp: Math.floor(Date.now() / 1000) + 3600 }));
    service.logout();
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(service.getCurrentUser()).toBeNull();
  });
});

/** Builds an unsigned JWT whose payload decodes; jwtDecode does not verify signatures. */
function makeJwt(payload: Record<string, unknown>): string {
  const encode = (o: Record<string, unknown>) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}
```

- [ ] **Step 3: Write the guard spec**

Create `frontend/src/app/guards/auth.guard.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
  });

  afterEach(() => localStorage.clear());

  it('blocks an unauthenticated visitor', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBeFalse();
    expect(router.navigate).toHaveBeenCalled();
  });

  it('admits a visitor holding an unexpired token', () => {
    const encode = (o: Record<string, unknown>) =>
      btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const token =
      `${encode({ alg: 'HS256', typ: 'JWT' })}.` +
      `${encode({ exp: Math.floor(Date.now() / 1000) + 3600 })}.sig`;
    localStorage.setItem('auth_token', token);

    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBeTrue();
  });
});
```

- [ ] **Step 4: Write the interceptor spec**

Create `frontend/src/app/interceptors/auth.interceptor.fn.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { authInterceptorFn } from './auth.interceptor.fn';

describe('authInterceptorFn', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptorFn])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('sends no Authorization header when no token is stored', () => {
    http.get('/api/anything').subscribe();
    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('attaches the stored token as a Bearer credential', () => {
    localStorage.setItem('auth_token', 'stored-token');
    http.get('/api/anything').subscribe();
    const req = httpMock.expectOne('/api/anything');
    expect(req.request.headers.get('Authorization')).toBe('Bearer stored-token');
    req.flush({});
  });
});
```

- [ ] **Step 5: Run the frontend suite**

Run: `cd frontend && npx ng test --watch=false`

Expected: all specs pass, including the pre-existing `app.spec.ts`.

Any spec that fails is telling you the real behaviour differs from the
assumption in the code above. Read the source, correct the assertion to match
what the code does today, and note the surprise in
`docs/decisions/0003-deferred-findings.md`.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/services/auth.service.spec.ts \
        frontend/src/app/guards/auth.guard.spec.ts \
        frontend/src/app/interceptors/auth.interceptor.fn.spec.ts
git commit -m "test: characterize the frontend authentication path"
```

### Task 5: Characterize the security boundary

**Files:**
- Create: `backend/src/test/java/ch/multispace/backend/security/SecurityBoundaryTest.java`
- Test: the file above

**Interfaces:**
- Consumes: nothing.
- Produces: the record of which endpoints are public, which Task 8 must not change.

- [ ] **Step 1: Read the security configuration**

Run: `cat backend/src/main/java/ch/multispace/backend/config/SecurityConfig.java`

List every matcher that is `permitAll`. The test below must match that list
exactly. If it does not, the test is wrong, not the configuration.

- [ ] **Step 2: Write the test**

Create `backend/src/test/java/ch/multispace/backend/security/SecurityBoundaryTest.java`:

```java
package ch.multispace.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins which endpoints are reachable without credentials. If a refactor makes a
 * protected endpoint public, one of these tests fails and the deploy is blocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityBoundaryTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void registerIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"public@example.com","username":"publicuser","password":"Passw0rd!"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void loginIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"nobody@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserIsProtected() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().is4xxClientError());
    }

    @Test
    void roomListingIsProtected() throws Exception {
        mockMvc.perform(get("/api/rooms")).andExpect(status().is4xxClientError());
    }

    @Test
    void roomCreationIsProtected() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType("application/json")
                        .content("""
                                {"name":"nope"}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void theSseStreamRejectsAMissingToken() throws Exception {
        mockMvc.perform(get("/api/rooms/stream")).andExpect(status().is4xxClientError());
    }
}
```

- [ ] **Step 3: Run the test**

Run: `cd backend && mvn -B test -Dtest=SecurityBoundaryTest`

Expected: all six pass.

`theSseStreamRejectsAMissingToken` is the one to watch. `streamRooms` throws a
bare `RuntimeException` when the token is missing, which Spring translates to
500, not 4xx. If it fails with 500, that confirms the finding: change the
assertion to `is5xxServerError()`, record it in
`docs/decisions/0003-deferred-findings.md`, and note that Task 8 corrects it to
401 and updates this assertion in the same commit.

- [ ] **Step 4: Run the whole backend suite**

Run: `cd backend && mvn -B test`
Expected: PASS, including the pre-existing `BackendApplicationTests` and `JwtServiceTest`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/ch/multispace/backend/security/SecurityBoundaryTest.java
git commit -m "test: pin which endpoints are reachable without credentials"
```

---

## Phase C — Refactor

**Do not start this phase until Tasks 2 to 5 are committed and both `mvn -B test`
and `npx ng test --watch=false` are green.**

Task order in this phase is a dependency order, not a preference. Error
translation comes first because the two security fixes assert specific status
codes that only exist once the handler does. The formatter sweep comes last
because running it earlier would mix reformatting into every review diff above.

### Task 6: Centralize error translation

**Files:**
- Create: `backend/src/main/java/ch/multispace/backend/dtos/ErrorResponse.java`
- Create: `backend/src/main/java/ch/multispace/backend/exceptions/NotFoundException.java`
- Create: `backend/src/main/java/ch/multispace/backend/exceptions/ForbiddenException.java`
- Create: `backend/src/main/java/ch/multispace/backend/exceptions/UnauthorizedException.java`
- Create: `backend/src/main/java/ch/multispace/backend/exceptions/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/ch/multispace/backend/controllers/AuthController.java`
- Modify: `backend/src/main/java/ch/multispace/backend/controllers/GameRoomController.java`
- Test: `backend/src/test/java/ch/multispace/backend/security/SecurityBoundaryTest.java`

**Interfaces:**
- Consumes: `AuthService.DuplicateEmailException`, `AuthService.DuplicateUsernameException`, `AuthService.InvalidCredentialsException` — existing nested types, left where they are.
- Produces: `NotFoundException(String message)` → 404, `ForbiddenException(String message)` → 403, `UnauthorizedException(String message)` → 401, `ErrorResponse(String error)`. Tasks 7, 8 and 9 all throw these.

- [ ] **Step 1: Write the failing test**

In `SecurityBoundaryTest`, change `theSseStreamRejectsAMissingToken` to assert
the corrected status:

```java
    @Test
    void theSseStreamRejectsAMissingToken() throws Exception {
        mockMvc.perform(get("/api/rooms/stream"))
                .andExpect(status().isUnauthorized());
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && mvn -B test -Dtest=SecurityBoundaryTest#theSseStreamRejectsAMissingToken`

Expected: FAIL. `streamRooms` throws a bare `RuntimeException` when the token is
missing, which Spring reports as 500, not 401.

- [ ] **Step 3: Create the error shape**

Create `backend/src/main/java/ch/multispace/backend/dtos/ErrorResponse.java`:

```java
package ch.multispace.backend.dtos;

/** The single error shape returned by every failing endpoint. */
public record ErrorResponse(String error) {
}
```

The key is `error`, matching the `Map.of("error", ...)` shape `AuthController`
already returned, so the client contract does not change.

- [ ] **Step 4: Create the three exception types**

Create `backend/src/main/java/ch/multispace/backend/exceptions/NotFoundException.java`:

```java
package ch.multispace.backend.exceptions;

/** Thrown when a lookup finds nothing. Translated to 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

Create `backend/src/main/java/ch/multispace/backend/exceptions/ForbiddenException.java`:

```java
package ch.multispace.backend.exceptions;

/** Thrown when an authenticated caller lacks the right to act. Translated to 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
```

Create `backend/src/main/java/ch/multispace/backend/exceptions/UnauthorizedException.java`:

```java
package ch.multispace.backend.exceptions;

/** Thrown when credentials are missing or unusable. Translated to 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Create the handler**

Create `backend/src/main/java/ch/multispace/backend/exceptions/GlobalExceptionHandler.java`:

```java
package ch.multispace.backend.exceptions;

import ch.multispace.backend.dtos.ErrorResponse;
import ch.multispace.backend.services.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The only place an HTTP status is chosen for a failure. Controllers throw
 * meaning; this class turns meaning into a status code and one error shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({UnauthorizedException.class, AuthService.InvalidCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({AuthService.DuplicateEmailException.class,
                       AuthService.DuplicateUsernameException.class})
    public ResponseEntity<ErrorResponse> handleDuplicate(RuntimeException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Anything unmapped is a bug, not a client error. It is logged with its
     * stack trace and reported without internal detail.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        LOGGER.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error"));
    }
}
```

- [ ] **Step 6: Simplify `AuthController`**

Delete the `try`/`catch` blocks and the now-unused `java.util.Map` import; the
handler translates instead.

```java
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
        String token = authService.register(
                request.getEmail(), request.getUsername(), request.getPassword());
        return ResponseEntity.ok(new TokenResponse(token));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(new TokenResponse(token));
    }
```

`TokenResponse` is currently package-private. Change its declaration to
`public static class TokenResponse` so it can be a public return type.

Also replace the `/me` lookup's bare throw:

```java
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found"));
```

- [ ] **Step 7: Replace the bare throws in `GameRoomController`**

Four substitutions, all mechanical:

| Current | Replacement |
|---|---|
| `throw new RuntimeException("Missing token for SSE")` | `throw new UnauthorizedException("Missing token for SSE")` |
| `throw new RuntimeException("userDetails is null! Authentication failed?")` | `throw new UnauthorizedException("Authentication required")` |
| `orElseThrow(() -> new RuntimeException("User not found"))` | `orElseThrow(() -> new NotFoundException("User not found"))` |
| `orElseThrow(() -> new RuntimeException("Room not found"))` | `orElseThrow(() -> new NotFoundException("Room not found"))` |

Add `import ch.multispace.backend.exceptions.NotFoundException;` and
`import ch.multispace.backend.exceptions.UnauthorizedException;`.

Confirm none were missed:

```bash
grep -n "new RuntimeException" backend/src/main/java/ch/multispace/backend/controllers/
```

Expected: no output.

- [ ] **Step 8: Run the full backend suite**

Run: `cd backend && mvn -B test`

Expected: PASS.

`registerRejectsDuplicateEmailWith400` and `loginWithWrongPasswordReturns401`
from Task 2 must pass **unchanged**. They assert both the status and the
`$.error` field, which is precisely the contract this refactor had to preserve.
If either breaks, the handler's shape is wrong — fix the handler, not the test.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/ch/multispace/backend/exceptions \
        backend/src/main/java/ch/multispace/backend/dtos/ErrorResponse.java \
        backend/src/main/java/ch/multispace/backend/controllers \
        backend/src/test/java/ch/multispace/backend/security/SecurityBoundaryTest.java
git commit -m "refactor: translate failures in one place"
```

### Task 7: Stop serializing the password hash

A security fix, so it asserts corrected behaviour rather than current behaviour
(Spec D8).

**Files:**
- Create: `backend/src/main/java/ch/multispace/backend/dtos/UserDto.java`
- Modify: `backend/src/main/java/ch/multispace/backend/model/User.java`
- Modify: `backend/src/main/java/ch/multispace/backend/controllers/AuthController.java`
- Test: `backend/src/test/java/ch/multispace/backend/controllers/AuthControllerTest.java`

**Interfaces:**
- Consumes: `NotFoundException` from Task 6.
- Produces: `UserDto.from(User user)` — a static factory returning `UserDto`.

- [ ] **Step 1: Write the failing test**

Append to `AuthControllerTest`:

```java
    @Test
    void meNeverReturnsThePasswordHash() throws Exception {
        String token = registerAndGetToken();
        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        assertTrue(!result.getResponse().getContentAsString().contains("$2a$"),
                "the response must not contain a BCrypt hash");
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && mvn -B test -Dtest=AuthControllerTest#meNeverReturnsThePasswordHash`

Expected: FAIL — the JSON contains a `password` field holding a `$2a$` BCrypt hash.

This failure is the bug, reproduced. Paste the observed response body into
`docs/decisions/0002-dto-boundary.md` as the evidence for the decision.

- [ ] **Step 3: Create the DTO**

Create `backend/src/main/java/ch/multispace/backend/dtos/UserDto.java`:

```java
package ch.multispace.backend.dtos;

import ch.multispace.backend.model.User;

import java.util.UUID;

/**
 * The public shape of a user. There is no password field, so no future change
 * to the entity can reintroduce the leak this type was created to close.
 */
public record UserDto(UUID id, String username, String email) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail());
    }
}
```

- [ ] **Step 4: Add defence in depth on the entity**

In `backend/src/main/java/ch/multispace/backend/model/User.java`, annotate the
password field. `@JsonIgnore` is already imported by this file.

```java
    @JsonIgnore
    @Column(nullable = false)
    private String password;
```

This is a serialization annotation only. It adds, removes and renames no column,
so `database/init.sql` does not change — see Task 15, step 4.

- [ ] **Step 5: Return the DTO from the controller**

In `AuthController`:

```java
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User userDetails) {
        // userDetails.getUsername() contains the email
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return ResponseEntity.ok(UserDto.from(user));
    }
```

Add `import ch.multispace.backend.dtos.UserDto;`.

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && mvn -B test -Dtest=AuthControllerTest`

Expected: PASS, all six tests. `meReturnsTheIdentityOfTheBearer` from Task 2
must still pass, because `id`, `username` and `email` are unchanged — that is
what proves the fix removed only the hash.

- [ ] **Step 7: Confirm the frontend contract is unaffected**

Run: `cat frontend/src/app/models/user.model.ts`

Expected: the `User` interface declares `id`, `username?` and `email?` only. It
never referenced `password`, so no frontend change is needed.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/ch/multispace/backend/dtos/UserDto.java \
        backend/src/main/java/ch/multispace/backend/model/User.java \
        backend/src/main/java/ch/multispace/backend/controllers/AuthController.java \
        backend/src/test/java/ch/multispace/backend/controllers/AuthControllerTest.java
git commit -m "security: stop returning the password hash from /api/auth/me"
```

### Task 8: Restore the room ownership check

Also a security fix, so it asserts corrected behaviour (Spec D8).

**Files:**
- Modify: `backend/src/main/java/ch/multispace/backend/controllers/GameRoomController.java`
- Test: `backend/src/test/java/ch/multispace/backend/controllers/GameRoomControllerTest.java`

**Interfaces:**
- Consumes: `NotFoundException`, `ForbiddenException`, `UnauthorizedException` from Task 6.
- Produces: nothing.

- [ ] **Step 1: Write the failing test**

Append to `GameRoomControllerTest`:

```java
    @Test
    void aStrangerCannotDeleteSomeoneElsesRoom() throws Exception {
        String hostToken = registerAndGetToken();
        String roomId = createRoom(hostToken, "Echo");

        String strangerToken = registerAndGetToken();
        mockMvc.perform(delete("/api/rooms/" + roomId + "/delete")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        // and the room must still be there
        mockMvc.perform(get("/api/rooms/" + roomId)
                        .header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
    }
```

The second assertion matters as much as the first. A 403 that still deleted the
room would pass a status-only test.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && mvn -B test -Dtest=GameRoomControllerTest#aStrangerCannotDeleteSomeoneElsesRoom`

Expected: FAIL — the delete returns 204 and the follow-up fetch returns 404,
because the ownership check is commented out.

- [ ] **Step 3: Restore the check**

In `GameRoomController.deleteRoom`, replace the two commented-out blocks and the
null guard with a real check. The room's owner is reached through
`GameRoom.getHost().getUser()`.

```java
    @DeleteMapping("/{roomId}/delete")
    public ResponseEntity<Void> deleteRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID roomId
    ) {
        if (userDetails == null) {
            throw new UnauthorizedException("Authentication required");
        }

        User user = userRepository
                .findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found"));

        GameRoom room = gameRoomService.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        if (room.getHost() == null || !room.getHost().getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the host may delete this room");
        }

        gameRoomService.deleteRoom(room);
        roomsEventBroadcaster.broadcastRoomDeleted(room.getRoomId());

        return ResponseEntity.noContent().build();
    }
```

Compare by `getId()` rather than by `equals`. `User` is a Lombok entity with no
generated `equals`, so identity comparison across two Hibernate sessions is not
reliable.

Add `import ch.multispace.backend.exceptions.ForbiddenException;`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && mvn -B test -Dtest=GameRoomControllerTest`

Expected: PASS, all seven tests. `theHostCanDeleteItsOwnRoom` from Task 3 must
still pass — if it now returns 403, the host comparison is inverted or the host
is not being set on creation. Fix the comparison, not the test.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/ch/multispace/backend/controllers/GameRoomController.java \
        backend/src/test/java/ch/multispace/backend/controllers/GameRoomControllerTest.java
git commit -m "security: only the host may delete a room"
```

### Task 9: Move player provisioning out of the controller

**Files:**
- Create: `backend/src/main/java/ch/multispace/backend/services/PlayerProvisioningService.java`
- Create: `backend/src/main/java/ch/multispace/backend/dtos/GameRoomDto.java`
- Modify: `backend/src/main/java/ch/multispace/backend/controllers/GameRoomController.java`

**Interfaces:**
- Consumes: `NotFoundException`, `UnauthorizedException` from Task 6.
- Produces: `PlayerProvisioningService.forPrincipal(UserDetails userDetails)` returning `PlayerEntity`; `GameRoomDto.from(GameRoom room)` returning `GameRoomDto`.

- [ ] **Step 1: Confirm the duplication being removed**

```bash
grep -c "orElseGet" backend/src/main/java/ch/multispace/backend/controllers/GameRoomController.java
```

Expected: `2` — the identical find-or-create-player block appears in both
`createRoom` and `joinRoom`.

- [ ] **Step 2: Create the service**

Create `backend/src/main/java/ch/multispace/backend/services/PlayerProvisioningService.java`:

```java
package ch.multispace.backend.services;

import ch.multispace.backend.exceptions.NotFoundException;
import ch.multispace.backend.exceptions.UnauthorizedException;
import ch.multispace.backend.model.PlayerEntity;
import ch.multispace.backend.model.User;
import ch.multispace.backend.repositories.PlayerRepository;
import ch.multispace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Resolves the game profile behind an authenticated principal, creating it on
 * first use. AuthService.register already creates a PlayerEntity, so the
 * create branch only fires for accounts that predate that behaviour.
 */
@Service
@RequiredArgsConstructor
public class PlayerProvisioningService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;

    public PlayerEntity forPrincipal(UserDetails userDetails) {
        if (userDetails == null) {
            throw new UnauthorizedException("Authentication required");
        }
        // UserDetails.getUsername() carries the email; see AuthService.
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found"));

        return playerRepository.findByUser(user)
                .orElseGet(() -> {
                    PlayerEntity created = new PlayerEntity();
                    created.setUser(user);
                    return playerRepository.save(created);
                });
    }
}
```

- [ ] **Step 3: Create the room DTO**

Create `backend/src/main/java/ch/multispace/backend/dtos/GameRoomDto.java`:

```java
package ch.multispace.backend.dtos;

import ch.multispace.backend.model.GameRoom;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The public shape of a room. Field names match what the entity serialized
 * before, so the frontend contract is unchanged. hostUsername is added because
 * host is @JsonIgnore'd on the entity and the waiting room had no way to name
 * the host.
 */
public record GameRoomDto(
        UUID roomId,
        String roomName,
        String status,
        Integer maxPlayer,
        Integer wave,
        List<UUID> playerIds,
        String hostUsername,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt) {

    public static GameRoomDto from(GameRoom room) {
        String hostUsername = room.getHost() != null && room.getHost().getUser() != null
                ? room.getHost().getUser().getUsername()
                : null;
        return new GameRoomDto(
                room.getRoomId(),
                room.getRoomName(),
                room.getStatus(),
                room.getMaxPlayer(),
                room.getWave(),
                room.getPlayerIds(),
                hostUsername,
                room.getStartedAt(),
                room.getEndedAt());
    }
}
```

- [ ] **Step 4: Rewrite the controller's handlers**

Replace the fields and the four handlers:

```java
    private final GameRoomService gameRoomService;
    private final PlayerProvisioningService playerProvisioningService;
    private final RoomsEventBroadcaster roomsEventBroadcaster;
    private final JwtService jwtService;

    /** List open rooms */
    @GetMapping
    public List<GameRoomDto> listRooms() {
        return gameRoomService.listOpenRooms().stream().map(GameRoomDto::from).toList();
    }

    /** Create a new room */
    @PostMapping
    public ResponseEntity<GameRoomDto> createRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CreateRoomRequestDTO request
    ) {
        PlayerEntity player = playerProvisioningService.forPrincipal(userDetails);
        GameRoom room = gameRoomService.createRoom(player, request.name());
        roomsEventBroadcaster.broadcastRoomCreated(room);
        return ResponseEntity.ok(GameRoomDto.from(room));
    }

    /** Join a room */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<GameRoomDto> joinRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        PlayerEntity player = playerProvisioningService.forPrincipal(userDetails);
        Optional<GameRoom> roomOpt = gameRoomService.joinRoom(roomId, player);
        roomOpt.ifPresent(room -> {
            roomsEventBroadcaster.broadcastRoomUpdated(room);
            if ("STARTED".equalsIgnoreCase(room.getStatus())) {
                roomsEventBroadcaster.broadcastRoomStarted(room);
            }
        });
        return roomOpt.map(room -> ResponseEntity.ok(GameRoomDto.from(room)))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    /** Get a room state */
    @GetMapping("/{roomId}")
    public ResponseEntity<GameRoomDto> getRoom(@PathVariable UUID roomId) {
        return gameRoomService.getRoom(roomId)
                .map(room -> ResponseEntity.ok(GameRoomDto.from(room)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
```

In `deleteRoom`, replace **only the user lookup** with the service, so the last
two repository fields can go:

```java
        User user = playerProvisioningService.forPrincipal(userDetails).getUser();
```

**Keep the ownership check from Task 8 exactly as it is.** This step removes a
repository dependency; it does not touch authorization. If the resulting
`deleteRoom` no longer contains the
`throw new ForbiddenException("Only the host may delete this room")` branch, you
have reintroduced the security defect Task 8 closed. Verify before moving on:

```bash
grep -n "ForbiddenException" backend/src/main/java/ch/multispace/backend/controllers/GameRoomController.java
```

Expected: one hit, inside `deleteRoom`.

Then delete the `PlayerRepository` and `UserRepository` fields and their
imports. `JwtService` stays — `streamRooms` still uses it.

Note that `.orElse(...)` became `.orElseGet(...)` in the two handlers above.
With `orElse`, Java builds the fallback `ResponseEntity` on every call even when
the Optional is present; `orElseGet` defers it. Behaviour is identical, the
allocation is not.

`RoomsEventBroadcaster` still receives entities. Converting the broadcast
payloads is out of scope here — record it in
`docs/decisions/0003-deferred-findings.md`.

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && mvn -B test`

Expected: PASS. Every room test from Task 3 asserts `roomId`, `roomName` and
`status`, all of which `GameRoomDto` preserves by name — that is what proves the
serialized contract survived the change.

- [ ] **Step 6: Verify the frontend model still matches**

Run: `cat frontend/src/app/models/game-room.model.ts`

Every field the interface declares must exist in `GameRoomDto` under the same
name. If the interface declares a field the DTO omits, add it to the DTO: the
DTO must be a superset of what the client already reads. Do not instead edit the
interface — that would be changing the client to match a regression.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/ch/multispace/backend/services/PlayerProvisioningService.java \
        backend/src/main/java/ch/multispace/backend/dtos/GameRoomDto.java \
        backend/src/main/java/ch/multispace/backend/controllers/GameRoomController.java
git commit -m "refactor: move player provisioning into a service and return DTOs"
```

### Task 10: Frontend cleanup

**Files:**
- Delete: `frontend/src/app/auth/auth.module.ts`, `frontend/src/app/auth/auth-routing.module.ts`
- Modify: `frontend/src/app/services/auth.service.ts`
- Modify: `frontend/src/environments/environment.ts`
- Create: `frontend/src/environments/environment.production.ts`
- Modify: `frontend/angular.json`
- Test: `frontend/src/app/services/auth.service.spec.ts`

**Interfaces:**
- Consumes: the specs from Task 4.
- Produces: nothing.

- [ ] **Step 1: Confirm the NgModules are dead before deleting them**

```bash
grep -rn "AuthModule\|AuthRoutingModule" frontend/src --include=*.ts
```

Expected: hits only inside `auth.module.ts` and `auth-routing.module.ts`
themselves. Routing lives in `app.routes.ts` and both components are standalone,
so nothing imports these files. If any other file appears, stop and do not
delete.

- [ ] **Step 2: Delete them, then build and test**

```bash
git rm frontend/src/app/auth/auth.module.ts frontend/src/app/auth/auth-routing.module.ts
cd frontend && npx ng test --watch=false && npx ng build
```

Expected: both PASS. A successful build is the proof the files were unreachable.

- [ ] **Step 3: Write the failing test for the register contract**

Append to `frontend/src/app/services/auth.service.spec.ts`, inside the
`describe('AuthService', ...)` block:

```ts
  it('reads the token out of the register response', () => {
    let received: string | null = null;
    service.register('n@example.com', 'newbie', 'pw').subscribe((t) => (received = t));

    const req = httpMock.expectOne(`${environment.apiUrl}/auth/register`);
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'issued-token' });

    expect(received).toBe('issued-token');
  });
```

- [ ] **Step 4: Run it to verify it fails**

Run: `cd frontend && npx ng test --watch=false`

Expected: FAIL. `register()` asks for `responseType: 'text'` against a JSON
endpoint, so it resolves with the raw body `{"token":"issued-token"}` rather
than the token.

- [ ] **Step 5: Fix `register()`**

In `frontend/src/app/services/auth.service.ts`:

```ts
  register(email: string, username: string, password: string): Observable<string> {
    const payload: RegisterRequest = { email, username, password };
    return this.http
      .post<AuthResponse>(`${this.API_URL}/register`, payload)
      .pipe(map((response) => response.token));
  }
```

Add `map` to the existing `rxjs` import. Then check every caller:

```bash
grep -rn "\.register(" frontend/src --include=*.ts
```

Any caller treating the result as a JSON string must be updated to treat it as
a bare token.

- [ ] **Step 6: Type the decoded token**

Replace the `any` in `isTokenValid`:

```ts
  private isTokenValid(token: string): boolean {
    try {
      const decoded = jwtDecode<{ exp?: number }>(token);
      if (typeof decoded.exp !== 'number') return false;
      return Date.now() < decoded.exp * 1000;
    } catch {
      return false;
    }
  }
```

This also closes a latent hole: a token whose payload carries no `exp` produced
`NaN`, and `Date.now() < NaN` is `false` only by accident of IEEE 754, not by
intent.

- [ ] **Step 7: Split the environments**

`frontend/src/environments/environment.ts` becomes the development file:

```ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  wsUrl: 'ws://localhost:8080',
};
```

Create `frontend/src/environments/environment.production.ts` holding the values
the single file used to have:

```ts
export const environment = {
  production: true,
  apiUrl: 'https://simulti.santoriello.ch/api',
  wsUrl: 'wss://simulti.santoriello.ch',
};
```

In `frontend/angular.json`, under
`projects.<name>.architect.build.configurations.production`, add:

```json
"fileReplacements": [
  {
    "replace": "src/environments/environment.ts",
    "with": "src/environments/environment.production.ts"
  }
]
```

- [ ] **Step 8: Prove the production build still points at production**

```bash
cd frontend && npx ng build --configuration production
grep -rl "simulti.santoriello.ch" dist | head
```

Expected: at least one built bundle contains the production hostname.

If nothing matches, the file replacement is not wired up and the deployed app
would call `localhost` — a silent, total outage of every API call. Stop and fix
it before committing. This check is the entire point of the step.

- [ ] **Step 9: Confirm the Dockerfile builds the production configuration**

```bash
grep -n "ng build\|npm run build" frontend/Dockerfile
```

If the Dockerfile runs a plain `ng build` or `npm run build`, the file
replacement never applies, and step 8 passed only because you named the
configuration explicitly. Either change the Dockerfile to
`ng build --configuration production`, or confirm that `production` is already
`defaultConfiguration` in `angular.json`. Do not leave this ambiguous — getting
it wrong points the live site at `localhost`.

- [ ] **Step 10: Run the full frontend suite**

Run: `cd frontend && npx ng test --watch=false`
Expected: PASS.

- [ ] **Step 11: Commit**

```bash
git add frontend/src frontend/angular.json frontend/Dockerfile
git commit -m "refactor: delete dead auth modules, fix register(), split environments"
```

### Task 11: Typed forms and the error message nobody can see

**Files:**
- Modify: `frontend/src/app/auth/login/login.component.ts`
- Modify: `frontend/src/app/auth/register/register.component.ts`
- Create: `frontend/src/app/auth/login/login.component.spec.ts`

**Interfaces:**
- Consumes: `AuthService.login`, `AuthService.register`.
- Produces: nothing.

- [ ] **Step 1: Establish what the backend actually sends on a failure**

The handler built in Task 6 returns `ErrorResponse(String error)`, serialized as
`{"error":"Invalid email or password"}`. Angular puts that parsed body on
`HttpErrorResponse.error`, so the message is at `err.error.error`.

`login.component.ts` reads `err?.error?.message`, which does not exist. Every
rejected login therefore falls through to `err?.message` — Angular's generic
"Http failure response for ..." string. The user never sees why login failed.

- [ ] **Step 2: Write the failing test**

Create `frontend/src/app/auth/login/login.component.spec.ts`:

```ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../../services/auth.service';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['login']);
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: authSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows the message the backend sent when the credentials are rejected', () => {
    authSpy.login.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: { error: 'Invalid email or password' },
          }),
      ),
    );

    component.form.setValue({ email: 'a@example.com', password: 'wrongpw' });
    component.submit();

    expect(component.error).toBe('Invalid email or password');
  });

  it('falls back to a generic message when the server sends no body', () => {
    authSpy.login.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 0, error: null })),
    );

    component.form.setValue({ email: 'a@example.com', password: 'wrongpw' });
    component.submit();

    expect(component.error).toBe('Connection failed');
  });

  it('does not call the service when the form is invalid', () => {
    component.form.setValue({ email: 'not-an-email', password: '' });
    component.submit();

    expect(authSpy.login).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 3: Run it to verify the first test fails**

Run: `cd frontend && npx ng test --watch=false`

Expected: `shows the message the backend sent` FAILS — `component.error` holds
Angular's generic HTTP failure string, not `Invalid email or password`. The
other two pass.

- [ ] **Step 4: Fix the error extraction and type the form**

In `frontend/src/app/auth/login/login.component.ts`, update the imports:

```ts
import { Component, OnDestroy, inject } from '@angular/core';
import { FormBuilder, Validators, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
```

and replace the constructor-built form with a typed, non-nullable one:

```ts
export class LoginComponent implements OnDestroy {
  error: string | null = null;
  loading = false;

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroy$ = new Subject<void>();

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error = null;
    this.loading = true;

    const { email, password } = this.form.getRawValue();
    this.auth
      .login(email, password)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.loading = false;
          void this.router.navigate(['/home']);
        },
        error: (err: HttpErrorResponse) => {
          this.loading = false;
          this.error = err?.error?.error ?? 'Connection failed';
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
```

`getRawValue()` on a `nonNullable` group returns
`{ email: string; password: string }`, so `login(email, password)` is now type
checked. Previously `form.value` was `any` and the compiler could not see a
mistake.

Delete the now-unused `FormGroup` import and the old constructor.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd frontend && npx ng test --watch=false`
Expected: PASS — all three login specs plus everything from Task 4.

- [ ] **Step 6: Apply the same two fixes to the register component**

```bash
grep -n "FormGroup\|err?.error\|this.fb.group" frontend/src/app/auth/register/register.component.ts
```

Apply the identical treatment: `fb.nonNullable.group`, `getRawValue()`, and
`err?.error?.error ?? '<existing fallback>'`. Keep whatever fallback string the
component already used — this task fixes the lookup path, not the copy.

- [ ] **Step 7: Build and test**

Run: `cd frontend && npx ng test --watch=false && npx ng build`
Expected: both PASS. `strictTemplates` is already enabled, so a template still
reading an untyped control would fail the build here.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/auth
git commit -m "fix: show the server's message when login is rejected"
```

### Task 12: Remove the end-of-life OAuth2 dependency

**Files:**
- Modify: `backend/pom.xml`
- Modify: `docs/decisions/0003-deferred-findings.md`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

- [ ] **Step 1: Prove it is unused**

```bash
grep -rn "oauth\|OAuth" backend/src
```

Expected: no output. The project authenticates with jjwt (`io.jsonwebtoken`) in
`security/JwtService.java`, not with OAuth2.

- [ ] **Step 2: Record what it is before removing it**

`org.springframework.security.oauth:spring-security-oauth2:2.5.2.RELEASE` is the
retired Spring Security OAuth project. It reached end of life in 2022, it is
pinned here to a 2021 release, and it sits inside a Spring Boot 3.5.7
application that never calls it. It receives no security patches.

Append that paragraph, plus the empty output of step 1 as the evidence it was
unused, to `docs/decisions/0003-deferred-findings.md` under a heading
`Removed: spring-security-oauth2`. The record matters more than the removal —
without it the next person to see an OAuth-shaped requirement may re-add it.

- [ ] **Step 3: Remove the dependency**

Delete this block from `backend/pom.xml`:

```xml
        <dependency>
            <groupId>org.springframework.security.oauth</groupId>
            <artifactId>spring-security-oauth2</artifactId>
            <version>2.5.2.RELEASE</version>
        </dependency>
```

- [ ] **Step 4: Verify it is gone from the resolved tree**

```bash
cd backend && mvn -B dependency:tree | grep -i "oauth" || echo "NOT PRESENT"
```

Expected: `NOT PRESENT`. If it still appears it is arriving transitively through
another dependency — do not force-exclude it. Record the finding and stop, since
that is a different problem with a different fix.

- [ ] **Step 5: Compile and test**

Run: `cd backend && mvn -B clean test`

Expected: PASS. A dependency nothing imports cannot break a build by leaving.
`spring-boot-starter-security` remains and is what actually secures the app.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml docs/decisions/0003-deferred-findings.md
git commit -m "security: drop the end-of-life spring-security-oauth2 dependency"
```

### Task 13: Naming consistency

**Files:**
- Rename: `backend/src/main/java/ch/multispace/backend/game/GameRoom.java` → `GameSession.java`
- Modify: every file importing it
- Rename: `frontend/src/app/waitingRoom/` → `frontend/src/app/waiting-room/`
- Modify: `frontend/src/app/app.routes.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: `game/GameSession.java` replacing `game/GameRoom.java`.

- [ ] **Step 1: Establish what each class is**

```bash
head -40 backend/src/main/java/ch/multispace/backend/game/GameRoom.java
grep -rln "game.GameRoom" backend/src/main/java
```

`model/GameRoom` is the JPA entity — the lobby record, named for its table.
`game/GameRoom` is the in-memory simulation state driven by `GameLoop`. Two
classes with one name in one codebase is a reading hazard. The runtime one is
renamed, because the entity's name is anchored to the `game_rooms` table.

- [ ] **Step 2: Rename the runtime class**

```bash
git mv backend/src/main/java/ch/multispace/backend/game/GameRoom.java \
       backend/src/main/java/ch/multispace/backend/game/GameSession.java
```

Rename the type and its constructors inside the file, then update every
reference found in step 1. Where a file previously imported both classes, the
disambiguating fully-qualified references can now be simplified.

- [ ] **Step 3: Compile and test**

Run: `cd backend && mvn -B test`

Expected: PASS. A rename that compiles and leaves the suite green changed no
behaviour.

- [ ] **Step 4: Rename the camelCase frontend folder**

Every other component folder is kebab-case; `waitingRoom` is not.

```bash
git mv frontend/src/app/waitingRoom frontend/src/app/waiting-room
cd frontend/src/app/waiting-room
git mv waitingRoom.component.ts waiting-room.component.ts
git mv waitingRoom.component.html waiting-room.component.html
git mv waitingRoom.component.css waiting-room.component.css
```

Update `templateUrl` and `styleUrls` inside the component, and the
`loadComponent` import path in `app.routes.ts`.

- [ ] **Step 5: Leave the route path alone, and say why**

```bash
grep -rn "waitingRoom" frontend/src --include=*.ts --include=*.html
```

The route path is `waitingRoom`. Renaming it changes a URL that may be
bookmarked and any link that hardcodes it, and doing that safely needs a
redirect from the old path. Leave the path as `waitingRoom` and record the
reason in `docs/decisions/0003-deferred-findings.md`: the folder rename is
internal and free, the URL rename is user-visible and belongs with a redirect,
which is out of scope for this cycle.

- [ ] **Step 6: Build and test**

Run: `cd frontend && npx ng test --watch=false && npx ng build`
Expected: both PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: give the runtime room class its own name, kebab-case the waiting room folder"
```

### Task 14: Formatter sweep

Deliberately last: running it earlier would mix reformatting into every review
diff above.

**Files:**
- Modify: `backend/pom.xml`
- Create: `frontend/.prettierrc`
- Modify: `frontend/package.json`
- Create: `.git-blame-ignore-revs`
- Modify: every source file (formatting only)

**Interfaces:**
- Consumes: nothing.
- Produces: nothing.

- [ ] **Step 1: Add Spotless to the backend build**

In `backend/pom.xml`, inside `<build><plugins>`:

```xml
            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>2.44.0</version>
                <configuration>
                    <java>
                        <googleJavaFormat>
                            <version>1.25.2</version>
                            <style>AOSP</style>
                        </googleJavaFormat>
                        <removeUnusedImports/>
                    </java>
                </configuration>
            </plugin>
```

`AOSP` keeps the 4-space indentation this codebase already uses. The default
Google style would reindent every line to 2 spaces for no reason, making the
sweep diff twice as large and twice as hard to trust.

- [ ] **Step 2: Move the frontend Prettier config out of package.json**

The `prettier` key currently sits inline in `frontend/package.json`, where
editors and the CLI find it inconsistently. Create `frontend/.prettierrc` with
exactly the settings that key already holds:

```json
{
  "printWidth": 100,
  "singleQuote": true,
  "overrides": [
    {
      "files": "*.html",
      "options": { "parser": "angular" }
    }
  ]
}
```

Then delete the `prettier` key from `frontend/package.json`. The settings are
unchanged, so this moves configuration without changing formatting.

- [ ] **Step 3: Confirm the suite is green before reformatting anything**

```bash
cd backend && mvn -B test
cd ../frontend && npx ng test --watch=false
```

Expected: both PASS. Write down the test counts.

Do not proceed otherwise. A sweep applied on top of a red suite makes it
impossible to tell formatting from breakage.

- [ ] **Step 4: Run both formatters**

```bash
cd backend && mvn -B spotless:apply
cd ../frontend && npx prettier --write "src/**/*.{ts,html,css}"
```

- [ ] **Step 5: Confirm nothing but formatting changed**

```bash
cd backend && mvn -B test
cd ../frontend && npx ng test --watch=false
```

Expected: both PASS, with **the same test counts as step 3**. A changed count
means something other than formatting happened.

Then read the diff:

```bash
git diff --stat
```

Expected: many files, whitespace and import ordering only. If the diff shows a
changed string literal or a reordered argument, revert and investigate before
going further.

- [ ] **Step 6: Commit formatting alone**

```bash
git add -A
git commit -m "style: apply Spotless and Prettier across the codebase"
```

- [ ] **Step 7: Record the sweep so blame stays readable**

```bash
git rev-parse HEAD
```

Create `.git-blame-ignore-revs` at the repository root:

```
# Commits that only reformat. Enable with:
#   git config blame.ignoreRevsFile .git-blame-ignore-revs
# Spotless and Prettier sweep, 2026-08-22
<paste the SHA printed above>
```

- [ ] **Step 8: Commit it and enable it locally**

```bash
git add .git-blame-ignore-revs
git commit -m "chore: ignore the formatting sweep in git blame"
git config blame.ignoreRevsFile .git-blame-ignore-revs
```

- [ ] **Step 9: Document the tooling**

Add a "Formatting" section to `docs/technical.md` naming both commands
(`mvn spotless:apply` and `npx prettier --write "src/**/*.{ts,html,css}"`), and
stating that each fresh clone needs the one-time
`git config blame.ignoreRevsFile .git-blame-ignore-revs` for blame to skip the
sweep.

```bash
git add docs/technical.md
git commit -m "docs: record the formatting tooling"
```

---

## Phase D — Verify

### Task 15: Full verification and handover

**Files:**
- Modify: `docs/decisions/0003-deferred-findings.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the verification record the reviewing session reads before merging.

- [ ] **Step 1: Run both suites from clean**

```bash
cd backend && mvn -B clean test
cd ../frontend && npx ng test --watch=false
```

Expected: PASS. Record both test counts — they go in the handover.

- [ ] **Step 2: Build the images the way CI does**

```bash
cd .. && docker compose build
```

Expected: the backend and frontend images build.

This is not a formality: Task 13 renamed files and Task 10 may have edited
`frontend/Dockerfile`, so a stale path surfaces here rather than during a
production deploy.

- [ ] **Step 3: Confirm the CI workflow still matches the commands**

```bash
grep -n "mvn\|ng test" .github/workflows/deploy.yml
```

Expected: `mvn -B test` and `npx ng test --watch=false`. Neither is changed by
this plan, so the gate runs exactly the suites that were just extended. If the
workflow names a path this plan renamed, fix the workflow now.

- [ ] **Step 4: Verify the schema of record is still honoured**

```bash
git diff main --stat -- backend/src/main/java/ch/multispace/backend/model database/init.sql
```

Expected: the only entity change is the `@JsonIgnore` added to `User.password`
in Task 7. That is a serialization annotation; it adds, drops and renames no
column, so `database/init.sql` must be **unchanged**.

If any entity gained, lost or renamed a persisted field, stop. `init.sql` must
change in the same commit and an `ALTER` must be written for production before
this branch can merge — with `ddl-auto: validate` a mismatch does not degrade
gracefully, it refuses to start the backend.

- [ ] **Step 5: Complete the deferred findings record**

`docs/decisions/0003-deferred-findings.md` must list everything found and not
fixed, each with a reason. At minimum:

- the `waitingRoom` route path, left unchanged (Task 13)
- `RoomsEventBroadcaster` still broadcasting entities rather than DTOs (Task 9)
- `GameRoom.status` as an unconstrained `String` rather than an enum, compared with `equalsIgnoreCase`
- the dead `game_sessions` and `game_session_players` tables described in `database/init.sql`
- the repeated `form.get('email')?.errors?.[...]` lookups in the auth templates, which Task 11 leaves alone because typing the component was the substantive half
- anything surprising found while running the characterization tests in Phase B

```bash
git add docs/decisions/0003-deferred-findings.md
git commit -m "docs: record what this cycle deliberately left alone"
```

- [ ] **Step 6: Write the handover summary**

Report to the reviewing session. State:

- both suite results with test counts, before and after
- the `docker compose build` result
- the four defects fixed and the test covering each: the password-hash leak (Task 7), the missing ownership check (Task 8), the end-of-life OAuth2 dependency (Task 12), and the unreachable error message (Task 11)
- the list of deferred findings

- [ ] **Step 7: Push the branch and stop**

```bash
git push -u origin refactor/space-multi
```

Do not open a pull request and do not merge to `main`. Per Spec D6 the reviewing
session reads the diff, re-runs the suites, and merges.

---

## Deployment note

Merging to `main` triggers `.github/workflows/deploy.yml`, which runs both
suites and then deploys to the VPS. After the reviewing session merges, confirm
the live site recovered:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://simulti.santoriello.ch/
```

Expected: `200`.

A `000` with exit 60 means the hostname resolved but no traefik router matched.
That is a routing symptom, not a certificate failure — see `docs/runbook.md`
before touching certificates.
