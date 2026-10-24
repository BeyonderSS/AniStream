import { API, loadSession } from "./core";

/** Admin telemetry (POST /v1/telemetry): batched, flushed every 60 s and on page hide, signed-in users only. */
type Ev = { kind: "play" | "play_error" | "source_used" | "heartbeat" | "search_empty"; detail: Record<string, unknown> };
const queue: Ev[] = [];
let timer: ReturnType<typeof setInterval> | undefined;

export function track(kind: Ev["kind"], detail: Record<string, unknown>) {
  if (typeof window === "undefined" || !loadSession().token || queue.length >= 40) return;
  queue.push({ kind, detail });
  if (!timer) {
    timer = setInterval(flush, 60_000);
    addEventListener("pagehide", flush);
  }
}

export function flush() {
  const s = loadSession();
  if (!s.token || !queue.length) return;
  const events = queue.splice(0, 20);
  fetch(`${API}/v1/telemetry`, {
    method: "POST", keepalive: true,
    headers: { "content-type": "application/json", Authorization: `Bearer ${s.token}` },
    body: JSON.stringify({ events, profileId: s.profile?.id, platform: "web", appVersion: "web", helper: document.documentElement.dataset.reelmHelper || undefined }),
  }).catch(() => {});
}
