import assert from "node:assert/strict";
import test from "node:test";

import { dashboardContent } from "../views/dashboard-view.mjs";

const hero = {
  title: "Suas evidências",
  subtitle: "Painel de análises",
  copy: "Resumo",
};

function stateFor(tab) {
  return {
    dashboardTab: tab,
    dashboardFilters: {},
    evidences: [],
    pendingEvidences: [],
    pendingStatus: "ready",
    viewingAsAdmin: false,
    insightsStatus: "ready",
    insightsError: null,
    insights: {
      totalEvidence: 2,
      criteriaWithEvidence: 1,
      criteriaCount: 2,
      sourceDistribution: [{ label: "GitHub", count: 2, percentage: 100 }],
      estimatedLevelDistribution: [{ label: "L4", count: 2, percentage: 100 }],
      criterionCoverage: [
        {
          level: "L4",
          levelTitle: "Pleno",
          criterion: "Entrega",
          description: "Entrega com autonomia",
          status: "SUPPORTED",
          evidenceCount: 1,
          supportingEvidence: [],
        },
      ],
      recentTrend: [{ label: "L4", count: 2, percentage: 100 }],
      gaps: [{ level: "L4", criterion: "Mentoria" }],
    },
    githubImport: {
      repoSlug: "",
      authorLogin: "",
      pullNumber: "",
      pullRequests: [],
      settingsStatus: "ready",
      settingsSaving: false,
      testStatus: "idle",
      testMessage: "",
      syncStatus: "idle",
      syncError: "",
      syncResult: null,
      status: "idle",
      error: "",
      lastSyncAt: null,
      lastSyncOutcome: "",
    },
  };
}

test("dashboard tabs render one focused panel at a time", () => {
  const dashboard = dashboardContent(stateFor("dashboard"), hero);
  assert.match(dashboard, /dashboard-panel-dashboard/);
  assert.match(dashboard, /id="recent-evidence"/);
  assert.match(dashboard, /Evid&ecirc;ncias recentes/);
  assert.ok(dashboard.indexOf('id="recent-evidence"') < dashboard.indexOf('id="insights-title"'));
  assert.match(dashboard, /Fontes das evid&ecirc;ncias/);
  assert.doesNotMatch(dashboard, /id="criterion-coverage-title"/);
  assert.doesNotMatch(dashboard, /github-connection-title/);

  const framework = dashboardContent(stateFor("framework"), hero);
  assert.match(framework, /dashboard-panel-framework/);
  assert.match(framework, /id="criterion-coverage-title"/);
  assert.match(framework, /criterion-level-groups/);
  assert.match(framework, /criterion-level-group/);
  assert.match(framework, /aria-controls="criterion-level-l4-panel"/);
  assert.doesNotMatch(framework, /Fontes das evid&ecirc;ncias/);
  assert.doesNotMatch(framework, /github-connection-title/);

  const criteria = dashboardContent(stateFor("criteria"), hero);
  assert.match(criteria, /dashboard-panel-criteria/);
  assert.match(criteria, /Crit&eacute;rios sem evid&ecirc;ncia de apoio/);
  assert.doesNotMatch(criteria, /id="criterion-coverage-title"/);

  const connections = dashboardContent(stateFor("connections"), hero);
  assert.match(connections, /dashboard-panel-connections/);
  assert.match(connections, /GitHub agora fica no seu Perfil/);
  assert.match(connections, /data-action="open-profile"/);
  assert.doesNotMatch(connections, /id="github-connection-title"/);
  assert.doesNotMatch(connections, /data-action="apply-filters"/);
  assert.doesNotMatch(connections, /Fontes das evid&ecirc;ncias/);
});

test("unknown dashboard tab falls back to the main dashboard", () => {
  const html = dashboardContent(stateFor("unknown"), hero);
  assert.match(html, /dashboard-tab active[^>]*id="dashboard-tab-dashboard"/);
  assert.match(html, /dashboard-panel-dashboard/);
});

test("dashboard tabs expose accessible roving-tab semantics", () => {
  const html = dashboardContent(stateFor("criteria"), hero);

  assert.match(html, /role="tablist"/);
  assert.equal((html.match(/role="tab"/g) || []).length, 4);
  assert.equal((html.match(/role="tabpanel"/g) || []).length, 1);

  for (const id of ["dashboard", "framework", "criteria", "connections"]) {
    assert.match(html, new RegExp(`id="dashboard-tab-${id}"`));
    assert.match(html, new RegExp(`aria-controls="dashboard-panel-${id}"`));
    assert.match(
      html,
      new RegExp(`id="dashboard-tab-${id}"[\\s\\S]*?tabindex="${id === "criteria" ? "0" : "-1"}"`),
    );
  }

  assert.match(html, /id="dashboard-tab-criteria"[\s\S]*?aria-selected="true"/);
  assert.match(html, /id="dashboard-panel-criteria"[\s\S]*?aria-labelledby="dashboard-tab-criteria"/);
});

test("evidence cards expose one escaped, accessible expanded item", () => {
  const state = stateFor("dashboard");
  state.expandedEvidenceId = "7";
  state.pendingEvidences = [
    {
      id: 7,
      source: "GitHub",
      sourceMeta: "PR <script>alert(1)</script>",
      content: "Full <img src=x onerror=alert(1)> content",
      sourceUrl: "javascript:alert(1)",
      occurredAt: "2026-09-01T10:00:00Z",
    },
    {
      id: 8,
      source: "GitHub",
      sourceMeta: "PR #8",
      content: "Second item",
      occurredAt: "2026-09-02T10:00:00Z",
    },
  ];

  const html = dashboardContent(state, hero);
  assert.equal((html.match(/aria-expanded="true"/g) || []).length, 1);
  assert.match(html, /aria-controls="pending-evidence-7"/);
  assert.match(html, /id="pending-evidence-7"/);
  assert.match(html, /&lt;img src=x onerror=alert\(1\)&gt;/);
  assert.doesNotMatch(html, /<script>|<img src=x/);
  assert.doesNotMatch(html, /href="javascript:/);
});
