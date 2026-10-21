"use client";
import Link from "next/link";
import { useApi } from "@/lib/session";

export default function Schedule() {
  const { data, loading, error } = useApi<any[]>("anime/schedule");
  return (
    <main className="space-y-6 px-4 pb-16 md:px-10">
      <h1 className="font-display text-3xl font-bold">Airing this week</h1>
      {loading && <p className="text-muted-foreground">Loading…</p>}
      {error && <p role="alert" className="text-destructive">{error}</p>}
      {data?.map((d) => (
        <section key={d.date}>
          <h2 className="mb-2 font-display text-xl font-semibold">{d.day} <span className="text-sm font-normal text-muted-foreground">{d.date}</span></h2>
          <ul className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {d.items.map((i: any) => (
              <li key={i.slug + i.time}><Link href={`/anime/${i.slug}`} className="beam flex justify-between gap-3 rounded-xl bg-card px-4 py-3 text-sm">
                <span className="line-clamp-1">{i.title}</span><span className="shrink-0 text-muted-foreground">{i.episode ? `Ep ${i.episode} · ` : ""}{i.time}</span></Link></li>
            ))}
          </ul>
        </section>
      ))}
    </main>
  );
}
