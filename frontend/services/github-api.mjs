import { parseGithubRepoSlug } from "../utils/github.mjs";
import { apiGet, apiPost, apiPut } from "./http.mjs";

const DEFAULT_PAGE_SIZE = 8;

export function fetchGithubSettings() {
  return apiGet("/api/github/settings", null, { auth: true });
}

export function saveGithubSettings({ repoSlug, authorLogin }) {
  return apiPut(
    "/api/github/settings",
    { repoSlug: repoSlug?.trim(), authorLogin: authorLogin?.trim() },
    { auth: true },
  );
}

export function testGithubSettings() {
  return apiPost("/api/github/settings/test", {}, { auth: true });
}

export function syncGithub() {
  return apiPost("/api/github/sync", {}, { auth: true });
}

export async function findGithubPullRequests({ repoSlug, usernameHint, authorLogin }) {
  const { owner, repo } = parseGithubRepoSlug(repoSlug);
  const path = `/api/github/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/pulls`;
  const author = (authorLogin || usernameHint || "").trim();

  if (author) {
    const response = await apiGet(`${path}/search`, {
      q: `author:${author}`,
      per_page: DEFAULT_PAGE_SIZE,
      page: 1,
    });

    return Array.isArray(response.items) ? response.items : [];
  }

  return apiGet(path, {
    state: "all",
    per_page: DEFAULT_PAGE_SIZE,
    page: 1,
  });
}
