import { API_BASE_URL } from "../config.mjs";
import { clearAuthSession, loadAuthToken } from "./auth-store.mjs";

export async function apiGet(path, params, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}${queryString(params)}`, {
    headers: buildHeaders(options),
  });
  return parseApiResponse(response, options);
}

export async function apiPost(path, body, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: buildHeaders(options, true),
    body: JSON.stringify(body ?? {}),
  });

  return parseApiResponse(response, options);
}

export async function apiPut(path, body, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "PUT",
    headers: buildHeaders(options, true),
    body: JSON.stringify(body ?? {}),
  });

  return parseApiResponse(response, options);
}

export async function apiDelete(path, params, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}${queryString(params)}`, {
    method: "DELETE",
    headers: buildHeaders(options),
  });

  return parseApiResponse(response, options);
}

async function parseApiResponse(response, options = {}) {
  if (!response.ok) {
    let message = `Request failed: ${response.status}`;

    try {
      const payload = await response.json();
      if (payload?.message) {
        message = payload.message;
      }
    } catch {
      // Keep default message.
    }

    const error = new Error(message);
    error.status = response.status;
    error.isUnauthorized = response.status === 401;
    error.isForbidden = response.status === 403;

    if (error.isUnauthorized && options.auth !== false) {
      clearAuthSession();
      notifyAuthExpired();
    }

    throw error;
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

function buildHeaders(options, withJson = false) {
  const headers = {};

  if (withJson) {
    headers["Content-Type"] = "application/json";
  }

  if (options.auth !== false) {
    const token = loadAuthToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  return headers;
}

function notifyAuthExpired() {
  if (typeof window !== "undefined" && typeof window.dispatchEvent === "function") {
    window.dispatchEvent(new Event("promova:auth-expired"));
  }
}

function queryString(params) {
  if (!params) {
    return "";
  }

  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      searchParams.set(key, String(value));
    }
  });

  const value = searchParams.toString();
  return value ? `?${value}` : "";
}
