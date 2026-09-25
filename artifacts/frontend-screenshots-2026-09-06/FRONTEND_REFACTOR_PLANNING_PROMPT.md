# Task: Plan a production-ready modernization of the Promova front end

You are working in the Promova repository at:

`/Users/joao.rihan/IdeaProjects/Promova`

Your job is to inspect the entire current front end and produce an implementation-ready refactoring plan plus visual wireframes. Do not implement the refactor in this task.

## Product direction

The current interface frequently places multiple features and large sections in one long page. Users must scroll vertically to discover or reach distinct functionality. This is the main problem to solve.

Design a modern application system in which:

- one screen presents one primary feature, task, or view;
- users switch features through explicit navigation rather than by scrolling through unrelated sections;
- authenticated desktop screens use a persistent application shell, with a compact sidebar or hamburger-triggered navigation and clear location/context;
- primary navigation, contextual navigation, and actions have distinct roles;
- navigation works through real routes or durable view state, including refresh, back/forward, deep links, and permission-aware destinations;
- the viewport remains stable wherever practical, with headers/navigation fixed and only intentional, bounded content regions scrolling when the data itself is long;
- wide content does not force page-level horizontal scrolling;
- mobile and small-screen layouts use a drawer or similarly compact navigation model;
- the result feels cohesive, polished, accessible, responsive, and appropriate for a production product.

Do not interpret “no scrolling” literally in a way that hides content or harms accessibility. Long tables, lists, evidence text, and forms may need scrolling inside a clearly bounded workspace. The important rule is that unrelated features must not be stacked into a long document. Treat the public marketing landing page separately from the authenticated product shell; if you recommend that it remain a conventional scrolling page, explain why and show how it still fits the design system.

## Required evidence

Start by reading:

- `/Users/joao.rihan/IdeaProjects/Promova/artifacts/frontend-screenshots-2026-09-06/README.md`
- `/Users/joao.rihan/IdeaProjects/Promova/artifacts/frontend-screenshots-2026-09-06/00-contact-sheet.png`
- every numbered full-page PNG in that folder, from `01-...png` through `20-...png`

Then inspect the current front-end source, including:

- `/Users/joao.rihan/IdeaProjects/Promova/index.html`
- `/Users/joao.rihan/IdeaProjects/Promova/styles.css`
- `/Users/joao.rihan/IdeaProjects/Promova/frontend/app.mjs`
- all files under `/Users/joao.rihan/IdeaProjects/Promova/frontend/components`
- all files under `/Users/joao.rihan/IdeaProjects/Promova/frontend/views`
- relevant services, tests, and configuration under `/Users/joao.rihan/IdeaProjects/Promova/frontend`

Use the screenshots as the visual truth of the current experience and the source as the behavioral truth. Account for employee and manager roles, route persistence, deep links, permissions, authentication, pending evidence, saved analyses, review history/actions, career plans, GitHub connectivity, organization terminology, job roles, and framework criteria.

## Audit every captured screen

Create a screen-by-screen audit covering all 20 numbered screenshots. For each screen, record:

- current purpose and user role;
- primary user task;
- content or features that are incorrectly stacked together;
- navigation and hierarchy problems;
- excessive vertical or horizontal growth;
- actions that should remain visible without page-level scrolling;
- proposed destination in the new information architecture;
- whether the screen is retained, split, merged, converted into a panel/detail route, or removed;
- important loading, empty, error, success, disabled, and permission states.

Do not skip a screenshot because it resembles another one.

## Define the new page system

Produce a concrete information architecture and navigation model. Include:

1. A route map for public, authentication, employee, manager, and shared detail screens.
2. A persistent application-shell specification: sidebar/drawer, top bar, breadcrumbs or contextual title, account controls, primary action placement, and content viewport behavior.
3. A distinction between global navigation, section navigation, tabs, filters, master-detail layouts, drawers, and modals, with rules for when each pattern is appropriate.
4. A desktop and mobile navigation model.
5. A mapping from every current screen and feature to its new route, tab, panel, or overlay.
6. A strategy for long datasets and long evidence content using pagination, virtualized or bounded lists, sticky controls, expandable detail regions, or dedicated detail routes as appropriate.
7. URL, browser history, refresh, deep-link, and role-authorization behavior.

At minimum, evaluate a structure along these lines, but change it when the evidence supports a better design:

- Employee: Overview, Inbox, Analyses, Framework, Career Plan, Integrations.
- Manager: People, employee detail workspace, Reviews, Career Configuration, Integrations/settings where appropriate.
- Shared: focused evidence and analysis detail routes.

## Create actual visual concepts

Create low- or medium-fidelity wireframes rather than only describing them in prose. Use a code-native format such as HTML/CSS, SVG, or the available visualization tooling so the artifacts are easy to inspect and revise.

Show at least:

- desktop employee application shell;
- desktop manager application shell;
- mobile navigation drawer and one representative mobile screen;
- employee overview;
- evidence inbox with bounded list and focused detail behavior;
- saved analysis detail with review history;
- career plan workspace;
- manager people master-detail workspace;
- manager review/detail workflow;
- career configuration workspace;
- a representative loading, empty, and error state.

The wireframes must demonstrate viewport containment, navigation transitions, sticky or persistent controls, and where scrolling occurs. Label the regions and interactions clearly. Create a contact sheet or index so another person can review all concepts quickly.

Do not merely recolor the current screens. Recompose the information hierarchy and interaction model.

## Production-ready design specification

Define enough detail for a separate implementation agent to build the system without inventing major UX decisions:

- layout grid, shell dimensions, content width, spacing, density, and responsive breakpoints;
- navigation states, active states, focus states, keyboard behavior, drawer behavior, and escape/back behavior;
- typography, color, elevation, borders, radii, icon usage, and design-token recommendations;
- reusable component inventory and ownership boundaries;
- page templates and composition rules;
- tables/lists, filters, forms, validation, destructive actions, confirmation patterns, toasts, dialogs, and status feedback;
- accessibility requirements aligned with WCAG 2.2 AA, including focus order, landmarks, headings, reduced motion, contrast, zoom/reflow, and screen-reader naming;
- responsive behavior for common desktop, tablet, and mobile widths;
- localization resilience for Portuguese labels and customizable organization terminology;
- performance considerations, including route-level loading, rendering of long lists, and avoiding layout shifts;
- analytics or usability signals that would validate whether the redesign reduces navigation effort and page-level scrolling.

Preserve Promova’s recognizable brand where it works, but identify visual elements that should be simplified or standardized. Explain the recommended aesthetic direction with concrete examples instead of subjective words alone.

## Architecture and migration plan

Inspect the current JavaScript architecture and recommend a target structure. Decide whether the existing lightweight architecture can support the redesign or whether adopting a framework/router is justified. Compare realistic options against this repository’s size and needs, then make one recommendation.

Specify:

- proposed folders/modules and component boundaries;
- routing and state-management approach;
- separation of server data, navigation state, form state, and transient UI state;
- API compatibility and any backend dependencies or gaps;
- migration phases that keep the product runnable;
- exact files or areas affected in each phase;
- testing strategy for routes, roles, responsive layouts, keyboard navigation, and visual regressions;
- rollout and rollback approach;
- risks, dependencies, assumptions, and unresolved product decisions.

Break implementation into small, ordered work packages. For each package include scope, prerequisites, expected files/components, acceptance criteria, and verification steps. Identify which work packages can be parallelized safely.

## Deliverables

Create a new directory:

`/Users/joao.rihan/IdeaProjects/Promova/docs/frontend-modernization`

Place these deliverables there:

1. `README.md` — executive summary, recommendation, artifact index, and the proposed user experience in plain language.
2. `screen-audit.md` — explicit audit of all 20 screenshots.
3. `information-architecture.md` — route map, navigation model, current-to-future screen mapping, and interaction rules.
4. `design-system.md` — production-ready shell, layout, responsive, component, state, and accessibility specification.
5. `technical-architecture.md` — target front-end architecture and decision rationale.
6. `implementation-plan.md` — phased, ordered work packages with acceptance criteria and verification.
7. `wireframes/` — source artifacts plus rendered PNGs for all required concepts and a visual index/contact sheet.

Use diagrams and tables when they clarify routes, mappings, hierarchy, or phased dependencies. Link every artifact from the new `README.md`.

## Boundaries

- This is a research, UX architecture, visual concept, and implementation-planning task.
- Do not modify production front-end or backend code.
- Do not submit forms or trigger state-changing actions in the running app.
- Do not erase or replace the existing screenshot archive.
- You may add only the planning and wireframe artifacts under `docs/frontend-modernization`.
- Base conclusions on the screenshots and repository evidence. Mark assumptions explicitly.
- Make reasonable assumptions and continue; ask a question only if the answer would materially change the entire architecture.

## Completion criteria

Do not stop at general recommendations. The task is complete only when:

- all 20 screenshots have an explicit audit entry;
- every current feature has a destination in the proposed system;
- desktop and mobile navigation are specified;
- the “one primary feature per screen” principle is visible in the wireframes;
- page-level scrolling and overflow rules are unambiguous;
- wireframes exist as reviewable rendered images and editable sources;
- the technical recommendation includes a reasoned architecture decision;
- the implementation plan is sufficiently detailed for another agent to execute phase by phase;
- all deliverables are linked from `docs/frontend-modernization/README.md`;
- you perform a final consistency pass across the audit, route map, wireframes, design system, and implementation plan.

In your final response, summarize the recommended direction, list the created artifacts with links, call out the highest-risk assumptions, and confirm that no application code was changed.
