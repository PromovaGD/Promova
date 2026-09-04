import assert from "node:assert/strict";
import test from "node:test";

import { fetchEvidence, fetchEvidences } from "../services/evidence-api.mjs";

test("evidence API consumes canonical durable content without browser storage state", async () => {
  const originalFetch = globalThis.fetch;
  const payload = {
    id: 41,
    source: "GitHub",
    externalId: "github:acme/app#7",
    sourceMeta: "PR #7",
    content: "Improved checkout coverage",
    occurredAt: "2026-05-10T10:00:00Z",
    capturedAt: "2026-05-12T10:00:00Z",
    updatedAt: "2026-05-12T10:00:00Z",
    status: "PENDING",
  };

  globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => payload });

  try {
    const evidence = await fetchEvidence(41);
    assert.equal(evidence.content, "Improved checkout coverage");
    assert.equal("evidence" in evidence, false);
    assert.equal(evidence.occurredAt, "2026-05-10T10:00:00Z");

    globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => [payload] });
    assert.deepEqual(await fetchEvidences(), [evidence]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
