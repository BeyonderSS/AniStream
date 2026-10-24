"use client";
/* eslint-disable @typescript-eslint/no-explicit-any -- admin API rows are untyped JSON, shapes documented in server/API.md */
import { useCallback, useEffect, useState } from "react";
import { reelm } from "./core";
import { useSession } from "./session";

/** Dev-only: `/admin?fixture` renders mocked data (screenshots, design work). Dead code in production builds. */
export function fixtureMode() {
  if (process.env.NODE_ENV === "production" || typeof window === "undefined") return false;
  try {
    if (new URLSearchParams(location.search).has("fixture")) sessionStorage.setItem("reelm.fixture", "1");
    return sessionStorage.getItem("reelm.fixture") === "1";
  } catch { return false; }
}

export async function adminGet<T>(path: string, token?: string): Promise<T> {
  if (process.env.NODE_ENV !== "production" && fixtureMode()) return (await import("./admin-fixtures")).fixture(path) as T;
  return reelm<T>(`/v1/admin/${path}`, { token });
}

/** GET an admin route; `every` re-polls in ms. `reload` refetches (after an action). */
export function useAdmin<T = any>(path: string | null, every?: number) {
  const { s } = useSession();
  const [st, setSt] = useState<{ data?: T; error?: string; at?: number }>({});
  const reload = useCallback(() => {
    if (!path) return;
    adminGet<T>(path, s.token).then((data) => setSt({ data, at: Date.now() })).catch((e) => setSt((o) => ({ ...o, error: e.message })));
  }, [path, s.token]);
  useEffect(() => {
    reload();
    if (!every) return;
    const i = setInterval(reload, every);
    return () => clearInterval(i);
  }, [reload, every]);
  return { ...st, loading: !st.data && !st.error, reload };
}

/** POST/DELETE an admin action. */
export function adminAct<T = any>(token: string | undefined, path: string, method = "POST"): Promise<T> {
  if (process.env.NODE_ENV !== "production" && fixtureMode()) return Promise.resolve({ ok: true, password: "k7mq2xp9rh4t" } as T);
  return reelm<T>(`/v1/admin/${path}`, { method, token, json: method === "POST" ? {} : undefined });
}
