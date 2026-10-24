"use client";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { Check, Star } from "lucide-react";
import { DONE } from "@/lib/eps";

export type EpInfo = { n: number; title: string; overview?: string; still?: string; air?: string; runtime?: number; rating?: number };
export type SeasonInfo = { name?: string; overview?: string; poster?: string; episodes: EpInfo[] };
export type EpRow = { n: number; href: string; info?: Partial<EpInfo>; progress?: number; current?: boolean };

const date = (d?: string) => d ? new Date(d + "T00:00").toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" }) : undefined;

export const EpisodeStripSkeleton = () => (
  <div className="strip" aria-label="Loading episodes"><ol>{[...Array(6)].map((_, i) => <li key={i} className="skel aspect-[4/3.4] w-60 shrink-0 rounded-xl" />)}</ol></div>
);

/** Episodes as stubs with their stills on the 35mm strip: ✓ when watched, minutes left when started; hover or focus opens the synopsis. Scrolls to the episode you're on. */
export function EpisodeStrip({ items }: { items: EpRow[] }) {
  const ref = useRef<HTMLOListElement>(null);
  const cur = items.find((x) => x.current)?.n;
  useEffect(() => {
    const ol = ref.current, li = ol?.querySelector<HTMLElement>("[data-current]");
    if (ol && li) ol.scrollLeft = li.offsetLeft - ol.clientWidth / 2 + li.clientWidth / 2;
  }, [cur, items.length]);
  if (!items.length) return <p className="px-4 text-sm text-foreground/60 md:px-10">No episodes are out yet.</p>;
  return (
    <div className="strip">
      <ol ref={ref}>
        {items.map(({ n, href, info: e = {}, progress = 0, current }) => {
          const done = progress >= DONE, title = e.title && !/^episode \d+$/i.test(e.title) ? e.title : `Episode ${n}`;
          const left = e.runtime && progress > 0.02 && !done ? Math.max(1, Math.round(e.runtime * (1 - progress))) : 0;
          const meta = [e.runtime && `${e.runtime} min`, left && `${left} min left`, !left && date(e.air)].filter(Boolean).join(", ");
          return (
            <li key={n} className="epstub group" data-current={current || undefined}>
              <div className="relative aspect-video bg-haze">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                {e.still ? <img src={e.still} alt="" loading="lazy" className="size-full object-cover" /> : <span className="grid size-full place-items-center font-display text-5xl font-extrabold text-foreground/25">{n}</span>}
                <span className="absolute top-2 left-2 rounded-full bg-[#0b0c11]/70 px-2.5 py-0.5 font-display text-sm font-extrabold">E{n}</span>
                {done && <span aria-label="Watched" className="absolute top-2 right-2 grid size-7 place-items-center rounded-full bg-signal text-dusk"><Check className="size-4" strokeWidth={3} /></span>}
              </div>
              <div className="flex flex-1 flex-col gap-1 border-t-2 border-dashed border-white/30 px-3.5 pt-2.5 pb-3">
                <b className="line-clamp-1 font-display text-[17px] leading-tight font-bold">{title}</b>
                <span className="flex items-center gap-2 text-xs text-[#bdb9c9]">{meta}{e.rating ? <span className="ml-auto inline-flex items-center gap-1"><Star className="size-3 fill-primary text-primary" />{e.rating}</span> : null}</span>
                {e.overview && <p className="line-clamp-3 text-xs leading-snug text-[#cfcbd9] md:hidden md:group-hover:block md:group-focus-within:block">{e.overview}</p>}
                {progress > 0.02 && !done && <div className="track-bar mt-1"><i style={{ width: `${progress * 100}%` }} /></div>}
              </div>
              <Link href={href} aria-label={`Play episode ${n}: ${title}`} className="absolute inset-0 z-10 outline-none" />
            </li>
          );
        })}
      </ol>
    </div>
  );
}
