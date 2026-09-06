import { dashboardContent } from "./dashboard-view.mjs";
import { appPage } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";

export function managerPage(state) {
  const labels = terminology(state);
  const section = state.managerSection || "people";
  const sectionNavigation = `
    <div class="manager-section-tabs" role="tablist" aria-label="Manager Console">
      <button class="button ${section === "people" ? "primary" : "secondary"}" type="button" role="tab" aria-selected="${section === "people"}" data-action="switch-manager-section" data-manager-section="people">${escapeHtml(labels.employee)}s</button>
      <button class="button ${section === "settings" ? "primary" : "secondary"}" type="button" role="tab" aria-selected="${section === "settings"}" data-action="switch-manager-section" data-manager-section="settings">Configurações</button>
    </div>
  `;

  if (section === "settings") {
    return appPage(`${sectionNavigation}${managerSettings(state, labels)}`, {
      user: state.user,
      mode: "manager",
      terminology: labels,
    });
  }

  const selectedEmployee =
    state.employees.find((employee) => employee.id === state.selectedEmployeeId) || state.employees[0];

  return appPage(
    `
    ${sectionNavigation}
    <div class="admin-shell">
      <aside class="admin-sidebar">
        <div class="admin-sidebar-head">
          <span class="eyebrow">Manager Console</span>
          <h2>${escapeHtml(labels.employee)}s</h2>
          <p class="card-copy">Selecione um ${escapeHtml(labels.employee.toLowerCase())} para acompanhar as análises.</p>
        </div>
        <nav class="admin-employee-list" aria-label="${escapeHtml(labels.employee)}s">
          ${state.employees
            .map(
              (employee) => `
                <button
                  class="admin-employee-item ${employee.id === selectedEmployee?.id ? "active" : ""}"
                  type="button"
                  data-action="select-employee"
                  data-employee-id="${employee.id}"
                >
                  <span class="admin-employee-avatar">${escapeHtml(initials(employee.name))}</span>
                  <span class="admin-employee-copy">
                    <strong>${escapeHtml(employee.name)}</strong>
                    <span>${escapeHtml(employee.email)}</span>
                  </span>
                </button>
              `,
            )
            .join("")}
        </nav>
      </aside>

      <section class="admin-main">
        ${
          selectedEmployee
            ? dashboardContent(
                {
                  ...state,
                  evidences: state.adminEvidences,
                  dashboardFilters: state.adminFilters,
                  viewingAsAdmin: true,
                  viewedEmployee: selectedEmployee,
                },
                {
                  title: selectedEmployee.name,
                  subtitle: `Painel de análises do ${labels.employee.toLowerCase()}`,
                  copy: "Visualize as evidências analisadas, filtros por data e detalhes completos de cada classificação.",
                },
              )
            : `<div class="empty-state dashboard-empty"><p>Nenhum ${escapeHtml(labels.employee.toLowerCase())} cadastrado.</p></div>`
        }
      </section>
    </div>
  `,
    { user: state.user, mode: "manager", terminology: labels },
  );
}

function managerSettings(state, labels) {
  if (state.managerSettingsStatus === "error") {
    return `
      <section class="manager-settings empty-state" role="alert">
        <h1>Não foi possível carregar as configurações</h1>
        <p>${escapeHtml(state.managerSettingsError || "Tente novamente.")}</p>
        <button class="button primary" type="button" data-action="open-manager">Tentar novamente</button>
      </section>`;
  }
  if (state.managerSettingsStatus === "loading" || !state.managerSettings) {
    return `<section class="manager-settings" aria-busy="true"><p>Carregando configurações…</p></section>`;
  }

  const status = state.managerSettingsError
    ? `<p class="profile-status error" role="alert">${escapeHtml(state.managerSettingsError)}</p>`
    : state.managerSettingsNotice
      ? `<p class="profile-status success" role="status">${escapeHtml(state.managerSettingsNotice)}</p>`
      : "";

  return `
    <section class="manager-settings" aria-labelledby="manager-settings-title">
      <div class="page-hero compact">
        <span class="eyebrow">Manager Settings</span>
        <h1 class="page-title" id="manager-settings-title">Configuração de carreira</h1>
        <p class="page-copy">Mantenha a terminologia da empresa e os ${escapeHtml(labels.jobRole.toLowerCase())}s disponíveis.</p>
      </div>
      ${status}
      <div class="manager-settings-grid">
        ${terminologyForm(state.managerSettings.labels, state.managerSettingsSaving)}
        ${jobRolesSection(state, labels)}
      </div>
    </section>
  `;
}

function terminologyForm(labels, saving) {
  const fields = [
    ["manager", "Gestor"],
    ["employee", "Funcionário"],
    ["jobRole", "Cargo"],
    ["level", "Nível"],
    ["characteristics", "Características"],
    ["objective", "Objetivo"],
  ];
  return `
    <form class="form-card settings-card" data-terminology-form novalidate>
      <div><span class="eyebrow">Terminologia</span><h2>Rótulos da organização</h2></div>
      <p class="card-copy">Esses termos aparecem nas superfícies de gestão e carreira.</p>
      <div class="settings-fields">
        ${fields
          .map(
            ([name, fallback]) => `
              <label class="field"><span>${escapeHtml(fallback)}</span>
                <input name="${name}" value="${escapeHtml(labels?.[name] || fallback)}" maxlength="80" required aria-required="true" />
              </label>`,
          )
          .join("")}
      </div>
      <button class="button primary" type="submit" ${saving ? "disabled" : ""}>${saving ? "Salvando…" : "Salvar terminologia"}</button>
    </form>
  `;
}

function jobRolesSection(state, labels) {
  const levels = state.managerSettings.frameworkLevels || [];
  return `
    <div class="settings-card-list">
      <form class="form-card settings-card" data-job-role-form novalidate>
        <div><span class="eyebrow">${escapeHtml(labels.jobRole)}s</span><h2>Novo ${escapeHtml(labels.jobRole.toLowerCase())}</h2></div>
        ${jobRoleFields(null, levels, labels)}
        <button class="button primary" type="submit" ${state.managerSettingsSaving ? "disabled" : ""}>Criar ${escapeHtml(labels.jobRole.toLowerCase())}</button>
      </form>
      ${(state.jobRoles || []).map((role) => jobRoleCard(role, state.jobRoles, levels, labels, state.managerSettingsSaving)).join("")}
    </div>
  `;
}

function jobRoleCard(role, roles, levels, labels, saving) {
  const activeAlternatives = roles.filter(
    (candidate) => candidate.status === "ACTIVE" && candidate.id !== role.id,
  );
  return `
    <article class="form-card settings-card ${role.status === "ARCHIVED" ? "archived" : ""}" data-job-role-card="${escapeHtml(role.id)}">
      <form data-job-role-form data-job-role-id="${escapeHtml(role.id)}" novalidate>
        <div class="settings-card-heading">
          <div><span class="eyebrow">${role.status === "ACTIVE" ? "Ativo" : "Arquivado"}</span><h2>${escapeHtml(role.name)}</h2></div>
        </div>
        ${jobRoleFields(role, levels, labels)}
        <button class="button secondary" type="submit" ${saving || role.status === "ARCHIVED" ? "disabled" : ""}>Salvar alterações</button>
      </form>
      ${
        role.status === "ACTIVE"
          ? `<div class="archive-row">
              <label class="field"><span>Alternativa para usuários atribuídos</span>
                <select data-replacement-role><option value="">Nenhuma selecionada</option>${activeAlternatives.map((item) => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`).join("")}</select>
              </label>
              <button class="button ghost" type="button" data-action="archive-job-role" data-job-role-id="${escapeHtml(role.id)}" ${saving ? "disabled" : ""}>Arquivar</button>
            </div>`
          : ""
      }
    </article>
  `;
}

function jobRoleFields(role, levels, labels) {
  const allowed = new Set(role?.allowedLevelIds || []);
  return `
    <label class="field"><span>Nome</span><input name="name" value="${escapeHtml(role?.name || "")}" maxlength="120" required /></label>
    <label class="field"><span>Descrição</span><textarea name="description" maxlength="1000" required>${escapeHtml(role?.description || "")}</textarea></label>
    <fieldset class="level-options"><legend>${escapeHtml(labels.level)}s permitidos</legend>
      ${levels.map((level) => `<label><input type="checkbox" name="allowedLevelIds" value="${escapeHtml(level.key)}" ${allowed.has(level.key) ? "checked" : ""} /> <span>${escapeHtml(level.key)} · ${escapeHtml(level.title)}</span></label>`).join("")}
    </fieldset>
  `;
}

function terminology(state) {
  return {
    manager: state.managerSettings?.labels?.manager || state.careerConfiguration?.labels?.manager || "Gestor",
    employee: state.managerSettings?.labels?.employee || state.careerConfiguration?.labels?.employee || "Funcionário",
    jobRole: state.managerSettings?.labels?.jobRole || state.careerConfiguration?.labels?.jobRole || "Cargo",
    level: state.managerSettings?.labels?.level || state.careerConfiguration?.labels?.level || "Nível",
    characteristics:
      state.managerSettings?.labels?.characteristics ||
      state.careerConfiguration?.labels?.characteristics ||
      "Características",
    objective:
      state.managerSettings?.labels?.objective || state.careerConfiguration?.labels?.objective || "Objetivo",
  };
}

export function permissionPage(state) {
  const destinationAction = state.user?.role === "MANAGER" ? "open-manager" : "open-dashboard";
  const destinationLabel = state.user?.role === "MANAGER" ? "Voltar ao Manager Console" : "Voltar ao painel";

  return appPage(
    `
      <div class="empty-state dashboard-empty" role="alert">
        <span class="eyebrow">Permissão necessária</span>
        <h1>Você não pode acessar este recurso</h1>
        <p>${escapeHtml(state.permissionError || "Você não tem permissão para realizar esta ação.")}</p>
        <button class="button primary" type="button" data-action="${destinationAction}">${destinationLabel}</button>
      </div>
    `,
    { user: state.user, mode: state.user?.role === "MANAGER" ? "manager" : "app" },
  );
}

function initials(name) {
  return name
    .split(" ")
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || "")
    .join("");
}
