# Frontend rollout and rollback

Promova has one native-module frontend implementation. `frontend/app.mjs`, `styles.css`, and
`frontend/services/api.mjs` are the production sources; there is no runtime or build-time UI selector.

## Rollout sequence

1. Apply backend migration V8 and deploy the additive API contracts.
2. Run `npm run check`, `npm run test:e2e`, the backend tests, and `npm run smoke:ci`.
3. Build the static artifact with `npm run build` and deploy `dist/` together as one immutable version.
4. Verify login, both role homes, direct nested routes, evidence analysis, review history, career-plan
   mutations, GitHub capture, and production-server HTML fallback.
5. Monitor route-load status, not-found responses, API status classes, and duplicate mutation outcomes
   by route ID. Do not log evidence text, comments, access tokens, or email filter values.

## Rollback

Redeploy the previously verified immutable frontend artifact or revert the frontend commit and rebuild.
Do not roll back V8: its nullable column and indexes remain backward-compatible, while reversing it could
remove idempotency metadata written by the application.

After rollback, validate `/app/overview`, `/app/inbox`, `/manage/people`, login/logout, direct resource
routes, evidence analysis, review history, career-plan updates, and GitHub capture.
