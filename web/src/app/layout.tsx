import type { Metadata, Viewport } from "next";
import { Bricolage_Grotesque, Figtree } from "next/font/google";
import "./globals.css";
import { SessionProvider } from "@/lib/session";
import { Nav, TabBar } from "@/components/app";
import { Motion } from "@/components/motion";
import { ReelMark } from "@/components/reel";

const display = Bricolage_Grotesque({ variable: "--font-bricolage", subsets: ["latin"] });
const text = Figtree({ variable: "--font-figtree", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Reelm", description: "Anime, movies, series and live TV in one place.",
  referrer: "no-referrer", // the MovieBox CDN answers 429 to any Referer
};
export const viewport: Viewport = { themeColor: "#15171F", viewportFit: "cover" };

/** First load of a browser session: the Android launch animation in CSS. Hidden before paint when already seen or with reduced motion. */
const SPLASH_GATE = `try{var d=document.documentElement;if(sessionStorage.getItem("reelm.splash")||matchMedia("(prefers-reduced-motion: reduce)").matches)d.dataset.splash="off";else{sessionStorage.setItem("reelm.splash","1");setTimeout(function(){d.dataset.splash="off"},1700)}}catch(e){}`;

function Splash() {
  return (
    <div id="splash" aria-hidden>
      <div className="glow" />
      <div className="stack">
        <ReelMark className="reel" />
        <div className="word">{[..."Reelm"].map((c, i) => <span key={i} style={{ animationDelay: `${383 + i * 62}ms` }}>{c}</span>)}<span className="sweep">Reelm</span></div>
      </div>
    </div>
  );
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning className={`dark ${display.variable} ${text.variable} h-full antialiased`}>
      <head><script dangerouslySetInnerHTML={{ __html: SPLASH_GATE }} /></head>
      <body className="min-h-full bg-background font-sans text-foreground">
        <Splash />
        <Motion><SessionProvider><Nav />{children}<TabBar /></SessionProvider></Motion>
      </body>
    </html>
  );
}
