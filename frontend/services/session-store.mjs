import { SESSION_KEYS } from "../config.mjs";

export function loadSessionEvidences() {
  return readJson(SESSION_KEYS.evidences, []);
}

export function saveSessionEvidences(evidences) {
  writeJson(SESSION_KEYS.evidences, evidences);
}

export function loadEvidenceCursor() {
  const rawCursor = readValue(SESSION_KEYS.cursor);
  const cursor = Number(rawCursor);
  return Number.isFinite(cursor) ? cursor : 0;
}

export function saveEvidenceCursor(cursor) {
  writeValue(SESSION_KEYS.cursor, String(cursor));
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
  if (typeof sessionStorage === "undefined") {
    return null;
  }

  return sessionStorage.getItem(key);
}

function writeValue(key, value) {
  if (typeof sessionStorage === "undefined") {
    return;
  }

  sessionStorage.setItem(key, value);
}
