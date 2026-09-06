import { apiPost } from "./http.mjs";

export async function analyzeCapturedEvidence(evidenceId, userObservation = "") {
  const apiResponse = await apiPost(
    `/evidences/${encodeURIComponent(evidenceId)}/analysis`,
    { userObservation: String(userObservation || "").trim() || null },
    { auth: true },
  );

  return normalizeAnalysisResponse(apiResponse);
}

function normalizeAnalysisResponse(apiResponse) {
  return {
    id: apiResponse.id,
    analysisId: apiResponse.analysisId ?? null,
    source: apiResponse.source,
    sourceMeta: apiResponse.sourceMeta,
    impactLevel: apiResponse.impactLevel,
    confidence: apiResponse.confidence,
    justification: apiResponse.justification,
    competencies: Array.isArray(apiResponse.competencies) ? apiResponse.competencies : [],
    suggestions: Array.isArray(apiResponse.suggestions) ? apiResponse.suggestions : [],
    readiness: apiResponse.readiness,
    currentLevel: apiResponse.currentLevel,
    targetLevel: apiResponse.targetLevel,
    evidence: apiResponse.evidence,
    userObservation: apiResponse.userObservation || null,
    createdAt: apiResponse.createdAt,
  };
}
