import assert from "node:assert/strict";
import test from "node:test";

import { evidenceDisplay, selectedEvidence } from "../utils/evidence-display.mjs";

test("GitHub records show work descriptions instead of PR identifiers", () => {
  const display = evidenceDisplay({
    sourceMeta: "PR #7 - acme/app - Improve checkout coverage",
    content: "GitHub - repositorio acme/app - PR #7\n\nTitulo: Improve checkout coverage\n\nDescricao/resumo:\nAdded integration tests for checkout and fixed retries.\n\nLink do PR: https://example.com/7",
  });

  assert.deepEqual(display, {
    title: "Improve checkout coverage",
    summary: "Added integration tests for checkout and fixed retries.",
    origin: "PR #7 · acme/app",
  });
});

test("old records use only available source wording and bound long text", () => {
  const display = evidenceDisplay({
    source: "Slack",
    sourceMeta: "Thread de implantação",
    content: "Documentei o procedimento de recuperação.\nResultado medido pela equipe.",
  });
  assert.equal(display.title, "Documentei o procedimento de recuperação.");
  assert.equal(display.summary, "Resultado medido pela equipe.");
  assert.equal(display.origin, "Thread de implantação");

  const long = evidenceDisplay({ content: `Titulo: ${"x".repeat(1200)}\nDescricao/resumo:\n${"y".repeat(1200)}` });
  assert.ok(long.title.length <= 101);
  assert.ok(long.summary.length <= 171);
  assert.equal(evidenceDisplay({ sourceMeta: "PR #9 - acme/app" }).title, "Descrição não informada");
  assert.deepEqual(evidenceDisplay({
    sourceMeta: "PR #10 - acme/app - Corrigir consulta de pagamentos",
    content: "Descricao/resumo:\nAjustei a consulta para manter os resultados ordenados.\nLink do PR: https://example.com/10",
  }), {
    title: "Corrigir consulta de pagamentos",
    summary: "Ajustei a consulta para manter os resultados ordenados.",
    origin: "PR #10 · acme/app",
  });
});

test("selection keeps the requested item and falls back to the first visible record", () => {
  const items = [{ id: 7 }, { id: 8 }];
  assert.equal(selectedEvidence(items, 8), items[1]);
  assert.equal(selectedEvidence(items, 9), items[0]);
  assert.equal(selectedEvidence([], 8), null);
});

test("AI title takes precedence after analysis while legacy records keep their source title", () => {
  const evidence = { content: "Titulo: Ajuste de pagamentos", analysisTitle: "Corrigiu a ordenação dos pagamentos" };
  assert.equal(evidenceDisplay(evidence).title, "Corrigiu a ordenação dos pagamentos");
  assert.equal(evidenceDisplay({ content: evidence.content }).title, "Ajuste de pagamentos");
  assert.equal(evidenceDisplay({ content: evidence.content, analysisTitle: "  " }).title, "Ajuste de pagamentos");
});
