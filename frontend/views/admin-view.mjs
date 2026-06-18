import { dashboardContent } from "./dashboard-view.mjs";
import { appPage } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";

export function adminPage(state) {
  const selectedEmployee =
    state.employees.find((employee) => employee.id === state.selectedEmployeeId) || state.employees[0];

  return appPage(
    `
    <div class="admin-shell">
      <aside class="admin-sidebar">
        <div class="admin-sidebar-head">
          <span class="eyebrow">Administração</span>
          <h2>Funcionários</h2>
          <p class="card-copy">Selecione um funcionário para ver o painel de análises.</p>
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
        <button class="button secondary admin-back" type="button" data-action="open-dashboard">Meu painel</button>
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
    { user: state.user, mode: "admin" },
  );
}

function initials(name) {
  return name
    .split(" ")
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || "")
    .join("");
}
