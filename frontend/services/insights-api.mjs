import { apiGet } from "./http.mjs";

export async function loadInsightsForCurrentUser(filters = {}) {
  const response = await apiGet("/insights", buildDateParams(filters), { auth: true });
  return normalizeInsights(response);
}

function normalizeInsights(response = {}) {
  return {
    totalEvidence: response.totalEvidence ?? 0,
    criteriaCount: response.criteriaCount ?? 0,
    criteriaWithEvidence: response.criteriaWithEvidence ?? 0,
    sourceDistribution: Array.isArray(response.sourceDistribution)
      ? response.sourceDistribution
      : [],
    estimatedLevelDistribution: Array.isArray(response.estimatedLevelDistribution)
      ? response.estimatedLevelDistribution
      : [],
    criterionCoverage: Array.isArray(response.criterionCoverage) ? response.criterionCoverage : [],
    recentTrend: Array.isArray(response.recentTrend) ? response.recentTrend : [],
    gaps: Array.isArray(response.gaps) ? response.gaps : [],
  };
}

function buildDateParams(filters = {}) {
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
  return new Date(`${dateValue}T00:00:00`).toISOString();
}

function endOfDayIso(dateValue) {
  return new Date(`${dateValue}T23:59:59.999`).toISOString();
}
