"use client";
import type { ReactNode } from "react";
import Link from "next/link";
import { Marquee } from "@/components/app";
import { cn } from "@/lib/utils";

/** Show page marquee (D1): the backdrop stretched into a wash, the name huge on the left, the whole poster tilted on the right. */
export function DetailHero({ wash, poster, title, children }: { wash?: string; poster?: string; title: string; children: ReactNode }) {
  return (
    <section className="relative -mt-16 overflow-hidden md:-mt-20">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      {wash && <img src={wash} alt="" aria-hidden referrerPolicy="no-referrer" className="absolute inset-0 size-full scale-110 object-cover object-[50%_20%] opacity-60 blur-2xl saturate-125" />}
      <div className="absolute inset-0 bg-gradient-to-t from-dusk via-dusk/50 to-dusk/10" />
      <div className="absolute inset-0 bg-gradient-to-r from-dusk/95 via-dusk/60 to-transparent" />
      <div className="relative mx-auto grid max-w-[1500px] grid-cols-[minmax(0,1fr)_auto] items-start gap-5 px-4 pt-24 pb-8 md:gap-12 md:px-10 md:pt-32 md:pb-10">
        <div className="flex min-w-0 flex-col gap-4">
          <Marquee text={title} className="text-[clamp(40px,6.6vw,112px)]" />
          {children}
        </div>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        {poster && <img src={poster} alt="" referrerPolicy="no-referrer" className="page-in aspect-[2/3] w-24 rotate-[4deg] rounded-xl object-cover shadow-[0_30px_70px_rgb(0_0_0/0.6)] ring-1 ring-white/10 sm:w-36 md:w-[clamp(170px,15vw,240px)] md:-rotate-3" />}
      </div>
    </section>
  );
}

/** Seasons as marquee numerals: the others in outline, the current one lit amber. */
export function SeasonNumerals({ items, cur, onPick }: { items: { key: string | number; label: ReactNode; name: string; on: boolean; href?: string }[]; cur?: string; onPick?: (k: string | number) => void }) {
  return (
    <nav aria-label="Seasons" className="flex flex-wrap items-baseline gap-x-5 gap-y-1 font-display font-extrabold">
      {items.map((x) => {
        const cls = cn("beam rounded-md leading-none transition-colors", x.on ? "text-[clamp(34px,3.6vw,52px)] text-primary" : "hollow text-[clamp(26px,2.6vw,38px)] hover:text-foreground/20");
        return x.href
          ? <Link key={x.key} href={x.href} aria-label={x.name} title={x.name} aria-current={x.on ? "page" : undefined} className={cls}>{x.label}</Link>
          : <button key={x.key} type="button" aria-pressed={x.on} aria-label={x.name} onClick={() => onPick?.(x.key)} className={cls}>{x.label}</button>;
      })}
      {cur && <span className="font-sans text-sm font-medium text-[#a3a1ae]">{cur}</span>}
    </nav>
  );
}

export const DetailSkeleton = () => (
  <div className="mx-auto max-w-[1500px] px-4 pt-12 md:px-10" aria-label="Loading">
    <div className="flex gap-6">
      <div className="flex-1 space-y-4"><div className="skel h-24 w-2/3 rounded-xl" /><div className="skel h-7 w-1/2 rounded-full" /><div className="skel h-14 w-full max-w-xl rounded-xl" /><div className="skel h-11 w-44 rounded-full" /></div>
      <div className="skel aspect-[2/3] w-24 -rotate-3 rounded-xl md:w-52" />
    </div>
    <div className="mt-12 flex gap-4 overflow-hidden">{[...Array(6)].map((_, i) => <div key={i} className="skel aspect-[4/3.4] w-60 shrink-0 rounded-xl" />)}</div>
  </div>
);
