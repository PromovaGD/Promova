import { sourceCard } from "../components/cards.mjs";
import { appPage, pageHero } from "../components/layout.mjs";
import { githubImportPanel } from "../features/github-import/github-import-panel.mjs";
import { escapeHtml } from "../utils/html.mjs";
import { LEVELS, badgeClass, formatTimestamp } from "../utils/format.mjs";

export function dashboardPage(state) {
  return appPage(`
    ${pageHero(
      "Painel da sessão",
      "Evidências capturadas",
      "Acompanhe sinais detectados automaticamente nas ferramentas conectadas e abra novas evidências conforme elas chegam.",
    )}
    ${liveDashboardPreview(state)}
    ${githubImportPanel(state.githubImport)}
  `);
}

function liveDashboardPreview(state) {
  const counts = LEVELS.reduce((accumulator, level) => {
    accumulator[level] = state.evidences.filter((item) => item.impactLevel === level).length;
    return accumulator;
  }, {});
  const latest = state.evidences[0];

  return `
    <div class="dashboard-shell live-dashboard">
      <div class="dashboard-metrics">
        <div class="metric-card blue">
          <span class="metric-label">Evidências salvas</span>
          <strong class="metric-value">${state.evidences.length}</strong>
          <span class="metric-sub">Nesta sessão</span>
        </div>
        <div class="metric-card green">
          <span class="metric-label">Última classificação</span>
          <strong class="metric-value">${latest ? escapeHtml(latest.impactLevel) : "—"}</strong>
          <span class="metric-sub">${latest ? escapeHtml(formatTimestamp(latest.createdAt)) : "Nenhuma ainda"}</span>
        </div>
        <div class="metric-card purple">
          <span class="metric-label">Níveis L4+</span>
          <strong class="metric-value">${counts.L4 + counts.L5}</strong>
          <span class="metric-sub">Impacto mais forte</span>
        </div>
      </div>
      ${state.evidences.length ? evidenceFeed(state.evidences) : pendingEvidence(state)}
    </div>
  `;
}

function evidenceFeed(evidences) {
  return `
    <div class="dashboard-feed">
      ${evidences
        .map(
          (item) => `
            <article class="feed-item">
              <span class="${badgeClass(item.impactLevel)}">${escapeHtml(item.impactLevel)}</span>
              <div>
                <strong class="feed-title">${escapeHtml(item.evidence)}</strong>
                <p class="feed-sub">Atual: ${escapeHtml(item.currentLevel)} · Alvo: ${escapeHtml(item.targetLevel)}</p>
              </div>
              <span class="feed-time">${escapeHtml(formatTimestamp(item.createdAt))}</span>
            </article>
          `,
        )
        .join("")}
    </div>
  `;
}

function pendingEvidence(state) {
  if (state.pendingStatus === "loading") {
    return emptyPanel("Buscando a próxima evidência capturada...");
  }

  if (state.pendingStatus === "error") {
    return emptyPanel("Não foi possível buscar a próxima evidência no backend.", "Tentar novamente", "reload-pending");
  }

  if (!state.pendingEvidence) {
    return emptyPanel("Nenhuma evidência nova disponível agora.", "Atualizar", "reload-pending");
  }

  return `
    <div class="empty-state dashboard-empty">
      <div class="new-evidence-alert">
        ${sourceCard(state.pendingEvidence, "Pronta para ver")}
        <p>${escapeHtml(state.pendingEvidence.evidence)}</p>
        <button class="button primary" type="button" data-action="open-form">Ver evidência</button>
      </div>
    </div>
  `;
}

function emptyPanel(message, actionLabel, action) {
  return `
    <div class="empty-state dashboard-empty">
      <p>${escapeHtml(message)}</p>
      ${action ? `<button class="button primary" type="button" data-action="${action}">${escapeHtml(actionLabel)}</button>` : ""}
    </div>
  `;
}
