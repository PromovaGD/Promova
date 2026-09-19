const EMPLOYEE_HOME = "/app/overview";
const MANAGER_HOME = "/manage/people";

const definitions = [
  ["landing", /^\/$/, "public"],
  ["login", /^\/login$/, "auth"],
  ["register", /^\/register$/, "auth"],
  ["overview", /^\/app\/overview$/, "employee"],
  ["inbox", /^\/app\/inbox$/, "employee"],
  ["analyses", /^\/app\/analyses$/, "employee"],
  ["reports", /^\/app\/analyses\/reports$/, "employee"],
  ["framework", /^\/app\/framework$/, "employee"],
  ["criterion", /^\/app\/framework\/criteria\/([a-z0-9-]+)$/, "employee", ["criterionId"]],
  ["career-plan", /^\/app\/career-plan$/, "employee"],
  ["integrations", /^\/app\/integrations$/, "employee"],
  ["github", /^\/app\/integrations\/github$/, "employee"],
  ["github-import", /^\/app\/integrations\/github\/import$/, "employee"],
  ["people", /^\/manage\/people$/, "manager"],
  ["person-plan", /^\/manage\/people\/(\d+)\/career-plan$/, "manager", ["employeeId"]],
  ["objective-new", /^\/manage\/people\/(\d+)\/career-plan\/objectives\/new$/, "manager", ["employeeId"]],
  ["objective", /^\/manage\/people\/(\d+)\/career-plan\/objectives\/(\d+)$/, "manager", ["employeeId", "objectiveId"]],
  ["person-evidence", /^\/manage\/people\/(\d+)\/evidence$/, "manager", ["employeeId"]],
  ["person-analyses", /^\/manage\/people\/(\d+)\/analyses$/, "manager", ["employeeId"]],
  ["reviews", /^\/manage\/reviews$/, "manager"],
  ["terminology", /^\/manage\/career\/terminology$/, "manager"],
  ["roles", /^\/manage\/career\/roles$/, "manager"],
  ["role-new", /^\/manage\/career\/roles\/new$/, "manager"],
  ["role", /^\/manage\/career\/roles\/(\d+)$/, "manager", ["roleId"]],
  ["manager-framework", /^\/manage\/career\/framework$/, "manager"],
  ["evidence-detail", /^\/workspace\/people\/(\d+)\/evidence\/(\d+)$/, "resource", ["ownerId", "evidenceId"]],
  ["analysis-detail", /^\/workspace\/people\/(\d+)\/analyses\/(\d+)$/, "resource", ["ownerId", "analysisId"]],
];

const allowedQuery = {
  inbox: { status: ["pending", "dismissed"], page: "page", source: "text", from: "date", to: "date", selected: "id" },
  analyses: { page: "page", source: "text", from: "date", to: "date" },
  reports: { view: ["sources", "levels", "trend"], from: "date", to: "date" },
  framework: { level: "level", support: ["all", "missing", "supported"], from: "date", to: "date" },
  criterion: { level: "level", from: "date", to: "date" },
  "career-plan": { view: ["objectives", "context"], status: ["active", "completed", "archived", "all"] },
  github: { view: ["connection", "sync"] },
  "github-import": { pr: "id", page: "page" },
  people: { q: "text", role: "text", level: "level", page: "page" },
  "person-plan": { view: ["objectives", "context"] },
  "person-evidence": { status: ["pending", "dismissed", "analyzed"], source: "text", from: "date", to: "date", page: "page" },
  "person-analyses": { review: ["unreviewed", "needs-context", "accepted"], source: "text", from: "date", to: "date", page: "page" },
  reviews: { status: ["unreviewed", "needs-context", "accepted"], employee: "id", source: "text", from: "date", to: "date", page: "page" },
  roles: { status: ["active", "archived", "all"], page: "page" },
  "manager-framework": { level: "level" },
  "analysis-detail": { view: ["summary", "source", "history", "review"] },
};

export function matchRoute(locationLike) {
  const url = new URL(locationLike.href || String(locationLike), "http://promova.local");
  const definition = definitions.find(([, pattern]) => pattern.test(url.pathname));
  if (!definition) return { id: "not-found", access: "public", path: url.pathname, params: {}, query: {} };
  const [id, pattern, access, paramNames = []] = definition;
  const values = url.pathname.match(pattern)?.slice(1) || [];
  const params = Object.fromEntries(paramNames.map((name, index) => [name, name.endsWith("Id") ? Number(values[index]) : values[index]]));
  if (Object.values(params).some((value) => typeof value === "number" && (!Number.isSafeInteger(value) || value < 1))) {
    return { id: "not-found", access: "public", path: url.pathname, params: {}, query: {} };
  }
  return { id, access, path: url.pathname, params, query: normalizeQuery(id, url.searchParams) };
}

export function canonicalUrl(route) {
  const params = new URLSearchParams();
  Object.entries(route.query || {}).forEach(([key, value]) => params.set(key, String(value)));
  return `${route.path}${params.size ? `?${params}` : ""}`;
}

export function safeReturnTo(value) {
  if (!value || typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) return null;
  try {
    const url = new URL(value, "http://promova.local");
    if (url.origin !== "http://promova.local") return null;
    const route = matchRoute(url);
    return ["employee", "manager", "resource"].includes(route.access) ? canonicalUrl(route) : null;
  } catch {
    return null;
  }
}

export function roleHome(user) {
  return user?.role === "MANAGER" ? MANAGER_HOME : EMPLOYEE_HOME;
}

export function canAccess(route, user) {
  if (["public", "auth"].includes(route.access)) return true;
  if (!user) return false;
  if (route.access === "employee") return user.role === "EMPLOYEE";
  if (route.access === "manager") return user.role === "MANAGER";
  if (route.access === "resource") return user.role === "MANAGER" || Number(route.params.ownerId) === Number(user.id);
  return false;
}

function normalizeQuery(routeId, searchParams) {
  const schema = allowedQuery[routeId] || {};
  const result = {};
  Object.entries(schema).forEach(([key, rule]) => {
    const raw = searchParams.get(key);
    if (!raw) return;
    if (Array.isArray(rule) && rule.includes(raw)) result[key] = raw;
    if (rule === "page" && /^\d+$/.test(raw) && Number(raw) >= 1) result[key] = Number(raw);
    if (rule === "id" && /^\d+$/.test(raw) && Number(raw) >= 1) result[key] = Number(raw);
    if (rule === "date" && /^\d{4}-\d{2}-\d{2}$/.test(raw)) result[key] = raw;
    if (rule === "level" && /^[A-Za-z0-9_-]{1,20}$/.test(raw)) result[key] = raw;
    if (rule === "text" && raw.length <= 120) result[key] = raw;
  });
  if (result.from && result.to && result.from > result.to) delete result.to;
  return result;
}

export const ROUTE_DEFINITIONS = definitions.map(([id,,access]) => ({ id, access }));
