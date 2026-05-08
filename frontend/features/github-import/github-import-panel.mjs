import { escapeHtml } from "../../utils/html.mjs";

export function githubImportPanel(githubImport) {
  return `
    <section class="form-card github-import-panel">
      <div class="panel-heading">
        <div>
          <span class="eyebrow">GitHub conectado</span>
          <h2>Nova evidência via Pull Request</h2>
        </div>
        <span class="sync-status">API Java</span>
      </div>
      <div class="github-import-grid">
        ${githubField("Repositório", "repoSlug", githubImport.repoSlug, "owner/repo")}
        ${githubField("Autor ou contexto", "usernameHint", githubImport.usernameHint, "usuário do GitHub")}
        ${githubField("PR", "pullNumber", githubImport.pullNumber, "número", "number")}
      </div>
      <div class="form-actions">
        <button class="button secondary" type="button" data-action="search-github-prs" ${isLoading(githubImport) ? "disabled" : ""}>
          ${isLoading(githubImport) ? "Buscando..." : "Buscar PRs"}
        </button>
        <button class="button primary" type="button" data-action="import-github-pr" ${isLoading(githubImport) ? "disabled" : ""}>
          Importar como evidência
        </button>
      </div>
      ${githubImport.error ? `<p class="helper error-text">${escapeHtml(githubImport.error)}</p>` : ""}
      ${githubPullList(githubImport)}
    </section>
  `;
}

function githubField(label, fieldName, value, placeholder, type = "text") {
  const min = type === "number" ? ` min="1"` : "";

  return `
    <label class="field">
      <span>${escapeHtml(label)}</span>
      <input data-github-import-field="${fieldName}" type="${type}"${min} value="${escapeHtml(value)}" placeholder="${escapeHtml(placeholder)}" />
    </label>
  `;
}

function githubPullList(githubImport) {
  if (!githubImport.pullRequests.length) {
    return `<p class="helper github-empty">Busque um repositório para ver PRs recentes ou informe o número diretamente.</p>`;
  }

  return `
    <div class="github-pr-list">
      ${githubImport.pullRequests.map(githubPullRequestItem).join("")}
    </div>
  `;
}

function githubPullRequestItem(pullRequest) {
  return `
    <article class="github-pr-item">
      <div>
        <strong>#${escapeHtml(pullRequest.number)} ${escapeHtml(pullRequest.title)}</strong>
        <p>${escapeHtml(pullRequest.author_login || "unknown")} · ${escapeHtml(pullRequest.state || "unknown")}</p>
      </div>
      <button class="button secondary compact" type="button" data-action="use-github-pr" data-pull-number="${escapeHtml(pullRequest.number)}">Usar</button>
    </article>
  `;
}

function isLoading(githubImport) {
  return githubImport.status === "loading";
}
