import { apiGet } from "./http.mjs";

export function fetchProfile() {
  return apiGet("/profile", null, { auth: true });
}
