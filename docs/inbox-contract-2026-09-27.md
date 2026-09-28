# Contrato da Caixa de entrada

Implementado em 27 de setembro de 2026 sobre os contratos existentes de evidência. `GET /evidences`, inclusive `status`, paginação e detalhe canônico, continua disponível.

## Consulta

`GET /evidences/inbox?bucket=pending|accepted|dismissed|all&q=...&source=...&from=...&to=...&sort=newest|oldest&page=1&pageSize=25`

A API resolve o usuário pelo token e consulta somente suas evidências. `q` procura, sem diferenciar maiúsculas, no texto de origem, conteúdo capturado, fonte e identificador externo. Fonte é uma correspondência exata sem diferenciar maiúsculas. `from` e `to` são instantes inclusivos; o cliente converte datas locais para o início e o fim do dia. A ordenação usa `occurredAt` e `id` como desempate. `pageSize` mantém o limite comum de 50 itens. Valores inválidos de estado, ordem e intervalo retornam 400.

A resposta contém `{items, page, pageSize, total, counts}`. As contagens representam todos os registros autorizados que correspondem a `q`, fonte e período, independentemente da página; cada estado tem sua própria contagem. `all` abrange todos os estados da evidência e por isso pode incluir análises ainda não aceitas. Uma evidência `ANALYZED` só aparece em `accepted` quando a revisão mais recente da sua análise é `ACCEPTED`. Revisões `NEEDS_CONTEXT` e análises sem revisão ficam em Análises, sem receber o rótulo Aceita.

Cada item contém título, resumo e origem derivados da fonte, status da evidência, `currentReviewStatus` quando conhecido, datas, URL segura, `analysisId` quando há análise, autor, merge e tags quando persistidos. A lista não contém o texto técnico integral; `GET /evidences/{id}` o fornece sob demanda. Metadados ausentes são omitidos na interface. O fallback do resumo é “Sem resumo na fonte.”, sem atribuição à IA.

Novas capturas normalizadas do GitHub persistem autor, data de merge e labels existentes da fonte. Evidências históricas continuam válidas e podem não trazer esses campos. A migração V12 adiciona apenas colunas e índices; não altera o texto capturado nem revisões já registradas.
