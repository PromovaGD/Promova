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

O motor de análise e o provider do career framework ainda são implementações mockadas de propósito. Eles estão isolados atrás de interfaces para que uma análise real com IA possa substituir essa camada sem alterar os contratos dos controllers.
