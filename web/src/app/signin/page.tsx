"use client";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { reelm } from "@/lib/core";
import { useSession } from "@/lib/session";

function Form() {
  const { set } = useSession(), router = useRouter(), next = useSearchParams().get("next") ?? "/profiles";
  const [u, setU] = useState(""), [p, setP] = useState(""), [err, setErr] = useState(""), [busy, setBusy] = useState(false);
  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true); setErr("");
    try { const r = await reelm("/v1/auth/login", { json: { username: u.trim().toLowerCase(), password: p } }); set({ token: r.token, user: r.user }); router.replace(next); }
    catch (x) { setErr((x as Error).message); setBusy(false); }
  }
  return (
    <main className="flex min-h-dvh items-center justify-center px-6">
      <form onSubmit={submit} className="flex w-full max-w-sm flex-col gap-3">
        <h1 className="font-display text-3xl font-bold">Welcome back</h1>
        <Input aria-label="Username" placeholder="Username" autoCapitalize="none" autoFocus value={u} onChange={(e) => setU(e.target.value)} className="h-11" />
        <Input aria-label="Password" type="password" placeholder="Password" value={p} onChange={(e) => setP(e.target.value)} className="h-11" />
        {err && <p role="alert" className="text-sm text-destructive">{err}</p>}
        <Button type="submit" size="lg" disabled={busy || !u || !p} className="h-11 rounded-full">Sign in</Button>
        <Button type="button" variant="ghost" onClick={() => router.push("/welcome")}>Back</Button>
      </form>
    </main>
  );
}
export default function SignIn() { return <Suspense><Form /></Suspense>; }
