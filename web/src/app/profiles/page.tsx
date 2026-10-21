"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Pencil, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { GUEST, reelm, syncHistory, type Profile } from "@/lib/core";
import { useSession } from "@/lib/session";
import { Avatar, AvatarPicker } from "@/components/app";

export default function Profiles() {
  const { s, set, ready } = useSession(), router = useRouter();
  const [list, setList] = useState<Profile[]>([]), [manage, setManage] = useState(false), [edit, setEdit] = useState<Partial<Profile> | null>(null), [err, setErr] = useState("");
  const load = () => s.token && reelm("/v1/profiles", { token: s.token }).then((d) => setList(d.profiles)).catch((e) => setErr(e.message));
  useEffect(() => { if (ready) s.token ? void load() : setList([GUEST]); }, [ready, s.token]); // eslint-disable-line react-hooks/exhaustive-deps
  const pick = (p: Profile) => { set({ ...s, profile: p }); if (s.token) syncHistory(p.id, s.token).catch(() => {}); router.replace("/"); };
  async function save() {
    if (!edit?.name?.trim() || !s.token) return;
    const body = { name: edit.name.trim(), avatar: edit.avatar ?? "grinning_face", kids: !!edit.kids };
    try { await (edit.id ? reelm(`/v1/profiles/${edit.id}`, { method: "PATCH", token: s.token, json: body }) : reelm("/v1/profiles", { token: s.token, json: body })); setEdit(null); await load(); }
    catch (e) { setErr((e as Error).message); }
  }
  async function del() {
    try { await reelm(`/v1/profiles/${edit!.id}`, { method: "DELETE", token: s.token }); setEdit(null); await load(); } catch (e) { setErr((e as Error).message); }
  }
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-8 px-6 py-10">
      <h1 className="font-display text-4xl font-bold">{manage ? "Manage profiles" : "Who's watching?"}</h1>
      <div className="flex flex-wrap justify-center gap-6">
        {list.map((p) => (
          <button key={p.id} onClick={() => (manage ? setEdit(p) : pick(p))} className="beam group flex w-28 flex-col items-center gap-2 rounded-2xl p-2">
            <span className="relative"><Avatar id={p.avatar} size={96} />{manage && <Pencil className="absolute inset-0 m-auto size-6 rounded-full bg-background/80 p-1" />}</span>
            <span className="text-sm">{p.name}</span>{p.kids && <span className="rounded-full bg-secondary px-2 text-xs">Kids</span>}
          </button>
        ))}
        {manage && s.token && list.length < 5 && (
          <button onClick={() => setEdit({ name: "", avatar: "cat_face", kids: false })} className="beam flex w-28 flex-col items-center gap-2 rounded-2xl p-2">
            <span className="grid size-24 place-items-center rounded-full bg-secondary"><Plus /></span><span className="text-sm">Add profile</span>
          </button>
        )}
      </div>
      {err && <p role="alert" className="text-destructive">{err}</p>}
      <div className="flex gap-2">
        {s.token && <Button variant="outline" className="rounded-full" onClick={() => setManage(!manage)}>{manage ? "Done" : "Manage profiles"}</Button>}
        <Button variant="ghost" className="rounded-full" onClick={() => { set({}); router.replace("/welcome"); }}>{s.token ? "Sign out" : "Leave guest mode"}</Button>
      </div>
      <Dialog open={!!edit} onOpenChange={(o) => !o && setEdit(null)}>
        <DialogContent>
          <DialogHeader><DialogTitle>{edit?.id ? "Edit profile" : "New profile"}</DialogTitle></DialogHeader>
          <Input aria-label="Name" placeholder="Name" value={edit?.name ?? ""} maxLength={24} onChange={(e) => setEdit({ ...edit, name: e.target.value })} />
          <AvatarPicker value={edit?.avatar ?? "cat_face"} onChange={(avatar) => setEdit({ ...edit, avatar })} />
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={!!edit?.kids} onChange={(e) => setEdit({ ...edit, kids: e.target.checked })} />Kids profile (filters anime, movies and live TV)</label>
          <div className="flex justify-between">
            {edit?.id && list.length > 1 ? <Button variant="destructive" onClick={del}>Delete</Button> : <span />}
            <Button onClick={save} disabled={!edit?.name?.trim()}>Save</Button>
          </div>
        </DialogContent>
      </Dialog>
    </main>
  );
}
