import { createId, readinessFor } from "../utils/format.mjs";
import { apiPost } from "./http.mjs";

export async function analyzeCapturedEvidence(capturedEvidence) {
  const apiResponse = await apiPost("/analyze", {
    evidence: capturedEvidence.evidence,
    currentLevel: capturedEvidence.currentLevel,
    targetLevel: capturedEvidence.targetLevel,
  });

  return normalizeAnalysisResponse(apiResponse, capturedEvidence);
}

function normalizeAnalysisResponse(apiResponse, capturedEvidence) {
  const impactLevel = apiResponse.estimatedLevel || capturedEvidence.currentLevel;

  return {
    id: createId(),
    source: capturedEvidence.source,
    sourceMeta: capturedEvidence.sourceMeta,
    impactLevel,
    confidence: apiResponse.confidence || "medium",
    justification: apiResponse.reasoning || "Análise concluída pelo backend.",
    competencies: Array.isArray(apiResponse.competencies) ? apiResponse.competencies : [],
    suggestions: Array.isArray(apiResponse.suggestions) ? apiResponse.suggestions : [],
    readiness: readinessFor(impactLevel, capturedEvidence.targetLevel),
    currentLevel: capturedEvidence.currentLevel,
    targetLevel: capturedEvidence.targetLevel,
    evidence: capturedEvidence.evidence,
    createdAt: new Date().toISOString(),
  };
}
