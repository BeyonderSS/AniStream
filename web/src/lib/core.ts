export const API = process.env.NEXT_PUBLIC_REELM_API_URL || "https://reelm-api.ssbeyonder.workers.dev";
export const WS = API.replace(/^http/, "ws");

/** Server-side content routes (same origin). Catalog reads are shared per page load (home asked for anime/home four times);
 *  stream URLs carry expiring tokens, so they are never cached. */
const memo = new Map<string, Promise<any>>();
export function get<T = any>(path: string): Promise<T> {
  const hit = memo.get(path);
  if (hit) return hit;
  const [key, qs = ""] = path.split("?");
  if (typeof window !== "undefined" && /^movies\/(search|detail|stream)$/.test(key)) {
    const p = import("./movies").then((m) => m.movieRoutes[key](new URLSearchParams(qs))) as Promise<T>;
    if (key !== "movies/stream") { memo.set(path, p); p.catch(() => memo.delete(path)); }
    return p;
  }
  const p = fetch(`/api/${path}`).then(async (r) => {
    const d = await r.json().catch(() => ({ error: `HTTP ${r.status}` }));
    if (!r.ok) throw new Error(d.error ?? `HTTP ${r.status}`);
    return d as T;
  });
  if (!/stream/.test(path)) { memo.set(path, p); p.catch(() => memo.delete(path)); }
  return p;
}

/** Reelm account API (server/API.md). */
export async function reelm<T = any>(path: string, o: { method?: string; json?: unknown; token?: string } = {}): Promise<T> {
  const r = await fetch(API + path, {
    method: o.method ?? (o.json ? "POST" : "GET"),
    headers: { ...(o.json ? { "content-type": "application/json" } : {}), ...(o.token ? { Authorization: `Bearer ${o.token}` } : {}) },
    body: o.json ? JSON.stringify(o.json) : undefined,
  });
  if (r.status === 204) return undefined as T;
  const d = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(d.error ?? `HTTP ${r.status}`);
  return d;
}

/** Route a stream through the Worker proxy (adds Referer + CORS). */
export const proxied = (u: string, referer = "", any = false) =>
  `${API}/v1/proxy?u=${encodeURIComponent(u)}&r=${encodeURIComponent(referer)}${any ? "&any=1" : ""}`;

// ---- Fluent Emoji avatars (ids and URL format from android core/account Models.kt) ----
const FOLDERS = [
  "Grinning face", "Smiling face with sunglasses", "Star-struck", "Nerd face", "Partying face", "Winking face",
  "Cat face", "Dog face", "Fox", "Panda", "Koala", "Lion", "Unicorn", "Frog", "Monkey face", "Penguin", "Owl", "Rabbit face",
  "Pizza", "Hamburger", "Cupcake", "Doughnut", "Avocado", "Popcorn",
];
const idOf = (f: string) => f.toLowerCase().replace(/ /g, "_");
export const EMOJI_IDS = FOLDERS.map(idOf);
export const EMOJI_GROUPS: [string, string[]][] = [["Faces", EMOJI_IDS.slice(0, 6)], ["Animals", EMOJI_IDS.slice(6, 18)], ["Snacks", EMOJI_IDS.slice(18)]];
export const avatarUrl = (id: string) => {
  const f = FOLDERS.find((x) => idOf(x) === id) ?? FOLDERS[0];
  return `https://cdn.jsdelivr.net/gh/microsoft/fluentui-emoji@main/assets/${f.replace(/ /g, "%20")}/3D/${idOf(f)}_3d.png`;
};

// ---- Kids policy (port of android domain KidsPolicy) ----
const ANIME_GENRES = ["hentai", "ecchi", "horror", "gore"];
const MEDIA_GENRES = ["horror", "thriller", "crime", "war", "adult", "erotic"];
const FAMILY_GENRES = ["animation", "family", "kids", "comedy", "adventure"];
const ADULT_CERTS = ["R", "NC-17", "NC17", "X", "TV-MA", "18", "18+", "R18", "R+", "RX"];
export const KIDS_BLOCKED = "Not available on this profile";
export const kids = {
  animeRatingBlocked: (r?: string) => /^(R|18|NC)/.test((r ?? "").trim().toUpperCase()),
  animeCard: (c: { rating?: string }) => !kids.animeRatingBlocked(c.rating),
  anime: (rating: string | undefined, genres: string[]) =>
    !kids.animeRatingBlocked(rating) && !genres.some((g) => ANIME_GENRES.includes(g.trim().toLowerCase())) && (!!rating?.trim() || genres.length > 0),
  media(genres: string[] = [], cert?: string) {
    if (cert && ADULT_CERTS.includes(cert.trim().toUpperCase())) return false;
    const g = genres.map((x) => x.trim().toLowerCase());
    return !g.some((x) => MEDIA_GENRES.includes(x)) && g.some((x) => FAMILY_GENRES.includes(x));
  },
};

// ---- Local library: history (per profile) + favorites ----
export type Play = { kind: "anime" | "movie" | "live"; id: string; title: string; poster?: string; ep?: number; epId?: number; season?: number; episode?: number; lang?: string; type?: string; year?: number; url?: string };
export type Entry = { key: string; payload: Play; position: number; duration: number; updatedAt: number; deleted: boolean };
export type CardData = { href: string; title: string; poster?: string; sub?: string; rating?: string; genres?: string[]; kind?: "anime" | "media"; progress?: number };

export const playKey = (p: Play) => p.kind === "anime" ? `anime:${p.id}:${p.ep}` : `movie:${p.id}:${p.season ?? 0}:${p.episode ?? 0}`;
/** `f` resumes at a fraction of the duration (synced entries from the apps store progress as 0..1000 of 1000). */
export const watchHref = (p: Play, f = 0) => `/watch?p=${encodeURIComponent(JSON.stringify(p))}${f > 0 && f < 0.95 ? `&f=${f.toFixed(4)}` : ""}`;
export const entryHref = (e: Entry) => watchHref(e.payload, e.duration ? e.position / e.duration : 0);
export const entrySub = (p: Play) => p.kind === "anime" ? `Episode ${p.ep}` : p.season ? `Season ${p.season}, episode ${p.episode}` : "Movie";

const num = (v: unknown) => (Number.isFinite(Number(v)) ? Number(v) : 0);
const str = (v: unknown) => (v == null ? "" : String(v).trim());
/**
 * Turn any payload the apps write into a web `Play`: web payloads ({kind:"anime",id,ep,epId} / {kind:"movie",id,season,episode,type}),
 * Android/TV HistoryPayload ({slug,title,kind:"anime"|"media",episode,season,mediaId,poster,progress}) and room payloads.
 * Anime without an `epId` is resolved by episode number on the watch page.
 */
export function toPlay(r: any): Play | null {
  if (!r || typeof r !== "object") return null;
  const title = str(r.title ?? r.animeTitle), poster = str(r.poster ?? r.posterUrl) || undefined;
  if (r.kind === "live") return r.url ? { kind: "live", id: str(r.id) || r.url, title, poster, url: r.url } : null;
  if (r.kind === "anime" || (!r.kind && !r.mediaId)) {
    const id = str(r.id ?? r.slug ?? r.animeSlug);
    if (!id) return null;
    return { kind: "anime", id, title, poster, ep: Math.max(1, num(r.ep ?? r.episode ?? r.lastEpisodeWatched)), epId: num(r.epId) || undefined, lang: r.lang === "dub" ? "dub" : "sub" };
  }
  const id = str(r.kind === "media" ? r.mediaId || r.slug : r.id || r.mediaId).replace(/:s\d+e\d+$/, "");
  if (!id) return null;
  const season = num(r.season), episode = num(r.episode);
  const type = r.type === "series" || r.type === "movie" ? r.type : season > 0 || episode > 0 ? "series" : "movie";
  return { kind: "movie", id, title, poster, season, episode, type, year: num(r.year) || undefined };
}

const ls = {
  get<T>(k: string, d: T): T { try { return JSON.parse(localStorage.getItem(k) ?? "") ?? d; } catch { return d; } },
  set(k: string, v: unknown) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};
export const getFavs = () => ls.get<CardData[]>("reelm.fav", []);
export const toggleFav = (c: CardData) => { const f = getFavs(), has = f.some((x) => x.href === c.href); ls.set("reelm.fav", has ? f.filter((x) => x.href !== c.href) : [c, ...f]); return !has; };

const hKey = (pid: string) => `reelm.hist.${pid}`;
/** History with every payload normalized to `Play` (entries whose payload can't be played are dropped). */
export function getHistory(pid: string): Record<string, Entry> {
  const out: Record<string, Entry> = {};
  for (const [k, e] of Object.entries(ls.get<Record<string, Entry>>(hKey(pid), {}))) {
    const payload = toPlay(e?.payload);
    if (payload) out[k] = { ...e, payload };
  }
  return out;
}
/** Progress (0..1) per history key, for episode grids. */
export const progressOf = (pid: string) => Object.fromEntries(Object.values(getHistory(pid)).filter((e) => !e.deleted && e.duration).map((e) => [playKey(e.payload), Math.min(1, e.position / e.duration)]));
/** Latest entry per show, unfinished first-class citizens of Continue Watching. */
export function continueWatching(pid: string): Entry[] {
  const seen = new Set<string>();
  return Object.values(getHistory(pid)).filter((e) => !e.deleted && e.payload.kind !== "live").sort((a, b) => b.updatedAt - a.updatedAt)
    .filter((e) => { const s = `${e.payload.kind}:${e.payload.id}`; return !seen.has(s) && !!seen.add(s); })
    .filter((e) => !e.duration || e.position / e.duration < 0.95);
}
export async function saveProgress(pid: string, token: string | undefined, p: Play, position: number, duration: number) {
  if (p.kind === "live") return;
  const e: Entry = { key: playKey(p), payload: p, position, duration, updatedAt: Date.now(), deleted: false };
  ls.set(hKey(pid), { ...getHistory(pid), [e.key]: e });
  if (token) await reelm(`/v1/profiles/${pid}/history`, { method: "PUT", token, json: { entries: [e] } }).catch(() => {});
}
/** Pull the profile's synced history into the local copy (last write wins). */
export async function syncHistory(pid: string, token: string) {
  const { entries } = await reelm<{ entries: Entry[] }>(`/v1/profiles/${pid}/history?since=0`, { token });
  const h = ls.get<Record<string, Entry>>(hKey(pid), {});
  for (const e of entries) if (!h[e.key] || h[e.key].updatedAt < e.updatedAt) h[e.key] = e;
  ls.set(hKey(pid), h);
}

export type Profile = { id: string; name: string; avatar: string; kids: boolean };
export type Session = { token?: string; user?: { id: string; username: string }; profile?: Profile; guest?: boolean };
export const loadSession = () => ls.get<Session>("reelm.session", {});
export const storeSession = (s: Session) => ls.set("reelm.session", s);
export const GUEST: Profile = { id: "guest", name: "Guest", avatar: "grinning_face", kids: false };
export const deviceId = () => { let d = ls.get<string>("reelm.device", ""); if (!d) ls.set("reelm.device", (d = crypto.randomUUID())); return d; };
