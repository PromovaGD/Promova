import { fetchEmployeeAnalyses, fetchUserAnalyses, saveUserAnalysis } from "./auth-api.mjs";

export async function loadAnalysesForCurrentUser(filters = {}) {
  const items = await fetchUserAnalyses(buildDateParams(filters));
  return items.map(normalizeSavedAnalysis);
}

export async function loadAnalysesForEmployee(userId, filters = {}) {
  const items = await fetchEmployeeAnalyses(userId, buildDateParams(filters));
  return items.map(normalizeSavedAnalysis);
}

export async function persistAnalysis(analysis) {
  await saveUserAnalysis({
    externalId: analysis.id,
    source: analysis.source,
    sourceMeta: analysis.sourceMeta,
    evidence: analysis.evidence,
    currentLevel: analysis.currentLevel,
    targetLevel: analysis.targetLevel,
    impactLevel: analysis.impactLevel,
    confidence: analysis.confidence,
    justification: analysis.justification,
    readiness: analysis.readiness,
    createdAt: analysis.createdAt,
    competencies: analysis.competencies,
    suggestions: analysis.suggestions,
  });
}

function normalizeSavedAnalysis(item) {
  return {
    id: item.id,
    source: item.source,
    sourceMeta: item.sourceMeta,
    evidence: item.evidence,
    currentLevel: item.currentLevel,
    targetLevel: item.targetLevel,
    impactLevel: item.impactLevel,
    confidence: item.confidence,
    justification: item.justification,
    competencies: item.competencies || [],
    suggestions: item.suggestions || [],
    readiness: item.readiness,
    createdAt: item.createdAt,
  };
}

function buildDateParams(filters) {
  const params = {};

  if (filters.dateFrom) {
    params.from = startOfDayIso(filters.dateFrom);
  }

  if (filters.dateTo) {
    params.to = endOfDayIso(filters.dateTo);
  }

  return params;
}

function startOfDayIso(dateValue) {
  const date = new Date(`${dateValue}T00:00:00`);
  return date.toISOString();
}

function endOfDayIso(dateValue) {
  const date = new Date(`${dateValue}T23:59:59.999`);
  return date.toISOString();
}
