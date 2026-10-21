// node scripts/check-play.mjs — every payload shape the apps write must become a playable web `Play`.
import assert from "node:assert/strict";
import { toPlay, normTitle, playKey } from "../src/lib/core.ts";

// Android/TV media (movie): the payload from the bug report
assert.deepEqual(toPlay({ slug: "mb:1732378050823619144", title: "Obsession", kind: "media", mediaId: "mb:1732378050823619144", season: 0, episode: 0, poster: "x.jpg" }),
  { kind: "movie", id: "mb:1732378050823619144", title: "Obsession", poster: "x.jpg", season: 0, episode: 0, type: "movie", year: undefined });
// Android media series without mediaId: slug carries an episode suffix
assert.deepEqual(toPlay({ slug: "tt0903747:s2e3", title: "Breaking Bad", kind: "media", season: 2, episode: 3 }),
  { kind: "movie", id: "tt0903747", title: "Breaking Bad", poster: undefined, season: 2, episode: 3, type: "series", year: undefined });
// Android anime: episode number, no epId
assert.deepEqual(toPlay({ slug: "naruto-1335", title: "Naruto", kind: "anime", episode: 7, progress: 0.4 }),
  { kind: "anime", id: "naruto-1335", title: "Naruto", poster: undefined, ep: 7, epId: undefined, lang: "sub" });
// Web payloads pass through
assert.equal(toPlay({ kind: "anime", id: "naruto-1335", title: "Naruto", ep: 1, epId: 22676, lang: "dub" }).epId, 22676);
assert.equal(toPlay({ kind: "movie", id: "tt1", title: "A", type: "series", season: 1, episode: 2 }).type, "series");
assert.equal(toPlay({ kind: "live", id: "u", title: "Ch", url: "https://x/y.m3u8" }).url, "https://x/y.m3u8");
// junk
assert.equal(toPlay(null), null);
assert.equal(toPlay({ kind: "media" }), null);
// canonical media key: same vectors as Android MediaSlugTest
assert.equal(normTitle("The Mentalist"), "mentalist");
assert.equal(normTitle("Mentalist"), "mentalist");
assert.equal(normTitle("Spider-Man: Brand New Day"), "spidermanbrandnewday");
assert.equal(normTitle("Fast & Furious"), "fastandfurious");
assert.equal(playKey({ kind: "movie", id: "tt1", title: "The Mentalist", type: "series" }), playKey({ kind: "movie", id: "mb:9", title: "Mentalist", type: "series" }));
assert.equal(playKey({ kind: "movie", id: "tt1", title: "The Mentalist", type: "series" }), "media:series:mentalist");
console.log("toPlay ok");
