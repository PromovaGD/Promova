# Plano de execução: profiles `mock` e `qa`

**Estado:** proposta para implementação em outra tarefa. Este documento não altera a configuração de execução.

## Objetivo e decisão

Selecionar um único profile ao iniciar o backend. `mock` deve funcionar sem credenciais nem chamadas externas, com respostas previsíveis para percorrer os fluxos da interface. `qa` deve executar os mesmos controllers e regras de negócio contra um banco isolado e chamar GitHub e OpenRouter de verdade. O uso diário de IA deve ocorrer em `qa`; não deve depender de trocar apenas `PROMOVA_ANALYSIS_ENGINE` dentro de `dev`.

| Profile | Dados e respostas HTTP | GitHub | Análise | Banco | Uso |
| --- | --- | --- | --- | --- | --- |
| `mock` | Fixtures conhecidas, com ações persistidas para que analisar, dispensar e revisar tenham efeito coerente | Respostas locais determinísticas | Motor determinístico | H2 local separado de QA | Demonstração e desenvolvimento sem rede |
| `qa` | Dados criados por usuários/testes; nenhum seed de demonstração | API real | OpenRouter real, incluindo título | PostgreSQL exclusivo de QA | Validação funcional com provedores reais |

“Tudo mockado” significa que **nenhuma integração externa é acessada** em `mock` e que os dados exibidos vêm de fixtures controladas. Manter os controllers, validação, autorização e transições de estado reais evita duplicar cada endpoint e permite que as respostas mudem de modo consistente depois das ações do usuário. Um servidor que devolvesse JSON fixo para cada rota exigiria uma segunda implementação do produto e não validaria esses fluxos.

`prod` já existe no repositório, mas fica fora desta entrega. Preservar seu comportamento; a configuração e os testes específicos de produção serão uma etapa futura. `test` e `ci` continuam como profiles internos de testes automatizados.

## Diagnóstico do estado atual

- [`application.properties`](../backend/src/main/resources/application.properties) define `dev` como padrão e `PROMOVA_ANALYSIS_ENGINE` como seletor do motor. Isso permite análise real sem trocar o restante do ambiente.
- [`application-dev.properties`](../backend/src/main/resources/application-dev.properties) usa H2 em arquivo, console H2 e CORS local. [`DataSeeder`](../backend/src/main/java/br/com/promova/config/DataSeeder.java) e [`DemoDashboardDataSeeder`](../backend/src/main/java/br/com/promova/config/DemoDashboardDataSeeder.java) injetam contas/dados de demonstração em `dev`.
- [`MockAnalysisEngine`](../backend/src/main/java/br/com/promova/analysis/engine/MockAnalysisEngine.java) só existe em `dev`, `test` e `ci`; [`OpenRouterAnalysisEngine`](../backend/src/main/java/br/com/promova/analysis/engine/openrouter/OpenRouterAnalysisEngine.java) e [`OpenRouterChatClient`](../backend/src/main/java/br/com/promova/analysis/engine/openrouter/OpenRouterChatClient.java) são selecionados por propriedade. O cliente real já falha sem chave.
- [`GithubApiClient`](../backend/src/main/java/br/com/promova/github/client/GithubApiClient.java) é sempre um bean real e [`GithubPullRequestService`](../backend/src/main/java/br/com/promova/github/service/GithubPullRequestService.java) depende da classe concreta. Assim, `mock` ainda poderia fazer chamadas ao GitHub.
- O [E2E](../e2e/application.e2e.mjs) inicia `dev` com um servidor GitHub local; o [smoke de CI](../scripts/ci-smoke.sh) inicia `ci`. Ambos precisarão continuar isolados de provedores reais.

## Configuração proposta

Criar `backend/src/main/resources/application.yaml` apenas com propriedades compartilhadas e seguras: nome, porta, Flyway, `ddl-auto=validate`, framework e parâmetros comuns de timeout. Retirar dele defaults de motor, banco, CORS permissivo e URLs de provedores. Criar dois arquivos específicos:

| Arquivo | Conteúdo principal |
| --- | --- |
| `application-mock.yaml` | H2 em arquivo próprio ou memória persistente durante o processo; console opcional só local; CORS local; `promova.mode: mock`; fixture/seed habilitados; integrações locais. |
| `application-qa.yaml` | PostgreSQL QA via variáveis de ambiente; Flyway sem baseline automático; console e seeds desligados; CORS explícito; `promova.mode: qa`; URL real do GitHub; OpenRouter real. |

Não colocar `spring.profiles.active` dentro de um YAML específico de profile. Spring Boot carrega automaticamente `application-{profile}.yaml` quando o profile é selecionado; a propriedade de ativação pertence ao arquivo comum, ao ambiente ou à linha de comando ([documentação de profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)).

Converter o arquivo comum `.properties` para YAML sem manter os dois formatos com o mesmo nome base. Os arquivos `application-test.properties`, `application-ci.properties` e `application-prod.properties` podem permanecer durante esta etapa, sem duplicata YAML do mesmo profile; Spring Boot aceita os formatos, mas dá precedência a `.properties` quando os dois coexistem no mesmo local ([ordem de configuração](https://docs.spring.io/spring-boot/reference/features/external-config.html)). Migrar esses três depois, em uma mudança separada de formato, sem alterar seus valores.

Remover o default implícito `dev`. Exigir seleção explícita de `mock` ou `qa` para execução local e falhar na inicialização se ambos forem ativados juntos. O guard de startup também deve confirmar **um único** `AnalysisEngine` e um único cliente GitHub para o modo escolhido. Variáveis como `PROMOVA_ANALYSIS_ENGINE` não devem conseguir trocar silenciosamente o motor de um profile; a seleção dos beans deve depender do profile. Atualizar referências a `dev` nos scripts, seeds e README; manter `test`/`ci` explícitos no Gradle e CI.

### Contrato de ambiente de QA

| Variável | Finalidade |
| --- | --- |
| `PROMOVA_QA_DB_URL`, `PROMOVA_QA_DB_USERNAME`, `PROMOVA_QA_DB_PASSWORD` | PostgreSQL exclusivo de QA. Sem fallback para H2 ou banco `dev`. |
| `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` | Chamada real; verificar que o modelo escolhido está disponível e aceita o formato de saída configurado. |
| `GITHUB_TOKEN` | Repositório de teste e limites de uso da API real do GitHub. |
| `PROMOVA_CORS_ALLOWED_ORIGINS` | Origem explícita do frontend de QA. |

Validar variáveis obrigatórias na inicialização e apresentar apenas os **nomes** ausentes, sem registrar valores. Não guardar segredos nos YAMLs. Restringir a URL do banco QA e nunca apontar seus testes de limpeza para produção. Hoje `github.api.base-url` está fixo no arquivo comum; torná-lo configurável por ambiente para os testes com servidor local e fixá-lo no YAML QA para a API real. Um token GitHub pode ser opcional para leitura pública em desenvolvimento, mas o cenário completo de QA deve usar credenciais próprias.

## Separação dos componentes

1. **Análise:** usar `MockAnalysisEngine` em `mock`/`test`/`ci` e `OpenRouterAnalysisEngine` + `OpenRouterChatClient` em `qa` (preservando `prod` atual). A mesma resposta salva título, nível, confiança e justificativa. Em QA, verificar um modelo com suporte ao formato JSON exigido; considerar JSON Schema estrito para reduzir respostas inválidas, conforme [suporte do OpenRouter](https://openrouter.ai/docs/guides/features/structured-outputs). Manter validação do backend mesmo com saída estruturada.
2. **GitHub:** introduzir uma interface pequena, por exemplo `GithubGateway#get(path)`, no limite de rede usado por `GithubPullRequestService`. Implementação real em QA; implementação de fixtures em mock. Reaproveitar os cenários do stub E2E para busca, detalhes de PR, arquivos, erro e paginação. Nenhuma chamada de `GithubApiClient` real deve ocorrer em mock. O endpoint de “testar conexão” deve indicar claramente quando o resultado é simulado.
3. **Dados locais:** mover os seeds de desenvolvimento para `mock`. Usar contas e registros de demonstração identificáveis, ids estáveis quando possível, datas relativas controladas por relógio injetado e títulos compatíveis com os dados da fonte. Em mock, respostas de GET vêm dessas fixtures persistidas; POST/PUT/DELETE seguem a lógica real e atualizam o estado local. QA não executa seeds nem reutiliza o banco de mock.
4. **Autorização e frontend:** manter contratos HTTP, DTOs e permissões iguais nos dois profiles. Mostrar um indicador discreto de ambiente simulado em mock, para que uma análise ou “conexão verificada” de demonstração não pareça uma resposta da IA ou do GitHub real. O frontend só precisa apontar para o backend selecionado.

## Sequência sugerida para o agente implementador

1. Registrar um baseline: `npm run check`, testes do backend e teste de startup/migrations. Anotar falhas já existentes antes de mexer nos profiles.
2. Criar `application.yaml`, `application-mock.yaml` e `application-qa.yaml`, com seleção explícita e validação de configuração. Confirmar por teste quais propriedades efetivas cada profile recebe.
3. Trocar seleção de motor por profile e bloquear combinações ambíguas. Testar startup de mock sem chaves e falha rápida de QA sem chave/banco.
4. Extrair o gateway do GitHub, criar fixtures mock e manter cliente real em QA. Testar que mock não abre conexão externa e que QA aponta para a API real.
5. Mover seeds e atualizar E2E, smoke de CI, comandos Gradle e README. Garantir que `test`/`ci` permaneçam determinísticos.
6. Rodar um roteiro QA opt-in com credenciais: criar usuário em QA, configurar repositório de teste, buscar/importar um PR real, analisar via OpenRouter, verificar título salvo e abrir análise, depois testar uma resposta inválida/falha do provedor sem mudar a evidência para `ANALYZED`.

## Como iniciar depois da implementação

```sh
cd backend
SPRING_PROFILES_ACTIVE=mock ./gradlew bootRun

# Em outro processo, com as variáveis QA acima carregadas:
SPRING_PROFILES_ACTIVE=qa ./gradlew bootRun
```

Em PowerShell: `$env:SPRING_PROFILES_ACTIVE="mock"; .\gradlew.bat bootRun` (ou `qa`). Também aceitar `./gradlew bootRun --args='--spring.profiles.active=qa'`. O log de startup deve mostrar o profile, o tipo de banco e os nomes das integrações selecionadas, sem segredos.

## Critérios de aceite

- Iniciar `mock` sem `OPENROUTER_API_KEY` ou `GITHUB_TOKEN` e percorrer busca, captura, análise e revisão com dados previsíveis; observar **zero chamadas de rede** aos provedores.
- Iniciar `qa` sem qualquer variável obrigatória falha claramente; com credenciais, usa PostgreSQL QA, GitHub real e OpenRouter real. Nenhum seed de demonstração aparece.
- Mudar apenas `SPRING_PROFILES_ACTIVE` seleciona o YAML e os beans corretos. `mock,qa` juntos falham. Um override de variável não consegue ativar o motor mock em QA nem o cliente real em mock.
- O contrato HTTP e o fluxo de análise permanecem iguais; o título retornado pelo OpenRouter persiste e reaparece na evidência/análise. Erros do provedor não geram análise parcial.
- Testes de configuração e isolamento passam, `npm run check` passa, e o smoke de QA com provedores reais é executado separadamente com credenciais, sem torná-lo obrigatório no CI comum.

## Fora desta etapa

Não criar nem ampliar o profile `prod`, não conectar a dados de produção, não substituir a suíte `test`/`ci` por chamadas pagas ao OpenRouter e não manter duas implementações de todos os controllers HTTP.
