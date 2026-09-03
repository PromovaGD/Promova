import assert from "node:assert/strict";
import test from "node:test";

import {
  evidenceDetailPage,
  evidenceEmptyPage,
  evidenceErrorPage,
} from "../views/evidence-view.mjs";

const savedEvidence = {
  id: 42,
  source: "GitHub",
  sourceMeta: "promova/app#42",
  evidence: "Entregou uma melhoria relevante",
  currentLevel: "L3",
  targetLevel: "L4",
  impactLevel: "L4",
  confidence: "high",
  justification: "A mudança foi entregue com autonomia.",
  competencies: ["Entrega"],
  suggestions: [],
  readiness: "Pronto para conversar sobre o impacto.",
  createdAt: "2026-09-02T12:00:00.000Z",
};

test("empty evidence flow explains that the inbox is empty and offers next actions", () => {
  const html = evidenceEmptyPage({ user: { name: "João Silva", role: "EMPLOYEE" } });

  assert.match(html, /Não há novas evidências para revisar/);
  assert.match(html, /data-action="back-dashboard"/);
  assert.match(html, /data-action="open-connections"/);
  assert.doesNotMatch(html, /Erro inesperado/);
});

test("evidence API failures remain actionable error states", () => {
  const html = evidenceErrorPage(
    { user: { name: "João Silva", role: "EMPLOYEE" } },
    "Request failed: 503",
  );

  assert.match(html, /Request failed: 503/);
  assert.match(html, /data-action="reload-pending"/);
  assert.match(html, /Tentar novamente/);
});

test("employees get review visibility without administrative instructions", () => {
  const html = evidenceDetailPage({
    user: { name: "João Silva", role: "EMPLOYEE" },
    viewingAsAdmin: false,
    evidences: [savedEvidence],
    adminEvidences: [],
    selectedEvidenceId: savedEvidence.id,
    review: { currentStatus: "UNREVIEWED", history: [] },
    reviewStatus: "ready",
    reviewSaving: false,
    reviewError: null,
  });

  assert.match(html, /Acompanhamento da revisão/);
  assert.match(html, /Não revisada/);
  assert.doesNotMatch(html, /Revisão administrativa/);
  assert.doesNotMatch(html, /Apenas administradores podem registrar/);
  assert.doesNotMatch(html, /data-review-form/);
});

test("admins retain the review history and review actions", () => {
  const html = evidenceDetailPage({
    user: { name: "Admin", role: "ADMIN" },
    viewingAsAdmin: true,
    evidences: [],
    adminEvidences: [savedEvidence],
    selectedEvidenceId: savedEvidence.id,
    review: {
      currentStatus: "ACCEPTED",
      history: [
        {
          status: "ACCEPTED",
          reviewerName: "Admin",
          createdAt: "2026-09-02T12:30:00.000Z",
          comment: "Evidência conferida.",
        },
      ],
    },
    reviewStatus: "ready",
    reviewSaving: false,
    reviewError: null,
  });

  assert.match(html, /Revisão administrativa/);
  assert.match(html, /review-form/);
  assert.match(html, /Aceitar an&aacute;lise/);
  assert.match(html, /Evidência conferida/);
});
