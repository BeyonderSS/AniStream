"use client";
import { use, useState } from "react";
import Link from "next/link";
import { MonitorCheck } from "lucide-react";
import { AuthShell } from "@/components/auth";
import { pill } from "@/components/app";
import { reelm } from "@/lib/core";
import { useSession } from "@/lib/session";

export default function Pair({ params }: { params: Promise<{ code: string }> }) {
  const { code } = use(params), { s, ready } = useSession();
  const [msg, setMsg] = useState(""), [done, setDone] = useState(false);
  async function claim() {
    try { await reelm(`/v1/pair/${code}/claim`, { method: "POST", token: s.token }); setDone(true); } catch (e) { setMsg((e as Error).message); }
  }
  return (
    <AuthShell back="/">
      <div className="page-in flex flex-col items-center gap-4 text-center">
        <MonitorCheck className="size-12 text-primary" />
        <h1 className="font-display text-3xl font-bold tracking-tight">Sign in on your TV</h1>
        <p className="rounded-2xl bg-slate px-6 py-3 font-display text-3xl font-bold tracking-[0.2em] text-primary">{code}</p>
        {!ready ? null : done ? <p className="text-signal">Approved. Your TV is signing in.</p> : s.token ? (
          <><p className="text-foreground/72">Check the code matches your TV, then approve it as @{s.user?.username}.</p><button className={pill.primary} onClick={claim}>Approve</button></>
        ) : (
          <><p className="text-foreground/72">Sign in first, then approve the code.</p><Link href={`/signin?next=/pair/${code}`} className={pill.primary}>Sign in</Link></>
        )}
        {msg && <p role="alert" className="text-sm text-destructive">{msg}</p>}
      </div>
    </AuthShell>
  );
}
