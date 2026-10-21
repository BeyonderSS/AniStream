import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Reelm",
    short_name: "Reelm",
    description: "Anime, movies, series and live TV.",
    start_url: "/",
    display: "standalone",
    background_color: "#15171F",
    theme_color: "#15171F",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
    ],
  };
}
