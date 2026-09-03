import { dashboardContent } from "./dashboard-view.mjs";
import { appPage } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";

export function managerPage(state) {
  const selectedEmployee =
    state.employees.find((employee) => employee.id === state.selectedEmployeeId) || state.employees[0];

  return appPage(
    `
    <div class="admin-shell">
      <aside class="admin-sidebar">
        <div class="admin-sidebar-head">
          <span class="eyebrow">Manager Console</span>
          <h2>Funcionários</h2>
          <p class="card-copy">Selecione um funcionário para acompanhar as análises.</p>
        </div>
        <nav class="admin-employee-list" aria-label="Funcionários">
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
                  subtitle: "Painel de análises do funcionário",
                  copy: "Visualize as evidências analisadas, filtros por data e detalhes completos de cada classificação.",
                },
              )
            : `<div class="empty-state dashboard-empty"><p>Nenhum funcionário cadastrado.</p></div>`
        }
      </section>
    </div>
  `,
    { user: state.user, mode: "manager" },
  );
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
