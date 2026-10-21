"use client";
import Link from "next/link";
import type { ReactNode } from "react";
import { ArrowLeft } from "lucide-react";
import { Avatar } from "@/components/app";
import { ReelMark } from "@/components/reel";
import { cn } from "@/lib/utils";

/** Centered column under the projector lamp, shared by the sign-in, sign-up and profile screens. */
export function AuthShell({ children, back, wide }: { children: ReactNode; back?: string | (() => void); wide?: boolean }) {
  return (
    <main className="lamp relative flex min-h-dvh flex-col px-5 pt-[max(env(safe-area-inset-top),12px)] pb-8">
      <div className="flex h-14 items-center">
        {back && (typeof back === "string"
          ? <Link href={back} aria-label="Back" className="beam grid size-11 place-items-center rounded-full hover:bg-white/8"><ArrowLeft /></Link>
          : <button onClick={back} aria-label="Back" className="beam grid size-11 place-items-center rounded-full hover:bg-white/8"><ArrowLeft /></button>)}
        <div className="flex-1" /><span className="flex items-center gap-1.5 font-display text-lg font-bold"><ReelMark className="size-5" />Reelm</span>
      </div>
      <div className={cn("mx-auto flex w-full flex-1 flex-col justify-center py-6", wide ? "max-w-2xl" : "max-w-sm")}>{children}</div>
    </main>
  );
}

export const StepBar = ({ step, of = 3 }: { step: number; of?: number }) => (
  <div className="mb-6 flex gap-1.5" role="progressbar" aria-valuemin={1} aria-valuemax={of} aria-valuenow={step + 1} aria-label={`Step ${step + 1} of ${of}`}>
    {[...Array(of)].map((_, i) => <span key={i} className={cn("h-1 flex-1 rounded-full transition-colors duration-300", i <= step ? "bg-primary" : "bg-white/12")} />)}
  </div>
);

export function Field({ label, hint, error, ...p }: React.InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }) {
  const id = `f-${label.replace(/\W/g, "").toLowerCase()}`;
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="text-sm font-medium text-foreground/72">{label}</label>
      <input id={id} aria-invalid={!!error || undefined} aria-describedby={`${id}-h`} {...p}
        className="beam h-12 w-full rounded-2xl bg-slate px-4 text-base outline-none ring-1 ring-white/8 placeholder:text-foreground/32 focus-visible:bg-haze aria-invalid:ring-destructive/70" />
      <p id={`${id}-h`} className={cn("min-h-4 text-xs", error ? "text-destructive" : "text-foreground/52")}>{error || hint}</p>
    </div>
  );
}

const CLUSTER = ["cat_face", "star-struck", "popcorn", "fox", "smiling_face_with_sunglasses"];
/** Five avatars on a shallow arc, the middle one lit (as on Android). */
export const AvatarCluster = () => (
  <div className="flex items-start justify-center -space-x-2.5">
    {CLUSTER.map((id, i) => (
      <span key={id} style={{ marginTop: [28, 10, 0, 10, 28][i] }}><Avatar id={id} size={[52, 68, 104, 68, 52][i]} ring={i === 2} /></span>
    ))}
  </div>
);
