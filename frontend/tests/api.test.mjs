import assert from "node:assert/strict";
import test from "node:test";

import { api } from "../services/api.mjs";

test("review retries reuse a deterministic idempotency key", async () => {
  const originalFetch = globalThis.fetch;
  const bodies = [];
  globalThis.fetch = async (_url, options) => {
    bodies.push(JSON.parse(options.body));
    return jsonResponse({ currentStatus: "ACCEPTED", history: [] });
  };

  try {
    const body = {
      status: "ACCEPTED",
      comment: "Evidence is clear.",
      expectedLatestReviewId: 19,
      idempotencyKey: "random-browser-value",
    };
    await api.review(2, 7, body);
    await api.review(2, 7, body);

    assert.equal(bodies[0].idempotencyKey, bodies[1].idempotencyKey);
    assert.match(bodies[0].idempotencyKey, /^review-2-7-19-[a-f0-9]+$/);
    assert.notEqual(bodies[0].idempotencyKey, body.idempotencyKey);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("analyze reconciles an ambiguous response through canonical detail", async () => {
  const originalFetch = globalThis.fetch;
  const requests = [];
  globalThis.fetch = async (url, options = {}) => {
    requests.push({ url: String(url), method: options.method || "GET" });
    if (requests.length === 1) return errorResponse(503, "Response was interrupted.");
    return jsonResponse({ id: 41, status: "ANALYZED", analysisId: 91 });
  };

  try {
    assert.deepEqual(await api.analyzeEvidence(41, "Context"), { analysisId: 91 });
    assert.deepEqual(requests.map(({ method }) => method), ["POST", "GET"]);
    assert.match(requests[1].url, /\/evidences\/41$/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test("dismiss reconciles a completed transition after a lost response", async () => {
  const originalFetch = globalThis.fetch;
  let call = 0;
  globalThis.fetch = async () => {
    call += 1;
    if (call === 1) return errorResponse(500, "Unknown outcome.");
    return jsonResponse({ id: 8, status: "DISMISSED" });
  };

  try {
    assert.equal((await api.dismissEvidence(8)).status, "DISMISSED");
    assert.equal(call, 2);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

function jsonResponse(payload) {
  return new Response(JSON.stringify(payload), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

function errorResponse(status, message) {
  return new Response(JSON.stringify({ message }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
