import assert from "node:assert/strict";
import test from "node:test";

import { loadAuthToken, loadAuthUser, saveAuthSession } from "../services/auth-store.mjs";
import { fetchEmployeeAnalyses, fetchEmployees } from "../services/auth-api.mjs";
import {
  loadReviewsForEmployee,
  submitReviewForEmployee,
} from "../services/analyses-api.mjs";
import { apiGet } from "../services/http.mjs";

function installBrowserFakes() {
  const values = new Map();
  const events = [];
  globalThis.localStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  };
  globalThis.CustomEvent = class CustomEvent {
    constructor(type, init = {}) {
      this.type = type;
      this.detail = init.detail;
    }
  };
  globalThis.window = {
    dispatchEvent: (event) => {
      events.push(event);
      return true;
    },
  };
  return events;
}

test("401 clears local authentication and requests the login view", async () => {
  const events = installBrowserFakes();
  saveAuthSession("expired-token", { id: 2, role: "EMPLOYEE" });
  globalThis.fetch = async () =>
    new Response(JSON.stringify({ message: "Autenticação necessária." }), {
      status: 401,
      headers: { "content-type": "application/json" },
    });

  await assert.rejects(() => apiGet("/profile", null, { auth: true }), /Autenticação necessária/);

  assert.equal(loadAuthToken(), null);
  assert.equal(loadAuthUser(), null);
  assert.deepEqual(events.map((event) => event.type), ["promova:auth-expired"]);
});

test("403 preserves the valid session and publishes a permission error", async () => {
  const events = installBrowserFakes();
  const user = { id: 2, role: "EMPLOYEE" };
  saveAuthSession("valid-token", user);
  globalThis.fetch = async () =>
    new Response(JSON.stringify({ message: "Você não tem permissão para realizar esta ação." }), {
      status: 403,
      headers: { "content-type": "application/json" },
    });

  await assert.rejects(
    () => apiGet("/manager/employees", null, { auth: true }),
    /não tem permissão/,
  );

  assert.equal(loadAuthToken(), "valid-token");
  assert.deepEqual(loadAuthUser(), user);
  assert.deepEqual(events.map((event) => event.type), ["promova:permission-denied"]);
  assert.match(events[0].detail.message, /não tem permissão/);
});

test("server and network failures preserve the valid session", async () => {
  const events = installBrowserFakes();
  const user = { id: 2, role: "EMPLOYEE" };
  saveAuthSession("valid-token", user);
  globalThis.fetch = async () =>
    new Response(JSON.stringify({ message: "Serviço indisponível." }), {
      status: 503,
      headers: { "content-type": "application/json" },
    });

  await assert.rejects(() => apiGet("/profile", null, { auth: true }), /indisponível/);
  assert.equal(loadAuthToken(), "valid-token");
  assert.deepEqual(loadAuthUser(), user);
  assert.deepEqual(events, []);

  globalThis.fetch = async () => {
    throw new TypeError("Failed to fetch");
  };
  await assert.rejects(() => apiGet("/profile", null, { auth: true }), /Failed to fetch/);
  assert.equal(loadAuthToken(), "valid-token");
  assert.deepEqual(loadAuthUser(), user);
  assert.deepEqual(events, []);
});

test("supported manager API calls use only the manager namespace", async () => {
  installBrowserFakes();
  saveAuthSession("manager-token", { id: 1, role: "MANAGER" });
  const urls = [];
  globalThis.fetch = async (url) => {
    urls.push(String(url));
    const body = String(url).endsWith("/employees") || String(url).includes("/analyses?")
      ? []
      : { analysisId: 7, currentStatus: "UNREVIEWED", history: [] };
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  };

  await fetchEmployees();
  await fetchEmployeeAnalyses(2, { from: "2026-01-01T00:00:00.000Z" });
  await loadReviewsForEmployee(2, 7);
  await submitReviewForEmployee(2, 7, { status: "ACCEPTED" });

  assert.equal(urls.length, 4);
  assert.ok(urls.every((url) => url.includes("/manager/")));
  assert.ok(urls.every((url) => !url.includes(`/${"ad"}min/`)));
});
