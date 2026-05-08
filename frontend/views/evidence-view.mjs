import { sourceCard } from "../components/cards.mjs";
import { appPage, pageHero } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";

export function evidenceLoadingPage(state) {
  const evidence = state.pendingEvidence;

  return appPage(`
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
          <h3>Resumo da sessão</h3>
          <p class="card-copy">Esta sessão já inclui <strong>${state.evidences.length}</strong> evidência${state.evidences.length === 1 ? "" : "s"} salva${state.evidences.length === 1 ? "" : "s"}.</p>
        </div>
      </aside>
    </div>
  `);
}

export function evidenceResultPage(state) {
  const result = state.result;
  const source = {
    source: result.source || "Integração",
    sourceMeta: result.sourceMeta || "Sinal capturado automaticamente",
  };
  const competencies = result.competencies
    .map((item) => `<li class="tag">${escapeHtml(item)}</li>`)
    .join("");
  const suggestions = result.suggestions.map((item) => `<li>${escapeHtml(item)}</li>`).join("");

  return appPage(`
    ${pageHero(
      "Nova evidência",
      "Evidência pronta para revisar",
      "O sinal foi capturado automaticamente e a leitura de impacto já está disponível.",
    )}
    <div class="result-grid">
      <div class="evidence-source-card">
        ${sourceCard(source, "Analisada")}
      </div>

      <div class="analysis-card emphasis">
        <span class="score-label">Nível de impacto</span>
        <strong class="score-value">${escapeHtml(result.impactLevel)}</strong>
        <p class="score-note">${escapeHtml(result.readiness)}</p>
      </div>

      <div class="analysis-card">
        <h3>Justificativa</h3>
        <p>${escapeHtml(result.justification)}</p>
      </div>

      <div class="analysis-card">
        <h3>Competências identificadas</h3>
        <ul class="tag-list">${competencies}</ul>
      </div>

      <div class="analysis-card">
        <h3>Sugestões</h3>
        <ul class="suggestion-list">${suggestions}</ul>
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
            <li><span class="mini-dot green"></span><span>Salve a próxima evidência para manter o painel da sessão atualizado.</span></li>
            <li><span class="mini-dot purple"></span><span>Use o painel para comparar múltiplos envios na mesma sessão.</span></li>
          </ul>
        </div>
        <div class="form-actions">
          <button class="button primary" type="button" data-action="back-form">Ver próxima evidência</button>
          <button class="button secondary" type="button" data-action="back-dashboard">Voltar ao painel</button>
        </div>
      </div>
    </div>
  `);
}

export function evidenceErrorPage(errorMessage) {
  return appPage(`
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
  `);
}
