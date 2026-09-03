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
import {
  loadAnalysesForCurrentUser,
  loadAnalysesForEmployee,
  loadReviewsForCurrentUser,
  loadReviewsForEmployee,
  submitReviewForEmployee,
} from "./services/analyses-api.mjs";
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
  evidenceEmptyPage,
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
  dashboardTab: "dashboard",
  adminFilters: {},
  employees: [],
  selectedEmployeeId: null,
  adminEvidences: [],
  selectedEvidenceId: null,
  selectedAnalysisId: null,
  review: null,
  reviewStatus: "idle",
  reviewSaving: false,
  reviewError: null,
  viewingAsAdmin: false,
};

const DASHBOARD_TABS = ["dashboard", "framework", "criteria", "connections"];

let appRoot;
let lastRenderedView = null;

export function startApp(root) {
  appRoot = root;
  lastRenderedView = null;
  appRoot.addEventListener("click", handleClick);
  appRoot.addEventListener("input", handleInput);
  appRoot.addEventListener("submit", handleSubmit);
  appRoot.addEventListener("keydown", handleKeydown);
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
  state.selectedAnalysisId = null;
  state.review = null;
  state.reviewStatus = "idle";
  state.reviewSaving = false;
  state.reviewError = null;
  state.pendingEvidence = null;
  state.pendingEvidences = [];
  state.pendingStatus = "idle";
  state.result = null;
  state.githubImport = createGithubImportState();
  state.dashboardTab = "dashboard";
  state.viewingAsAdmin = false;
  state.authLoading = false;
  state.authError = "Sua sessão expirou. Faça login novamente.";
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

function handleKeydown(event) {
  const tab = event.target.closest?.('[role="tab"][data-dashboard-tab]');
  if (!tab || state.view !== "dashboard" || state.viewingAsAdmin) {
    return;
  }

  const tabs = Array.from(appRoot.querySelectorAll('[role="tab"][data-dashboard-tab]'));
  const currentIndex = tabs.indexOf(tab);
  if (currentIndex < 0) {
    return;
  }

  let nextIndex;
  if (event.key === "ArrowRight") {
    nextIndex = (currentIndex + 1) % tabs.length;
  } else if (event.key === "ArrowLeft") {
    nextIndex = (currentIndex - 1 + tabs.length) % tabs.length;
  } else if (event.key === "Home") {
    nextIndex = 0;
  } else if (event.key === "End") {
    nextIndex = tabs.length - 1;
  } else {
    return;
  }

  event.preventDefault();
  selectDashboardTab(tabs[nextIndex].dataset.dashboardTab, true);
}

async function handleSubmit(event) {
  const form = event.target.closest("[data-auth-form], [data-profile-form], [data-review-form]");
  if (!form) {
    return;
  }

  event.preventDefault();

  if (form.matches("[data-review-form]")) {
    await submitAnalysisReview(form, event.submitter);
    return;
  }

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
    state.selectedEmployeeId = null;
    state.selectedEvidenceId = null;
    state.selectedAnalysisId = null;
    state.review = null;
    state.reviewStatus = "idle";
    state.reviewSaving = false;
    state.reviewError = null;
    state.githubImport = createGithubImportState();
    state.dashboardTab = "dashboard";
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

  if (action === "open-connections") {
    if (!requireAuth()) {
      return;
    }
    state.view = "dashboard";
    state.viewingAsAdmin = false;
    state.dashboardTab = "connections";
    state.error = null;
    render();
    return;
  }

  if (action === "switch-dashboard-tab") {
    const nextTab = trigger.dataset.dashboardTab;
    selectDashboardTab(nextTab, true);
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
    const pool = state.view === "admin" ? state.adminEvidences : state.evidences;
    const selected = pool.find((item) => String(item.id) === String(state.selectedEvidenceId));
    state.selectedAnalysisId =
      trigger.dataset.savedAnalysisId || selected?.analysisId || null;
    state.review = null;
    state.reviewStatus = "loading";
    state.reviewError = null;
    state.viewingAsAdmin = state.view === "admin";
    state.view = "evidence-detail";
    render();
    await refreshSelectedAnalysisReview();
    if (state.user && state.view === "evidence-detail") {
      render();
    }
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
    state.selectedEvidenceId = null;
    state.selectedAnalysisId = null;
    state.review = null;
    state.reviewStatus = "idle";
    state.reviewError = null;
    if (returnToAdmin) {
      await openAdmin();
      return;
    }
    state.dashboardTab = "dashboard";
    state.view = "dashboard";
    render();
    return;
  }

  if (action === "reload-pending") {
    state.dashboardTab = "dashboard";
    state.view = "dashboard";
    await loadPendingEvidence({ force: true });
    if (state.pendingStatus === "error") {
      state.view = "error";
    }
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

function selectDashboardTab(nextTab, focus = false) {
  if (!DASHBOARD_TABS.includes(nextTab)) {
    return;
  }

  state.dashboardTab = nextTab;
  render();

  if (focus) {
    appRoot.querySelector(`#dashboard-tab-${nextTab}`)?.focus();
  }
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
  state.dashboardTab = "dashboard";
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

async function refreshSelectedAnalysisReview() {
  if (!state.selectedAnalysisId) {
    state.review = { currentStatus: "UNREVIEWED", history: [] };
    state.reviewStatus = "ready";
    return;
  }

  try {
    state.review = state.viewingAsAdmin
      ? await loadReviewsForEmployee(state.selectedEmployeeId, state.selectedAnalysisId)
      : await loadReviewsForCurrentUser(state.selectedAnalysisId);
    state.reviewStatus = "ready";
    state.reviewError = null;
  } catch (error) {
    if (error.isUnauthorized || error.status === 401) {
      return;
    }
    state.reviewStatus = "error";
    state.reviewError = error.message || "Não foi possível carregar a revisão.";
  }
}

async function submitAnalysisReview(form, submitter) {
  if (!state.selectedEmployeeId || !state.selectedAnalysisId) {
    return;
  }

  const formData = new FormData(form);
  const status = submitter?.dataset.reviewStatus || String(formData.get("status") || "");
  const comment = String(formData.get("comment") || "");
  state.reviewSaving = true;
  state.reviewError = null;
  render();

  try {
    state.review = await submitReviewForEmployee(
      state.selectedEmployeeId,
      state.selectedAnalysisId,
      { status, comment },
    );
    state.reviewStatus = "ready";
  } catch (error) {
    if (error.isUnauthorized || error.status === 401) {
      return;
    }
    state.reviewStatus = "error";
    state.reviewError = error.message || "Não foi possível salvar a revisão.";
  } finally {
    state.reviewSaving = false;
    if (state.user) {
      render();
    }
  }
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
    state.view = state.pendingStatus === "error" ? "error" : "empty-evidence";
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
    state.selectedAnalysisId = analyzedEvidence.analysisId || null;
    state.review = { currentStatus: "UNREVIEWED", history: [] };
    state.reviewStatus = "ready";
    state.reviewError = null;
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
      throw new Error("Esta evidência não está mais pendente.");
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
  const viewChanged = lastRenderedView !== state.view;
  let page;

  if (state.view === "home") {
    page = landingPage();
  } else if (state.view === "auth") {
    page = authPage(state);
  } else if (state.view === "profile") {
    page = profilePage(state);
  } else if (state.view === "admin") {
    page = adminPage(state);
  } else if (state.view === "dashboard") {
    page = dashboardPage(state);
  } else if (state.view === "evidence-detail") {
    page = evidenceDetailPage(state);
  } else if (state.view === "loading-evidence") {
    page = evidenceLoadingPage(state);
  } else if (state.view === "empty-evidence") {
    page = evidenceEmptyPage(state);
  } else if (state.view === "error") {
    page = evidenceErrorPage(state, state.error?.message || "Erro inesperado.");
  } else {
    page = evidenceResultPage(state);
  }

  appRoot.innerHTML = page;
  lastRenderedView = state.view;

  if (viewChanged) {
    resetScrollPosition();
  }
}

function resetScrollPosition() {
  scrollToTop();

  if (typeof window !== "undefined" && typeof window.requestAnimationFrame === "function") {
    window.requestAnimationFrame(scrollToTop);
  }
}

function scrollToTop() {
  if (typeof window !== "undefined" && typeof window.scrollTo === "function") {
    try {
      window.scrollTo({ top: 0, left: 0, behavior: "auto" });
    } catch {
      try {
        window.scrollTo(0, 0);
      } catch {
        // Some test environments expose scrollTo without implementing it.
      }
    }
  }

  if (typeof document !== "undefined") {
    document.documentElement.scrollTop = 0;
    if (document.body) {
      document.body.scrollTop = 0;
    }
  }
}
