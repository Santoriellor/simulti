# Estate Refactor — Design

Date: 2026-08-22
Status: awaiting review
Scope: the four live projects plus retirement of everything else

This is the estate-level coordination document. It fixes the standards every
project cycle follows. Each project also gets its own design spec, committed
inside its own repository.

---

## 1. Goal

Bring each live project to a state where:

- its design, architecture and technical documentation is written down and accurate,
- its code follows one consistent set of patterns rather than several,
- its formatting and linting are enforced by tooling, not habit,
- its behaviour is protected by tests that run in the existing deploy gate.

Obsolete projects leave the estate entirely: local folders archived, containers
and routers removed from the VPS, DNS cleaned.

## 2. Estate survey

| Project | Stack | Source files | Tests today | Hostname |
|---|---|---|---|---|
| space-multi | Angular 20, Spring Boot (JDK 25), PostgreSQL 16, nginx | 22 ts, 38 java, 8 html | 1 Angular spec, 2 Java | `simulti.santoriello.ch` |
| workshop | Django 5.1 + DRF, React 19 on Vite, MySQL, nginx | 36 py, 36 js | 7 pytest modules, 1 FE smoke | `workshop.santoriello.ch` |
| santoriello.ch | React 19 on CRA (`react-scripts` 5) | 16 js, 11 css | 1 CRA smoke test | `santoriello.ch` |
| website-laferme | React 19 on CRA (`react-scripts` 5) | 20 js, 18 css | 1 CRA smoke test | `website.santoriello.ch` |

Retiring: `casproject` (`casproject.santoriello.ch`), `workinghours`
(`hours.santoriello.ch`), and the local-only folders `PythonProject`,
`PythonTestProject`, `planets`, `santoriello`, `space-fe`, `space-multi-ionic`,
`thegarage`.

Shared infrastructure: `traefik`, seven routers, one edge for every live site.
It is not refactored; it is edited only during the retirement cycle.

Every repository already runs a test job that gates its deploy job. Tests added
here therefore have immediate effect on whether a live site can ship.

## 3. Decisions

These were settled during brainstorming and are not reopened by individual
project specs.

**D1 — Stacks stay.** No CRA to Vite migration, no JavaScript to TypeScript
conversion, no framework replacement. `react-scripts` being unmaintained is
recorded as an ADR in each affected repository, not acted on.

**D2 — Documentation is a fixed file set.** Every repository gets exactly:

```
README.md              entry point: what it is, how to run it, links out
docs/architecture.md   components, boundaries, data flow, deployment topology
docs/design.md         domain model, product intent, key design decisions
docs/technical.md      build, configuration, environment variables, CI, secrets
docs/runbook.md        operating it: logs, backups, restore, common incidents
docs/decisions/NNNN-*.md  ADRs, including deliberately deferred problems
```

No `CLAUDE.md`. Same filenames and same section ordering in all four repos.

**D3 — Characterization tests come before refactoring.** Behaviour is pinned
first, code is changed second. A repository whose characterization suite cannot
be made green does not proceed to its refactor phase.

**D4 — Formatters are adopted, in one isolated commit per repository.** The
sweep commit contains formatting only, and its SHA is added to
`.git-blame-ignore-revs` so history stays readable.

| Language | Tool | Current state |
|---|---|---|
| JS / TS / HTML / CSS | Prettier + ESLint | only `workshop/front` configured; `space-multi/frontend` has Prettier options inline in `package.json` |
| Python | ruff (lint + format) | nothing configured |
| Java | Spotless with google-java-format | nothing configured |

**D5 — Security-motivated dependency bumps are in scope.** `workshop` moves off
`axios@0.27`. Because the 1.x upgrade changes error and parameter handling, it
happens in the refactor phase, after that repository's characterization tests
are green — never before.

**D6 — Projects run in parallel; agents do not merge.** One subagent per
repository, each on its own branch. Every diff is reviewed and its suite run
before merge. Merges are performed by the reviewing session, not by the agent
that wrote the code.

**D8 — Security fixes are deliberate behaviour changes, not characterized.**
D3 says tests pin current behaviour. Security defects are the exception: current
behaviour is the defect. Such a finding is written as a test asserting the
*corrected* behaviour, fails, and is then fixed — ordinary TDD. It is never
pinned by a characterization test, because that would lock the defect in and
make the deploy gate defend it.

Two are already confirmed in space-multi, both live:

- `GET /api/auth/me` returns the `User` entity and `User.password` carries no
  `@JsonIgnore`, so every call serializes the BCrypt hash to the browser
  (`controllers/AuthController.java`, `model/User.java`).
- The room-ownership check in `deleteRoom` is commented out, so any
  authenticated user can delete any room
  (`controllers/GameRoomController.java`).

Each project's survey phase looks for the same class of defect before its
characterization phase begins.

**D7 — Retirement is last and separate.** It is destructive and touches the
shared edge that serves all four live sites, so it runs after the four project
cycles and gets its own design and its own approval.

## 4. Per-project pipeline

Every project follows the same four phases, merged in the `Merge phase NN`
style already used in these repositories.

**Phase A — Document.** Read the codebase. Write the D2 file set and rewrite
the README as an entry point. Record findings that will not be fixed as ADRs.
No production code changes. This phase also commits a copy of this estate
document to `docs/superpowers/specs/`, so the plan's spec reference resolves
inside the repository the executor is working in. That copy is also this
document's permanent home, which settles the open item in section 8.

**Phase B — Characterize.** Write tests that pin current behaviour: routes
render, API contracts hold, authentication and permission boundaries behave as
they do today. No production code changes. The suite goes green and is wired
into the existing `deploy.yml` test job.

**Phase C — Refactor.** Only against a green suite. In order: structural work,
then dependency bumps (D5), then the formatter sweep commit (D4) last. Small
commits, suite green at each one.

The sweep goes last because D4 requires it to be *one* isolated commit, and a
refactor creates and deletes files. Sweeping first formats files that are about
to be deleted and misses every file created afterwards, which forces a second
sweep and breaks the single-commit rule that makes `.git-blame-ignore-revs`
work at all.

> **Corrected 2026-08-23.** This section originally specified the opposite
> order — the sweep first, then structural work. All four project cycles
> executed it the other way round, and the sweep landed among the last handful
> of commits in every repository: space-multi 90/97, workshop 133/141,
> santoriello.ch 70/76, website-laferme 60/66. The practice was right and the
> specification was wrong, so the specification is corrected here rather than
> four cycles being recorded as having deviated from it.

**Phase D — Verify.** Full suite, `docker compose build`, and a deploy smoke
check that the live hostname still answers. Then merge.

Phase B is the gate that makes C safe. If B cannot go green, C does not start.

## 5. Per-project refactor targets

Identified during the survey; each project spec refines them.

**space-multi.** Backend: enforce controller / service / repository layering,
introduce DTOs at the API boundary so entities stop leaking into responses,
centralize error handling in `@ControllerAdvice`, make the JWT security
configuration consistent and externalize configuration. Frontend: standalone
components, typed reactive forms, logic moved out of templates, strict
TypeScript settings.

**workshop.** Backend: clarify the split between serializers, views and
permissions; declare permissions consistently across viewsets; fix N+1 query
patterns; split settings by environment; audit and re-pin `requirements.txt`.
Frontend: one centralized axios client, clear zustand store boundaries,
component and container split, axios 1.x migration.

**santoriello.ch and website-laferme.** Component structure and naming,
consolidation of 11 and 18 stylesheets respectively, routing configuration in
one place, content and data extracted out of markup, baseline accessibility
(landmarks, alt text, focus order, colour contrast).

## 6. Risks

- **Four unreviewed diffs at once.** The mitigation is D6: agents report, the
  reviewing session reads every diff and runs every suite before merging.
- **A failing test blocks a live deploy.** Every repository gates deploy on
  tests. A characterization test that is wrong or flaky stops a real site from
  shipping. Tests must assert current behaviour, not desired behaviour.
- **Formatter churn.** Contained by the isolated sweep commit and
  `.git-blame-ignore-revs`.
- **axios 1.x behaviour changes.** Contained by ordering: bump only after the
  frontend characterization suite is green.
- **`workshop/secrets/`.** Present in the repository. Treated as read-only
  during refactoring; any finding about it goes into an ADR rather than a
  code change.
- **Shared traefik edge.** Only touched in the retirement cycle, with volume
  backups taken before any removal and the four live hostnames verified after
  each step.

## 7. Out of scope

Framework and build-tool migrations (D1), feature work, visual redesign,
performance tuning beyond obvious N+1 fixes, and any change to the traefik
configuration outside the retirement cycle.

## 8. Where this document lives

Phase A of every project cycle commits a copy of this document to that
repository's `docs/superpowers/specs/`. It therefore survives in four places,
next to each plan that argues from it. The scratchpad copy is working state and
is not authoritative once the first cycle has committed.

If the four copies ever need to diverge, they do not: changes to shared
decisions are made here and re-copied, because a decision that differs per
repository is by definition a project-level decision and belongs in that
project's ADRs instead.
