"use client";
import { use, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { Play } from "lucide-react";
import { Button } from "@/components/ui/button";
import { FavButton, mediaCard } from "@/components/app";
import { kids, KIDS_BLOCKED, watchHref, type Play as P } from "@/lib/core";
import { useApi, useSession } from "@/lib/session";
import { Suspense } from "react";

function Title({ id }: { id: string }) {
  const type = useSearchParams().get("type") ?? "movie", { s } = useSession();
  const { data: m, error } = useApi(`movies/detail?id=${encodeURIComponent(id)}&type=${type}`);
  const [season, setSeason] = useState(1);
  if (error) return <p role="alert" className="p-10 text-destructive">{error}</p>;
  if (!m) return <div className="m-10 h-80 animate-pulse rounded-2xl bg-card" />;
  if (s.profile?.kids && !kids.media(m.genres, m.certification)) return <p role="alert" className="p-10">{KIDS_BLOCKED}</p>;
  const play = (se = 0, ep = 0): P => ({ kind: "movie", id, title: m.title, poster: m.poster, season: se, episode: ep, type: m.type, year: m.year });
  const cur = m.seasons.find((x: any) => x.season === season) ?? m.seasons[0];
  const chips = [m.year, m.type === "series" ? "Series" : "Movie", m.runtime, m.rating && `IMDb ${m.rating}`, ...m.genres.slice(0, 4)].filter(Boolean);
  return (
    <main className="pb-16">
      <div className="relative min-h-[50vh]">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        {m.backdrop && <img src={m.backdrop} alt="" referrerPolicy="no-referrer" className="absolute inset-0 size-full object-cover opacity-40" />}
        <div className="absolute inset-0 bg-gradient-to-t from-background to-transparent" />
        <div className="relative flex min-h-[50vh] max-w-2xl flex-col justify-end gap-3 px-4 pb-6 md:px-10">
          <h1 className="font-display text-4xl font-bold md:text-5xl">{m.title}</h1>
          <div className="flex flex-wrap gap-2">{chips.map((c: any) => <span key={c} className="rounded-full bg-secondary px-3 py-0.5 text-xs">{c}</span>)}</div>
          <p className="line-clamp-4 text-muted-foreground">{m.description}</p>
          {m.cast.length > 0 && <p className="text-sm text-muted-foreground">Cast: {m.cast.slice(0, 6).join(", ")}</p>}
          <div className="flex flex-wrap gap-2">
            {m.type === "movie" && <Link href={watchHref(play())}><Button size="lg" className="beam h-11 rounded-full px-6"><Play />Play</Button></Link>}
            <FavButton c={mediaCard(m)} />
          </div>
        </div>
      </div>
      {m.type === "series" && cur && (
        <section className="mt-6 space-y-3 px-4 md:px-10">
          <div className="flex flex-wrap gap-2">{m.seasons.map((x: any) => <Button key={x.season} size="sm" variant={x.season === cur.season ? "default" : "secondary"} className="rounded-full" onClick={() => setSeason(x.season)}>Season {x.season}</Button>)}</div>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))] gap-2">
            {[...Array(cur.episodes)].map((_, i) => <Link key={i} href={watchHref(play(cur.season, i + 1))} className="beam grid h-11 place-items-center rounded-xl bg-card text-sm">{i + 1}</Link>)}
          </div>
        </section>
      )}
    </main>
  );
}
export default function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <Suspense><Title id={decodeURIComponent(id)} /></Suspense>;
}
