import { mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { chromium } from "playwright-core";

const outputDirectory = path.dirname(fileURLToPath(import.meta.url));
const appBaseUrl = "http://localhost:14173";
const apiBaseUrl = "http://localhost:8080";
const chromeExecutable = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";

await mkdir(outputDirectory, { recursive: true });

const browser = await chromium.launch({ executablePath: chromeExecutable, headless: true });
const captured = [];

async function newPage() {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 1,
    colorScheme: "light",
    locale: "pt-BR",
  });
  await context.addInitScript((url) => {
    window.PROMOVA_API_BASE_URL = url;
  }, apiBaseUrl);
  const page = await context.newPage();
  return { context, page };
}

async function settle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.locator("#app").waitFor({ state: "visible" });
  await page.addStyleTag({
    content: "*, *::before, *::after { animation: none !important; transition: none !important; caret-color: transparent !important; }",
  });
  await page.waitForTimeout(250);
}

async function shot(page, filename, description) {
  await settle(page);
  await page.screenshot({
    path: path.join(outputDirectory, filename),
    fullPage: true,
  });
  captured.push({ filename, description, url: page.url() });
  console.log(`captured ${filename}`);
}

async function login(page, email, password) {
  await page.goto(appBaseUrl);
  await page.locator('[data-action="open-auth"]').first().click();
  const form = page.locator('[data-auth-form="login"]');
  await form.locator('input[name="email"]').fill(email);
  await form.locator('input[name="password"]').fill(password);
  await form.getByRole("button", { name: "Entrar", exact: true }).click();
  await page.getByRole("button", { name: "Sair", exact: true }).waitFor();
  await settle(page);
}

try {
  {
    const { context, page } = await newPage();
    await page.goto(appBaseUrl);
    await shot(page, "01-public-landing-full.png", "Public landing page, full length");

    await page.locator('[data-action="open-auth"]').first().click();
    await shot(page, "02-auth-login.png", "Authentication page in login mode");

    await page.locator('[data-action="switch-auth-register"]').first().click();
    await shot(page, "03-auth-register.png", "Authentication page in registration mode");
    await context.close();
  }

  {
    const { context, page } = await newPage();
    await login(page, "maria.santos@empresa.com", "senha123");
    await page.getByRole("heading", { name: "Suas evidências" }).waitFor();
    await shot(page, "04-employee-dashboard-populated.png", "Employee dashboard with saved analyses");

    const savedEvidenceToggle = page.locator('.recent-evidence-section [data-action="toggle-evidence"]').first();
    if (await savedEvidenceToggle.count()) {
      await savedEvidenceToggle.click();
      await shot(page, "05-employee-dashboard-analysis-expanded.png", "Employee dashboard with a saved analysis expanded");

      await page.locator('.recent-evidence-section [data-action="open-evidence-detail"]').first().click();
      await page.getByRole("heading", { name: "Detalhe da evidência" }).waitFor();
      await shot(page, "06-employee-analysis-detail-review.png", "Employee analysis detail and read-only review history");
      await page.locator('[data-action="back-dashboard"]').last().click();
      await page.getByRole("heading", { name: "Suas evidências" }).waitFor();
    }

    await page.locator('[data-dashboard-tab="framework"]').click();
    await page.locator("details.criterion-level-group").evaluateAll((groups) => {
      groups.forEach((group) => {
        group.open = true;
      });
    });
    await shot(page, "07-employee-dashboard-framework-all-levels.png", "Framework coverage tab with all level groups expanded");

    await page.locator('[data-dashboard-tab="criteria"]').click();
    await shot(page, "08-employee-dashboard-criteria.png", "Criteria gaps tab");

    await page.locator('[data-dashboard-tab="connections"]').click();
    await shot(page, "09-employee-dashboard-connections.png", "Connections tab and profile handoff");

    await page.getByRole("button", { name: "Abrir Perfil", exact: true }).click();
    await page.getByRole("heading", { name: "Seu plano de carreira" }).waitFor();
    await shot(page, "10-employee-profile-career-and-github.png", "Employee career profile and complete GitHub connection section");
    await context.close();
  }

  {
    const { context, page } = await newPage();
    await login(page, "joao.silva@empresa.com", "senha123");
    await page.getByRole("heading", { name: "Suas evidências" }).waitFor();
    await shot(page, "11-employee-dashboard-pending-inbox.png", "Employee dashboard with pending evidence inbox");

    const pendingToggle = page.locator('.evidence-inbox [data-action="toggle-evidence"]').first();
    if (await pendingToggle.count()) {
      await pendingToggle.click();
      await shot(page, "12-employee-dashboard-pending-expanded.png", "Pending evidence expanded with complete source content and actions");

      await page.locator('.evidence-inbox [data-action="open-pending-evidence"]').first().click();
      await page.getByRole("heading", { name: "Revise antes de analisar" }).waitFor();
      await shot(page, "13-employee-pending-evidence-review.png", "Pending evidence review page before analysis");
    }
    await context.close();
  }

  {
    const { context, page } = await newPage();
    await login(page, "admin@promova.com", "admin123");
    await page.getByText("Manager Console", { exact: true }).first().waitFor();
    await shot(page, "14-manager-people-joao-career-plan.png", "Manager people directory and João's career plan");

    await page.locator('[data-manager-detail="evidence"]').click();
    await shot(page, "15-manager-joao-evidences.png", "Manager read-only evidence list for João");
    const managerEvidenceToggle = page.locator('.manager-record-list [data-action="toggle-evidence"]').first();
    if (await managerEvidenceToggle.count()) {
      await managerEvidenceToggle.click();
      await shot(page, "16-manager-joao-evidence-expanded.png", "Manager evidence list with a record expanded");
    }

    await page.locator('[data-action="select-employee"]', { hasText: "maria.santos@empresa.com" }).click();
    await page.getByRole("heading", { name: "Maria Santos" }).waitFor();
    await shot(page, "17-manager-maria-career-plan-objective.png", "Manager career plan for Maria, including an existing objective");

    await page.locator('[data-manager-detail="analyses"]').click();
    await shot(page, "18-manager-maria-analyses.png", "Manager saved analyses list for Maria");

    const managerAnalysis = page.locator('[data-action="open-evidence-detail"]').first();
    if (await managerAnalysis.count()) {
      await managerAnalysis.click();
      await page.getByRole("heading", { name: "Detalhe da evidência" }).waitFor();
      await shot(page, "19-manager-analysis-detail-review-controls.png", "Manager analysis detail with review controls and history");
      await page.locator('[data-action="back-dashboard"]').last().click();
      await page.getByText("Manager Console", { exact: true }).first().waitFor();
    }

    await page.locator('[data-manager-section="settings"]').click();
    await page.getByRole("heading", { name: "Configuração de carreira" }).waitFor();
    await shot(page, "20-manager-career-settings-full.png", "Manager terminology, role catalog, level options, and archive controls");
    await context.close();
  }
} finally {
  await browser.close();
}

console.log(JSON.stringify(captured, null, 2));
