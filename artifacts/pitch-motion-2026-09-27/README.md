# Promova — demonstração do produto para o pitch

Vídeo contínuo de aproximadamente 30 segundos, 1600 × 900 (16:9), para inserir dentro de um slide. A gravação acompanha o aplicativo em uso: cursor, cliques, digitação, navegação, carregamento e alterações reais na interface. Não é uma sequência de telas estáticas.

## Usar na apresentação

Insira `promova-demo-produto-30s.mp4` no slide da demonstração e configure reprodução automática. O vídeo não tem música nem locução para permitir a fala ao vivo. `index.html` abre uma prévia com controles e um botão para baixar o MP4.

## Fluxo mostrado

1. João abre a caixa de entrada e uma evidência capturada do GitHub.
2. Acrescenta uma observação e solicita a análise da evidência.
3. Consulta o resumo e a fonte da análise.
4. A pessoa gestora abre o painel, entra na fila de revisões, comenta e aceita a análise.
5. Abre o plano de carreira de João e marca um objetivo como concluído.

Uma fala curta possível: “O trabalho entra com a fonte preservada. A pessoa adiciona contexto; a análise organiza a evidência. A gestão revisa o resultado e acompanha sua evolução no plano de carreira.”

## Produção e limites

`record-live.mjs` gravou uma sessão executada no aplicativo local em Chrome, usando dados fictícios do seed `dev` e um banco H2 temporário. O script espera frontend em `http://127.0.0.1:14176` e backend em `http://127.0.0.1:18084` para repetir a captura.

A análise exibida foi produzida pelo motor **mock** do ambiente `dev`. Seu texto ilustrativo aparece em inglês; não representa a redação do provedor de IA de produção. Pessoas e PRs também são fictícios.

Referências de formato: [Notion Demo Hub](https://www.notion.com/en-gb/product/demos) e [Figma Demos](https://www.figma.com/demo/), que demonstram o produto por fluxos de uso.
