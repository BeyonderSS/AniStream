import type { Metadata } from "next";
import { Bricolage_Grotesque, Figtree } from "next/font/google";
import "./globals.css";
import { SessionProvider } from "@/lib/session";
import { Nav } from "@/components/app";

const display = Bricolage_Grotesque({ variable: "--font-bricolage", subsets: ["latin"] });
const text = Figtree({ variable: "--font-figtree", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Reelm", description: "Anime, movies, series and live TV in one place.",
  referrer: "no-referrer", // the MovieBox CDN answers 429 to any Referer
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`dark ${display.variable} ${text.variable} h-full antialiased`}>
      <body className="min-h-full bg-background font-sans text-foreground">
        <SessionProvider><Nav />{children}</SessionProvider>
      </body>
    </html>
  );
}
