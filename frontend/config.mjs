export const API_BASE_URL =
  typeof window !== "undefined" && window.PROMOVA_API_BASE_URL
    ? window.PROMOVA_API_BASE_URL
    : "http://localhost:8080";

export const SESSION_KEYS = {
  authToken: "promova.auth-token",
  authUser: "promova.auth-user",
};
