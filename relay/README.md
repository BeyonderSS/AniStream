# Reelm MovieBox relay
MovieBox rejects requests from Cloudflare Workers (HTTP 440), so the web app sends its already-signed MovieBox calls here.
Deploy: `cd relay && vercel deploy --prod`, then set `RELAY_KEY` (any long random string) in the Vercel project.
In the web worker set `MOVIEBOX_RELAY=https://<project>.vercel.app/api/mb` and the same `RELAY_KEY` (`wrangler secret put RELAY_KEY`).
Only the MovieBox API hosts and `/wefeed-mobile-bff/` paths are forwarded; requests without the right `x-relay-key` get 401.
