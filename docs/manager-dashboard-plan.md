# Dashboard inicial do gestor — análise e plano

## Implementação entregue

O gestor entra em `/manage/dashboard`. A navegação mantém **Pessoas** como diretório, além de **Revisões** e **Equipes**. O painel consulta agregados no servidor e oferece período de 30/90 dias ou intervalo personalizado, equipe, cargo e pessoa. O filtro fica na URL. Os cartões distinguem eventos do período de pendências atuais. A tendência semanal, a situação das análises, as fontes, as ações prioritárias e a tabela paginada de pessoas têm estados vazios e leitura textual dos valores.

O banco agora mantém equipes e histórico de vínculos. Todas as rotas gerenciais de pessoas, planos, evidências, análises e revisões usam o escopo de membros ativos do gestor. Há cadastro de equipes e movimentação de pessoas. Objetivos registram eventos de status e prazo. A nova seção de PRs mostra apenas PRs capturados pelo Promova, com fatos estruturados e eventos de code review do GitHub separados das revisões de análises. A ação **Atualizar code reviews** busca até 10 PRs recentes visíveis; a captura e sincronização GitHub já fazem upsert dos fatos por repositório e número.

As execuções de análise registram motor, versão, modelo quando aplicável, início/fim, resultado e tipo do erro; sugestões são guardadas individualmente para futura classificação. O painel usa também idade da fila, prazos, ausência de objetivo, recência de captura e cobertura de critérios do framework. O limiar padrão dessas pendências é 7 dias e pode ser alterado com `promova.dashboard.attention-days` (1–90). A fixture do perfil `dev` cria três pessoas com cargos/níveis distintos, objetivos, evidências, PRs, análises e revisões. É desativável com `PROMOVA_DEMO_DATA_ENABLED=false`; a interface identifica os dados de demonstração.

### Dados que ainda exigem coleta ou validação

| Dado | Situação e próximo passo |
| --- | --- |
| Universo de PRs | A contagem atual cobre apenas PRs capturados e sincronizados pelos usuários. Para cobertura da equipe inteira, cadastrar repositórios gerenciados, credenciais de instalação e cursor de sincronização ou webhook. |
| Cobertura do primeiro pedido de revisão e commits | O primeiro pedido é coletado dos eventos da issue na atualização de code reviews; commits vêm dos detalhes na captura individual. O sync de resumos não traz commits e a busca de eventos é limitada a três páginas por PR. Para cobrir todo o histórico, adicionar varredura paginada/cursor e política de retenção. |
| Sugestões por categoria e severidade | Os textos estão em registros individuais, com campos de categoria/severidade reservados. Definir taxonomia versionada e revisar a classificação antes de apresentar agregados. |
| Qualidade de código em porcentagem | Não existe rubrica calibrada nem validação humana. O dashboard não inventa esse índice ou ranking de pessoas; acrescentá-lo requer critérios versionados, amostra validada e acompanhamento de vieses. |

As migrações V9–V11 incluem backfill dos vínculos legados, fatos de PR, eventos de review e telemetria. Os testes cobrem agregação com filtros e paginação, isolamento de equipes, migração, rotas, interface e layout móvel. No backfill, cada gestor legado recebe uma equipe com os funcionários existentes para preservar acesso; a divisão definitiva dessas equipes exige revisão dos vínculos pela organização.

## Análise original que orientou a implementação

## Direção do produto

Usar a imagem enviada como referência de hierarquia: contexto e filtros no topo, indicadores curtos, visualização temporal, distribuição de estados e lista de pessoas. O dashboard da Promova deve responder primeiro **o que precisa de atenção na gestão de carreira e evidências**. Métricas de engenharia de PRs entram quando houver coleta própria para elas. Manter a navegação lateral e os componentes visuais atuais; a nova página será a primeira opção do menu, e **Pessoas** continuará como diretório.

## Situação atual verificada

- A página inicial de `MANAGER` é `/manage/people` (`frontend/router.mjs`). O menu contém Pessoas, Revisões e Configuração de carreira (`frontend/app.mjs`). A página Pessoas é uma lista paginada com busca, cargo e nível; os detalhes de cada pessoa têm plano, evidências e análises.
- O estilo atual usa fundo cinza claro, cartões brancos com borda, roxo como cor de ação, barra lateral azul escuro e layout responsivo (`styles.css`). Já existem cartões de indicadores na visão geral do funcionário, filtros compactos, painéis, estados vazios e navegação por URL.
- O backend já possui pessoas, cargo e nível, objetivos com status e prazo, evidências com fonte/status/datas, análises salvas e a fila de revisões com contagem por estado. Os estados da revisão gerencial são `UNREVIEWED`, `NEEDS_CONTEXT` e `ACCEPTED`.
- O seed de desenvolvimento cria um gestor e três funcionários, com perfis padrão, mas não cria PRs, evidências, análises, revisões nem objetivos. O motor de análise mock produz resultados ao analisar uma evidência; ele não é uma base de dados demonstrativa.
- O gestor enxerga hoje todos os funcionários. Não existe relação gestor–equipe. O endpoint `/insights` é restrito aos dados do usuário autenticado, portanto não serve para um painel agregado do gestor.

## Tradução da imagem para dados reais

| Bloco da referência | Pode ser mostrado agora? | Implementação recomendada |
| --- | --- | --- |
| Equipe e membros | Parcialmente | Mostrar **Pessoas acompanhadas** (todos os funcionários visíveis). Trocar o filtro Equipe por **Cargo** até existir cadastro de equipes. |
| Total de PRs e fluxo de PRs | Não como universo de PRs | Mostrar **Evidências capturadas** e sua tendência, com filtro de origem GitHub. A sincronização automática captura PRs mesclados, e a captura manual pode trazer outros PRs; isso não equivale a todos os PRs da equipe. |
| PRs avaliados pela IA | Parcialmente | Mostrar **Evidências analisadas** ou **Análises geradas**. Em desenvolvimento, indicar que a análise usa motor mock. Não interpretar a contagem como cobertura de todos os PRs. |
| PRs revisados por humanos | Não nesse sentido | Mostrar **Análises revisadas pelo gestor** (`ACCEPTED` + `NEEDS_CONTEXT`). A revisão gerencial de uma análise é diferente do code review no GitHub. |
| Status dos PRs, aprovados/rejeitados | Não | Substituir por **Situação das análises**: sem revisão, aceitas, precisam de contexto. Não há estado `REJECTED` no fluxo atual. |
| Tempo médio de revisão | Sim, com novo agregado | Medir o intervalo entre criação da análise e **primeira revisão gerencial**, com mediana e amostra; rotular explicitamente como tempo de revisão da análise. Tempo de code review do PR requer eventos do GitHub. |
| Qualidade do código (IA) | Não | Não exibir porcentagem. A análise atual estima nível e traz justificativa/sugestões, sem escore calibrado de qualidade de código. |
| Sugestões e pontos de atenção da IA | Parcialmente | As sugestões existem como texto livre; o motor mock retorna sugestões genéricas. Usar apenas em detalhes até haver categorias, severidade e deduplicação. |
| Desempenho por pessoa | Parcialmente | Usar tabela de **acompanhamento**: cargo/nível, objetivos ativos, evidências, análises sem revisão e última atividade. Evitar ranking ou “taxa de qualidade” individual sem medição confiável. |

## Proposta de tela

1. **Cabeçalho:** “Visão da gestão”, descrição curta, período (30/90 dias ou intervalo personalizado), cargo e pessoa. Indicar claramente “Todos os funcionários” como escopo inicial. O seletor de equipe aparece só depois de modelar equipes.
2. **Indicadores:** Pessoas acompanhadas; evidências do período; análises geradas no período; análises aguardando revisão; objetivos vencidos. Os dois últimos são **estoque atual**, independentemente do período, e devem trazer esse rótulo.
3. **Ações prioritárias:** lista curta de análises sem revisão, solicitações de contexto e objetivos vencidos, com links diretos para o registro relevante. Esta região deve ficar acima dos gráficos no mobile.
4. **Gráficos:** tendência semanal de evidências, análises e revisões como séries distintas (não empilhadas, pois se sobrepõem); distribuição do estado atual das análises criadas no período; opcionalmente distribuição por fonte de evidência.
5. **Pessoas:** tabela compacta com nome, cargo/nível, objetivos ativos, evidências pendentes, análises sem revisão e última atividade. Cada linha abre a página já existente da pessoa. No mobile, virar cartões legíveis com apenas os campos prioritários.

Reutilizar tokens, tipografia, cartões, foco e estados da aplicação. Reduzir a densidade da referência para caber na área útil com a barra lateral atual; não copiar o cabeçalho horizontal isolado da imagem. Gráficos devem ter legenda, valores textuais e estados vazios. Filtros devem atualizar a URL para suportar reload e compartilhamento.

## Entrega em fases

### Fase 1 — dashboard de carreira e revisão

- Criar `/manage/dashboard` como `roleHome(MANAGER)`, primeiro item do menu e destino após login. Preservar `/manage/people`, URLs profundas e regras de acesso; ajustar testes de rota e E2E.
- Criar agregação **no servidor** para contagens e séries. Consultas por pessoa/página no navegador perderiam dados por causa da paginação e causariam múltiplas requisições. Proposta: `GET /manager/dashboard?from=...&to=...&jobRoleId=...&employeeId=...` para resumo, séries e atenção; `GET /manager/dashboard/people?...&page=...` para a tabela.
- Calcular separadamente eventos do período e estoque atual. Evidências usam `occurredAt`; análises usam `createdAt`; revisões usam o instante do evento de revisão; prazos dos objetivos usam `targetDate`. Padronizar limites inclusivos e fuso exibido. Contar estado **atual** das análises criadas no período e deixar isso visível na legenda.
- Usar `page.total` de funcionários e os dados já existentes como referência para a implementação da agregação. A fila `/manager/reviews` já retorna `counts` para os três estados; não somar apenas itens da primeira página.
- Criar fixtures de demonstração determinísticas apenas em desenvolvimento/teste: três pessoas existentes, cargos/níveis variados, objetivos com prazos, evidências GitHub em diferentes status, análises e revisões nos três estados. Os números devem decorrer dos registros seedados; nunca fixar “124” ou “94%” na UI. Dados mockados devem ser identificáveis como demonstração.

### Fase 2 — escopo de equipe e visibilidade operacional

- Modelar `team` e histórico de vínculo `team_member` (gestor, membro, início e fim), inclusive mudanças de equipe. Aplicar o escopo na autorização de todos os endpoints gerenciais, não apenas no filtro visual. Só então mostrar o filtro Equipe e “7 membros da equipe”.
- Adicionar métricas de atenção: idade da fila de revisão, análises aguardando há mais de N dias, pessoas sem objetivo ativo, objetivos próximos do prazo/vencidos, cobertura de evidências por critério e recência da última captura. Definir limiares configuráveis e mostrar o denominador ao exibir proporções.
- Para tendências de objetivos, persistir transições de status e mudanças de prazo. O status atual e `updatedAt` não bastam para reconstruir corretamente o histórico.

### Fase 3 — métricas reais de PR e análise automatizada

- Persistir fatos estruturados por PR (`repositório` + `número`, autor associado, criação, fechamento, merge, estado, revisões, primeiro pedido/primeira revisão, commits e atualização). Coletar alterações por sincronização incremental ou webhook e deduplicar; definir cobertura dos repositórios e período. O `providerMetadata` criado pelo adaptador GitHub não é persistido na entidade de evidência atual.
- Persistir eventos de code review (autor da revisão, instante, tipo) separados das revisões de análise da Promova. Definir “aprovação” e “rejeição” de PR com semântica explícita; PR fechado sem merge não significa necessariamente rejeitado.
- Instrumentar execução da análise (motor/modelo/versão, início/fim, sucesso/erro, duração), sugestões estruturadas e taxonomia de pontos de atenção. Uma nota de qualidade de código só deve entrar após rubrica versionada, validação humana e acompanhamento de vieses/precisão; evitar tratá-la como avaliação de pessoas.

## Critérios de aceite da primeira fase

- Login do gestor e clique na marca/menu levam ao dashboard; Pessoas continua acessível e links antigos funcionam.
- Todas as contagens conferem com registros do banco para período, cargo e pessoa; paginação não altera totais. Filtros e back/forward preservam a seleção.
- Estados vazios mostram “sem dados no período” ou “sem pendências” conforme o caso; nenhum card usa número ilustrativo em ambiente real.
- Card e linha navegáveis levam a Pessoas, Revisões, análise ou objetivo correspondente. Usuário funcionário não acessa endpoint nem rota gerencial.
- Layout verificado em desktop e 320–390 px, com leitura por teclado e valores de gráficos disponíveis em texto.

## Decisão de produto recomendada

O primeiro dashboard deve medir **fluxo de evidências, revisões e acompanhamento de carreira**. Os indicadores de PR da referência podem virar uma aba ou seção de engenharia quando os dados estruturados e o escopo de equipes existirem. Assim, o painel estreia útil com o domínio atual da Promova, sem atribuir ao produto métricas que ele ainda não mede.
