"use client";
import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import Hls from "hls.js";
import { ArrowLeft, Copy, MonitorPlay, SkipForward, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import { deviceId, get, getHistory, proxied, reelm, saveProgress, WS, type Play } from "@/lib/core";
import { useSession } from "@/lib/session";
import { Avatar } from "@/components/app";

type Source = { src: string; hls: boolean; label: string };
type Sub = { url: string; label: string; lang: string; default?: boolean };
type Member = { id: string; name: string; avatar: string };
type Device = { deviceId: string; deviceName: string; kind: string };

function Player() {
  const q = useSearchParams(), router = useRouter(), { s } = useSession();
  const room = q.get("room") ?? "";
  const p = useMemo(() => { try { return JSON.parse(q.get("p") ?? "") as Play; } catch { return null; } }, [q]);
  const t0 = Number(q.get("t")) || 0;
  const video = useRef<HTMLVideoElement>(null);
  const hls = useRef<Hls | null>(null);
  const wsRef = useRef<WebSocket | null>(null), hub = useRef<WebSocket | null>(null);
  const applying = useRef(false), youId = useRef("");
  const [sources, setSources] = useState<Source[]>([]), [si, setSi] = useState(0);
  const [subs, setSubs] = useState<Sub[]>([]), [levels, setLevels] = useState<{ h: number }[]>([]), [level, setLevel] = useState(-1);
  const [err, setErr] = useState(""), [members, setMembers] = useState<Member[]>([]), [devices, setDevices] = useState<Device[]>([]);
  const [eps, setEps] = useState<any[]>([]), [skips, setSkips] = useState<{ s: number; e: number; type: string }[]>([]), [now, setNow] = useState(0);
  const pid = s.profile?.id ?? "guest", pkey = p ? JSON.stringify(p) : "";

  // resolve the stream
  useEffect(() => {
    if (!p) return;
    let live = true;
    setErr(""); setSources([]); setSubs([]); setSkips([]);
    (async () => {
      if (p.kind === "live") return [[{ src: proxied(p.url!, "", true), hls: true, label: "Live" }], []] as const;
      if (p.kind === "anime") {
        const r = await get(`anime/stream?ep=${p.epId}&lang=${p.lang ?? "sub"}`);
        if (r.malId) get(`aniskip?mal=${r.malId}&ep=${p.ep}`).then((d) => live && setSkips((d.results ?? []).map((x: any) => ({ s: x.interval.startTime, e: x.interval.endTime, type: x.skipType })))).catch(() => {});
        return [[{ src: proxied(r.master, r.referer), hls: true, label: "Auto" }], r.subtitles.map((x: Sub) => ({ ...x, url: proxied(x.url, r.referer) }))] as const;
      }
      const list = await get<any[]>(`movies/stream?id=${encodeURIComponent(p.id)}&type=${p.type}&title=${encodeURIComponent(p.title)}&year=${p.year ?? ""}&s=${p.season ?? 0}&e=${p.episode ?? 0}`);
      if (!list.length) throw new Error("No streams found for this title.");
      return [list.map((x) => ({ src: x.url, hls: false, label: x.label })), list[0].subtitles.map((x: Sub) => ({ ...x, url: `/api/sub?u=${encodeURIComponent(x.url)}` }))] as const;
    })().then(([a, b]) => { if (live) { setSources([...a]); setSubs([...b]); setSi(0); } }).catch((e) => live && setErr(e.message));
    if (p.kind === "anime") get(`anime/episodes?slug=${p.id}`).then((d) => live && setEps(d)).catch(() => {});
    return () => { live = false; };
  }, [pkey]); // eslint-disable-line react-hooks/exhaustive-deps

  // attach the source
  const src = sources[si];
  useEffect(() => {
    const v = video.current;
    if (!v || !src) return;
    const resume = v.currentTime || t0 || getHistory(pid)[p ? (p.kind === "anime" ? `anime:${p.id}:${p.ep}` : `movie:${p.id}:${p.season ?? 0}:${p.episode ?? 0}`) : ""]?.position || 0;
    hls.current?.destroy(); hls.current = null;
    const start = () => { if (resume > 5 && p?.kind !== "live") v.currentTime = resume; v.play().catch(() => {}); };
    if (src.hls && Hls.isSupported()) {
      const h = new Hls({ startPosition: resume > 5 && p?.kind !== "live" ? resume : -1 });
      hls.current = h;
      h.loadSource(src.src); h.attachMedia(v);
      h.on(Hls.Events.MANIFEST_PARSED, (_, d) => { setLevels(d.levels.map((l) => ({ h: l.height }))); setLevel(-1); v.play().catch(() => {}); });
      h.on(Hls.Events.ERROR, (_, d) => { if (d.fatal) setErr(`Playback failed (${d.details}).`); });
    } else { v.src = src.src; v.addEventListener("loadedmetadata", start, { once: true }); }
    return () => { hls.current?.destroy(); hls.current = null; };
  }, [src]); // eslint-disable-line react-hooks/exhaustive-deps

  // progress: every 10 s and on leave
  const save = useCallback(() => { const v = video.current; if (p && v && v.currentTime > 5 && isFinite(v.duration)) saveProgress(pid, s.token, p, v.currentTime, v.duration); }, [p, pid, s.token]);
  useEffect(() => { const i = setInterval(save, 10000); return () => { clearInterval(i); save(); }; }, [save]);

  // watch together
  const sendRoom = (m: object) => wsRef.current?.readyState === 1 && wsRef.current.send(JSON.stringify(m));
  const apply = (fn: () => void) => { applying.current = true; fn(); setTimeout(() => (applying.current = false), 400); };
  useEffect(() => {
    if (!room) return;
    const ws = new WebSocket(`${WS}/v1/rooms/${room}`);
    wsRef.current = ws;
    ws.onopen = () => ws.send(JSON.stringify({ type: "join", name: s.profile?.name ?? "Guest", avatar: s.profile?.avatar ?? "grinning_face" }));
    ws.onmessage = (ev) => {
      const m = JSON.parse(ev.data), v = video.current;
      if (m.type === "state") {
        youId.current = m.you; setMembers(m.members);
        const mine = pkey, theirs = m.payload && JSON.stringify(m.payload);
        if (theirs && theirs !== mine) return router.replace(`/watch?p=${encodeURIComponent(theirs)}&room=${room}`);
        if (v) apply(() => { v.currentTime = m.position + (m.playing ? (Date.now() - m.at) / 1000 : 0); if (m.playing) v.play().catch(() => {}); });
      } else if (m.type === "members") setMembers(m.members);
      else if (m.type === "load") { if (m.by !== youId.current) router.replace(`/watch?p=${encodeURIComponent(JSON.stringify(m.payload))}&room=${room}`); }
      else if (v && m.by !== youId.current) apply(() => {
        if (m.type === "seek") v.currentTime = m.position;
        if (m.type === "play") { v.currentTime = m.position + (Date.now() - m.at) / 1000; v.play().catch(() => {}); }
        if (m.type === "pause") { v.pause(); v.currentTime = m.position; }
      });
    };
    return () => ws.close();
  }, [room]); // eslint-disable-line react-hooks/exhaustive-deps
  const startRoom = async () => { const { code } = await reelm("/v1/rooms", { json: { payload: p } }); router.replace(`/watch?p=${encodeURIComponent(pkey)}&room=${code}`); };
  const pos = () => video.current?.currentTime ?? 0;

  // continue on TV: this browser is a "phone" on the account hub
  useEffect(() => {
    if (!s.token) return;
    const ws = new WebSocket(`${WS}/v1/hub?token=${s.token}`);
    hub.current = ws;
    ws.onopen = () => ws.send(JSON.stringify({ type: "hello", deviceId: deviceId(), deviceName: "Reelm Web", kind: "phone" }));
    ws.onmessage = (ev) => { const m = JSON.parse(ev.data); if (m.type === "devices") setDevices(m.devices); };
    return () => ws.close();
  }, [s.token]);
  const handoff = (to: string) => { hub.current?.send(JSON.stringify({ type: "handoff", to, profileId: pid, playback: { payload: p, position: pos() } })); video.current?.pause(); };

  // switch episode (and tell the room)
  const go = (np: Play) => {
    if (room) sendRoom({ type: "load", payload: np, position: 0 });
    router.replace(`/watch?p=${encodeURIComponent(JSON.stringify(np))}${room ? `&room=${room}` : ""}`);
  };
  const next = p?.kind === "anime" ? eps.find((e) => e.number === (p.ep ?? 0) + 1) : null;
  const skip = skips.find((k) => now >= k.s && now < k.e - 1);

  // keyboard: f fullscreen, m mute, j/l seek 10 s (space and arrows are native when the video is focused)
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      const v = video.current, t = e.target as HTMLElement;
      if (!v || /INPUT|SELECT|TEXTAREA/.test(t.tagName) || e.metaKey || e.ctrlKey) return;
      if (e.key === "f") document.fullscreenElement ? document.exitFullscreen() : v.parentElement?.requestFullscreen();
      else if (e.key === "m") v.muted = !v.muted;
      else if (e.key === "j" || e.key === "ArrowLeft") v.currentTime -= 10;
      else if (e.key === "l" || e.key === "ArrowRight") v.currentTime += 10;
      else if (e.key === " " || e.key === "k") { if (t.tagName !== "BUTTON" && t !== v) { v.paused ? v.play() : v.pause(); e.preventDefault(); } }
    };
    window.addEventListener("keydown", on);
    return () => window.removeEventListener("keydown", on);
  }, []);

  if (!p) return <p className="p-10">Nothing to play. <Link href="/" className="text-primary">Go home</Link></p>;
  const back = p.kind === "anime" ? `/anime/${p.id}` : p.kind === "live" ? "/live" : `/title/${encodeURIComponent(p.id)}?type=${p.type}`;
  const sel = "h-9 rounded-full border border-input bg-card px-3 text-sm";
  return (
    <main className="mx-auto flex min-h-dvh max-w-6xl flex-col gap-3 px-3 py-3">
      <div className="flex items-center gap-3">
        <Link href={back} aria-label="Back" className="beam rounded-full p-2"><ArrowLeft /></Link>
        <h1 className="line-clamp-1 flex-1 font-display text-lg font-semibold">{p.title}{p.kind === "anime" && ` · Episode ${p.ep}`}{p.kind === "movie" && p.season ? ` · S${p.season} E${p.episode}` : ""}</h1>
      </div>
      <div className="relative overflow-hidden rounded-2xl bg-black">
        <video ref={video} controls autoPlay playsInline {...({ referrerPolicy: "no-referrer" } as object)} crossOrigin={src?.hls ? "anonymous" : undefined} className="aspect-video w-full"
          onTimeUpdate={(e) => setNow(e.currentTarget.currentTime)}
          onPlay={() => !applying.current && sendRoom({ type: "play", position: pos() })} onPause={() => { save(); !applying.current && sendRoom({ type: "pause", position: pos() }); }}
          onSeeked={() => !applying.current && sendRoom({ type: "seek", position: pos() })}
          onEnded={() => next && go({ ...p, ep: next.number, epId: next.id })}>
          {subs.map((t, i) => <track key={t.url} kind="subtitles" src={t.url} label={t.label} srcLang={t.lang || "en"} default={i === 0 || t.default} />)}
        </video>
        {skip && <Button className="absolute bottom-16 right-4 rounded-full" onClick={() => { video.current!.currentTime = skip.e; }}><SkipForward />Skip {skip.type.includes("op") ? "intro" : skip.type === "ed" ? "outro" : "recap"}</Button>}
        {err && <p role="alert" className="absolute inset-x-0 top-0 bg-destructive/90 p-3 text-center text-sm text-white">{err}</p>}
        {!sources.length && !err && <div className="absolute inset-0 grid place-items-center text-muted-foreground">Finding a stream…</div>}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {sources.length > 1 && <select aria-label="Quality" className={sel} value={si} onChange={(e) => setSi(+e.target.value)}>{sources.map((x, i) => <option key={i} value={i}>{x.label}</option>)}</select>}
        {levels.length > 1 && <select aria-label="Quality" className={sel} value={level} onChange={(e) => { setLevel(+e.target.value); if (hls.current) hls.current.currentLevel = +e.target.value; }}>
          <option value={-1}>Auto</option>{levels.map((l, i) => <option key={i} value={i}>{l.h ? `${l.h}p` : `Level ${i + 1}`}</option>)}</select>}
        {p.kind === "anime" && <select aria-label="Audio" className={sel} value={p.lang ?? "sub"} onChange={(e) => go({ ...p, lang: e.target.value })}><option value="sub">Sub</option><option value="dub">Dub</option></select>}
        {p.kind === "anime" && eps.length > 0 && <select aria-label="Episode" className={sel} value={p.ep} onChange={(e) => { const x = eps.find((z) => z.number === +e.target.value); go({ ...p, ep: x.number, epId: x.id }); }}>{eps.map((e) => <option key={e.id} value={e.number}>Episode {e.number}</option>)}</select>}
        {next && <Button variant="secondary" className="rounded-full" onClick={() => go({ ...p, ep: next.number, epId: next.id })}><SkipForward />Next episode</Button>}
        <div className="flex-1" />
        {p.kind !== "live" && (room
          ? <Button variant="secondary" className="rounded-full" onClick={() => navigator.clipboard?.writeText(`${location.origin}/watch?p=${encodeURIComponent(pkey)}&room=${room}`)}><Copy />Room {room}</Button>
          : <Button variant="secondary" className="rounded-full" onClick={startRoom}><Users />Watch together</Button>)}
        {room && <div className="flex -space-x-2" aria-label="In the room">{members.map((m) => <span key={m.id} title={m.name}><Avatar id={m.avatar} size={32} /></span>)}</div>}
        {devices.filter((d) => d.kind === "tv").map((d) => <Button key={d.deviceId} variant="outline" className="rounded-full" onClick={() => handoff(d.deviceId)}><MonitorPlay />Continue on {d.deviceName}</Button>)}
      </div>
      <p className="text-xs text-muted-foreground">Keys: f fullscreen, m mute, j/l or arrows seek 10 s, k or space play/pause.{!s.token && " Sign in to continue on a TV."}</p>
    </main>
  );
}
export default function Page() { return <Suspense><Player /></Suspense>; }
