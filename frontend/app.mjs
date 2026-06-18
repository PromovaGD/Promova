import {
  chooseGithubPullRequest,
  createGithubImportState,
  githubImportRequest,
  setGithubImportError,
  setGithubImportIdle,
  setGithubImportLoading,
  setGithubImportResults,
  updateGithubImportField,
} from "./features/github-import/github-import-model.mjs";
import { loadAnalysesForCurrentUser, loadAnalysesForEmployee, persistAnalysis } from "./services/analyses-api.mjs";
import { analyzeCapturedEvidence } from "./services/analysis-api.mjs";
import {
  fetchCurrentUser,
  fetchEmployees,
  loginUser,
  logoutUser,
  registerUser,
  clearUserAnalyses,
} from "./services/auth-api.mjs";
import { clearAuthSession, loadAuthToken, loadAuthUser, saveAuthSession } from "./services/auth-store.mjs";
import { captureEvidenceFromGithubPullRequest, fetchNextCapturedEvidence } from "./services/evidence-api.mjs";
import { findGithubPullRequests } from "./services/github-api.mjs";
import {
  loadEvidenceCursor,
  saveEvidenceCursor,
} from "./services/session-store.mjs";
import { adminPage } from "./views/admin-view.mjs";
import { authPage } from "./views/auth-view.mjs";
import { dashboardPage } from "./views/dashboard-view.mjs";
import {
  evidenceDetailPage,
  evidenceErrorPage,
  evidenceLoadingPage,
  evidenceResultPage,
} from "./views/evidence-view.mjs";
import { landingPage } from "./views/landing-view.mjs";

const state = {
  view: "home",
  cursor: loadEvidenceCursor(),
  pendingEvidence: null,
  pendingStatus: "idle",
  githubImport: createGithubImportState(),
  result: null,
  error: null,
  evidences: [],
  user: loadAuthUser(),
  authMode: "login",
  authLoading: false,
  authError: null,
  dashboardFilters: {},
  adminFilters: {},
  employees: [],
  selectedEmployeeId: null,
  adminEvidences: [],
  selectedEvidenceId: null,
  viewingAsAdmin: false,
};

let appRoot;

export function startApp(root) {
  appRoot = root;
  appRoot.addEventListener("click", handleClick);
  appRoot.addEventListener("input", handleInput);
  appRoot.addEventListener("submit", handleSubmit);
  bootstrapSession();
}

async function bootstrapSession() {
  const token = loadAuthToken();
  if (!token) {
    render();
    return;
  }

  try {
    state.user = await fetchCurrentUser();
    saveAuthSession(token, state.user);
    await refreshUserAnalyses();
  } catch {
    clearAuthSession();
    state.user = null;
    state.evidences = [];
  }

  render();
}

function handleInput(event) {
  const githubField = event.target.closest("[data-github-import-field]");
  if (githubField) {
    updateGithubImportField(state.githubImport, githubField.dataset.githubImportField, githubField.value);
    return;
  }

  const filterField = event.target.closest("[data-filter-field]");
  if (filterField) {
    const scope = filterField.dataset.filterScope;
    const key = filterField.dataset.filterField;
    const filters = scope === "admin" ? state.adminFilters : state.dashboardFilters;
    filters[key] = filterField.value;
  }
}

async function handleSubmit(event) {
  const form = event.target.closest("[data-auth-form]");
  if (!form) {
    return;
  }

  event.preventDefault();
  const formData = new FormData(form);
  const payload = {
    email: String(formData.get("email") || "").trim(),
    password: String(formData.get("password") || ""),
  };

  state.authLoading = true;
  state.authError = null;
  render();

  try {
    const response =
      form.dataset.authForm === "register"
        ? await registerUser({
            name: String(formData.get("name") || "").trim(),
            ...payload,
          })
        : await loginUser(payload);

    saveAuthSession(response.token, response.user);
    state.user = response.user;
    state.authError = null;
    await refreshUserAnalyses();
    state.view = state.user.role === "ADMIN" ? "admin" : "dashboard";
    if (state.view === "admin") {
      await openAdmin();
    }
  } catch (error) {
    state.authError = error.message || "Não foi possível autenticar.";
  } finally {
    state.authLoading = false;
    render();
  }
}

async function handleClick(event) {
  const trigger = event.target.closest("[data-action]");
  if (!trigger) {
    return;
  }

  const action = trigger.dataset.action;

  if (action === "open-auth") {
    state.view = "auth";
    state.authError = null;
    render();
    return;
  }

  if (action === "switch-auth-login" || action === "switch-auth-register") {
    state.authMode = action === "switch-auth-login" ? "login" : "register";
    state.authError = null;
    render();
    return;
  }

  if (action === "logout") {
    await logoutUser();
    clearAuthSession();
    state.user = null;
    state.evidences = [];
    state.employees = [];
    state.adminEvidences = [];
    state.view = "home";
    render();
    return;
  }

  if (action === "open-dashboard") {
    if (!requireAuth()) {
      return;
    }
    await openDashboard();
    return;
  }

  if (action === "open-admin") {
    if (!requireAuth() || state.user.role !== "ADMIN") {
      return;
    }
    await openAdmin();
    return;
  }

  if (action === "select-employee") {
    state.selectedEmployeeId = Number(trigger.dataset.employeeId);
    await refreshEmployeeAnalyses();
    render();
    return;
  }

  if (action === "open-evidence-detail") {
    state.selectedEvidenceId = trigger.dataset.evidenceId;
    state.viewingAsAdmin = state.view === "admin";
    state.view = "evidence-detail";
    render();
    return;
  }

  if (action === "apply-filters") {
    await applyFilters(trigger.dataset.filterScope);
    return;
  }

  if (action === "clear-filters") {
    if (trigger.dataset.filterScope === "admin") {
      state.adminFilters = {};
    } else {
      state.dashboardFilters = {};
    }
    await applyFilters(trigger.dataset.filterScope);
    return;
  }

  if (action === "clear-analyses") {
    await clearAnalyses(trigger.dataset.filterScope);
    return;
  }

  if (action === "open-form" || action === "back-form") {
    if (!requireAuth()) {
      return;
    }
    await openCapturedEvidence();
    return;
  }

  if (action === "back-home") {
    event.preventDefault();
    state.view = "home";
    render();
    return;
  }

  if (action === "back-dashboard") {
    const returnToAdmin = state.viewingAsAdmin;
    state.viewingAsAdmin = false;
    if (returnToAdmin) {
      await openAdmin();
      return;
    }
    state.view = "dashboard";
    render();
    return;
  }

  if (action === "reload-pending") {
    await loadPendingEvidence({ force: true });
    state.view = "dashboard";
    render();
    return;
  }

  if (action === "search-github-prs") {
    await searchGithubPulls();
    return;
  }

  if (action === "use-github-pr") {
    chooseGithubPullRequest(state.githubImport, trigger.dataset.pullNumber);
    render();
    return;
  }

  if (action === "import-github-pr") {
    await importGithubPullRequest();
  }
}

function requireAuth() {
  if (state.user) {
    return true;
  }

  state.view = "auth";
  state.authError = "Faça login para continuar.";
  render();
  return false;
}

async function openDashboard() {
  state.view = "dashboard";
  state.viewingAsAdmin = false;
  render();
  await refreshUserAnalyses();

  if (!state.pendingEvidence) {
    await loadPendingEvidence();
    render();
  }
}

async function openAdmin() {
  state.view = "admin";
  state.viewingAsAdmin = true;
  render();

  try {
    state.employees = await fetchEmployees();
    state.selectedEmployeeId = state.selectedEmployeeId || state.employees[0]?.id || null;
    await refreshEmployeeAnalyses();
  } catch (error) {
    state.error = error;
  }

  render();
}

async function refreshUserAnalyses() {
  if (!state.user) {
    state.evidences = [];
    return;
  }

  state.evidences = await loadAnalysesForCurrentUser(state.dashboardFilters);
}

async function refreshEmployeeAnalyses() {
  if (!state.selectedEmployeeId) {
    state.adminEvidences = [];
    return;
  }

  state.adminEvidences = await loadAnalysesForEmployee(state.selectedEmployeeId, state.adminFilters);
}

async function applyFilters(scope) {
  if (scope === "admin") {
    await refreshEmployeeAnalyses();
    render();
    return;
  }

  await refreshUserAnalyses();
  render();
}

async function clearAnalyses(scope) {
  const filters = scope === "admin" ? state.adminFilters : state.dashboardFilters;
  const hasFilter = filters.dateFrom || filters.dateTo;
  const message = hasFilter
    ? "Deseja remover as evidências do período selecionado?"
    : "Deseja remover todo o histórico de evidências?";

  if (!window.confirm(message)) {
    return;
  }

  if (scope === "admin") {
    render();
    return;
  }

  await clearUserAnalyses(buildClearParams(filters));
  await refreshUserAnalyses();
  render();
}

function buildClearParams(filters) {
  const params = {};
  if (filters.dateFrom) {
    params.from = new Date(`${filters.dateFrom}T00:00:00`).toISOString();
  }
  if (filters.dateTo) {
    params.to = new Date(`${filters.dateTo}T23:59:59.999`).toISOString();
  }
  return params;
}

async function openCapturedEvidence() {
  if (!state.pendingEvidence) {
    await loadPendingEvidence();
  }

  if (!state.pendingEvidence) {
    state.view = "error";
    render();
    return;
  }

  state.view = "loading-evidence";
  render();

  try {
    const analyzedEvidence = await analyzeCapturedEvidence(state.pendingEvidence);
    await persistAnalysis(analyzedEvidence);
    state.cursor = state.pendingEvidence.nextCursor;
    saveEvidenceCursor(state.cursor);
    state.pendingEvidence = null;
    state.pendingStatus = "idle";
    state.result = analyzedEvidence;
    state.view = "result";
    await refreshUserAnalyses();
  } catch (error) {
    state.error = error;
    state.view = "error";
  }

  render();
}

async function loadPendingEvidence({ force = false } = {}) {
  if (state.pendingEvidence && !force) {
    return;
  }

  state.pendingStatus = "loading";
  state.error = null;
  render();

  try {
    state.pendingEvidence = await fetchNextCapturedEvidence(state.cursor);
    state.pendingStatus = "ready";
  } catch (error) {
    state.error = error;
    state.pendingEvidence = null;
    state.pendingStatus = "error";
  }
}

async function searchGithubPulls() {
  setGithubImportLoading(state.githubImport);
  render();

  try {
    const pullRequests = await findGithubPullRequests(githubImportRequest(state.githubImport));
    setGithubImportResults(state.githubImport, pullRequests);
  } catch (error) {
    setGithubImportError(state.githubImport, error, "Não foi possível buscar PRs no GitHub.");
  }

  render();
}

async function importGithubPullRequest() {
  setGithubImportLoading(state.githubImport);
  render();

  try {
    state.pendingEvidence = await captureEvidenceFromGithubPullRequest(githubImportRequest(state.githubImport));
    state.pendingStatus = "ready";
    setGithubImportIdle(state.githubImport);
    await openCapturedEvidence();
  } catch (error) {
    setGithubImportError(state.githubImport, error, "Não foi possível importar o PR como evidência.");
    state.view = "dashboard";
  }

  render();
}

function render() {
  if (state.view === "home") {
    appRoot.innerHTML = landingPage();
    return;
  }

  if (state.view === "auth") {
    appRoot.innerHTML = authPage(state);
    return;
  }

  if (state.view === "admin") {
    appRoot.innerHTML = adminPage(state);
    return;
  }

  if (state.view === "dashboard") {
    appRoot.innerHTML = dashboardPage(state);
    return;
  }

  if (state.view === "evidence-detail") {
    appRoot.innerHTML = evidenceDetailPage(state);
    return;
  }

  if (state.view === "loading-evidence") {
    appRoot.innerHTML = evidenceLoadingPage(state);
    return;
  }

  if (state.view === "error") {
    appRoot.innerHTML = evidenceErrorPage(state, state.error?.message || "Erro inesperado.");
    return;
  }

  appRoot.innerHTML = evidenceResultPage(state);
}
