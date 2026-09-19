import { loadAuthToken } from "./auth-store.mjs";
import { apiGet, apiPost } from "./http.mjs";

export async function registerUser(payload) {
  return apiPost("/auth/register", payload, { auth: false });
}

export async function loginUser(payload) {
  return apiPost("/auth/login", payload, { auth: false });
}

export async function logoutUser() {
  const token = loadAuthToken();
  if (!token) {
    return;
  }

  try {
    await apiPost("/auth/logout", {}, { auth: true });
  } catch {
    // Ignore logout failures and clear local session anyway.
  }
}

export async function fetchCurrentUser() {
  return apiGet("/auth/me", null, { auth: true });
}
