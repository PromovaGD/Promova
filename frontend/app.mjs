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
import {
  clearAuthSession,
  loadAuthRoute,
  loadAuthToken,
  loadAuthUser,
  saveAuthRoute,
  saveAuthSession,
} from "./services/auth-store.mjs";
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
import { fetchProfile } from "./services/profile-api.mjs";
import {
  createEmployeeObjective,
  fetchEmployeeCareerPlan,
  updateEmployeeCareerPlan,
  updateEmployeeObjective,
} from "./services/career-plan-api.mjs";
import {
  archiveJobRole,
  createJobRole,
  fetchCareerConfiguration,
  fetchJobRoles,
  fetchManagerSettings,
  updateJobRole,
  updateTerminology,
} from "./services/manager-settings-api.mjs";
import { managerPage, permissionPage } from "./views/manager-view.mjs";
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
  profileLoading: false,
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
  managerSection: "people",
  managerSettings: null,
  managerSettingsStatus: "idle",
  managerSettingsSaving: false,
  managerSettingsError: null,
  managerSettingsNotice: null,
  jobRoles: [],
  careerConfiguration: null,
  selectedCareerPlan: null,
  careerPlanStatus: "idle",
  careerPlanSaving: false,
  careerPlanError: null,
  careerPlanNotice: null,
  selectedEvidenceId: null,
  selectedAnalysisId: null,
  review: null,
  reviewStatus: "idle",
  reviewSaving: false,
  reviewError: null,
  viewingAsAdmin: false,
  permissionError: null,
};

const DASHBOARD_TABS = ["dashboard", "framework", "criteria", "connections"];

let appRoot;
let lastRenderedView = null;
let pendingProtectedRoute = null;

export function startApp(root) {
  appRoot = root;
  lastRenderedView = null;
  appRoot.addEventListener("click", handleClick);
  appRoot.addEventListener("input", handleInput);
  appRoot.addEventListener("submit", handleSubmit);
  appRoot.addEventListener("keydown", handleKeydown);
  if (typeof window !== "undefined") {
    pendingProtectedRoute = browserRoute();
    window.addEventListener("promova:auth-expired", handleAuthExpired);
    window.addEventListener("promova:permission-denied", handlePermissionDenied);
    window.addEventListener("popstate", handleBrowserNavigation);
  }
  return bootstrapSession();
}

async function bootstrapSession() {
  const token = loadAuthToken();
  if (!token) {
    if (isProtectedRoute(pendingProtectedRoute)) {
      state.view = "auth";
      state.authError = "Faça login para continuar.";
    }
    render();
    return;
  }

  try {
    state.user = await fetchCurrentUser();
    saveAuthSession(token, state.user);
  } catch (error) {
    if (error.status === 401 || error.isUnauthorized) {
      expireSession();
      return;
    }
    state.error = error;
    if (!isStoredUser(state.user)) {
      state.view = "auth";
      state.authError = "Não foi possível validar sua sessão agora. Tente novamente sem sair da conta.";
      render();
      return;
    }
  }

  await restoreAuthenticatedLocation();
}

async function restoreAuthenticatedLocation() {
  const requestedRoute =
    (isProtectedRoute(pendingProtectedRoute) && pendingProtectedRoute) || loadAuthRoute();
  pendingProtectedRoute = null;

  if (state.user.role === "MANAGER") {
    state.managerSection = requestedRoute === "/manager?section=settings" ? "settings" : "people";
    await openManager();
    return;
  }

  state.viewingAsAdmin = false;
  state.dashboardTab = dashboardTabFromRoute(requestedRoute);
  state.view = requestedRoute === "/profile" ? "profile" : "dashboard";
  render();

  try {
    await loadEmployeeWorkspace();
  } catch (error) {
    if (error.status === 401 || error.isUnauthorized) {
      return;
    }
    state.error = error;
    if (state.view === "profile") {
      state.profileError = error.message || "Não foi possível carregar o perfil.";
    }
  }

  if (state.user) {
    render();
  }
}

async function handleBrowserNavigation() {
  pendingProtectedRoute = browserRoute();
  if (state.user && isProtectedRoute(pendingProtectedRoute)) {
    await restoreAuthenticatedLocation();
  }
}

function handleAuthExpired() {
  expireSession();
}

function handlePermissionDenied(event) {
  showPermissionError(event?.detail?.message);
}

function expireSession() {
  clearAuthSession();
  state.user = null;
  state.profile = null;
  state.profileLoading = false;
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
  state.selectedCareerPlan = null;
  state.careerPlanStatus = "idle";
  state.careerPlanSaving = false;
  state.careerPlanError = null;
  state.careerPlanNotice = null;
  state.result = null;
  state.githubImport = createGithubImportState();
  state.dashboardTab = "dashboard";
  state.viewingAsAdmin = false;
  state.permissionError = null;
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
  const form = event.target.closest(
    "[data-auth-form], [data-review-form], [data-terminology-form], [data-job-role-form], [data-career-plan-form], [data-objective-form]",
  );
  if (!form) {
    return;
  }

  event.preventDefault();

  if (form.matches("[data-career-plan-form]")) {
    await submitCareerPlan(form);
    return;
  }

  if (form.matches("[data-objective-form]")) {
    await submitCareerObjective(form);
    return;
  }

  if (form.matches("[data-terminology-form]")) {
    await submitTerminology(form);
    return;
  }

  if (form.matches("[data-job-role-form]")) {
    await submitJobRole(form);
    return;
  }

  if (form.matches("[data-review-form]")) {
    await submitAnalysisReview(form, event.submitter);
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
    await restoreAuthenticatedLocation();
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
    state.profileLoading = false;
    state.profileError = null;
    state.evidences = [];
    state.insights = null;
    state.insightsStatus = "idle";
    state.insightsError = null;
    state.pendingEvidence = null;
    state.pendingEvidences = [];
    state.employees = [];
    state.adminEvidences = [];
    state.managerSection = "people";
    state.managerSettings = null;
    state.managerSettingsStatus = "idle";
    state.managerSettingsSaving = false;
    state.managerSettingsError = null;
    state.managerSettingsNotice = null;
    state.jobRoles = [];
    state.careerConfiguration = null;
    state.selectedCareerPlan = null;
    state.careerPlanStatus = "idle";
    state.careerPlanSaving = false;
    state.careerPlanError = null;
    state.careerPlanNotice = null;
    state.selectedEmployeeId = null;
    state.selectedEvidenceId = null;
    state.selectedAnalysisId = null;
    state.review = null;
    state.reviewStatus = "idle";
    state.reviewSaving = false;
    state.reviewError = null;
    state.githubImport = createGithubImportState();
    state.dashboardTab = "dashboard";
    state.permissionError = null;
    state.authError = null;
    state.view = "home";
    render();
    return;
  }

  if (action === "open-dashboard") {
    if (!requireEmployeeAccess()) {
      return;
    }
    await openDashboard();
    return;
  }

  if (action === "open-connections") {
    if (!requireEmployeeAccess()) {
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
    if (!requireEmployeeAccess()) {
      return;
    }
    await openProfile();
    return;
  }

  if (action === "open-manager") {
    if (!requireAuth()) {
      return;
    }
    if (state.user.role !== "MANAGER") {
      showPermissionError();
      return;
    }
    await openManager();
    return;
  }

  if (action === "select-employee") {
    state.selectedEmployeeId = Number(trigger.dataset.employeeId);
    state.careerPlanNotice = null;
    state.careerPlanError = null;
    await Promise.all([refreshEmployeeAnalyses(), refreshSelectedCareerPlan()]);
    render();
    return;
  }

  if (action === "switch-manager-section") {
    state.managerSection = trigger.dataset.managerSection === "settings" ? "settings" : "people";
    state.managerSettingsError = null;
    state.managerSettingsNotice = null;
    render();
    return;
  }

  if (action === "archive-job-role") {
    const card = trigger.closest("[data-job-role-card]");
    const replacementRoleId = card?.querySelector("[data-replacement-role]")?.value || null;
    if (!window.confirm("Arquivar este cargo? Usuários atribuídos exigem um cargo alternativo.")) {
      return;
    }
    await archiveManagerJobRole(trigger.dataset.jobRoleId, replacementRoleId);
    return;
  }

  if (action === "open-evidence-detail") {
    state.selectedEvidenceId = trigger.dataset.analysisId || trigger.dataset.evidenceId;
    const pool = state.view === "manager" ? state.adminEvidences : state.evidences;
    const selected = pool.find((item) => String(item.id) === String(state.selectedEvidenceId));
    state.selectedAnalysisId =
      trigger.dataset.savedAnalysisId || selected?.analysisId || null;
    state.review = null;
    state.reviewStatus = "loading";
    state.reviewError = null;
    state.viewingAsAdmin = state.view === "manager";
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
    if (!requireEmployeeAccess()) {
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
      await openManager();
      return;
    }
    if (state.user?.role === "MANAGER") {
      await openManager();
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

function requireEmployeeAccess() {
  if (!requireAuth()) {
    return false;
  }
  if (state.user.role === "MANAGER") {
    showPermissionError("O Manager Console não oferece ações do painel de funcionário.");
    return false;
  }
  return true;
}

function showPermissionError(message) {
  state.permissionError = message || "Você não tem permissão para realizar esta ação.";
  state.view = "permission-error";
  render();
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
    return null;
  }

  state.profileLoading = true;
  try {
    const profile = await fetchProfile();
    state.profile = profile;
    state.profileError = null;
    return profile;
  } finally {
    state.profileLoading = false;
  }
}

async function submitTerminology(form) {
  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }
  const values = new FormData(form);
  state.managerSettingsSaving = true;
  state.managerSettingsError = null;
  state.managerSettingsNotice = null;
  render();

  try {
    const labels = await updateTerminology({
      manager: String(values.get("manager") || "").trim(),
      employee: String(values.get("employee") || "").trim(),
      jobRole: String(values.get("jobRole") || "").trim(),
      level: String(values.get("level") || "").trim(),
      characteristics: String(values.get("characteristics") || "").trim(),
      objective: String(values.get("objective") || "").trim(),
    });
    state.managerSettings = { ...state.managerSettings, labels };
    state.managerSettingsNotice = "Terminologia atualizada.";
  } catch (error) {
    state.managerSettingsError = error.message || "Não foi possível salvar a terminologia.";
  } finally {
    state.managerSettingsSaving = false;
    render();
  }
}

async function submitJobRole(form) {
  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }
  const values = new FormData(form);
  const allowedLevelIds = values.getAll("allowedLevelIds").map(String);
  if (!allowedLevelIds.length) {
    state.managerSettingsError = "Selecione ao menos um nível permitido.";
    render();
    return;
  }

  state.managerSettingsSaving = true;
  state.managerSettingsError = null;
  state.managerSettingsNotice = null;
  render();
  try {
    const payload = {
      name: String(values.get("name") || "").trim(),
      description: String(values.get("description") || "").trim(),
      allowedLevelIds,
    };
    if (form.dataset.jobRoleId) {
      await updateJobRole(form.dataset.jobRoleId, payload);
      state.managerSettingsNotice = "Cargo atualizado.";
    } else {
      await createJobRole(payload);
      state.managerSettingsNotice = "Cargo criado.";
    }
    await refreshManagerSettings();
  } catch (error) {
    state.managerSettingsError = error.message || "Não foi possível salvar o cargo.";
  } finally {
    state.managerSettingsSaving = false;
    render();
  }
}

async function archiveManagerJobRole(roleId, replacementRoleId) {
  state.managerSettingsSaving = true;
  state.managerSettingsError = null;
  state.managerSettingsNotice = null;
  render();
  try {
    await archiveJobRole(roleId, replacementRoleId);
    await refreshManagerSettings();
    state.managerSettingsNotice = "Cargo arquivado.";
  } catch (error) {
    state.managerSettingsError = error.message || "Não foi possível arquivar o cargo.";
  } finally {
    state.managerSettingsSaving = false;
    render();
  }
}

async function refreshManagerSettings() {
  const [settings, jobRoles] = await Promise.all([fetchManagerSettings(), fetchJobRoles()]);
  state.managerSettings = settings;
  state.jobRoles = jobRoles;
  state.managerSettingsStatus = "ready";
}

async function refreshSelectedCareerPlan() {
  if (!state.selectedEmployeeId) {
    state.selectedCareerPlan = null;
    state.careerPlanStatus = "idle";
    return;
  }
  state.careerPlanStatus = "loading";
  state.careerPlanError = null;
  try {
    state.selectedCareerPlan = await fetchEmployeeCareerPlan(state.selectedEmployeeId);
    state.careerPlanStatus = "ready";
  } catch (error) {
    if (error.isUnauthorized || error.status === 401) {
      return;
    }
    state.selectedCareerPlan = null;
    state.careerPlanStatus = "error";
    state.careerPlanError = error.message || "Não foi possível carregar o plano de carreira.";
  }
}

async function submitCareerPlan(form) {
  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }
  const values = new FormData(form);
  state.careerPlanSaving = true;
  state.careerPlanError = null;
  state.careerPlanNotice = null;
  render();
  try {
    state.selectedCareerPlan = await updateEmployeeCareerPlan(state.selectedEmployeeId, {
      jobRoleId: Number(values.get("jobRoleId")),
      currentLevel: String(values.get("currentLevel") || ""),
      targetLevel: String(values.get("targetLevel") || ""),
      characteristics: String(values.get("characteristics") || "")
        .split(/[,\n]/)
        .map((item) => item.trim())
        .filter(Boolean),
    });
    state.careerPlanNotice = "Plano de carreira atualizado.";
  } catch (error) {
    state.careerPlanError = error.message || "Não foi possível salvar o plano de carreira.";
  } finally {
    state.careerPlanSaving = false;
    render();
  }
}

async function submitCareerObjective(form) {
  if (!form.checkValidity()) {
    form.reportValidity();
    return;
  }
  const values = new FormData(form);
  const payload = {
    text: String(values.get("text") || "").trim(),
    status: String(values.get("status") || "ACTIVE"),
    targetDate: String(values.get("targetDate") || "") || null,
  };
  state.careerPlanSaving = true;
  state.careerPlanError = null;
  state.careerPlanNotice = null;
  render();
  try {
    if (form.dataset.objectiveId) {
      await updateEmployeeObjective(
        state.selectedEmployeeId,
        form.dataset.objectiveId,
        payload,
      );
      state.careerPlanNotice = "Objetivo atualizado.";
    } else {
      await createEmployeeObjective(state.selectedEmployeeId, payload);
      state.careerPlanNotice = "Objetivo criado.";
    }
    await refreshSelectedCareerPlan();
  } catch (error) {
    state.careerPlanError = error.message || "Não foi possível salvar o objetivo.";
  } finally {
    state.careerPlanSaving = false;
    render();
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

async function openManager() {
  state.view = "manager";
  state.viewingAsAdmin = true;
  state.permissionError = null;
  render();

  try {
    state.managerSettingsStatus = "loading";
    const [employees, settings, jobRoles] = await Promise.all([
      fetchEmployees(),
      fetchManagerSettings(),
      fetchJobRoles(),
    ]);
    state.employees = employees;
    state.managerSettings = settings;
    state.jobRoles = jobRoles;
    state.managerSettingsStatus = "ready";
    state.selectedEmployeeId = state.selectedEmployeeId || state.employees[0]?.id || null;
    await Promise.all([refreshEmployeeAnalyses(), refreshSelectedCareerPlan()]);
  } catch (error) {
    state.error = error;
    state.managerSettingsStatus = "error";
    state.managerSettingsError = error.message || "Não foi possível carregar as configurações.";
  }

  render();
}

async function loadEmployeeWorkspace() {
  state.careerConfiguration = await fetchCareerConfiguration();
  await refreshProfile();
  await refreshUserAnalyses();
  await refreshInsights();
  await refreshPendingEvidences();
  await refreshGithubSettings();
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
  } else if (state.view === "manager") {
    page = managerPage(state);
  } else if (state.view === "permission-error") {
    page = permissionPage(state);
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
  syncBrowserLocation();

  if (viewChanged) {
    resetScrollPosition();
  }
}

function syncBrowserLocation() {
  if (typeof window === "undefined") {
    return;
  }

  const route = routeForState();
  if (state.user && isProtectedRoute(route)) {
    saveAuthRoute(route);
  }

  try {
    const currentRoute = `${window.location?.pathname || "/"}${window.location?.search || ""}`;
    if (currentRoute !== route && typeof window.history?.replaceState === "function") {
      window.history.replaceState({}, "", route);
    }
  } catch {
    // Embedded browsers can restrict History API access; route persistence still works.
  }
}

function routeForState() {
  if (state.view === "auth") {
    return "/login";
  }
  if (state.view === "profile") {
    return "/profile";
  }
  if (state.view === "manager" || (state.user?.role === "MANAGER" && state.view === "permission-error")) {
    return state.managerSection === "settings" ? "/manager?section=settings" : "/manager";
  }
  if (state.user?.role === "EMPLOYEE" && state.view !== "home") {
    return state.dashboardTab === "dashboard" ? "/dashboard" : `/dashboard?tab=${state.dashboardTab}`;
  }
  return "/";
}

function browserRoute() {
  try {
    return `${window.location?.pathname || "/"}${window.location?.search || ""}`;
  } catch {
    return null;
  }
}

function isProtectedRoute(route) {
  return typeof route === "string" && (/^\/dashboard(?:\?|$)/.test(route) || route === "/profile" || /^\/manager(?:\?|$)/.test(route));
}

function dashboardTabFromRoute(route) {
  if (typeof route !== "string" || !route.startsWith("/dashboard")) {
    return "dashboard";
  }
  try {
    const tab = new URL(route, "http://promova.local").searchParams.get("tab");
    return DASHBOARD_TABS.includes(tab) ? tab : "dashboard";
  } catch {
    return "dashboard";
  }
}

function isStoredUser(user) {
  return Boolean(user && Number.isFinite(Number(user.id)) && ["EMPLOYEE", "MANAGER"].includes(user.role));
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
