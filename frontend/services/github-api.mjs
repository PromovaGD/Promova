import { parseGithubRepoSlug } from "../utils/github.mjs";
import { apiGet } from "./http.mjs";

const DEFAULT_PAGE_SIZE = 8;

export async function findGithubPullRequests({ repoSlug, usernameHint }) {
  const { owner, repo } = parseGithubRepoSlug(repoSlug);
  const path = `/api/github/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/pulls`;

  if (usernameHint && usernameHint.trim()) {
    const response = await apiGet(`${path}/search`, {
      q: `author:${usernameHint.trim()}`,
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
