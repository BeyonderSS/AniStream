"use client";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Input } from "@/components/ui/input";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { animeCard, Grid, mediaCard } from "@/components/app";
import { kids } from "@/lib/core";
import { useApi, useSession } from "@/lib/session";

function Search() {
  const q = useSearchParams(), router = useRouter(), { s } = useSession();
  const term = q.get("q") ?? "", tab = q.get("t") === "movies" ? "movies" : "anime";
  const [text, setText] = useState(term);
  const { data, loading, error } = useApi(term ? `${tab === "anime" ? "anime" : "movies"}/search?q=${encodeURIComponent(term)}` : null);
  const go = (t: string, x = term) => router.replace(`/search?q=${encodeURIComponent(x)}&t=${t}`);
  const k = s.profile?.kids;
  const items = ((data ?? []) as any[]).map(tab === "anime" ? animeCard : mediaCard).filter((c) => !k || (c.kind === "anime" ? kids.animeCard(c) : kids.media(c.genres)));
  return (
    <main className="space-y-6 px-4 pb-16 md:px-10">
      <form onSubmit={(e) => { e.preventDefault(); go(tab, text); }} role="search">
        <Input aria-label="Search" autoFocus value={text} onChange={(e) => setText(e.target.value)} placeholder="Search anime, movies and series" className="h-12 rounded-full px-5 text-base" />
      </form>
      <Tabs value={tab} onValueChange={(v) => go(v as string)}>
        <TabsList><TabsTrigger value="anime">Anime</TabsTrigger><TabsTrigger value="movies">Movies &amp; TV</TabsTrigger></TabsList>
      </Tabs>
      {loading && <p className="text-muted-foreground">Searching…</p>}
      {error && <p role="alert" className="text-destructive">{error}</p>}
      {term && !loading && !error && !items.length && <p className="text-muted-foreground">Nothing found for “{term}”.</p>}
      <Grid items={items} />
    </main>
  );
}
export default function Page() { return <Suspense><Search /></Suspense>; }
