# Audit of all 20 captured screens

Visual baseline: [archive README](../frontend-screenshots-2026-09-06/README.md) and [contact sheet](../frontend-screenshots-2026-09-06/00-contact-sheet.png). Every numbered image below was opened individually. Heights are actual PNG metadata; all widths are 1440 px, captured from a 1000 px-high viewport. Screenshots establish visible composition; source establishes interactions and hidden states. The states below are **required target behavior**, not a claim that every state was captured.

State shorthand: **L** loading; **E** empty; **X** error; **S** success; **D** disabled; **P** permission/authentication. Read-only success means loaded content, not a write. Global rules apply to every protected screen: authenticated 401 clears session and returns through login; 403 preserves session and offers role home; unknown/foreign resources do not reveal private identity. Each entry adds its own relevant states.

## 01 — Public landing · 5,571 px

[Image](../frontend-screenshots-2026-09-06/01-public-landing-full.png)

- **Purpose / role / primary task:** Public visitor understands evidence-based career progression and starts access.
- **Stacking:** Hero, problem, solution, five-step process, example dashboard, audiences, benefits, integrations and CTA are sequential marketing content. This is a coherent narrative, not unrelated authenticated tasks. The excessive repetition can be shortened.
- **Navigation / hierarchy:** Product/how/benefits anchors are appropriate. Very large type and many equal-weight sections make product proof slow to reach; integration cards suggest capabilities beyond implemented GitHub PR capture. Illustrative readiness/“75%” values should be clearly example content or removed.
- **Growth / visible actions:** Long vertical growth is permitted here; no horizontal overflow visible. Keep compact sticky public header with Entrar/Começar; section anchors must land clear of it. Do not force narrative into a fixed-height shell.
- **Destination / disposition:** **Retain and simplify** `/`, keeping anchor links. Shared purple/navy type/color/button tokens with app; public layout retains normal document scroll and footer. Wireframe scenario “Public landing”.
- **States:** L: content visible without API/bootstrap dependence, fixed image dimensions. E: optional missing illustration leaves readable copy/CTA. X: failed illustration has alt/fallback, no blocked signup link. S: CTA reaches login/register route. D: unavailable provider is labeled planned, not a connection button. P: public access; authenticated visitor may explicitly open role workspace.

## 02 — Login · 1,280 px

[Image](../frontend-screenshots-2026-09-06/02-auth-login.png)

- **Purpose / role / task:** Unauthenticated employee or manager signs in.
- **Stacking:** Login form sits beside demo credentials and a tall integration catalog; marketing footer adds height.
- **Navigation / hierarchy:** “Painel” before auth and repeated Entrar buttons compete with the form. Login/register switching is memory state and uses `/login` for both.
- **Growth / visible actions:** Submit is initially visible at this capture size, but the surrounding header/aside causes document scroll; mobile stacking will worsen it. Keep submit, mode switch and validation adjacent to fields, not displaced by promotional content.
- **Destination / disposition:** **Retain and simplify** `/login?returnTo=…` in AuthLayout. Remove demo credentials in production; separate real `/register` link. No side integration list.
- **States:** L: “Entrando…” disables only submit; preserve input/focus. E: blank form with correct autocomplete. X: invalid credentials inline; auth 401 must not show “session expired”; network failure offers retry. S: server identity chooses role route or authorized returnTo. D: duplicate submission blocked. P: no role selector or trust in cached role; session-validation failure preserves credentials.

## 03 — Register · 1,280 px

[Image](../frontend-screenshots-2026-09-06/03-auth-register.png)

- **Purpose / role / task:** Visitor creates account with name, email and password.
- **Stacking:** Same demo/provider aside as login, still irrelevant to account creation.
- **Navigation / hierarchy:** Registration is just an auth tab with no durable URL. “Entrar” top CTA and login tab provide duplicate exits. Name adds height but not distinct workflow complexity.
- **Growth / visible actions:** Form nearly fills first viewport after oversized heading; avoid fixed-height centering that hides submit under mobile keyboard. Form may scroll as one task.
- **Destination / disposition:** **Retain** `/register`, compact AuthLayout. Keep existing registration rules unless product policy changes; no invented invitation or role selection flow.
- **States:** L: create pending with retained name/email. E: pristine fields. X: duplicate email, validation and network errors mapped to fields/form. S: save returned session and continue safely. D: repeated submit blocked. P: server determines role; retain safe returnTo through login/register and refresh.

## 04 — Employee dashboard populated · 2,966 px

[Image](../frontend-screenshots-2026-09-06/04-employee-dashboard-populated.png)

- **Purpose / role / task:** Maria browses saved analyses and career summaries; primary task is unclear because several views coexist.
- **Stacking:** Two saved analyses, summary metrics, source distribution, estimated levels and recent trend. Pending inbox would be above them when present.
- **Navigation / hierarchy:** Header mixes public home, panel, profile, “Nova evidência” and duplicate CTA. Dashboard, Framework, Criteria and Connections tabs mix independent destinations. Clear history shares the filtering area and appears on unrelated views.
- **Growth / visible actions:** Nearly three viewports for two results; report controls inaccessible while reading lower sections. Date filters, library navigation and Import need stable placement. Clear history stays discoverable in overflow, not prominent.
- **Destination / disposition:** **Split** `/app/overview`, `/app/analyses`, `/app/analyses/reports`. Overview keeps metrics plus at most two compact recent rows, linking to full list. Each report is a separate facet.
- **States:** L: independent overview skeleton/list/report loads. E: no saved data and no filter results distinguished. X: failed insight panel does not erase saved list. S: current counts and selected period visible. D: clear disabled when no affected analyses; no manager clear. P: employee own workspace only.

## 05 — Employee saved analysis expanded · 3,272 px

[Image](../frontend-screenshots-2026-09-06/05-employee-dashboard-analysis-expanded.png)

- **Purpose / role / task:** Maria inspects one saved result and opens full analysis.
- **Stacking:** Expanded source summary and justification duplicate row content; full analysis is yet another step. All reports still follow.
- **Navigation / hierarchy:** Disclosure is an intermediate navigation layer; selected record is memory-only. “Abrir análise completa” is hidden until expansion.
- **Growth / visible actions:** Expansion adds 306 px and pushes reports down. Keep Open analysis directly available per row, with parent context on detail.
- **Destination / disposition:** **Merge with Analyses list and convert expansion to detail route** `/workspace/people/:ownerId/analyses/:analysisId?view=summary`. Remove the intermediate full-content accordion.
- **States:** L: destination skeleton after click. E: optional competencies/suggestions show no data, not missing analysis. X: deleted/unavailable ID with return to filtered list. S: canonical selection survives reload. D: historical row without canonical ID displays unavailability, no broken action. P: owner read-only review visibility.

## 06 — Employee analysis detail and review · 1,918 px

[Image](../frontend-screenshots-2026-09-06/06-employee-analysis-detail-review.png)

- **Purpose / role / task:** Maria reads classification and review history for Slack communication evidence.
- **Stacking:** Oversized level banner, justification, competencies, suggestions, source, history and generic next actions. History is below first viewport.
- **Navigation / hierarchy:** Title says “Detalhe da evidência” while displaying analysis; Back at bottom; no owner breadcrumb or explicit history location. Current screenshot history is empty (“Não revisada”), not a populated history example.
- **Growth / visible actions:** Almost two viewports; long source/history will grow indefinitely. Keep parent link, status and Summary/Source/History controls visible.
- **Destination / disposition:** **Retain as shared analysis detail**, splitting facets. Summary shows historical L4 → L5 context; do not replace it with live profile L3 → L4 from screen 10. Generic next actions merge into suggestions.
- **States:** L: analysis and history load independently. E: no history = Não revisada; no suggestions explicit. X: history failure keeps analysis readable with Retry. S: loaded status/history including reviewer, timestamp, optional comment. D: no manager review form; no historical source link if absent. P: own analysis only for employee, scoped manager read allowed.

## 07 — Framework all levels expanded · 3,769 px

[Image](../frontend-screenshots-2026-09-06/07-employee-dashboard-framework-all-levels.png)

- **Purpose / role / task:** Maria compares criterion support across L3, L4 and L5.
- **Stacking:** Three complete eight-criterion grids can all expand; definitions and supporting records can grow again inside each card.
- **Navigation / hierarchy:** “Suas evidências” obscures framework context; all-level disclosure is being used as navigation. Period applies, but Clear history has no place here. One support reference explicitly says detail unavailable for historical references.
- **Growth / visible actions:** 24 criteria yield almost four viewports before full descriptions. Keep level, support filter, dates and return-to-criterion navigation visible.
- **Destination / disposition:** **Retain and restructure** `/app/framework?level=L4`; criteria in bounded list, one definition/support detail at a time. No “expand all levels” default. All official levels still available through explicit selection.
- **States:** L: coverage skeleton retains controls. E: no configured framework versus configured criterion with no support distinguished. X: coverage API failure with retry. S: support count uses server data. D: legacy support without valid IDs is text with explanation. P: owner coverage; configuration authoring not available here.

## 08 — Criteria gaps · 2,909 px

[Image](../frontend-screenshots-2026-09-06/08-employee-dashboard-criteria.png)

- **Purpose / role / task:** Maria identifies framework criteria with no saved supporting analysis in period.
- **Stacking:** Repeated gap rows across all levels; 23 missing out of 24 supported pairs in these fixtures. No genuinely unrelated feature inside the list itself.
- **Navigation / hierarchy:** Separate Criteria tab duplicates Framework information; repeated no-evidence disclaimer on every row consumes space. No direct criterion definition navigation.
- **Growth / visible actions:** Nearly three viewports of rows. Keep support/level/date filters and count visible; no clear history action.
- **Destination / disposition:** **Merge** into Framework `support=missing`, with all existing descriptions preserved in criterion detail. One clear no-negative-assessment explanation applies to the view.
- **States:** L: loading must not read as no gaps. E: zero gaps versus no framework versus no analyses explicitly distinguished. X: retry retains selected period/level. S: exact filtered criteria count. D: no “fix gap” write action; no promise of promotion readiness. P: employee coverage data only.

## 09 — Connections redirect tab · 1,000 px

[Image](../frontend-screenshots-2026-09-06/09-employee-dashboard-connections.png)

- **Purpose / role / task:** Employee tries to reach GitHub setup.
- **Stacking:** No large data stack; the problem is an entire empty detour screen saying GitHub moved to Profile.
- **Navigation / hierarchy:** Connections is reachable from dashboard but configuration lives elsewhere. The instruction card is the only meaningful content.
- **Growth / visible actions:** Fits captured viewport, yet wastes much of it. Direct Integrations navigation should always be available.
- **Destination / disposition:** **Remove intermediary screen; merge destination** `/app/integrations/github`. Old `?tab=connections` redirects once via replace.
- **States:** L: real connection loading replaces navigation placeholder. E: not configured offers setup. X: settings failure offers Retry. S: saved/tested/synced separately labeled. D: sync not offered on unsaved/invalid settings. P: employee connection only; manager does not inherit access.

## 10 — Employee career profile + GitHub · 2,025 px

[Image](../frontend-screenshots-2026-09-06/10-employee-profile-career-and-github.png)

- **Purpose / role / task:** Maria reads assigned career plan, or configures/imports GitHub; two separate tasks.
- **Stacking:** Read-only job role/levels/characteristics/objectives and official level ladder, then repository/author/save/test/disconnect/sync, then manual PR search/import; pending GitHub list also appears here in source when populated.
- **Navigation / hierarchy:** “Perfil” actually means career plan and integration administration. “GitHub conectado” appears with empty fields and no sync, so it is not trustworthy state feedback. Manual import is below other operations.
- **Growth / visible actions:** Two viewports in this small fixture; objectives/pending PRs would grow further. Keep plan view selectors, and integration save/test/sync in their respective focused workspaces.
- **Destination / disposition:** **Split** `/app/career-plan?view=objectives|context`, `/app/integrations/github?view=connection|sync`, `/app/integrations/github/import`. Replace duplicate pending PR list with scoped Inbox link.
- **States:** L: plan and integration have independent loaders. E: no characteristics/objectives, no saved config and no PR results are distinct. X: profile failure doesn't block integration route; save/test/sync/import errors retain inputs. S: explicit config saved, access tested, sync counts discovered/created/existing/failed and timestamp. D: employee plan read-only; sync/search use saved valid config, disconnect disabled if unconfigured. P: no personal token field or OAuth claims; repository access depends on server credentials.

## 11 — Employee pending inbox · 3,933 px

[Image](../frontend-screenshots-2026-09-06/11-employee-dashboard-pending-inbox.png)

- **Purpose / role / task:** João triages four pending records; three dismissed records also shown.
- **Stacking:** Pending and dismissed mixed, then empty saved-analysis library, zero metrics and three separate empty report panels.
- **Navigation / hierarchy:** Triage actions require expanding rows. “Limpar histórico” could be confused with clearing inbox even though it deletes saved analyses.
- **Growth / visible actions:** Almost four viewports despite no saved analyses. Keep status/source/date filters, item actions and page controls available.
- **Destination / disposition:** **Split** to `/app/inbox?status=pending`; dismissed is explicit filter. Empty Analyses and Overview no longer trail the inbox.
- **States:** L: list skeleton with pending count unknown, not zero. E: all caught up versus no filtered matches. X: fetch failure not treated as empty; Retry preserves filters. S: dismissal updates pending/dismissed counts after acknowledgement. D: dismissed items have no analyze/restore action. P: João only can analyze/dismiss his records.

## 12 — Employee pending evidence expanded · 4,348 px

[Image](../frontend-screenshots-2026-09-06/12-employee-dashboard-pending-expanded.png)

- **Purpose / role / task:** João reads GitHub PR #7 source and chooses Review or Dismiss.
- **Stacking:** Full source expanded among all other records, followed by empty library/reporting sections from 11.
- **Navigation / hierarchy:** A very long body separates list title from action buttons; source URL is rendered as text. Selecting/expanding has no durable address.
- **Growth / visible actions:** Adds 415 px even for this source; arbitrary PR descriptions can grow without bound. Keep Open evidence and Dismiss in preview footer; full source uses focus route.
- **Destination / disposition:** **Convert to bounded preview/detail**, Inbox `selected=:id` and `/workspace/people/:ownerId/evidence/:evidenceId`. Use safe clickable source link when valid, wrapping display URL if shown.
- **States:** L: preview-specific spinner does not replace list. E: absent source metadata shown explicitly. X: source no longer pending gives current state, not stale actions. S: selected item persists through URL and list scroll restore. D: analyze/dismiss disabled during mutation; no source edits. P: owner mutation only, safe foreign-ID handling.

## 13 — Pending evidence before analysis · 1,616 px

[Image](../frontend-screenshots-2026-09-06/13-employee-pending-evidence-review.png)

- **Purpose / role / task:** João reads immutable source, optionally adds observation, then requests analysis.
- **Stacking:** This is already one coherent task; oversized hero, long source and explanatory side panel displace the form actions. Keep source and observation distinct.
- **Navigation / hierarchy:** “Voltar ao painel” lacks inbox context. Analyze is only at form bottom. Source note explains separation but need not occupy a permanent large aside.
- **Growth / visible actions:** Submit is below first viewport. Keep an editor footer visible; source and observation use one workspace or explicit narrow-screen facets without hiding full content.
- **Destination / disposition:** **Retain focused detail** `/workspace/people/:ownerId/evidence/:evidenceId`. Rename action “Analisar evidência”, not create new evidence. On acknowledged success replace with canonical analysis route.
- **States:** L: “Analisando…” without fabricated percent or durable-job claim. E: optional observation blank valid, source missing disables analysis. X: recoverable engine error keeps 2,000-char draft; 409 shows changed record/profile constraints. S: server returns saved analysis ID; “Análise salva” and next pending link. D: one submission at a time, no edit-source control; missing/invalid career context explained. P: employee owner only; manager sees source read-only.

## 14 — Manager People + João plan · 1,680 px

[Image](../frontend-screenshots-2026-09-06/14-manager-people-joao-career-plan.png)

- **Purpose / role / task:** Manager finds João and changes career context or adds objective.
- **Stacking:** Directory introduction/filters/people and selected person's role/levels/characteristics form, then objective creation. Related content but multiple editing tasks shown together.
- **Navigation / hierarchy:** Directory is a contextual master list, not global app navigation. Manager Console duplicated in header. Person selection writes hash, sections do not persist. Long email/name truncates in directory.
- **Growth / visible actions:** Save plan and Add objective below first viewport; minimum-column objective row is cramped. Keep person title/section links; edit context footer stable.
- **Destination / disposition:** **Retain master-detail, split editors** `/manage/people/:id/career-plan?view=context`; objectives facet/new objective route separate. Base directory requires explicit person selection.
- **States:** L: independent directory and selected plan. E: zero people, no search match, no selection and no objectives distinct. X: plan error leaves directory usable. S: save updates plan and person summary. D: invalid role/level pair and saving disable commit; no selected person disables edit. P: manager only; allowed server scope, no implied team filter.

## 15 — Manager João evidence list · 2,925 px

[Image](../frontend-screenshots-2026-09-06/15-manager-joao-evidences.png)

- **Purpose / role / task:** Manager reads João's captured evidence across analyzed, pending and dismissed states.
- **Stacking:** A long list rather than unrelated features; its lack of a bounded region makes global context scroll away. Directory filters stay in an oversized side stack.
- **Navigation / hierarchy:** Raw ANALYZED/DISMISSED/PENDING enums, no evidence-specific status/date filters in rendered manager view, and disclosure rows obscure the open-detail action.
- **Growth / visible actions:** 17 records produce almost three viewports. Retain person sections, list filters, count and pagination in fixed workspace rows.
- **Destination / disposition:** **Retain and bound** `/manage/people/:id/evidence`; rows open shared evidence detail. Translate states consistently and distinguish capture state from review state.
- **States:** L: only evidence list skeleton. E: person has none versus no filter matches. X: list Retry does not clear person. S: loaded read-only records, analysis link only when resolvable. D: no analyze/dismiss controls. P: manager read under selected owner; source endpoint needs manager scope.

## 16 — Manager João evidence expanded · 3,317 px

[Image](../frontend-screenshots-2026-09-06/16-manager-joao-evidence-expanded.png)

- **Purpose / role / task:** Manager reads source of João's analyzed PR #16.
- **Stacking:** Full source inside 17-record list; “Visualização gerencial somente leitura” is correct but buried at end of expanded body.
- **Navigation / hierarchy:** Selection not durable, read-only nature not visible until expanded; full source competes with directory/list.
- **Growth / visible actions:** Expansion adds 392 px; unbounded evidence length can break reading context. Keep parent and source link visible; no fake manager triage actions.
- **Destination / disposition:** **Convert expansion to shared read-only evidence detail** `/workspace/people/:id/evidence/:evidenceId`. Owner context and return link restore directory filter/scroll and evidence section.
- **States:** L: detail skeleton on canonical ID. E: no linked analysis explicit. X: inaccessible/deleted source with safe return. S: loaded immutable content and persisted capture state. D: analyze, source editing and dismissal unavailable. P: manager read capability; API authorization independent of URL.

## 17 — Manager Maria plan with objective · 1,814 px

[Image](../frontend-screenshots-2026-09-06/17-manager-maria-career-plan-objective.png)

- **Purpose / role / task:** Manager updates Maria's existing objective or creates another.
- **Stacking:** Role/levels/characteristics editor above existing objective editor plus new objective editor, each with independent submit behavior.
- **Navigation / hierarchy:** Existing objective “ad” is real captured data; no inference about meaningful objective quality. Raw ACTIVE label; related Save/Update/Add actions are visually competing. Person context correctly shows one active objective.
- **Growth / visible actions:** Existing objective below career form, additive forms scale badly. Keep objective list/status controls; open one objective editor with Save/Cancel footer.
- **Destination / disposition:** **Split and retain** `/manage/people/:id/career-plan?view=objectives` plus `/objectives/:objectiveId` and `/objectives/new`. Prototype meaningful objective text is illustrative, not claimed as screenshot data.
- **States:** L: objective loading independent from plan context. E: no active objectives versus none ever created. X: field errors/conflict preserve text, status and target date. S: update refreshes active objective count and employee read model. D: only edited form is busy; no unsupported delete action. P: manager write, employee read; role change still validates official levels.

## 18 — Manager Maria saved analyses · 1,420 px

[Image](../frontend-screenshots-2026-09-06/18-manager-maria-analyses.png)

- **Purpose / role / task:** Manager selects one of Maria's two saved analyses for review.
- **Stacking:** Only one domain list, but large directory/filter/header consumes much of viewport and result cards are spacious.
- **Navigation / hierarchy:** Raw “medium/high” shown; review status not visible in row; date/filter affordance absent from current manager section despite API date support. Selected Analysis tab not durable.
- **Growth / visible actions:** Two rows produce 1.4 viewports; large datasets multiply it. Keep section links/filter row and direct Open analysis action.
- **Destination / disposition:** **Retain and compact** `/manage/people/:id/analyses`, sharing result-list components. Also reachable from new cross-person Reviews queue without directory detour.
- **States:** L: list only. E: no saved analyses or filtered matches. X: retry list without losing person selection. S: human-readable confidence and review projection. D: missing canonical ID disallows broken detail; no employee history clear. P: manager only for this owner workspace.

## 19 — Manager analysis + review controls · 2,175 px

[Image](../frontend-screenshots-2026-09-06/19-manager-analysis-detail-review-controls.png)

- **Purpose / role / task:** Manager accepts Maria's analysis or asks for context, optionally commenting.
- **Stacking:** Complete analysis before current review status/composer/actions, then generic next actions and Back at bottom. Captured history is empty, with current status Não revisada.
- **Navigation / hierarchy:** Owner identity/person tabs lost on leaving directory; detail uses same ambiguous evidence title. Review actions should be first-class workflow, not lower-page content.
- **Growth / visible actions:** Accept/Request context and parent navigation below first viewport. Keep compact historical summary, link to full source/history, editor and stable action footer.
- **Destination / disposition:** **Retain as manager facet of shared analysis detail** `?view=review`; links to Summary/Source/History; review queue return context persists. No separate duplicate analysis entity.
- **States:** L: disable decision until analysis+latest review loaded. E: no events means unreviewed. X: save error preserves comment; ambiguous response reconciles history before retry; 409 handles stale version when new contract exists. S: appended event and status update announced; return-to-queue remains available. D: pending save, missing ID, lost capability; same-status repeat handled explicitly, not accidental double click. P: manager only writes; employee history facet has no composer.

## 20 — Career configuration · 3,129 px

[Image](../frontend-screenshots-2026-09-06/20-manager-career-settings-full.png)

- **Purpose / role / task:** Manager edits organization labels and creates/updates/archives job roles with allowed framework levels.
- **Stacking:** Six-field terminology form, new-role form, two full role editors, allowed-level checkboxes and archive replacement controls. Every added role adds another large form.
- **Navigation / hierarchy:** Two independent configuration domains on one screen; multiple save actions and dangerous archive action embedded per card. Framework levels can be assigned, not authored here. “Manager Settings” English eyebrow inconsistent with product Portuguese.
- **Growth / visible actions:** Three viewports with only two roles; side terminology column mostly empty below first form. Keep section navigation and role list/create; save/archive visible only for selected role editor.
- **Destination / disposition:** **Split** `/manage/career/terminology`, `/manage/career/roles`, `/manage/career/roles/:roleId`, read-only `/manage/career/framework`. Archive confirmation includes assignment/replacement context. No invented framework CRUD or unarchive capability.
- **States:** L: per-section load; catalog error need not erase terminology form. E: no roles/archived results; no allowed levels explains why creation disabled. X: required text, duplicate/invalid role, incompatible replacement or server error inline and draft retained. S: terminology preview and labels refresh; created/updated/archived role acknowledged. D: archived role read-only, require ≥1 allowed level, save while busy disabled. P: manager write only; labels never grant permissions or change route IDs.

## Feature completeness cross-check

| Current feature / action | Target owner |
|---|---|
| Public anchors, product story and CTA | Public layout, screen 01 retained |
| Login/register, mode switch, session restore/logout | Auth routes + account control; screens 02–03 |
| Header New evidence / View new evidence | Inbox route or explicit next pending link; no free-text create feature invented |
| Pending and dismissed evidence, full content/source URL | Inbox + canonical evidence detail; 11–13 and manager 15–16 |
| Optional observation, analysis submission/loading/error/result/next | Evidence detail → analysis detail with acknowledged result; 13 and source-only states |
| Saved library, expansion, clear history by date/all | Analyses list/detail + scoped destructive overlay; 04–06 |
| Counts, source distribution, estimated levels, recent trend | Overview metrics + Analyses Reports facets; 04/11 |
| Framework groups, descriptions, supporting references, missing criteria | Framework level/filter/detail; 07–08, preserving legacy unavailable references |
| Employee read-only role/current/target/characteristics/objectives/levels | Career Plan context/objectives + Framework link; 10 |
| GitHub save/test/disconnect/sync and discovered/created/existing/failed counts | Integrations connection/sync; 09–10 |
| GitHub manual search, select “Usar”, PR number import, pending actions | Dedicated import; selected PR in URL, imported result links to Inbox; 10/source |
| People query/name/email/role/level filters; person summary | Manager People master-detail; 14–18 |
| Manager captured evidence read; saved analyses open | Person evidence/analyses routes + shared details; 15/16/18 |
| Plan edit, characteristic parsing, objective create/update/status/date | Person plan/context and single objective editor; 14/17 |
| Read history, accept, request context, optional comment | Shared analysis History/Review with capability gate; 06/19 |
| Six custom labels; role create/update/allowed levels/archive/replacement | Career Configuration; 20 |
| Permission screen, no results, HTTP failures, expired credentials | Route-level state boundaries in persistent shell + auth layout |

No captured screen is omitted. Removed content consists of navigation detours, duplicate full-record expansions, repeated generic guidance and promotional chrome inside authenticated workspaces; underlying supported tasks remain mapped.
