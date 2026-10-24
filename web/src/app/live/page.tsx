"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { Search, Tv } from "lucide-react";
import { Marquee, Notice } from "@/components/app";
import { watchHref } from "@/lib/core";
import { useApi, useSession } from "@/lib/session";

export default function Live() {
  const { s } = useSession();
  const [text, setText] = useState(""), [q, setQ] = useState(""), [group, setGroup] = useState("");
  useEffect(() => { const t = setTimeout(() => setQ(text.trim()), 300); return () => clearTimeout(t); }, [text]);
  const { data, loading, error } = useApi(`live?q=${encodeURIComponent(q)}&group=${encodeURIComponent(group)}${s.profile?.kids ? "&kids=1" : ""}`);
  const [groups, setGroups] = useState<string[]>([]);
  useEffect(() => { if (data?.groups?.length && !groups.length) setGroups(data.groups); }, [data, groups.length]);
  return (
    <main className="mx-auto max-w-[1500px] space-y-6 px-4 pt-6 pb-16 md:px-10 md:pt-10">
      <Marquee text="Live TV" word className="text-[clamp(48px,7vw,112px)]" />
      <div className="relative max-w-md">
        <Search className="pointer-events-none absolute top-1/2 left-4 size-4 -translate-y-1/2 text-foreground/52" />
        <input aria-label="Search channels" placeholder="Search channels" value={text} onChange={(e) => setText(e.target.value)} className="beam h-11 w-full rounded-full bg-white/7 pr-4 pl-11 outline-none placeholder:text-foreground/40" />
      </div>
      {groups.length > 0 && (
        <div role="radiogroup" aria-label="Category" className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4">
          {["", ...groups].map((g) => (
            <button key={g || "all"} role="radio" aria-checked={group === g} onClick={() => setGroup(g)} className="beam opt solid shrink-0">{g || "All"}</button>
          ))}
        </div>
      )}
      {error && <Notice title="Channels didn't load" body={error} />}
      {data && <p className="text-sm text-foreground/52">{data.total} channels{data.total > 120 ? ". Showing the first 120, search to narrow down." : ""}</p>}
      <ol className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {loading && !data ? [...Array(12)].map((_, i) => <li key={i} className="skel h-24 rounded-xl" />) : data?.channels.map((c: any) => (
          <li key={c.url}>
            <Link href={watchHref({ kind: "live", id: c.url, title: c.name, poster: c.logo, url: c.url })} className="beam group flex h-24 overflow-hidden rounded-xl bg-gradient-to-br from-[#232130] to-[#121219] outline-offset-2 hover:outline-2 hover:outline-primary">
              <span className="grid w-28 shrink-0 place-items-center bg-white/4 p-3">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                {c.logo ? <img src={c.logo} alt="" loading="lazy" referrerPolicy="no-referrer" className="max-h-14 max-w-full object-contain" /> : <Tv className="size-8 text-foreground/32" />}
              </span>
              <span className="flex min-w-0 flex-1 flex-col justify-center gap-1 border-l-2 border-dashed border-white/20 px-4">
                <span className="line-clamp-2 font-display text-[17px] leading-tight font-bold">{c.name}</span>
                {c.group && <span className="line-clamp-1 text-xs text-[#bdb9c9]">{c.group}</span>}
              </span>
            </Link>
          </li>
        ))}
      </ol>
      {data && !data.channels.length && <Notice title="No channels match" body="Try another name or category." />}
    </main>
  );
}
