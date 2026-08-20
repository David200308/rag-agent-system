export const CURRENCIES = ["HKD", "CNY", "USD", "JPY", "SGD", "GBP", "AUD", "EUR", "CAD"] as const;


export const CARD_TYPES = ["Credit", "Debit", "ATM"] as const;
export type CardType = typeof CARD_TYPES[number];

export const CARD_NETWORKS = ["Mastercard", "Visa", "UnionPay", "JCB", "AMEX"] as const;
export type CardNetwork = typeof CARD_NETWORKS[number];

export interface Card {
  id: string;
  ownerEmail: string;
  bank: string;
  countryRegion: string;
  types: CardType[];
  cardName: string;
  network: CardNetwork;
  expireDate: string;
  creditLimit: number | null;
  creditLimitCurrency: string | null;
  sharedCredit: boolean | null;
  createdAt: string;
  updatedAt: string;
}
export type Currency = typeof CURRENCIES[number];

export const DEPOSIT_TYPES = ["FIXED", "FLEX"] as const;
export type DepositType = typeof DEPOSIT_TYPES[number];

export const STOCK_TYPES = ["US_STOCK", "HK_STOCK", "CN_STOCK", "SG_STOCK", "OTHER"] as const;
export type StockType = typeof STOCK_TYPES[number];

export const STOCK_TYPE_LABELS: Record<StockType, string> = {
  US_STOCK: "US Stock",
  HK_STOCK: "HK Stock",
  CN_STOCK: "CN Stock",
  SG_STOCK: "SG Stock",
  OTHER:    "Other",
};

export const FUTURE_EXCHANGE_KINDS = ["SECURITY", "CRYPTO_CEX", "CRYPTO_DEX"] as const;
export type FutureExchangeKind = typeof FUTURE_EXCHANGE_KINDS[number];

export const FUTURE_EXCHANGE_KIND_LABELS: Record<FutureExchangeKind, string> = {
  SECURITY:   "Security",
  CRYPTO_CEX: "Crypto (CEX)",
  CRYPTO_DEX: "Crypto (DEX)",
};

export const FUTURE_EXCHANGES_BY_KIND: Record<FutureExchangeKind, string[]> = {
  SECURITY:   ["IBKR"],
  CRYPTO_CEX: ["BINANCE", "OKX", "KRAKEN"],
  CRYPTO_DEX: ["HYPERLIQUID"],
};

export const FUTURE_SIDES = ["LONG", "SHORT"] as const;
export type FutureSide = typeof FUTURE_SIDES[number];

// ── Backend DTOs (mirror of Java records) ────────────────────────────────────

export interface CashDeposit {
  id: string;
  ownerEmail: string;
  platform: string;
  platformType: string;
  countryRegion: string;
  depositType: DepositType;
  currency: string;
  amount: number;
  /** Server-side converted amount in convertedCurrency */
  convertedAmount: number;
  convertedCurrency: string;
  createdAt: string;
  updatedAt: string;
}

export interface StockInvestment {
  id: string;
  ownerEmail: string;
  broker: string;
  stockType: StockType;
  symbol: string;
  name: string;
  stockAmount: number;
  investAmount: number;
  currency: string;
  fee: number;
  /** Live price from Yahoo Finance in priceCurrency; null if unavailable */
  currentPrice: number | null;
  priceCurrency: string | null;
  /** Company logo image URL from Finnhub; null if unavailable */
  logoUrl: string | null;
  /** currentPrice × stockAmount; null if price unavailable */
  currentValue: number | null;
  convertedInvestAmount: number;
  /** currentValue in convertedCurrency; null if price unavailable */
  convertedCurrentValue: number | null;
  convertedCurrency: string;
  /** (convertedCurrentValue - convertedInvestAmount) / convertedInvestAmount * 100; null if price unavailable */
  pnlPercent: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CryptoInvestment {
  id: string;
  ownerEmail: string;
  name: string;
  symbol: string;
  amount: number;
  investAmount: number;
  currency: string;
  /** Live USDT price from Binance; null if unavailable */
  currentPrice: number | null;
  /** Coin logo image URL from CoinGecko; null if unavailable */
  logoUrl: string | null;
  /** currentPrice × amount (USDT); null if price unavailable */
  currentValue: number | null;
  convertedInvestAmount: number;
  convertedCurrentValue: number | null;
  convertedCurrency: string;
  /** (convertedCurrentValue - convertedInvestAmount) / convertedInvestAmount * 100; null if price unavailable */
  pnlPercent: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface FutureInvestment {
  id: string;
  ownerEmail: string;
  exchangeKind: FutureExchangeKind;
  exchange: string;
  symbol: string | null;
  side: FutureSide | null;
  quantity: number | null;
  entryPrice: number | null;
  leverage: number | null;
  currency: string;
  connectionAddress: string | null;
  /** Live mark/last price; null if unavailable */
  currentPrice: number | null;
  /** currentPrice × quantity, in `currency`; null if price unavailable */
  currentValue: number | null;
  convertedInvestAmount: number;
  convertedCurrentValue: number | null;
  convertedCurrency: string;
  pnlPercent: number | null;
  /** "MANUAL" for user-entered Security/CEX rows, "HYPERLIQUID" for live-fetched DEX positions */
  source: "MANUAL" | "HYPERLIQUID";
  /** Only set for HYPERLIQUID rows — id of the tracked-address row this position was expanded from */
  sourceConnectionId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SalaryUsageRecord {
  id: string;
  ownerEmail: string;
  year: number;
  month: number;
  region: string;
  currency: string;
  salary: number;
  bonus: number;
  retirementSavingEmployee: number;
  retirementSavingEmployer: number;
  tax: number;
  houseRent: number;
  livingExpense: number;
  otherExpense: number;
  totalExpense: number;
  createdAt: string;
  updatedAt: string;
}

// ── Formatting helpers ────────────────────────────────────────────────────────

export function formatAmount(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${currency} ${value.toFixed(2)}`;
  }
}

export function formatPrice(value: number): string {
  if (value >= 1000) return value.toLocaleString("en-US", { maximumFractionDigits: 2 });
  if (value >= 1)    return value.toFixed(4);
  return value.toPrecision(4);
}
