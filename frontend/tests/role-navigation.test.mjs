import assert from "node:assert/strict";
import test from "node:test";

import { siteHeader } from "../components/layout.mjs";
import { roleLabel } from "../utils/format.mjs";
import { managerPage, permissionPage } from "../views/manager-view.mjs";

const manager = { id: 1, name: "Marina Gestora", email: "manager@example.com", role: "MANAGER" };
const employee = { id: 2, name: "João Silva", email: "employee@example.com", role: "EMPLOYEE" };

test("manager navigation exposes only the Manager Console workspace", () => {
  const header = siteHeader("manager", manager);
  const page = managerPage({
    user: manager,
    employees: [],
    selectedEmployeeId: null,
    adminEvidences: [],
    adminFilters: {},
  });

  assert.match(header, /Manager Console/);
  assert.match(header, /data-action="open-manager"/);
  assert.doesNotMatch(header, /data-action="open-dashboard"/);
  assert.doesNotMatch(header, /data-action="open-profile"/);
  assert.doesNotMatch(header, /data-action="open-form"/);
  assert.doesNotMatch(page, /Meu painel/);
  assert.doesNotMatch(page, /Administrador|Administração/);
  assert.equal(roleLabel(manager.role), "Gestor");
});

test("employee navigation keeps dashboard, profile, and evidence actions", () => {
  const header = siteHeader("app", employee);

  assert.match(header, /data-action="open-dashboard"/);
  assert.match(header, /data-action="open-profile"/);
  assert.match(header, /data-action="open-form"/);
  assert.doesNotMatch(header, /data-action="open-manager"/);
  assert.equal(roleLabel(employee.role), "Funcionário");
});

test("manager settings render configured terminology and framework roles", () => {
  const page = managerPage({
    user: manager,
    managerSection: "settings",
    managerSettingsStatus: "ready",
    managerSettingsSaving: false,
    managerSettings: {
      labels: {
        manager: "Líder",
        employee: "Talento",
        jobRole: "Trilha",
        level: "Estágio",
        characteristics: "Forças",
        objective: "Meta",
      },
      frameworkLevels: [{ key: "L3", title: "Engineer I" }],
    },
    jobRoles: [
      {
        id: 7,
        name: "Engenharia",
        description: "Produto digital",
        status: "ACTIVE",
        allowedLevelIds: ["L3"],
      },
    ],
  });

  assert.match(page, /Talentos/);
  assert.match(page, /trilhas disponíveis/);
  assert.match(page, /Estágios permitidos/);
  assert.match(page, /Engenharia/);
  assert.match(page, /data-terminology-form/);
});

test("permission errors preserve role-appropriate navigation destinations", () => {
  const managerHtml = permissionPage({ user: manager, permissionError: "Acesso negado." });
  const employeeHtml = permissionPage({ user: employee, permissionError: "Acesso negado." });

  assert.match(managerHtml, /Acesso negado/);
  assert.match(managerHtml, /data-action="open-manager"/);
  assert.doesNotMatch(managerHtml, /data-action="open-dashboard"/);
  assert.match(employeeHtml, /data-action="open-dashboard"/);
});
