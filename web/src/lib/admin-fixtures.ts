/** Dev-only mocked admin API responses (loaded by lib/admin.ts behind `?fixture`, never in production). */
const DAY = 86_400_000, now = Date.now();
let seed = 7;
const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
const pick = <T,>(a: T[]) => a[Math.floor(rnd() * a.length)];
const iso = (t: number) => new Date(t).toISOString().slice(0, 10);

const NAMES = ["beyonder", "mira_k", "dev_patel", "sunny", "aoi", "rhea", "kabir", "lena_w", "tomo", "nadia", "jin", "olu"];
const TITLES = ["Frieren: Beyond Journey's End", "Dune: Part Two", "Shōgun", "Solo Leveling", "The Bear", "Oppenheimer", "Dandadan", "Severance", "Spirited Away", "Arcane"];
const SOURCES = ["MovieBox", "Dramachi", "4KHDHub", "Pixeldrain", "anime"];
const AVATARS = ["fox", "panda", "cat_face", "owl", "popcorn", "nerd_face", "unicorn", "koala"];
const users = NAMES.map((u, i) => ({ id: `u${i}`, username: u, is_admin: i === 0 ? 1 : 0, disabled: i === 9 ? 1 : 0, created_at: now - (40 - i * 3) * DAY,
  last_seen_at: now - i * 47 * 60_000, profiles: 1 + (i % 4), sessions: 1 + (i % 3) }));
const KINDS = ["login", "play", "heartbeat", "history_push", "source_used", "profile_update", "room_join", "hub_connect", "play_error", "login_failed", "pair_claim"];
const ev = (i: number, kind = pick(KINDS)) => {
  const u = pick(users);
  return { id: 5000 - i, at: now - i * 97_000, user_id: u.id, username: u.username, profile_id: "p1", device: pick(["phone", "tv", "web"]), kind,
    detail: kind === "play_error" ? { title: pick(TITLES), source: pick(SOURCES), codec: pick(["hevc", "h264", "eac3 dolby", "av1"]), error: pick(["MEDIA_ERR_DECODE: hvc1 not supported", "manifest 403 from host", "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"]) }
      : kind === "history_push" ? { count: 1 + Math.floor(rnd() * 6) } : kind === "login_failed" ? { username: u.username }
      : ["play", "heartbeat", "source_used"].includes(kind) ? { title: pick(TITLES), source: pick(SOURCES), quality: pick(["1080p", "720p", "2160p HDR"]) } : {},
    ip_country: pick(["IN", "US", "JP", "DE", "GB"]), ua: "Mozilla/5.0 (X11; Linux x86_64) Chrome/141", app_version: pick(["1.0.41", "1.0.42", "web"]) };
};
const events = Array.from({ length: 60 }, (_, i) => ev(i));

const overview = () => ({
  totals: { users: 128, profiles: 301, devices: 214, history: 9_842, activeRooms: 3, watchingNow: 17, onlineNow: 26 },
  daily: Array.from({ length: 30 }, (_, i) => {
    const wave = Math.sin(i / 3.2) * 6 + i * 0.9;
    return { day: iso(now - (29 - i) * DAY), signups: Math.max(0, Math.round(2 + rnd() * 5 + (i > 22 ? 4 : 0))), dau: Math.round(28 + wave + rnd() * 6), wau: Math.round(64 + i * 1.6 + rnd() * 4) };
  }),
  topTitles: TITLES.slice(0, 8).map((title, i) => ({ title, plays: 240 - i * 26 - Math.round(rnd() * 10), viewers: 61 - i * 6 })),
  sources: SOURCES.map((source, i) => ({ source, ok: 900 - i * 160, errors: [22, 140, 31, 260, 12][i] })),
  codecs: [{ codec: "hevc", errors: 188 }, { codec: "eac3 dolby", errors: 74 }, { codec: "h264", errors: 29 }],
});

const fixtures: Record<string, () => unknown> = {
  overview,
  users: () => ({ users, total: users.length }),
  events: () => ({ events, next: 4940 }),
  live: () => ({
    watching: Array.from({ length: 6 }, (_, i) => ({ at: now - i * 20_000, user_id: users[i].id, username: users[i].username, device: ["tv", "phone", "web", "tv", "phone", "web"][i],
      app_version: ["1.0.42", "1.0.42", "web", "1.0.41", "1.0.42", "web"][i], profile: ["Puneet", "Mira", "Dev", "Kids", "Aoi", "Rhea"][i], avatar: AVATARS[i],
      detail: { title: TITLES[i], kind: i % 3 ? "movie" : "anime", episode: i % 3 ? undefined : 4 + i, source: SOURCES[i % 5], quality: ["2160p HDR", "1080p", "720p"][i % 3], position: 600 + i * 431, duration: 1440 + i * 1300 } })),
    rooms: [{ code: "KX7PQ2", title: "Dune: Part Two", members: [{ name: "Puneet", avatar: "fox" }, { name: "Mira", avatar: "panda" }, { name: "Dev", avatar: "owl" }], created_at: now - 40 * 60_000, updated_at: now - 60_000 },
      { code: "M4TRZ9", title: "Frieren: Beyond Journey's End", members: [{ name: "Aoi", avatar: "cat_face" }, { name: "Jin", avatar: "koala" }], created_at: now - 12 * 60_000, updated_at: now - 3 * 60_000 }],
  }),
  engagement: () => ({
    perUser: users.slice(0, 8).map((u, i) => ({ user_id: u.id, username: u.username, minutes: 2400 - i * 260 })),
    perProfile: users.slice(0, 8).map((u, i) => ({ profile_id: `p${i}`, name: ["Puneet", "Mira", "Kids", "Dev", "Aoi", "Rhea", "Kabir", "Lena"][i], avatar: AVATARS[i], kids: i === 2 ? 1 : 0, username: u.username, minutes: 1800 - i * 190 })),
    heatmap: Array.from({ length: 7 * 24 }, (_, k) => { const dow = Math.floor(k / 24), hour = k % 24, eve = Math.exp(-((hour - 21) ** 2) / 8) * 90; return { dow, hour, minutes: Math.round(eve * (dow === 5 || dow === 6 ? 1.5 : 1) + rnd() * 12) }; }),
    platforms: [{ platform: "tv", minutes: 9100, users: 41 }, { platform: "phone", minutes: 6200, users: 77 }, { platform: "web", minutes: 2900, users: 33 }],
    completion: TITLES.map((title, i) => ({ title, starts: 80 - i * 6, completed: Math.round((80 - i * 6) * (0.82 - i * 0.07)) })),
    binge: users.slice(0, 6).map((u, i) => ({ username: u.username, current: [9, 4, 0, 2, 6, 1][i], longest: [14, 11, 9, 7, 6, 5][i] })),
  }),
  content: () => ({
    watched: TITLES.map((title, i) => ({ title, minutes: 5200 - i * 430, viewers: 70 - i * 5 })),
    abandoned: TITLES.slice(3).map((title, i) => ({ title, abandoned: 21 - i * 3 })),
    kinds: [{ kind: "anime", minutes: 8400 }, { kind: "movie", minutes: 5300 }, { kind: "series", minutes: 3900 }, { kind: "live", minutes: 1200 }],
    searches: ["jujutsu kaisen s3", "the boys", "naruto hindi dub", "chainsaw man movie", "kgf 3", "blue lock"].map((query, i) => ({ query, times: 14 - i * 2, last_at: now - i * 3 * 3_600_000 })),
  }),
  sources: () => ({
    bySource: SOURCES.flatMap((source, i) => [{ source, host: ["vid.moviebox.ng", "drama1.cfd", "hubcloud.one", "pixeldrain.com", "zokoanime.video"][i], ok: 820 - i * 150, errors: [18, 120, 26, 240, 9][i], ttff: [1300, 2600, 3900, 2100, 900][i] }]),
    daily: Array.from({ length: 14 }, (_, d) => SOURCES.map((source, i) => ({ source, day: iso(now - (13 - d) * DAY), ok: 60 - i * 8 + Math.round(rnd() * 8), errors: Math.round((i === 3 && d > 10 ? 40 : 3) + rnd() * 6) }))).flat(),
    alerts: [{ source: "Pixeldrain", ok: 3, errors: 11 }],
    codecs: overview().codecs.map((c) => ({ ...c, hevc: c.codec === "hevc" ? c.errors : 0, dolby: c.codec.includes("dolby") ? c.errors : 0 })),
    hevcFailures: 188, dolbyFailures: 74,
    recent: events.filter((e) => e.kind === "play_error").concat(Array.from({ length: 6 }, (_, i) => ev(i + 100, "play_error"))),
  }),
  app: () => ({
    crashes: Array.from({ length: 4 }, (_, i) => ({ ...ev(i + 200, "crash"), detail: { trace: `main: java.lang.IllegalStateException: Player released\n\tat androidx.media3.exoplayer.ExoPlayerImpl.verifyApplicationThread(ExoPlayerImpl.java:2899)\n\tat com.anistream.feature.player.PlayerViewModel.saveProgress(PlayerViewModel.kt:${180 + i})` } })),
    versions: [{ platform: "phone", version: "1.0.42", users: 58 }, { platform: "phone", version: "1.0.41", users: 14 }, { platform: "tv", version: "1.0.42", users: 30 }, { platform: "tv", version: "1.0.39", users: 6 }, { platform: "web", version: "web", users: 33 }],
    updates: [{ version: "v1.0.42", offered: 61, installed: 52 }, { version: "v1.0.41", offered: 40, installed: 37 }],
    helpers: [{ helper: "0.4.2", users: 19 }, { helper: "0.4.1", users: 6 }, { helper: "none", users: 8 }],
  }),
  security: () => ({
    failed: Array.from({ length: 6 }, (_, i) => ({ at: now - i * 2.2 * 3_600_000, ip_country: pick(["IN", "US", "RU", "BR"]), device: "web", ua: "Mozilla/5.0", username: pick([...NAMES, "admin", "root"]), known: i % 2 })),
    newDevices: users.slice(1, 6).map((u, i) => ({ user_id: u.id, username: u.username, device: ["tv", "phone", "web", "tv", "phone"][i], ua: "okhttp/4.12.0", ip_country: "IN", first_at: now - i * 9 * 3_600_000 })),
    sessions: users.slice(0, 6).map((u, i) => ({ user_id: u.id, username: u.username, sessions: 6 - i, last_used_at: now - i * 3_600_000 })),
    kids: [{ id: "pk", name: "Kids", avatar: "unicorn", username: "beyonder", minutes: 340, last_at: now - 5 * 3_600_000, last_title: "Spirited Away" }, { id: "pk2", name: "Little one", avatar: "penguin", username: "mira_k", minutes: 95, last_at: now - 2 * DAY, last_title: "Bluey" }],
  }),
  ops: () => ({
    connected: false, cloudflare: null, limits: { rowsReadPerDay: 5_000_000, rowsWrittenPerDay: 100_000, storageBytes: 5e9 },
    deploys: {
      runs: Array.from({ length: 6 }, (_, i) => ({ id: i, name: ["Deploy web", "Android release", "Deploy API"][i % 3], branch: "main", status: "completed", conclusion: i === 2 ? "failure" : "success", at: new Date(now - i * 7 * 3_600_000).toISOString(), url: "#", title: ["perf(web): Dolby audio starts sooner", "fix(web): Dolby audio no longer stays silent", "fix(update): delete the downloaded APK"][i % 3] })),
      releases: [{ tag: "v1.0.42", name: "v1.0.42", at: new Date(now - DAY).toISOString(), url: "#", downloads: 214 }, { tag: "v1.0.41", name: "v1.0.41", at: new Date(now - 5 * DAY).toISOString(), url: "#", downloads: 388 }],
    },
  }),
};

export function fixture(path: string): unknown {
  const [key] = path.split("?");
  if (key.startsWith("users/")) {
    const u = users.find((x) => x.id === key.split("/")[1]) ?? users[0];
    return {
      user: u,
      profiles: [{ id: "p1", name: "Puneet", avatar: "fox", kids: false }, { id: "p2", name: "Kids", avatar: "unicorn", kids: true }, { id: "p3", name: "Mira", avatar: "panda", kids: false }],
      devices: [{ device: "tv", ua: "okhttp/4.12.0", country: "IN", first_at: now - 30 * DAY, last_at: now - 3_600_000, events: 412 }, { device: "web", ua: "Mozilla/5.0 (X11; Linux x86_64) Chrome/141", country: "IN", first_at: now - 9 * DAY, last_at: now - 600_000, events: 120 }],
      history: TITLES.slice(0, 6).map((t, i) => ({ profile_id: "p1", key: `k${i}`, payload: { title: t, kind: i % 2 ? "movie" : "anime", ep: i + 1 }, position: 600 + i * 300, duration: 1440 + i * 900, updated_at: now - i * 5 * 3_600_000 })),
      events: events.slice(0, 15).map((e) => ({ ...e, user_id: u.id })),
      sessions: [{ id: "a".repeat(64), created_at: now - 20 * DAY, last_used_at: now - 3_600_000, device: "tv", ua: "okhttp/4.12.0" }, { id: "b".repeat(64), created_at: now - 2 * DAY, last_used_at: now - 60_000, device: "web", ua: "Mozilla/5.0 Chrome/141" }],
    };
  }
  return (fixtures[key] ?? (() => ({})))();
}
