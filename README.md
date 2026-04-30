# castellan-shadow

A live performance-attribution dashboard for the Castellan Targeted Equity ETF (CTEF) and Castellan Targeted Income ETF (CTIF), plus a covered-call overlay sandbox. Built from public data only.

> **Disclaimer.** This is an independent educational tool. It is not affiliated with, endorsed by, or sponsored by Castellan Group LLC, Arin Risk Advisors LLC, Empowered Funds LLC (ETF Architect), or PINE Distributors LLC. Nothing here is investment advice, a recommendation, or a benchmark. All data is derived from public sources and may contain errors.

## What it does

1. **Daily ETL** pulls each ETF's published holdings, public price/dividend data, and option contract marks.
2. **Attribution engine** decomposes daily fund returns into:
   - Stock selection alpha vs benchmark
   - Sector / Brinson-Fachler attribution
   - Fama-French factor exposure (rolling regression)
   - Options overlay P&L (isolated from the equity sleeve)
3. **Covered-call sandbox** — for any dividend-growth stock, find the call write (delta, DTE, position size) that maximizes annualized income subject to a configurable upside-give-up cap.
4. **Public dashboard** at `castellan-shadow.minhsonle.sunhomelab.com` (planned).

## Repo layout

```
backend/    Spring Boot 3.4 + Java 21. Daily ETL, persistence, REST API.
analytics/  Python 3.14 + FastAPI. Attribution math, factor regression, options pricing.
frontend/   Astro 5 + Tailwind + Cloudflare. The dashboard.
infra/      SQL schema, GitHub Actions cron, Cloudflare config.
docs/       Methodology notes, data-source documentation.
```

## Status

Week 1 — data pipeline. Backend boots locally on H2; Postgres for production.

## Local dev

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm install && npm run dev
cd analytics && uv venv && uv pip install -r requirements.txt && uvicorn app.main:app --reload
```
