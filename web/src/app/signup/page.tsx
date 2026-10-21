"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { reelm } from "@/lib/core";
import { useSession } from "@/lib/session";
import { AvatarPicker } from "@/components/app";

const userErr = (u: string) => !u ? "" : !/^[a-z0-9_]+$/.test(u) ? "Use only lowercase letters, numbers and _" : u.length < 3 ? "At least 3 characters" : u.length > 32 ? "At most 32 characters" : "";

export default function SignUp() {
  const { set } = useSession(), router = useRouter();
  const [step, setStep] = useState(0);
  const [u, setU] = useState(""), [p, setP] = useState(""), [avatar, setAvatar] = useState("grinning_face"), [err, setErr] = useState(""), [busy, setBusy] = useState(false);
  async function finish() {
    setBusy(true); setErr("");
    try {
      const r = await reelm("/v1/auth/register", { json: { username: u, password: p } });
      const { profiles } = await reelm("/v1/profiles", { token: r.token });
      await reelm(`/v1/profiles/${profiles[0].id}`, { method: "PATCH", token: r.token, json: { avatar } }).catch(() => {});
      set({ token: r.token, user: r.user }); router.replace("/profiles");
    } catch (x) { setErr((x as Error).message); setBusy(false); setStep(0); }
  }
  const ok = [!!u && !userErr(u), p.length >= 8, true][step];
  return (
    <main className="flex min-h-dvh items-center justify-center px-6">
      <form onSubmit={(e) => { e.preventDefault(); if (!ok) return; step < 2 ? setStep(step + 1) : finish(); }} className="flex w-full max-w-md flex-col gap-3">
        <p className="text-sm text-muted-foreground">Step {step + 1} of 3</p>
        {step === 0 && <><h1 className="font-display text-3xl font-bold">Pick a username</h1>
          <Input aria-label="Username" autoFocus autoCapitalize="none" value={u} onChange={(e) => setU(e.target.value.toLowerCase())} className="h-11" />
          <p className="text-sm text-destructive">{userErr(u)}</p></>}
        {step === 1 && <><h1 className="font-display text-3xl font-bold">Choose a password</h1>
          <Input aria-label="Password" type="password" autoFocus value={p} onChange={(e) => setP(e.target.value)} className="h-11" />
          <p className="text-sm text-muted-foreground">At least 8 characters</p></>}
        {step === 2 && <><h1 className="font-display text-3xl font-bold">Pick your avatar</h1><AvatarPicker value={avatar} onChange={setAvatar} /></>}
        {err && <p role="alert" className="text-sm text-destructive">{err}</p>}
        <Button type="submit" size="lg" disabled={!ok || busy} className="h-11 rounded-full">{step < 2 ? "Continue" : "Create account"}</Button>
        <Button type="button" variant="ghost" onClick={() => (step ? setStep(step - 1) : router.push("/welcome"))}>Back</Button>
      </form>
    </main>
  );
}
