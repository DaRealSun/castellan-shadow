# castellan-shadow

> A live, public-data dashboard for the Castellan Targeted Equity ETF (CTEF) and Castellan Targeted Income ETF (CTIF), with attribution-grade modeling of the FLEX options sleeve. Built end-to-end as a portfolio piece.

**🌐 Live site:** https://castellan-shadow.sunhomelab.com
**📂 Repo:** https://github.com/DaRealSun/castellan-shadow

---

## Why this exists

Castellan Group is a small Louisville/Jupiter-based RIA (~12 people) that runs two transparent active ETFs sub-advised in partnership with Arin Risk Advisors LLC and issued through Empowered Funds (EA Advisers / ETF Architect). Their public daily-holdings disclosure is published as an HTML table at [castellanetf.com](https://castellanetf.com); their structural detail lives in SEC-filed Part F and Schedule of Investments PDFs hosted by ETF Architect.

For an outside reader, the gap is real: there is no public time-series view, no machine-readable export, and the cryptic FLEX option symbols (`2DECK 260918C00120010`) make the most interesting part of the strategy invisible without 8 pages of prospectus reading.

**castellan-shadow closes that gap.** It ingests every public disclosure, models the option contracts properly, and presents a unified time-series dashboard.

It is also an unsolicited portfolio piece for Castellan's [Pillar 1: Public Equity & Quant Strategies](https://castellangroup.com/internship-program/) internship track. Their published intern projects to date are all Pillar 2 (private equity / VC / real estate). This is the first Pillar 1 portfolio artifact.

---

## What it shows

Eight historical snapshots — four per fund — drawn from real disclosures:

| As of | Source | CTEF rows | CTIF rows |
|---|---|---|---|
| 2025-08-31 | Q1 Part F (~2 mo. after launch) | 24 | 25 |
| 2025-11-30 | Q2 Semi-Annual Schedule of Investments | 25 | 23 |
| 2026-02-28 | Q3 Part F | 34 | 39 |
| 2026-04-30 | Live HTML scrape (today) | 45 | 45 |

**Most useful disclosure surfaced:** CTEF's options sleeve is structured as **1×2 ratio FLEX call spreads** — long the lower strike, short twice as many contracts at a higher strike on the same expiration. The two legs roughly self-finance each other and produce defined upside between strikes:

```
DECK  Long  +400 contracts @ $120.01 strike, exp 2026-09-18
DECK  Short -800 contracts @ $140.01 strike, exp 2026-09-18  (1×2 ratio)
JBL   Long  +179 contracts @ $225.01 / Short -358 @ $255.01
MU    Long  +152 contracts @ $315.01 / Short -304 @ $400.01
NCLH  Long +1,309 contracts @ $23.01 / Short -2,618 @ $26.01
```

The dashboard isolates the long premium spent ($4.1M) from the short premium received ($5.1M), and the corresponding `option_contract` rows are deduplicated and FK-linked from any snapshot that holds them — so the same MU 7/17/26 $315.01 Call is one row in the database, referenced by every snapshot it appeared in.

---

## Architecture

```
                   ┌────────────────────────┐
                   │  castellanetf.com/ctef │  (daily HTML table)
                   │  castellanetf.com/ctif │
                   └─────────────┬──────────┘
                                 │
                   ┌─────────────▼──────────┐         ┌──────────────────┐
                   │  CastellanWebsiteHoldings        │  ETF Architect    │
                   │  Fetcher (Jsoup HTML scrape)     │  Part F / Q2 SOI  │
                   │                                  │  PDFs             │
                   │   FLEX OCC parser:               │                   │
                   │   2DECK 260918C00120010          │   PDFBox + regex  │
                   │   → DECK, 2026-09-18, $120.01,   │   → Schedule of   │
                   │     CALL, FLEX                   │     Investments   │
                   └─────────────┬───────────┬────────┴─────────┬─────────┘
                                 │           │                  │
                                 │  ┌────────▼──────────────────▼────────┐
                                 │  │  HoldingsIngestService             │
                                 │  │  upsert OptionContract (deduped)   │
                                 │  │  upsert Holding row + FK link      │
                                 │  └─────────────────┬──────────────────┘
                                 │                    │
                                 ▼                    ▼
                        Postgres / H2 (JPA + Flyway-migrated)
                        ┌──────────┬──────────┬──────────────────┐
                        │ etf      │ holding  │ option_contract  │
                        │ price_bar│ etl_run  │                  │
                        └──────────┴──────────┴──────────────────┘
                                       │
                                       ▼
                        Spring Boot REST API on :8081
                        ┌────────────────────────────┐
                        │ /api/etfs                  │
                        │ /api/etfs/{ticker}/holdings│
                        │ /api/admin/etl/{job}       │
                        └─────────────┬──────────────┘
                                      │
                              static JSON fixtures
                                      │
                                      ▼
                        Astro 5 + Tailwind + Cloudflare
                        ┌─────────────────────────────┐
                        │ /        home overview      │
                        │ /ctef    + 3 historical     │
                        │ /ctif    + 3 historical     │
                        │ /about   methodology        │
                        └─────────────────────────────┘
```

### Schema highlights

```sql
-- v1 base schema
CREATE TABLE holding (
    id, etf_id, as_of_date, security_type, instrument_symbol,
    underlying_ticker, shares, market_value, weight_pct, ...
    UNIQUE (etf_id, as_of_date, instrument_symbol)
);

-- v3: fields lifted from the live HTML table
ALTER TABLE holding ADD COLUMN cusip, security_name, price;

-- v4: option contracts modeled separately so a contract held in multiple
-- snapshots is one row, not N
CREATE TABLE option_contract (
    underlying_ticker, expiration, strike, option_type,
    contract_size, is_flex, description,
    UNIQUE (underlying_ticker, expiration, strike, option_type)
);
ALTER TABLE holding ADD COLUMN option_contract_id BIGINT REFERENCES option_contract(id);
```

### Pipeline correctness

- **Idempotent ETL.** Re-running a job is a no-op; `(etf_id, as_of_date, instrument_symbol)` is the natural key.
- **Signed positions.** Written calls land with negative `shares` and negative `market_value`, so a long/short distinction never relies on a separate flag.
- **Name → ticker resolver.** SEC filings only carry company names; the live scrape carries both. `NameTickerResolver` learns the map from live data and back-fills 67 historical placeholder rows (PROCTER → PG, AMERIPRISE → AMP, etc.). 46 rows remain as placeholders — those are stocks that have rotated out of the current portfolio entirely.
- **Layout-tolerant PDF parsing.** PDFBox extracts `CTEF Q3` with the section header at the top of the body, and `CTIF Q1` with it after the body. The parser doesn't depend on header position; it scans globally for option rows (anchored on `, Expiration: MM/DD/YYYY`) and equity rows independently, using the `SCHEDULE OF WRITTEN OPTIONS` boundary to disambiguate long vs. short.
- **Flyway-managed schema.** V1–V4 migrations are committed; H2 for local dev, Postgres for production, identical SQL.

---

## Tech stack

| Layer | Choice | Rationale |
|---|---|---|
| Backend | Spring Boot 3.4 / Java 21 | Same stack as a real wealth-firm internal tool |
| HTTP | WebFlux WebClient (reactor-netty) | Non-blocking ETL of multiple feeds |
| Persistence | Spring Data JPA + Flyway | Audit-friendly schema migrations |
| HTML scrape | Jsoup 1.18 | Robust to layout drift |
| PDF parse | Apache PDFBox 3.0 | Authoritative open-source, easily testable |
| Local DB | H2 (PostgreSQL mode) | Fast iteration |
| Prod DB | Postgres | Standard |
| Frontend | Astro 5 + Tailwind 3 | Same stack as my [portfolio](https://github.com/DaRealSun/MinhSonLe-Portfolio); zero JS shipped to client |
| Hosting | Cloudflare Workers | Same stack as [minhsonle.sunhomelab.com](https://minhsonle.sunhomelab.com) |

---

## Local dev

```bash
# Backend (port 8081)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl -X POST http://localhost:8081/api/admin/etl/holdings
curl -X POST "http://localhost:8081/api/admin/etl/partf?ticker=CTEF&path=../docs/sources/CTEF_part_f_q3.pdf"
curl -X POST http://localhost:8081/api/admin/etl/resolve-tickers

# Frontend (port 4321; reads JSON fixtures in src/data/)
cd ../frontend
npm install
npm run dev
```

Production preview without Cloudflare auth:

```bash
cd frontend
npm run preview     # serves the prod build at http://localhost:8787
```

Deploy:

```bash
npx wrangler login   # one-time
npm run deploy
```

---

## What's not here yet

The data foundation is the part that's hard to throw away. The next layers — none of which would have been buildable without the foundation — are:

- Daily NAV time-series and benchmark-relative attribution (vs. SPY / IWV / NOBL / VYM)
- Brinson-Fachler sector decomposition
- Fama-French rolling factor regression on the equity sleeve
- Options sleeve P&L isolated from the equity sleeve (mark-to-market the FLEX contracts daily, decompose the ratio-spread payoff)
- Covered-call optimizer for CTIF-style dividend growers (delta × DTE × position-size grid maximizing income subject to an upside-give-up cap)

These are the layers that would turn this from a *transparency tool* into an *attribution-grade tool* — and they are exactly the kind of work an intern in [Castellan's Pillar 1 program](https://castellangroup.com/internship-program/) could plausibly own.

---

## Disclaimer

This is an **independent educational project**. It is not affiliated with, endorsed by, or sponsored by Castellan Group LLC, Arin Risk Advisors LLC, Empowered Funds LLC (dba EA Advisers / ETF Architect), or PINE Distributors LLC. Nothing here is investment advice, a recommendation, a benchmark, or a solicitation. All data is derived from public sources and may contain errors. Past performance does not predict future results.

Built by [Minh Son Le](https://minhsonle.sunhomelab.com).
