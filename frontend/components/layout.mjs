import { escapeHtml } from "../utils/html.mjs";
import { roleLabel } from "../utils/format.mjs";

export function siteHeader(mode, user = null) {
  const landingLinks = [
    ["#product", "Produto"],
    ["#how-it-works", "Como funciona"],
    ["#benefits", "Benefícios"],
  ];
  const isManager = user?.role === "MANAGER";
  const appLinks = [{ label: "Início", action: "back-home" }];

  if (isManager) {
    appLinks.push({ label: "Manager Console", action: "open-manager" });
  } else {
    appLinks.push({ label: "Painel", action: "open-dashboard" });
  }

  if (mode !== "landing" && user && !isManager) {
    appLinks.push({ label: "Perfil", action: "open-profile" });
    appLinks.push({ label: "Nova evidência", action: "open-form" });
  }

  const nav =
    mode === "landing"
      ? landingLinks
          .map(([href, label]) => `<a class="nav-link" href="${href}">${escapeHtml(label)}</a>`)
          .join("")
      : appLinks
          .map(
            ({ label, action }) =>
              `<button class="nav-link button-reset" type="button" data-action="${action}">${escapeHtml(label)}</button>`,
          )
          .join("");

  const ctaAction =
    mode === "landing" ? "open-auth" : isManager ? "open-manager" : user ? "open-form" : "open-auth";
  const ctaLabel =
    mode === "landing" ? "Começar agora" : isManager ? "Manager Console" : user ? "Ver nova evidência" : "Entrar";

  return `
    <header class="site-header">
      <a class="brand" href="#" data-action="back-home" aria-label="Ir para a página inicial">
        <span class="brand-mark">PV</span>
        <span class="brand-copy">
          <span class="brand-name">Promova</span>
          <span class="brand-tagline">Evolução de carreira com evidências</span>
        </span>
      </a>
      <nav class="site-nav" aria-label="Principal">${nav}</nav>
      <div class="header-actions">
        ${user ? userBadge(user) : ""}
        <button class="button primary button-cta" type="button" data-action="${ctaAction}">${escapeHtml(ctaLabel)}</button>
        ${user ? `<button class="button ghost button-logout" type="button" data-action="logout">Sair</button>` : ""}
      </div>
    </header>
  `;
}

function userBadge(user) {
  const initials = user.name
    .split(" ")
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || "")
    .join("");

  return `
    <div class="user-badge" aria-label="Usuário autenticado">
      <span class="user-badge-avatar">${escapeHtml(initials)}</span>
      <span class="user-badge-copy">
        <strong>${escapeHtml(user.name)}</strong>
        <span>${escapeHtml(roleLabel(user.role))}</span>
      </span>
    </div>
  `;
}

export function footerLinks() {
  return `
    <footer class="site-footer">
      <div class="container footer-row">
        <a class="brand footer-brand" href="#" data-action="back-home">
          <span class="brand-mark">PV</span>
          <span class="brand-copy">
            <span class="brand-name">Promova</span>
            <span class="brand-tagline">Evolução de carreira com evidências</span>
          </span>
        </a>
        <nav class="footer-links" aria-label="Rodapé">
          <a href="#product">Produto</a>
          <a href="#how-it-works">Como funciona</a>
          <a href="#benefits">Benefícios</a>
        </nav>
        <p class="footer-copy">© 2026 Promova. Todos os direitos reservados.</p>
      </div>
    </footer>
  `;
}

export function appPage(content, options = {}) {
  const mode = options.mode || "app";
  const user = options.user || null;

  return `
    <div class="site-page">
      <section class="surface-light section">
        <div class="container">
          ${siteHeader(mode, user)}
          ${content}
        </div>
      </section>
      ${footerLinks()}
    </div>
  `;
}

export function pageHero(eyebrow, title, copy) {
  return `
    <div class="page-hero compact">
      <span class="eyebrow">${escapeHtml(eyebrow)}</span>
      <h1 class="page-title">${escapeHtml(title)}</h1>
      <p class="page-copy">${escapeHtml(copy)}</p>
    </div>
  `;
}
