"use client";
import Link from "next/link";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { AuthShell, Field } from "@/components/auth";
import { pill } from "@/components/app";
import { reelm } from "@/lib/core";
import { useSession } from "@/lib/session";

const friendly = (m: string) => /invalid|credential|password|401/i.test(m) ? "That username and password don't match. Check them and try again." : /fetch|network/i.test(m) ? "Can't reach Reelm right now. Check your connection and try again." : m;

function Form() {
  const { set } = useSession(), router = useRouter(), next = useSearchParams().get("next") ?? "/profiles";
  const [u, setU] = useState(""), [p, setP] = useState(""), [err, setErr] = useState(""), [busy, setBusy] = useState(false);
  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true); setErr("");
    try { const r = await reelm("/v1/auth/login", { json: { username: u.trim().toLowerCase(), password: p } }); set({ token: r.token, user: r.user }); router.replace(next); }
    catch (x) { setErr(friendly((x as Error).message)); setBusy(false); }
  }
  return (
    <AuthShell back="/welcome">
      <form onSubmit={submit} className="page-in flex flex-col gap-2">
        <h1 className="font-display text-3xl font-bold tracking-tight">Welcome back</h1>
        <p className="mb-6 text-foreground/72">Sign in with your Reelm username.</p>
        <Field label="Username" autoCapitalize="none" autoComplete="username" autoFocus value={u} onChange={(e) => setU(e.target.value)} />
        <Field label="Password" type="password" autoComplete="current-password" value={p} onChange={(e) => setP(e.target.value)} />
        {err && <p role="alert" className="rounded-2xl bg-destructive/12 px-4 py-3 text-sm text-destructive">{err}</p>}
        <button type="submit" disabled={busy || !u || !p} className={`${pill.primary} mt-3`}>{busy && <Loader2 className="animate-spin" />}{busy ? "Signing in" : "Sign in"}</button>
        <p className="mt-4 text-center text-sm text-foreground/60">New to Reelm? <Link href="/signup" className="font-semibold text-primary hover:underline">Create account</Link></p>
      </form>
    </AuthShell>
  );
}
export default function SignIn() { return <Suspense><Form /></Suspense>; }
