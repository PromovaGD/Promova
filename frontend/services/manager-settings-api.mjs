import { apiGet, apiPost, apiPut } from "./http.mjs";

export function fetchCareerConfiguration() {
  return apiGet("/career-configuration", null, { auth: true });
}

export function fetchManagerSettings() {
  return apiGet("/manager/settings", null, { auth: true });
}

export function fetchJobRoles() {
  return apiGet("/manager/settings/job-roles", { includeArchived: true }, { auth: true });
}

export function updateTerminology(payload) {
  return apiPut("/manager/settings/terminology", payload, { auth: true });
}

export function createJobRole(payload) {
  return apiPost("/manager/settings/job-roles", payload, { auth: true });
}

export function updateJobRole(roleId, payload) {
  return apiPut(`/manager/settings/job-roles/${encodeURIComponent(roleId)}`, payload, {
    auth: true,
  });
}

export function archiveJobRole(roleId, replacementRoleId = null) {
  return apiPost(
    `/manager/settings/job-roles/${encodeURIComponent(roleId)}/archive`,
    replacementRoleId ? { replacementRoleId: Number(replacementRoleId) } : {},
    { auth: true },
  );
}
