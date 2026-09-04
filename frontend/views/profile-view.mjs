import { appPage, pageHero } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";

export function profilePage(state) {
  const labels = {
    jobRole: state.careerConfiguration?.labels?.jobRole || "Cargo",
    level: state.careerConfiguration?.labels?.level || "Nível",
    characteristics: state.careerConfiguration?.labels?.characteristics || "Características",
    objective: state.careerConfiguration?.labels?.objective || "Objetivo",
  };

  return appPage(
    `
    ${pageHero(
      "Perfil de carreira",
      "Seu plano de carreira",
      "Consulte o contexto de carreira definido pela gestão e usado em cada nova análise.",
    )}
    ${state.profileLoading && !state.profile ? profileLoading() : profileReadModel(state.profile, state, labels)}
  `,
    { user: state.user, mode: "app", terminology: state.careerConfiguration?.labels },
  );
}

function profileLoading() {
  return `
    <section class="form-card profile-card" aria-busy="true">
      <p class="helper">Carregando seu plano e o framework de carreira…</p>
    </section>
  `;
}

function profileReadModel(profile, state, labels) {
  if (!profile) {
    return `
      <section class="form-card profile-card" role="alert">
        ${state.profileError ? `<p class="profile-status error">${escapeHtml(state.profileError)}</p>` : ""}
        <p class="helper">O plano ainda não está disponível. Tente novamente.</p>
        <button class="button primary" type="button" data-action="open-profile">Tentar novamente</button>
      </section>`;
  }

  const levels = Array.isArray(profile.levels) ? profile.levels : [];
  const characteristics = Array.isArray(profile.characteristics) ? profile.characteristics : [];
  const objectives = Array.isArray(profile.objectives) ? profile.objectives : [];

  return `
    <div class="content-grid profile-layout">
      <section class="form-card profile-card">
        <div>
          <span class="eyebrow">Somente leitura</span>
          <h2>${escapeHtml(labels.jobRole)}: ${escapeHtml(profile.jobRole?.name || "Não definido")}</h2>
          <p class="helper">Alterações no plano são feitas pelo gestor responsável.</p>
        </div>
        <div class="career-path" aria-label="Caminho de carreira">
          <strong>${escapeHtml(profile.currentLevel)}</strong>
          <span aria-hidden="true">→</span>
          <strong>${escapeHtml(profile.targetLevel)}</strong>
        </div>
        <section>
          <h3>${escapeHtml(labels.characteristics)}</h3>
          <ul class="tag-list">${characteristics.length ? characteristics.map((item) => `<li class="tag">${escapeHtml(item)}</li>`).join("") : "<li class='helper'>Nenhuma característica registrada.</li>"}</ul>
        </section>
        <section>
          <h3>${escapeHtml(labels.objective)}s</h3>
          <ul class="profile-objectives">${objectives.length ? objectives.map(objectiveItem).join("") : "<li class='helper'>Nenhum objetivo registrado.</li>"}</ul>
        </section>
      </section>

      <aside class="info-card soft-panel profile-framework-card">
        <h3>Framework de carreira</h3>
        <p class="card-copy">${escapeHtml(labels.level)}s disponíveis na ordem oficial.</p>
        <ol class="profile-level-list">
          ${levels.map((level, index) => frameworkLevel(level, index, profile.currentLevel, profile.targetLevel)).join("")}
        </ol>
      </aside>
    </div>`;
}

function objectiveItem(objective) {
  const target = objective.targetDate ? ` · até ${escapeHtml(objective.targetDate)}` : "";
  return `<li><strong>${escapeHtml(objective.text)}</strong><span>${escapeHtml(objective.status)}${target}</span></li>`;
}

function frameworkLevel(level, index, currentLevel, targetLevel) {
  const stateLabel =
    level.key === currentLevel ? "Atual" : level.key === targetLevel ? "Alvo" : "Disponível";
  return `
    <li class="profile-level-item ${level.key === currentLevel ? "current" : ""} ${level.key === targetLevel ? "target" : ""}">
      <span class="profile-level-index">${index + 1}</span>
      <span class="profile-level-copy"><strong>${escapeHtml(level.title || level.key)}</strong><span>${escapeHtml(level.key)} · ${escapeHtml(stateLabel)}</span></span>
    </li>`;
}
