"use client";
import { useEffect, useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Grid } from "@/components/app";
import { getFavs, getHistory, watchHref, type CardData } from "@/lib/core";
import { useSession } from "@/lib/session";

export default function Library() {
  const { s } = useSession();
  const [favs, setFavs] = useState<CardData[]>([]), [hist, setHist] = useState<CardData[]>([]);
  useEffect(() => {
    setFavs(getFavs());
    setHist(Object.values(getHistory(s.profile?.id ?? "guest")).filter((e) => !e.deleted).sort((a, b) => b.updatedAt - a.updatedAt).map((e) => ({
      href: watchHref(e.payload, e.position), title: e.payload.title, poster: e.payload.poster,
      sub: e.payload.kind === "anime" ? `Episode ${e.payload.ep}` : e.payload.season ? `S${e.payload.season} E${e.payload.episode}` : "Movie",
    })));
  }, [s.profile]);
  return (
    <main className="space-y-5 px-4 pb-16 md:px-10">
      <h1 className="font-display text-3xl font-bold">Library</h1>
      <Tabs defaultValue="fav">
        <TabsList><TabsTrigger value="fav">Favorites</TabsTrigger><TabsTrigger value="hist">History</TabsTrigger></TabsList>
        <TabsContent value="fav" className="pt-4">{favs.length ? <Grid items={favs} /> : <p className="text-muted-foreground">Nothing saved yet.</p>}</TabsContent>
        <TabsContent value="hist" className="pt-4">{hist.length ? <Grid items={hist} /> : <p className="text-muted-foreground">Your watch history will show up here.</p>}
          {s.token && <p className="mt-4 text-xs text-muted-foreground">History syncs with your account across devices.</p>}</TabsContent>
      </Tabs>
    </main>
  );
}
