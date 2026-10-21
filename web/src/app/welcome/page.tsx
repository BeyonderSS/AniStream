"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { GUEST } from "@/lib/core";
import { useSession } from "@/lib/session";

export default function Welcome() {
  const { set } = useSession(), router = useRouter();
  return (
    <main className="relative flex min-h-dvh items-center justify-center overflow-hidden px-6">
      <div className="pointer-events-none absolute -top-40 left-1/2 size-[40rem] -translate-x-1/2 rounded-full bg-primary/15 blur-3xl" />
      <div className="relative flex w-full max-w-sm flex-col gap-3 text-center">
        <h1 className="font-display text-6xl font-bold text-primary">Reelm</h1>
        <p className="mb-6 text-muted-foreground">Anime, movies, series and live TV. One place, every screen.</p>
        <Link href="/signin"><Button size="lg" className="h-11 w-full rounded-full">Sign in</Button></Link>
        <Link href="/signup"><Button size="lg" variant="secondary" className="h-11 w-full rounded-full">Create account</Button></Link>
        <Button size="lg" variant="ghost" className="h-11 rounded-full" onClick={() => { set({ guest: true, profile: GUEST }); router.replace("/"); }}>Continue as guest</Button>
      </div>
    </main>
  );
}
