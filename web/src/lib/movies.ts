import * as mb from "@/lib/server/moviebox";
import * as misc from "@/lib/server/misc";

// MovieBox refuses datacenter IPs (Cloudflare, Vercel: HTTP 440) but allows CORS, so the browser runs these
// from the viewer's own connection (see get() in core.ts). The API route keeps them for local dev.
const num = (v: string | null, d = 0) => Number(v) || d;

async function findMb(q: URLSearchParams) {
  const type = q.get("type") === "series" ? "series" : "movie", year = num(q.get("year"));
  const hits = (await mb.search(q.get("title") ?? "")).filter((h) => h.type === type);
  const norm = (t: string) => t.toLowerCase().replace(/[^a-z0-9]/g, "");
  const same = hits.filter((h) => norm(h.title) === norm(q.get("title") ?? ""));
  return (same.find((h) => !year || Math.abs((h.year ?? year) - year) <= 1) ?? same[0] ?? hits[0])?.id.slice(3);
}

export const movieRoutes: Record<string, (q: URLSearchParams) => Promise<unknown>> = {
  "movies/search": async (q) => {
    const t = q.get("q") ?? "";
    const m = await mb.search(t).catch(() => []);
    return m.length ? m : misc.cinemetaSearch(t);
  },
  "movies/detail": (q) => {
    const id = q.get("id")!;
    return id.startsWith("mb:") ? mb.detail(id.slice(3)) : misc.cinemetaDetail(id, q.get("type") === "series" ? "series" : "movie");
  },
  "movies/stream": async (q) => {
    const id = q.get("id")!;
    const sid = id.startsWith("mb:") ? id.slice(3) : await findMb(q);
    if (!sid) throw new Error("No stream source found for this title");
    return mb.streams(sid, num(q.get("s")), num(q.get("e")));
  },
};
