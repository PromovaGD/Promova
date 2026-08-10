import { API_BASE_URL } from "../config.mjs";
import { apiGet } from "./http.mjs";
import { clearAuthSession, loadAuthToken } from "./auth-store.mjs";

export function fetchProfile() {
  return apiGet("/profile", null, { auth: true });
}

export async function updateProfile(payload) {
  const headers = { "Content-Type": "application/json" };
  const token = loadAuthToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}/profile`, {
    method: "PUT",
    headers,
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    try {
      const body = await response.json();
      if (body?.message) {
        message = body.message;
      }
    } catch {
      // Keep the status-based message when the response has no JSON body.
    }

    const error = new Error(message);
    error.status = response.status;
    error.isUnauthorized = response.status === 401;
    error.isForbidden = response.status === 403;
    if (error.isUnauthorized) {
      clearAuthSession();
      notifyAuthExpired();
    }
    throw error;
  }

  return response.json();
}

function notifyAuthExpired() {
  if (typeof window !== "undefined" && typeof window.dispatchEvent === "function") {
    window.dispatchEvent(new Event("promova:auth-expired"));
  }
}
