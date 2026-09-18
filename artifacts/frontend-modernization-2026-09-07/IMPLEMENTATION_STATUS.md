# Promova modernization implementation status

Updated: 18 September 2026

This is the continuation and release-evidence ledger for the approved modernization in `PLAN.md`.
The planning package and original screenshot archive are user-owned inputs and remain intact.

## Baseline and decisions

- Source reconciliation: `HEAD` is the planning baseline commit
  `93d31ccafce453f45eeaf7bceb09ec13d8bcec24`; no later committed source changes needed merging.
- Pre-existing uncommitted inputs preserved: `.sdkmanrc`, the 2026-09-06 screenshot archive, and the
  2026-09-07 planning package. No `AGENTS.md` exists in the repository.
- All 35 wireframe concepts were inspected in a browser at 1440×1000 and 390×844 and measured at
  320×740. `gallery.html`, `previews/`, all 20 original audit screenshots, and long original captures
  were reviewed before implementation.
- Native ES modules are retained. The approved application is now the sole production frontend:
  `frontend/app.mjs`, `styles.css`, and `frontend/services/api.mjs` are canonical, with no UI selector.
- Existing unpaged array endpoints remain compatible. Pagination is opt-in with `page`/`pageSize`,
  one-based pages, a default size of 25, a hard maximum of 50, and stable secondary ID ordering.
- Existing global manager-to-employee authority is preserved. Every manager resource still validates
  the authenticated manager and that the selected owner is an employee. This work does not claim
  tenant/team isolation that the source domain does not provide.
- Deleting saved analyses continues to delete their reviews while preserving captured evidence.

## P0 — contracts and parity map

- [x] Reconciled source/Git state and confirmed all current domain behaviors and audit invariants.
- [x] Reviewed every one of the 20 audit mappings and all 35 visual concepts.
- [x] Added route-registry cases, safe query normalization, and canonical entity-ID mappings.
- [x] Added shared page envelope, direct-resource contracts, and deterministic framework identities.
- [x] Investigated the baseline GitHub sync test failure: a fixed 8 August fixture had fallen outside
  its rolling 30-day window. The fixture now derives timestamps from the test clock; the full suite is green.

## P1 — shell, routing, session

- [x] Separate public, authentication, employee, and manager layouts with a persistent authenticated shell.
- [x] Explicit route registry, canonical query handling, native links, `popstate`, titles,
  not-found/forbidden states, and heading focus.
- [x] Safe same-origin protected `returnTo`, cold cached-session validation, role guards, expiry redirect,
  and employee ownership guard before a resource request.
- [x] Distinct role navigation, account dialog, skip link, keyboard-contained mobile drawer, and explicit
  dialog focus containment/return.
- [x] Intended HTML deep links fall back in both development and production-build servers; missing assets
  return 404 rather than HTML.

## P2 — additive backend contracts

- [x] Owner and manager canonical analysis detail with owner/evidence IDs and current review projection.
- [x] Owner and manager canonical evidence detail, with server-side ownership/role enforcement.
- [x] Paginated/filterable evidence, analysis, people, and manager-person collections with stable ordering.
- [x] Direct employee summary and aggregate review queue with counts, filters, current status, and one
  bounded client request (no client fan-out).
- [x] Stable SHA-256 framework version, criterion IDs, canonical supporting analysis IDs, and manager
  read-only framework endpoint.
- [x] GitHub PR search paging metadata.
- [x] Review idempotency/stale-history conflict plus version-aware career-plan/objective mutations.
- [x] V8 adds a nullable review idempotency key and indexes only; old clients remain valid.
- [x] Authorization, direct-resource, pagination, filtering, queue, duplicate, conflict, length-boundary,
  and stale-version backend tests.

## P3 — employee workspaces

- [x] Overview metrics, recent analyses, framework support, and next pending action.
- [x] Paginated Inbox with filters, pending/dismissed states, bounded desktop preview, canonical detail,
  separate immutable source/observation, guarded analyze/dismiss actions, and ambiguous-response reconciliation.
- [x] Paginated Analyses, scoped clear-history confirmation, Summary/Source/History facets, review status,
  and missing/deleted/unavailable result states.
- [x] Reports by source/level/trend and Framework level/support/date views with canonical criterion links.
- [x] Loading, empty, error, success, disabled, permission, and session states use actual API outcomes.

## P4 — career, manager, configuration, GitHub

- [x] Employee read-only Career Plan objective/context workspaces.
- [x] Manager People directory, direct summary, and separate plan/evidence/analysis routes.
- [x] Career-context and objective editors preserve drafts, send version expectations, and use server-enforced
  allowed levels.
- [x] Aggregate Reviews queue and manager-only append-only review facet with 2,000-character boundary,
  deterministic retry key, duplicate guard, and stale-history conflict.
- [x] Six terminology fields; role list/detail/new/archive replacement; archived read-only state; official
  framework read-only route.
- [x] GitHub connection/test/disconnect/sync/import split with saved-versus-draft state, paged search, and
  canonical Inbox/detail handoff.

## P5 — hardening and rollout readiness

- [x] Obsolete reads abort and late commits are rejected by navigation epoch.
- [x] Evidence/review/objective drafts survive local rerenders; dirty navigation/unload is guarded.
- [x] Duplicate clicks are disabled; evidence mutations reconcile unknown outcomes; review retries reuse the
  same server idempotency identity; conflicts preserve editable values.
- [x] Responsive bounded workspaces, visible focus, skip link, drawer/dialog focus contract, Escape, reduced
  motion, live loading/toast states, long-text wrapping, and no horizontal-overflow concealment.
- [x] Immutable-artifact rollout/rollback and additive migration behavior documented in `ROLLOUT.md`.
- [x] Frontend lint/tests/build, backend full tests, boot JAR/runtime smoke, and production Chrome E2E pass.
- [x] 24 deterministic actual-application screenshots captured: all 20 audit mappings plus four critical
  mobile states, with machine-readable timing/viewport evidence in `actual/verification.json`.
- [x] A second production-build control audit exercised 29 workflow groups across both roles, including
  every authenticated mobile navigation destination, manager edits/reviews/configuration, GitHub actions,
  evidence mutations, destructive confirmations, and explicit logout token invalidation.
- [x] The follow-up audit found and fixed four shared frontend regressions: filter serialization/navigation
  races, missing manager person tabs, intercepted native dialog submissions, and a pointer-blocking mobile
  drawer backdrop. Permanent E2E assertions now cover each path.
- [x] The pre-production consolidation removed the superseded UI, duplicate views/components/services/tests,
  `modern-*` production names, legacy URL shims, and `PROMOVA_MODERN_UI`; only the approved implementation ships.
- [!] Firefox, desktop Safari, mobile Safari, and mobile Chrome engine runs remain externally blocked; see below.

## Release-gate evidence

- [x] Desktop 1440×1000 and 1280×720; 501 pending rows are server-paged to 25 and remain in the declared
  list/preview regions. Production E2E uses source text over 10,000 characters plus an unbroken 1,200-character token.
- [x] 390×844, 320×740, 768×1024, 844×390, 200% text, and the 320-CSS-pixel reflow equivalent of
  1280px at 400% zoom; commit controls remain reachable with zero body horizontal overflow.
- [x] Cold load, direct nested route, reload, parent/breadcrumb links, back/forward, filters, invalid/deleted
  IDs, safe `returnTo`, expiry redirect, forbidden role, and intentional production fallback.
- [x] Same-owner/foreign-owner and employee/manager authorization tests return non-leaking 404/403 results.
- [x] Skip link, route-heading focus, visible focus, keyboard drawer/dialog containment and return, Escape,
  native-link rows, bounded scrolling, and text-plus-color states.
- [x] Mutation duplicate/retry, ambiguous outcome, stale review/plan/objective versions, archive replacement,
  status validation, and 2,001-character rejection have automated coverage.
- [x] Historical profile context, criterion-gap disclaimer, source/observation separation, all six terminology
  fields, official levels, allowed roles, and unknown-status fallback are retained.
- [x] Active-route calls only; list API and DOM are bounded. On the declared local Chrome/H2/stub profile,
  employee Overview cold load was 584ms, its slowest API was 16ms, and usable in-app Overview→Inbox
  navigation was 144ms. The observed cold-route range was 539–1,376ms, excluding analysis-engine runtime.
- [x] Actual production-build application exercised as both employee and manager in Google Chrome.
- [!] Cross-engine/device gate incomplete because the required engines are unavailable or disabled.

## Validation evidence

- `npm run check`: lint passed, 16/16 canonical frontend tests passed, production build passed. Obsolete
  tests tied only to the removed UI were deleted rather than carried into the production architecture.
- `backend/gradlew test`: 139/139 backend tests passed.
- `npm run smoke:ci`: frontend build, backend `bootJar`, packaged runtime/auth/CORS smoke passed.
- `npm run test:e2e`: 1/1 production-build browser scenario passed after creating 500 real records;
  both roles, 20 mappings, four mobile captures, routing, session, permission, keyboard, long-content,
  pagination and reflow checks passed.
- Production control audit: 29/29 workflow groups passed after remediation; no application 404, console
  exception, API error response, or failed mutation remained. Chromium reported `ERR_ABORTED` for three
  successful 204 responses; response assertions, persisted mutation state, and 401 checks for both revoked
  logout tokens confirmed successful completion.
- Rollback uses the previously verified immutable artifact or a source revert; no parallel UI is compiled.
- Screenshot directory: `actual/`; structured evidence: `actual/verification.json`.

## Material deviations / external blockers

- Firefox is not installed on the host. Safari 26.6 is installed, but `safaridriver` rejects session creation
  until a user enables “Allow remote automation” in Safari Settings. Xcode `simctl` is unavailable, so there
  is no iOS Simulator; no Android/mobile-Chrome runtime is installed. Enabling Safari automation is a user
  setting and installing browser/simulator engines is outside the repository implementation. Chrome desktop
  and responsive/emulated viewport gates pass; cross-engine sign-off must be run when those environments exist.
- No product/API deviations from the approved plan were accepted. No deploy, merge, or publish was performed.
