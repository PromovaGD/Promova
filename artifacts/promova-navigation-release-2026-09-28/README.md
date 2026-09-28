# Promova — da evidência à análise

Filme de 25,6 segundos, 1600 × 900, 30 fps, com trilha original. Usa as quatro telas enviadas pelo usuário e o logo fornecido. A forma inicial se abre para o produto; o cursor navega por Visão geral → Caixa de entrada → PR #7 → Analisar evidência → Análise salva. Uma observação ilustrativa é digitada no campo e quatro mensagens são digitadas enquanto a análise carrega.

## Arquivos

- `promova-navigation-release.mp4`: vídeo exportado.
- `index.html`: player com capítulos.
- `scene.html`: composição editável com tempo determinístico via `seek(t)`.
- `render-mp4.mjs`: exportador de quadros e áudio.
- `assets/`: cópias das imagens enviadas, preservadas sem edição.

As telas recebidas têm aproximadamente 770 px de largura, por isso o texto de interface fica suave no MP4. O logo original está em `assets/logo.png`. A frase digitada, “A entrega consolidou validação, rollback e rastreabilidade da análise.”, é roteiro ilustrativo; não representa observação de usuário registrada no produto.

Para renderizar novamente, sirva a raiz do repositório em `http://127.0.0.1:14177` e rode `node artifacts/promova-navigation-release-2026-09-28/render-mp4.mjs`. O exportador usa Chrome, Playwright Core e Mediabunny disponíveis neste workspace.
