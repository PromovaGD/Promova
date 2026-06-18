import { integrationCard } from "../components/cards.mjs";
import { appPage, pageHero } from "../components/layout.mjs";
import { escapeHtml } from "../utils/html.mjs";

export function authPage(state) {
  const isLogin = state.authMode !== "register";

  return appPage(
    `
    ${pageHero(
      "Acesso",
      isLogin ? "Entrar na sua conta" : "Criar sua conta",
      isLogin
        ? "Use seu e-mail e senha para acessar o painel de evidências e acompanhar sua evolução."
        : "Cadastre-se para salvar análises, acompanhar seu progresso e centralizar suas evidências.",
    )}
    <div class="auth-layout">
      <section class="form-card auth-card">
        <div class="auth-tabs" role="tablist" aria-label="Modo de autenticação">
          <button
            class="auth-tab ${isLogin ? "active" : ""}"
            type="button"
            data-action="switch-auth-login"
            role="tab"
            aria-selected="${isLogin}"
          >
            Entrar
          </button>
          <button
            class="auth-tab ${isLogin ? "" : "active"}"
            type="button"
            data-action="switch-auth-register"
            role="tab"
            aria-selected="${!isLogin}"
          >
            Cadastrar
          </button>
        </div>

        ${state.authError ? `<p class="auth-error">${escapeHtml(state.authError)}</p>` : ""}

        <form class="auth-form" data-auth-form="${isLogin ? "login" : "register"}">
          ${
            isLogin
              ? ""
              : `
            <label class="field">
              <span>Nome completo</span>
              <input type="text" name="name" autocomplete="name" required placeholder="Seu nome" />
            </label>
          `
          }
          <label class="field">
            <span>E-mail</span>
            <input type="email" name="email" autocomplete="email" required placeholder="voce@empresa.com" />
          </label>
          <label class="field">
            <span>Senha</span>
            <input type="password" name="password" autocomplete="${isLogin ? "current-password" : "new-password"}" required minlength="6" placeholder="Mínimo 6 caracteres" />
          </label>
          <button class="button primary" type="submit" ${state.authLoading ? "disabled" : ""}>
            ${state.authLoading ? "Aguarde..." : isLogin ? "Entrar" : "Criar conta"}
          </button>
        </form>

        <p class="auth-hint">
          ${
            isLogin
              ? 'Ainda não tem conta? <button class="link-button" type="button" data-action="switch-auth-register">Cadastre-se</button>'
              : 'Já tem conta? <button class="link-button" type="button" data-action="switch-auth-login">Entrar</button>'
          }
        </p>
      </section>

      <aside class="auth-aside">
        <div class="info-card soft-panel">
          <h3>Contas de demonstração</h3>
          <ul class="demo-accounts">
            <li><strong>Funcionário:</strong> joao.silva@empresa.com / senha123</li>
            <li><strong>Admin:</strong> admin@promova.com / admin123</li>
          </ul>
        </div>
        <div class="info-card soft-panel">
          <h3>Integrações disponíveis</h3>
          <p class="card-copy">GitHub já está integrado. Jira, Slack e LinkedIn permanecem como no fluxo atual do protótipo.</p>
          <div class="integration-grid compact">
            ${integrationCard("github", "GitHub", "PRs, commits, revisões")}
            ${integrationCard("calendar", "Jira", "Tarefas, sprints, entregas")}
            ${integrationCard("message", "Slack", "Comunicação, colaboração")}
          </div>
        </div>
      </aside>
    </div>
  `,
    { user: null, mode: "auth" },
  );
}
