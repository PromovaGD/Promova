# Plano de implementação da refatoração UX/UI do Promova

Data: 27 de setembro de 2026. Destinatário: agente que implementará a próxima etapa do front end.

## Objetivo e fontes

Aplicar a direção visual da designer e melhorar os fluxos de **Caixa de entrada** e **Pessoas** na aplicação real. Entregar essas duas telas com dados, navegação e ações funcionais; depois propagar os componentes compartilhados para as demais áreas sem redesenhar fluxos que não foram estudados.

Referências preservadas neste repositório:

- [Análise comparativa de UX/UI](../artifacts/designer-ux-ui-2026-09-27/analise-ux-ui-comparativo.docx): raciocínio, antes/depois e questões ainda abertas. O próprio documento define o estudo como inicial e limitado às duas telas.
- [Style guide](../artifacts/designer-ux-ui-2026-09-27/style-guide.pdf): paleta, tipografia, espaçamento e exemplos de componentes. A paleta é apresentada como oficial; Inter aparece como sugestão.
- [Tela proposta de Caixa de entrada](../artifacts/designer-ux-ui-2026-09-27/caixa-de-entrada.jpeg) e [tela proposta de Pessoas](../artifacts/designer-ux-ui-2026-09-27/pessoas.jpeg): referência de hierarquia e composição desktop. Nomes, quatro PRs, contagens, tags e texto de objetivos são dados ilustrativos, não requisitos de conteúdo.
- [Símbolo da marca fornecido](../artifacts/designer-ux-ui-2026-09-27/logo-promova.png): PNG transparente; o nome “promova” no layout continua sendo texto.

**Ordem de autoridade:** pedido do usuário e comportamento real do produto; depois requisitos visuais do style guide e intenção de UX das propostas. As instruções de “validar com o time” e “aprofundar depois” dentro da análise descrevem o processo da designer, não suspendem a implementação solicitada. Onde a proposta exige dados ou semântica ausentes, resolver o contrato explicitamente antes de mostrar o controle na interface.

## Diagnóstico da base atual

O produto já usa ES modules, rotas reais e API Spring Boot. Não começar uma segunda interface nem trocar a arquitetura por um framework. `frontend/app.mjs` renderiza shell e telas, `frontend/router.mjs` normaliza rotas e query strings, `frontend/services/api.mjs` concentra chamadas, `frontend/utils/evidence-display.mjs` extrai títulos e resumos da fonte, e `styles.css` contém os estilos. O README e [a revisão recente](frontend-main-layout-review.md) registram que a caixa atual já possui lista, prévia curta, seleção por URL e leitura mobile. Há alterações locais extensas no repositório; o agente deve verificar `git status` e preservar o trabalho existente antes de editar.

| Área | Existe hoje | Diferença relevante para a proposta |
| --- | --- | --- |
| Shell | Sidebar escura de 216 px, barra superior branca, navegação por papel e marca antiga em `frontend/assets/promova-symbol.png` | Sidebar clara, item ativo em Purple Tint, hierarquia do cabeçalho e símbolo fornecido. O novo PNG não é o mesmo arquivo da marca atual. |
| Tokens | Roxo principal já é `#4B1FD1`, mas ink, dark, muted, line, foco, sucesso e erro divergem; há valores dispersos no CSS | Centralizar tokens oficiais e aplicá-los aos componentes e estados. |
| Caixa de entrada | Lista e prévia lado a lado, filtro compacto, estados `PENDING`, `ANALYZED`, `DISMISSED`, seleção em `?selected=` e paginação de 25 | Barra clara com busca, estados, fonte/período/ordem; metadados, resumo e detalhe técnico em leitura progressiva; tags reais; ações coerentes com o status. |
| Pessoas | `/manage/people` é só diretório; `/manage/people/:id/...` abre plano, evidências e análises em página própria | Lista persistente à esquerda e pessoa selecionada à direita no desktop, sem perder rotas profundas nem edição separada. |
| Dados de evidência | O endpoint paginado retorna fonte, `sourceMeta`, `content`, status e `analysisId`; não há busca, ordenação escolhida pelo usuário, tags estruturadas nem autor/merge estruturados no item | A tela proposta depende de campos e filtros que a API ainda não oferece. Não preencher com texto fictício nem filtrar só os 25 itens carregados. |

**Cuidado com a comparação:** as imagens “Antes” no DOCX são de um protótipo de estudo, não são necessariamente o código atual. Comparar o resultado final com a aplicação em `HEAD` mais mudanças locais preservadas, usando as imagens “Depois” para avaliar a direção visual.

## Decisões de produto e contrato antes de codificar controles

1. **“Aceitas” não é `ANALYZED`.** Hoje `EvidenceStatus` tem `PENDING`, `ANALYZED` e `DISMISSED`; `ACCEPTED` é `ReviewStatus` da análise, definido por revisão da gestão. Recomendação: “Aceitas” mostrar apenas evidências cuja análise esteja com revisão atual `ACCEPTED`, com contagem e filtro paginados no servidor. Análises ainda não revisadas ou com `NEEDS_CONTEXT` continuam acessíveis em Análises. Se a intenção da designer era “já analisadas”, usar o rótulo **Analisadas** com validação editorial, sem apresentar `ANALYZED` como “Aceita”. Registrar a decisão no contrato e nos testes.
2. **Busca, período e ordenação:** especificar `q` pesquisando título/descrição/origem, sem diferenciar maiúsculas; fonte exata; período com limites inclusivos na data local; `newest` e `oldest` por `occurredAt`, com `id` como desempate. Acrescentar parâmetros e índices/consulta adequados na API, mantendo paginação e autorização. Se um filtro ainda não tiver backend, não exibi-lo como funcional.
3. **Contagens por estado:** o número em “Pendentes” e o título da lista devem refletir o conjunto autorizado e os filtros ativos, não apenas a página atual. Definir resposta agregada ou endpoint de contagens para evitar várias consultas por item.
4. **Tags e metadados:** a captura do GitHub conhece labels, autor e data de merge, mas o item de evidência atual não os expõe como campos estruturados. Definir como persistir/projetar esses metadados na leitura, com migração aditiva se preciso. Exibir tags existentes da fonte e omitir as ausentes; não gerar “backend”, “segurança” etc. por inferência arbitrária. Evidências antigas e fontes não GitHub precisam de fallback honesto.
5. **Resumo:** `evidenceDisplay()` hoje deriva um trecho de `content`, limitado em tamanho. A proposta pede resumo em destaque, mas não fornece um novo resumo editorial ou por IA. Tratar o texto derivado como resumo da fonte, com rótulo claro e fallback “Sem resumo na fonte”. O conteúdo integral continua disponível na rota de evidência; no painel, “Ver detalhes técnicos” abre somente o que existe, sem alegar análise concluída.

Essas decisões são dependências de implementação, não licença para bloquear o restante: shell, componentes, diretório de Pessoas e o layout da caixa podem ser construídos sobre os contratos atuais enquanto o enriquecimento aditivo é preparado.

Contrato recomendado para a caixa: uma consulta paginada do funcionário com `bucket=pending|accepted|dismissed`, `q`, `source`, `from`, `to`, `sort=newest|oldest`, `page` e `pageSize`; resposta com `{items, page, pageSize, total, counts}`. Cada item deve trazer apenas os campos necessários à lista/prévia (`id`, status da evidência, status atual da revisão quando houver, título, resumo da fonte, origem, datas, URL segura, tags e metadados opcionais). Buscar `content` integral pelo detalhe selecionado. Pode ser um endpoint novo ou extensão aditiva do existente; manter `GET /evidences` e seu filtro `status` compatíveis. Se a decisão for usar “Analisadas”, o bucket intermediário representa `ANALYZED` e deve receber esse nome na UI e nos testes.

## Sequência de implementação

### 0. Preparar base e critérios

- Confirmar `git status`, rotas, DTOs e testes atuais. Identificar quais alterações locais ainda estão em andamento e preparar um checkout de trabalho que as preserve; não aplicar reset ou sobrescrever trabalho de terceiros. Capturar uma referência da aplicação real nos dois papéis, com dados representativos e também estados vazios.
- Definir, em pequena especificação de API, parâmetros da caixa, resposta de contagens, campos opcionais de metadados e semântica do estado “Aceitas”. Manter contratos antigos ou adicionar campos de forma compatível durante a migração.
- Preparar fixtures com pelo menos: pendente GitHub com labels, pendente sem labels, analisada não revisada, análise aceita, análise que precisa de contexto, dispensada, fonte sem resumo, muitos itens e texto técnico longo. Não usar as quatro PRs do mockup como dados permanentes.

**Concluído quando:** decisões de status e filtros estiverem registradas, fixtures cobrirem as condições acima e houver captura da base real para comparação.

### 1. Fundamentos visuais e shell compartilhado

- Criar uma camada de tokens no CSS, com os valores exatos do guia: purple `#4B1FD1`, purple tint `#EEE9FF`, soft `#F3EFFF`, light `#FAF8FF`, ink `#171B3B`, dark `#131831`, muted `#62687D`, disabled `#777D8E`, white `#FFFFFF`, canvas `#F5F6FA`, line `#DCDFEA`, success `#14654E`/`#E7F5EF`, warning `#825017`/`#FFF4DF`, error `#A3273C`/`#FFF0F2` e focus `#7658DB`. Usar escala de espaço 4–64 px e raios 5–16 px. Conferir contraste em texto pequeno e estados de foco.
- Estabelecer tipo H1 28/35, H2 20/26, H3 16/24, corpo 15/22, corpo pequeno 13/19 e caption 11–12/16. Usar Inter 400/500/600/700 quando disponível, com fallback de sistema; não carregar uma fonte remota como dependência obrigatória.
- Atualizar componentes comuns: botão primário/secundário/ghost/perigo, input, badge, aba, card, lista, alerta e modal. Estados hover, foco, selecionado, desabilitado, carregando e erro precisam de texto/ícone além da cor. Substituir símbolos improvisados por ícones lineares consistentes de 16/18/20/24 px.
- Reorganizar sidebar e cabeçalho conforme as referências, mantendo navegação, menu mobile, skip link, menu da conta e saída acessíveis. Preparar cópia otimizada do símbolo fornecido e conferir transparência/nitidez; manter o nome da marca legível como texto.
- Reduzir valores literais conflitantes em `styles.css` progressivamente. Evitar uma troca global cega que altere gráficos, página pública ou alertas sem inspeção.

**Concluído quando:** as duas áreas e os estados comuns usam os mesmos tokens e componentes, com menu e navegação funcionais em desktop e mobile.

### 2. Caixa de entrada e contrato de evidências

- Ajustar `frontend/router.mjs`, `frontend/services/api.mjs` e o endpoint `/evidences` para busca/ordenação/contagens e, se adotado, status de revisão aceita. Manter IDs e filtros na URL, voltar/avançar, reload e paginação estável. Consultas devem ser feitas no servidor; não filtrar ou contar apenas `items` da página.
- Recriar a região superior: busca com rótulo acessível, estados visualmente separados, filtros de fonte e período, controle de ordem e ação de importar PR. O layout deve quebrar em linhas úteis em larguras médias; no mobile, os filtros podem abrir em painel, com estados ainda visíveis e alcançáveis.
- Manter a seleção única da lista, destacar item selecionado e oferecer título, origem/data, status e tags reais. Usar `aria-current` ou outro padrão adequado de seleção, com nome acessível e foco preservado após atualização. Respeitar `?selected=` existente e reconciliar seleção quando filtro/dispensa a remove.
- No painel de detalhe, ordenar: origem/status, título e tags; metadados disponíveis; resumo da fonte; expansão de detalhes técnicos; rodapé de ações. “Abrir evidência” segue para a rota canônica. “Dispensar” aparece apenas em pendentes do próprio funcionário, com confirmação e atualização da lista/contagens. A análise continua na evidência completa, com observação separada e proteção contra envio duplo.
- Carregar texto técnico completo sob demanda se o endpoint de lista passar a ser leve. Preservar fonte original imutável, escape de HTML, links externos seguros e fallback para campos ausentes. Não fazer uma chamada por item para preencher a lista.

**Concluído quando:** busca/filtros/ordem retornam resultados corretos em 500+ evidências; números e status batem com a API; seleção, dispensa e abertura sobrevivem a reload, teclado e mobile; a fonte longa não domina a leitura inicial.

### 3. Pessoas como lista e detalhe

- Extrair um workspace de Pessoas usado por `/manage/people` e pelas rotas existentes `/manage/people/:employeeId/career-plan`, `/evidence` e `/analyses`. No desktop, manter diretório paginado à esquerda e detalhe da pessoa à direita. Na rota base sem pessoa selecionada, mostrar orientação para selecionar alguém; a captura da designer representa uma pessoa já selecionada, não uma exigência de seleção automática.
- Preservar os links profundos e a proteção do backend. Ao selecionar outra pessoa, trocar a rota de detalhe e destacar a linha sem perder busca, cargo, nível e página do diretório. Guardar esses filtros em query strings próprias do diretório para que Back/Forward e reload recuperem o contexto, sem colidir com filtros das abas de evidências/análises.
- No detalhe, colocar identidade (avatar, nome, e-mail, cargo e progressão) acima das abas Plano de carreira, Evidências e Análises. No plano, destacar objetivos em cards com título, status, data e descrição; mostrar nível/cargo sem duplicar dados, e manter “Editar contexto” e “Novo objetivo” em posições previsíveis. A edição continua em formulário/rota separada, um objetivo por vez, usando os controles de versão já existentes.
- No mobile, usar lista e detalhe em sequência ou navegação em duas etapas com retorno explícito à lista e seus filtros. Não esconder o detalhe. Validar listas grandes, muitos objetivos, nomes longos, ausência de objetivo, pessoa inacessível e permissão 403/404.

**Concluído quando:** o gestor encontra pessoa, lê plano/evidências/análises, edita contexto e objetivo e volta ao diretório filtrado; a seleção e as abas sobrevivem a links diretos e recarga.

### 4. Propagação controlada e acabamento

- Aplicar os componentes comuns a Visão geral, Análises, Framework, Plano do funcionário, Integrações, Revisões e Configuração. Priorizar navegação, botões, campos, cards, badges, alertas e diálogos; manter a arquitetura de informação e as regras de negócio dessas áreas, que não receberam telas redesenhadas.
- Revisar rótulos de espaço, ações e estados para consistência entre papel de funcionário e gestor. Corrigir variações de espaçamento, fontes e contraste introduzidas pelo CSS compartilhado.
- Registrar diferenças deliberadas entre referência e produto real: conteúdo dinâmico, rotas, controles omitidos por falta de dado e comportamento mobile não especificado nas capturas desktop.

**Concluído quando:** os componentes reutilizados parecem e se comportam da mesma forma em todas as rotas, sem regressão visual ou funcional nas demais áreas.

## Verificação e entrega do agente implementador

1. Rodar `npm run check`. Se houver mudança de API/DTO/consulta, rodar os testes do backend (`cd backend && ./gradlew test`) e adicionar testes de contrato para busca, ordenação, contagem, tags/metadados e distinção entre `ANALYZED` e `ACCEPTED`.
2. Estender `e2e/application.e2e.mjs` para os fluxos novos, preservando as verificações existentes de autenticação, permissão, paginação, seleção, fonte escapada, análise e dispensa. Testar URLs diretas, reload, Back/Forward, filtro sem resultado e erro de rede.
3. Capturar telas reais com dados determinísticos em desktop próximo às referências (1600 px de largura), 1280 px, tablet, 390 px e 320 px. Comparar hierarquia, densidade, estado selecionado, alinhamento, ações e responsividade; não exigir igualdade de texto ou contagens ilustrativas.
4. Verificar teclado, foco visível, rótulos, leitura de status sem depender apenas da cor, zoom de 200%, reflow de 400%, movimento reduzido e ausência de rolagem horizontal no corpo. Em listas longas, garantir que cabeçalho, lista e detalhe continuem operáveis.
5. Entregar resumo das mudanças por etapa, capturas de antes/depois, comandos e resultados dos testes, decisões de produto tomadas e qualquer pendência real. Não declarar “Aceitas”, tags, autor/merge, busca ou ordenação como concluídos se estiverem simulados no cliente.

## Prioridade e limite de escopo

**P0:** decisões de semântica e contrato, tokens, shell e os fluxos completos das duas telas. **P1:** propagação dos componentes comuns e ajuste visual das demais áreas. **P2:** refinamentos de conteúdo e experimentos de UX após observação de usuários, como quantidade ideal de informação em objetivos e utilidade dos filtros.

O plano admite pequenas mudanças aditivas no backend porque a proposta visual inclui capacidades que a API atual não entrega. A implementação deve preservar autenticação, isolamento de dados, fonte original, análise persistida, revisões, rotas canônicas e estado de edição. Não migrar o stack, refazer integrações ou usar dados do mockup como conteúdo de produção para cumprir este trabalho.
