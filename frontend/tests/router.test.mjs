import assert from "node:assert/strict";
import test from "node:test";
import { canAccess, canonicalUrl, legacyDestination, matchRoute, safeReturnTo } from "../router.mjs";

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

test("legacy dashboard, settings and hash links redirect predictably", () => {
  assert.equal(legacyDestination("https://promova.test/dashboard?tab=criteria"), "/app/framework?support=missing");
  assert.equal(legacyDestination("https://promova.test/manager?section=settings"), "/manage/career/roles");
  assert.equal(legacyDestination("https://promova.test/manager#/manager/employees/8"), "/manage/people/8/career-plan");
});
