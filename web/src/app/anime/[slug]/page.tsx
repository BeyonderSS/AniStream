"use client";
import { use, useEffect, useState } from "react";
import Link from "next/link";
import { Play } from "lucide-react";
import { Button } from "@/components/ui/button";
import { animeCard, FavButton, Rail } from "@/components/app";
import { getHistory, kids, KIDS_BLOCKED, watchHref, type Play as P } from "@/lib/core";
import { useApi, useSession } from "@/lib/session";

export default function Anime({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params), { s } = useSession();
  const d = useApi(`anime/detail?slug=${slug}`), e = useApi(`anime/episodes?slug=${slug}`);
  const [lang, setLang] = useState("sub");
  const [resume, setResume] = useState<{ p: P; t: number } | null>(null);
  useEffect(() => {
    const h = Object.values(getHistory(s.profile?.id ?? "guest")).filter((x) => !x.deleted && x.payload.kind === "anime" && x.payload.id === slug).sort((a, b) => b.updatedAt - a.updatedAt)[0];
    setResume(h ? { p: h.payload, t: h.position } : null);
  }, [s.profile, slug]);
  const a = d.data;
  if (d.error) return <p role="alert" className="p-10 text-destructive">{d.error}</p>;
  if (!a) return <div className="m-10 h-80 animate-pulse rounded-2xl bg-card" />;
  if (s.profile?.kids && !kids.anime(a.rating, a.genres)) return <p role="alert" className="p-10">{KIDS_BLOCKED}</p>;
  const play = (ep: any): P => ({ kind: "anime", id: slug, title: a.title, poster: a.poster, ep: ep.number, epId: ep.id, lang });
  const eps = (e.data ?? []) as any[];
  const chips = [a.type, a.year, a.status, a.rating, a.duration, a.score && `MAL ${a.score}`, ...a.genres.slice(0, 4)].filter(Boolean);
  return (
    <main className="space-y-8 px-4 pb-16 md:px-10">
      <div className="flex flex-col gap-6 md:flex-row">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        {a.poster && <img src={a.poster} alt="" referrerPolicy="no-referrer" className="w-48 self-start rounded-2xl" />}
        <div className="max-w-2xl space-y-4">
          <h1 className="font-display text-4xl font-bold">{a.title}</h1>
          <div className="flex flex-wrap gap-2">{chips.map((c) => <span key={String(c)} className="rounded-full bg-secondary px-3 py-0.5 text-xs">{c}</span>)}</div>
          <p className="text-muted-foreground">{a.synopsis}</p>
          <div className="flex flex-wrap gap-2">
            {(resume || eps[0]) && <Link href={resume ? watchHref(resume.p, resume.t) : watchHref(play(eps[0]))}><Button size="lg" className="beam h-11 rounded-full px-6"><Play />{resume ? `Resume episode ${resume.p.ep}` : "Play episode 1"}</Button></Link>}
            <FavButton c={{ ...animeCard(a), href: `/anime/${slug}`, sub: a.type }} />
          </div>
          {a.seasons.length > 0 && <div className="flex flex-wrap gap-2">{a.seasons.map((x: any) => <Link key={x.slug} href={`/anime/${x.slug}`} className={`beam rounded-full px-3 py-1 text-sm ${x.slug === slug ? "bg-primary text-primary-foreground" : "bg-secondary"}`}>{x.title}</Link>)}</div>}
        </div>
      </div>
      <section>
        <div className="mb-3 flex items-center gap-3"><h2 className="font-display text-xl font-semibold">Episodes</h2>
          <div role="group" aria-label="Audio" className="flex gap-1">{["sub", "dub"].filter((l) => l === "sub" || a.dub).map((l) => <Button key={l} size="sm" variant={lang === l ? "default" : "secondary"} className="rounded-full" onClick={() => setLang(l)}>{l === "sub" ? "Sub" : "Dub"}</Button>)}</div></div>
        {e.loading && <p className="text-muted-foreground">Loading episodes…</p>}
        <div className="grid grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))] gap-2">
          {eps.map((x) => <Link key={x.id} href={watchHref(play(x))} title={x.title} className="beam grid h-11 place-items-center rounded-xl bg-card text-sm">{x.number}</Link>)}
        </div>
      </section>
      <Rail title="More like this" items={a.recommendations.map(animeCard).filter((c: any) => !s.profile?.kids || kids.animeCard(c))} />
    </main>
  );
}
