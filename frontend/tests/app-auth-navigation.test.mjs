import assert from "node:assert/strict";
import test from "node:test";

function installDom(token, user) {
  const values = new Map();
  if (token) {
    values.set("promova.auth-token", token);
  }
  if (user) {
    values.set("promova.auth-user", JSON.stringify(user));
  }

  globalThis.localStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  };
  globalThis.window = {
    addEventListener() {},
    dispatchEvent() { return true; },
    scrollTo() {},
  };
  globalThis.document = { documentElement: { scrollTop: 0 }, body: { scrollTop: 0 } };

  return {
    innerHTML: "",
    addEventListener() {},
    querySelector() { return null; },
  };
}

test("manager session lands in the console without loading employee workspace APIs", async () => {
  const manager = { id: 1, name: "Marina Gestora", email: "manager@example.com", role: "MANAGER" };
  const root = installDom("manager-token", manager);
  const urls = [];
  globalThis.fetch = async (url) => {
    urls.push(String(url));
    const path = new URL(String(url)).pathname;
    const body =
      path === "/auth/me"
        ? manager
        : path === "/manager/settings"
          ? { labels: {}, activeRoles: [], frameworkLevels: [] }
          : [];
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  };

  const { startApp } = await import(`../app.mjs?manager-startup=${Date.now()}`);
  await startApp(root);

  assert.deepEqual(
    urls.map((url) => new URL(url).pathname),
    ["/auth/me", "/manager/settings", "/manager/settings/job-roles", "/manager/employees"],
  );
  assert.match(root.innerHTML, /Manager Console/);
  assert.doesNotMatch(root.innerHTML, /data-action="open-dashboard"/);
  assert.doesNotMatch(root.innerHTML, /data-action="open-profile"/);
  assert.doesNotMatch(root.innerHTML, /data-action="open-form"/);
});

test("401 during bootstrap clears the session and opens login", async () => {
  const root = installDom("expired-token", { id: 2, role: "EMPLOYEE" });
  globalThis.fetch = async () =>
    new Response(JSON.stringify({ message: "Autenticação necessária." }), {
      status: 401,
      headers: { "content-type": "application/json" },
    });

  const { startApp } = await import(`../app.mjs?expired-startup=${Date.now()}`);
  await startApp(root);

  assert.equal(globalThis.localStorage.getItem("promova.auth-token"), null);
  assert.equal(globalThis.localStorage.getItem("promova.auth-user"), null);
  assert.match(root.innerHTML, /Sua sessão expirou/);
  assert.match(root.innerHTML, /data-auth-form="login"/);
});
