# Backlog executável — atualizações do Promova

Este documento transforma os itens de `Atualizações Promova.md` em tarefas de
implementação pequenas o bastante para um agente assumir uma por vez. Ele usa o
estado atual do repositório como ponto de partida — não trata o conteúdo do arquivo
de origem como instruções para executar ações fora deste projeto.

## Estado confirmado do código

- O papel privilegiado atual é `ADMIN`; ele vê uma lista global de funcionários e
  pode alterar apenas o papel do usuário. A interface ainda oferece **Meu painel**.
- Os níveis `L3` e `L4` são enviados pelo navegador/hard-coded durante a captura.
  Não há entidade de cargo, plano de carreira ou objetivos por pessoa.
- A leitura da IA já tem engines `mock` e `openrouter`, mas `POST /analyze` é
  público e o navegador persiste uma classificação que ele mesmo monta em
  `POST /analyses`. A evidência capturada também não é persistida.
- GitHub é a única integração real e só importa uma PR por vez. Jira e Slack são
  exemplos estáticos no fluxo atual, não integrações.
- O front-end é JavaScript modular sem framework. A navegação e o estado central
  ficam em `frontend/app.mjs`; o CSS compartilhado fica em `styles.css`.

Essas lacunas viram tarefas explícitas abaixo para que os itens solicitados sejam
implementáveis de ponta a ponta, e não apenas mudanças visuais sobre dados falsos.

## Decisões de produto adotadas neste backlog

1. **Administrador passa a ser Gestor.** O novo papel canônico exposto pela API é
   `GESTOR`; não haverá tela de “meu painel” para quem estiver nesse papel. O gestor
   administra as pessoas sob sua visibilidade global atual. Hierarquia/equipes não
   entram neste escopo.
2. **Nível, cargo e objetivos têm fontes distintas.** A empresa mantém cargos,
   níveis e nomenclaturas; o gestor atribui cargo, nível atual, nível-alvo,
   características e objetivos a uma pessoa; a pessoa pode consultar seu plano, mas
   não se autoatribui.
3. **A observação da pessoa faz parte da análise.** Ela é opcional, é salva junto da
   evidência analisada e é enviada à IA como contexto delimitado. Não altera a fonte
   original e não permite editar uma análise já concluída.
4. **Integrações são conexões reais, não mocks de produção.** A primeira entrega usa
   GitHub com o token de servidor já documentado. Jira e Slack só começam após a
   organização disponibilizar suas credenciais/instalações OAuth ou de workspace;
   os dois gates estão descritos nas tarefas correspondentes.

## Ordem de entrega

| Ordem | Tarefa | Prioridade | Depende de |
| --- | --- | --- | --- |
| 1 | T01 — Papéis, autorização e renomeação para Gestor | P0 | estado atual |
| 2 | T02 — Evidência persistente e análise confiável no servidor | P0 | T01 |
| 3 | T03 — Catálogo de nomenclaturas, cargos e níveis | P0 | T01 |
| 4 | T04 — Plano de carreira por pessoa | P0 | T02, T03 |
| 5 | T05 — Console do gestor | P0 | T04 |
| 6 | T06 — Integração definitiva da IA e observações da pessoa | P0 | T02, T04 |
| 7 | T07 — Dashboard separado por função | P1 | T04, T06 |
| 8 | T08 — Leitura expansível de evidências | P1 | T02, T07 |
| 9 | T09 — Central de conexões de fontes | P1 | T02, T07 |
| 10 | T10 — Sincronização automática de PRs do GitHub | P1 | T09 |
| 11 | T11 — Importação de evidências do Jira | P2 | T09 |
| 12 | T12 — Importação de evidências do Slack | P2 | T09 |

Não executar tarefas que mexem no mesmo fluxo em paralelo. Em especial, T02, T04,
T06, T07 e T08 alteram entidades/estado/navegação compartilhados e devem partir da
última tarefa aceita. T10, T11 e T12 usam o mesmo contrato de fontes; GitHub deve
ser aceito antes de Jira e Slack.

## Contrato de trabalho para cada agente

Cada agente deve limitar o diff aos arquivos de que é dono na tarefa, preservar
arquivos não rastreados e mudanças de outros agentes, e nunca fazer `reset`,
`checkout` destrutivo, push ou merge. A devolutiva deve conter:

- status (`concluída`, `parcial` ou `bloqueada`), commit/base e arquivos alterados;
- decisões tomadas e qualquer desvio de contrato;
- comandos de verificação e seus resultados;
- passos manuais para validar a interface, se houver;
- lacunas, credenciais ou decisões que ainda dependam do time.

Validação mínima de todas as tarefas:

```powershell
npm run check
cd backend
.\gradlew.bat test --no-daemon --no-configuration-cache
```

Se o Gradle falhar por processo/JDK antes de executar testes, registrar o erro e a
versão do Java/Gradle; não remover nem enfraquecer testes para mascará-lo.

---

## T01 — Papéis, autorização e renomeação para Gestor

**Objetivo.** Tornar `GESTOR` o papel privilegiado e aplicar autenticação/autorização
coerente antes de ampliar os seus poderes.

**Escopo de implementação.**

- Criar uma migração de dados compatível que converta `ADMIN` para `GESTOR` nas
  contas existentes. Atualizar `UserRole`, seed, DTOs, endpoints e testes para que a
  API passe a retornar `GESTOR`; não aceitar `ADMIN` em novas requisições.
- Renomear o domínio HTTP de `/admin/**` para `/manager/**`. Se uma rota de
  compatibilidade for indispensável nesta entrega, mantê-la temporariamente
  autenticada, documentada e marcada para remoção — a interface deve usar apenas as
  novas rotas.
- Restringir todas as rotas de domínio (`/analyses`, `/analyze`, `/evidences/**` e
  `/api/github/**`) a uma sessão válida. Restringir `/manager/**` a `GESTOR` e
  devolver `401` para sessão ausente/inválida e `403` para papel insuficiente.
- Alterar textos, navegação e `roleLabel` para “Gestor”; o cabeçalho do gestor deve
  levar direto ao Console do gestor. Remover o botão/rota **Meu painel** somente
  para esse papel, sem quebrar o dashboard de `EMPLOYEE`.
- No cliente, uma resposta `401` limpa a sessão e abre login; `403` mantém a sessão
  e mostra uma mensagem de permissão.

**Arquivos sob responsabilidade.** `backend/.../auth/**`, `user/UserRole.java`,
`config/WebConfig.java`, `config/ApiExceptionHandler.java`,
`manager/**` (novo, movendo o controlador atual), migrações/seed necessários,
`frontend/services/http.mjs`, `frontend/services/auth-*.mjs`,
`frontend/components/layout.mjs`, `frontend/utils/format.mjs`, `frontend/app.mjs` e
testes focados de autenticação/gestor.

**Fora de escopo.** Equipes, gestor direto, permissões por departamento, edição de
nível/cargo e a reformulação visual completa do console.

**Aceite.** Uma pessoa não acessa dados de outra; pessoa não acessa rota de gestor;
gestor não vê seu próprio dashboard; dados/contas seed existentes continuam válidos
depois da migração; nenhum token/senha aparece em resposta ou log.

**Verificação adicional.** Cobrir em teste: login, token inválido, `401`, `403`,
acesso de pessoa e acesso de gestor. Validar no navegador os dois fluxos de
navegação.

---

## T02 — Evidência persistente e análise confiável no servidor

**Objetivo.** Substituir o carrossel de evidências e a análise salva pelo cliente por
um fluxo rastreável: `fonte → evidência da pessoa → análise no servidor`.

**Escopo de implementação.**

- Criar entidade JPA `Evidence` pertencente a `User`, com `id`, `source`,
  `externalId`, `sourceMeta`, `sourceUrl`, `content`, `occurredAt`, `capturedAt`,
  `updatedAt` e status `PENDING`, `ANALYZED` ou `DISMISSED`.
- Garantir unicidade por `owner + source + externalId` e nunca expor uma evidência
  de outro dono por ID, filtro ou mensagem de erro.
- Criar `GET /evidences`, `GET /evidences/{id}` e ação autenticada para dispensar
  uma evidência pendente. Remover o cursor do `sessionStorage` como fonte de dados e
  retirar exemplos estáticos de Jira/Slack do comportamento de produção.
- Criar `POST /evidences/{id}/analysis`. O servidor deve carregar evidência, perfil
  e framework, chamar `AnalysisEngine`, persistir resultado e marcar a evidência
  como `ANALYZED` em uma única operação. A requisição não pode aceitar nível,
  classificação, confiança, justificativa, usuário ou datas produzidas pelo cliente.
- Repetir a análise de uma mesma evidência deve retornar o resultado já salvo (ou um
  conflito explícito), sem chamar/bilhar o modelo duas vezes. Falhas mantêm a
  evidência em `PENDING` e não salvam resultado parcial.
- Atualizar a API e o estado do front-end para ler inbox e histórico do servidor;
  tornar `POST /analyses` e `POST /analyze` indisponíveis para uso de produto.

**Arquivos sob responsabilidade.** Todo o pacote `backend/.../evidence/**`, a parte
transacional necessária em `backend/.../analysis/**`, ajustes mínimos em
`github/**`, `frontend/services/evidence-api.mjs`, `analysis-api.mjs`,
`analyses-api.mjs`, `session-store.mjs`, `frontend/app.mjs`, views que hoje dependem
da evidência pendente e testes de entidade/repositório/controlador.

**Fora de escopo.** Interface final de detalhe, comentários, conexões persistentes e
mudança do motor OpenRouter.

**Aceite.** Evidências persistem após reinício, filtros de status funcionam,
importar o mesmo item não duplica, somente o dono pode consultar/dispensar/analisar,
e uma classificação não pode ser forjada pelo navegador.

**Verificação adicional.** Testar caminho feliz, falha do engine, retry e acesso
cruzado com `AnalysisEngine` mockado; apresentar os estados antes/depois no handoff.

---

## T03 — Catálogo de nomenclaturas, cargos e níveis

**Objetivo.** Permitir que o gestor mantenha os nomes usados pela empresa e defina
cargos vinculados aos níveis do framework, em vez de ter rótulos fixos no código.

**Escopo de implementação.**

- Criar configuração organizacional persistente com rótulos editáveis pelo gestor
  para, no mínimo: `gestor`, `pessoa`, `cargo`, `nível`, `objetivo` e
  `características`. Usar valores-padrão atuais na primeira execução.
- Criar entidade de `JobRole`/cargo com nome, descrição, status ativo e níveis
  permitidos. Os níveis possíveis vêm do `career-framework.json`; o catálogo não
  pode inventar um ID de nível que o framework não possua.
- Criar endpoints de gestor para listar/criar/editar/arquivar cargos e atualizar as
  nomenclaturas. Entregar endpoint de leitura autenticado com nomenclaturas, cargos
  ativos e níveis/títulos do framework para os formulários.
- Criar uma tela de Configurações do gestor com abas “Nomenclaturas” e “Cargos”,
  validação acessível e confirmação para arquivar. Aplicar os rótulos configurados
  nas telas tocadas por este backlog, com fallback seguro enquanto a configuração
  carrega.
- Impedir arquivamento de um cargo atribuído a alguém sem uma alternativa explícita:
  responder `409` e informar quantas pessoas precisam ser migradas.

**Arquivos sob responsabilidade.** Novos pacotes `organization/**` e/ou
`career/**`, extensões mínimas em `framework/**`, migrações, testes, novos serviços e
views de configuração, `frontend/app.mjs`, `layout.mjs`, `format.mjs` e estilos
correspondentes.

**Fora de escopo.** Atribuição individual, objetivos individuais, alteração do JSON
do framework na tela e edição de critérios pela IA.

**Aceite.** Gestor consegue mudar um rótulo e criar/arquivar cargo; formulários usam
os valores do backend; pessoa não altera catálogo; cargo em uso não é apagado; nível
inválido é recusado no servidor.

**Verificação adicional.** Testar autorização, validação, `409` de cargo em uso e
renderização após atualizar uma nomenclatura.

---

## T04 — Plano de carreira por pessoa: cargo, níveis, características e objetivos

**Objetivo.** Dar ao gestor poder real de configurar o contexto de carreira de cada
pessoa e torná-lo a única fonte de nível para a análise.

**Escopo de implementação.**

- Criar perfil de carreira persistente por pessoa com cargo, nível atual, nível-alvo,
  características (lista de tags curtas) e objetivos. Cada objetivo possui texto,
  estado (`ACTIVE`, `COMPLETED`, `ARCHIVED`), data-alvo opcional e datas de auditoria.
- Validar no servidor que cargo está ativo, níveis existem no framework e o nível
  alvo está acima do atual segundo a ordem declarada do framework. Registrar defaults
  válidos para usuários/seed existentes.
- Criar endpoints: leitura do próprio plano para pessoa; leitura e atualização do
  plano de qualquer pessoa pelo gestor. Toda alteração do gestor deve registrar
  `updatedBy` e `updatedAt`; pessoa não pode editar cargo, níveis ou características.
- Criar, no Console do gestor, um formulário de plano com seletor de cargo, níveis,
  características e CRUD de objetivos. Criar para a pessoa uma visão somente leitura
  de “Meu plano” que mostre cargo, caminho de nível e objetivos ativos.
- Alterar a análise de T02 para carregar níveis desse perfil, e nunca mais receber
  `currentLevel`/`targetLevel` do navegador ou de uma captura GitHub.

**Arquivos sob responsabilidade.** Novo pacote `profile/**` (ou `careerplan/**`),
`user/**` apenas para relacionamento necessário, `framework/**`, migrações/seed,
ajustes precisos em `analysis/**`, endpoints do gestor, serviços/views de perfil,
`frontend/app.mjs`, layout e testes focados.

**Fora de escopo.** Regras de promoção, probabilidade de promoção, equipe/hierarquia,
edição do catálogo de T03 e integrações externas.

**Aceite.** Dados sobrevivem a reinício; gestor consegue atribuir e alterar plano de
outra pessoa; pessoa só visualiza o próprio; níveis e cargos inválidos são recusados;
cada nova análise usa exatamente os níveis do perfil salvo.

**Verificação adicional.** Cobrir alvo igual/inferior, cargo arquivado, usuário sem
permissão e a chamada do `AnalysisEngine` recebendo os níveis persistidos.

---

## T05 — Console do gestor sem “Meu painel”

**Objetivo.** Substituir a área administrativa baseada em dashboard de funcionário
por uma área de gestão orientada a pessoas, planos e acompanhamento.

**Escopo de implementação.**

- Renomear visualmente a área para **Console do gestor** e remover qualquer link,
  botão ou fallback de “Meu painel” para `GESTOR`.
- Criar listagem de pessoas com busca por nome/e-mail, filtro por cargo e nível,
  resumo de nível atual/alvo e quantidade de objetivos ativos. O estado vazio precisa
  funcionar sem dados seed.
- Criar detalhe de pessoa com abas: **Plano de carreira** (T04), **Evidências** e
  **Análises**. A aba de evidências deve reutilizar somente APIs de gestão autorizada,
  sem carregar o dashboard do gestor como se fosse funcionário.
- Acrescentar ação de alteração de papel `EMPLOYEE`/`GESTOR` com confirmação e
  proteção contra remover o último gestor ativo. Não permitir que o gestor altere o
  próprio papel nesta tela.
- Garantir rotas/links acessíveis, deep link seguro e mensagens claras para 401/403,
  404 e nenhum resultado.

**Arquivos sob responsabilidade.** `backend/.../manager/**`, consultas de perfil
read-only necessárias, `frontend/views/admin-view.mjs` (renomear/migrar se adequado),
novas subviews de gestor, serviços de gestor, `frontend/app.mjs` e CSS do console.

**Fora de escopo.** Revisão humana da IA, criação de equipe, novas regras de
visibilidade, edição do framework e dashboard da pessoa.

**Aceite.** Gestor entra sempre no Console, não encontra “Meu painel”, vê e filtra
pessoas, altera planos existentes e abre evidências/análises da pessoa; employee não
consegue carregar nenhuma dessas telas/APIs.

**Verificação adicional.** Validar no navegador com gestor e pessoa, e testar os
filtros/autorizações no backend.

---

## T06 — Integração definitiva da IA com observações da pessoa

**Objetivo.** Tornar o uso de OpenRouter confiável para produção e permitir que a
pessoa contextualize uma evidência antes da análise.

**Escopo de implementação.**

- Estender a análise de T02 com `userObservation` opcional, limitado em tamanho e
  normalizado. Persistir a observação imutavelmente com a evidência/análise; nunca
  sobrescrever o conteúdo trazido pela integração.
- No detalhe de uma evidência `PENDING`, oferecer campo “Observação para a análise”
  e botão para analisar. Bloquear duplo clique e mostrar estados de envio, sucesso e
  erro. Após `ANALYZED`, exibir a observação como contexto usado, sem edição.
- Atualizar contrato interno do `AnalysisEngine` e prompt OpenRouter para separar
  `sourceEvidence` de `userObservation`, instruindo o modelo a usar ambos somente
  como contexto e a não inventar fatos. Manter saída JSON com schema validado:
  nível pertencente ao framework, confiança permitida, justificativa e listas com
  limites razoáveis.
- Configurar timeout de conexão/leitura, tratamento de resposta malformada e erros
  HTTP do provedor. Erro transiente deve ser apresentado sem expor chave, prompt ou
  corpo bruto; erro deixa evidência pendente. Não fazer retry automático que possa
  gerar custo duplicado — usar a idempotência de T02.
- Separar profiles `dev`/`test`/`prod`: mock permitido somente em dev/test; em prod
  falhar ao iniciar se `PROMOVA_ANALYSIS_ENGINE=openrouter` não tiver chave/modelo
  válido. Documentar todas as variáveis, sem incluir segredo em arquivo versionado.
- Adicionar logs estruturados sem conteúdo sensível, métrica/registro simples de
  êxito, falha e duração por provider, e teste de parsing/validação com cliente HTTP
  fake.

**Arquivos sob responsabilidade.** `backend/.../analysis/**`,
`analysis/engine/openrouter/**`, propriedades/profiles e documentação do backend,
`frontend/views/evidence-view.mjs`, APIs de análise/evidência, `frontend/app.mjs`,
estilos e testes de engine/controlador.

**Fora de escopo.** Troca para outro fornecedor, streaming/chat, nova análise de
resultado concluído e alteração de objetivos.

**Aceite.** A IA recebe a observação e a fonte claramente separadas; entrada/saída é
validada; falha não persiste análise incompleta; produção não inicia com configuração
OpenRouter inválida; nenhuma chave aparece na interface, API ou log.

**Verificação adicional.** Usar cliente fake para saída válida, JSON inválido,
timeout e erro HTTP; demonstrar fluxo mock com e sem observação.

---

## T07 — Dashboard separado por função

**Objetivo.** Reduzir a poluição do painel da pessoa separando acompanhamento,
evidências, plano de carreira e conexões em páginas claras.

**Escopo de implementação.**

- Redefinir a navegação da pessoa em quatro destinos: **Resumo**, **Evidências**,
  **Meu plano** e **Conexões**. O gestor mantém apenas os destinos de seu console e
  configurações.
- Deixar o Resumo com poucos indicadores de carreira: cargo/níveis, objetivos ativos,
  evidências pendentes e últimas análises. Ele deve usar dados persistidos, sem
  controles de captura/importação nem o detalhe completo de análises.
- Mover inbox, filtros de fonte/status/período e ação de analisar para **Evidências**.
  Mover o perfil de T04 para **Meu plano**. Reservar **Conexões** para T09.
- Preservar deep links e back navigation; manter estados vazios, carregamento, falha
  e acessibilidade de teclado/semântica. Remover código/estado obsoleto do dashboard
  em vez de manter duas versões da mesma função.

**Arquivos sob responsabilidade.** `frontend/views/dashboard-view.mjs`, novas views
de inbox/profile/connections quando necessário, `layout.mjs`, `frontend/app.mjs`,
serviços de leitura e `styles.css`. Só mudar backend se faltar uma leitura já
necessária para o resumo.

**Fora de escopo.** Novo design visual completo, integração de provider e mudança
das regras de análise.

**Aceite.** Cada função aparece em uma única área, pessoa consegue voltar ao resumo
sem perder filtros relevantes, gestor não vê navegação de funcionário, e não restam
botões de importação/captura no Resumo.

**Verificação adicional.** Conferir os quatro destinos em viewport desktop e mobile,
tabulação e os estados sem evidências/sem objetivos.

---

## T08 — Evidências com leitura expansível e recolhível

**Objetivo.** Tornar a leitura das evidências escaneável, com expansão no clique para
conteúdo completo e recolhimento previsível.

**Escopo de implementação.**

- Na lista de **Evidências**, renderizar cada item como um `button`/controle acessível
  que alterna um painel de detalhe na própria lista. Fechado: fonte, data, status,
  trecho seguro e nível, se analisada. Aberto: conteúdo completo, observação da
  pessoa, metadados, resultado da IA e ações permitidas pelo status.
- Usar `aria-expanded`, `aria-controls`, foco visível e controles por Enter/Espaço.
  O texto não pode depender apenas de cor ou animação. Respeitar `prefers-reduced-motion`.
- Manter no máximo uma evidência expandida por vez, fechar a anterior ao abrir outra,
  preservar a expansão ao atualizar filtros quando o item continuar na lista e
  limpá-la quando o item deixar de existir.
- Escapar todo conteúdo de integração/observação e prevenir layout quebrado por texto
  longo. Detalhe de gestor é somente leitura; a pessoa vê apenas suas ações.

**Arquivos sob responsabilidade.** View e estado da inbox/evidência, `frontend/app.mjs`,
`frontend/utils/html.mjs` se necessário, `styles.css` e testes de interface possíveis.

**Fora de escopo.** Troca de API, paginação, alteração de análise e um modal separado.

**Aceite.** Clique e teclado expandem/recolhem sem navegar indevidamente; detalhes
completos aparecem e somem; conteúdo malicioso é exibido como texto; a lista continua
legível em telas pequenas.

**Verificação adicional.** Cobrir manualmente PENDING, ANALYZED e DISMISSED, texto
extenso e navegação somente por teclado.

---

## T09 — Central de conexões e contrato comum de fontes

**Objetivo.** Preparar GitHub, Jira e Slack para usar uma mesma experiência de conexão
e um mesmo caminho de normalização de evidência.

**Escopo de implementação.**

- Criar contrato de backend para uma conexão por pessoa e provedor: `provider`,
  `status`, identificação não secreta da conta/workspace, `connectedAt`, `lastSyncAt`,
  resumo da última sincronização e erro sanitizado. Nunca devolver token/segredo.
- Criar adapter normalizado de descoberta: `source`, `externalId`, `content`,
  `sourceUrl`, `sourceMeta`, `author`, `occurredAt` e metadados permitidos. Todos os
  adapters devem persistir via o mesmo serviço de deduplicação de T02.
- Criar APIs autenticadas para listar conexões, iniciar autorização, receber callback
  quando aplicável, desconectar e disparar sincronização. Validar `state` OAuth,
  expiração e posse da conexão; criptografar segredos em repouso com chave fornecida
  por variável de ambiente, nunca no banco em texto puro.
- Criar tela **Conexões** de T07 com cards GitHub/Jira/Slack, status, CTA de conectar,
  desconectar, sincronizar e último resultado. Mostrar instruções quando provider não
  estiver configurado, sem simular conexão bem-sucedida.
- Implementar o adapter de GitHub pelo contrato sem mudar ainda a regra de sync em
  lote de T10. Criar fixtures de contrato sem chamar provedores reais em testes.

**Arquivos sob responsabilidade.** Novo pacote `integration/**` ou `source/**`,
ajustes mínimos em `github/**`, migrações, serviços/API de conexões, `frontend`
features/services/views de conexões, `app.mjs`, CSS e testes de contrato/segurança.

**Fora de escopo.** Paginação e regras de ingestão específicas de cada provider,
captura histórica ampla, scheduler e UI de configuração de cargo.

**Aceite.** A pessoa vê estados reais de conexão; somente ela controla a sua conexão;
desconectar revoga/descarta credenciais; um adapter gera evidência normalizada e
deduplicável; nenhum segredo é retornado ou logado.

**Verificação adicional.** Testar callback com `state` inválido, acesso cruzado,
desconexão e normalização/deduplicação com fixtures.

---

## T10 — Sincronização automática de Pull Requests do GitHub

**Objetivo.** Importar automaticamente as PRs relevantes de uma conexão GitHub para
a inbox persistente, sem duplicatas.

**Escopo de implementação.**

- Na conexão GitHub, permitir configurar e validar `repositorySlug` e login do autor.
  Para o MVP, usar o `GITHUB_TOKEN` de servidor existente; exibir que repositórios
  privados exigem permissão desse token. Não solicitar token pessoal na interface.
- Criar `POST /connections/github/sync` que busca PRs fechadas/mescladas do autor
  dentro de janela configurável (padrão de 90 dias), pagina explicitamente e passa
  cada resultado pelo adapter/deduplicação de T09.
- Registrar `lastSyncAt`, resultado (`discovered`, `created`, `existing`, `failed`) e
  erros sanitizados. Sync repetido deve criar zero duplicatas.
- Substituir o fluxo atual de escolher uma única PR por uma ação de sincronizar e
  resumo de resultado. Manter importação manual apenas se ela usar exatamente o
  mesmo serviço de persistência.
- Tratar 401, 403, 404 e rate limit do GitHub com mensagens acionáveis, sem vazar o
  token ou URL arbitrária. Não criar scheduler nesta tarefa; a automação é disparada
  pelo usuário.

**Arquivos sob responsabilidade.** `backend/.../github/**`, adapter/serviço de
integração GitHub, APIs de conexão, `frontend/features/github-import/**` (migrar ou
substituir), APIs/views de conexão e testes com servidor HTTP fake local.

**Fora de escopo.** OAuth individual, polling agendado, Jira/Slack e importação de
issues GitHub.

**Aceite.** Configuração é salva por pessoa, sync importa PRs do filtro correto,
paginação funciona, segunda sync não duplica e a inbox mostra os itens pendentes.

**Verificação adicional.** Cobrir sucesso, duas páginas, duplicata, 401/403/404 e
429 no servidor fake; entregar contagens observadas por cenário.

---

## T11 — Importação de evidências do Jira

**Gate obrigatório antes de iniciar.** O time deve informar se é Jira Cloud ou Data
Center, URL/base da organização, método de autenticação aprovado (OAuth 2.0 ou token
de serviço), projeto(s) incluídos e quais eventos contam como evidência (issue
concluída, comentário, transição, sprint etc.). Sem essas escolhas, a tarefa fica
bloqueada — não substituir por dados fictícios.

**Objetivo.** Conectar Jira pela central de T09 e trazer issues atribuídas/concluídas
que satisfaçam a definição aprovada de evidência.

**Escopo de implementação.**

- Implementar autorização/conexão seguindo o modelo aprovado e adapter `JiraSource`.
- Consultar somente projetos e janela aprovados, paginar resultados e normalizar
  chave da issue como `externalId`, título/descrição/resumo permitido como conteúdo,
  URL, autor/responsável e data relevante.
- Exibir na central status da conexão e resumo da sync; importar para `Evidence` pelo
  caminho comum e registrar erros por item.
- Cobrir permissão, paginação, item sem campos opcionais, duplicata e erro do Jira
  com fixtures/servidor fake.

**Arquivos sob responsabilidade.** Novo pacote/adapter `jira/**`, extensão limitada
do contrato de T09, APIs/views de conexão e testes. Não editar domínio de análise,
perfil ou GitHub além de interfaces estritamente compartilhadas.

**Aceite.** Somente dados de escopo aprovado entram, sync é idempotente, erros não
vazam credenciais e os itens aparecem como fonte Jira na inbox expansível.

---

## T12 — Importação de evidências do Slack

**Gate obrigatório antes de iniciar.** O time deve aprovar a instalação Slack
(OAuth/escopos), canais permitidos, política de privacidade/retenção e a definição de
mensagem que é evidência. Não indexar DM, canal privado ou conteúdo fora da política.

**Objetivo.** Conectar um workspace Slack pela central e transformar mensagens
autorizadas em evidências rastreáveis.

**Escopo de implementação.**

- Implementar instalação/autorização com escopos mínimos aprovados e adapter
  `SlackSource`; guardar apenas identificadores e tokens criptografados necessários.
- Buscar mensagens somente dos canais/janelas permitidos, identificando-as por
  `channelId:timestamp`, e normalizar texto, permalink, autor e data para o contrato
  de T09. Remover/mascarar dados não permitidos antes de persistir.
- Oferecer configuração de canais autorizados, sync manual, status e contagens na
  central. Usar exatamente a deduplicação de T02.
- Testar autorização, seleção de canais, paginação/cursor, duplicata, permalink
  ausente, rate limit e sanitização de dados com fixtures locais.

**Arquivos sob responsabilidade.** Novo pacote/adapter `slack/**`, extensão limitada
de T09, API/view de conexões e testes. Não modificar as regras do Jira/GitHub nem o
motor da IA.

**Aceite.** Nenhuma mensagem fora dos canais autorizados é persistida; sync é
idempotente; cada item traz proveniência suficiente para auditoria; tokens e conteúdo
restrito não aparecem em API/logs; a inbox identifica a fonte como Slack.

---

## Critério de pronto do backlog

O backlog está concluído quando T01–T10 estiverem aceitas e o produto suportar, de
ponta a ponta: gestor → plano por pessoa → conexão GitHub → importação de PR →
observação opcional → análise confiável de IA → leitura organizada de evidências.
T11 e T12 são entregas condicionais às decisões de credenciais e privacidade acima.
