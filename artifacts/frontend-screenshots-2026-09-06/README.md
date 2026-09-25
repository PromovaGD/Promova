# Promova front-end screenshot archive

Captured on 2026-09-06 from the current workspace source in Google Chrome, using a 1440 × 1000 light-mode viewport. Every PNG is a full-page capture. Animations and transitions were disabled to keep the images deterministic.

The capture used the front end at `http://localhost:14173` and the local development API at `http://localhost:8080`. Demo data from João Silva, Maria Santos, and the administrator account was used to cover pending, populated, employee, and manager states. No evidence was analyzed, dismissed, reviewed, edited, or otherwise mutated while capturing.

[Open the visual contact sheet](00-contact-sheet.png)

## Public and authentication

1. [Public landing page](01-public-landing-full.png)
2. [Login mode](02-auth-login.png)
3. [Registration mode](03-auth-register.png)

## Employee experience

4. [Populated dashboard](04-employee-dashboard-populated.png)
5. [Dashboard with saved analysis expanded](05-employee-dashboard-analysis-expanded.png)
6. [Analysis detail and employee review history](06-employee-analysis-detail-review.png)
7. [Framework coverage with every level expanded](07-employee-dashboard-framework-all-levels.png)
8. [Criteria gaps](08-employee-dashboard-criteria.png)
9. [Connections tab](09-employee-dashboard-connections.png)
10. [Career profile and complete GitHub section](10-employee-profile-career-and-github.png)
11. [Pending evidence inbox](11-employee-dashboard-pending-inbox.png)
12. [Pending evidence expanded](12-employee-dashboard-pending-expanded.png)
13. [Pending evidence review before analysis](13-employee-pending-evidence-review.png)

## Manager experience

14. [People directory and João's career plan](14-manager-people-joao-career-plan.png)
15. [João's evidence list](15-manager-joao-evidences.png)
16. [João's evidence list with a record expanded](16-manager-joao-evidence-expanded.png)
17. [Maria's career plan and existing objective](17-manager-maria-career-plan-objective.png)
18. [Maria's saved analyses](18-manager-maria-analyses.png)
19. [Manager analysis detail, review history, and review controls](19-manager-analysis-detail-review-controls.png)
20. [Career settings: terminology, roles, levels, and archive controls](20-manager-career-settings-full.png)

## Regeneration

The capture driver is [capture.mjs](capture.mjs). It expects:

- the current front end on port `14173`;
- a development backend with the richer local demo database on port `8080`;
- `npm ci` completed so `playwright-core` is available;
- Google Chrome at `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`.

Run it from the repository root:

```sh
node artifacts/frontend-screenshots-2026-09-06/capture.mjs
```

The script overwrites PNGs with the same names and does not submit any state-changing UI action.
