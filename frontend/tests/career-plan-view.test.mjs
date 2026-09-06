import assert from "node:assert/strict";
import test from "node:test";

import { managerCareerPlan } from "../views/manager-view.mjs";
import { profilePage } from "../views/profile-view.mjs";

const plan = {
  userId: 7,
  jobRole: { id: 3, name: "Engineering", allowedLevelIds: ["L3", "L4"] },
  currentLevel: "L3",
  targetLevel: "L4",
  characteristics: ["Mentoria", "Ownership"],
  objectives: [
    { id: 9, text: "Liderar uma entrega", status: "ACTIVE", targetDate: "2026-12-01" },
  ],
  levels: [
    { key: "L3", title: "Engineer I" },
    { key: "L4", title: "Engineer II" },
  ],
};

test("manager career plan renders backend values and objective controls", () => {
  const html = managerCareerPlan(
    {
      careerPlanStatus: "ready",
      selectedCareerPlan: plan,
      managerSettings: {
        activeRoles: [{ id: 3, name: "Engineering" }],
        frameworkLevels: plan.levels,
      },
      careerPlanSaving: false,
    },
    {
      employee: "Talento",
      jobRole: "Trilha",
      level: "Estágio",
      characteristics: "Forças",
      objective: "Meta",
    },
  );

  assert.match(html, /data-career-plan-form/);
  assert.match(html, /Engineering/);
  assert.match(html, /Mentoria, Ownership/);
  assert.match(html, /data-objective-id="9"/);
  assert.match(html, /Liderar uma entrega/);
});

test("employee profile is read-only and shows the assigned plan", () => {
  const html = profilePage({
    user: { name: "Employee", role: "EMPLOYEE" },
    profile: plan,
    profileLoading: false,
    careerConfiguration: { labels: { jobRole: "Trilha", level: "Estágio" } },
  });

  assert.match(html, /Somente leitura/);
  assert.match(html, /Trilha: Engineering/);
  assert.match(html, /Liderar uma entrega/);
  assert.doesNotMatch(html, /data-profile-form/);
  assert.doesNotMatch(html, /Salvar perfil/);
});
