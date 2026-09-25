# Promova modernization planning package

7 September 2026 · Planning only · No product implementation changes.

Start with the [implementation plan](PLAN.md), then open the [interactive concepts](wireframes.html). Use the Concept selector to switch between 35 scenarios. The dark selector bar and displayed route path are design-review annotations, not proposed product chrome. Resize to mobile to inspect the drawer and list-to-detail behavior.

- [Complete 20-screen audit](AUDIT.md): each captured screen, its task, problems, destination, disposition and six state categories.
- [Implementation plan](PLAN.md): route map, shell/scroll rules, feature ownership, migration, authorization, API gaps, module boundaries, phases and release gates.
- [Wireframe gallery](gallery.html): captured desktop and mobile concepts together for quick comparison.
- [Editable concepts](wireframes.html), [styles](wireframes.css), [behavior](wireframes.js): native HTML/CSS/JavaScript, no external runtime or API calls.
- [Verification](VERIFICATION.md) and [machine-readable results](verification.json).

## Coverage in the concepts

| Requested concept | Selector entry / gallery |
|---|---|
| Desktop employee shell and overview | Employee · Overview |
| Desktop manager shell | Manager · People workspace, Manager · Review queue |
| Mobile drawer + representative screen | Mobile · Drawer + inbox; Employee · Bounded inbox at narrow width |
| Bounded evidence inbox and focused detail | Employee · Bounded inbox, Employee · Focused evidence |
| Saved analysis and review history | Shared · Analysis summary, Shared · Review history, Shared · Immutable source |
| Career plan | Employee · Career plan, Employee · Career context |
| Manager People master-detail | Manager · People workspace |
| Manager review workflow | Manager · Review queue, Manager · Review decision |
| Career configuration | Manager · Career configuration, Role detail, Terminology, Official framework |
| Loading, empty, error | States · Loading, Empty inbox, Recoverable error; also Permission denied |
| Additional retained features | Reports, Framework + gaps, GitHub connection/sync/import, Manager person evidence/analyses, analysis success, public landing, login and registration |

## Inspect and regenerate

Open `wireframes.html` directly in a browser, or serve the repository root so the existing brand asset resolves. Example from the repository root:

```sh
python3 -m http.server 14174 --bind 127.0.0.1
```

Then open `http://127.0.0.1:14174/artifacts/frontend-modernization-2026-09-07/wireframes.html`.

Regenerate checks and viewport PNGs from the repository root:

```sh
node artifacts/frontend-modernization-2026-09-07/verify-wireframes.mjs
```

This uses installed `playwright-core` and Google Chrome at its normal macOS application path. It writes only this package's previews and verification record. The original screenshot archive is untouched.

## Prototype limits

These are navigable wireframes, not the refactored application. Hash navigation selects sample scenarios; the displayed paths describe the intended canonical product routes. Actual authentication, URL authorization, durable API mutations, server pagination, date/source filtering and persistence are specified in PLAN.md, not implemented here. Some controls show local explanatory confirmations. Person/analysis examples reuse representative fixtures rather than a complete dataset. Review events, sync results and meaningful objective examples are illustrative; they must not be mistaken for changes to the captured accounts.

The protocol/permission design remains authoritative when a concept simplifies a workflow: a long objective or role editor becomes a dedicated URL-backed workspace, even if its short demonstration opens a dialog. No missing backend endpoint is concealed as completed work.
