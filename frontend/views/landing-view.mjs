import { siteHeader, footerLinks } from "../components/layout.mjs";
import {
  cardGrid,
  infoCard,
  integrationCard,
  previewFeedItem,
  sectionHeading,
  stepCard,
} from "../components/cards.mjs";
import { iconSvg } from "../components/icons.mjs";

export function landingPage() {
  const problemCards = [
    infoCard("doc", "Processos manuais", "A coleta de evidências é manual e demorada, desperdiçando tempo valioso de gestores.", "info-card problem-card"),
    infoCard("shield", "Falta de transparência", "As pessoas não sabem o que precisam fazer para evoluir de nível.", "info-card problem-card"),
    infoCard("users", "Decisões enviesadas", "Muitas decisões ainda se baseiam em opinião e memória, não em evidências do trabalho ao longo do tempo.", "info-card problem-card"),
  ];
  const solutionCards = [
    infoCard("chart", "Registro contínuo", "Evidências organizadas ao longo do tempo para evitar perda de contexto.", "info-card solution-card"),
    infoCard("plug", "Integração com o fluxo", "Uma experiência simples para centralizar sinais do trabalho já realizado.", "info-card solution-card"),
    infoCard("flow", "Framework de carreira", "Estrutura clara de níveis para comparar impacto e evolução de forma consistente.", "info-card solution-card"),
    infoCard("trend", "Visão para lideranças", "Resumo visual do progresso para apoiar conversas mais justas e objetivas.", "info-card solution-card"),
  ];
  const benefitCards = [
    infoCard("shield", "Redução de viés", "Decisões baseadas em métricas objetivas.", "info-card feature-card"),
    infoCard("chart", "Baseado em dados", "Evidências concretas de performance.", "info-card feature-card"),
    infoCard("doc", "Transparência", "Critérios claros para todas as pessoas.", "info-card feature-card"),
    infoCard("trend", "Evolução clara", "Visibilidade do progresso contínuo.", "info-card feature-card"),
  ];
  const audienceCards = [
    audienceCard("users", "Para engenheiros", [
      "Visualize todas as suas evidências de trabalho em um só lugar.",
      "Acompanhe sua evolução de carreira em tempo real.",
      "Entenda claramente o que precisa para evoluir.",
      "Tenha transparência total no processo.",
    ]),
    audienceCard("shield", "Para gestores", [
      "Veja o progresso de todo o time em tempo real.",
      "Tome decisões de evolução baseadas em dados concretos.",
      "Reduza o tempo gasto com coleta manual de evidências.",
      "Justifique decisões com evidências objetivas.",
    ]),
  ];
  const integrationCards = [
    integrationCard("github", "GitHub", "PRs, commits, revisões"),
    integrationCard("calendar", "Jira", "Tarefas, sprints, entregas"),
    integrationCard("message", "Slack", "Comunicação, colaboração"),
  ];
  const steps = [
    stepCard(1, "Trabalho diário", "A pessoa realiza suas tarefas normalmente."),
    stepCard(2, "Coleta automática", "O sistema captura dados das ferramentas."),
    stepCard(3, "Análise com IA", "A IA analisa com base no framework de carreira."),
    stepCard(4, "Evidências", "Registros organizados e categorizados."),
    stepCard(5, "Decisão", "A liderança decide com base em dados."),
  ];

  return `
    <div class="site-page">
      <section class="surface-light hero" id="product">
        <div class="container">
          ${siteHeader("landing")}
          <div class="hero-grid">
            <div class="hero-copy">
              <span class="eyebrow">Promoções justas baseadas em evidências reais</span>
              <h1>Promova sua carreira com evidências</h1>
              <p>Registre suas evidências, acompanhe seu impacto e simplifique conversas de carreira com uma experiência clara e direta.</p>
              <div class="hero-actions">
                <button class="button primary" type="button" data-action="open-dashboard">Começar agora</button>
                <a class="button secondary" href="#how-it-works">Ver como funciona</a>
              </div>
            </div>
            <div class="preview-shell">
              <div class="preview-card">
                <div class="preview-head">
                  <span>Painel</span>
                  <strong>1º tri 2026</strong>
                </div>
                ${previewFeedItem("green", "Solicitação de merge aprovada", "Arquitetura do novo módulo", "Sênior")}
                ${previewFeedItem("blue", "Funcionalidade entregue", "Sistema de autenticação", "Pleno")}
                ${previewFeedItem("purple", "Mentoria realizada", "Onboarding de 3 desenvolvedores", "Sênior")}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="surface-muted section">
        <div class="container">
          ${sectionHeading("O problema atual", "Processos tradicionais de evolução ainda dependem de memória, opinião e pouca visibilidade.")}
          ${cardGrid(problemCards, "three")}
        </div>
      </section>

      <section class="surface-light section">
        <div class="container">
          ${sectionHeading("A solução", "Decisões mais claras com evidências concretas e um fluxo simples para quem usa.")}
          ${cardGrid(solutionCards, "four")}
        </div>
      </section>

      <section class="dark-band section" id="how-it-works">
        <div class="container">
          ${sectionHeading("Como funciona", "Um processo simples, guiado e fácil de explicar para qualquer pessoa do time.")}
          ${cardGrid(steps, "five")}
        </div>
      </section>

      <section class="surface-light section" id="dashboard-preview">
        <div class="container">
          ${sectionHeading("Painel intuitivo", "Visualize o progresso de forma clara e organizada.")}
          ${landingDashboardPreview()}
        </div>
      </section>

      <section class="surface-muted section" id="audience">
        <div class="container">
          ${sectionHeading("Para quem é", "")}
          ${cardGrid(audienceCards, "two")}
        </div>
      </section>

      <section class="surface-light section" id="benefits">
        <div class="container">
          ${sectionHeading("Benefícios", "Transforme seu processo de carreira em algo claro e confiável.")}
          ${cardGrid(benefitCards, "four")}
        </div>
      </section>

      <section class="surface-muted section" id="integrations">
        <div class="container">
          ${sectionHeading("Integrações", "Conecte com as ferramentas que você já usa.")}
          <div class="integration-grid">${integrationCards.join("")}</div>
        </div>
      </section>

      <section class="footer-cta section">
        <div class="container cta-inner">
          <h2>Comece a usar o Promova hoje</h2>
          <p>Transforme seu processo de carreira em algo justo, transparente e baseado em dados.</p>
          <button class="button primary" type="button" data-action="open-dashboard">Começar agora grátis</button>
        </div>
      </section>

      ${footerLinks()}
    </div>
  `;
}

function landingDashboardPreview() {
  return `
    <div class="dashboard-shell">
      <div class="dashboard-metrics">
        <div class="metric-card blue">
          <span class="metric-label">Evidências coletadas</span>
          <strong class="metric-value">142</strong>
          <span class="metric-sub">+18 neste mês</span>
        </div>
        <div class="metric-card green">
          <span class="metric-label">Nível atual</span>
          <strong class="metric-value">Pleno</strong>
          <span class="metric-sub">75% para Sênior</span>
        </div>
        <div class="metric-card purple">
          <span class="metric-label">Impacto</span>
          <strong class="metric-value">Excelente</strong>
          <span class="metric-sub">Acima da média</span>
        </div>
      </div>
      <div class="dashboard-feed">
        ${previewFeedItem("blue", "Arquitetura de sistema", "Design de microsserviços - GitHub", "Sênior")}
        ${previewFeedItem("green", "Planejamento de sprint", "Liderança técnica - Jira", "Pleno")}
        ${previewFeedItem("purple", "Code review", "15 PRs revisados - GitHub", "Pleno")}
      </div>
    </div>
  `;
}

function audienceCard(icon, title, items) {
  return `
    <article class="info-card">
      <div class="card-icon">${iconSvg(icon)}</div>
      <h3 class="card-title">${title}</h3>
      <ul class="check-list">
        ${items.map((item) => `<li>${item}</li>`).join("")}
      </ul>
    </article>
  `;
}
