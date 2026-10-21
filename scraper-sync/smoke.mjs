// Live end-to-end checks that run an extracted config against the real sites.
// Result is "pass", "fail" (site answered, flow broke) or "skip" (blocked: Cloudflare challenge, MovieBox HTTP 440 to datacenter IPs).
import { createHash, createHmac, randomBytes, randomUUID } from "node:crypto";

export class Skip extends Error {}
const T = 20000;
const attr = (tag, name) => tag.match(new RegExp(`\\b${name}="([^"]*)"`))?.[1];
const tags = (html, cls) => html.match(new RegExp(`<[a-z]+\\b[^>]*\\b${cls}\\b[^>]*>`, "g")) ?? [];
const must = (v, msg) => { if (!v) throw new Error(msg); return v; };

// --- hianime ----------------------------------------------------------------------------------------------------
export async function hianime(c) {
  const get = async (url, referer) => {
    const r = await fetch(url, { headers: { "User-Agent": c.userAgent, ...(referer && { Referer: referer }) }, signal: AbortSignal.timeout(T) });
    const t = await r.text();
    if (/<title>Just a moment/i.test(t)) throw new Skip("hianime: Cloudflare challenge");
    if (!r.ok) throw new Error(`${new URL(url).host} HTTP ${r.status}`);
    return t;
  };
  const fill = (tpl, v) => c.base + tpl.replace("%s", v);

  const search = (await get(fill(c.search, "naruto"))).split('id="main-sidebar"')[0];
  const slug = must(search.match(/<h3 class="film-name">\s*<a href="[^"]*\/([^"\/]+)"/)?.[1], "search: no results parsed");

  const eps = JSON.parse(await get(fill(c.episodes, slug.split("-").pop()))).html;
  const ep = must(tags(eps, "ep-item").map((t) => attr(t, "data-id")).find(Boolean), "episodes: no episode id parsed");

  const servers = JSON.parse(await get(fill(c.servers, ep))).html;
  const hash = must(tags(servers, "server-item").find((t) => attr(t, "data-type") === "sub" && attr(t, "data-server-name")?.toLowerCase() === c.server.toLowerCase() && attr(t, "data-hash")), `servers: no ${c.server} sub server`);
  const embed = Buffer.from(attr(hash, "data-hash").trim(), "base64").toString();
  must(/^https?:\/\//.test(embed), "servers: data-hash is not a url");

  const blob = must((await get(embed)).match(/window\.__P\s*=\s*"([^"]+)"/)?.[1], "embed: window.__P missing");
  const xor = Buffer.from(blob, "base64").map((b, i) => b ^ c.key.charCodeAt(i % c.key.length));
  let cfg;
  try { cfg = JSON.parse(xor.toString()); } catch { throw new Error("embed: blob does not decode with the xor key"); }
  const master = must(cfg.src, "embed: decoded config has no src");

  const m3u8 = await get(master, new URL(embed).origin + "/");
  if (!m3u8.startsWith("#EXTM3U")) throw new Error("stream: master playlist is not an m3u8");
  return `${slug} ep ${ep} -> ${master.split("/").slice(2, 3)[0]}`;
}

// --- moviebox ---------------------------------------------------------------------------------------------------
const md5 = (b) => createHash("md5").update(b).digest("hex");
function canonicalUrl(url) {
  const u = new URL(url);
  const pairs = [...u.searchParams].sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
  return pairs.length ? `${u.pathname}?${pairs.map(([k, v]) => `${k}=${v}`).join("&")}` : u.pathname;
}
export function sign(secret, method, url, body, ts) {
  const bytes = body == null ? null : Buffer.from(body);
  const canon = [method, "application/json", "application/json", bytes ? bytes.length : "", ts, bytes ? md5(bytes.subarray(0, 102400)) : "", canonicalUrl(url)].join("\n");
  return `${ts}|2|${createHmac("md5", Buffer.from(secret)).update(canon).digest("base64")}`;
}

export async function moviebox(c) {
  const pick = (a) => a[Math.floor(Math.random() * a.length)];
  const vc = pick(c.versionCodes), [os, build] = pick(c.androidVersions), [model, brand] = pick(c.devices);
  const ua = `${c.packageName}/${vc} (Linux; U; Android ${os}; en_US; ${model}; Build/${build}; Cronet/${c.cronet})`;
  const info = JSON.stringify({ package_name: c.packageName, version_name: c.versionName, version_code: vc, os: "android", os_version: os, install_ch: "ps", device_id: randomBytes(16).toString("hex"), install_store: "ps", gaid: randomUUID(), brand, model, system_language: "en", net: pick(c.networks), region: "US", timezone: pick(c.timezones), sp_code: c.spCode, "X-Play-Mode": "2" });
  let token, blocked = 0;

  const call = async (method, path, body = null) => {
    let last;
    for (const host of c.hosts) {
      const ts = Date.now(), url = host + path;
      const h = { "User-Agent": ua, Accept: "application/json", "Content-Type": "application/json", "x-client-token": `${ts},${md5([...String(ts)].reverse().join(""))}`, "x-tr-signature": sign(c.secret, method, url, body, ts), "x-client-info": info, "x-client-status": "0" };
      if (token) h.Authorization = `Bearer ${token}`;
      try {
        const r = await fetch(url, { method, headers: h, body: method === "POST" ? body ?? "{}" : undefined, signal: AbortSignal.timeout(T) });
        if (r.status === 440) { blocked++; last = new Error("HTTP 440"); continue; }
        if (c.retryStatus.includes(r.status)) { last = new Error(`HTTP ${r.status}`); continue; }
        if (!r.ok) throw new Error(`${path} HTTP ${r.status}`);
        try { token = JSON.parse(r.headers.get("x-user") ?? "{}").token || token; } catch {}
        const root = await r.json();
        if (Number(root.code ?? 0) !== 0) throw new Error(`${path} code ${root.code}: ${root.message}`);
        return root.data ?? {};
      } catch (e) { last = e; if (/^\/wefeed.* (HTTP|code)/.test(e.message)) throw e; }
    }
    if (blocked === c.hosts.length) throw new Skip("moviebox: every host answered HTTP 440 (datacenter IP)");
    throw new Error(`moviebox: all hosts failed (${last?.message}${last?.cause?.code ? ` ${last.cause.code}` : ""})`);
  };

  const login = await call("POST", "/wefeed-mobile-bff/user-api/visitor-login", "{}");
  token = login.token || token;
  must(token, "login: no token");
  const found = await call("POST", "/wefeed-mobile-bff/subject-api/search/v2", JSON.stringify({ keyword: "avatar", page: 1, perPage: 15, subjectType: 0 }));
  const hit = must((found.results ?? []).flatMap((r) => r.subjects ?? []).find((s) => s.subjectId && s.title), "search: no results parsed");
  const d = await call("GET", `/wefeed-mobile-bff/subject-api/get?subjectId=${hit.subjectId}`);
  must(d.title, "detail: no title");
  return `${hit.title} (${hit.subjectId})`;
}

export const checks = { hianime, moviebox };

/** Runs one check and never throws: { status, detail }. */
export async function run(source, config) {
  try { return { status: "pass", detail: await checks[source](config) }; }
  catch (e) { return { status: e instanceof Skip ? "skip" : "fail", detail: e.message }; }
}

if (process.argv[1]?.endsWith("smoke.mjs")) {
  const [, , source, file] = process.argv;
  const cfg = JSON.parse((await import("node:fs")).readFileSync(file, "utf8"))[source].config;
  const r = await run(source, cfg);
  console.log(source, r.status, r.detail);
  process.exit(r.status === "fail" ? 1 : 0);
}
