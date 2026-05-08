export function parseGithubRepoSlug(value) {
  const normalized = String(value || "").trim().replace(/^https:\/\/github\.com\//i, "").replace(/\/+$/g, "");
  const [owner, repo] = normalized.split("/");

  if (!owner || !repo || normalized.split("/").length !== 2) {
    throw new Error("Use o formato owner/repo do GitHub.");
  }

  return { owner, repo, slug: `${owner}/${repo}` };
}
