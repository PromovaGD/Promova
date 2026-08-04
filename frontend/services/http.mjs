import { API_BASE_URL } from "../config.mjs";
import { loadAuthToken } from "./auth-store.mjs";

export async function apiGet(path, params, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}${queryString(params)}`, {
    headers: buildHeaders(options),
  });
  return parseApiResponse(response);
}

export async function apiPost(path, body, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: buildHeaders(options, true),
    body: JSON.stringify(body ?? {}),
  });

  return parseApiResponse(response);
}

export async function apiDelete(path, params, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}${queryString(params)}`, {
    method: "DELETE",
    headers: buildHeaders(options),
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
}

async function parseApiResponse(response) {
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

    throw new Error(message);
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

  if (options.auth) {
    const token = loadAuthToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  return headers;
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
