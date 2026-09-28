# Revisão do frontend na `main` modernizada

Base: `68a446c` (`Modernize Promova workspaces and APIs`). A implementação anterior, feita sobre `93d31cc`, foi descartada. Esta revisão usa somente `frontend/app.mjs`, `frontend/router.mjs`, `frontend/services/api.mjs` e `styles.css`, que são a interface de produção atual.

| Área revisada | Situação na nova base | Ajuste desta tarefa |
| --- | --- | --- |
| Landing, acesso e cadastro | Rotas próprias, leitura mobile e formulário curto. | Nenhuma mudança de layout necessária. |
| Visão geral, análises e relatórios | Já separados em tarefas e rotas, com estados de carregamento e erro. | Títulos e resumos das análises passam a usar a descrição disponível na evidência. |
| Framework e plano de carreira | Filtros, critério individual e contexto de carreira já têm telas próprias. | Nenhuma mudança de layout necessária. |
| Integrações e GitHub | Configuração, sincronização e busca de PR já estão separados. | Nenhuma mudança no fluxo. |
| Console do gestor | Pessoas, evidências, análises, revisões e configuração já são rotas focadas. | As listas de evidências e análises mostram descrições curtas derivadas da fonte. |
| Caixa de entrada | Já tinha colunas e API paginada, mas as linhas abriam o detalhe diretamente, a prévia exibia a fonte inteira e desaparecia no mobile. | Seleção única e evidente, prévia curta, filtros compactos acima da lista, estados vazios distintos e leitura mobile da prévia. |
| Evidência completa | Preserva fonte, observação separada e ações por status. | Título derivado do conteúdo; fluxo de análise e dispensa preservado. |

## Caixa de entrada

- **Lista:** cada item usa `Titulo:` do conteúdo quando existe; para PRs, também aceita o título presente em `sourceMeta`. O resumo vem de `Descricao/resumo:` ou de outra linha informativa da fonte. Sem esses dados, a interface informa a ausência de descrição em vez de atribuir uma realização.
- **Seleção:** um botão por item expõe `aria-pressed`. A escolha altera a prévia sem novo pedido à API e fica na URL como `selected`. Filtros e paginação mantêm a seleção quando o item continua na página; se sair do resultado, a primeira evidência disponível assume a prévia.
- **Filtros:** status, fonte exata, data inicial e final usam o endpoint paginado real. Há ações de aplicar e limpar. A ausência de evidências pendentes difere da ausência de resultados para filtros aplicados.
- **Prévia:** mostra resumo, origem, status, fonte e datas disponíveis. A fonte integral fica na rota canônica da evidência, que conserva os controles de análise e dispensa conforme o status.
- **Mobile:** lista com rolagem limitada e prévia abaixo dela; selecionar um item leva a leitura até a prévia. Texto longo quebra dentro da coluna e do cartão.

## Verificação

1. `npm run check` executa lint, testes de unidade e build.
2. `PROMOVA_E2E_SCREENSHOT_DIR=tmp/evidence-refactor-real npm run test:e2e` abre a aplicação de produção em Chrome contra Spring Boot, H2 temporário e stub GitHub. O teste confere seleção única por clique e teclado, persistência após filtro e reload, caixa vazia, ausência de resultados, datas inclusivas, status, escape de HTML da fonte, leitura mobile, análise e dispensa.
3. Revise `tmp/evidence-refactor-real/27-inbox-selected.png`, `28-mobile-inbox-preview.png` e `30-mobile-320-inbox.png` para desktop, prévia mobile e tela estreita. O diretório pode ser alterado por `PROMOVA_E2E_SCREENSHOT_DIR`.

O teste automatizado usa Chrome com viewport responsivo. Não substitui a validação em Safari e Firefox.
