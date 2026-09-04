import { escapeHtml } from "../../utils/html.mjs";
import { formatTimestamp } from "../../utils/format.mjs";

export function githubImportPanel(githubImport, pendingEvidences = []) {
  const busy = isBusy(githubImport);
  const githubPendingEvidences = pendingEvidences.filter(
    (item) => String(item.source || "").toLowerCase() === "github",
  );

  return `
    <section class="form-card github-import-panel" aria-labelledby="github-connection-title">
      <div class="panel-heading">
        <div>
          <span class="eyebrow">GitHub conectado</span>
          <h2 id="github-connection-title">Conexão e sincronização</h2>
        </div>
        <span class="sync-status">Token do servidor</span>
      </div>
      <p class="helper github-token-note" id="github-token-note">
        O Promova usa o token GitHub configurado pela empresa no servidor. Acesso a repositórios privados depende desse token; esta tela não cria autorização OAuth nem armazena tokens pessoais.
      </p>
      <div class="github-settings-grid">
        ${githubField("Repositório", "repoSlug", githubImport.repoSlug, "owner/repo")}
        ${githubField("Login do autor", "authorLogin", githubImport.authorLogin, "usuário do GitHub")}
      </div>
      <div class="form-actions">
        <button class="button secondary" type="button" data-action="save-github-settings" ${busy || githubImport.settingsSaving ? "disabled" : ""}>
          ${githubImport.settingsSaving ? "Salvando..." : "Salvar configuração"}
        </button>
        <button class="button ghost" type="button" data-action="test-github-settings" ${busy ? "disabled" : ""}>
          ${githubImport.testStatus === "loading" ? "Testando..." : "Testar acesso"}
        </button>
        <button class="button primary" type="button" data-action="sync-github" ${busy ? "disabled" : ""}>
          ${githubImport.syncStatus === "loading" ? "Sincronizando..." : "Sincronizar PRs"}
        </button>
      </div>
      ${settingsStatus(githubImport)}
      ${syncSummary(githubImport)}
      ${githubPendingList(githubPendingEvidences)}
      ${manualImport(githubImport, busy)}
    </section>
  `;
}

function githubField(label, fieldName, value, placeholder, type = "text") {
  const inputId = `github-${fieldName}`;
  const autocomplete = fieldName === "authorLogin" ? "username" : "url";
  const required = fieldName === "pullNumber" ? "" : " required";
  const min = fieldName === "pullNumber" ? ' min="1"' : "";
  return `
    <label class="field" for="${inputId}">
      <span>${escapeHtml(label)}</span>
      <input id="${inputId}" data-github-import-field="${fieldName}" type="${type}"${min}${required} value="${escapeHtml(value)}" placeholder="${escapeHtml(placeholder)}" autocomplete="${autocomplete}" aria-describedby="github-token-note" />
    </label>
  `;
}

function settingsStatus(githubImport) {
  if (githubImport.settingsError) {
    return `<p class="helper error-text" role="alert">${escapeHtml(githubImport.settingsError)}</p>`;
  }

  if (githubImport.testMessage) {
    const className = githubImport.testStatus === "error" ? "error-text" : "success-text";
    return `<p class="helper ${className}" role="status">${escapeHtml(githubImport.testMessage)}</p>`;
  }

  if (githubImport.lastSyncAt) {
    return `<p class="helper" role="status">Último sync: ${escapeHtml(safeTimestamp(githubImport.lastSyncAt))} · ${escapeHtml(githubImport.lastSyncOutcome || "desconhecido")}</p>`;
  }

  return `<p class="helper">${githubImport.settingsStatus === "loading" ? "Carregando configuração salva..." : "Nenhuma sincronização executada ainda."}</p>`;
}

function syncSummary(githubImport) {
  if (githubImport.syncError) {
    return `<p class="helper error-text" role="alert">${escapeHtml(githubImport.syncError)}</p>`;
  }

  const result = githubImport.syncResult;
  if (!result) {
    return "";
  }

  return `
    <div class="github-sync-summary" aria-live="polite">
      <strong>Resultado do sync</strong>
      <span class="github-sync-outcome">${escapeHtml(result.lastSyncOutcome || "Concluído")}</span>
      <div class="github-sync-counts">
        <span><strong>${result.discovered ?? 0}</strong> encontrados</span>
        <span><strong>${result.created ?? 0}</strong> novos</span>
        <span><strong>${result.existing ?? 0}</strong> já existentes</span>
        <span><strong>${result.failed ?? 0}</strong> falhas</span>
      </div>
    </div>
  `;
}

function githubPendingList(evidences) {
  if (!evidences.length) {
    return `<p class="helper github-empty">Os PRs importados aparecerão aqui como evidências pendentes para revisão.</p>`;
  }

  return `
    <div class="github-pending-panel">
      <div class="section-heading">
        <h3 class="section-title">Evidências GitHub pendentes</h3>
        <p class="section-lead">Revise ou dispense os itens importados antes da análise.</p>
      </div>
      <div class="github-pr-list">
        ${evidences.map(githubPendingItem).join("")}
      </div>
    </div>
  `;
}

function githubPendingItem(item) {
  return `
    <article class="github-pr-item github-pending-item">
      <div>
        <strong>${escapeHtml(item.sourceMeta || "Evidência GitHub")}</strong>
        <p>${escapeHtml(truncate(item.content, 180))}</p>
      </div>
      <div class="github-pending-actions">
        <button class="button primary compact" type="button" data-action="open-pending-evidence" data-evidence-id="${escapeHtml(item.id)}">Revisar</button>
        <button class="button ghost compact" type="button" data-action="dismiss-evidence" data-evidence-id="${escapeHtml(item.id)}">Dispensar</button>
      </div>
    </article>
  `;
}

function manualImport(githubImport, busy) {
  return `
    <div class="github-manual-import">
      <div class="panel-heading compact-heading">
        <div>
          <span class="eyebrow">Ação manual opcional</span>
          <h3>Importar um PR específico</h3>
        </div>
      </div>
      <p class="helper">Use a configuração salva para buscar ou importar um único PR fora do sync recente.</p>
      <div class="github-import-grid github-manual-grid">
        ${githubField("Número do PR", "pullNumber", githubImport.pullNumber, "número", "number")}
      </div>
      <div class="form-actions">
        <button class="button secondary" type="button" data-action="search-github-prs" ${busy ? "disabled" : ""}>
          ${githubImport.status === "loading" ? "Buscando..." : "Buscar PRs"}
        </button>
        <button class="button ghost" type="button" data-action="import-github-pr" ${busy ? "disabled" : ""}>Importar PR manualmente</button>
      </div>
      ${githubImport.error ? `<p class="helper error-text" role="alert">${escapeHtml(githubImport.error)}</p>` : ""}
      ${githubPullList(githubImport)}
    </div>
  `;
}

function githubPullList(githubImport) {
  if (!githubImport.pullRequests.length) {
    return `<p class="helper github-empty">Nenhum resultado manual carregado.</p>`;
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

function isBusy(githubImport) {
  return (
    githubImport.settingsSaving
    || githubImport.status === "loading"
    || githubImport.testStatus === "loading"
    || githubImport.syncStatus === "loading"
  );
}

function safeTimestamp(value) {
  try {
    return formatTimestamp(value);
  } catch {
    return String(value || "");
  }
}

function truncate(value, maxLength) {
  const normalized = String(value || "");
  return normalized.length <= maxLength ? normalized : `${normalized.slice(0, maxLength).trim()}...`;
}
