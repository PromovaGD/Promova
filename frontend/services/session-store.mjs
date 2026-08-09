import { SESSION_KEYS } from "../config.mjs";

export function clearLegacyEvidenceStorage() {
  if (typeof sessionStorage === "undefined") {
    return;
  }

  sessionStorage.removeItem(SESSION_KEYS.cursor);
  sessionStorage.removeItem(SESSION_KEYS.evidences);
}
