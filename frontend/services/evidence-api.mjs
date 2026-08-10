import { parseGithubRepoSlug } from "../utils/github.mjs";
import { apiGet, apiPost } from "./http.mjs";

export function fetchEvidences(params = {}) {
  return apiGet("/evidences", params, { auth: true });
}

export function fetchEvidence(evidenceId) {
  return apiGet(`/evidences/${encodeURIComponent(evidenceId)}`, null, { auth: true });
}

export function dismissEvidence(evidenceId) {
  return apiPost(`/evidences/${encodeURIComponent(evidenceId)}/dismiss`, {}, { auth: true });
}

export function captureEvidenceFromGithubPullRequest({ repoSlug, pullNumber, usernameHint }) {
  const { slug } = parseGithubRepoSlug(repoSlug);

  return apiPost(
    "/evidences/github/pull-request",
    { repo: slug, pullNumber: Number(pullNumber), usernameHint },
    { auth: true },
  );
}
