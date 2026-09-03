import assert from "node:assert/strict";
import test from "node:test";

import { apiGet, apiPost } from "../services/http.mjs";

const AUTH_TOKEN_KEY = "promova.auth-token";

test("public authentication failures do not look like expired sessions", async () => {
  const storage = createStorage({ [AUTH_TOKEN_KEY]: "existing-token" });
  const authEvents = [];
  const originalFetch = globalThis.fetch;
  const originalLocalStorage = globalThis.localStorage;
  const originalWindow = globalThis.window;

  globalThis.localStorage = storage;
  globalThis.window = {
    dispatchEvent(event) {
      authEvents.push(event.type);
    },
  };
  globalThis.fetch = async () => createErrorResponse(401, "Credenciais inválidas.");

  try {
    await assert.rejects(
      apiPost("/auth/login", { email: "wrong@example.com", password: "wrong" }, { auth: false }),
      (error) => {
        assert.equal(error.status, 401);
        assert.equal(error.message, "Credenciais inválidas.");
        assert.equal(error.isUnauthorized, true);
        return true;
      },
    );

    assert.equal(storage.getItem(AUTH_TOKEN_KEY), "existing-token");
    assert.deepEqual(authEvents, []);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.localStorage = originalLocalStorage;
    globalThis.window = originalWindow;
  }
});

test("authenticated 401 responses still clear the session and notify the app", async () => {
  const storage = createStorage({ [AUTH_TOKEN_KEY]: "expired-token" });
  const authEvents = [];
  const originalFetch = globalThis.fetch;
  const originalLocalStorage = globalThis.localStorage;
  const originalWindow = globalThis.window;

  globalThis.localStorage = storage;
  globalThis.window = {
    dispatchEvent(event) {
      authEvents.push(event.type);
    },
  };
  globalThis.fetch = async (_url, options) => {
    assert.equal(options.headers.Authorization, "Bearer expired-token");
    return createErrorResponse(401, "Sessão inválida.");
  };

  try {
    await assert.rejects(apiGet("/auth/me", null, { auth: true }));

    assert.equal(storage.getItem(AUTH_TOKEN_KEY), null);
    assert.deepEqual(authEvents, ["promova:auth-expired"]);
  } finally {
    globalThis.fetch = originalFetch;
    globalThis.localStorage = originalLocalStorage;
    globalThis.window = originalWindow;
  }
});

function createStorage(initialValues = {}) {
  const values = new Map(Object.entries(initialValues));

  return {
    getItem(key) {
      return values.get(key) ?? null;
    },
    setItem(key, value) {
      values.set(key, String(value));
    },
    removeItem(key) {
      values.delete(key);
    },
  };
}

function createErrorResponse(status, message) {
  return {
    ok: false,
    status,
    async json() {
      return { message };
    },
  };
}
