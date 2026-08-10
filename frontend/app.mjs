import {
  chooseGithubPullRequest,
  createGithubImportState,
  githubImportRequest,
  applyGithubSettings,
  setGithubConnectionTestError,
  setGithubConnectionTestResult,
  setGithubConnectionTesting,
  setGithubImportError,
  setGithubImportIdle,
  setGithubImportLoading,
  setGithubImportResults,
  setGithubSettingsError,
  setGithubSettingsLoading,
  setGithubSettingsSaving,
  setGithubSyncError,
  setGithubSyncLoading,
  setGithubSyncResult,
  updateGithubImportField,
} from "./features/github-import/github-import-model.mjs";
import { loadAnalysesForCurrentUser, loadAnalysesForEmployee } from "./services/analyses-api.mjs";
import { loadInsightsForCurrentUser } from "./services/insights-api.mjs";
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
import {
  captureEvidenceFromGithubPullRequest,
  dismissEvidence,
  fetchEvidence,
  fetchEvidences,
} from "./services/evidence-api.mjs";
import {
  fetchGithubSettings,
  findGithubPullRequests,
  saveGithubSettings,
  syncGithub,
  testGithubSettings,
} from "./services/github-api.mjs";
import { fetchProfile, updateProfile } from "./services/profile-api.mjs";
import { clearLegacyEvidenceStorage } from "./services/session-store.mjs";
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
import { profilePage } from "./views/profile-view.mjs";

const state = {
  view: "home",
  pendingEvidence: null,
  pendingEvidences: [],
  pendingStatus: "idle",
  githubImport: createGithubImportState(),
  result: null,
  error: null,
  evidences: [],
  insights: null,
  insightsStatus: "idle",
  insightsError: null,
  user: loadAuthUser(),
  profile: null,
  profileDraft: null,
  profileLoading: false,
  profileSaving: false,
  profileError: null,
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
  if (typeof window !== "undefined") {
    window.addEventListener("promova:auth-expired", handleAuthExpired);
  }
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
    await refreshProfile();
    await refreshUserAnalyses();
    await refreshInsights();
    await refreshPendingEvidences();
    await refreshGithubSettings();
  } catch (error) {
    if (error.status === 401 || error.isUnauthorized) {
      expireSession();
      return;
    }
    state.error = error;
  }

  render();
}

function handleAuthExpired() {
  expireSession();
}

function expireSession() {
  clearAuthSession();
  state.user = null;
  state.profile = null;
  state.profileDraft = null;
  state.profileLoading = false;
  state.profileSaving = false;
  state.profileError = null;
  state.evidences = [];
  state.insights = null;
  state.insightsStatus = "idle";
  state.insightsError = null;
  state.adminEvidences = [];
  state.employees = [];
  state.selectedEmployeeId = null;
  state.selectedEvidenceId = null;
  state.pendingEvidence = null;
  state.pendingEvidences = [];
  state.pendingStatus = "idle";
  state.result = null;
  state.githubImport = createGithubImportState();
  state.viewingAsAdmin = false;
  state.authLoading = false;
  state.authError = "Sua sessÃ£o expirou. FaÃ§a login novamente.";
  state.view = "auth";
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

  const profileField = event.target.closest("[data-profile-field]");
  if (profileField) {
    state.profileDraft = {
      ...(state.profileDraft || state.profile || {}),
      [profileField.dataset.profileField]: profileField.value,
    };
  }
}

async function handleSubmit(event) {
  const form = event.target.closest("[data-auth-form], [data-profile-form]");
  if (!form) {
    return;
  }

  event.preventDefault();

  if (form.matches("[data-profile-form]")) {
    await submitProfile(form);
    return;
  }

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
    await refreshProfile();
    await refreshUserAnalyses();
    await refreshInsights();
    await refreshPendingEvidences();
    await refreshGithubSettings();
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
    state.profile = null;
    state.profileDraft = null;
    state.profileLoading = false;
    state.profileSaving = false;
    state.profileError = null;
    state.evidences = [];
    state.insights = null;
    state.insightsStatus = "idle";
    state.insightsError = null;
    state.pendingEvidence = null;
    state.pendingEvidences = [];
    state.employees = [];
    state.adminEvidences = [];
    state.githubImport = createGithubImportState();
    state.authError = null;
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

  if (action === "open-profile") {
    if (!requireAuth()) {
      return;
    }
    await openProfile();
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
    state.selectedEvidenceId = trigger.dataset.analysisId || trigger.dataset.evidenceId;
    state.viewingAsAdmin = state.view === "admin";
    state.view = "evidence-detail";
    render();
    return;
  }

  if (action === "open-pending-evidence") {
    await openPendingEvidence(trigger.dataset.evidenceId);
    return;
  }

  if (action === "dismiss-evidence") {
    await dismissPendingEvidence(trigger.dataset.evidenceId);
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

  if (action === "reload-insights") {
    await refreshInsights();
    state.view = "dashboard";
    render();
    return;
  }

  if (action === "search-github-prs") {
    await searchGithubPulls();
    return;
  }

  if (action === "save-github-settings") {
    await saveGithubConnectionSettings();
    return;
  }

  if (action === "test-github-settings") {
    await testGithubConnection();
    return;
  }

  if (action === "sync-github") {
    await syncGithubConnection();
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

async function openProfile() {
  state.view = "profile";
  state.profileError = null;
  render();

  try {
    await refreshProfile();
  } catch (error) {
    if (!error.isUnauthorized && error.status !== 401) {
      state.profileError = error.message || "Não foi possível carregar o perfil.";
    }
  }

  if (state.user) {
    render();
  }
}

async function refreshProfile() {
  if (!state.user) {
    state.profile = null;
    state.profileDraft = null;
    return null;
  }

  state.profileLoading = true;
  try {
    const profile = await fetchProfile();
    state.profile = profile;
    state.profileDraft = {
      currentLevel: profile.currentLevel,
      targetLevel: profile.targetLevel,
    };
    state.profileError = null;
    return profile;
  } finally {
    state.profileLoading = false;
  }
}

async function submitProfile(form) {
  const formData = new FormData(form);
  state.profileSaving = true;
  state.profileError = null;
  render();

  try {
    const profile = await updateProfile({
      currentLevel: String(formData.get("currentLevel") || ""),
      targetLevel: String(formData.get("targetLevel") || ""),
    });
    state.profile = profile;
    state.profileDraft = {
      currentLevel: profile.currentLevel,
      targetLevel: profile.targetLevel,
    };
  } catch (error) {
    if (!error.isUnauthorized && error.status !== 401) {
      state.profileError = error.message || "Não foi possível salvar o perfil.";
    }
  } finally {
    state.profileSaving = false;
    if (state.user) {
      render();
    }
  }
}

async function openDashboard() {
  state.view = "dashboard";
  state.viewingAsAdmin = false;
  state.insightsStatus = "loading";
  state.insightsError = null;
  render();
  await refreshUserAnalyses();
  await refreshInsights();
  await refreshPendingEvidences();
  render();
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

async function refreshInsights() {
  if (!state.user) {
    state.insights = null;
    state.insightsStatus = "idle";
    state.insightsError = null;
    return;
  }

  state.insightsStatus = "loading";
  state.insightsError = null;

  try {
    state.insights = await loadInsightsForCurrentUser(state.dashboardFilters);
    state.insightsStatus = "ready";
  } catch (error) {
    if (error.isUnauthorized || error.status === 401) {
      return;
    }
    state.insights = null;
    state.insightsStatus = "error";
    state.insightsError = error.message || "Erro inesperado.";
  }
}

async function refreshPendingEvidences() {
  if (!state.user) {
    state.pendingEvidences = [];
    state.pendingEvidence = null;
    return;
  }

  state.pendingEvidences = await fetchEvidences({
    status: "PENDING",
    ...buildClearParams(state.dashboardFilters),
  });
  clearLegacyEvidenceStorage();

  if (state.pendingEvidence) {
    state.pendingEvidence =
      state.pendingEvidences.find((item) => String(item.id) === String(state.pendingEvidence.id)) || null;
  }
}

async function refreshGithubSettings() {
  if (!state.user) {
    state.githubImport = createGithubImportState();
    return;
  }

  setGithubSettingsLoading(state.githubImport);
  try {
    applyGithubSettings(state.githubImport, await fetchGithubSettings());
  } catch (error) {
    if (error.isUnauthorized || error.status === 401) {
      return;
    }
    setGithubSettingsError(
      state.githubImport,
      error,
      "Não foi possível carregar a configuração do GitHub.",
    );
  }
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
  await refreshInsights();
  await refreshPendingEvidences();
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
  await refreshInsights();
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
    const profile = state.profile || (await refreshProfile());
    if (!profile) {
      throw new Error("Seu perfil de carreira não está disponível.");
    }

    const analyzedEvidence = await analyzeCapturedEvidence(state.pendingEvidence.id);
    state.pendingEvidence = null;
    state.pendingStatus = "idle";
    state.result = analyzedEvidence;
    state.view = "result";
    await refreshUserAnalyses();
    await refreshInsights();
    await refreshPendingEvidences();
  } catch (error) {
    if (error.isUnauthorized || error.status === 401) {
      return;
    }
    state.error = error;
    state.view = "error";
  }

  if (state.user) {
    render();
  }
}

async function loadPendingEvidence({ force = false } = {}) {
  if (state.pendingEvidence && !force) {
    return;
  }

  state.pendingStatus = "loading";
  state.error = null;
  render();

  try {
    await refreshPendingEvidences();
    state.pendingEvidence = state.pendingEvidences[0] || null;
    state.pendingStatus = "ready";
  } catch (error) {
    state.error = error;
    state.pendingEvidence = null;
    state.pendingStatus = "error";
  }
}

async function openPendingEvidence(evidenceId) {
  state.pendingStatus = "loading";
  state.error = null;
  render();

  try {
    state.pendingEvidence = await fetchEvidence(evidenceId);
    if (state.pendingEvidence.status !== "PENDING") {
      throw new Error("Esta evidÃªncia nÃ£o estÃ¡ mais pendente.");
    }
    state.pendingStatus = "ready";
    await openCapturedEvidence();
  } catch (error) {
    if (error.isUnauthorized || error.status === 401) {
      return;
    }
    state.error = error;
    state.pendingEvidence = null;
    state.pendingStatus = "error";
    render();
  }
}

async function dismissPendingEvidence(evidenceId) {
  try {
    await dismissEvidence(evidenceId);
    state.pendingEvidences = state.pendingEvidences.filter(
      (item) => String(item.id) !== String(evidenceId),
    );
    if (state.pendingEvidence && String(state.pendingEvidence.id) === String(evidenceId)) {
      state.pendingEvidence = null;
    }
    state.pendingStatus = "ready";
    render();
  } catch (error) {
    if (error.isUnauthorized || error.status === 401) {
      return;
    }
    state.error = error;
    render();
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

async function saveGithubConnectionSettings() {
  setGithubSettingsSaving(state.githubImport, true);
  state.githubImport.testMessage = "";
  state.githubImport.syncError = "";
  render();

  try {
    const settings = await saveGithubSettings({
      repoSlug: state.githubImport.repoSlug,
      authorLogin: state.githubImport.authorLogin,
    });
    applyGithubSettings(state.githubImport, settings);
  } catch (error) {
    if (!error.isUnauthorized && error.status !== 401) {
      setGithubSettingsError(
        state.githubImport,
        error,
        "Não foi possível salvar a configuração do GitHub.",
      );
    }
  } finally {
    setGithubSettingsSaving(state.githubImport, false);
    if (state.user) {
      render();
    }
  }
}

async function testGithubConnection() {
  setGithubConnectionTesting(state.githubImport);
  render();

  try {
    setGithubConnectionTestResult(state.githubImport, await testGithubSettings());
  } catch (error) {
    if (!error.isUnauthorized && error.status !== 401) {
      setGithubConnectionTestError(
        state.githubImport,
        error,
        "Não foi possível testar o acesso ao GitHub.",
      );
    }
  }

  if (state.user) {
    render();
  }
}

async function syncGithubConnection() {
  setGithubSyncLoading(state.githubImport);
  render();

  try {
    setGithubSyncResult(state.githubImport, await syncGithub());
    await refreshGithubSettings();
    await refreshPendingEvidences();
  } catch (error) {
    if (!error.isUnauthorized && error.status !== 401) {
      setGithubSyncError(state.githubImport, error, "Não foi possível sincronizar o GitHub.");
    }
  }

  if (state.user) {
    render();
  }
}

async function importGithubPullRequest() {
  setGithubImportLoading(state.githubImport);
  render();

  try {
    const capturedEvidence = await captureEvidenceFromGithubPullRequest(
      githubImportRequest(state.githubImport),
    );
    state.pendingEvidence = capturedEvidence.status === "PENDING" ? capturedEvidence : null;
    state.pendingStatus = "ready";
    setGithubImportIdle(state.githubImport);
    await refreshPendingEvidences();
    if (state.pendingEvidence) {
      await openCapturedEvidence();
    } else {
      state.view = "dashboard";
    }
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

  if (state.view === "profile") {
    appRoot.innerHTML = profilePage(state);
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
