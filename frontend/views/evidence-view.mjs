import { sourceCard } from "../components/cards.mjs";
import { appPage, pageHero } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";
import { confidenceLabel, formatTimestamp } from "../utils/format.mjs";

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

      ${reviewPanel(state, options)}

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

function reviewPanel(state, options) {
  const review = state.review || { currentStatus: "UNREVIEWED", history: [] };
  const status = review.currentStatus || "UNREVIEWED";
  const history = Array.isArray(review.history) ? review.history : [];
  const canReview =
    options.fromDashboard && state.viewingAsAdmin && state.user?.role === "ADMIN";
  const saving = state.reviewSaving;

  return `
    <section class="analysis-card review-card" aria-labelledby="review-title">
      <div class="review-heading">
        <div>
          <span class="score-label">Revis&atilde;o administrativa</span>
          <h3 id="review-title">Status atual</h3>
        </div>
        <span class="review-status ${reviewStatusClass(status)}">${escapeHtml(reviewStatusLabel(status))}</span>
      </div>
      ${
        state.reviewStatus === "loading"
          ? `<div class="loading-strip"><span class="loading-dot"></span><span>Carregando hist&oacute;rico de revis&atilde;o...</span></div>`
          : ""
      }
      ${state.reviewError ? `<p class="review-error">${escapeHtml(state.reviewError)}</p>` : ""}
      ${
        history.length
          ? `<ol class="review-history">${history.map(reviewHistoryItem).join("")}</ol>`
          : `<p class="review-empty">Nenhum evento registrado. Esta an&aacute;lise est&aacute; <strong>n&atilde;o revisada</strong>.</p>`
      }
      ${
        canReview
          ? `
            <form class="review-form" data-review-form>
              <label class="field">
                <span>Coment&aacute;rio opcional</span>
                <textarea name="comment" maxlength="2000" rows="4" placeholder="Registre o contexto que deve acompanhar esta decis&atilde;o."></textarea>
              </label>
              <p class="review-help">At&eacute; 2.000 caracteres. Cada a&ccedil;&atilde;o cria um novo evento no hist&oacute;rico.</p>
              <div class="form-actions">
                <button class="button primary" type="submit" data-review-status="ACCEPTED" ${saving ? "disabled" : ""}>Aceitar an&aacute;lise</button>
                <button class="button secondary" type="submit" data-review-status="NEEDS_CONTEXT" ${saving ? "disabled" : ""}>Pedir mais contexto</button>
              </div>
            </form>
          `
          : `<p class="review-readonly">O hist&oacute;rico &eacute; imut&aacute;vel. Apenas administradores podem registrar uma nova revis&atilde;o.</p>`
      }
    </section>
  `;
}

function reviewHistoryItem(item) {
  const reviewer = item.reviewerName || item.reviewerEmail || "Administrador";
  const timestamp = item.createdAt ? formatTimestamp(item.createdAt) : "Data indispon&iacute;vel";
  return `
    <li class="review-history-item">
      <div class="review-history-meta">
        <span class="review-status ${reviewStatusClass(item.status)}">${escapeHtml(reviewStatusLabel(item.status))}</span>
        <span>${escapeHtml(reviewer)} &middot; ${escapeHtml(timestamp)}</span>
      </div>
      ${item.comment ? `<p>${escapeHtml(item.comment)}</p>` : `<p class="subtle">Sem coment&aacute;rio.</p>`}
    </li>
  `;
}

function reviewStatusLabel(status) {
  if (status === "ACCEPTED") {
    return "Aceita";
  }

  if (status === "NEEDS_CONTEXT") {
    return "Precisa de contexto";
  }

  return "Nao revisada";
}

function reviewStatusClass(status) {
  if (status === "ACCEPTED") {
    return "accepted";
  }

  if (status === "NEEDS_CONTEXT") {
    return "needs-context";
  }

  return "unreviewed";
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
  return pool.find((item) => String(item.id) === String(state.selectedEvidenceId));
}
