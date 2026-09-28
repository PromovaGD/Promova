import assert from "node:assert/strict";
import test from "node:test";
import { createDemoSummary, renderManagerSummary } from "../manager-summary.mjs";

const ui = {
  esc: value => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll('"', "&quot;"),
  sel: (a, b) => String(a ?? "") === String(b ?? "") ? "selected" : "",
  date: value => value,
  dateTime: value => value,
  labelStatus: value => value,
};

test("demo summary preserves period totals and visibly identifies illustrative data", () => {
  const summary = createDemoSummary({ period: "7" });
  assert.equal(summary.trend.reduce((total, point) => total + point.evidence, 0), summary.evidenceCount);
  assert.equal(summary.trend.reduce((total, point) => total + point.analyses, 0), summary.analysisCount);
  assert.equal(summary.statuses.reduce((total, item) => total + item.count, 0), summary.analysisCount);
  const html = renderManagerSummary([summary, [], [], []], { period: "7", preview: "demo" }, ui);
  assert.match(html, /Prévia fictícia/);
  assert.match(html, /Últimos 7 dias/);
  assert.doesNotMatch(html, /Marina Costa|Rafael Lima|Pessoa não informada/);
  assert.doesNotMatch(html, /href="\/workspace\/people\//);
});

test("real highlights link to analyses and escape external titles", () => {
  const summary = createDemoSummary({ period: "30" });
  summary.highlights = [{ ...summary.highlights[0], title: '<script>alert(1)</script>' }];
  const html = renderManagerSummary([summary, [], [], []], { period: "30" }, ui);
  assert.match(html, /href="\/workspace\/people\/1\/analyses\/1"/);
  assert.doesNotMatch(html, /<script>/);
});
