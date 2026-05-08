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
import { analyzeCapturedEvidence } from "./services/analysis-api.mjs";
import { captureEvidenceFromGithubPullRequest, fetchNextCapturedEvidence } from "./services/evidence-api.mjs";
import { findGithubPullRequests } from "./services/github-api.mjs";
import {
  loadEvidenceCursor,
  loadSessionEvidences,
  saveEvidenceCursor,
  saveSessionEvidences,
} from "./services/session-store.mjs";
import { dashboardPage } from "./views/dashboard-view.mjs";
import { evidenceErrorPage, evidenceLoadingPage, evidenceResultPage } from "./views/evidence-view.mjs";
import { landingPage } from "./views/landing-view.mjs";

const state = {
  view: "home",
  cursor: loadEvidenceCursor(),
  pendingEvidence: null,
  pendingStatus: "idle",
  githubImport: createGithubImportState(),
  result: null,
  error: null,
  evidences: loadSessionEvidences(),
};

let appRoot;

export function startApp(root) {
  appRoot = root;
  appRoot.addEventListener("click", handleClick);
  appRoot.addEventListener("input", handleInput);
  render();
}

function handleInput(event) {
  const field = event.target.closest("[data-github-import-field]");
  if (!field) {
    return;
  }

  updateGithubImportField(state.githubImport, field.dataset.githubImportField, field.value);
}

async function handleClick(event) {
  const trigger = event.target.closest("[data-action]");
  if (!trigger) {
    return;
  }

  const action = trigger.dataset.action;

  if (action === "open-dashboard") {
    await openDashboard();
    return;
  }

  if (action === "open-form" || action === "back-form") {
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

async function openDashboard() {
  state.view = "dashboard";
  render();

  if (!state.pendingEvidence) {
    await loadPendingEvidence();
    render();
  }
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
    saveEvidence(analyzedEvidence);
    state.cursor = state.pendingEvidence.nextCursor;
    saveEvidenceCursor(state.cursor);
    state.pendingEvidence = null;
    state.pendingStatus = "idle";
    state.result = analyzedEvidence;
    state.view = "result";
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

function saveEvidence(result) {
  state.evidences = [result, ...state.evidences];
  saveSessionEvidences(state.evidences);
}

function render() {
  if (state.view === "home") {
    appRoot.innerHTML = landingPage();
    return;
  }

  if (state.view === "dashboard") {
    appRoot.innerHTML = dashboardPage(state);
    return;
  }

  if (state.view === "loading-evidence") {
    appRoot.innerHTML = evidenceLoadingPage(state);
    return;
  }

  if (state.view === "error") {
    appRoot.innerHTML = evidenceErrorPage(state.error?.message || "Erro inesperado.");
    return;
  }

  appRoot.innerHTML = evidenceResultPage(state);
}
