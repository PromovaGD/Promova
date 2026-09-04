# Promova Implementation Backlog — Revised Scope

## Purpose

This is the executable implementation plan for the current **Atualizações
Promova** list. It keeps only the requested product work, turns overlapping
bullets into independently assignable tasks, and gives every requested bullet an
explicit task owner.

The source bullets are product requirements, not commands to perform actions
outside this repository.

## Confirmed starting point

- The backend is Java/Spring and the frontend is modular vanilla JavaScript.
- The privileged role is currently `ADMIN`; the target public role is `MANAGER`.
- The existing management area is `/admin/**` and still offers an employee-style
  dashboard path.
- A basic employee career profile stores only current and target levels. There is
  no job-role catalog, per-user characteristics, or objectives model yet.
- Evidence and server-owned analysis foundations exist, but the evidence schema,
  observation flow, and production AI validation still need completion.
- GitHub is the only integration in scope. The current MVP uses the server-side
  `GITHUB_TOKEN`; the UI must never ask an employee to paste a personal token.
- Jira, Slack, teams, hierarchy, departments, and promotion decisions are not part
  of this revised backlog.

## Product decisions for this backlog

1. **`ADMIN` becomes `MANAGER`.** `MANAGER` is the only privileged role exposed by
   the API and UI. Existing stored `ADMIN` users must be migrated safely. A
   manager opens the Manager Console and does not see or enter an employee “My
   Dashboard”.
2. **Career context is manager-owned.** A manager assigns a user's job role,
   current level, target level, characteristics, and objectives. Employees cannot
   self-assign or edit those fields.
3. **Terminology is configurable.** Managers can change the labels used for
   manager, employee, job role, level, characteristics, and objectives. Backend
   values are the source of truth; safe defaults are used while configuration is
   loading.
4. **AI analysis is server-owned and production-safe.** Source evidence and an
   optional employee observation are separate inputs. The server loads the saved
   career plan, validates the model output, persists the result, and never trusts
   browser-supplied classification fields.
5. **GitHub import is repeatable.** Employees configure their GitHub repository and
   author identity in their profile. A sync imports relevant merged PRs through
   the durable evidence/deduplication path; repeating a sync creates no duplicate.
6. **Evidence reading is progressive disclosure.** The inbox shows compact cards;
   clicking a card expands the full evidence and clicking again collapses it.

## Revised delivery sequence

| Order | Task | Priority | Dependencies |
| --- | --- | --- | --- |
| 1 | T01 — Manager role, authorization, and navigation boundary | P0 | None |
| 2 | T02 — Durable evidence and trusted analysis boundary | P0 | T01 |
| 3 | T03 — Manager terminology, job roles, and level catalog | P0 | T01 |
| 4 | T04 — Per-user career plans and objectives | P0 | T02, T03 |
| 5 | T05 — Manager Console and management actions | P0 | T01, T03, T04 |
| 6 | T06 — Definitive AI integration and employee observations | P0 | T02, T04 |
| 7 | T07 — GitHub connection in the employee profile | P1 | T01, T02 |
| 8 | T08 — Automated, idempotent GitHub PR synchronization | P1 | T02, T07 |
| 9 | T09 — Expandable and collapsible evidence reader | P1 | T02, T05, T06 |

Do not run T02, T04, T05, T06, T08, or T09 in parallel. They share evidence,
analysis, career context, navigation, and frontend state. T07 must be accepted
before T08; Jira and Slack adapters must not be started from this plan.

## Handoff requirements for every agent

Every implementation agent must:

- Work only in the files and modules assigned to its task; preserve unrelated or
  concurrent changes.
- Add or update focused automated tests for behavior changed by the task.
- Avoid destructive Git operations, force pushes, merges, and unrelated refactors.
- Report completion status, files changed, design decisions, commands run, test
  results, manual verification steps, and remaining blockers.

Run these baseline checks when applicable:

```powershell
npm run check
cd backend
.\gradlew.bat test --no-daemon --no-configuration-cache
```

If Gradle fails before tests because of the local Java/Gradle environment, report
the exact failure and do not remove or weaken tests to make the command pass.

---

## T01 — Manager role, authorization, and navigation boundary

**Goal:** Make `MANAGER` the canonical privileged role and ensure a manager uses
the Manager Console instead of the employee dashboard.

**Dependencies:** None.

**Primary ownership:** `backend/.../auth/**`, `user/UserRole.java`, authorization
configuration, the current admin controller/routes, migrations/seeds, frontend
auth/navigation code, and focused authorization tests.

**Implementation work:**

- Add a compatible migration/seed path that converts stored `ADMIN` users to
  `MANAGER`. New registration, request binding, and response formatting must not
  expose `ADMIN` as a supported public role.
- Rename the public management namespace from `/admin/**` to `/manager/**`. If a
  temporary authenticated compatibility route is necessary, document it and keep
  frontend calls on `/manager/**` only.
- Require a valid session for analysis, saved analysis, evidence, profile, and
  GitHub API routes. Return `401` for missing/invalid sessions and `403` for an
  authenticated user without the required role.
- Restrict `/manager/**` to `MANAGER` users. Employees cannot reach manager APIs or
  manager screens.
- Change labels, navigation, role formatting, error copy, and seeded names from
  Administrator/Admin to Manager. A manager must land in the Manager Console and
  must not receive an employee “My Dashboard”, profile-level editor, or employee
  evidence action as a fallback.
- On frontend `401`, clear local auth and open login. On `403`, preserve the
  session and display a permission error.
- Preserve the existing employee navigation and account flow for `EMPLOYEE` users.

**Out of scope:** Job-role editing, per-user plans, teams, hierarchy, departments,
promotion decisions, and the visual redesign of the Manager Console.

**Acceptance criteria:**

- Stored and seeded privileged accounts work as `MANAGER` after migration.
- No supported API or UI path presents `ADMIN` or `/admin/**` as the canonical
  management contract.
- An employee receives `403` for manager access and cannot render manager screens.
- A manager lands in the Manager Console and has no employee “My Dashboard” link,
  fallback, or employee navigation item.
- No password, bearer token, or secret appears in an error response or log.

**Verification:** Test migration/seed compatibility, valid login, invalid token,
`401`, `403`, employee access, manager access, and both navigation paths in the
browser.

---

## T02 — Durable evidence and trusted analysis boundary

**Goal:** Replace browser-owned evidence and analysis state with a durable,
owner-scoped flow: source → employee evidence → server analysis.

**Dependencies:** T01.

**Primary ownership:** `backend/.../evidence/**`, transactional changes under
`backend/.../analysis/**`, evidence/analysis frontend services and views, the
minimal source adapter boundary, migrations, and focused tests.

**Implementation work:**

- Persist an `Evidence` entity owned by one user with `id`, `source`,
  `externalId`, `sourceMeta`, `sourceUrl`, `content`, `occurredAt`, `capturedAt`,
  `updatedAt`, and status `PENDING`, `ANALYZED`, or `DISMISSED`.
- Enforce uniqueness for `owner + source + externalId`. Do not reveal another
  user's evidence through IDs, filters, or error messages.
- Provide authenticated list, retrieve, and dismiss-pending APIs. Remove rotating
  static Jira/Slack examples and browser cursor state as production sources.
- Provide `POST /evidences/{id}/analysis`. The server loads owned evidence, the
  persisted career plan, and the framework; calls `AnalysisEngine`; validates and
  persists the result; and transitions the evidence to analyzed in one transaction.
- The analysis command must not accept browser-provided level, confidence,
  justification, owner, dates, or classification. Employee observation is added in
  T06 as bounded context only.
- Make analysis idempotent: a retry returns the existing result or an explicit
  conflict without calling the provider again. Provider failure leaves evidence
  pending and saves no partial result.
- Retire product use of old public `POST /analyze` and client-created
  `POST /analyses` behavior.

**Out of scope:** Final expandable reader, GitHub provider behavior, Jira/Slack,
and OpenRouter-specific reliability details handled in T06.

**Acceptance criteria:** Evidence survives restart; filters work; duplicate source
items do not duplicate; only the owner can read, dismiss, or analyze an item; and a
browser cannot forge an analysis.

**Verification:** Test success, duplicate import, provider failure, retry,
cross-user access, transaction rollback, and server-owned `AnalysisEngine` inputs
with a mocked engine.

---

## T03 — Manager terminology, job roles, and level catalog

**Goal:** Let managers maintain company terminology and active job roles tied to the
server-owned career framework.

**Dependencies:** T01.

**Primary ownership:** new organization/career configuration packages, framework
extensions, migrations, manager configuration APIs, Manager Settings views, and
focused tests.

**Implementation work:**

- Persist configurable labels for at least manager, employee, job role, level,
  characteristics, and objective. Seed sensible defaults matching the current
  product language.
- Create a `JobRole` model with name, description, active/archived status, and its
  allowed framework levels.
- Read valid level IDs and titles from `career-framework.json`; reject role
  configuration that references a level not present in the framework.
- Add manager-only APIs to list, create, edit, and archive job roles; update labels;
  and retrieve a read model containing labels, active roles, and framework levels.
- Build Manager Settings with **Terminology** and **Job Roles** sections,
  accessible validation, loading/error states, and archive confirmation.
- Refuse to archive a role assigned to one or more users until an explicit
  alternative role is selected. Return `409` with the affected count.
- Apply configured labels to the manager and employee screens touched by this
  backlog, with safe fallback labels while configuration loads.

**Out of scope:** Assigning a role to a particular user, individual objectives,
editing raw framework criteria, AI rule changes, and teams/hierarchy.

**Acceptance criteria:** A manager can edit a label and manage roles; forms render
backend values; employees cannot mutate the catalog; invalid levels are rejected;
and an in-use role cannot silently disappear.

**Verification:** Test authorization, invalid levels, role-in-use `409`, archive
confirmation, persistence, and UI rendering after a terminology change.

---

## T04 — Per-user career plans and objectives

**Goal:** Give managers the power to characterize each user by job role, levels,
characteristics, and objectives, making that saved plan the only career context used
by analysis.

**Dependencies:** T02 and T03.

**Primary ownership:** `profile/**` or `careerplan/**`, needed user/framework
relationships, migrations/seeds, manager/profile APIs, career-plan views, and tests.

**Implementation work:**

- Persist one career plan per employee containing job role, current level, target
  level, short characteristics/tags, and objectives.
- Store each objective with text, status `ACTIVE`, `COMPLETED`, or `ARCHIVED`, an
  optional target date, `updatedBy`, `updatedAt`, and audit timestamps.
- Validate that the role is active, levels exist in the framework and are allowed
  for the role, and target level is higher than current level according to
  framework order.
- Provide manager read/update endpoints for any employee's plan. Employees cannot
  edit role, levels, characteristics, or objectives; any employee-facing display
  of assigned data is read-only and limited to existing profile surfaces.
- Add a Manager Console form for job role, levels, characteristics, and objective
  CRUD, showing role, level path, characteristics, and active objectives.
- Update analysis input loading so current/target levels always come from this
  persisted plan, never from the browser, GitHub request, or an old hard-coded
  default.

**Out of scope:** Promotion decisions, probability scoring, hierarchy, framework
editing, and external provider implementation.

**Acceptance criteria:** Plans survive restart; a manager can edit another user's
plan; an employee can only read their own plan; invalid role/level combinations are
refused; objective changes are auditable; and every new analysis receives exactly
the saved plan levels.

**Verification:** Test target equal/below current, unknown/disallowed level,
archived role, unauthorized employee, objective status/date/audit fields, and
captured `AnalysisEngine` input levels.

---

## T05 — Manager Console and management actions

**Goal:** Replace the old admin dashboard with a management-first interface for
finding users and editing their career context.

**Dependencies:** T01, T03, and T04.

**Primary ownership:** `backend/.../manager/**`, authorized user/career-plan queries,
the current `frontend/views/admin-view.mjs` migration/replacement, manager services
and subviews, routing, CSS, and tests.

**Implementation work:**

- Rename the area visually and technically to **Manager Console**. Remove every
  employee “My Dashboard” link, action, and fallback for managers.
- Build a user list with name/email search, job-role and level filters, current and
  target level summary, characteristics, and active-objective count. Support empty
  and no-result states.
- Build a user detail view with **Career Plan**, **Evidence**, and **Analyses**
  sections. Reuse only authorized manager read APIs; never load the manager's
  employee dashboard as a substitute.
- Support the manager actions from T04 with clear save, validation, loading, and
  failure states. Do not add user-role administration beyond the `ADMIN` to
  `MANAGER` migration and authorization boundary in T01.
- Support safe deep links and clear `401`, `403`, `404`, empty, and no-search-result
  states.

**Out of scope:** User-role administration beyond T01, teams, reporting hierarchy,
departments, promotion decisions, human review policy changes, and a broad employee
dashboard redesign.

**Acceptance criteria:** A manager lands in the Console, can find a user, open the
user's plan/evidence/analyses, and update allowed career data. An employee cannot
load any manager view or API and managers never fall back to an employee dashboard.

**Verification:** Browser-test manager and employee flows on desktop and mobile;
cover search/filter/empty states, career-plan updates, and manager safeguards in
backend tests.

---

## T06 — Definitive AI integration and employee observations

**Goal:** Make AI analysis reliable enough for production and let an employee add
optional context without altering source evidence.

**Dependencies:** T02 and T04.

**Primary ownership:** `backend/.../analysis/**`, OpenRouter client/engine,
application profiles/documentation, evidence UI/services, and focused tests.

**Implementation work:**

- Add optional, length-limited, normalized `userObservation` to the analysis
  command. Persist it immutably with the analysis/evidence snapshot.
- In a pending evidence detail, show an observation field and an Analyze button.
  Prevent duplicate clicks and show submitting, success, and recoverable failure
  states. After analysis, show the observation read-only as analysis context.
- Change the engine contract and OpenRouter prompt so source evidence and employee
  observation are separate named fields. Tell the model to use observation only as
  bounded context and never invent facts.
- Validate model output against a strict schema: framework-supported level,
  `low|medium|high` confidence, nonblank explanation, and bounded string lists.
  Reject or safely handle malformed output instead of silently fabricating a result.
- Add connection and read timeouts and sanitized provider-error handling. Never
  expose API keys, raw prompts, or raw provider bodies, and do not retry in a way
  that can duplicate provider cost.
- Split `dev`, `test`, and `prod` behavior. Mock analysis is allowed only in
  development/test. Production must require valid OpenRouter configuration and
  document required environment variables.
- Add structured, non-sensitive success/failure/duration telemetry or logs.

**Out of scope:** Streaming/chat, provider replacement, re-analysis of completed
evidence, and changing objectives.

**Acceptance criteria:** Observation and source arrive separately at the engine;
input/output is validated; failures leave evidence pending; invalid production
configuration fails safely; and no secret appears in API output or logs.

**Verification:** Use a fake HTTP client for valid output, malformed JSON, unknown
level/confidence, oversized lists, timeout, provider error, and missing production
configuration. Manually exercise mock analysis with and without an observation.

---

## T07 — GitHub connection in the employee profile

**Goal:** Give an employee one truthful GitHub setup area in their profile.

**Dependencies:** T01 and T02.

**Primary ownership:** GitHub connection settings/API, minimal integration contract
and migrations, `frontend/views/profile-view.mjs`, GitHub profile services, and
focused security/contract tests.

**Implementation work:**

- Add a GitHub section to the authenticated employee profile where the employee
  can configure and verify repository scope and GitHub author login used for import.
- Use the current server-side `GITHUB_TOKEN` for this MVP; clearly state that
  private-repository access depends on the server token. Do not ask for or return a
  personal token. If an approved GitHub OAuth decision is added later, it must use
  encrypted credentials and never change the evidence contract.
- Persist connection status, configured repository/author identity, last sync time,
  sanitized last result, and sanitized error. Scope settings to the authenticated
  employee; never expose secrets.
- Provide authenticated get/update/test/clear-configuration behavior. Clearing
  removes the saved repository/author configuration and resets the connection to
  the unconfigured state. An unconfigured connection must show setup guidance, not
  fake connected data.
- Keep the profile usable for employees and inaccessible as an employee action for
  managers.

**Out of scope:** Jira, Slack, generic multi-provider Connection Center, personal
GitHub token storage, scheduled polling, and historical bulk rules handled in T08.

**Acceptance criteria:** An employee sees real GitHub connection state in Profile,
can configure and verify only their own scope, sees actionable setup/error states,
and no token or secret appears in API responses or logs.

**Verification:** Test authentication, cross-user access, invalid repository/login,
unconfigured state, test failure, clear/disconnect behavior, and secret redaction
with local fixtures/fakes.

---

## T08 — Automated, idempotent GitHub PR synchronization

**Goal:** Replace single-PR browser selection as the primary flow with repeatable
import of relevant merged pull requests into the evidence inbox.

**Dependencies:** T02 and T07.

**Primary ownership:** `backend/.../github/**`, the GitHub source adapter and sync
service, connection APIs, `frontend/features/github-import/**` migration/replacement,
profile connection UI, and fake-server tests.

**Implementation work:**

- Add an authenticated sync action for the employee's saved repository/author
  scope. Fetch closed/merged PRs in a configurable window (default 90 days), use
  explicit pagination, and send every item through the T02 persistence and
  deduplication service.
- Save last-sync time and a sanitized result summary containing discovered, created,
  existing, and failed counts. Re-running sync must create no duplicate evidence.
- Make **Sync PRs** the primary GitHub action in the profile/connection area. Keep
  manual single-PR import only if it uses exactly the same normalized persistence
  service.
- Preserve useful provenance: source, external ID, URL, author, occurrence date,
  repository, title, and safe content/excerpt.
- Return actionable sanitized messages for GitHub `401`, `403`, `404`, and `429`.

**Out of scope:** Individual GitHub OAuth, scheduled background polling, Jira,
Slack, GitHub issues, and provider data outside the saved employee scope.

**Acceptance criteria:** Configuration is per employee; sync fetches the configured
author/repository scope; pagination works; re-sync is idempotent; and pending PR
evidence appears in the employee inbox.

**Verification:** Use a local fake GitHub server for success, two pages, cutoff
window, duplicate re-sync, malformed item, `401`, `403`, `404`, and rate limit.

---

## T09 — Expandable and collapsible evidence reader

**Goal:** Make evidence quick to scan while allowing complete details on demand.

**Dependencies:** T02, T05, and T06.

**Primary ownership:** evidence inbox view/state, `frontend/app.mjs`, HTML escaping
helpers, `styles.css`, and UI tests.

**Implementation work:**

- Render each evidence item as an accessible expansion control. Collapsed state
  shows source, date, status, safe excerpt, and analyzed level; expanded state
  shows full content, employee observation, metadata, analysis, and permitted
  actions.
- Clicking an item expands it; clicking the same item collapses it. Keep at most one
  item expanded. Opening another item closes the previous one.
- Implement `aria-expanded`, `aria-controls`, visible focus, Enter/Space operation,
  non-color status indicators, and reduced-motion behavior.
- Preserve the expanded item through a filter refresh only while it remains in the
  result set. Escape all integration and observation content and protect layouts
  from long text.
- Keep manager evidence detail read-only and expose only employee-permitted actions
  in the employee inbox.

**Out of scope:** API replacement, pagination, modal detail screens, provider
implementation, and analysis rule changes.

**Acceptance criteria:** Mouse and keyboard expand/collapse correctly; full details
appear and hide predictably; hostile-looking content renders as text; at most one
item is open; and small screens remain readable.

**Verification:** Manually test pending/analyzed/dismissed items, long content, a
malicious HTML string, filter refresh, mobile layout, and keyboard-only use. Add
focused rendering/state tests where the project supports them.

---

## Explicit bullet-to-task coverage

| Requested bullet | Covered by |
| --- | --- |
| Fix the definitive AI integration | T02 and T06 |
| Update the admin management interface | T05 |
| Admin does not need “My Dashboard” | T01 and T05 |
| Give admin power to edit a user's level and characteristics | T04 and T05 |
| Let admin modify terminology | T03 |
| Characterize level and objectives by job role/user in the admin interface | T03, T04, and T05 |
| GitHub account connection in the user's profile | T07 |
| Expand/collapse evidence on click | T09 |
| Automate PR import | T08 |
| Let users add observations to AI analysis | T06 |
| `ADMIN` = `MANAGER` | T01 |

## Deliberately removed from this plan

- Jira evidence import and Slack evidence import, including their product/privacy
  gates.
- Generic multi-provider Connection Center and provider-neutral OAuth workflows.
- Teams, hierarchy, departments, promotion decisions, probability scoring, and
  broad reporting features.
- Streaming/chat, scheduled provider polling, a separate employee “My Plan”/
  dashboard redesign, and unrelated visual redesign.

## Definition of completion

This revised backlog is complete when T01 through T09 are accepted and this
end-to-end flow works:

`Manager → user career plan → GitHub setup in Profile → repeatable PR sync → optional employee observation → validated server AI analysis → expandable evidence reading`.
