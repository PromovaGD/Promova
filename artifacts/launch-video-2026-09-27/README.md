# Promova · Launch Video

Vídeo de **37,95 segundos**, 1600 × 900 (16:9), inspirado no [raw footage launch video](https://r2.autoae.online/marketing/page-family-v1/compare/launch-before-4a66aa84e8ad379a.mp4) indicado pelo usuário. A navegação real do Promova aparece em uma janela de navegador sobre um fundo azul e laranja, com zooms suaves e cursor visível. O vídeo não tem locução nem música.

## Assistir

O player em `index.html` permite assistir ao vídeo e saltar para oito momentos da jornada. Sirva a pasta a partir da raiz do projeto:

```sh
python3 -m http.server 14177
```

Abra `http://127.0.0.1:14177/artifacts/launch-video-2026-09-27/`.

## Sequência

1. Visão geral do espaço pessoal.
2. Caixa de entrada, evidência e fonte original.
3. Observação humana e análise salva.
4. Framework e critérios com evidência de apoio.
5. Plano de carreira e integração GitHub.
6. Painel de gestão e sumários executivos.
7. Revisão, comentário e aceite da análise.
8. Objetivo, equipes e configuração de carreira.

## Arquivos

- `promova-launch-video.mp4`: vídeo final, pronto para compartilhar.
- `promova-launch-video.webm`: versão WebM do vídeo final.
- `index.html`: player com saltos interativos.
- `promova-raw-full.webm`: captura original do produto com cursor e ações reais.
- `composition.html`, `render-film.mjs`, `transcode-film.mjs` e `make-poster.mjs`: fontes da composição e exportação.
- `record-full.mjs`: roteiro de captura do produto.
- `autoae-promova-opener.mp4`: experimento anterior com o template Browser Window Showcase. Possui marca d'água e não faz parte do vídeo final.

Os dados e perfis exibidos vêm de um banco H2 temporário com seed `dev`; a análise é ilustrativa do motor mock. A integração de captura disponível no produto é GitHub. Os arquivos de `artifacts/pitch-motion-2026-09-27/` foram preservados como referência.

Para repetir a captura, execute o backend no perfil `dev` com um banco temporário em `SERVER_PORT=18084`, o frontend com `PORT=14176` e `PROMOVA_API_BASE_URL=http://127.0.0.1:18084`, e o servidor estático na porta 14177. Rode `record-full.mjs`, `render-film.mjs`, `transcode-film.mjs` e `make-poster.mjs` nessa ordem. Os scripts usam o Chrome em `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`.
