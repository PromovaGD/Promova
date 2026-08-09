import { appPage, pageHero } from "../components/layout.mjs";
import { githubImportPanel } from "../features/github-import/github-import-panel.mjs";
import { escapeHtml } from "../utils/html.mjs";
import { LEVELS, badgeClass, confidenceLabel, formatTimestamp, sourceBadgeClass } from "../utils/format.mjs";

export function dashboardPage(state) {
  return appPage(
    dashboardContent(state, {
      title: "Suas evidências",
      subtitle: "Painel de análises",
      copy: "Acompanhe sua caixa de entrada de evidências pendentes e o histórico de análises da sua conta.",
    }) + githubImportPanel(state.githubImport),
    { user: state.user, mode: "app" },
  );
}

export function dashboardContent(state, hero) {
  return `
    ${pageHero(hero.subtitle, hero.title, hero.copy)}
    ${state.viewingAsAdmin && state.viewedEmployee ? adminContextBanner(state.viewedEmployee) : ""}
    ${dashboardFilters(state)}
    ${liveDashboardPreview(state)}
  `;
}

function adminContextBanner(employee) {
  return `
    <div class="admin-context-banner">
      <strong>Visualizando:</strong> ${escapeHtml(employee.name)} &middot; ${escapeHtml(employee.email)}
    </div>
  `;
}

function dashboardFilters(state) {
  const filterScope = state.viewingAsAdmin ? "admin" : "user";
  const filters = filterScope === "admin" ? state.adminFilters || {} : state.dashboardFilters || {};

  return `
    <div class="dashboard-toolbar">
      <div class="dashboard-filters">
        <label class="field compact">
          <span>De</span>
          <input type="date" data-filter-scope="${filterScope}" data-filter-field="dateFrom" value="${escapeHtml(filters.dateFrom || "")}" />
        </label>
        <label class="field compact">
          <span>At&eacute;</span>
          <input type="date" data-filter-scope="${filterScope}" data-filter-field="dateTo" value="${escapeHtml(filters.dateTo || "")}" />
        </label>
        <button class="button secondary" type="button" data-action="apply-filters" data-filter-scope="${filterScope}">Filtrar</button>
        <button class="button ghost" type="button" data-action="clear-filters" data-filter-scope="${filterScope}">Limpar filtros</button>
      </div>
      ${
        state.viewingAsAdmin
          ? ""
          : `
      <div class="dashboard-toolbar-actions">
        <button class="button ghost danger-text" type="button" data-action="clear-analyses" data-filter-scope="${filterScope}">Limpar hist&oacute;rico</button>
      </div>`
      }
    </div>
  `;
}

function liveDashboardPreview(state) {
  const filters = state.viewingAsAdmin ? state.adminFilters || {} : state.dashboardFilters || {};
  const evidences = filterEvidencesLocally(state.evidences, filters);
  const counts = LEVELS.reduce((accumulator, level) => {
    accumulator[level] = evidences.filter((item) => item.impactLevel === level).length;
    return accumulator;
  }, {});
  const latest = evidences[0];
  const sources = [...new Set(evidences.map((item) => item.source))];

  return `
    <div class="dashboard-shell live-dashboard">
      <div class="dashboard-metrics">
        <div class="metric-card blue">
          <span class="metric-label">Evid&ecirc;ncias</span>
          <strong class="metric-value">${evidences.length}</strong>
          <span class="metric-sub">${state.viewingAsAdmin ? "Do funcion&aacute;rio" : "Salvas na conta"}</span>
        </div>
        <div class="metric-card green">
          <span class="metric-label">&Uacute;ltima classifica&ccedil;&atilde;o</span>
          <strong class="metric-value">${latest ? escapeHtml(latest.impactLevel) : "&mdash;"}</strong>
          <span class="metric-sub">${latest ? escapeHtml(formatTimestamp(latest.createdAt)) : "Nenhuma ainda"}</span>
        </div>
        <div class="metric-card purple">
          <span class="metric-label">Ferramentas</span>
          <strong class="metric-value">${sources.length}</strong>
          <span class="metric-sub">${sources.length ? escapeHtml(sources.join(", ")) : "Sem fontes"}</span>
        </div>
      </div>
      ${pendingInbox(state)}
      ${evidences.length ? evidenceFeed(evidences) : pendingEvidence(state)}
    </div>
  `;
}

function filterEvidencesLocally(evidences, filters = {}) {
  if (!filters?.dateFrom && !filters?.dateTo) {
    return evidences;
  }

  return evidences.filter((item) => {
    const createdAt = new Date(item.createdAt).getTime();
    if (filters.dateFrom) {
      const from = new Date(`${filters.dateFrom}T00:00:00`).getTime();
      if (createdAt < from) {
        return false;
      }
    }

    if (filters.dateTo) {
      const to = new Date(`${filters.dateTo}T23:59:59.999`).getTime();
      if (createdAt > to) {
        return false;
      }
    }

    return true;
  });
}

function evidenceFeed(evidences) {
  return `
    <div class="dashboard-feed">
      ${evidences
        .map(
          (item) => `
            <button class="feed-item feed-item-button" type="button" data-action="open-evidence-detail" data-evidence-id="${escapeHtml(item.id)}">
              <span class="${badgeClass(item.impactLevel)}">${escapeHtml(item.impactLevel)}</span>
              <div class="feed-copy">
                <div class="feed-meta-row">
                  <span class="source-badge ${sourceBadgeClass(item.source)}">${escapeHtml(item.source)}</span>
                  <span class="confidence-pill">${escapeHtml(confidenceLabel(item.confidence))}</span>
                </div>
                <strong class="feed-title">${escapeHtml(item.evidence)}</strong>
                <p class="feed-sub">${escapeHtml(item.sourceMeta)}</p>
                <p class="feed-detail">Atual: ${escapeHtml(item.currentLevel)} &middot; Alvo: ${escapeHtml(item.targetLevel)} &middot; ${escapeHtml(truncate(item.justification, 120))}</p>
                ${
                  item.competencies?.length
                    ? `<div class="feed-tags">${item.competencies
                        .slice(0, 3)
                        .map((tag) => `<span class="tag-mini">${escapeHtml(tag)}</span>`)
                        .join("")}</div>`
                    : ""
                }
              </div>
              <span class="feed-time">${escapeHtml(formatTimestamp(item.createdAt))}</span>
            </button>
          `,
        )
        .join("")}
    </div>
  `;
}

function pendingInbox(state) {
  if (state.viewingAsAdmin || !state.pendingEvidences?.length) {
    return "";
  }

  return `
    <section class="dashboard-feed evidence-inbox">
      <div class="section-heading">
        <h2 class="section-title">Caixa de entrada</h2>
        <p class="section-lead">Evid&ecirc;ncias persistidas que aguardam sua revis&atilde;o.</p>
      </div>
      ${state.pendingEvidences.map(pendingInboxItem).join("")}
    </section>
  `;
}

function pendingInboxItem(item) {
  return `
    <article class="feed-item pending-feed-item">
      <span class="badge info">Pendente</span>
      <div class="feed-copy">
        <div class="feed-meta-row">
          <span class="source-badge ${sourceBadgeClass(item.source)}">${escapeHtml(item.source)}</span>
          <span class="confidence-pill">Aguardando an&aacute;lise</span>
        </div>
        <strong class="feed-title">${escapeHtml(item.sourceMeta)}</strong>
        <p class="feed-sub">${escapeHtml(item.evidence)}</p>
        <p class="feed-detail">Capturada em ${escapeHtml(formatTimestamp(item.capturedAt))}</p>
      </div>
      <div class="feed-actions">
        <button class="button primary compact" type="button" data-action="open-pending-evidence" data-evidence-id="${escapeHtml(item.id)}">Revisar</button>
        <button class="button ghost compact" type="button" data-action="dismiss-evidence" data-evidence-id="${escapeHtml(item.id)}">Dispensar</button>
      </div>
    </article>
  `;
}

function pendingEvidence(state) {
  if (state.viewingAsAdmin) {
    return emptyPanel("Este funcion&aacute;rio ainda n&atilde;o possui evid&ecirc;ncias no per&iacute;odo selecionado.");
  }

  if (state.pendingStatus === "loading") {
    return emptyPanel("Buscando evid&ecirc;ncias pendentes...");
  }

  if (state.pendingStatus === "error") {
    return emptyPanel("N&atilde;o foi poss&iacute;vel buscar as evid&ecirc;ncias pendentes no backend.", "Tentar novamente", "reload-pending");
  }

  return emptyPanel("Nenhuma an&aacute;lise salva no per&iacute;odo selecionado.", "Atualizar", "reload-pending");
}

function emptyPanel(message, actionLabel, action) {
  return `
    <div class="empty-state dashboard-empty">
      <p>${message}</p>
      ${action ? `<button class="button primary" type="button" data-action="${action}">${actionLabel}</button>` : ""}
    </div>
  `;
}

function truncate(value, maxLength) {
  if (!value || value.length <= maxLength) {
    return value || "";
  }

  return `${value.slice(0, maxLength).trim()}...`;
}
