"use client";
import { animeCard, ContinueRow, Hero, mediaCard, Row } from "@/components/app";
import { useApi, useSession } from "@/lib/session";
import { kids } from "@/lib/core";

export default function HomePage() {
  const { s } = useSession();
  const { data } = useApi("anime/home");
  const hero = (data?.trending ?? []).find((a: any) => !s.profile?.kids || kids.animeCard(a)) ?? data?.trending?.[0];
  return (
    <main className="pb-16">
      {hero ? <Hero item={hero.title} poster={hero.poster} subtitle={hero.type ?? "Anime"} href={`/anime/${hero.slug}`} />
        : <div className="h-[40vh]" />}
      <ContinueRow />
      <Row title="Trending anime" path="anime/home" map={(d) => d.trending.map(animeCard)} />
      <Row title="Popular movies" path="movies/catalog?key=movie/top" map={(d) => d.map(mediaCard)} />
      <Row title="Popular series" path="movies/catalog?key=series/top" map={(d) => d.map(mediaCard)} />
      <Row title="Top airing" path="anime/home" map={(d) => d.topAiring.map(animeCard)} />
      <Row title="Most popular anime" path="anime/home" map={(d) => d.popular.map(animeCard)} />
      <Row title="Latest episodes" path="anime/home" map={(d) => d.recent.map(animeCard)} />
    </main>
  );
}
