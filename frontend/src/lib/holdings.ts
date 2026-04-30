import etfsRaw from '../data/etfs.json';
// Vite-style glob import to load every snapshot statically at build time.
const snapshotMods = import.meta.glob<{ default: HoldingsSnapshot }>(
  '../data/{CTEF,CTIF}-*.json',
  { eager: true },
);

export type Etf = {
  id: number;
  ticker: string;
  name: string;
  expenseRatio: number;
  inceptionDate: string;
  castellanFund: boolean;
};

export type Holding = {
  instrumentSymbol: string;
  securityName: string | null;
  cusip: string | null;
  securityType: 'EQUITY' | 'OPTION' | 'CASH' | 'OTHER';
  underlyingTicker: string | null;
  shares: number | null;
  price: number | null;
  marketValue: number | null;
  weightPct: number | null;
  notionalValue: number | null;
};

export type HoldingsSnapshot = {
  ticker: string;
  asOf: string;
  holdings: Holding[];
};

export const etfs: Etf[] = etfsRaw as Etf[];

const TICKERS = ['CTEF', 'CTIF'] as const;
export type CastellanTicker = (typeof TICKERS)[number];

const SNAPSHOTS: Record<string, HoldingsSnapshot> = {};
const DATES_BY_TICKER: Record<string, string[]> = { CTEF: [], CTIF: [] };

for (const [path, mod] of Object.entries(snapshotMods)) {
  const m = path.match(/(CTEF|CTIF)-(\d{4}-\d{2}-\d{2})\.json$/);
  if (!m) continue;
  const ticker = m[1];
  const asOf   = m[2];
  SNAPSHOTS[`${ticker}|${asOf}`] = mod.default;
  DATES_BY_TICKER[ticker].push(asOf);
}
for (const k of Object.keys(DATES_BY_TICKER)) {
  DATES_BY_TICKER[k].sort().reverse();
}

export function getEtf(ticker: CastellanTicker): Etf {
  const e = etfs.find((x) => x.ticker === ticker);
  if (!e) throw new Error(`No etf for ${ticker}`);
  return e;
}

export function datesFor(ticker: CastellanTicker): string[] {
  return DATES_BY_TICKER[ticker] ?? [];
}

export function snapshotFor(ticker: CastellanTicker, asOf?: string): HoldingsSnapshot {
  const dates = datesFor(ticker);
  const date = asOf ?? dates[0];
  const snap = SNAPSHOTS[`${ticker}|${date}`];
  if (!snap) throw new Error(`No snapshot for ${ticker} ${date}`);
  return snap;
}

export type SnapshotStats = {
  totalAssets: number;
  equityCount: number;
  optionCount: number;
  cashCount: number;
  topSector: string;
  longOptionsValue: number;
  writtenOptionsValue: number;
};

export function computeStats(snap: HoldingsSnapshot): SnapshotStats {
  let totalAssets = 0;
  let equityCount = 0;
  let optionCount = 0;
  let cashCount = 0;
  let longOptionsValue = 0;
  let writtenOptionsValue = 0;
  for (const h of snap.holdings) {
    if (h.marketValue) totalAssets += h.marketValue;
    if (h.securityType === 'EQUITY') equityCount++;
    if (h.securityType === 'CASH')   cashCount++;
    if (h.securityType === 'OPTION') {
      optionCount++;
      if ((h.marketValue ?? 0) >= 0) longOptionsValue    += h.marketValue ?? 0;
      else                            writtenOptionsValue += h.marketValue ?? 0;
    }
  }
  return {
    totalAssets,
    equityCount,
    optionCount,
    cashCount,
    topSector: '—',
    longOptionsValue,
    writtenOptionsValue,
  };
}

export function fmtMoney(n: number | null | undefined, scale: 'full' | 'mm' = 'full'): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  if (scale === 'mm') return `$${(n / 1_000_000).toFixed(2)}M`;
  return n.toLocaleString('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 0,
  });
}

export function fmtNumber(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  return n.toLocaleString('en-US', { maximumFractionDigits: 0 });
}

export function fmtPct(n: number | null | undefined, digits = 2): string {
  if (n === null || n === undefined || Number.isNaN(n)) return '—';
  return `${n.toFixed(digits)}%`;
}

export function fmtDate(s: string): string {
  const d = new Date(s);
  return d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}
