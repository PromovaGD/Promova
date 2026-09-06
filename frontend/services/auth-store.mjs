import { SESSION_KEYS } from "../config.mjs";

const memoryStorage = new Map();

export function loadAuthToken() {
  return readValue(SESSION_KEYS.authToken);
}

export function saveAuthSession(token, user) {
  writeValue(SESSION_KEYS.authToken, token);
  writeJson(SESSION_KEYS.authUser, user);
}

export function loadAuthUser() {
  return readJson(SESSION_KEYS.authUser, null);
}

export function loadAuthRoute() {
  return readValue(SESSION_KEYS.authRoute);
}

export function saveAuthRoute(route) {
  writeValue(SESSION_KEYS.authRoute, route);
}

export function clearAuthSession() {
  removeValue(SESSION_KEYS.authToken);
  removeValue(SESSION_KEYS.authUser);
}

function readJson(key, fallback) {
  const rawValue = readValue(key);
  if (!rawValue) {
    return fallback;
  }

  try {
    return JSON.parse(rawValue);
  } catch {
    removeValue(key);
    return fallback;
  }
}

function writeJson(key, value) {
  writeValue(key, JSON.stringify(value));
}

function readValue(key) {
  try {
    if (typeof localStorage !== "undefined") {
      const value = localStorage.getItem(key);
      if (value !== null) {
        memoryStorage.set(key, value);
      } else {
        memoryStorage.delete(key);
      }
      return value;
    }
  } catch {
    // Storage can be disabled by browser/privacy policy. Use tab-local memory instead.
  }
  return memoryStorage.get(key) ?? null;
}

function writeValue(key, value) {
  const storedValue = String(value);
  memoryStorage.set(key, storedValue);
  try {
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(key, storedValue);
    }
  } catch {
    // Keep the current tab usable when persistent browser storage is unavailable.
  }
}

function removeValue(key) {
  memoryStorage.delete(key);
  try {
    if (typeof localStorage !== "undefined") {
      localStorage.removeItem(key);
    }
  } catch {
    // The in-memory copy is already gone.
  }
}
