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

  const selectedEmployee = state.employees.find(
    (employee) => employee.id === state.selectedEmployeeId,
  );

  return appPage(
    `
    ${sectionNavigation}
    <div class="admin-shell">
      <aside class="admin-sidebar">
        <div class="admin-sidebar-head">
          <span class="eyebrow">Manager Console</span>
          <h2>${escapeHtml(labels.employee)}s</h2>
          <p class="card-copy">Encontre uma pessoa e gerencie seu contexto de carreira.</p>
        </div>
        ${managerPeopleFilters(state, labels)}
        <nav class="admin-employee-list" aria-label="${escapeHtml(labels.employee)}s">
          ${state.employees.length ? state.employees
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
                    <span>${escapeHtml(employee.jobRoleName || `Sem ${labels.jobRole.toLowerCase()}`)} · ${escapeHtml(employee.currentLevel || "—")} → ${escapeHtml(employee.targetLevel || "—")}</span>
                  </span>
                </button>
              `,
            )
            .join("") : `<p class="manager-no-results">Nenhum resultado para os filtros atuais.</p>`}
        </nav>
      </aside>

      <section class="admin-main">
        ${
          selectedEmployee ? managerEmployeeDetail(state, selectedEmployee, labels) : managerPeopleEmpty(state, labels)
        }
      </section>
    </div>
  `,
    { user: state.user, mode: "manager", terminology: labels },
  );
}

function managerPeopleFilters(state, labels) {
  const filters = state.managerFilters || {};
  const roles = state.managerSettings?.activeRoles || [];
  const levels = state.managerSettings?.frameworkLevels || [];
  return `
    <form class="manager-people-filters" data-manager-search-form>
      <label class="field"><span>Nome ou e-mail</span><input name="query" value="${escapeHtml(filters.query || "")}" placeholder="Buscar ${escapeHtml(labels.employee.toLowerCase())}" /></label>
      <label class="field"><span>${escapeHtml(labels.jobRole)}</span><select name="jobRoleId"><option value="">Todos</option>${roles.map((role) => `<option value="${escapeHtml(role.id)}" ${String(role.id) === String(filters.jobRoleId || "") ? "selected" : ""}>${escapeHtml(role.name)}</option>`).join("")}</select></label>
      <label class="field"><span>${escapeHtml(labels.level)}</span><select name="level"><option value="">Todos</option>${levels.map((level) => `<option value="${escapeHtml(level.key)}" ${level.key === filters.level ? "selected" : ""}>${escapeHtml(level.key)} · ${escapeHtml(level.title)}</option>`).join("")}</select></label>
      <div class="manager-filter-actions"><button class="button secondary" type="submit">Buscar</button><button class="button ghost" type="button" data-action="clear-manager-filters">Limpar</button></div>
    </form>`;
}

function managerPeopleEmpty(state, labels) {
  if (state.managerPeopleStatus === "error") {
    return `<div class="empty-state dashboard-empty" role="alert"><h2>Não foi possível carregar ${escapeHtml(labels.employee.toLowerCase())}s</h2><p>${escapeHtml(state.managerPeopleError || "Tente novamente.")}</p></div>`;
  }
  const filtered = Object.values(state.managerFilters || {}).some(Boolean);
  return `<div class="empty-state dashboard-empty"><h2>${filtered ? "Nenhum resultado" : `Nenhum ${escapeHtml(labels.employee.toLowerCase())} cadastrado`}</h2><p>${filtered ? "Ajuste ou limpe os filtros para tentar novamente." : "Quando houver pessoas cadastradas, elas aparecerão aqui."}</p></div>`;
}

function managerEmployeeDetail(state, employee, labels) {
  const section = state.managerDetailSection || "career-plan";
  const characteristics = employee.characteristics || [];
  return `
    <div class="manager-employee-hero">
      <div><span class="eyebrow">${escapeHtml(employee.jobRoleName || labels.employee)}</span><h1>${escapeHtml(employee.name)}</h1><p>${escapeHtml(employee.email)}</p></div>
      <dl class="manager-summary"><div><dt>${escapeHtml(labels.level)}</dt><dd>${escapeHtml(employee.currentLevel || "—")} → ${escapeHtml(employee.targetLevel || "—")}</dd></div><div><dt>${escapeHtml(labels.characteristics)}</dt><dd>${characteristics.length ? characteristics.map(escapeHtml).join(", ") : "—"}</dd></div><div><dt>${escapeHtml(labels.objective)}s ativos</dt><dd>${Number(employee.activeObjectiveCount || 0)}</dd></div></dl>
    </div>
    <div class="manager-detail-tabs" role="tablist" aria-label="Detalhes de ${escapeHtml(employee.name)}">
      ${managerDetailTab("career-plan", "Plano de carreira", section)}
      ${managerDetailTab("evidence", "Evidências", section)}
      ${managerDetailTab("analyses", "Análises", section)}
    </div>
    <div role="tabpanel">
      ${section === "career-plan" ? managerCareerPlan(state, labels) : section === "evidence" ? managerEvidenceSection(state) : managerAnalysesSection(state)}
    </div>`;
}

function managerDetailTab(value, label, selected) {
  return `<button class="button ${value === selected ? "primary" : "secondary"}" type="button" role="tab" aria-selected="${value === selected}" data-action="switch-manager-detail" data-manager-detail="${value}">${label}</button>`;
}

function managerEvidenceSection(state) {
  if (state.managerDetailStatus === "loading") return `<section class="form-card" aria-busy="true"><p>Carregando evidências…</p></section>`;
  if (state.managerDetailError) return `<section class="form-card" role="alert"><p>${escapeHtml(state.managerDetailError)}</p></section>`;
  const items = state.managerEvidenceRecords || [];
  if (!items.length) return `<section class="empty-state"><h2>Nenhuma evidência</h2><p>Esta pessoa ainda não possui evidências capturadas.</p></section>`;
  return `<section class="manager-record-list" aria-label="Evidências">${items.map((item) => `<article class="form-card manager-record"><div><span class="status-pill">${escapeHtml(item.status)}</span><h3>${escapeHtml(item.sourceMeta || item.source)}</h3></div><p>${escapeHtml(item.content)}</p><small>${escapeHtml(formatTimestamp(item.occurredAt))}</small></article>`).join("")}</section>`;
}

function managerAnalysesSection(state) {
  if (state.managerDetailStatus === "loading") return `<section class="form-card" aria-busy="true"><p>Carregando análises…</p></section>`;
  if (state.managerDetailError) return `<section class="form-card" role="alert"><p>${escapeHtml(state.managerDetailError)}</p></section>`;
  const items = state.adminEvidences || [];
  if (!items.length) return `<section class="empty-state"><h2>Nenhuma análise</h2><p>As análises concluídas aparecerão aqui.</p></section>`;
  return `<section class="manager-record-list" aria-label="Análises">${items.map((item) => `<article class="form-card manager-record"><div><span class="status-pill">${escapeHtml(item.confidence || "—")}</span><h3>${escapeHtml(item.sourceMeta || item.source)}</h3></div><p>${escapeHtml(item.justification || item.evidence)}</p><div class="manager-record-footer"><small>${escapeHtml(formatTimestamp(item.createdAt))}</small><button class="button secondary" type="button" data-action="open-evidence-detail" data-evidence-id="${escapeHtml(item.id)}" data-saved-analysis-id="${escapeHtml(item.analysisId || "")}">Ver análise</button></div></article>`).join("")}</section>`;
}

function formatTimestamp(value) {
  if (!value) return "Data indisponível";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat("pt-BR", { dateStyle: "medium" }).format(date);
}

export function managerCareerPlan(state, labels) {
  if (state.careerPlanStatus === "loading") {
    return `<section class="form-card manager-career-plan" aria-busy="true"><p>Carregando plano de carreira…</p></section>`;
  }
  if (state.careerPlanStatus === "error" || !state.selectedCareerPlan) {
    return `<section class="form-card manager-career-plan" role="alert"><p>${escapeHtml(state.careerPlanError || "Plano de carreira indisponível.")}</p></section>`;
  }

  const plan = state.selectedCareerPlan;
  const roles = state.managerSettings?.activeRoles || [];
  const levels = state.managerSettings?.frameworkLevels || plan.levels || [];
  const objectives = Array.isArray(plan.objectives) ? plan.objectives : [];
  const feedback = state.careerPlanError
    ? `<p class="profile-status error" role="alert">${escapeHtml(state.careerPlanError)}</p>`
    : state.careerPlanNotice
      ? `<p class="profile-status success" role="status">${escapeHtml(state.careerPlanNotice)}</p>`
      : "";

  return `
    <section class="form-card manager-career-plan" aria-labelledby="career-plan-title">
      <div><span class="eyebrow">Plano de carreira</span><h2 id="career-plan-title">Contexto de ${escapeHtml(labels.employee.toLowerCase())}</h2></div>
      ${feedback}
      <form class="career-plan-form" data-career-plan-form novalidate>
        <label class="field"><span>${escapeHtml(labels.jobRole)}</span>
          <select name="jobRoleId" required>${roles.map((role) => `<option value="${escapeHtml(role.id)}" ${role.id === plan.jobRole?.id ? "selected" : ""}>${escapeHtml(role.name)}</option>`).join("")}</select>
        </label>
        <div class="career-plan-levels">
          <label class="field"><span>${escapeHtml(labels.level)} atual</span><select name="currentLevel" required>${careerLevelOptions(levels, plan.currentLevel)}</select></label>
          <label class="field"><span>${escapeHtml(labels.level)} alvo</span><select name="targetLevel" required>${careerLevelOptions(levels, plan.targetLevel)}</select></label>
        </div>
        <label class="field"><span>${escapeHtml(labels.characteristics)}</span><textarea name="characteristics" maxlength="1200" placeholder="Separe por vírgulas ou linhas">${escapeHtml((plan.characteristics || []).join(", "))}</textarea></label>
        <button class="button primary" type="submit" ${state.careerPlanSaving ? "disabled" : ""}>${state.careerPlanSaving ? "Salvando…" : "Salvar plano"}</button>
      </form>

      <div class="career-objectives">
        <h3>${escapeHtml(labels.objective)}s</h3>
        ${objectives.map((objective) => objectiveForm(objective, state.careerPlanSaving)).join("")}
        ${objectiveForm(null, state.careerPlanSaving)}
      </div>
    </section>`;
}

function careerLevelOptions(levels, selected) {
  return levels
    .map(
      (level) =>
        `<option value="${escapeHtml(level.key)}" ${level.key === selected ? "selected" : ""}>${escapeHtml(level.key)} · ${escapeHtml(level.title)}</option>`,
    )
    .join("");
}

function objectiveForm(objective, saving) {
  const isNew = !objective;
  return `
    <form class="objective-form" data-objective-form ${isNew ? "" : `data-objective-id="${escapeHtml(objective.id)}"`}>
      <label class="field"><span>${isNew ? "Novo objetivo" : "Objetivo"}</span><input name="text" value="${escapeHtml(objective?.text || "")}" maxlength="1000" required /></label>
      <label class="field"><span>Status</span><select name="status">
        ${["ACTIVE", "COMPLETED", "ARCHIVED"].map((status) => `<option value="${status}" ${status === (objective?.status || "ACTIVE") ? "selected" : ""}>${status}</option>`).join("")}
      </select></label>
      <label class="field"><span>Data alvo</span><input type="date" name="targetDate" value="${escapeHtml(objective?.targetDate || "")}" /></label>
      <button class="button secondary" type="submit" ${saving ? "disabled" : ""}>${isNew ? "Adicionar" : "Atualizar"}</button>
    </form>`;
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
