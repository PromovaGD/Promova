# Promova implementation backlog

This backlog was prepared from the current repository documentation, the code on
`main` at `dd4651f`, and merged pull requests #1-#3. It focuses on completing the
product loop already described by Promova:

```text
connected source -> captured evidence -> framework-based analysis -> employee review -> admin visibility
```

## Current state and planning assumptions

- PR #1 added CI for the frontend and backend.
- PR #2 added the career framework, mock analysis engine, and OpenRouter engine.
- PR #3 added registration/login, persistent saved analyses, an employee dashboard,
  and a global admin view.
- GitHub is the only real source integration. The Jira and Slack items returned by
  `CapturedEvidenceService` are fixtures, not integrations.
- The browser currently calls public `POST /analyze` and then sends the resulting
  classification back to authenticated `POST /analyses`. This means the saved result
  is client-authored and is not a trusted analysis record.
- Captured evidence is not persisted. The default inbox rotates through three static
  examples, and an imported GitHub pull request exists only in browser state until its
  analysis is saved.
- `currentLevel` and `targetLevel` are hard-coded as L3 and L4 during GitHub capture.
- The prototype's existing global `ADMIN` role is retained for now. Team/manager
  scoping is a separate product decision and is not silently introduced here.
- GitHub should be completed before adding Jira or Slack.

## Baseline verification recorded during planning

- `npm run check` passes on `main@dd4651f` (frontend lint and build).
- Backend source and test compilation complete, but the local Windows test process
  currently exits before executing tests because Gradle cannot launch
  `worker.org.gradle.process.internal.worker.GradleWorkerMain`. This reproduced with
  one Gradle worker and is an environment/worker-launch failure, not a reported
  Promova test assertion. The merged GitHub PRs reported passing checks.
- The first implementation task should rerun the documented backend command in its
  worktree. If the worker-launch failure reproduces, record the JDK/Gradle paths and
  use CI as additional evidence; do not weaken or delete tests to make the command
  green.

## Delivery order

| Order | Task | Priority | Depends on | Result |
| --- | --- | --- | --- | --- |
| 1 | Secure the API boundary introduced by PR #3 | P0 | Current `main` | Consistent authentication, authorization, and regression tests |
| 2 | Add an employee career profile | P0 | 1 | Framework-valid current and target levels; no hard-coded L3/L4 |
| 3 | Build a persistent evidence inbox | P0 | 2 | Durable, owned, deduplicated evidence with lifecycle states |
| 4 | Make analysis and persistence one trusted operation | P0 | 3 | Server-owned, idempotent analysis records |
| 5 | Add saved GitHub connection settings and sync | P0 | 4 | Repeatable GitHub ingestion instead of one-PR-at-a-time demo import |
| 6 | Add framework-grounded career insights | P1 | 5 | Competency coverage and evidence-based gaps on the dashboard |
| 7 | Add admin review and employee feedback | P1 | 6 | Human review lifecycle for AI classifications |
| 8 | Add production-safe database/configuration foundations | P1 | 7 | Versioned schema, isolated test data, deployable configuration |
| 9 | Add Jira and Slack through the source adapter boundary | P2 | 8 | Additional real sources without source-specific domain leakage |

Tasks 1-5 are the next MVP core. Do not run them concurrently: they intentionally
touch shared authentication, user, evidence, and frontend orchestration files. Tasks
6-9 should also use the latest accepted commit from the preceding task.

## Common Luna instructions

Prepend or retain this block when prompting an implementation agent:

```text
ROLE
Act as the implementation worker for this Promova task. Implement and verify only the
scope below. Do not redesign adjacent product behavior or broaden file ownership. You
are not alone in the repository: preserve unrelated and untracked user files, adapt to
edits you encounter, and never revert work outside this task.

GIT / PR BOUNDARY
Start from the specified accepted base. Inspect and report git status, base commit,
changed files, and the complete diff. Do not push, open, update, or merge a pull
request unless the primary task explicitly authorizes it after reviewing the diff and
verification. Do not rewrite accepted history.

STRUCTURED RETURN
Return: STATUS (complete/partial/blocked), BASE, CHANGES by file, VERIFIED commands
and outcomes, GIT status/branch/commit, JUDGMENT CALLS, and GAPS.
```

---

## Task 1 - Secure the API boundary introduced by PR #3

### Luna prompt

```text
OBJECTIVE
Make authentication and authorization consistent across the Promova backend and add
regression coverage for the account/admin/saved-analysis behavior added in PR #3.
Anonymous callers must not access user data, GitHub extraction, captured evidence, or
analysis. Employees must not access admin routes. The frontend must reliably end an
expired session after a 401 response.

STARTING STATE / BASE
- Base: main at dd4651f.
- Preserve existing untracked .localdeps/, outputs/, and tmp/ directories.

FILES AND OWNERSHIP
You own:
- backend/build.gradle only if an authentication dependency is required.
- backend/src/main/java/br/com/promova/auth/**
- backend/src/main/java/br/com/promova/config/WebConfig.java
- backend/src/main/java/br/com/promova/config/ApiExceptionHandler.java
- backend/src/main/java/br/com/promova/admin/controller/AdminController.java
- Authentication enforcement in existing analysis, evidence, and GitHub controllers.
- frontend/services/http.mjs, frontend/services/auth-api.mjs,
  frontend/services/auth-store.mjs, and the minimal frontend/app.mjs session handling.
- Focused backend tests under backend/src/test/java/br/com/promova/auth/**,
  backend/src/test/java/br/com/promova/admin/**, and controller security tests.
You do not own:
- Analysis engine behavior, career framework content, dashboard design, or source
  ingestion behavior.

INTERFACES AND SETTLED BEHAVIOR
- Keep POST /auth/register and POST /auth/login public.
- Require a valid `Authorization: Bearer <token>` session for all domain operations,
  including /analyses, /analyze, /evidences/**, and /api/github/**.
- Keep /admin/** restricted to ADMIN.
- Return 401 for missing/invalid/expired authentication and 403 for an authenticated
  user without the required role. Use the repository's JSON error contract.
- Do not switch to cookie authentication in this task.
- Do not log credentials or bearer tokens.
- A frontend 401 must clear local auth state and lead the user back to login; a 403
  must remain distinguishable and must not silently destroy a valid session.
- Preserve the existing endpoint payload shapes unless security requires only an
  additive change.

ACCEPTANCE CRITERIA
- Integration tests cover register, duplicate register, login success/failure,
  /auth/me, logout, expired/invalid tokens, employee access, and admin-only access.
- Tests prove one employee cannot list or delete another employee's analyses.
- Tests prove public requests cannot call analysis/evidence/GitHub domain endpoints.
- Existing seeded demo accounts still work in the development profile.
- No raw token or password appears in logs or an API error.

VERIFICATION
- Run: cd backend; .\\gradlew.bat test --no-daemon --no-configuration-cache
  Success: exit 0 and all security/auth tests pass.
- Run: npm run check
  Success: exit 0 with lint and build passing.
- Inspect all route mappings and produce a short public/authenticated/admin matrix in
  the handoff.
```

---

## Task 2 - Add an employee career profile

### Luna prompt

```text
OBJECTIVE
Replace the hard-coded L3/L4 context with an authenticated employee career profile.
Each analysis must ultimately use levels that exist in the configured career
framework. Employees need a small UI to view and update their profile.

STARTING STATE / BASE
- Start from the accepted Task 1 commit on the then-current main branch.
- Confirm Task 1's authentication tests pass before editing.

FILES AND OWNERSHIP
You own:
- backend/src/main/java/br/com/promova/user/**
- A new backend profile package under br.com.promova.profile/**
- Framework lookup/validation additions under br.com.promova.framework/**
- Minimal seeder changes in backend/src/main/java/br/com/promova/config/DataSeeder.java
- frontend/services/profile-api.mjs (new), frontend/views/profile-view.mjs (new),
  frontend/components/layout.mjs, frontend/app.mjs, and required styles.css additions.
- Focused backend profile/framework tests.
You do not own:
- Evidence persistence, GitHub synchronization, analysis prompt behavior, or admin
  review behavior.

INTERFACES AND SETTLED BEHAVIOR
- Add authenticated GET /profile and PUT /profile.
- Profile response contains currentLevel and targetLevel; return framework level keys
  and titles needed by the UI either in this response or a read-only authenticated
  framework endpoint.
- Reject unknown levels with 400.
- Reject a target level that is not above the current level according to framework
  declaration order. Do not infer ordering from lexical string comparison.
- Existing users and demo seeds must receive valid defaults compatible with the
  current L3/L4 framework.
- The frontend must use profile data rather than embedding a level list.
- Do not let a request update another user's profile or role.

ACCEPTANCE CRITERIA
- Profile data survives logout/restart through JPA persistence.
- Changing the configured framework changes the permitted UI/API choices without a
  frontend code edit.
- Validation tests cover missing, unknown, equal, and reversed levels.
- Existing registration produces a valid default profile.

VERIFICATION
- Run focused profile and framework tests, then the full backend test command.
- Run npm run check.
- Demonstrate GET/PUT /profile with an authenticated employee in the handoff.
```

---

## Task 3 - Build a persistent evidence inbox

### Luna prompt

```text
OBJECTIVE
Replace the rotating static evidence feed and browser-only imported PR state with a
durable, per-user evidence inbox. Evidence must have a lifecycle and be deduplicated
before any AI analysis is run.

STARTING STATE / BASE
- Start from the accepted Task 2 commit.

FILES AND OWNERSHIP
You own:
- backend/src/main/java/br/com/promova/evidence/**
- Repository-safe changes to backend/src/main/java/br/com/promova/github/** needed to
  persist an imported PR, but not connection/sync settings.
- frontend/services/evidence-api.mjs, frontend/services/session-store.mjs,
  frontend/views/dashboard-view.mjs, frontend/app.mjs, and the GitHub import feature
  files only where required by the new evidence contract.
- Focused evidence repository/service/controller tests.
You do not own:
- Analysis engine implementation, profile semantics, dashboard career insights, or
  new Jira/Slack integrations.

INTERFACES AND SETTLED BEHAVIOR
- Add a JPA evidence entity owned by a User with an internal ID, source, externalId,
  sourceMeta, evidence text, source URL when available, capturedAt, updatedAt, and a
  status enum containing at least PENDING, ANALYZED, and DISMISSED.
- Enforce uniqueness for owner + source + externalId. Importing the same PR twice must
  return/reuse the existing evidence and must not create duplicates.
- Add authenticated GET /evidences with status and date filters.
- Add authenticated GET /evidences/{id} and an action to dismiss a PENDING evidence.
- Change GitHub PR capture into an authenticated persistence operation. Keep a small
  compatibility route only if needed by the current frontend during the same diff.
- Never expose another user's evidence by ID, filters, or error details.
- Remove the static Jira/Slack/GitHub carousel from production behavior. Test fixtures
  may remain in test code only.
- Use the authenticated profile for level context; do not store fresh hard-coded
  L3/L4 values during capture.

ACCEPTANCE CRITERIA
- Evidence remains after restart and is visible only to its owner (or existing global
  ADMIN read access if deliberately routed through an admin endpoint).
- Importing one PR twice is idempotent.
- Pending, analyzed, and dismissed filters behave correctly.
- The frontend inbox renders persisted pending evidence and no longer uses a cursor
  in sessionStorage as the source of truth.

VERIFICATION
- Run focused evidence tests including ownership, deduplication, and status
  transitions, then the full backend test command.
- Run npm run check.
- In the handoff, show the exact evidence schema/constraint and endpoint matrix.
```

---

## Task 4 - Make analysis and persistence one trusted operation

### Luna prompt

```text
OBJECTIVE
Create a server-owned, transactional analysis operation. A browser must no longer be
able to submit an arbitrary classification and save it as a Promova result. An
authenticated user requests analysis of one owned PENDING evidence; the backend loads
the profile/framework, invokes AnalysisEngine, saves the result, and moves the
evidence to ANALYZED.

STARTING STATE / BASE
- Start from the accepted Task 3 commit.

FILES AND OWNERSHIP
You own:
- backend/src/main/java/br/com/promova/analysis/**
- The evidence service/entity methods needed for the analysis transition.
- frontend/services/analysis-api.mjs, frontend/services/analyses-api.mjs,
  frontend/services/evidence-api.mjs, frontend/app.mjs, and evidence result/error UI.
- Focused transaction, ownership, idempotency, and controller tests.
You do not own:
- OpenRouter transport/model selection except where needed to preserve the existing
  AnalysisEngine contract.
- GitHub connection/sync settings, admin review, or dashboard aggregation.

INTERFACES AND SETTLED BEHAVIOR
- Add authenticated POST /evidences/{evidenceId}/analysis.
- The request must not accept estimatedLevel/impactLevel, confidence, reasoning,
  competencies, suggestions, userId, or timestamps from the browser.
- Load current/target levels from the authenticated user's profile and validate them
  against the current framework before invoking AnalysisEngine.
- Persist the engine output and evidence source snapshot in one server-side result.
- Enforce one current analysis per evidence. A safe repeated request returns the
  existing result or a clear conflict; it must never bill/run the model twice due to
  an ordinary retry.
- If analysis fails, keep the evidence PENDING and do not persist a partial result.
- Retain authenticated GET /analyses and date filters.
- Remove or make non-public the client-authored POST /analyses. Remove the public
  product use of POST /analyze; a test/dev-only diagnostic route is acceptable only
  if it cannot be enabled accidentally in production.
- Preserve the current mock and OpenRouter engine selection.

ACCEPTANCE CRITERIA
- Tests with a mocked AnalysisEngine prove exact invocation count, rollback on
  failure, ownership protection, profile/framework use, and retry idempotency.
- The frontend performs one product operation to analyze and save, then refreshes the
  inbox/history from the server.
- Saved timestamps and classifications are generated by the server/engine, not trusted
  from the browser.

VERIFICATION
- Run all backend tests and npm run check.
- Run one mock-engine happy-path API flow and one forced-failure flow; report the
  resulting evidence statuses and database rows.
- Inspect the frontend network path and confirm it no longer POSTs an analysis result.
```

---

## Task 5 - Add saved GitHub connection settings and sync

### Luna prompt

```text
OBJECTIVE
Turn the one-PR-at-a-time GitHub demo into a repeatable source connection. An employee
configures a repository and GitHub author, tests the configuration, and requests a
sync that imports all matching recent pull requests into the persistent evidence
inbox without duplicates.

STARTING STATE / BASE
- Start from the accepted Task 4 commit.

FILES AND OWNERSHIP
You own:
- A new backend integration/source package and GitHub-specific implementation.
- Existing backend/src/main/java/br/com/promova/github/** where required.
- frontend/features/github-import/** (rename to a connection/sync feature if useful),
  frontend/services/github-api.mjs, frontend/views/profile-view.mjs or a new
  integrations view, frontend/app.mjs, and related styles.
- Focused GitHub client/service/controller tests using a local stub HTTP server; do
  not call live GitHub in automated tests.
You do not own:
- GitHub OAuth or storage of end-user personal access tokens.
- Jira/Slack, scheduled background jobs, career insights, or admin review.

INTERFACES AND SETTLED BEHAVIOR
- Store per-user repository slug and GitHub author/login plus lastSyncAt and the last
  sync outcome. Continue using the server-side GITHUB_TOKEN configuration documented
  by the repository.
- Add authenticated endpoints to get/update/test GitHub settings and POST a sync.
- Sync merged/closed pull requests authored by the configured login, with explicit
  pagination and a bounded configurable lookback.
- Reuse Task 3's source/externalId uniqueness and return counts for discovered,
  created, existing, and failed items.
- Surface upstream 401/403/404/rate-limit failures without exposing the server token.
- Validate owner/repository/login inputs and prevent arbitrary URL fetching.
- Keep the single-PR import only if it remains useful as an explicit manual action;
  it must share the same persistence/deduplication path.

ACCEPTANCE CRITERIA
- A second sync creates zero duplicate evidence records.
- Pagination is covered by tests.
- Private repository behavior is documented as requiring the server token to have
  access; do not claim per-user GitHub authorization.
- The UI shows saved settings, sync progress, a concrete result summary, and the
  imported pending evidence.

VERIFICATION
- Run focused GitHub sync tests with success, pagination, duplicate, not-found,
  unauthorized, and rate-limit responses; then run all backend tests.
- Run npm run check.
- Report exact stub scenarios and resulting evidence counts.
```

---

## Task 6 - Add framework-grounded career insights

### Luna prompt

```text
OBJECTIVE
Upgrade the employee dashboard from counts/feed into a career evidence view grounded
only in saved analyses and the configured framework. Show which framework criteria
have supporting evidence, where evidence is missing, and how recent analyses are
distributed. Do not invent a promotion probability or opaque score.

STARTING STATE / BASE
- Start from the accepted Task 5 commit.

FILES AND OWNERSHIP
You own:
- A backend insights DTO/service/controller under br.com.promova.analysis or a new
  br.com.promova.insight package.
- Read-only repository queries needed for aggregation.
- frontend/services/insights-api.mjs, frontend/views/dashboard-view.mjs,
  frontend/utils/format.mjs, frontend/app.mjs, and dashboard styles.
- Focused aggregation/controller tests.
You do not own:
- Analysis prompt semantics, source ingestion, profile mutation, or admin review.

INTERFACES AND SETTLED BEHAVIOR
- Add authenticated GET /insights with the same optional date window semantics used
  by analyses.
- Return total evidence, source distribution, estimated-level distribution,
  competency/criterion coverage, recent trend buckets, and evidence-backed gaps.
- Every value must be reproducible from the authenticated user's saved analyses and
  current framework. Clearly distinguish `no evidence` from `negative evidence`.
- Empty, one-item, and partial-framework states must render without errors.
- Keep detailed evidence drill-down and accessible text equivalents for visual bars.

ACCEPTANCE CRITERIA
- Aggregation tests use fixed records/timestamps and assert exact output.
- No client-only recomputation can disagree with server filters.
- The dashboard explains that insights summarize evidence and are not a promotion
  decision.

VERIFICATION
- Run focused insight tests, full backend tests, and npm run check.
- Provide screenshots or a concise DOM description for empty and populated states.
```

---

## Task 7 - Add admin review and employee feedback

### Luna prompt

```text
OBJECTIVE
Make AI classifications reviewable by the existing global ADMIN role. An admin can
mark an analysis as accepted or needing more context and leave feedback; the employee
can see the immutable review history and current review status.

STARTING STATE / BASE
- Start from the accepted Task 6 commit.

FILES AND OWNERSHIP
You own:
- Saved analysis review entities/repositories/services/controllers under the existing
  analysis/admin packages.
- frontend/views/admin-view.mjs, frontend/views/evidence-view.mjs,
  frontend/services/analyses-api.mjs, frontend/app.mjs, and review styles.
- Focused review authorization/history tests.
You do not own:
- Team hierarchy or manager assignment, changing global ADMIN semantics, rerunning AI
  analysis, or editing historical engine output.

INTERFACES AND SETTLED BEHAVIOR
- Review status contains UNREVIEWED, ACCEPTED, and NEEDS_CONTEXT.
- An admin review records reviewer, timestamp, status, and optional bounded comment.
- Review actions are append-only. The latest review defines current status, but prior
  reviews remain readable.
- Employees can read reviews on their analyses but cannot create or change them.
- Admins cannot edit the original evidence or AI classification through this API.
- Preserve the current admin employee-list access model for the prototype.

ACCEPTANCE CRITERIA
- Tests cover employee rejection, admin success, cross-record access, multiple review
  events, comment validation, and history ordering.
- Employee and admin evidence detail views show current review state and history.
- Audit fields come from authenticated/server state, not request-supplied identity or
  time.

VERIFICATION
- Run focused review tests, all backend tests, and npm run check.
- Demonstrate employee/admin API behavior and the append-only history in the handoff.
```

---

## Task 8 - Add production-safe database and configuration foundations

### Luna prompt

```text
OBJECTIVE
Make the completed MVP schema reproducible and configuration safe outside a local
prototype. Introduce versioned migrations, isolated test data, explicit dev/test/prod
profiles, and deployment documentation without changing product behavior.

STARTING STATE / BASE
- Start from the accepted Task 7 commit, after the MVP domain schema has stabilized.

FILES AND OWNERSHIP
You own:
- backend/build.gradle and backend/src/main/resources/** except the semantic content
  of career-framework.json.
- Database migration files and backend test profile resources.
- .github/workflows/ci.yml, .gitignore, README.md, and backend/README.md.
- Minimal entity annotations required for migration validation.
You do not own:
- Product endpoints, frontend redesign, analysis behavior, or new integrations.

INTERFACES AND SETTLED BEHAVIOR
- Add Flyway migrations for the accepted user/session/profile/evidence/analysis/review
  schema and constraints.
- Replace production `ddl-auto=update` with schema validation.
- Keep an easy H2 development path, use isolated in-memory H2 for tests, and document
  a production PostgreSQL configuration through environment variables.
- Disable the H2 console and permissive localhost-only assumptions outside dev.
- Make CORS origins configurable and deny unconfigured production origins.
- Do not commit databases, tokens, or local generated directories.

ACCEPTANCE CRITERIA
- A clean database migrates to the latest version and the application starts.
- An existing prototype database has an explicitly documented migration/baseline
  strategy; do not silently destroy it.
- Tests do not read/write backend/data/promova.*.
- CI runs frontend checks, backend tests, bootJar, and a migration/startup smoke test.

VERIFICATION
- Run the clean migration/startup smoke test, all backend tests, bootJar, and npm run
  check.
- Report migration versions, schema validation result, and gitignored generated data.
```

---

## Task 9 - Add Jira and Slack through the source adapter boundary

This is deliberately deferred until the GitHub-backed core loop and database
contracts are stable. Split Jira and Slack into separate implementation tasks when
their authentication choices are known.

### Luna prompt for the shared adapter boundary

```text
OBJECTIVE
Extract a source adapter contract from the accepted GitHub sync implementation so
additional sources can discover and normalize evidence without leaking provider DTOs
into the evidence, analysis, or dashboard domains. Prove the contract with GitHub;
do not implement Jira or Slack credentials in this task.

STARTING STATE / BASE
- Start from the accepted Task 8 commit.

FILES AND OWNERSHIP
You own the backend integration/source packages, GitHub adapter, adapter contract
tests, and documentation of the normalized evidence contract. Do not modify analysis
semantics or UI behavior except for provider-neutral naming required by the contract.

INTERFACES AND SETTLED BEHAVIOR
- Normalize source, externalId, sourceMeta, body, source URL, author, occurredAt, and
  provider metadata needed for traceability.
- Reuse the existing evidence uniqueness, ownership, status, and analysis pipeline.
- Provider failures are isolated per item and produce a sync summary.
- No provider token appears in normalized evidence, logs, or API responses.

ACCEPTANCE CRITERIA AND VERIFICATION
- All existing GitHub sync behavior passes unchanged through the adapter contract.
- Contract tests cover duplicates, pagination, partial failure, and unsafe metadata.
- Run all backend tests and npm run check; report the extension points a future Jira
  or Slack task will own.
```

## Product decisions to confirm before their affected task

1. Before Task 5: is a company-managed server GitHub token acceptable for the MVP, or
   must each employee connect GitHub through OAuth? The task above intentionally keeps
   the documented server token and does not pretend it represents end-user consent.
2. Before expanding Task 7: should `ADMIN` remain a global HR/admin role, or should
   employees be visible only to assigned managers/teams? The current code and this
   backlog retain global access.
3. Before Jira/Slack implementation: choose OAuth/workspace installation and the
   exact evidence events to ingest. The existing static examples are insufficient to
   infer those contracts safely.
