import assert from "node:assert/strict";
import test from "node:test";
import { canAccess, canonicalUrl, matchRoute, roleHome, safeReturnTo } from "../router.mjs";

test("manager home, dashboard filters and team route are role scoped", () => {
  assert.equal(roleHome({ role: "MANAGER" }), "/manage/dashboard");
  assert.equal(matchRoute("/manage/teams?team=3").id, "manager-teams");
  const dashboard = matchRoute("/manage/dashboard?period=90&team=3&member=7&page=2&extra=no");
  assert.equal(dashboard.id, "manager-dashboard");
  assert.deepEqual(dashboard.query, { period: "90", team: 3, member: 7, page: 2 });
  assert.equal(canonicalUrl(dashboard), "/manage/dashboard?period=90&team=3&member=7&page=2");
  assert.equal(canAccess(dashboard, { id: 9, role: "EMPLOYEE" }), false);
  assert.equal(canAccess(dashboard, { id: 1, role: "MANAGER" }), true);
});

test("executive summary route keeps its period, scope and explicit demo preview", () => {
  const summary = matchRoute("/manage/summaries?period=7&team=3&preview=demo&extra=secret");
  assert.equal(summary.id, "manager-summary");
  assert.deepEqual(summary.query, { period: "7", team: 3, preview: "demo" });
  assert.equal(canAccess(summary, { id: 4, role: "EMPLOYEE" }), false);
  assert.equal(canAccess(summary, { id: 1, role: "MANAGER" }), true);
});

test("all canonical nested resources match stable numeric identifiers", () => {
  const evidence = matchRoute("https://promova.test/workspace/people/22/evidence/91");
  assert.equal(evidence.id, "evidence-detail");
  assert.deepEqual(evidence.params, { ownerId: 22, evidenceId: 91 });
  assert.equal(matchRoute("https://promova.test/workspace/people/no/evidence/91").id, "not-found");
});

test("query normalization keeps supported values and drops unsafe or malformed values", () => {
  const route = matchRoute("https://promova.test/app/inbox?status=pending&page=2&from=2026-09-01&selected=9&extra=secret");
  assert.deepEqual(route.query, { status: "pending", page: 2, from: "2026-09-01", selected: 9 });
  assert.equal(canonicalUrl(route), "/app/inbox?status=pending&page=2&from=2026-09-01&selected=9");
  assert.deepEqual(matchRoute("https://promova.test/app/inbox?status=all").query, { status: "all" });
  assert.deepEqual(matchRoute("https://promova.test/app/inbox?status=accepted&q=OAuth&sort=oldest").query, { status: "accepted", q: "OAuth", sort: "oldest" });
  assert.deepEqual(matchRoute("https://promova.test/app/inbox?status=analyzed").query, {});
  assert.deepEqual(
    matchRoute("https://promova.test/app/analyses?from=2026-09-10&to=2026-09-01").query,
    { from: "2026-09-10" },
  );
});

test("return destinations are same-origin protected allowlisted routes", () => {
  assert.equal(safeReturnTo("/app/analyses?page=2"), "/app/analyses?page=2");
  assert.equal(safeReturnTo("//evil.example/app/overview"), null);
  assert.equal(safeReturnTo("javascript:alert(1)"), null);
  assert.equal(safeReturnTo("/login"), null);
  assert.equal(safeReturnTo("/not-a-route"), null);
});

test("resource ownership and role destinations are guarded", () => {
  const resource = matchRoute("https://promova.test/workspace/people/2/analyses/9");
  assert.equal(canAccess(resource, { id: 2, role: "EMPLOYEE" }), true);
  assert.equal(canAccess(resource, { id: 3, role: "EMPLOYEE" }), false);
  assert.equal(canAccess(resource, { id: 1, role: "MANAGER" }), true);
  assert.equal(canAccess(matchRoute("https://promova.test/manage/reviews"), { id: 2, role: "EMPLOYEE" }), false);
});
