import { escapeHtml } from "../utils/html.mjs";

export function siteHeader(mode) {
  const landingLinks = [
    ["#product", "Produto"],
    ["#how-it-works", "Como funciona"],
    ["#benefits", "Benefícios"],
  ];
  const appLinks = [
    { label: "Início", action: "back-home" },
    { label: "Painel", action: "open-dashboard" },
    { label: "Novidades", action: "open-form" },
  ];
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
  const ctaAction = mode === "landing" ? "open-dashboard" : "open-form";
  const ctaLabel = mode === "landing" ? "Começar agora" : "Ver nova evidência";

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
      <button class="button primary button-cta" type="button" data-action="${ctaAction}">${escapeHtml(ctaLabel)}</button>
    </header>
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

export function appPage(content) {
  return `
    <div class="site-page">
      <section class="surface-light section">
        <div class="container">
          ${siteHeader("app")}
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
