import { apiDelete, apiGet, apiPost, apiPut } from "./http.mjs";

export const api = {
  evidences: (params, signal) => apiGet("/evidences", { ...dateParams(params), status: params?.status, source: params?.source, page: params?.page || 1, pageSize: 25 }, { signal }),
  evidence: (ownerId, id, manager, signal) => apiGet(manager ? `/manager/employees/${ownerId}/evidences/${id}` : `/evidences/${id}`, null, { signal }),
  dismissEvidence: (id) => reconcileEvidenceMutation(id, () => apiPost(`/evidences/${id}/dismiss`, {}), evidence => evidence.status === "DISMISSED"),
  analyzeEvidence: (id, userObservation) => reconcileEvidenceMutation(id, () => apiPost(`/evidences/${id}/analysis`, { userObservation: userObservation?.trim() || null }), evidence => evidence.analysisId ? { analysisId: evidence.analysisId } : null),
  analyses: (params, signal) => apiGet("/analyses", { ...dateParams(params), source: params?.source, page: params?.page || 1, pageSize: 25 }, { signal }),
  analysis: (ownerId, id, manager, signal) => apiGet(manager ? `/manager/employees/${ownerId}/analyses/${id}` : `/analyses/${id}`, null, { signal }),
  reviews: (ownerId, id, manager, signal) => apiGet(manager ? `/manager/employees/${ownerId}/analyses/${id}/reviews` : `/analyses/${id}/reviews`, null, { signal }),
  review: (ownerId, id, body) => apiPost(`/manager/employees/${ownerId}/analyses/${id}/reviews`, { ...body, idempotencyKey: reviewKey(ownerId, id, body) }),
  clearAnalyses: (params) => apiDelete("/analyses", dateParams(params)),
  insights: (params, signal) => apiGet("/insights", dateParams(params), { signal }),
  framework: (manager, signal) => apiGet(manager ? "/manager/settings/framework" : "/career-configuration/framework", null, { signal }),
  profile: (signal) => apiGet("/profile", null, { signal }),
  employees: (params, signal) => apiGet("/manager/employees", { query: params?.q, jobRoleId: params?.role, level: params?.level, page: params?.page || 1, pageSize: 25 }, { signal }),
  employee: (id, signal) => apiGet(`/manager/employees/${id}`, null, { signal }),
  employeePlan: (id, signal) => apiGet(`/manager/employees/${id}/career-plan`, null, { signal }),
  updatePlan: (id, body) => apiPut(`/manager/employees/${id}/career-plan`, body),
  createObjective: (id, body) => apiPost(`/manager/employees/${id}/career-plan/objectives`, body),
  updateObjective: (id, objectiveId, body) => apiPut(`/manager/employees/${id}/career-plan/objectives/${objectiveId}`, body),
  employeeEvidences: (id, params, signal) => apiGet(`/manager/employees/${id}/evidences`, { ...dateParams(params), status: params?.status, source: params?.source, page: params?.page || 1, pageSize: 25 }, { signal }),
  employeeAnalyses: (id, params, signal) => apiGet(`/manager/employees/${id}/analyses`, { ...dateParams(params), source: params?.source, review: params?.review, page: params?.page || 1, pageSize: 25 }, { signal }),
  reviewQueue: (params, signal) => apiGet("/manager/reviews", { ...dateParams(params), status: params?.status, employee: params?.employee, source: params?.source, page: params?.page || 1, pageSize: 25 }, { signal }),
  settings: (signal) => apiGet("/manager/settings", null, { signal }),
  roles: (signal) => apiGet("/manager/settings/job-roles", { includeArchived: true }, { signal }),
  terminology: (body) => apiPut("/manager/settings/terminology", body),
  createRole: (body) => apiPost("/manager/settings/job-roles", body),
  updateRole: (id, body) => apiPut(`/manager/settings/job-roles/${id}`, body),
  archiveRole: (id, replacementRoleId) => apiPost(`/manager/settings/job-roles/${id}/archive`, replacementRoleId ? { replacementRoleId } : {}),
  githubSettings: (signal) => apiGet("/api/github/settings", null, { signal }),
  saveGithub: (body) => apiPut("/api/github/settings", body),
  testGithub: () => apiPost("/api/github/settings/test", {}),
  syncGithub: () => apiPost("/api/github/sync", {}),
  clearGithub: () => apiDelete("/api/github/settings"),
  searchGithub: (owner, repo, query, page, signal) => apiGet(`/api/github/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/pulls/search`, { q: query, page, per_page: 8 }, { signal }),
  captureGithub: (body) => apiPost("/evidences/github/pull-request", body),
};

function dateParams(params = {}) {
  const result = {};
  if (params.from) result.from = new Date(`${params.from}T00:00:00`).toISOString();
  if (params.to) result.to = new Date(`${params.to}T23:59:59.999`).toISOString();
  return result;
}

async function reconcileEvidenceMutation(id, mutation, isComplete) {
  try {
    return await mutation();
  } catch (originalError) {
    try {
      const current = await apiGet(`/evidences/${id}`);
      const reconciled = isComplete(current);
      if (reconciled) return reconciled === true ? current : reconciled;
    } catch {
      // Preserve the mutation failure when reconciliation is also unavailable.
    }
    throw originalError;
  }
}

function reviewKey(ownerId, analysisId, body) {
  const input = `${ownerId}|${analysisId}|${body.expectedLatestReviewId || 0}|${body.status}|${body.comment || ""}`;
  let hash = 2166136261;
  for (const character of input) {
    hash ^= character.codePointAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return `review-${ownerId}-${analysisId}-${body.expectedLatestReviewId || 0}-${(hash >>> 0).toString(16)}`;
}
