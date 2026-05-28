export const CURRENCIES = ["HKD", "CNY", "USD", "JPY", "SGD", "GBP", "AUD", "EUR", "CAD"] as const;

export const CARD_TYPES = ["Credit", "Debit", "ATM"] as const;
export type CardType = typeof CARD_TYPES[number];

export const CARD_NETWORKS = ["Mastercard", "Visa", "UnionPay", "JCB", "AMEX"] as const;
export type CardNetwork = typeof CARD_NETWORKS[number];

export interface Card {
  id: string;
  ownerEmail: string;
  bank: string;
  types: CardType[];
  cardName: string;
  network: CardNetwork;
  expireDate: string;
  creditLimit: number | null;
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
