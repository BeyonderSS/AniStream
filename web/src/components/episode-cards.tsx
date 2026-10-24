"use client";
import Link from "next/link";
import { useState } from "react";
import { Check, Play, Star } from "lucide-react";
import { BlurFade } from "@/components/ui/blur-fade";
import { cn } from "@/lib/utils";

export type EpInfo = { n: number; title: string; overview?: string; still?: string; air?: string; runtime?: number; rating?: number };
export type SeasonInfo = { name?: string; overview?: string; poster?: string; episodes: EpInfo[] };
type Row = { href: string; progress?: number; current?: boolean };

const date = (d?: string) => d ? new Date(d + "T00:00").toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" }) : undefined;

export const EpisodeCardsSkeleton = () => (
  <div className="space-y-3" aria-label="Loading episodes">
    {[...Array(5)].map((_, i) => (
      <BlurFade key={i} delay={i * 0.05} className="flex gap-4 rounded-2xl bg-slate p-3">
        <div className="skel aspect-video w-40 shrink-0 rounded-xl md:w-56" />
        <div className="flex-1 space-y-3 py-1"><div className="skel h-5 w-1/2 rounded-lg" /><div className="skel h-3.5 w-1/3 rounded-lg" /><div className="skel h-10 w-full rounded-lg" /></div>
      </BlurFade>
    ))}
  </div>
);

function Card({ e, r, i }: { e: EpInfo; r: Row; i: number }) {
  const [open, setOpen] = useState(false);
  const done = (r.progress ?? 0) >= 0.9;
  const meta = [date(e.air), e.runtime && `${e.runtime} min`].filter(Boolean).join(" · ");
  return (
    <BlurFade delay={Math.min(i, 8) * 0.04} className={cn("flex gap-3 rounded-2xl bg-slate p-3 md:gap-4", r.current && "ring-1 ring-primary/60")}>
        <Link href={r.href} aria-label={`Play episode ${e.n}`} className="beam group relative aspect-video w-36 shrink-0 self-start overflow-hidden rounded-xl bg-haze sm:w-48 md:w-60">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          {e.still ? <img src={e.still} alt="" loading="lazy" className="size-full object-cover transition-transform duration-300 group-hover:scale-105" /> : <span className="grid size-full place-items-center font-display text-3xl text-foreground/30">{e.n}</span>}
          <span className="absolute inset-0 grid place-items-center bg-black/0 transition-colors group-hover:bg-black/40"><Play className="size-8 fill-white text-white opacity-0 drop-shadow transition-opacity group-hover:opacity-100" /></span>
          {/* progress slot: bar and completed check overlay live here */}
          {done && <span className="absolute top-2 right-2 grid size-6 place-items-center rounded-full bg-signal text-dusk"><Check className="size-3.5" /></span>}
          {r.progress != null && r.progress > 0.02 && !done && <span className="absolute inset-x-0 bottom-0 h-1 bg-black/50"><span className="block h-full bg-primary" style={{ width: `${r.progress * 100}%` }} /></span>}
        </Link>
        <div className="min-w-0 flex-1 py-0.5">
          <div className="flex items-baseline gap-2">
            <Link href={r.href} className="beam min-w-0 flex-1 truncate font-display font-semibold hover:text-primary">E{e.n} · {e.title}</Link>
            {e.rating != null && <span className="flex shrink-0 items-center gap-1 text-sm text-foreground/72"><Star className="size-3.5 fill-primary text-primary" />{e.rating}</span>}
          </div>
          {meta && <p className="mt-0.5 text-xs text-foreground/52">{meta}</p>}
          {e.overview && <button type="button" aria-expanded={open} onClick={() => setOpen(!open)} className={cn("beam mt-2 block text-left text-sm leading-relaxed text-foreground/72", !open && "line-clamp-2 sm:line-clamp-3")}>{e.overview}</button>}
        </div>
    </BlurFade>
  );
}

/** Season header (poster + overview) over one rich card per episode; `rows` carries per-episode link/progress/selected state by episode number. */
export function EpisodeCards({ season, rows }: { season: SeasonInfo; rows: (n: number) => Row }) {
  return (
    <div className="space-y-5">
      {(season.overview || season.poster) && (
        <BlurFade className="flex gap-4">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          {season.poster && <img src={season.poster} alt="" className="hidden w-24 shrink-0 self-start rounded-lg ring-1 ring-white/10 sm:block" />}
          <div className="max-w-prose space-y-1">
            {season.name && <h3 className="font-display text-lg font-semibold">{season.name}</h3>}
            {season.overview && <p className="line-clamp-4 text-sm leading-relaxed text-foreground/72">{season.overview}</p>}
          </div>
        </BlurFade>
      )}
      <div className="space-y-3">{season.episodes.map((e, i) => <Card key={e.n} e={e} r={rows(e.n)} i={i} />)}</div>
    </div>
  );
}
