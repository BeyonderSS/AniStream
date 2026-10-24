"use client";
import { useEffect, useRef } from "react";

const W = 224, H = 126, BUCKET = 10;

/**
 * Seek thumbnails without sprite sheets: a hidden muted twin of the video seeks to the hovered time
 * and the frame is painted on a small canvas above the scrubber. Frames are cached per 10 s bucket.
 * The twin is only created on the first hover, so nobody pays for it until they scrub.
 * Rendered inside the player; finds the skin's `.media-time-slider` by pointer position.
 */
export function SeekPreview({ src, hls, duration }: { src: string; hls: boolean; duration: () => number }) {
  const box = useRef<HTMLDivElement>(null), canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const root = box.current?.parentElement, ctx = canvas.current?.getContext("2d");
    if (!root || !ctx) return;
    let twin: HTMLVideoElement | undefined, stop = () => {}, want = -1, busy = false, timer = 0, last = 0;
    const cache = new Map<number, HTMLCanvasElement>();
    const paint = (c: CanvasImageSource) => { ctx.drawImage(c, 0, 0, W, H); };
    const start = async () => {
      twin = document.createElement("video");
      Object.assign(twin, { muted: true, preload: "auto", referrerPolicy: "no-referrer" });
      if (hls) {
        twin.crossOrigin = "anonymous";
        const { Hls } = await import("@videojs/hlsjs-video");
        if (!Hls.isSupported()) return;
        const h = new Hls({ startLevel: 0, maxBufferLength: 4, capLevelToPlayerSize: true });
        h.on(Hls.Events.MANIFEST_PARSED, () => { h.currentLevel = 0; }); // lowest rung is plenty for 224 px
        h.loadSource(src); h.attachMedia(twin);
        stop = () => h.destroy();
      } else { twin.src = src; stop = () => twin!.removeAttribute("src"); }
      twin.onseeked = () => {
        busy = false;
        const k = Math.floor(twin!.currentTime / BUCKET), c = document.createElement("canvas");
        c.width = W; c.height = H;
        try { c.getContext("2d")!.drawImage(twin!, 0, 0, W, H); cache.set(k, c); if (k === Math.floor(want / BUCKET)) paint(c); } catch { /* undecodable frame: keep the last one */ }
        if (Math.floor(want / BUCKET) !== k) seek();
      };
    };
    const seek = () => {
      const k = Math.floor(want / BUCKET), hit = cache.get(k);
      if (hit) return paint(hit);
      if (!twin || busy || twin.readyState < 1) return;
      busy = true; twin.currentTime = k * BUCKET;
    };
    const hide = () => { if (box.current) box.current.style.opacity = "0"; };
    const move = (e: PointerEvent) => {
      const sl = (e.target as Element).closest?.(".media-time-slider");
      const d = duration();
      if (!sl || !d || !isFinite(d)) return hide();
      const r = sl.getBoundingClientRect(), f = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)), b = box.current!, rr = root.getBoundingClientRect();
      b.style.opacity = "1";
      b.style.left = `${Math.min(rr.width - W / 2 - 8, Math.max(W / 2 + 8, e.clientX - rr.left))}px`;
      b.style.bottom = `${rr.bottom - r.top + 40}px`;
      want = f * d;
      if (!twin) start();
      clearTimeout(timer);
      const wait = 150 - (Date.now() - last);
      timer = window.setTimeout(() => { last = Date.now(); seek(); }, Math.max(0, wait)); // throttle ~150 ms
    };
    root.addEventListener("pointermove", move);
    root.addEventListener("pointerleave", hide);
    root.addEventListener("pointerup", hide);
    root.addEventListener("pointercancel", hide);
    return () => {
      root.removeEventListener("pointermove", move); root.removeEventListener("pointerleave", hide);
      root.removeEventListener("pointerup", hide); root.removeEventListener("pointercancel", hide);
      clearTimeout(timer); stop();
    };
  }, [src, hls]); // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <div ref={box} aria-hidden className="pointer-events-none absolute z-20 -translate-x-1/2 overflow-hidden rounded-xl bg-black opacity-0 shadow-[0_12px_40px_rgb(0_0_0/0.6)] ring-1 ring-white/15 transition-opacity duration-150" style={{ width: W, height: H }}>
      <canvas ref={canvas} width={W} height={H} className="size-full" />
    </div>
  );
}
