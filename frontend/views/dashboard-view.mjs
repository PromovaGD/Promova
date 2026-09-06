import { appPage, pageHero } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";
import {
  badgeClass,
  confidenceLabel,
  formatCount,
  formatPercentage,
  formatTimestamp,
  sourceBadgeClass,
} from "../utils/format.mjs";

export function dashboardPage(state) {
  return appPage(
    dashboardContent(state, {
      title: "Suas evidências",
      subtitle: "Painel de análises",
      copy: "Acompanhe sua caixa de entrada de evidências pendentes e o histórico de análises da sua conta.",
    }),
    { user: state.user, mode: "app", terminology: state.careerConfiguration?.labels },
  );
}

export function dashboardContent(state, hero) {
  if (state.viewingAsAdmin) {
    return `
      ${pageHero(hero.subtitle, hero.title, hero.copy)}
      ${state.viewedEmployee ? adminContextBanner(state.viewedEmployee) : ""}
      ${dashboardFilters(state)}
      ${liveDashboardPreview(state)}
    `;
  }

  const activeTab = normalizeDashboardTab(state.dashboardTab);

  return `
    ${pageHero(hero.subtitle, hero.title, hero.copy)}
    ${dashboardTabs(activeTab)}
    ${activeTab === "connections" ? "" : dashboardFilters(state)}
    ${dashboardTabPanel(state, activeTab)}
  `;
}

function dashboardTabs(activeTab) {
  const tabs = [
    ["dashboard", "Dashboard"],
    ["framework", "Cobertura do framework"],
    ["criteria", "Critérios"],
    ["connections", "Conexões"],
  ];

  return `
    <nav class="dashboard-tabs" aria-label="Seções do painel de evidências">
      <div class="dashboard-tab-list" role="tablist" aria-label="Visualizações do painel">
        ${tabs
          .map(([id, label]) => {
            const selected = id === activeTab;
            return `
              <button
                class="dashboard-tab ${selected ? "active" : ""}"
                id="dashboard-tab-${id}"
                type="button"
                role="tab"
                aria-selected="${selected}"
                aria-controls="dashboard-panel-${id}"
                tabindex="${selected ? "0" : "-1"}"
                data-action="switch-dashboard-tab"
                data-dashboard-tab="${id}"
              >${label}</button>
            `;
          })
          .join("")}
      </div>
    </nav>
  `;
}

function dashboardTabPanel(state, activeTab) {
  const content =
    activeTab === "connections"
      ? `<section class="form-card"><span class="eyebrow">Conexões</span><h2>GitHub agora fica no seu Perfil</h2><p class="helper">Configure o repositório e o autor, teste o acesso e sincronize PRs em um único lugar.</p><button class="button primary" type="button" data-action="open-profile">Abrir Perfil</button></section>`
      : liveDashboardPreview(state, activeTab);

  return `
    <div
      class="dashboard-tab-panel dashboard-tab-panel-${activeTab}"
      id="dashboard-panel-${activeTab}"
      role="tabpanel"
      aria-labelledby="dashboard-tab-${activeTab}"
      tabindex="0"
    >
      ${content}
    </div>
  `;
}

function normalizeDashboardTab(tab) {
  return ["dashboard", "framework", "criteria", "connections"].includes(tab)
    ? tab
    : "dashboard";
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

function liveDashboardPreview(state, activeTab = "dashboard") {
  const evidences = state.evidences || [];
  const showEvidenceLists = state.viewingAsAdmin || activeTab === "dashboard";

  return `
    <div class="dashboard-shell live-dashboard">
      ${
        state.viewingAsAdmin
          ? `${adminDashboardSummary(evidences)}${recentEvidenceSection(state)}`
          : `${showEvidenceLists ? pendingInbox(state) : ""}${showEvidenceLists ? recentEvidenceSection(state) : ""}${insightsDashboard(state, activeTab)}`
      }
    </div>
  `;
}

function adminDashboardSummary(evidences) {
  const latest = evidences[0];
  const sources = [...new Set(evidences.map((item) => item.source))];

  return `
    <div class="dashboard-metrics">
      <div class="metric-card blue">
        <span class="metric-label">Evid&ecirc;ncias</span>
        <strong class="metric-value">${formatCount(evidences.length)}</strong>
        <span class="metric-sub">Do funcion&aacute;rio</span>
      </div>
      <div class="metric-card green">
        <span class="metric-label">&Uacute;ltima classifica&ccedil;&atilde;o</span>
        <strong class="metric-value">${latest ? escapeHtml(latest.impactLevel) : "&mdash;"}</strong>
        <span class="metric-sub">${latest ? escapeHtml(formatTimestamp(latest.createdAt)) : "Nenhuma ainda"}</span>
      </div>
      <div class="metric-card purple">
        <span class="metric-label">Ferramentas</span>
        <strong class="metric-value">${formatCount(sources.length)}</strong>
        <span class="metric-sub">${sources.length ? escapeHtml(sources.join(", ")) : "Sem fontes"}</span>
      </div>
    </div>
  `;
}

function insightsDashboard(state, activeTab = "dashboard") {
  if (state.insightsStatus === "error") {
    return insightsErrorPanel(state.insightsError);
  }

  if (state.insightsStatus !== "ready" || !state.insights) {
    return insightsLoadingPanel();
  }

  const insights = state.insights;

  if (activeTab === "framework") {
    return criterionCoverageSection(insights);
  }

  if (activeTab === "criteria") {
    return gapsSection(insights.gaps);
  }

  return `
    ${insightOverview(insights)}
    ${insightDistributionSection(
      "Fontes das evid&ecirc;ncias",
      "De quais ferramentas vieram as an&aacute;lises salvas neste per&iacute;odo.",
      insights.sourceDistribution,
    )}
    ${insightDistributionSection(
      "N&iacute;veis estimados",
      "Como as an&aacute;lises salvas foram distribu&iacute;das por n&iacute;vel estimado.",
      insights.estimatedLevelDistribution,
    )}
    ${trendSection(insights.recentTrend)}
  `;
}

function insightOverview(insights) {
  const sourceLabels = (insights.sourceDistribution || []).map((item) => item.label).filter(Boolean);
  const hasEvidence = Number(insights.totalEvidence) > 0;

  return `
    <section class="insights-panel" aria-labelledby="insights-title">
      <div class="insights-heading">
        <div>
          <span class="eyebrow">Vis&atilde;o de carreira</span>
          <h2 id="insights-title">Evid&ecirc;ncias que apoiam sua evolu&ccedil;&atilde;o</h2>
          <p class="section-lead">Resumo server-side das an&aacute;lises salvas e do framework de carreira configurado.</p>
        </div>
        <p class="insights-disclaimer">Este resumo organiza evid&ecirc;ncias salvas; n&atilde;o &eacute; uma decis&atilde;o de promo&ccedil;&atilde;o.</p>
      </div>
      <div class="dashboard-metrics insights-metrics">
        <div class="metric-card blue">
          <span class="metric-label">Evid&ecirc;ncias salvas</span>
          <strong class="metric-value">${formatCount(insights.totalEvidence)}</strong>
          <span class="metric-sub">${hasEvidence ? "No per&iacute;odo selecionado" : "Nenhuma no per&iacute;odo"}</span>
        </div>
        <div class="metric-card green">
          <span class="metric-label">Crit&eacute;rios com apoio</span>
          <strong class="metric-value">${formatCount(insights.criteriaWithEvidence)}<span class="metric-denominator"> / ${formatCount(insights.criteriaCount)}</span></strong>
          <span class="metric-sub">Crit&eacute;rios do framework atual</span>
        </div>
        <div class="metric-card purple">
          <span class="metric-label">Fontes</span>
          <strong class="metric-value">${formatCount(sourceLabels.length)}</strong>
          <span class="metric-sub">${sourceLabels.length ? escapeHtml(sourceLabels.join(", ")) : "Nenhuma fonte"}</span>
        </div>
      </div>
      ${
        hasEvidence
          ? ""
          : `<div class="empty-state insight-empty"><p>Nenhuma an&aacute;lise salva no per&iacute;odo selecionado.</p><p>Sem evid&ecirc;ncia de apoio n&atilde;o significa evid&ecirc;ncia negativa; significa apenas que ainda n&atilde;o h&aacute; um registro salvo correspondente.</p></div>`
      }
    </section>
  `;
}

function insightDistributionSection(title, copy, items = []) {
  return `
    <section class="insights-section" aria-labelledby="${slugify(title)}">
      <div class="section-heading">
        <h3 class="section-title" id="${slugify(title)}">${title}</h3>
        <p class="section-lead">${copy}</p>
      </div>
      ${
        items.length
          ? `<div class="insight-bars">${items.map(insightBar).join("")}</div>`
          : `<div class="insight-inline-empty">Nenhum dado salvo para este per&iacute;odo.</div>`
      }
    </section>
  `;
}

function insightBar(item) {
  const label = item.label || "Sem r&oacute;tulo";
  const count = Number(item.count) || 0;
  const percentage = boundedPercentage(item.percentage);

  return `
    <div class="insight-bar-row">
      <div class="insight-bar-label">
        <span>${escapeHtml(label)}</span>
        <strong>${formatCount(count)}</strong>
      </div>
      <div class="insight-bar-track" role="img" aria-label="${escapeHtml(label)}: ${formatCount(count)} evid&ecirc;ncia${count === 1 ? "" : "s"} (${formatPercentage(percentage)})">
        <span class="insight-bar-fill" style="--insight-bar-width: ${percentage}%"></span>
      </div>
      <p class="insight-bar-text">${escapeHtml(label)}: ${formatCount(count)} evid&ecirc;ncia${count === 1 ? "" : "s"} (${formatPercentage(percentage)}).</p>
    </div>
  `;
}

function criterionCoverageSection(insights) {
  const coverage = insights.criterionCoverage || [];

  return `
    <section class="insights-section" aria-labelledby="criterion-coverage-title">
      <div class="section-heading">
        <h3 class="section-title" id="criterion-coverage-title">Cobertura do framework</h3>
        <p class="section-lead">Os crit&eacute;rios est&atilde;o agrupados por n&iacute;vel. Abra um grupo para revisar sua cobertura e acessar as evid&ecirc;ncias relacionadas.</p>
      </div>
      ${
        coverage.length
          ? `<div class="criterion-level-groups">${groupCriteriaByLevel(coverage).map(criterionLevelGroup).join("")}</div>`
          : `<div class="insight-inline-empty">O framework atual n&atilde;o possui crit&eacute;rios configurados.</div>`
      }
    </section>
  `;
}

function groupCriteriaByLevel(coverage) {
  const groups = [];
  const groupsByLevel = new Map();

  coverage.forEach((item) => {
    const level = item.level || "Nível não informado";
    let group = groupsByLevel.get(level);

    if (!group) {
      group = { level, levelTitle: item.levelTitle || "", items: [] };
      groupsByLevel.set(level, group);
      groups.push(group);
    }

    group.items.push(item);
  });

  return groups;
}

function criterionLevelGroup(group, index) {
  const supportedCount = group.items.filter((item) => item.status === "SUPPORTED").length;
  const groupId = `criterion-level-${slugify(group.level)}`;
  const criteriaLabel = `${formatCount(group.items.length)} crit&eacute;rio${group.items.length === 1 ? "" : "s"}`;
  const supportedLabel = `${formatCount(supportedCount)} com evid&ecirc;ncia`;

  return `
    <details class="criterion-level-group" ${index === 0 ? "open" : ""}>
      <summary aria-controls="${groupId}-panel">
        <span class="criterion-level-group-title">
          <span class="criterion-level">${escapeHtml(group.level)}</span>
          <strong>${escapeHtml(group.levelTitle || group.level)}</strong>
        </span>
        <span class="criterion-level-group-meta">${criteriaLabel} &middot; ${supportedLabel}</span>
      </summary>
      <div class="criterion-grid" id="${groupId}-panel">
        ${group.items.map(criterionCard).join("")}
      </div>
    </details>
  `;
}

function criterionCard(item) {
  const supported = item.status === "SUPPORTED";
  const evidence = Array.isArray(item.supportingEvidence) ? item.supportingEvidence : [];
  const criterionId = slugify(`${item.level || "level"}-${item.criterion || "criterion"}`);

  return `
    <article class="criterion-card ${supported ? "supported" : "missing"}" aria-labelledby="${criterionId}">
      <div class="criterion-card-heading">
        <div>
          <span class="criterion-level">${escapeHtml(item.level || "N&iacute;vel")}${item.levelTitle ? ` &middot; ${escapeHtml(item.levelTitle)}` : ""}</span>
          <h4 id="${criterionId}">${escapeHtml(item.criterion || "Crit&eacute;rio sem nome")}</h4>
        </div>
        <span class="criterion-status">${supported ? "Com evid&ecirc;ncia" : "Sem evid&ecirc;ncia"}</span>
      </div>
      <details class="criterion-description">
        <summary>Ver descri&ccedil;&atilde;o</summary>
        <p>${escapeHtml(item.description || "Descri&ccedil;&atilde;o n&atilde;o configurada.")}</p>
      </details>
      ${
        supported
          ? `<p class="criterion-support-copy">${formatCount(item.evidenceCount)} evid&ecirc;ncia${Number(item.evidenceCount) === 1 ? "" : "s"} de apoio no per&iacute;odo.</p>
             <ul class="criterion-evidence-list">${evidence.map(criterionEvidenceItem).join("")}</ul>`
          : `<p class="criterion-missing-copy">Sem evid&ecirc;ncia de apoio salva neste per&iacute;odo. Isso n&atilde;o &eacute; uma avalia&ccedil;&atilde;o negativa.</p>`
      }
    </article>
  `;
}

function criterionEvidenceItem(item) {
  const title = item.title || "Evid&ecirc;ncia salva";
  const timestamp = item.createdAt ? formatTimestamp(item.createdAt) : "Data indispon&iacute;vel";
  const evidenceId = Number(item.evidenceId);
  const canDrillDown = Number.isSafeInteger(evidenceId) && evidenceId > 0;

  if (!canDrillDown) {
    return `
      <li>
        <div class="criterion-evidence-reference legacy">
          <strong>${escapeHtml(title)}</strong>
          <span>${escapeHtml(item.source || "Fonte desconhecida")} &middot; ${escapeHtml(timestamp)}</span>
          <small>Detalhe indispon&iacute;vel para refer&ecirc;ncias hist&oacute;ricas.</small>
        </div>
      </li>
    `;
  }

  return `
    <li>
      <button class="criterion-evidence-link" type="button" data-action="open-evidence-detail" data-evidence-id="${escapeHtml(String(evidenceId))}" data-analysis-id="${escapeHtml(item.id || "")}">
        <strong>${escapeHtml(title)}</strong>
        <span>${escapeHtml(item.source || "Fonte desconhecida")} &middot; ${escapeHtml(timestamp)}</span>
      </button>
    </li>
  `;
}

function trendSection(items = []) {
  return `
    <section class="insights-section" aria-labelledby="trend-title">
      <div class="section-heading">
        <h3 class="section-title" id="trend-title">Tend&ecirc;ncia recente</h3>
        <p class="section-lead">Distribui&ccedil;&atilde;o server-side das an&aacute;lises salvas dentro do per&iacute;odo selecionado.</p>
      </div>
      ${
        items.length
          ? `<div class="insight-bars trend-bars">${items.map(insightBar).join("")}</div>`
          : `<div class="insight-inline-empty">Ainda n&atilde;o h&aacute; an&aacute;lises salvas para formar uma tend&ecirc;ncia.</div>`
      }
    </section>
  `;
}

function gapsSection(gaps = []) {
  return `
    <section class="insights-section insights-gaps" aria-labelledby="gaps-title">
      <div class="section-heading">
        <h3 class="section-title" id="gaps-title">Crit&eacute;rios sem evid&ecirc;ncia de apoio</h3>
        <p class="section-lead">Estes itens est&atilde;o no framework atual, mas n&atilde;o tiveram uma compet&ecirc;ncia correspondente nas an&aacute;lises salvas do per&iacute;odo.</p>
      </div>
      ${
        gaps.length
          ? `<ul class="insight-gap-list">${gaps.map(gapItem).join("")}</ul>`
          : `<div class="insight-inline-empty">N&atilde;o h&aacute; crit&eacute;rios sem apoio no per&iacute;odo analisado.</div>`
      }
    </section>
  `;
}

function gapItem(item) {
  return `
    <li>
      <strong>${escapeHtml(item.level || "N&iacute;vel")} &middot; ${escapeHtml(item.criterion || "Crit&eacute;rio")}</strong>
      <span>Sem evid&ecirc;ncia salva correspondente; isso n&atilde;o indica evid&ecirc;ncia negativa.</span>
    </li>
  `;
}

function insightsLoadingPanel() {
  return `
    <section class="insights-panel" aria-live="polite">
      <div class="loading-strip"><span class="loading-dot"></span><span>Carregando a vis&atilde;o de evid&ecirc;ncias...</span></div>
    </section>
  `;
}

function insightsErrorPanel(error) {
  return `
    <section class="insights-panel" aria-live="assertive">
      <div class="empty-state insight-error">
        <p>N&atilde;o foi poss&iacute;vel carregar os insights deste per&iacute;odo.</p>
        ${error ? `<p class="error-text">${escapeHtml(error)}</p>` : ""}
        <button class="button primary" type="button" data-action="reload-insights">Tentar novamente</button>
      </div>
    </section>
  `;
}

function boundedPercentage(value) {
  const numericValue = Number(value) || 0;
  return Math.max(0, Math.min(100, Math.round(numericValue)));
}

function slugify(value) {
  return String(value || "section")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "") || "section";
}

function evidenceFeed(evidences, state) {
  return `
    <div class="dashboard-feed">
      ${evidences
        .map((item) => {
          const expanded = String(state.expandedEvidenceId) === String(item.id);
          const panelId = `analysis-evidence-${escapeHtml(item.id)}`;
          return `
            <article class="feed-item expandable-evidence ${expanded ? "expanded" : ""}">
            <button class="feed-item-button evidence-expansion-control" type="button" data-action="toggle-evidence" data-evidence-id="${escapeHtml(item.id)}" aria-expanded="${expanded}" aria-controls="${panelId}">
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
            <div class="evidence-expanded-content" id="${panelId}" ${expanded ? "" : "hidden"}>
              <h3>Detalhes completos</h3><p>${escapeHtml(item.evidence)}</p>
              ${item.userObservation ? `<h4>Observação</h4><p>${escapeHtml(item.userObservation)}</p>` : ""}
              <h4>Análise</h4><p>${escapeHtml(item.justification)}</p>
              <button class="button secondary compact" type="button" data-action="open-evidence-detail" data-analysis-id="${escapeHtml(item.id)}" data-saved-analysis-id="${escapeHtml(item.analysisId ?? "")}">Abrir análise completa</button>
            </div></article>`;
        })
        .join("")}
    </div>
  `;
}

function recentEvidenceSection(state) {
  const evidences = state.evidences || [];
  const count = evidences.length;
  const countLabel = `${formatCount(count)} evid&ecirc;ncia${count === 1 ? "" : "s"} salva${count === 1 ? "" : "s"}`;
  const emptyMessage = state.viewingAsAdmin
    ? "Este funcion&aacute;rio ainda n&atilde;o possui evid&ecirc;ncias analisadas no per&iacute;odo selecionado."
    : "Ainda n&atilde;o h&aacute; evid&ecirc;ncias salvas no per&iacute;odo selecionado.";

  return `
    <section class="recent-evidence-section" id="recent-evidence" aria-labelledby="recent-evidence-title">
      <div class="recent-evidence-heading">
        <div>
          <span class="eyebrow">Hist&oacute;rico salvo</span>
          <h2 class="section-title" id="recent-evidence-title">Evid&ecirc;ncias recentes</h2>
          <p class="section-lead">Abra uma an&aacute;lise para revisar a classifica&ccedil;&atilde;o, as compet&ecirc;ncias e o hist&oacute;rico de revis&atilde;o.</p>
        </div>
        <span class="recent-evidence-count" aria-label="${countLabel}">${countLabel}</span>
      </div>
      ${count ? evidenceFeed(evidences, state) : recentEvidenceEmpty(emptyMessage, state.viewingAsAdmin)}
    </section>
  `;
}

function recentEvidenceEmpty(message, isAdmin) {
  return `
    <div class="empty-state recent-evidence-empty">
      <p>${message}</p>
      ${
        isAdmin
          ? ""
          : `<button class="button primary" type="button" data-action="open-connections">Abrir Conex&otilde;es e importar</button>`
      }
    </div>
  `;
}

function pendingInbox(state) {
  const items = [...(state.pendingEvidences || []), ...(state.dismissedEvidences || [])];
  if (state.viewingAsAdmin || !items.length) {
    return "";
  }

  return `
    <section class="dashboard-feed evidence-inbox">
      <div class="section-heading">
        <h2 class="section-title">Caixa de entrada</h2>
        <p class="section-lead">Evid&ecirc;ncias persistidas, pendentes ou dispensadas.</p>
      </div>
      ${items.map((item) => pendingInboxItem(item, state)).join("")}
    </section>
  `;
}

function pendingInboxItem(item, state) {
  const pending = item.status !== "DISMISSED";
  const expanded = String(state.expandedEvidenceId) === String(item.id);
  const panelId = `pending-evidence-${escapeHtml(item.id)}`;
  return `
    <article class="feed-item pending-feed-item expandable-evidence ${expanded ? "expanded" : ""}">
      <button class="evidence-expansion-control" type="button" data-action="toggle-evidence" data-evidence-id="${escapeHtml(item.id)}" aria-expanded="${expanded}" aria-controls="${panelId}">
      <span class="badge info">${pending ? "Pendente" : "Dispensada"}</span>
      <div class="feed-copy">
        <div class="feed-meta-row">
          <span class="source-badge ${sourceBadgeClass(item.source)}">${escapeHtml(item.source)}</span>
          <span class="confidence-pill">${pending ? "Aguardando an&aacute;lise" : "Sem análise"}</span>
        </div>
        <strong class="feed-title">${escapeHtml(item.sourceMeta)}</strong>
        <p class="feed-sub">${escapeHtml(truncate(item.content, 160))}</p>
        <p class="feed-detail">Ocorrida em ${escapeHtml(formatTimestamp(item.occurredAt))}</p>
      </div>
      </button>
      <div class="evidence-expanded-content" id="${panelId}" ${expanded ? "" : "hidden"}>
        <h3>Conteúdo completo</h3><p>${escapeHtml(item.content)}</p>
        ${item.sourceUrl ? `<p class="integration-url">${escapeHtml(item.sourceUrl)}</p>` : ""}
      ${pending ? `<div class="feed-actions">
        <button class="button primary compact" type="button" data-action="open-pending-evidence" data-evidence-id="${escapeHtml(item.id)}">Revisar</button>
        <button class="button ghost compact" type="button" data-action="dismiss-evidence" data-evidence-id="${escapeHtml(item.id)}">Dispensar</button>
      </div>` : `<p class="subtle">Nenhuma ação disponível para uma evidência dispensada.</p>`}</div>
    </article>
  `;
}

function truncate(value, maxLength) {
  if (!value || value.length <= maxLength) {
    return value || "";
  }

  return `${value.slice(0, maxLength).trim()}...`;
}
