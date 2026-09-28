# Promova — release motion (morph)

Filme de lançamento de **21,4 segundos**, 1600 × 900, 30 fps, em português. Esta segunda direção parte da referência enviada pelo usuário: uma única forma muda continuamente de tamanho, canto e cor, enquanto um cursor aciona as transições. A forma passa por marca → evidência → análise → revisão → plano → assinatura final. O áudio eletrônico foi sintetizado para este filme.

## Referência e adaptação

O vídeo [Claude Opus 5.5 Just Solved Motion Graphics](https://www.youtube.com/watch?v=6Ij9-f2T2Ck) apresenta a skill [motion-broll](https://github.com/Barty-Bart/motion-graphics), de Bart Slodyczka. A skill foi criada para acrescentar B-roll sincronizado à fala em um vídeo existente. Este filme usa o **motor de morphing** da skill, sob licença MIT, adaptado para uma peça standalone da Promova. O código original copiado está em `engine/`, com a licença em `ENGINE-LICENSE`; a composição e o exportador são próprios.

As telas internas são representações gráficas das funções do produto, e não capturas literais. `PR #1492` e “Ampliar impacto técnico” vêm dos dados de demonstração locais. Barras de texto são ilustrativas. Nenhum resultado quantitativo foi inventado.

## Roteiro

| Tempo | Estado da forma | Ação |
| --- | --- | --- |
| 0–2,7 s | Marca | A pílula Promova entra e se firma. |
| 2,7–6,4 s | Evidência | A pílula abre em cartão; aparece a fonte GitHub; o cursor clica em Analisar. |
| 6,4–10 s | Análise | O mesmo cartão cresce, organiza a contribuição e envia para revisão. |
| 10–13,6 s | Revisão | Comentário e decisão humana; o estado muda para Aceita. |
| 13,6–17,1 s | Plano | A forma amplia o objetivo de carreira e vincula a evidência. |
| 17,1–21,4 s | Assinatura | O cartão recolhe para a marca e a frase final. |

## Arquivos

- `promova-release-morph.mp4`: entrega final.
- `index.html`: player e capítulos.
- `clips/promova-release.html`: composição editável.
- `dist/promova-release.html`: HTML autônomo gerado com motor e fontes incorporados.
- `render-mp4.mjs`: exportador quadro a quadro com Playwright e Mediabunny.

Para remontar o HTML: `python3 artifacts/promova-motion-rebuild-2026-09-28/engine/build.py artifacts/promova-motion-rebuild-2026-09-28/dist artifacts/promova-motion-rebuild-2026-09-28/clips/promova-release.html`.

Para renderizar, sirva a raiz do repositório com `python3 -m http.server 14177` e rode `node artifacts/promova-motion-rebuild-2026-09-28/render-mp4.mjs`. O projeto usa o Chrome local e as dependências Playwright Core e Mediabunny já presentes neste workspace.
