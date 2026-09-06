import { apiGet, apiPost, apiPut } from "./http.mjs";

function planPath(employeeId) {
  return `/manager/employees/${encodeURIComponent(employeeId)}/career-plan`;
}

export function fetchEmployeeCareerPlan(employeeId) {
  return apiGet(planPath(employeeId), null, { auth: true });
}

export function updateEmployeeCareerPlan(employeeId, payload) {
  return apiPut(planPath(employeeId), payload, { auth: true });
}

export function createEmployeeObjective(employeeId, payload) {
  return apiPost(`${planPath(employeeId)}/objectives`, payload, { auth: true });
}

export function updateEmployeeObjective(employeeId, objectiveId, payload) {
  return apiPut(
    `${planPath(employeeId)}/objectives/${encodeURIComponent(objectiveId)}`,
    payload,
    { auth: true },
  );
}
