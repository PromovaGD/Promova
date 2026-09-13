const useModernUi = window.PROMOVA_MODERN_UI !== false;
if (!useModernUi) {
  const path = window.location.pathname;
  let legacyPath = null;
  if (path.startsWith("/manage/")) legacyPath = "/manager";
  else if (path.startsWith("/workspace/")) {
    try {
      legacyPath = JSON.parse(localStorage.getItem("promova.auth-user") || "null")?.role === "MANAGER" ? "/manager" : "/dashboard";
    } catch {
      legacyPath = "/dashboard";
    }
  } else if (path === "/app/career-plan") legacyPath = "/profile";
  else if (path.startsWith("/app/framework")) legacyPath = "/dashboard?tab=framework";
  else if (path.startsWith("/app/integrations")) legacyPath = "/dashboard?tab=connections";
  else if (path.startsWith("/app/")) legacyPath = "/dashboard";
  if (legacyPath) window.history.replaceState({}, "", legacyPath);
}
const entrypoint = useModernUi ? "./frontend/modern-app.mjs" : "./frontend/app.mjs";

import(entrypoint).then((module) => {
  const start = useModernUi ? module.startModernApp : module.startApp;
  start(document.querySelector("#app"));
});
