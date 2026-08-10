export const GITHUB_IMPORT_FIELDS = new Set(["repoSlug", "authorLogin", "usernameHint", "pullNumber"]);

export function createGithubImportState() {
  return {
    repoSlug: "",
    authorLogin: "",
    usernameHint: "",
    pullNumber: "",
    status: "idle",
    error: "",
    pullRequests: [],
    settingsStatus: "idle",
    settingsError: "",
    settingsSaving: false,
    testStatus: "idle",
    testMessage: "",
    syncStatus: "idle",
    syncError: "",
    syncResult: null,
    lastSyncAt: null,
    lastSyncOutcome: "NOT_CONFIGURED",
  };
}

export function updateGithubImportField(state, fieldName, value) {
  if (!GITHUB_IMPORT_FIELDS.has(fieldName)) {
    return;
  }

  state[fieldName] = value;
  if (fieldName === "authorLogin") {
    state.usernameHint = value;
  }
  state.error = "";
  state.settingsError = "";
}

export function chooseGithubPullRequest(state, pullNumber) {
  state.pullNumber = pullNumber || "";
  state.error = "";
}

export function setGithubImportLoading(state) {
  state.status = "loading";
  state.error = "";
}

export function setGithubImportIdle(state) {
  state.status = "idle";
  state.error = "";
}

export function setGithubImportResults(state, pullRequests) {
  state.status = "ready";
  state.pullRequests = Array.isArray(pullRequests) ? pullRequests : [];
}

export function setGithubImportError(state, error, fallbackMessage) {
  state.status = "error";
  state.error = error?.message || fallbackMessage;
  state.pullRequests = [];
}

export function setGithubSettingsLoading(state) {
  state.settingsStatus = "loading";
  state.settingsError = "";
}

export function applyGithubSettings(state, settings) {
  state.repoSlug = settings?.repoSlug || "";
  state.authorLogin = settings?.authorLogin || "";
  state.usernameHint = state.authorLogin;
  state.lastSyncAt = settings?.lastSyncAt || null;
  state.lastSyncOutcome = settings?.lastSyncOutcome || "NOT_CONFIGURED";
  state.settingsStatus = "ready";
  state.settingsError = "";
}

export function setGithubSettingsError(state, error, fallbackMessage) {
  state.settingsStatus = "error";
  state.settingsError = error?.message || fallbackMessage;
}

export function setGithubSettingsSaving(state, saving) {
  state.settingsSaving = saving;
  if (saving) {
    state.settingsError = "";
  }
}

export function setGithubConnectionTesting(state) {
  state.testStatus = "loading";
  state.testMessage = "";
  state.settingsError = "";
}

export function setGithubConnectionTestResult(state, result) {
  state.testStatus = result?.ok ? "success" : "error";
  state.testMessage = result?.message || "";
}

export function setGithubConnectionTestError(state, error, fallbackMessage) {
  state.testStatus = "error";
  state.testMessage = error?.message || fallbackMessage;
}

export function setGithubSyncLoading(state) {
  state.syncStatus = "loading";
  state.syncError = "";
  state.syncResult = null;
}

export function setGithubSyncResult(state, result) {
  state.syncStatus = "ready";
  state.syncResult = result || null;
  state.lastSyncAt = result?.lastSyncAt || state.lastSyncAt;
  state.lastSyncOutcome = result?.lastSyncOutcome || state.lastSyncOutcome;
}

export function setGithubSyncError(state, error, fallbackMessage) {
  state.syncStatus = "error";
  state.syncError = error?.message || fallbackMessage;
}

export function githubImportRequest(state) {
  return {
    repoSlug: state.repoSlug,
    usernameHint: state.authorLogin || state.usernameHint,
    authorLogin: state.authorLogin,
    pullNumber: state.pullNumber,
  };
}
