# Modern UI rollout and rollback

The modern native-module UI is selected once, before either router starts. Additive database and API changes are compatible with both UIs.

## Build selection

- Modern UI (default): `npm run build`
- Legacy rollback: `PROMOVA_MODERN_UI=false npm run build`

The build writes the selected value into `dist/promova-config.js`. A running browser session therefore uses one router only. Changing the flag requires rebuilding and replacing the static frontend files; it does not change or delete application data.

## Rollout sequence

1. Apply backend migration V8 and deploy the additive API contracts.
2. Verify legacy clients against those contracts.
3. Build the frontend with the modern UI enabled and pilot both employee and manager roles.
4. Monitor route-load status, not-found responses, API status classes, and duplicate mutation outcomes by route ID. Do not log evidence text, comments, access tokens, or email filter values.
5. Retain legacy path redirects for one documented deprecation window after the pilot.

## Rollback

Build with `PROMOVA_MODERN_UI=false` and replace only the static frontend artifact. Modern paths are normalized to the nearest legacy workspace before the legacy router starts. Do not roll back V8: its nullable column and indexes are safe for the old application, while rolling it back could remove idempotency metadata written during the pilot.

After rollback, validate `/dashboard`, `/profile`, `/manager`, login/logout, evidence analysis, review history, career plan updates, and GitHub capture. Canonical modern resource links fall back to the role-appropriate legacy workspace because the legacy UI has no durable detail route.
