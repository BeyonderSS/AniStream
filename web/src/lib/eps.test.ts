import { test } from "node:test";
import assert from "node:assert/strict";
import { epsKey, frac, mergeEps } from "./eps.ts";

test("keys match Android", () => {
  assert.equal(epsKey({ kind: "anime", ep: 12 }), "e12");
  assert.equal(epsKey({ kind: "movie", season: 5, episode: 1 }), "s5e1");
  assert.equal(epsKey({ kind: "movie", season: 0, episode: 0 }), "");
});

test("merge keeps the newest per episode and is symmetric", () => {
  const a = { e1: { p: 0.97, at: 200 }, e2: { p: 0.3, at: 100 } };
  const b = { e1: { p: 0.5, at: 100 }, e3: { p: 0.1, at: 50 } };
  assert.deepEqual(mergeEps(a, b), { e1: { p: 0.97, at: 200 }, e2: { p: 0.3, at: 100 }, e3: { p: 0.1, at: 50 } });
  assert.deepEqual(mergeEps(b, a), mergeEps(a, b));
  assert.deepEqual(mergeEps(undefined, { e1: { p: 2, at: 1 }, bad: null as any }), { e1: { p: 1, at: 1 } });
});

test("progress units", () => {
  assert.equal(frac(970, 1000), 0.97); // app permille
  assert.equal(frac(30, 120), 0.25); // web seconds
  assert.equal(frac(5, 0), 0);
  assert.equal(frac(1500, 1000), 1);
});
