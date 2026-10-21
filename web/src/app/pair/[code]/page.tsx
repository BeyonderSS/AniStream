"use client";
import { use, useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { reelm } from "@/lib/core";
import { useSession } from "@/lib/session";

export default function Pair({ params }: { params: Promise<{ code: string }> }) {
  const { code } = use(params), { s, ready } = useSession();
  const [msg, setMsg] = useState(""), [done, setDone] = useState(false);
  async function claim() {
    try { await reelm(`/v1/pair/${code}/claim`, { method: "POST", token: s.token }); setDone(true); } catch (e) { setMsg((e as Error).message); }
  }
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-4 px-6 text-center">
      <h1 className="font-display text-3xl font-bold">Sign in on your TV</h1>
      <p className="font-mono text-2xl tracking-widest text-primary">{code}</p>
      {!ready ? null : done ? <p>Done. Your TV is signing in.</p> : s.token ? (
        <><p className="text-muted-foreground">Approve this code as {s.user?.username}?</p><Button size="lg" className="rounded-full" onClick={claim}>Approve</Button></>
      ) : (
        <><p className="text-muted-foreground">Sign in first, then approve the code.</p><Link href={`/signin?next=/pair/${code}`}><Button size="lg" className="rounded-full">Sign in</Button></Link></>
      )}
      {msg && <p role="alert" className="text-destructive">{msg}</p>}
    </main>
  );
}
