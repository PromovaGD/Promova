import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

import { chromium } from "playwright-core";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const chromeExecutable =
  process.env.PROMOVA_E2E_CHROME ||
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const backendPort = Number(process.env.PROMOVA_E2E_BACKEND_PORT || 18080);
const frontendPort = Number(process.env.PROMOVA_E2E_FRONTEND_PORT || 14173);
const apiBaseUrl = `http://127.0.0.1:${backendPort}`;
const appBaseUrl = `http://127.0.0.1:${frontendPort}`;

test("authenticated manager and employee sessions survive reload, navigation, and backend restart", { timeout: 240_000 }, async (t) => {
  const temporaryDirectory = await mkdtemp(path.join(os.tmpdir(), "promova-auth-e2e-"));
  const databasePath = path.join(temporaryDirectory, "promova");
  const processes = [];
  let backend;
  let browser;

  t.after(async () => {
    if (browser) {
      await browser.close().catch(() => {});
    }
    await Promise.all(processes.map((process) => stopProcess(process)));
    await rm(temporaryDirectory, { recursive: true, force: true });
  });

  backend = startBackend(databasePath);
  processes.push(backend);
  processes.push(
    startProcess("node", ["scripts/dev-server.js"], {
      cwd: repositoryRoot,
      env: { ...process.env, PORT: String(frontendPort) },
    }),
  );

  await Promise.all([
    waitForHttp(`${apiBaseUrl}/auth/me`, 90_000, [401]),
    waitForHttp(appBaseUrl, 30_000, [200]),
  ]);

  browser = await chromium.launch({ executablePath: chromeExecutable, headless: true });
  const context = await browser.newContext();
  await context.addInitScript((url) => {
    window.PROMOVA_API_BASE_URL = url;
  }, apiBaseUrl);
  const page = await context.newPage();

  await login(page, "manager@promova.com", "manager123");
  await assertManagerConsole(page);
  await page.reload();
  await assertManagerConsole(page);
  assert.equal(new URL(page.url()).pathname, "/manager");

  await page.getByRole("button", { name: "Sair" }).click();
  await page.locator('[data-action="open-auth"]').first().click();
  await loginFromForm(page, "joao.silva@empresa.com", "senha123");
  await page.getByRole("button", { name: "Perfil" }).click();
  await page.getByRole("heading", { name: "Seu plano de carreira" }).waitFor();
  assert.match(page.url(), /\/profile$/);

  await page.reload();
  await assertEmployeeProfile(page);
  await page.goto(`${appBaseUrl}/profile`);
  await assertEmployeeProfile(page);

  await stopProcess(backend);
  await waitForUnavailable(`${apiBaseUrl}/auth/me`, 30_000);
  backend = startBackend(databasePath);
  processes.push(backend);
  await waitForHttp(`${apiBaseUrl}/auth/me`, 90_000, [401]);
  await page.reload();
  await assertEmployeeProfile(page);

  await page.evaluate(() => localStorage.setItem("promova.auth-token", "invalid-expired-token"));
  await page.reload();
  try {
    await page.getByText("Sua sessão expirou. Faça login novamente.").waitFor({ timeout: 10_000 });
  } catch (error) {
    const body = (await page.locator("body").innerText()).slice(0, 2_000);
    throw new Error(`Invalid session did not reach login at ${page.url()}. Page: ${body}`, { cause: error });
  }
  await page.locator('[data-auth-form="login"]').waitFor();
  assert.equal(await page.evaluate(() => localStorage.getItem("promova.auth-token")), null);
  assert.equal(await page.evaluate(() => localStorage.getItem("promova.auth-user")), null);
});

async function login(page, email, password) {
  await page.goto(appBaseUrl);
  await page.locator('[data-action="open-auth"]').first().click();
  await loginFromForm(page, email, password);
}

async function loginFromForm(page, email, password) {
  const form = page.locator('[data-auth-form="login"]');
  await form.locator('input[name="email"]').fill(email);
  await form.locator('input[name="password"]').fill(password);
  await form.getByRole("button", { name: "Entrar" }).click();
}

async function assertManagerConsole(page) {
  try {
    await page.getByText("Manager Console", { exact: true }).first().waitFor({ timeout: 10_000 });
  } catch (error) {
    const body = (await page.locator("body").innerText()).slice(0, 2_000);
    const tokenPresent = await page.evaluate(() => Boolean(localStorage.getItem("promova.auth-token")));
    throw new Error(`Manager console did not load at ${page.url()} (token present: ${tokenPresent}). Page: ${body}`, { cause: error });
  }
  await page.getByRole("button", { name: "Sair" }).waitFor();
  assert.equal(await page.locator('[data-auth-form="login"]').count(), 0);
}

async function assertEmployeeProfile(page) {
  await page.getByRole("heading", { name: "Seu plano de carreira" }).waitFor();
  await page.getByRole("button", { name: "Sair" }).waitFor();
  assert.equal(await page.locator('[data-auth-form="login"]').count(), 0);
  assert.match(page.url(), /\/profile$/);
}

function startBackend(databasePath) {
  return startProcess("bash", ["./gradlew", "bootRun", `--args=--server.port=${backendPort}`], {
    cwd: path.join(repositoryRoot, "backend"),
    env: {
      ...process.env,
      PROMOVA_DEV_DB_URL: `jdbc:h2:file:${databasePath};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`,
      PROMOVA_CORS_ALLOWED_ORIGINS: `http://127.0.0.1:${frontendPort}`,
    },
  });
}

function startProcess(command, args, options) {
  const child = spawn(command, args, {
    ...options,
    detached: process.platform !== "win32",
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.output = "";
  const capture = (chunk) => {
    child.output = `${child.output}${chunk}`.slice(-12_000);
  };
  child.stdout.on("data", capture);
  child.stderr.on("data", capture);
  child.on("error", capture);
  return child;
}

async function stopProcess(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) {
    return;
  }
  signalProcessTree(child, "SIGTERM");
  await Promise.race([
    new Promise((resolve) => child.once("exit", resolve)),
    new Promise((resolve) => setTimeout(resolve, 10_000)),
  ]);
  if (child.exitCode === null && child.signalCode === null) {
    signalProcessTree(child, "SIGKILL");
  }
}

function signalProcessTree(child, signal) {
  try {
    if (process.platform === "win32") {
      child.kill(signal);
    } else {
      process.kill(-child.pid, signal);
    }
  } catch {
    // The process group has already exited.
  }
}

async function waitForHttp(url, timeoutMs, acceptedStatuses) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (acceptedStatuses.includes(response.status)) {
        return;
      }
      lastError = new Error(`Unexpected status ${response.status} from ${url}`);
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`Timed out waiting for ${url}: ${lastError?.message || "no response"}`);
}

async function waitForUnavailable(url, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      await fetch(url);
    } catch {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  throw new Error(`Timed out waiting for ${url} to stop`);
}
