"use client";
import Link from "next/link";
import { useState } from "react";
import { Marquee, Notice } from "@/components/app";
import { useApi } from "@/lib/session";
import { cn } from "@/lib/utils";

export default function Schedule() {
  const { data, loading, error } = useApi<any[]>("anime/schedule");
  const [day, setDay] = useState(0);
  const cur = data?.[day];
  return (
    <main className="mx-auto max-w-5xl space-y-7 px-4 pt-6 pb-16 md:px-10 md:pt-10">
      <Marquee text="Airing this week" word className="text-[clamp(48px,7vw,104px)]" />
      {error && <Notice title="The schedule didn't load" body={error} />}
      <div role="tablist" aria-label="Day" className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4">
        {(data ?? [...Array(7)].map(() => null)).map((d, i) => d ? (
          <button key={d.date} role="tab" aria-selected={day === i} onClick={() => setDay(i)}
            className="beam relative flex shrink-0 flex-col items-center rounded-lg px-3 pt-1 pb-3 font-display">
            <span className={cn("text-2xl leading-none font-extrabold", day === i ? "text-primary" : "hollow")}>{i === 0 ? "Today" : d.day.slice(0, 3)}</span><span className="mt-1 font-sans text-xs text-foreground/52">{d.date.slice(5).replace("-", "/")}</span>
            {day === i && <span aria-hidden className="bulbs absolute inset-x-3 bottom-0" />}
          </button>
        ) : <span key={i} className="skel h-12 w-20 shrink-0 rounded-lg" />)}
      </div>
      {loading && <div className="space-y-2">{[...Array(6)].map((_, i) => <div key={i} className="skel h-14 rounded-xl" />)}</div>}
      {cur && (cur.items.length ? (
        <ol className="space-y-2">
          {cur.items.map((i: any) => (
            <li key={i.slug + i.time}>
              <Link href={`/anime/${i.slug}`} className="beam flex items-stretch overflow-hidden rounded-xl bg-gradient-to-br from-[#232130] to-[#121219] outline-offset-2 hover:outline-2 hover:outline-primary">
                <span className="grid w-24 shrink-0 place-items-center bg-white/4 py-3 font-display text-xl font-extrabold text-primary tabular-nums">{i.time}</span>
                <span className="flex min-w-0 flex-1 items-center gap-3 border-l-2 border-dashed border-white/20 px-4 py-3">
                  <span className="min-w-0 flex-1 truncate font-display text-[17px] font-bold">{i.title}</span>
                  {i.episode && <span className="shrink-0 text-sm text-[#bdb9c9]">Episode {i.episode}</span>}
                </span>
              </Link>
            </li>
          ))}
        </ol>
      ) : <Notice title="Nothing airs this day" />)}
    </main>
  );
}
