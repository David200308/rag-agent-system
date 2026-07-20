export interface AlertFrequency {
  number?: number;
  unit: "DAY" | "HOUR" | "ONCE" | "NEVER";
}

export interface PriceAlert {
  id: string;
  ownerUuid: string;
  orgId?: string;
  symbol: string;
  priceFeedId: string;
  assetType: "CRYPTO" | "STOCK";
  threshold: number;
  direction: ">=" | ">" | "=" | "<=" | "<";
  enabled: boolean;
  frequency?: AlertFrequency | null;
}

export interface AlertsResponse {
  price: PriceAlert[];
  defi: unknown[];
  predictMarket: unknown[];
}

export interface CreatePriceAlertRequest {
  symbol: string;
  assetType: "CRYPTO" | "STOCK";
  threshold: number;
  direction: PriceAlert["direction"];
  frequency?: AlertFrequency;
}

export interface UpdateAlertRequest {
  threshold?: number;
  direction?: PriceAlert["direction"];
  enabled?: boolean;
  frequency?: AlertFrequency;
}
