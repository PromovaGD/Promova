export const LEVELS = ["L1", "L2", "L3", "L4", "L5"];

export function createId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }

  return `evidence-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function levelIndex(level) {
  return LEVELS.indexOf(level);
}

export function readinessFor(impactLevel, targetLevel) {
  return levelIndex(impactLevel) >= levelIndex(targetLevel)
    ? `Esta evidência está alinhada com o seu alvo atual de ${targetLevel}.`
    : `Esta evidência ainda está abaixo do seu alvo de ${targetLevel}, então pode ser fortalecida com resultados mensuráveis.`;
}

export function formatTimestamp(isoDate) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(isoDate));
}

export function badgeClass(level) {
  if (level === "L4" || level === "L5") {
    return "badge success";
  }

  if (level === "L3") {
    return "badge info";
  }

  return "badge neutral";
}
