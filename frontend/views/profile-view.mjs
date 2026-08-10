import { appPage, pageHero } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";

export function profilePage(state) {
  const profile = state.profile;
  const draft = state.profileDraft || profile;

  return appPage(
    `
    ${pageHero(
      "Perfil de carreira",
      "Seu ponto de partida",
      "Defina o nível atual e o próximo nível desejado para que cada nova análise use o seu contexto de carreira.",
    )}
    ${state.profileLoading && !profile ? profileLoading() : profileForm(profile, draft, state)}
  `,
    { user: state.user, mode: "app" },
  );
}

function profileLoading() {
  return `
    <section class="form-card profile-card">
      <p class="helper">Carregando seu perfil e o framework de carreira...</p>
    </section>
  `;
}

function profileForm(profile, draft, state) {
  if (!profile) {
    return `
    <section class="form-card profile-card">
        ${state.profileError ? `<p class="profile-status error">${escapeHtml(state.profileError)}</p>` : ""}
        <p class="helper">O perfil ainda não está disponível. Tente novamente.</p>
        <div class="form-actions">
          <button class="button primary" type="button" data-action="open-profile">Tentar novamente</button>
        </div>
      </section>
    `;
  }

  const levels = Array.isArray(profile.levels) ? profile.levels : [];
  const currentLevel = draft?.currentLevel || profile.currentLevel;
  const targetLevel = draft?.targetLevel || profile.targetLevel;

  return `
    <div class="content-grid profile-layout">
      <section class="form-card profile-card">
        <div>
          <span class="eyebrow">Contexto autenticado</span>
          <h2>Atualize seu perfil</h2>
          <p class="helper">As opções abaixo vêm do framework configurado no servidor.</p>
        </div>
        <form class="profile-form" data-profile-form>
          <label class="field">
            <span>Nível atual</span>
            <select name="currentLevel" data-profile-field="currentLevel" required>
              ${levelOptions(levels, currentLevel)}
            </select>
          </label>
          <label class="field">
            <span>Próximo nível desejado</span>
            <select name="targetLevel" data-profile-field="targetLevel" required>
              ${levelOptions(levels, targetLevel)}
            </select>
          </label>
          <p class="helper">O nível desejado precisa estar acima do nível atual na ordem declarada pelo framework.</p>
          ${state.profileError ? `<p class="profile-status error">${escapeHtml(state.profileError)}</p>` : ""}
          <div class="form-actions">
            <button class="button primary" type="submit" ${state.profileSaving ? "disabled" : ""}>
              ${state.profileSaving ? "Salvando..." : "Salvar perfil"}
            </button>
            <button class="button secondary" type="button" data-action="open-dashboard">Voltar ao painel</button>
          </div>
        </form>
      </section>

      <aside class="info-card soft-panel profile-framework-card">
        <h3>Framework de carreira</h3>
        <p class="card-copy">Níveis disponíveis para seu contexto, na ordem oficial.</p>
        <ol class="profile-level-list">
          ${levels.map((level, index) => frameworkLevel(level, index, currentLevel, targetLevel)).join("")}
        </ol>
      </aside>
    </div>
  `;
}

function levelOptions(levels, selectedLevel) {
  return levels
    .map(
      (level) =>
        `<option value="${escapeHtml(level.key)}" ${level.key === selectedLevel ? "selected" : ""}>${escapeHtml(level.title || level.key)} (${escapeHtml(level.key)})</option>`,
    )
    .join("");
}

function frameworkLevel(level, index, currentLevel, targetLevel) {
  const stateLabel =
    level.key === currentLevel ? "Atual" : level.key === targetLevel ? "Alvo" : "Disponível";

  return `
    <li class="profile-level-item ${level.key === currentLevel ? "current" : ""} ${level.key === targetLevel ? "target" : ""}">
      <span class="profile-level-index">${index + 1}</span>
      <span class="profile-level-copy">
        <strong>${escapeHtml(level.title || level.key)}</strong>
        <span>${escapeHtml(level.key)} · ${escapeHtml(stateLabel)}</span>
      </span>
    </li>
  `;
}
