# Reelm API (Cloudflare Worker)

First-time setup (contract: `API.md`):
1. `npm ci && npx wrangler login`
2. `npx wrangler d1 create reelm`, then paste the printed id into `database_id` in `wrangler.toml`.
3. `npx wrangler d1 migrations apply reelm --remote`
4. `npx wrangler deploy` (optional: put the assetlinks JSON string in `ASSET_LINKS` under `[vars]`).
5. GitHub repo secrets `CLOUDFLARE_API_TOKEN` (Workers + D1 edit) and `CLOUDFLARE_ACCOUNT_ID` enable deploy on push to main.
6. `npm test` runs the suite locally.
