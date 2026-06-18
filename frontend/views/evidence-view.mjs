import { sourceCard } from "../components/cards.mjs";
import { appPage, pageHero } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";
import { confidenceLabel } from "../utils/format.mjs";

export function evidenceLoadingPage(state) {
  const evidence = state.pendingEvidence;

  return appPage(
    `
    ${pageHero(
      "Nova evidência",
      "Tem uma evidência nova",
      "O Promova detectou um sinal relevante nas ferramentas conectadas e está preparando a leitura de impacto para você abrir direto.",
    )}
    <div class="content-grid">
      <section class="form-card evidence-notice">
        ${evidence ? sourceCard(evidence, "Capturado agora") : ""}
        <div class="evidence-detected-panel">
          <h3>Evidência detectada</h3>
          <div class="evidence-preview">${escapeHtml(evidence?.evidence || "Carregando evidência capturada...")}</div>
        </div>
        <div class="loading-strip">
          <span class="loading-dot"></span>
          <span>Classificando impacto com o backend...</span>
        </div>
      </section>

      <aside class="analysis-side">
        <div class="info-card soft-panel">
          <h3>O que acontece agora</h3>
          <ul class="mini-list">
            <li><span class="mini-dot blue"></span><span>A evidência já chegou das integrações, sem preenchimento manual.</span></li>
            <li><span class="mini-dot green"></span><span>O backend calcula nível, competências e sugestões em segundo plano.</span></li>
            <li><span class="mini-dot purple"></span><span>Você abre o detalhe já com a leitura pronta para revisar.</span></li>
          </ul>
        </div>
        <div class="info-card soft-panel">
          <h3>Resumo da conta</h3>
          <p class="card-copy">Você já possui <strong>${state.evidences.length}</strong> evidência${state.evidences.length === 1 ? "" : "s"} salva${state.evidences.length === 1 ? "" : "s"}.</p>
        </div>
      </aside>
    </div>
  `,
    { user: state.user, mode: "app" },
  );
}

export function evidenceResultPage(state) {
  return appPage(renderAnalysisDetail(state.result, state), { user: state.user, mode: "app" });
}

export function evidenceDetailPage(state) {
  const evidence = findEvidence(state);

  if (!evidence) {
    return appPage(
      `
      ${pageHero("Análise", "Evidência não encontrada", "Volte ao painel e selecione outra evidência.")}
      <div class="empty-state dashboard-empty">
        <button class="button primary" type="button" data-action="back-dashboard">Voltar ao painel</button>
      </div>
    `,
      { user: state.user, mode: "app" },
    );
  }

  return appPage(renderAnalysisDetail(evidence, state, { fromDashboard: true }), {
    user: state.user,
    mode: "app",
  });
}

function renderAnalysisDetail(result, state, options = {}) {
  const source = {
    source: result.source || "Integração",
    sourceMeta: result.sourceMeta || "Sinal capturado automaticamente",
  };
  const competencies = (result.competencies || [])
    .map((item) => `<li class="tag">${escapeHtml(item)}</li>`)
    .join("");
  const suggestions = (result.suggestions || []).map((item) => `<li>${escapeHtml(item)}</li>`).join("");

  return `
    ${pageHero(
      options.fromDashboard ? "Análise completa" : "Nova evidência",
      options.fromDashboard ? "Detalhe da evidência" : "Evidência pronta para revisar",
      options.fromDashboard
        ? "Visualize a classificação completa, a ferramenta de origem e os critérios identificados."
        : "O sinal foi capturado automaticamente e a leitura de impacto já está disponível.",
    )}
    <div class="result-grid">
      <div class="evidence-source-card">
        ${sourceCard(source, "Analisada")}
      </div>

      <div class="analysis-card emphasis">
        <span class="score-label">Nível de impacto</span>
        <strong class="score-value">${escapeHtml(result.impactLevel)}</strong>
        <p class="score-note">${escapeHtml(result.readiness)}</p>
        <p class="subtle">Confiança: ${escapeHtml(confidenceLabel(result.confidence))}</p>
      </div>

      <div class="analysis-card">
        <h3>Justificativa</h3>
        <p>${escapeHtml(result.justification)}</p>
      </div>

      <div class="analysis-card">
        <h3>Competências identificadas</h3>
        <ul class="tag-list">${competencies || "<li class='tag'>Nenhuma competência listada</li>"}</ul>
      </div>

      <div class="analysis-card">
        <h3>Sugestões</h3>
        <ul class="suggestion-list">${suggestions || "<li>Nenhuma sugestão disponível.</li>"}</ul>
      </div>

      <div class="analysis-card">
        <h3>Resumo da evidência</h3>
        <p class="subtle">Nível atual: <strong>${escapeHtml(result.currentLevel)}</strong></p>
        <p class="subtle">Nível alvo: <strong>${escapeHtml(result.targetLevel)}</strong></p>
        <div class="evidence-preview">${escapeHtml(result.evidence)}</div>
      </div>

      <div class="analysis-side">
        <div class="info-card soft-panel">
          <h3>Próximas ações</h3>
          <ul class="mini-list">
            <li><span class="mini-dot blue"></span><span>Fortaleça a evidência com resultados mensuráveis.</span></li>
            <li><span class="mini-dot green"></span><span>Compare múltiplas evidências no painel por período.</span></li>
            <li><span class="mini-dot purple"></span><span>Use as sugestões para preparar a próxima conversa de carreira.</span></li>
          </ul>
        </div>
        <div class="form-actions">
          ${
            options.fromDashboard
              ? `<button class="button primary" type="button" data-action="back-dashboard">Voltar ao painel</button>`
              : `<button class="button primary" type="button" data-action="open-form">Ver próxima evidência</button>
                 <button class="button secondary" type="button" data-action="back-dashboard">Voltar ao painel</button>`
          }
        </div>
      </div>
    </div>
  `;
}

export function evidenceErrorPage(state, errorMessage) {
  return appPage(
    `
    ${pageHero(
      "Nova evidência",
      "Não foi possível abrir a evidência",
      "A chamada ao backend não respondeu como esperado. Tente novamente quando a API estiver disponível.",
    )}
    <div class="empty-state dashboard-empty">
      <p>${escapeHtml(errorMessage)}</p>
      <button class="button primary" type="button" data-action="reload-pending">Tentar novamente</button>
      <button class="button secondary" type="button" data-action="back-dashboard">Voltar ao painel</button>
    </div>
  `,
    { user: state.user, mode: "app" },
  );
}

function findEvidence(state) {
  const pool = state.viewingAsAdmin ? state.adminEvidences : state.evidences;
  return pool.find((item) => item.id === state.selectedEvidenceId);
}
