# Promova

Promova é um protótipo para captura e análise de evidências de carreira, com backend em Java/Spring Boot e frontend leve em JavaScript.

A aplicação captura evidências de fontes conectadas, transforma essas informações em sinais de carreira revisáveis e envia o conteúdo para o fluxo de análise do backend.

## Estrutura do Projeto

```text
.
├── backend/          API Spring Boot, fluxo de análise e integração com GitHub
├── frontend/         Módulos da interface e funcionalidades do frontend
├── scripts/          Scripts locais de build, dev e lint do frontend
├── app.js            Bootstrap do frontend
├── index.html        HTML de entrada do frontend
└── styles.css        Estilos compartilhados do frontend
```

O output gerado do frontend fica em `dist/` e não deve ser versionado.

## Requisitos

- Java 21
- Node.js 20+
- Opcional: `GITHUB_TOKEN` para repositórios privados ou limites maiores na API do GitHub
- Opcional: `OPENROUTER_API_KEY` para habilitar análise real com LLM via OpenRouter

## Como Rodar Localmente

Inicie o backend:

```powershell
cd backend
.\gradlew.bat bootRun
```

Em outro terminal, inicie o frontend:

```powershell
npm run dev
```

O frontend inicia em `http://localhost:4173`, a menos que a porta já esteja em uso. Nesse caso, o servidor local tenta as próximas portas automaticamente.

## Habilitar Análise com IA

Por padrão, o backend usa o motor mock para permitir desenvolvimento local e CI sem depender de chave externa.

Para usar análise real via OpenRouter:

```powershell
cd backend
$env:PROMOVA_ANALYSIS_ENGINE="openrouter"
$env:OPENROUTER_API_KEY="sua-chave"
.\gradlew.bat bootRun
```

O modelo padrão é:

```text
meta-llama/llama-3.3-70b-instruct:free
```

Você pode trocar o modelo com:

```powershell
$env:OPENROUTER_MODEL="qwen/qwen3-next-80b-a3b-instruct:free"
```

## Editar o Career Framework

O framework atual fica em:

[backend/src/main/resources/career-framework.json](</C:/Users/João/Documents/Projects/FIAP/Startup/backend/src/main/resources/career-framework.json>)

Hoje ele contém:

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

Para usar outro arquivo sem alterar o repo:

```powershell
$env:PROMOVA_FRAMEWORK_PATH="file:C:/caminho/para/career-framework.json"
```

## Validações

Frontend:

```powershell
npm run check
```

Backend:

```powershell
cd backend
.\gradlew.bat testClasses
.\gradlew.bat bootJar
```

## API do Backend

Endpoints principais:

```text
GET  /evidences/next?cursor=0
GET  /evidences/github/pull-request?repo=owner/repo&pullNumber=123
POST /analyze
```

Endpoints da integração com GitHub:

```text
GET /api/github/repos/{owner}/{repo}/pulls?state=all
GET /api/github/repos/{owner}/{repo}/pulls/{number}
GET /api/github/repos/{owner}/{repo}/pulls/search?q=author:usuario
```

## Observações

O motor de análise está isolado atrás da interface `AnalysisEngine`. O mock segue disponível para desenvolvimento local, enquanto o provider `openrouter` usa um LLM real com prompt de sistema orientado pelo career framework enviado pelo backend.
