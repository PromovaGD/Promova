export const GITHUB_IMPORT_FIELDS = new Set(["repoSlug", "usernameHint", "pullNumber"]);

export function createGithubImportState() {
  return {
    repoSlug: "",
    usernameHint: "",
    pullNumber: "",
    status: "idle",
    error: "",
    pullRequests: [],
  };
}

export function updateGithubImportField(state, fieldName, value) {
  if (!GITHUB_IMPORT_FIELDS.has(fieldName)) {
    return;
  }

  state[fieldName] = value;
  state.error = "";
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

export function githubImportRequest(state) {
  return {
    repoSlug: state.repoSlug,
    usernameHint: state.usernameHint,
    pullNumber: state.pullNumber,
  };
}
