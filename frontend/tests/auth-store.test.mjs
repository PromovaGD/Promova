import assert from "node:assert/strict";
import test from "node:test";

import {
  clearAuthSession,
  loadAuthToken,
  loadAuthUser,
  saveAuthSession,
} from "../services/auth-store.mjs";

test("unavailable browser storage falls back without crashing or exposing the token", () => {
  globalThis.localStorage = {
    getItem() { throw new DOMException("Storage blocked", "SecurityError"); },
    setItem() { throw new DOMException("Storage blocked", "SecurityError"); },
    removeItem() { throw new DOMException("Storage blocked", "SecurityError"); },
  };
  clearAuthSession();

  const user = { id: 2, role: "EMPLOYEE" };
  assert.doesNotThrow(() => saveAuthSession("tab-only-token", user));
  assert.equal(loadAuthToken(), "tab-only-token");
  assert.deepEqual(loadAuthUser(), user);

  clearAuthSession();
  assert.equal(loadAuthToken(), null);
  assert.equal(loadAuthUser(), null);
});

test("corrupt stored user data is discarded while the token remains available for server validation", () => {
  const values = new Map([
    ["promova.auth-token", "valid-token"],
    ["promova.auth-user", "{not-json"],
  ]);
  globalThis.localStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  };

  assert.equal(loadAuthUser(), null);
  assert.equal(values.has("promova.auth-user"), false);
  assert.equal(loadAuthToken(), "valid-token");
});
