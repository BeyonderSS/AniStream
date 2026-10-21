"use client";
import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import Hls from "hls.js";
import {
  ArrowLeft, Captions, Check, Copy, Loader2, Maximize, Minimize, MonitorPlay, Pause, Play, RotateCcw, RotateCw, Settings, SkipForward, Users, Volume1, Volume2, VolumeX, X,
} from "lucide-react";
import { Avatar, pill } from "@/components/app";
import { ReelMark } from "@/components/reel";
import { deviceId, get, getHistory, playKey, proxied, reelm, saveProgress, toPlay, WS, type Play as P } from "@/lib/core";
import { useApi, useSession } from "@/lib/session";
import { cn } from "@/lib/utils";

type Source = { src: string; hls: boolean; label: string };
type Sub = { url: string; label: string; lang: string; default?: boolean };
type Member = { id: string; name: string; avatar: string };
type Device = { deviceId: string; deviceName: string; kind: string };

const parse = (v: string | null) => { try { return toPlay(JSON.parse(v ?? "")); } catch { return null; } };
const href = (p: P, extra = "") => `/watch?p=${encodeURIComponent(JSON.stringify(p))}${extra}`;
const clock = (t: number) => {
  if (!isFinite(t) || t < 0) t = 0;
  const h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), s = Math.floor(t % 60);
  return `${h ? `${h}:${String(m).padStart(2, "0")}` : m}:${String(s).padStart(2, "0")}`;
};
const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];
/** Raw errors from the stream hosts are not for people; say what happened and what to try. */
const friendly = (m: string) =>
  /isn't available|not available|no stream|no streams|release/i.test(m) ? { t: "This one isn't streaming yet", b: "No source has it right now. Try again later, or pick another episode." }
  : /network|fetch|manifest|frag|level|load/i.test(m) ? { t: "The stream stopped loading", b: "The video host didn't answer. Retry, or switch quality in settings." }
  : /4\d\d|5\d\d|440/.test(m) ? { t: "The video host turned us away", b: "This usually clears up in a minute. Retry to ask again." }
  : { t: "This video didn't start", b: "Something went wrong while starting playback. Retry, or go back and pick it again." };

function Player() {
  const q = useSearchParams(), router = useRouter(), { s } = useSession();
  const room = (q.get("room") ?? "").toUpperCase();
  const p = useMemo(() => parse(q.get("p")), [q]);
  const t0 = Number(q.get("t")) || 0, f0 = Number(q.get("f")) || 0;
  const video = useRef<HTMLVideoElement>(null), box = useRef<HTMLDivElement>(null);
  const hls = useRef<Hls | null>(null);
  const wsRef = useRef<WebSocket | null>(null), hub = useRef<WebSocket | null>(null);
  const applying = useRef(false), youId = useRef(""), hideT = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [sources, setSources] = useState<Source[]>([]), [si, setSi] = useState(0);
  const [subs, setSubs] = useState<Sub[]>([]), [subIdx, setSubIdx] = useState(-1);
  const [levels, setLevels] = useState<number[]>([]), [level, setLevel] = useState(-1);
  const [err, setErr] = useState(""), [nonce, setNonce] = useState(0);
  const [members, setMembers] = useState<Member[]>([]), [devices, setDevices] = useState<Device[]>([]);
  const [eps, setEps] = useState<any[]>([]), [skips, setSkips] = useState<{ s: number; e: number; type: string }[]>([]);
  const [playing, setPlaying] = useState(false), [cur, setCur] = useState(0), [dur, setDur] = useState(0), [buf, setBuf] = useState(0), [waiting, setWaiting] = useState(true);
  const [vol, setVol] = useState(1), [muted, setMuted] = useState(false), [rate, setRate] = useState(1), [fs, setFs] = useState(false);
  const [show, setShow] = useState(true), [menu, setMenu] = useState<"settings" | "subs" | null>(null), [drawer, setDrawer] = useState(false);
  const [hover, setHover] = useState<{ x: number; t: number } | null>(null), [copied, setCopied] = useState(false), [roomErr, setRoomErr] = useState("");
  const pid = s.profile?.id ?? "guest", pkey = p ? JSON.stringify(p) : "";
  const extra = room ? `&room=${room}` : "";

  // resolve the stream (re-run by Retry)
  useEffect(() => {
    if (!p) return;
    let live = true;
    setErr(""); setSources([]); setSubs([]); setSkips([]); setWaiting(true); setSi(0); setLevels([]);
    (async () => {
      if (p.kind === "live") return [[{ src: proxied(p.url!, "", true), hls: true, label: "Live" }], []] as const;
      if (p.kind === "anime") {
        if (!p.epId) {
          // synced payloads from the apps carry the episode number only: find its id (and the right slug, by title if needed)
          let slug = p.id, list: any[] = await get<any[]>(`anime/episodes?slug=${slug}`).catch(() => [] as any[]);
          if (!list.length && p.title) {
            slug = (await get<any[]>(`anime/search?q=${encodeURIComponent(p.title)}`).catch(() => []))[0]?.slug ?? "";
            if (slug) list = await get<any[]>(`anime/episodes?slug=${slug}`).catch(() => []);
          }
          const e = list.find((x) => x.number === p.ep) ?? list[0];
          if (!e) throw new Error("This isn't available to stream.");
          if (live) router.replace(href({ ...p, id: slug, ep: e.number, epId: e.id }, `${extra}${f0 ? `&f=${f0}` : ""}`));
          return null;
        }
        const r = await get(`anime/stream?ep=${p.epId}&lang=${p.lang ?? "sub"}`);
        if (r.malId) get(`aniskip?mal=${r.malId}&ep=${p.ep}`).then((d) => live && setSkips((d.results ?? []).map((x: any) => ({ s: x.interval.startTime, e: x.interval.endTime, type: x.skipType })))).catch(() => {});
        return [[{ src: proxied(r.master, r.referer), hls: true, label: "Auto" }], r.subtitles.map((x: Sub) => ({ ...x, url: proxied(x.url, r.referer) }))] as const;
      }
      const list = await get<any[]>(`movies/stream?id=${encodeURIComponent(p.id)}&type=${p.type}&title=${encodeURIComponent(p.title)}&year=${p.year ?? ""}&s=${p.season ?? 0}&e=${p.episode ?? 0}`);
      if (!list.length) throw new Error("No streams found for this title.");
      return [list.map((x) => ({ src: x.url, hls: /\.m3u8(\?|$)/.test(x.url), label: x.label })), list[0].subtitles.map((x: Sub) => ({ ...x, url: `/api/sub?u=${encodeURIComponent(x.url)}` }))] as const;
    })().then((r) => {
      if (!live || !r) return;
      const [a, b] = r;
      setSources([...a]); setSubs([...b]);
      const d = b.findIndex((x: Sub) => x.default);
      setSubIdx(b.length ? (d >= 0 ? d : 0) : -1);
    }).catch((e) => live && setErr(e.message));
    if (p.kind === "anime") get(`anime/episodes?slug=${p.id}`).then((d) => live && setEps(d)).catch(() => {});
    return () => { live = false; };
  }, [pkey, nonce]); // eslint-disable-line react-hooks/exhaustive-deps

  // attach the source
  const src = sources[si];
  useEffect(() => {
    const v = video.current;
    if (!v || !src) return;
    const keep = v.currentTime;
    hls.current?.destroy(); hls.current = null;
    const onMeta = () => {
      if (p?.kind === "live") return;
      const hist = getHistory(pid)[p ? playKey(p) : ""];
      const at = keep || t0 || (f0 && v.duration ? f0 * v.duration : 0) || (hist && hist.duration && hist.position / hist.duration < 0.95 ? hist.position : 0);
      if (at > 5) v.currentTime = at;
    };
    v.addEventListener("loadedmetadata", onMeta, { once: true });
    const fail = (m: string) => (si + 1 < sources.length ? setSi(si + 1) : setErr(m));
    if (src.hls && Hls.isSupported()) {
      const h = new Hls();
      hls.current = h;
      let recovered = false;
      h.loadSource(src.src); h.attachMedia(v);
      h.on(Hls.Events.MANIFEST_PARSED, (_, d) => { setLevels(d.levels.map((l) => l.height)); setLevel(-1); v.play().catch(() => {}); });
      h.on(Hls.Events.ERROR, (_, d) => {
        if (!d.fatal) return;
        if (d.type === Hls.ErrorTypes.MEDIA_ERROR && !recovered) { recovered = true; h.recoverMediaError(); return; }
        fail(`Playback failed (${d.details}).`);
      });
    } else { v.src = src.src; v.play().catch(() => {}); }
    const onErr = () => !hls.current && fail("The video failed to load.");
    v.addEventListener("error", onErr);
    return () => { v.removeEventListener("loadedmetadata", onMeta); v.removeEventListener("error", onErr); hls.current?.destroy(); hls.current = null; };
  }, [src]); // eslint-disable-line react-hooks/exhaustive-deps

  // subtitles: exactly one track showing
  useEffect(() => {
    const t = video.current?.textTracks;
    if (t) for (let i = 0; i < t.length; i++) t[i].mode = i === subIdx ? "showing" : "disabled";
  }, [subIdx, subs]);

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
    ws.onerror = () => setRoomErr("Couldn't join the room. Check the code and try again.");
    ws.onmessage = (ev) => {
      const m = JSON.parse(ev.data), v = video.current;
      if (m.type === "state") {
        youId.current = m.you; setMembers(m.members);
        const theirs = toPlay(m.payload);
        if (theirs && JSON.stringify(theirs) !== pkey) return router.replace(href(theirs, `&room=${room}`));
        if (v) apply(() => { v.currentTime = m.position + (m.playing ? (Date.now() - m.at) / 1000 : 0); if (m.playing) v.play().catch(() => {}); });
      } else if (m.type === "members") setMembers(m.members);
      else if (m.type === "load") { const np = toPlay(m.payload); if (np && m.by !== youId.current) router.replace(href(np, `&room=${room}`)); }
      else if (v && m.by !== youId.current) apply(() => {
        if (m.type === "seek") v.currentTime = m.position;
        if (m.type === "play") { v.currentTime = m.position + (Date.now() - m.at) / 1000; v.play().catch(() => {}); }
        if (m.type === "pause") { v.pause(); v.currentTime = m.position; }
      });
    };
    return () => ws.close();
  }, [room]); // eslint-disable-line react-hooks/exhaustive-deps
  const startRoom = async () => {
    setRoomErr("");
    try { const { code } = await reelm("/v1/rooms", { json: { payload: p } }); router.replace(href(p!, `&room=${code}`)); }
    catch (e) { setRoomErr((e as Error).message); }
  };
  const roomLink = () => `${location.origin}/watch?room=${room}${p ? `&p=${encodeURIComponent(pkey)}` : ""}`;
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

  // next episode
  const series = useApi(p?.kind === "movie" && p.type === "series" ? `movies/detail?id=${encodeURIComponent(p.id)}&type=series` : null).data;
  const next: P | null = useMemo(() => {
    if (!p) return null;
    if (p.kind === "anime") { const n = eps.find((e) => e.number === (p.ep ?? 0) + 1); return n ? { ...p, ep: n.number, epId: n.id } : null; }
    if (p.kind === "movie" && series?.seasons) {
      const se = series.seasons.find((x: any) => x.season === p.season);
      if (se && (p.episode ?? 0) < se.episodes) return { ...p, episode: (p.episode ?? 0) + 1 };
      const ns = series.seasons.find((x: any) => x.season === (p.season ?? 0) + 1);
      if (ns) return { ...p, season: ns.season, episode: 1 };
    }
    return null;
  }, [p, eps, series]);
  const go = (np: P) => { save(); if (room) sendRoom({ type: "load", payload: np, position: 0 }); router.replace(href(np, extra)); };
  const skip = skips.find((k) => cur >= k.s && cur < k.e - 1);

  // controls visibility
  const poke = useCallback(() => {
    setShow(true); clearTimeout(hideT.current);
    hideT.current = setTimeout(() => { if (!video.current?.paused) setShow(false); }, 3000);
  }, []);
  const visible = show || !playing || !!menu || drawer || !!err;
  const toggle = () => { const v = video.current; if (!v) return; if (v.paused) v.play().catch(() => {}); else v.pause(); };
  const seekBy = (d: number) => { const v = video.current; if (v) v.currentTime = Math.max(0, Math.min((v.duration || 0) - 0.5, v.currentTime + d)); poke(); };
  const fullscreen = () => (document.fullscreenElement ? document.exitFullscreen() : box.current?.requestFullscreen())?.catch?.(() => {});
  useEffect(() => { const on = () => setFs(!!document.fullscreenElement); document.addEventListener("fullscreenchange", on); return () => document.removeEventListener("fullscreenchange", on); }, []);
  const setVolume = (x: number) => { const v = video.current; if (!v) return; v.volume = x; v.muted = x === 0; };

  // keyboard shortcuts
  useEffect(() => {
    const on = (e: KeyboardEvent) => {
      const v = video.current, t = e.target as HTMLElement;
      if (!v || /INPUT|SELECT|TEXTAREA/.test(t.tagName) && (t as HTMLInputElement).type !== "range" || e.metaKey || e.ctrlKey || e.altKey) return;
      const k = e.key.toLowerCase();
      if (k === "escape") { setMenu(null); setDrawer(false); return; }
      if ((k === " " && t.tagName !== "BUTTON") || k === "k") { toggle(); e.preventDefault(); }
      else if (k === "f") fullscreen();
      else if (k === "m") v.muted = !v.muted;
      else if (k === "j" || k === "arrowleft") { seekBy(-10); e.preventDefault(); }
      else if (k === "l" || k === "arrowright") { seekBy(10); e.preventDefault(); }
      else if (k === "arrowup") { setVolume(Math.min(1, v.volume + 0.1)); e.preventDefault(); }
      else if (k === "arrowdown") { setVolume(Math.max(0, v.volume - 0.1)); e.preventDefault(); }
      else if (k === "c" && subs.length) setSubIdx((i) => (i >= 0 ? -1 : 0));
      else if (k === "n" && next) go(next);
      else if (k === "s" && skip) v.currentTime = skip.e;
      else return;
      poke();
    };
    addEventListener("keydown", on);
    return () => removeEventListener("keydown", on);
  });

  if (!p && !room) return (
    <main className="grid min-h-dvh place-items-center p-6">
      <div className="flex max-w-sm flex-col items-center gap-3 text-center"><ReelMark className="size-12 opacity-60" /><h1 className="font-display text-2xl font-semibold">Nothing to play</h1>
        <p className="text-foreground/60">This link doesn't point to a title. Pick something from home.</p><Link href="/" className={pill.primary}>Go home</Link></div>
    </main>
  );

  const back = !p ? "/" : p.kind === "anime" ? `/anime/${p.id}` : p.kind === "live" ? "/live" : `/title/${encodeURIComponent(p.id)}?type=${p.type}`;
  const sub = !p ? "Joining room" : p.kind === "anime" ? `Episode ${p.ep}${p.lang === "dub" ? ", dubbed" : ""}` : p.kind === "live" ? "Live" : p.season ? `Season ${p.season}, episode ${p.episode}` : "";
  const fe = err && friendly(err);
  const isLive = p?.kind === "live" || !isFinite(dur);
  const VolIcon = muted || vol === 0 ? VolumeX : vol < 0.5 ? Volume1 : Volume2;
  const ctl = "beam grid size-11 shrink-0 place-items-center rounded-full text-foreground hover:bg-white/12 [&_svg]:size-5";
  const radio = (on: boolean) => cn("beam flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-sm hover:bg-white/8", on && "text-primary");

  return (
    <main ref={box} onPointerMove={(e) => e.pointerType === "mouse" && poke()} onPointerLeave={() => playing && !menu && setShow(false)}
      className={cn("relative h-dvh w-full overflow-hidden bg-black select-none", !visible && "cursor-none")}>
      <video ref={video} autoPlay playsInline {...({ referrerPolicy: "no-referrer" } as object)} crossOrigin={src?.hls ? "anonymous" : undefined} className="absolute inset-0 size-full object-contain"
        onPlay={() => { setPlaying(true); poke(); if (!applying.current) sendRoom({ type: "play", position: pos() }); }}
        onPause={() => { setPlaying(false); save(); if (!applying.current) sendRoom({ type: "pause", position: pos() }); }}
        onSeeked={() => !applying.current && sendRoom({ type: "seek", position: pos() })}
        onTimeUpdate={(e) => { const v = e.currentTarget; setCur(v.currentTime); if (v.buffered.length) setBuf(v.buffered.end(v.buffered.length - 1)); }}
        onDurationChange={(e) => setDur(e.currentTarget.duration)} onWaiting={() => setWaiting(true)} onPlaying={() => setWaiting(false)} onCanPlay={() => setWaiting(false)}
        onVolumeChange={(e) => { setVol(e.currentTarget.volume); setMuted(e.currentTarget.muted); }} onRateChange={(e) => setRate(e.currentTarget.playbackRate)}
        onEnded={() => { save(); if (next) go(next); }}>
        {subs.map((t) => <track key={t.url} kind="subtitles" src={t.url} label={t.label} srcLang={t.lang || "en"} />)}
      </video>

      {/* tap / click surface */}
      <div className="absolute inset-0" onDoubleClick={fullscreen}
        onPointerUp={(e) => { if (menu) return setMenu(null); if (e.pointerType === "mouse") toggle(); else if (visible && playing) setShow(false); else poke(); }} />

      {/* loading and errors */}
      {!err && (waiting || !sources.length) && (
        <div className="pointer-events-none absolute inset-0 grid place-items-center">
          <div className="flex flex-col items-center gap-4">
            <ReelMark className="size-14 animate-spin [animation-duration:1.6s]" />
            {!sources.length && <p className="text-sm text-foreground/72">{p ? "Finding a stream" : "Joining the room"}</p>}
          </div>
        </div>
      )}
      {fe && (
        <div className="absolute inset-0 z-20 grid place-items-center bg-dusk/80 p-6 backdrop-blur-md">
          <div role="alert" className="page-in flex max-w-sm flex-col items-center gap-3 rounded-[24px] bg-haze p-7 text-center shadow-[0_24px_60px_rgb(10_12_18/0.6)]">
            <ReelMark className="size-10 opacity-70" />
            <h2 className="font-display text-xl font-semibold">{fe.t}</h2>
            <p className="text-sm text-foreground/72">{fe.b}</p>
            <div className="mt-2 flex gap-2">
              <button className={pill.primary} onClick={() => { setErr(""); setNonce(nonce + 1); }}><RotateCcw />Retry</button>
              <Link href={back} className={pill.secondary}>Back</Link>
            </div>
            {next && <button className="mt-1 text-sm text-primary hover:underline" onClick={() => go(next)}>Play the next episode instead</button>}
          </div>
        </div>
      )}

      {/* big play */}
      {!playing && !waiting && !err && sources.length > 0 && (
        <button aria-label="Play" onClick={toggle} className="beam absolute top-1/2 left-1/2 z-10 grid size-20 -translate-1/2 place-items-center rounded-full bg-primary text-primary-foreground shadow-[0_0_60px_rgb(242_180_90/0.45)] transition-transform hover:scale-105">
          <Play className="size-8 translate-x-0.5 fill-current" />
        </button>
      )}

      {/* skip intro */}
      {skip && !err && (
        <button onClick={() => { video.current!.currentTime = skip.e; }} className={cn(pill.secondary, "absolute right-4 z-10 bg-dusk/80 ring-1 ring-white/15 backdrop-blur transition-[bottom] md:right-8", visible ? "bottom-32" : "bottom-8")}>
          <SkipForward />Skip {skip.type.includes("op") ? "intro" : skip.type === "ed" ? "outro" : "recap"}
        </button>
      )}

      {/* top bar */}
      <div className={cn("absolute inset-x-0 top-0 z-10 flex items-center gap-2 bg-gradient-to-b from-black/80 to-transparent px-3 pt-[max(env(safe-area-inset-top),12px)] pb-10 transition-opacity duration-300 md:px-6", visible ? "opacity-100" : "pointer-events-none opacity-0")}>
        <Link href={back} aria-label="Back" className={ctl}><ArrowLeft /></Link>
        <div className="min-w-0 flex-1">
          <h1 className="truncate font-display text-lg font-semibold md:text-xl">{p?.title ?? "Watch together"}</h1>
          {sub && <p className="truncate text-xs text-foreground/72">{sub}</p>}
        </div>
        {devices.filter((d) => d.kind === "tv").map((d) => <button key={d.deviceId} className={cn(pill.ghost, "h-10")} onClick={() => handoff(d.deviceId)}><MonitorPlay className="size-4" /><span className="hidden sm:inline">Continue on {d.deviceName}</span></button>)}
        {p?.kind !== "live" && (
          <button onClick={() => setDrawer(true)} className={cn(pill.ghost, "h-10 px-4", room && "text-primary")} aria-label="Watch together">
            <Users className="size-4" /><span className="hidden sm:inline">{room ? `Room ${room}` : "Watch together"}</span>
            {room && members.length > 0 && <span className="rounded-full bg-primary/20 px-1.5 text-xs">{members.length}</span>}
          </button>
        )}
      </div>

      {/* bottom controls */}
      <div className={cn("absolute inset-x-0 bottom-0 z-10 bg-gradient-to-t from-black/85 via-black/40 to-transparent px-3 pt-16 pb-[max(env(safe-area-inset-bottom),10px)] transition-opacity duration-300 md:px-6", visible ? "opacity-100" : "pointer-events-none opacity-0")}>
        {!isLive && (
          <div className="group/scrub relative mx-1 h-6" onPointerMove={(e) => { const r = e.currentTarget.getBoundingClientRect(); const x = Math.max(0, Math.min(r.width, e.clientX - r.left)); setHover({ x, t: (x / r.width) * dur }); }} onPointerLeave={() => setHover(null)}>
            <div className="pointer-events-none absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 overflow-hidden rounded-full bg-white/20 transition-[height] group-hover/scrub:h-1.5">
              <div className="absolute inset-y-0 left-0 bg-white/30" style={{ width: `${dur ? (buf / dur) * 100 : 0}%` }} />
              <div className="absolute inset-y-0 left-0 bg-primary" style={{ width: `${dur ? (cur / dur) * 100 : 0}%` }} />
              {skips.map((k) => dur > 0 && <div key={k.s} className="absolute inset-y-0 bg-signal/50" style={{ left: `${(k.s / dur) * 100}%`, width: `${((k.e - k.s) / dur) * 100}%` }} />)}
            </div>
            <input type="range" aria-label="Seek" min={0} max={dur || 0} step={0.1} value={Math.min(cur, dur || 0)} aria-valuetext={`${clock(cur)} of ${clock(dur)}`}
              onChange={(e) => { const v = video.current; if (v) v.currentTime = +e.target.value; setCur(+e.target.value); poke(); }} className="range absolute inset-0 w-full" />
            {hover && dur > 0 && <span className="pointer-events-none absolute -top-8 -translate-x-1/2 rounded-full bg-dusk/90 px-2.5 py-1 text-xs tabular-nums" style={{ left: hover.x }}>{clock(hover.t)}</span>}
          </div>
        )}
        <div className="flex items-center gap-0.5 md:gap-1">
          <button aria-label={playing ? "Pause" : "Play"} onClick={toggle} className={ctl}>{playing ? <Pause className="fill-current" /> : <Play className="fill-current" />}</button>
          {!isLive && <button aria-label="Back 10 seconds" onClick={() => seekBy(-10)} className={ctl}><RotateCcw /></button>}
          {!isLive && <button aria-label="Forward 10 seconds" onClick={() => seekBy(10)} className={ctl}><RotateCw /></button>}
          <div className="group/vol flex items-center">
            <button aria-label={muted ? "Unmute" : "Mute"} onClick={() => { const v = video.current; if (v) { v.muted = !v.muted; if (!v.muted && v.volume === 0) v.volume = 0.5; } }} className={ctl}><VolIcon /></button>
            <input type="range" aria-label="Volume" min={0} max={1} step={0.05} value={muted ? 0 : vol} onChange={(e) => setVolume(+e.target.value)}
              className="range hidden h-1 w-0 rounded-full opacity-0 transition-[width,opacity] duration-200 group-hover/vol:w-24 group-hover/vol:opacity-100 focus-visible:w-24 focus-visible:opacity-100 md:block" style={{ background: `linear-gradient(to right, #f2b45a ${(muted ? 0 : vol) * 100}%, rgb(255 255 255 / 0.25) 0)` }} />
          </div>
          <span className="ml-2 text-sm text-foreground/80 tabular-nums">{isLive ? <span className="inline-flex items-center gap-1.5 text-signal"><span className="size-2 rounded-full bg-signal" />Live</span> : `${clock(cur)} / ${clock(dur)}`}</span>
          <div className="flex-1" />
          {next && <button onClick={() => go(next)} className={cn(pill.ghost, "h-10 px-3")}><SkipForward className="size-4" /><span className="hidden sm:inline">Next episode</span></button>}
          {subs.length > 0 && <button aria-label="Subtitles" aria-expanded={menu === "subs"} onClick={() => setMenu(menu === "subs" ? null : "subs")} className={cn(ctl, subIdx >= 0 && "text-primary")}><Captions /></button>}
          <button aria-label="Settings" aria-expanded={menu === "settings"} onClick={() => setMenu(menu === "settings" ? null : "settings")} className={ctl}><Settings className={cn("transition-transform duration-300", menu === "settings" && "rotate-45")} /></button>
          <button aria-label={fs ? "Exit full screen" : "Full screen"} onClick={fullscreen} className={ctl}>{fs ? <Minimize /> : <Maximize />}</button>
        </div>
      </div>

      {/* menus */}
      {menu && (
        <div role="menu" className="page-in absolute right-3 bottom-24 z-30 max-h-[60vh] w-72 overflow-y-auto rounded-[24px] bg-haze/95 p-2 shadow-[0_24px_60px_rgb(10_12_18/0.6)] ring-1 ring-white/8 backdrop-blur-xl md:right-6">
          {menu === "subs" && <>
            <p className="px-3 pt-2 pb-1 text-xs font-semibold text-foreground/52">Subtitles</p>
            {[{ label: "Off" }, ...subs].map((t, i) => <button key={i} role="menuitemradio" aria-checked={subIdx === i - 1} onClick={() => { setSubIdx(i - 1); setMenu(null); }} className={radio(subIdx === i - 1)}><Check className={cn("size-4", subIdx !== i - 1 && "invisible")} />{t.label || "Subtitles"}</button>)}
          </>}
          {menu === "settings" && <>
            {(sources.length > 1 || levels.length > 1) && <p className="px-3 pt-2 pb-1 text-xs font-semibold text-foreground/52">Quality</p>}
            {sources.length > 1 && sources.map((x, i) => <button key={i} role="menuitemradio" aria-checked={si === i} onClick={() => setSi(i)} className={radio(si === i)}><Check className={cn("size-4", si !== i && "invisible")} />{x.label}</button>)}
            {levels.length > 1 && [-1, ...levels.map((_, i) => i)].map((i) => (
              <button key={i} role="menuitemradio" aria-checked={level === i} onClick={() => { setLevel(i); if (hls.current) hls.current.currentLevel = i; }} className={radio(level === i)}>
                <Check className={cn("size-4", level !== i && "invisible")} />{i < 0 ? "Auto" : levels[i] ? `${levels[i]}p` : `Level ${i + 1}`}
              </button>
            ))}
            {p?.kind === "anime" && <>
              <p className="px-3 pt-3 pb-1 text-xs font-semibold text-foreground/52">Audio</p>
              {(["sub", "dub"] as const).map((l) => <button key={l} role="menuitemradio" aria-checked={(p.lang ?? "sub") === l} onClick={() => { setMenu(null); go({ ...p, lang: l }); }} className={radio((p.lang ?? "sub") === l)}><Check className={cn("size-4", (p.lang ?? "sub") !== l && "invisible")} />{l === "sub" ? "Japanese with subtitles" : "English dub"}</button>)}
            </>}
            {!isLive && <>
              <p className="px-3 pt-3 pb-1 text-xs font-semibold text-foreground/52">Speed</p>
              <div className="flex flex-wrap gap-1 px-2 pb-2">{SPEEDS.map((r) => <button key={r} onClick={() => { if (video.current) video.current.playbackRate = r; }} className={cn("beam rounded-full px-3 py-1.5 text-sm", rate === r ? "bg-primary text-primary-foreground" : "bg-white/8 hover:bg-white/12")}>{r === 1 ? "Normal" : `${r}×`}</button>)}</div>
            </>}
            {p?.kind === "anime" && eps.length > 1 && <>
              <p className="px-3 pt-3 pb-1 text-xs font-semibold text-foreground/52">Episode</p>
              <div className="grid grid-cols-5 gap-1 px-2 pb-2">{eps.map((e) => <button key={e.id} onClick={() => { setMenu(null); go({ ...p, ep: e.number, epId: e.id }); }} className={cn("beam rounded-lg py-1.5 text-sm", e.number === p.ep ? "bg-primary text-primary-foreground" : "bg-white/8 hover:bg-white/12")}>{e.number}</button>)}</div>
            </>}
          </>}
        </div>
      )}

      {/* watch together drawer */}
      {drawer && (
        <div className="absolute inset-0 z-40 flex justify-end bg-black/50" onClick={(e) => e.target === e.currentTarget && setDrawer(false)}>
          <aside aria-label="Watch together" className="page-in flex h-full w-full max-w-sm flex-col gap-5 bg-haze p-5 pt-[max(env(safe-area-inset-top),20px)] shadow-[-24px_0_60px_rgb(10_12_18/0.6)]">
            <div className="flex items-center"><h2 className="flex-1 font-display text-2xl font-semibold">Watch together</h2><button aria-label="Close" onClick={() => setDrawer(false)} className={ctl}><X /></button></div>
            {room ? <>
              <div className="rounded-[18px] bg-slate p-5 text-center">
                <p className="text-sm text-foreground/60">Room code</p>
                <p className="mt-1 font-display text-4xl font-bold tracking-[0.18em] text-primary">{room}</p>
                <button className={cn(pill.primary, "mt-4 w-full")} onClick={() => { navigator.clipboard?.writeText(roomLink()); setCopied(true); setTimeout(() => setCopied(false), 1800); }}>{copied ? <Check /> : <Copy />}{copied ? "Link copied" : "Copy invite link"}</button>
                <p className="mt-3 text-xs text-foreground/52">Friends open the link, or enter the code from their avatar menu. Play, pause and seeking stay in sync.</p>
              </div>
              <div>
                <p className="mb-2 text-sm font-semibold">{members.length} watching</p>
                <ul className="space-y-1">{members.map((m) => <li key={m.id} className="flex items-center gap-3 rounded-xl px-2 py-2"><Avatar id={m.avatar} size={36} /><span className="flex-1">{m.name}</span>{m.id === youId.current && <span className="text-xs text-foreground/52">You</span>}</li>)}</ul>
              </div>
              <button className={cn(pill.ghost, "mt-auto")} onClick={() => { setDrawer(false); router.replace(p ? href(p) : "/"); }}>Leave room</button>
            </> : <>
              <p className="text-foreground/72">Start a room and share the code. Everyone in it plays, pauses and seeks together.</p>
              <button className={pill.primary} onClick={startRoom} disabled={!p}><Users />Start a room</button>
            </>}
            {roomErr && <p role="alert" className="text-sm text-destructive">{roomErr}</p>}
          </aside>
        </div>
      )}
    </main>
  );
}
export default function Page() { return <Suspense><Player /></Suspense>; }
