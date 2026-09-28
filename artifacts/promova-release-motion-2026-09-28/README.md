# Promova — release motion film

Vídeo de lançamento em português, **36 segundos**, **1600 × 900 (16:9)**, com trilha eletrônica e efeitos sonoros originais. A composição usa capturas reais do protótipo e elementos animados em Canvas: tipografia, cartões de evidência, movimentos de câmera, transições, barra de progresso e encerramento de marca.

## Roteiro

| Tempo | Mensagem | Visual |
| --- | --- | --- |
| 0–4,8 s | O trabalho acontece. Mas a evolução precisa aparecer. | Cartões de trabalho, feedback e evidência |
| 4,5–8,3 s | Promova | Marca e assinatura |
| 8–13,5 s | O trabalho vira evidência. | Caixa de entrada |
| 13,1–18,7 s | Contexto vira clareza. | Análise salva |
| 18,3–23,6 s | A gestão participa. | Revisão humana |
| 23,2–28,6 s | A conversa vira plano. | Plano de carreira |
| 28,2–32,2 s | Para cada pessoa. Para cada gestão. | Visão da gestão |
| 31,7–36 s | Carreira baseada em evidências. | Assinatura e convite |

## Arquivos

- `promova-release-motion.mp4`: vídeo final para compartilhar.
- `composition.html`: composição editável e trilha programada em Web Audio.
- `render-mp4.mjs`: renderização quadro a quadro com Chrome e Mediabunny.
- `index.html`: player local com roteiro por capítulos.

As telas usam dados fictícios do ambiente de demonstração, inclusive parte do texto de análise em inglês. As contagens vistas no painel são dados de demonstração e não são métricas comerciais. A integração representada é GitHub. A trilha foi sintetizada para este vídeo; não usa música de terceiros.

Para reproduzir o export, sirva a raiz do projeto com `python3 -m http.server 14177` e execute `node artifacts/promova-release-motion-2026-09-28/render-mp4.mjs`. A página do player fica em `http://127.0.0.1:14177/artifacts/promova-release-motion-2026-09-28/`.

## AutoAE

O [AutoAE](https://autoae.online/) foi avaliado por meio do skill online. Três modelos úteis para variantes curtas são [Minimal Text Reveal](https://autoae.online/hooks/autoae-saas-launch-s2-pt.9-minimal-text-reveal-mhsf6d0u), [Pain Point UI & Case Demo](https://autoae.online/hooks/autoae-saas-launch-pt.3-pain-point-ui-and-case-demo-qqjrkf7j) e [Dynamic CTA](https://autoae.online/hooks/autoae-saas-launch-pt.6-dynamic-cta-riul8gpm). O skill delimita o AutoAE a segmentos curtos; a composição principal foi feita localmente para manter uma narrativa contínua e editável sem consumo de créditos nem marca d'água.
