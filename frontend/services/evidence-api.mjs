import { parseGithubRepoSlug } from "../utils/github.mjs";
import { apiGet } from "./http.mjs";

export function fetchNextCapturedEvidence(cursor) {
  return apiGet("/evidences/next", { cursor });
}

export function captureEvidenceFromGithubPullRequest({ repoSlug, pullNumber, usernameHint }) {
  const { slug } = parseGithubRepoSlug(repoSlug);

  return apiGet("/evidences/github/pull-request", {
    repo: slug,
    pullNumber,
    usernameHint,
  });
}
