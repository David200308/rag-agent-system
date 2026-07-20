package config

import "testing"

func TestParsePriceRule_Valid(t *testing.T) {
	rule, err := ParsePriceRule(AlertRuleConfig{
		OwnerUuid: "owner-1", Symbol: "BTC/USD", PriceFeedID: "feed-1",
		AssetType: "CRYPTO", Threshold: 100, Direction: ">=", Enabled: true,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if rule.Symbol != "BTC/USD" || rule.OwnerUuid != "owner-1" {
		t.Errorf("unexpected rule: %+v", rule)
	}
}

func TestParsePriceRule_DefaultsAssetTypeToCrypto(t *testing.T) {
	rule, err := ParsePriceRule(AlertRuleConfig{
		OwnerUuid: "owner-1", Symbol: "BTC/USD", PriceFeedID: "feed-1",
		Threshold: 100, Direction: ">=", Enabled: true,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if rule.AssetType != "CRYPTO" {
		t.Errorf("expected default asset type CRYPTO, got %s", rule.AssetType)
	}
}

func TestParsePriceRule_StockAssetType(t *testing.T) {
	rule, err := ParsePriceRule(AlertRuleConfig{
		OwnerUuid: "owner-1", Symbol: "QQQ/USD", PriceFeedID: "feed-2",
		AssetType: "STOCK", Threshold: 400, Direction: "<=", Enabled: true,
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if rule.AssetType != "STOCK" {
		t.Errorf("expected STOCK asset type, got %s", rule.AssetType)
	}
}

func TestParsePriceRule_MissingOwnerUuid(t *testing.T) {
	_, err := ParsePriceRule(AlertRuleConfig{
		Symbol: "BTC/USD", PriceFeedID: "feed-1", Threshold: 100, Direction: ">=",
	})
	if err == nil {
		t.Fatal("expected error for missing ownerUuid")
	}
}

func TestParsePriceRule_InvalidDirection(t *testing.T) {
	_, err := ParsePriceRule(AlertRuleConfig{
		OwnerUuid: "owner-1", Symbol: "BTC/USD", PriceFeedID: "feed-1",
		Threshold: 100, Direction: "~=",
	})
	if err == nil {
		t.Fatal("expected error for invalid direction")
	}
}

func TestParsePriceRule_MissingSymbolOrFeedID(t *testing.T) {
	if _, err := ParsePriceRule(AlertRuleConfig{OwnerUuid: "o1", PriceFeedID: "f1", Threshold: 1, Direction: ">="}); err == nil {
		t.Fatal("expected error for missing symbol")
	}
	if _, err := ParsePriceRule(AlertRuleConfig{OwnerUuid: "o1", Symbol: "BTC/USD", Threshold: 1, Direction: ">="}); err == nil {
		t.Fatal("expected error for missing price_feed_id")
	}
}

func TestParsePriceRule_NonPositiveThreshold(t *testing.T) {
	_, err := ParsePriceRule(AlertRuleConfig{
		OwnerUuid: "o1", Symbol: "BTC/USD", PriceFeedID: "f1", Threshold: 0, Direction: ">=",
	})
	if err == nil {
		t.Fatal("expected error for non-positive threshold")
	}
}

func TestParsePriceRule_InvalidAssetType(t *testing.T) {
	_, err := ParsePriceRule(AlertRuleConfig{
		OwnerUuid: "o1", Symbol: "BTC/USD", PriceFeedID: "f1", Threshold: 1, Direction: ">=", AssetType: "BOND",
	})
	if err == nil {
		t.Fatal("expected error for invalid asset type")
	}
}

func TestParsePriceRule_FrequencyValidation(t *testing.T) {
	num := 2
	rule, err := ParsePriceRule(AlertRuleConfig{
		OwnerUuid: "o1", Symbol: "BTC/USD", PriceFeedID: "f1", Threshold: 1, Direction: ">=",
		Frequency: &FrequencyConfig{Number: &num, Unit: "DAY"},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if rule.Frequency == nil || rule.Frequency.Number != 2 {
		t.Errorf("expected frequency to carry through, got %+v", rule.Frequency)
	}

	_, err = ParsePriceRule(AlertRuleConfig{
		OwnerUuid: "o1", Symbol: "BTC/USD", PriceFeedID: "f1", Threshold: 1, Direction: ">=",
		Frequency: &FrequencyConfig{Unit: "DAY"}, // missing required Number
	})
	if err == nil {
		t.Fatal("expected error for DAY frequency missing number")
	}
}

func TestParseDeFiRule_MorphoRequiresCategory(t *testing.T) {
	_, err := ParseDeFiRule(DeFiAlertRuleConfig{
		OwnerUuid: "o1", Protocol: "morpho", Version: "v1", ChainID: "1",
		Field: "TVL", Threshold: 1, Direction: ">=",
	})
	if err == nil {
		t.Fatal("expected error for Morpho rule missing category")
	}
}

func TestParseDeFiRule_MorphoMarketFallsBackToMarketID(t *testing.T) {
	rule, err := ParseDeFiRule(DeFiAlertRuleConfig{
		OwnerUuid: "o1", Protocol: "morpho", Category: "market", Version: "v1", ChainID: "1",
		Field: "TVL", Threshold: 1, Direction: ">=",
		Params: DeFiAlertRuleParams{MarketID: "market-123"},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if rule.MarketTokenContract != "market-123" {
		t.Errorf("expected MarketTokenContract to fall back to MarketID, got %s", rule.MarketTokenContract)
	}
}

func TestParseDeFiRule_PendleFieldRestriction(t *testing.T) {
	_, err := ParseDeFiRule(DeFiAlertRuleConfig{
		OwnerUuid: "o1", Protocol: "pendle", Category: "pt", Version: "v2", ChainID: "1",
		Field: "UTILIZATION", Threshold: 1, Direction: ">=",
		Params: DeFiAlertRuleParams{MarketTokenContract: "0xMarket"},
	})
	if err == nil {
		t.Fatal("expected error — Pendle only supports APY/TVL fields")
	}
}

func TestParsePredictMarketRule_RequiresTokenID(t *testing.T) {
	_, err := ParsePredictMarketRule(PredictMarketAlertRuleConfig{
		OwnerUuid: "o1", PredictMarket: "polymarket", Field: "MIDPOINT", Threshold: 0.5, Direction: ">=",
	})
	if err == nil {
		t.Fatal("expected error for missing params.token_id")
	}
}

func TestParsePredictMarketRule_Valid(t *testing.T) {
	rule, err := ParsePredictMarketRule(PredictMarketAlertRuleConfig{
		OwnerUuid: "o1", PredictMarket: "polymarket", Field: "MIDPOINT", Threshold: 0.5, Direction: ">=",
		Params: PredictMarketAlertRuleParams{TokenID: "tok-1", Outcome: "YES"},
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if rule.TokenID != "tok-1" || rule.Outcome != "YES" {
		t.Errorf("unexpected rule: %+v", rule)
	}
}
