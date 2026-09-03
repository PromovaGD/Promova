import { fetchEmployeeAnalyses, fetchUserAnalyses } from "./auth-api.mjs";
import { apiGet, apiPost } from "./http.mjs";

export async function loadAnalysesForCurrentUser(filters = {}) {
  const items = await fetchUserAnalyses(buildDateParams(filters));
  return items.map(normalizeSavedAnalysis);
}

export async function loadAnalysesForEmployee(userId, filters = {}) {
  const items = await fetchEmployeeAnalyses(userId, buildDateParams(filters));
  return items.map(normalizeSavedAnalysis);
}

export async function loadReviewsForCurrentUser(analysisId) {
  return normalizeReview(
    await apiGet(`/analyses/${encodeURIComponent(analysisId)}/reviews`, null, { auth: true }),
  );
}

export async function loadReviewsForEmployee(employeeId, analysisId) {
  return normalizeReview(
    await apiGet(
      `/manager/employees/${encodeURIComponent(employeeId)}/analyses/${encodeURIComponent(analysisId)}/reviews`,
      null,
      { auth: true },
    ),
  );
}

export async function submitReviewForEmployee(employeeId, analysisId, payload) {
  return normalizeReview(
    await apiPost(
      `/manager/employees/${encodeURIComponent(employeeId)}/analyses/${encodeURIComponent(analysisId)}/reviews`,
      payload,
      { auth: true },
    ),
  );
}

function normalizeSavedAnalysis(item) {
  return {
    id: item.id,
    analysisId: item.analysisId ?? null,
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

function normalizeReview(item) {
  return {
    analysisId: item?.analysisId ?? null,
    currentStatus: item?.currentStatus || "UNREVIEWED",
    history: Array.isArray(item?.history)
      ? item.history.map((review) => ({
          id: review.id,
          reviewerId: review.reviewerId,
          reviewerName: review.reviewerName,
          reviewerEmail: review.reviewerEmail,
          createdAt: review.createdAt,
          status: review.status,
          comment: review.comment,
        }))
      : [],
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
