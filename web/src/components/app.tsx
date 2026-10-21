"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Search, Tv, Library, CalendarDays, Home, Play, Check, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { avatarUrl, continueWatching, EMOJI_GROUPS, getFavs, kids, toggleFav, watchHref, type CardData } from "@/lib/core";
import { useApi, useSession } from "@/lib/session";

export const Avatar = ({ id, size = 36 }: { id: string; size?: number }) => (
  // eslint-disable-next-line @next/next/no-img-element
  <img src={avatarUrl(id)} alt="" width={size} height={size} className="rounded-full bg-secondary object-cover" style={{ width: size, height: size }} />
);

const LINKS = [["/", "Home", Home], ["/search", "Search", Search], ["/live", "Live TV", Tv], ["/schedule", "Schedule", CalendarDays], ["/library", "Library", Library]] as const;

export function Nav() {
  const path = usePathname();
  const { s } = useSession();
  if (["/welcome", "/signin", "/signup", "/profiles", "/watch"].some((p) => path.startsWith(p)) || !s.profile) return null;
  return (
    <header className="sticky top-0 z-30 flex items-center gap-4 bg-gradient-to-b from-background to-transparent px-4 py-3 md:px-10">
      <Link href="/" className="font-display text-2xl font-bold text-primary">Reelm</Link>
      <nav className="no-scrollbar flex flex-1 gap-1 overflow-x-auto">
        {LINKS.map(([h, label, Icon]) => (
          <Link key={h} href={h} aria-label={label} className={`beam flex items-center gap-2 rounded-full px-3 py-1.5 text-sm ${path === h ? "bg-secondary text-foreground" : "text-muted-foreground"}`}>
            <Icon className="size-4" /><span className="hidden sm:inline">{label}</span>
          </Link>
        ))}
      </nav>
      <Link href="/profiles" aria-label="Switch profile" className="beam rounded-full"><Avatar id={s.profile.avatar} /></Link>
    </header>
  );
}

export function PosterCard({ c, wide }: { c: CardData; wide?: boolean }) {
  return (
    <Link href={c.href} className={`beam group block shrink-0 rounded-xl ${wide ? "w-60" : "w-36 sm:w-44"}`}>
      <div className={`relative overflow-hidden rounded-xl bg-card ${wide ? "aspect-video" : "aspect-[2/3]"} transition-transform duration-200 group-hover:scale-[1.04]`}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        {c.poster && <img src={c.poster} alt="" loading="lazy" referrerPolicy="no-referrer" className="size-full object-cover" />}
        {c.rating && <span className="absolute left-2 top-2 rounded-full bg-background/80 px-2 py-0.5 text-xs">{c.rating}</span>}
      </div>
      <p className="mt-2 line-clamp-2 text-sm font-medium">{c.title}</p>
      {c.sub && <p className="text-xs text-muted-foreground">{c.sub}</p>}
    </Link>
  );
}

export function Rail({ title, items, wide }: { title: string; items: CardData[]; wide?: boolean }) {
  if (!items.length) return null;
  return (
    <section className="mt-8">
      <h2 className="mb-3 px-4 font-display text-xl font-semibold md:px-10">{title}</h2>
      <div className="no-scrollbar flex gap-3 overflow-x-auto px-4 pb-4 md:px-10">{items.map((c) => <PosterCard key={c.href} c={c} wide={wide} />)}</div>
    </section>
  );
}

export const Grid = ({ items }: { items: CardData[] }) => (
  <div className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-7">{items.map((c) => <PosterCard key={c.href} c={c} />)}</div>
);

/** A rail that loads itself; `map` turns the API payload into cards (kids filter applied here). */
export function Row({ title, path, map }: { title: string; path: string; map: (d: any) => CardData[] }) {
  const { s } = useSession();
  const { data, loading, error } = useApi(path);
  if (loading) return <div className="mt-8 px-4 md:px-10"><div className="mb-3 h-6 w-40 animate-pulse rounded bg-card" /><div className="h-56 animate-pulse rounded-xl bg-card" /></div>;
  if (error || !data) return null;
  const k = s.profile?.kids;
  return <Rail title={title} items={map(data).filter((c) => !k || (c.kind === "anime" ? kids.animeCard(c) : kids.media(c.genres)))} />;
}

export const animeCard = (a: any): CardData => ({ href: `/anime/${a.slug}`, title: a.title, poster: a.poster, sub: a.type, rating: a.rating, kind: "anime" });
export const mediaCard = (m: any): CardData => ({ href: `/title/${encodeURIComponent(m.id)}?type=${m.type}`, title: m.title, poster: m.poster, sub: [m.year, m.type === "series" ? "Series" : "Movie"].filter(Boolean).join(" · "), genres: m.genres, kind: "media" });

export function ContinueRow() {
  const { s } = useSession();
  const [items, setItems] = useState<CardData[]>([]);
  useEffect(() => {
    if (!s.profile) return;
    setItems(continueWatching(s.profile.id).map((e) => ({
      href: watchHref(e.payload, e.position), title: e.payload.title, poster: e.payload.poster,
      sub: e.payload.kind === "anime" ? `Episode ${e.payload.ep}` : e.payload.season ? `S${e.payload.season} E${e.payload.episode}` : "Movie",
    })));
  }, [s.profile]);
  return <Rail title="Continue watching" items={items} wide />;
}

export function FavButton({ c }: { c: CardData }) {
  const [on, setOn] = useState(false);
  useEffect(() => setOn(getFavs().some((f) => f.href === c.href)), [c.href]);
  return <Button variant="secondary" size="lg" onClick={() => setOn(toggleFav(c))}>{on ? <Check /> : <Plus />}{on ? "In library" : "Add to library"}</Button>;
}

export function AvatarPicker({ value, onChange }: { value: string; onChange: (id: string) => void }) {
  return (
    <div className="space-y-3">
      {EMOJI_GROUPS.map(([g, ids]) => (
        <div key={g}>
          <p className="mb-1 text-sm text-muted-foreground">{g}</p>
          <div className="flex flex-wrap gap-2">
            {ids.map((id) => (
              <button key={id} type="button" aria-label={id.replace(/_/g, " ")} aria-pressed={value === id} onClick={() => onChange(id)}
                className={`beam rounded-full p-1 ${value === id ? "bg-primary/25 ring-2 ring-primary" : ""}`}><Avatar id={id} size={48} /></button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export function Hero({ item, poster, subtitle, desc, href }: { item: string; poster?: string; subtitle?: string; desc?: string; href: string }) {
  return (
    <section className="relative -mt-16 min-h-[62vh] overflow-hidden">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      {poster && <img src={poster} alt="" referrerPolicy="no-referrer" className="absolute inset-0 size-full object-cover object-top opacity-60" />}
      <div className="absolute inset-0 bg-gradient-to-t from-background via-background/50 to-background/10" />
      <div className="absolute inset-0 bg-gradient-to-r from-background/90 to-transparent" />
      <div className="relative flex min-h-[62vh] max-w-2xl flex-col justify-end gap-3 px-4 pb-10 md:px-10">
        <h1 className="font-display text-4xl font-bold md:text-6xl">{item}</h1>
        {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
        {desc && <p className="line-clamp-3 text-muted-foreground">{desc}</p>}
        <div><Link href={href}><Button size="lg" className="beam h-11 rounded-full px-6 text-base"><Play />Watch now</Button></Link></div>
      </div>
    </section>
  );
}
