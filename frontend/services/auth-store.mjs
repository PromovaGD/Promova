import { SESSION_KEYS } from "../config.mjs";

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
    return fallback;
  }
}

function writeJson(key, value) {
  writeValue(key, JSON.stringify(value));
}

function readValue(key) {
  if (typeof localStorage === "undefined") {
    return null;
  }

  return localStorage.getItem(key);
}

function writeValue(key, value) {
  if (typeof localStorage === "undefined") {
    return;
  }

  localStorage.setItem(key, value);
}

function removeValue(key) {
  if (typeof localStorage === "undefined") {
    return;
  }

  localStorage.removeItem(key);
}
