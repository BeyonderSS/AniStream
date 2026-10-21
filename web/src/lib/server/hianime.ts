import * as cheerio from "cheerio";
import type { Element } from "domhandler";

const BASE = "https://hianime.at";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
const KEY = "otaku-embed-v1";

async function get(url: string, referer?: string) {
  const r = await fetch(url, { headers: { "User-Agent": UA, Accept: "text/html,*/*", ...(referer ? { Referer: referer } : {}) } });
  if (!r.ok) throw new Error(`${new URL(url).host} returned HTTP ${r.status}`);
  return r.text();
}

export type Card = { slug: string; title: string; poster?: string; type?: string; rating?: string };
const slugOf = (u: string) => u.split("?")[0].replace(/\/+$/, "").split("/").pop() ?? "";
const txt = (s?: string) => (s?.trim() ? s.trim() : undefined);

function card($: cheerio.CheerioAPI, el: Element): Card | null {
  const e = $(el);
  const a = e.find(".film-name a").first().length ? e.find(".film-name a").first() : e.find("a[href]").first();
  const slug = slugOf(a.attr("href") ?? "");
  const title = a.attr("title") || a.text().trim();
  if (!slug || !title) return null;
  const img = e.find("img.film-poster-img").first();
  return { slug, title, poster: txt(img.attr("data-src") || img.attr("src")), type: txt(e.find(".fd-infor .fdi-item").first().text()), rating: txt(e.find(".tick-rate, .tick-pg").first().text()) };
}
const main = (html: string) => { const $ = cheerio.load(html); $("#main-sidebar").remove(); return $; };
function cards($: cheerio.CheerioAPI, root?: cheerio.Cheerio<any>) {
  const seen = new Set<string>();
  return (root ?? $("body")).find("div.flw-item").toArray().map((e) => card($, e)).filter((c): c is Card => !!c && !seen.has(c.slug) && !!seen.add(c.slug));
}

export async function search(q: string, page = 1) {
  return cards(main(await get(`${BASE}/search?keyword=${encodeURIComponent(q.trim())}&page=${page}`)));
}

export async function browse(path: string, page = 1) {
  return cards(main(await get(`${BASE}/${path.replace(/^\/+|\/+$/g, "")}?page=${page}`)));
}

export async function home() {
  const $ = main(await get(`${BASE}/home`));
  const trending = $("#trending-home .swiper-slide .item").toArray().map((it) => {
    const e = $(it), a = e.find("a.film-poster").first();
    const slug = slugOf(a.attr("href") ?? "");
    if (!slug) return null;
    return { slug, title: a.attr("title") || e.find(".film-title").first().text().trim(), poster: txt(a.find("img").attr("src")), rating: txt(e.find(".tick-rate, .tick-pg").first().text()) } as Card;
  }).filter((c): c is Card => !!c);
  const block = (h: string) => $("#anime-featured .anif-block").toArray()
    .filter((b) => $(b).find(".anif-block-header").first().text().trim().toLowerCase() === h.toLowerCase())
    .flatMap((b) => $(b).find("li").toArray().map((l) => card($, l)).filter((c): c is Card => !!c));
  const recent = $("h2.cat-heading").toArray().find((h) => $(h).text().trim().toLowerCase() === "latest episode");
  return {
    trending, topAiring: block("Top Airing"), popular: block("Most Popular"), favorites: block("Most Favorite"), completed: block("Latest Completed"),
    recent: recent ? cards($, $(recent).closest("section")) : [],
  };
}

export async function detail(slug: string) {
  const $ = main(await get(`${BASE}/${slug}`));
  const name = $("h2.film-name").first();
  const stats = $(".film-stats").first();
  const tick = (c: string) => { const m = stats.find(`.tick-${c}`).first().text().match(/\d+/); return m ? +m[0] : undefined; };
  const info: Record<string, cheerio.Cheerio<any>> = {};
  $(".anisc-info .item").each((_, e) => { info[$(e).find(".item-head").first().text().trim()] = $(e); });
  const it = (l: string) => txt(info[l]?.find(".name").first().text());
  const links = (l: string) => info[l]?.find("a").toArray().map((a) => $(a).text().trim()).filter(Boolean) ?? [];
  const items = stats.find(".item").toArray().map((e) => $(e).text().trim());
  const premiered = it("Premiered:");
  const rec = $("h2.cat-heading").toArray().find((h) => $(h).text().trim().toLowerCase() === "recommended for you");
  return {
    slug, title: name.text().trim(), japanese: txt(name.attr("data-jname")) ?? it("Japanese:"),
    synopsis: txt($(".film-description .text").first().text()), poster: txt($(".anisc-poster img").first().attr("src")),
    type: items[0], status: it("Status:"), score: it("MAL Score:"), rating: txt(stats.find(".tick-pg").first().text()),
    duration: it("Duration:") ?? items[1], year: Number(((premiered ?? it("Aired:")) ?? "").match(/\b(19|20)\d{2}\b/)?.[0]) || undefined,
    genres: info["Genres:"]?.find("a[href*=/genres/]").toArray().map((a) => $(a).text().trim()) ?? [],
    studios: links("Studios:"), sub: tick("sub"), dub: tick("dub"),
    seasons: $(".os-list a.os-item").toArray().map((a) => ({ slug: slugOf($(a).attr("href") ?? ""), title: $(a).find(".title").first().text().trim() || $(a).attr("title") || "" })),
    recommendations: rec ? cards($, $(rec).closest("section")) : [],
  };
}

export async function episodes(slug: string) {
  const id = Number(slug.split("-").pop());
  if (!id) throw new Error("Invalid slug");
  const { html } = JSON.parse(await get(`${BASE}/api/theme/episode/list/${id}`)) as { html: string };
  const $ = cheerio.load(html);
  return $("a.ep-item").toArray().filter((a) => ($(a).attr("href") ?? "").includes(`/watch/${slug}?`)).map((a) => ({
    id: Number($(a).attr("data-id")), number: Number($(a).attr("data-number")), title: txt($(a).attr("title")),
  })).filter((e) => e.id && e.number);
}

function decodeBlob(blob: string) {
  const b = Uint8Array.from(atob(blob), (c) => c.charCodeAt(0));
  return new TextDecoder().decode(b.map((x, i) => x ^ KEY.charCodeAt(i % KEY.length)));
}

/** Resolve an episode to the master playlist + subtitle. The stream host needs `referer`. */
export async function stream(episodeId: number, lang: string) {
  const type = lang === "dub" || lang === "eng" ? "dub" : "sub";
  const { html } = JSON.parse(await get(`${BASE}/api/theme/episode/servers?episodeId=${episodeId}`)) as { html: string };
  const $ = cheerio.load(html);
  const urls = $(`.server-item[data-type=${type}]`).toArray()
    .sort((a, b) => +/zokoanime/i.test($(b).attr("data-server-name") ?? "") - +/zokoanime/i.test($(a).attr("data-server-name") ?? ""))
    .map((e) => { try { return atob(($(e).attr("data-hash") ?? "").trim()); } catch { return ""; } })
    .filter((u) => /zokoanime/i.test(u));
  for (const embed of new Set(urls)) {
    try {
      const blob = (await get(embed)).match(/window\.__P\s*=\s*"([^"]+)"/)?.[1];
      if (!blob) continue;
      const cfg = JSON.parse(decodeBlob(blob)) as { src?: string; subtitles?: { src: string; lang?: string; label?: string; default?: boolean }[] };
      if (!cfg.src) continue;
      const subs = cfg.subtitles ?? [];
      return {
        master: cfg.src, referer: new URL(embed).origin + "/", malId: Number(embed.match(/\/mal\/(\d+)\//)?.[1]) || undefined,
        subtitles: subs.map((s) => ({ url: s.src, label: s.label ?? s.lang ?? "Subtitles", lang: s.lang ?? "", default: !!s.default })),
      };
    } catch { /* try the next embed */ }
  }
  throw new Error("This isn't available to stream yet. Check back after it releases.");
}

export async function schedule() {
  const tz = -new Date().getTimezoneOffset();
  const days = await Promise.all([...Array(7)].map(async (_, i) => {
    const d = new Date(Date.now() + i * 864e5);
    const date = d.toISOString().slice(0, 10);
    const { html } = JSON.parse(await get(`${BASE}/api/theme/schedule/day?tzOffset=${-tz}&date=${date}`)) as { html: string };
    const $ = cheerio.load(html);
    return { date, day: d.toLocaleDateString("en-US", { weekday: "long", timeZone: "UTC" }), items: $("a.tsl-link").toArray().map((a) => ({
      slug: slugOf($(a).attr("href") ?? ""), title: $(a).find(".film-name").first().text().trim(),
      episode: Number($(a).find(".btn-play").first().text().match(/\d+/)?.[0]) || undefined, time: txt($(a).find(".time").first().text()),
    })) };
  }));
  return days;
}
