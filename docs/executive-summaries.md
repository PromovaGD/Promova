# Sumários executivos da gestão

## Referência e adaptação

O arquivo `executive-summary.html` serve como referência de hierarquia visual: abertura editorial, números grandes, gráficos simples, cartões de acontecimentos e filtros. A nova aba **Sumários executivos** leva esse fluxo ao espaço de gestão da Promova, usando os registros que o produto já possui. O texto de abertura é montado a partir de contagens verificáveis; ele não atribui impacto de negócio nem inventa conclusões sobre o trabalho da equipe.

| No exemplo | Na Promova | Valor para o gestor |
| --- | --- | --- |
| Manchete e resumo da semana | Abertura com período, pessoas, evidências, análises e PRs capturados | Entender o volume recente em uma leitura |
| Faixa de estatísticas | Pessoas no escopo, evidências, análises, PRs capturados e mesclados | Ver dimensão e ritmo do trabalho |
| Gráficos de tendência e distribuição | Atividade semanal, situação atual das análises do período e fontes das evidências | Identificar mudanças de ritmo e concentração |
| Cartões de acontecimentos | Até seis análises recentes, com título, pessoa, fonte, impacto estimado e link para o registro | Ir do panorama à evidência original |
| Filtros e seções | Período de 7, 30 ou 90 dias, datas personalizadas e recorte por equipe, cargo e pessoa | Preparar conversas para escopos diferentes |

Os sinais de acompanhamento mostram a **situação atual** de revisões e objetivos, mesmo quando o período selecionado é histórico. O sumário indica essa diferença em cada cartão e na nota final. Os PRs incluem apenas registros capturados na Promova; “mesclados” é um subconjunto desses PRs. A distribuição das análises usa o status atual das análises criadas no período.

As métricas de uso de Copilot, classificação automática de tipo de trabalho, repositórios ativos e tempos de lead time do exemplo não foram portadas, pois a Promova ainda não coleta os dados necessários para calculá-las com precisão. Quando houver dados confiáveis e escopo claro, esses blocos poderão ser adicionados sem alterar a estrutura principal da página.

## Prévia e dados reais

A seleção **Prévia com dados fictícios** gera uma amostra local proporcional ao período escolhido, incluindo números, gráficos e cartões ilustrativos. Ela exibe um aviso visível e desabilita filtros de equipe, cargo e pessoa, porque a amostra não representa pessoas reais do espaço. Em **Dados do espaço**, a página consulta `/manager/dashboard` com os mesmos filtros do dashboard e os destaques são limitados ao escopo autorizado do gestor.

O perfil `dev` também pode semear registros demonstrativos reais no banco; nesse caso, a resposta da API marca `demoData` e a aba mostra o aviso **Ambiente de demonstração**. A prévia fictícia do navegador e os dados semeados no ambiente são identificados separadamente.

## Abrir a simulação no aplicativo local

Em um terminal, inicie uma API isolada com banco H2 temporário:

```sh
cd backend
SPRING_PROFILES_ACTIVE=dev SERVER_PORT=18082 \
  PROMOVA_DEV_DB_URL='jdbc:h2:mem:promova-summary-preview;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' \
  PROMOVA_CORS_ALLOWED_ORIGINS=http://localhost:14174 \
  PROMOVA_DEMO_DATA_ENABLED=true ./gradlew bootRun
```

Em outro terminal, na raiz do projeto:

```sh
PORT=14174 PROMOVA_API_BASE_URL=http://localhost:18082 npm run dev
```

Abra `http://localhost:14174/manage/summaries?period=30&preview=demo` e entre com `manager@promova.com` / `manager123`. A opção **Prévia com dados fictícios** usa a amostra local do frontend; **Dados do espaço** consulta a API e mostra os registros semeados no banco temporário. A prévia mantém a autenticação e a navegação de gestor. O banco em memória desaparece ao encerrar a API.
