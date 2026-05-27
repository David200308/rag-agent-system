export const CURRENCIES = ["HKD", "CNY", "USD", "JPY", "SGD", "GBP", "AUD", "EUR", "CAD"] as const;
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

export interface CashDeposit {
  id: string;
  ownerEmail: string;
  platform: string;
  platformType: string;
  countryRegion: string;
  depositType: DepositType;
  currency: Currency;
  amount: number;
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
  currency: Currency;
  fee: number;
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
  currency: Currency;
  createdAt: string;
  updatedAt: string;
}

export interface ExchangeRates {
  result: string;
  base_code: string;
  time_last_update_utc: string;
  conversion_rates: Record<string, number>;
}

/** Convert `amount` in `from` currency to `to` currency using USD-base rates. */
export function convertCurrency(
  amount: number,
  from: string,
  to: string,
  rates: Record<string, number>,
): number {
  if (from === to) return amount;
  const fromRate = rates[from] ?? 1;
  const toRate   = rates[to]   ?? 1;
  return (amount / fromRate) * toRate;
}

export function formatAmount(value: number, currency: string): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}
