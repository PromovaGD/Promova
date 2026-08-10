# Promova Backend

Backend PoC em Java com Spring Boot para analisar evidencias de carreira, capturar Pull Requests do GitHub e manter o motor de analise isolado atras de interfaces.

## Como executar

```powershell
cd backend
.\gradlew.bat bootRun
```

On macOS/Linux:

```sh
cd backend
./gradlew bootRun
```

O endpoint fica disponivel em:

```text
GET  http://localhost:8080/evidences?status=PENDING
GET  http://localhost:8080/evidences/{evidenceId}
GET  http://localhost:8080/evidences/github/pull-request?repo=owner/repo&pullNumber=123
GET  http://localhost:8080/api/github/repos/{owner}/{repo}/pulls?state=all
GET  http://localhost:8080/api/github/repos/{owner}/{repo}/pulls/{number}
GET  http://localhost:8080/api/github/repos/{owner}/{repo}/pulls/search?q=author:usuario
GET  http://localhost:8080/api/github/settings
PUT  http://localhost:8080/api/github/settings
POST http://localhost:8080/api/github/settings/test
POST http://localhost:8080/api/github/sync
POST http://localhost:8080/evidences/{evidenceId}/analysis
```

Para repositorios privados ou limites maiores de rate limit, defina `GITHUB_TOKEN` antes de iniciar o backend. A URL base da API tambem pode ser alterada via `github.api.base-url` em `application.properties`.

`/api/github/settings` recebe `{"repoSlug":"owner/repo","authorLogin":"login"}` e persiste
uma configuracao por usuario. `POST /api/github/settings/test` verifica o repositorio salvo e
`POST /api/github/sync` pagina pull requests fechados, importa apenas PRs merged do login
configurado dentro do lookback, e retorna `discovered`, `created`, `existing`, `failed`,
`lastSyncAt` e `lastSyncOutcome`. A Evidence usa a chave unica por usuario, fonte e externalId,
portanto uma repeticao informa os itens como existentes sem criar linhas duplicadas.

O acesso ao GitHub e exclusivamente pelo `GITHUB_TOKEN` do servidor. Repositorios privados
exigem que o token da empresa tenha acesso; isso nao e uma autorizacao GitHub por usuario, nao
usa OAuth e nenhum token pessoal e armazenado.

Configuracao opcional do sync:

```properties
github.sync.lookback-days=${GITHUB_SYNC_LOOKBACK_DAYS:90}
github.sync.page-size=${GITHUB_SYNC_PAGE_SIZE:50}
github.sync.max-pages=${GITHUB_SYNC_MAX_PAGES:10}
```

## Analise com IA via OpenRouter

O backend usa `promova.analysis.engine=mock` por padrao para funcionar localmente e no CI sem chave externa.

Para fazer uma analise real com IA:

1. Crie uma chave de API no OpenRouter.
2. Inicie o backend com o engine `openrouter`.

```powershell
cd backend
$env:PROMOVA_ANALYSIS_ENGINE="openrouter"
$env:OPENROUTER_API_KEY="sua-chave"
.\gradlew.bat bootRun
```

O modelo open-source/free padrao e:

```text
meta-llama/llama-3.3-70b-instruct:free
```

Para trocar o modelo:

```powershell
$env:OPENROUTER_MODEL="qwen/qwen3-next-80b-a3b-instruct:free"
```

Solicite a analise pelo endpoint autenticado, sem corpo de requisicao. O servidor carrega a
Evidence PENDING pertencente ao usuario autenticado, o perfil e o career framework, executa o
engine e persiste o resultado. A evidencia, os niveis e a classificacao nao sao enviados pelo
navegador:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/evidences/{evidenceId}/analysis `
  -Headers @{ Authorization = "Bearer <session-token>" }
```

Configuracoes relevantes:

```properties
promova.analysis.engine=${PROMOVA_ANALYSIS_ENGINE:mock}
openrouter.api-key=${OPENROUTER_API_KEY:}
openrouter.model=${OPENROUTER_MODEL:meta-llama/llama-3.3-70b-instruct:free}
openrouter.site-url=${OPENROUTER_SITE_URL:http://localhost:4173}
openrouter.app-name=${OPENROUTER_APP_NAME:Promova}
openrouter.max-tokens=${OPENROUTER_MAX_TOKENS:800}
openrouter.temperature=${OPENROUTER_TEMPERATURE:0.2}
```

O engine OpenRouter recebe a evidencia, os niveis atual/alvo e o career framework carregado pelo backend. O prompt de sistema instrui o modelo a:

- avaliar apenas com base na evidencia e no framework fornecidos;
- nao inventar fatos, metricas, escopo ou impacto;
- escolher `estimatedLevel` apenas entre os niveis existentes no framework;
- retornar somente JSON no contrato de `EvidenceAnalysisResponse`.

Se `PROMOVA_ANALYSIS_ENGINE` nao estiver definido como `openrouter`, o backend continuara usando o analisador mock.

## Career Framework

O framework padrao fica em:

```text
src/main/resources/career-framework.json
```

Voce pode editar esse arquivo diretamente ou apontar para outro JSON:

```powershell
$env:PROMOVA_FRAMEWORK_PATH="file:C:/caminho/para/career-framework.json"
```

Formato esperado:

```json
{
  "levels": {
    "L3": {
      "title": "Software Engineer I",
      "description": "Software Engineer I",
      "criteria": {
        "Writing code": "With guidance and support from more senior engineers...",
        "Testing": "Understands the basics about the test pyramid."
      }
    },
    "L4": {
      "title": "Software Engineer II",
      "description": "Software Engineer II",
      "criteria": {
        "Writing code": "Consistently writes code that is easily testable...",
        "Testing": "Understands all levels of testing in the testing pyramid."
      }
    }
  }
}
```

Exemplo:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/evidences/{evidenceId}/analysis `
  -Headers @{ Authorization = "Bearer <session-token>" }
```

## Fluxo interno

```text
Controller -> Service -> Analysis Engine -> Framework Provider (JSON)
```

## Testes

```powershell
cd backend
.\gradlew.bat test
```

On macOS/Linux:

```sh
cd backend
./gradlew test
```

## Perfis e migrations

Use `dev` para desenvolvimento local, `test` para testes isolados e `prod` para PostgreSQL:

```powershell
# Dev é o padrão de bootRun e usa H2 em backend/data/promova.
./gradlew.bat bootRun

# Os testes ativam test e usam H2 em memória, sem backend/data.
./gradlew.bat test

# Verifica migrations, startup e schema validation em banco limpo.
./gradlew.bat migrationStartupSmoke
```

O perfil `dev` mantém o console H2 e as origens `http://localhost:*` para preservar o fluxo local. Os perfis `test` e `prod` mantêm o console desabilitado; somente o perfil `dev` tem fallback H2 e localhost. O perfil `prod` exige `PROMOVA_DB_URL`, `PROMOVA_DB_USERNAME`, `PROMOVA_DB_PASSWORD` e `PROMOVA_CORS_ALLOWED_ORIGINS`, e usa `spring.jpa.hibernate.ddl-auto=validate`.

As migrations são aplicadas em ordem: `V1__create_core_schema.sql` cria o schema de usuários, sessões, perfis, evidências, integrações e análises; `V2__create_saved_analysis_reviews.sql` cria o histórico de revisões. O Hibernate apenas valida o resultado.

O banco H2 do protótipo foi criado antes do Flyway. O perfil `dev` usa `baseline-on-migrate=true` com baseline explícito na versão `2` para uma base existente equivalente ao schema completo da Task 7; isso não remove dados. Faça backup e confira as tabelas antes de aceitar o baseline. Em produção o baseline automático é desligado: uma base legada deve passar por baseline operacional aprovado na versão correta ou por migração manual se não for compatível. Nunca use `ddl-auto=update` para contornar uma falha de migration.

Configuração mínima de produção:

```text
SPRING_PROFILES_ACTIVE=prod
PROMOVA_DB_URL=jdbc:postgresql://db.example.com:5432/promova
PROMOVA_DB_USERNAME=promova
PROMOVA_DB_PASSWORD=<secret-from-secret-store>
PROMOVA_CORS_ALLOWED_ORIGINS=https://promova.example.com
```

As origens são separadas por vírgulas. O backend não registra nem devolve tokens; mantenha credenciais fora do Git e do log de deploy.
