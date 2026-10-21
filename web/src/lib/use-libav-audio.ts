"use client";
import { useEffect, useState, type RefObject } from "react";

const MAX_DRIFT = 0.08, AHEAD = 10; // s: re-anchor above 80 ms; decode this far past the playhead
type Chunk = { t: number; buf: AudioBuffer; node?: AudioBufferSourceNode };

/**
 * Plays a file's audio through libav.js (in a worker) when the browser can't: the stream is flagged `dolby`, or a plain
 * file plays but Chrome decodes no audio bytes. The <video> stays the clock; its own (silent) output is swallowed by an
 * unconnected MediaElementSource so the skin's volume/mute drive our GainNode instead. Returns a toast message, or "".
 */
export function useLibavAudio(video: RefObject<HTMLVideoElement | null>, url: string | undefined, dolby: boolean, key: string) {
  const [note, setNote] = useState("");
  useEffect(() => {
    const v = video.current;
    if (!v || !url) return;
    let w: Worker | undefined, ctx: AudioContext | undefined, gain: GainNode, seekId = 0, queue: Chunk[] = [];
    let anchor: { ctx: number; media: number } | null = null, timer = 0, dead = false;
    const stats = { codec: "", samples: 0, drift: 0, reanchors: 0, scheduled: 0, startMs: 0 }; // startMs: seek/open → first sound
    let asked = performance.now(), head = v.currentTime; // head: end of the newest decoded audio
    const say = (m: string) => { setNote(m); setTimeout(() => setNote(""), 5000); };
    const stopAll = () => { for (const c of queue) { c.node?.stop(); c.node = undefined; } anchor = null; };
    const volume = () => gain && (gain.gain.value = v.muted ? 0 : v.volume);
    const fail = (e: unknown) => {
      if (dead) return;
      console.warn("[libav audio]", e); stop(); say("Couldn't decode this audio");
    };

    const pump = () => {
      if (!ctx || !w) return;
      const now = v.currentTime;
      w.postMessage({ type: "want", until: now + AHEAD });
      queue = queue.filter((c) => c.t + c.buf.duration > now - 1);
      stats.scheduled = queue.filter((c) => c.node).length;
      if (v.paused || v.seeking || v.readyState < 3) return stopAll(); // stall/pause: silence until the video moves again
      if (ctx.state !== "running") ctx.resume();
      // decoding fell behind the video (slow host, or it started at 0 after a resume): skip ahead instead of trailing
      if (head < now - 2 && performance.now() - asked > 4000) return jump(now + 3);
      const lat = ctx.baseLatency + (ctx.outputLatency || 0);
      if (anchor) {
        stats.drift = now - (anchor.media + ctx.currentTime - anchor.ctx); // video clock vs audio clock
        if (Math.abs(stats.drift) > MAX_DRIFT) { stats.reanchors++; stopAll(); }
      }
      anchor ??= { ctx: ctx.currentTime, media: now };
      for (const c of queue) {
        if (c.node || c.t > now + 2) continue;
        const when = anchor.ctx - lat + (c.t - anchor.media), late = Math.max(0, ctx.currentTime - when);
        if (late >= c.buf.duration) continue;
        c.node = ctx.createBufferSource(); c.node.buffer = c.buf; c.node.connect(gain);
        c.node.start(when + late, late);
        if (asked) { stats.startMs = Math.round(performance.now() - asked); asked = 0; }
      }
    };
    const jump = (t: number) => { asked = performance.now(); head = t; stopAll(); queue = []; w?.postMessage({ type: "seek", t, id: ++seekId }); };
    const onSeek = () => jump(v.currentTime);
    const onRate = () => { if (v.playbackRate !== 1) { v.playbackRate = 1; say("Speed changes aren't available with this audio"); } };
    const events: [string, () => void][] = [["seeking", onSeek], ["volumechange", volume], ["ratechange", onRate], ["play", pump], ["pause", pump], ["waiting", pump], ["playing", pump]];

    const start = () => {
      try {
        ctx = new AudioContext();
        ctx.createMediaElementSource(v); // swallow the element's own output (unconnected)
        gain = ctx.createGain(); gain.connect(ctx.destination); volume();
        w = new Worker(new URL("./libav-audio.worker.ts", import.meta.url));
      } catch (e) { return fail(e); }
      w.onerror = (e) => fail(e.message);
      w.onmessage = ({ data: m }) => {
        if (m.type === "error") return fail(m.message);
        if (m.type === "info") { stats.codec = m.codec; console.info("[libav audio] codec", m.codec); }
        if (m.type !== "pcm" || m.id !== seekId) return;
        const buf = ctx!.createBuffer(2, m.l.length, m.sr);
        buf.copyToChannel(m.l, 0); buf.copyToChannel(m.r, 1);
        queue.push({ t: m.t, buf }); stats.samples += m.l.length; head = m.t + buf.duration;
        pump();
      };
      (v as any).reelmAudio = stats; // read by devtools / tests
      w.postMessage({ type: "open", url });
      w.postMessage({ type: "seek", t: v.currentTime, id: seekId });
      onRate();
      for (const [e, f] of events) v.addEventListener(e, f);
      timer = window.setInterval(pump, 250);
    };
    function stop() {
      dead = true; clearInterval(timer); stopAll(); w?.terminate(); ctx?.close();
      for (const [e, f] of events) v!.removeEventListener(e, f);
    }

    // Not flagged: start only if the browser plays the file but decodes no audio (Chromium's counter stays 0).
    let probe = 0, t0 = -1;
    if (dolby) start();
    else probe = window.setInterval(() => {
      if (v.paused || v.readyState < 3) return;
      if (t0 < 0) t0 = v.currentTime;
      if (v.currentTime - t0 < 2) return;
      clearInterval(probe);
      if ((v as any).webkitAudioDecodedByteCount === 0) start();
    }, 500);
    return () => { clearInterval(probe); if (w && !dead) stop(); };
  }, [url, dolby, key]); // eslint-disable-line react-hooks/exhaustive-deps
  return note;
}
