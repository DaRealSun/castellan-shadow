# Deploy guide

## Right now: see it locally

The site runs on a production-equivalent Cloudflare worker preview at
http://localhost:8787 (currently running in a background terminal).

If it's not running, start it from `frontend/`:

```bash
cd frontend
npm run preview
```

That builds the site and serves it via `wrangler dev` — no Cloudflare auth needed,
no internet hop. Hit `http://localhost:8787/`, `/ctef/`, `/ctif/`, `/about/`.

## Deploy to Cloudflare (one-time setup, ~3 minutes)

### 1. Authenticate wrangler

```bash
cd frontend
npx wrangler login
```

This opens a browser window; click "Allow" on the OAuth screen. Done — credentials
cache in `~/.wrangler/config/default.toml` and the next deploy doesn't ask again.

### 2. Push the worker

```bash
npm run deploy
```

That runs `astro build` then `wrangler deploy`. On success it prints something like:

```
Published castellan-shadow (1.2 sec)
  https://castellan-shadow.<your-account>.workers.dev
```

That URL is live immediately. Open it.

### 3. (Optional) Custom subdomain

In the Cloudflare dashboard:

- Workers & Pages → `castellan-shadow` → Settings → Triggers
- "Add custom domain" → `castellan-shadow.minhsonle.sunhomelab.com`
- Cloudflare auto-creates the DNS record. SSL cert provisions in seconds.

Now the project is at `https://castellan-shadow.minhsonle.sunhomelab.com`.

## Push to GitHub

You'll need to install the GitHub CLI or use the web UI to create the repo:

**Option A — gh CLI (cleanest)**

```bash
winget install GitHub.cli   # or: scoop install gh
gh auth login
cd C:/Users/infol/IdeaProjects/castellan-shadow
gh repo create castellan-shadow --public --source=. --remote=origin --push
```

**Option B — manual**

1. Create an empty repo at https://github.com/new named `castellan-shadow`
2. Then locally:

```bash
cd C:/Users/infol/IdeaProjects/castellan-shadow
git remote add origin https://github.com/<your-username>/castellan-shadow.git
git branch -M main
git push -u origin main
```

The current local commits are:

```
7b7fd5b  Initial castellan-shadow: ETL + 4 historical snapshots loaded
<next>   Add Astro 5 + Tailwind dashboard rendering 8 historical snapshots
```

## Refreshing the data

The frontend reads static JSON in `frontend/src/data/`. To capture a fresh
snapshot from the backend:

```bash
# Terminal 1 — boot the backend
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2 — re-run the daily ETL (or wait for the 6:30pm cron)
curl -X POST http://localhost:8081/api/admin/etl/holdings
curl -X POST http://localhost:8081/api/admin/etl/resolve-tickers

# Terminal 2 — re-snapshot the JSON fixtures (use the script in this repo or curl manually)
cd ../frontend/src/data
for d in $(curl -s http://localhost:8081/api/etfs/CTEF/holdings/dates | python -c "import sys,json; print(' '.join(json.load(sys.stdin)))"); do
  curl -s "http://localhost:8081/api/etfs/CTEF/holdings?asOf=$d" > "CTEF-$d.json"
done
# repeat for CTIF
```

Then `npm run deploy` from `frontend/` again.

## Future: skip the static-fixture step

When the backend is hosted (Railway, Fly, Render free tier), point the Astro pages
at the live API and the dashboard auto-refreshes daily. That's a 30-minute change
when you're ready.
