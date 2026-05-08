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
GET  http://localhost:8080/evidences/next?cursor=0
GET  http://localhost:8080/evidences/github/pull-request?repo=owner/repo&pullNumber=123
GET  http://localhost:8080/api/github/repos/{owner}/{repo}/pulls?state=all
GET  http://localhost:8080/api/github/repos/{owner}/{repo}/pulls/{number}
GET  http://localhost:8080/api/github/repos/{owner}/{repo}/pulls/search?q=author:usuario
POST http://localhost:8080/analyze
```

Para repositorios privados ou limites maiores de rate limit, defina `GITHUB_TOKEN` antes de iniciar o backend. A URL base da API tambem pode ser alterada via `github.api.base-url` em `application.properties`.

Exemplo:

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/analyze `
  -ContentType "application/json" `
  -Body '{"evidence":"Refactored payment module and increased test coverage","currentLevel":"L3","targetLevel":"L4"}'
```

## Fluxo interno

```text
Controller -> Service -> Analysis Engine (Mock) -> Framework Provider (Mock)
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
