import { timingSafeEqual } from "node:crypto";
import type { IncomingMessage, ServerResponse } from "node:http";

// Replays a request the Reelm web app already signed, from a host MovieBox doesn't block (it answers 440 to Cloudflare Workers).
// The signature covers method, path+query, content headers and the body bytes, so all of them pass through untouched.
const HOSTS = new Set(["api6.aoneroom.com", "api5.aoneroom.com", "api4.aoneroom.com", "api4sg.aoneroom.com", "api3.aoneroom.com", "api6sg.aoneroom.com", "api.inmoviebox.com"]);
const PASS = ["user-agent", "accept", "content-type", "x-client-token", "x-tr-signature", "x-client-info", "x-client-status", "x-forwarded-for", "authorization"];

const authorized = (got: unknown) => {
  const want = Buffer.from(process.env.RELAY_KEY ?? ""), have = Buffer.from(typeof got === "string" ? got : "");
  return want.length > 0 && want.length === have.length && timingSafeEqual(want, have);
};

export default async function handler(req: IncomingMessage, res: ServerResponse) {
  const done = (status: number, msg: string) => { res.statusCode = status; res.setHeader("content-type", "application/json"); res.end(JSON.stringify({ error: msg })); };
  if (!authorized(req.headers["x-relay-key"])) return done(401, "bad relay key");
  const q = new URL(req.url ?? "/", "http://relay").searchParams;
  const host = q.get("host") ?? "", path = q.get("path") ?? "";
  if (!HOSTS.has(host) || !path.startsWith("/wefeed-mobile-bff/")) return done(400, "host or path not allowed");
  const method = req.method ?? "GET";
  const chunks: Buffer[] = [];
  for await (const c of req) chunks.push(c as Buffer);
  const headers: Record<string, string> = {};
  for (const k of PASS) { const v = req.headers[k]; if (typeof v === "string") headers[k] = v; }
  try {
    const r = await fetch(`https://${host}${path}`, { method, headers, body: method === "GET" || method === "HEAD" ? undefined : Buffer.concat(chunks) });
    res.statusCode = r.status;
    res.setHeader("content-type", r.headers.get("content-type") ?? "application/json");
    const xu = r.headers.get("x-user");
    if (xu) res.setHeader("x-user", xu);
    res.end(Buffer.from(await r.arrayBuffer()));
  } catch (e) { done(502, (e as Error).message); }
}
