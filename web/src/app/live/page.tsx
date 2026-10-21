"use client";
import { useState } from "react";
import Link from "next/link";
import { Input } from "@/components/ui/input";
import { watchHref } from "@/lib/core";
import { useApi, useSession } from "@/lib/session";

export default function Live() {
  const { s } = useSession();
  const [q, setQ] = useState(""), [group, setGroup] = useState("");
  const { data, loading, error } = useApi(`live?q=${encodeURIComponent(q)}&group=${encodeURIComponent(group)}${s.profile?.kids ? "&kids=1" : ""}`);
  const [groups, setGroups] = useState<string[]>([]);
  if (data?.groups && !groups.length) setGroups(data.groups);
  return (
    <main className="space-y-5 px-4 pb-16 md:px-10">
      <h1 className="font-display text-3xl font-bold">Live TV</h1>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Input aria-label="Search channels" placeholder="Search channels" value={q} onChange={(e) => setQ(e.target.value)} className="h-11 sm:max-w-sm" />
        <select aria-label="Category" value={group} onChange={(e) => setGroup(e.target.value)} className="h-11 rounded-lg border border-input bg-card px-3 text-sm">
          <option value="">All categories</option>{groups.map((g) => <option key={g}>{g}</option>)}
        </select>
      </div>
      {error && <p role="alert" className="text-destructive">{error}</p>}
      {loading && !data && <p className="text-muted-foreground">Loading channels…</p>}
      {data && <p className="text-sm text-muted-foreground">{data.total} channels{data.total > 120 ? ", showing 120. Search to narrow down." : ""}</p>}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-5 lg:grid-cols-6">
        {data?.channels.map((c: any) => (
          <Link key={c.url} href={watchHref({ kind: "live", id: c.url, title: c.name, poster: c.logo, url: c.url })} className="beam flex flex-col items-center gap-2 rounded-xl bg-card p-3 text-center">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <div className="grid h-16 w-full place-items-center">{c.logo ? <img src={c.logo} alt="" loading="lazy" className="max-h-16 max-w-full object-contain" /> : <span className="text-2xl">📺</span>}</div>
            <span className="line-clamp-2 text-sm">{c.name}</span><span className="text-xs text-muted-foreground">{c.group}</span>
          </Link>
        ))}
      </div>
    </main>
  );
}
