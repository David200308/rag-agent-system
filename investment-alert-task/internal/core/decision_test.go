package core

import (
	"testing"
	"time"

	"investment-alert-task/internal/data/price"
)

func TestEvaluate_DirectionThresholds(t *testing.T) {
	tests := []struct {
		name      string
		direction Direction
		threshold float64
		price     float64
		want      bool
	}{
		{"gte triggers on equal", DirectionGreaterThanOrEqual, 100, 100, true},
		{"gte triggers above", DirectionGreaterThanOrEqual, 100, 101, true},
		{"gte suppressed below", DirectionGreaterThanOrEqual, 100, 99, false},
		{"gt suppressed on equal", DirectionGreaterThan, 100, 100, false},
		{"gt triggers above", DirectionGreaterThan, 100, 101, true},
		{"lte triggers on equal", DirectionLessThanOrEqual, 100, 100, true},
		{"lte suppressed above", DirectionLessThanOrEqual, 100, 101, false},
		{"lt triggers below", DirectionLessThan, 100, 99, true},
		{"lt suppressed on equal", DirectionLessThan, 100, 100, false},
		{"eq triggers within epsilon", DirectionEqual, 100, 100.005, true},
		{"eq suppressed outside epsilon", DirectionEqual, 100, 100.5, false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			engine := NewDecisionEngine()
			engine.AddRule(&AlertRule{
				ID: "rule-1", OwnerUuid: "owner-1", Symbol: "BTC/USD",
				Threshold: tt.threshold, Direction: tt.direction, Enabled: true,
			})

			decisions := engine.Evaluate(&price.PriceData{Symbol: "BTC/USD", Price: tt.price, Timestamp: time.Now()})

			got := len(decisions) > 0
			if got != tt.want {
				t.Errorf("Evaluate() shouldAlert = %v, want %v (decisions=%v)", got, tt.want, decisions)
			}
		})
	}
}

func TestEvaluate_DisabledRuleNeverAlerts(t *testing.T) {
	engine := NewDecisionEngine()
	engine.AddRule(&AlertRule{
		ID: "rule-1", OwnerUuid: "owner-1", Symbol: "BTC/USD",
		Threshold: 100, Direction: DirectionGreaterThanOrEqual, Enabled: false,
	})

	decisions := engine.Evaluate(&price.PriceData{Symbol: "BTC/USD", Price: 200, Timestamp: time.Now()})
	if len(decisions) != 0 {
		t.Errorf("expected no decisions for disabled rule, got %d", len(decisions))
	}
}

func TestEvaluate_SymbolMismatchIgnored(t *testing.T) {
	engine := NewDecisionEngine()
	engine.AddRule(&AlertRule{
		ID: "rule-1", OwnerUuid: "owner-1", Symbol: "ETH/USD",
		Threshold: 100, Direction: DirectionGreaterThanOrEqual, Enabled: true,
	})

	decisions := engine.Evaluate(&price.PriceData{Symbol: "BTC/USD", Price: 200, Timestamp: time.Now()})
	if len(decisions) != 0 {
		t.Errorf("expected no decisions for mismatched symbol, got %d", len(decisions))
	}
}

func TestEvaluate_FrequencySuppression(t *testing.T) {
	t.Run("ONCE fires once then disables immediately", func(t *testing.T) {
		// The rule must come back disabled from the very evaluation that fires it — not a
		// hypothetical repeat trigger — so the caller can persist enabled=false right away.
		// Otherwise a rule whose condition doesn't hold on the next poll would never get
		// disabled at all, in memory or in the DB.
		engine := NewDecisionEngine()
		rule := &AlertRule{
			ID: "rule-1", OwnerUuid: "owner-1", Symbol: "BTC/USD",
			Threshold: 100, Direction: DirectionGreaterThanOrEqual, Enabled: true,
			Frequency: &Frequency{Unit: FrequencyUnitOnce},
		}
		engine.AddRule(rule)

		first := engine.Evaluate(&price.PriceData{Symbol: "BTC/USD", Price: 200, Timestamp: time.Now()})
		if len(first) != 1 {
			t.Fatalf("expected 1 decision on first trigger, got %d", len(first))
		}
		if rule.Enabled {
			t.Error("expected rule to be disabled immediately after its ONCE trigger fires")
		}

		second := engine.Evaluate(&price.PriceData{Symbol: "BTC/USD", Price: 200, Timestamp: time.Now()})
		if len(second) != 0 {
			t.Errorf("expected no decisions once the rule is disabled, got %d", len(second))
		}
	})

	t.Run("NEVER always suppresses", func(t *testing.T) {
		engine := NewDecisionEngine()
		engine.AddRule(&AlertRule{
			ID: "rule-1", OwnerUuid: "owner-1", Symbol: "BTC/USD",
			Threshold: 100, Direction: DirectionGreaterThanOrEqual, Enabled: true,
			Frequency: &Frequency{Unit: FrequencyUnitNever},
		})

		decisions := engine.Evaluate(&price.PriceData{Symbol: "BTC/USD", Price: 200, Timestamp: time.Now()})
		if len(decisions) != 0 {
			t.Errorf("expected NEVER frequency to always suppress, got %d decisions", len(decisions))
		}
	})

	t.Run("HOUR suppresses within window, fires after", func(t *testing.T) {
		engine := NewDecisionEngine()
		past := time.Now().Add(-2 * time.Hour)
		rule := &AlertRule{
			ID: "rule-1", OwnerUuid: "owner-1", Symbol: "BTC/USD",
			Threshold: 100, Direction: DirectionGreaterThanOrEqual, Enabled: true,
			Frequency: &Frequency{Number: 1, Unit: FrequencyUnitHour}, LastTriggered: &past,
		}
		engine.AddRule(rule)

		decisions := engine.Evaluate(&price.PriceData{Symbol: "BTC/USD", Price: 200, Timestamp: time.Now()})
		if len(decisions) != 1 {
			t.Errorf("expected trigger once the 1-hour window has passed, got %d decisions", len(decisions))
		}
	})

	t.Run("default suppresses duplicate within 1 hour", func(t *testing.T) {
		engine := NewDecisionEngine()
		recent := time.Now().Add(-10 * time.Minute)
		engine.AddRule(&AlertRule{
			ID: "rule-1", OwnerUuid: "owner-1", Symbol: "BTC/USD",
			Threshold: 100, Direction: DirectionGreaterThanOrEqual, Enabled: true,
			LastTriggered: &recent,
		})

		decisions := engine.Evaluate(&price.PriceData{Symbol: "BTC/USD", Price: 200, Timestamp: time.Now()})
		if len(decisions) != 0 {
			t.Errorf("expected default 1-hour suppression window to hold, got %d decisions", len(decisions))
		}
	})
}

func TestReplaceRules_PreservesLastTriggeredByID(t *testing.T) {
	engine := NewDecisionEngine()
	triggered := time.Now().Add(-30 * time.Minute)
	engine.AddRule(&AlertRule{ID: "rule-1", OwnerUuid: "owner-1", Symbol: "BTC/USD", LastTriggered: &triggered})

	reloaded := &AlertRule{ID: "rule-1", OwnerUuid: "owner-1", Symbol: "BTC/USD", Threshold: 999}
	engine.ReplaceRules([]*AlertRule{reloaded}, nil, nil)

	got := engine.GetRules()
	if len(got) != 1 {
		t.Fatalf("expected 1 rule after reload, got %d", len(got))
	}
	if got[0].LastTriggered == nil || !got[0].LastTriggered.Equal(triggered) {
		t.Error("expected LastTriggered to carry over across reload for the same rule ID")
	}
}

func TestReplaceRules_NewIDStartsFresh(t *testing.T) {
	engine := NewDecisionEngine()
	triggered := time.Now().Add(-30 * time.Minute)
	engine.AddRule(&AlertRule{ID: "rule-1", OwnerUuid: "owner-1", LastTriggered: &triggered})

	reloaded := &AlertRule{ID: "rule-2", OwnerUuid: "owner-1"}
	engine.ReplaceRules([]*AlertRule{reloaded}, nil, nil)

	got := engine.GetRules()
	if len(got) != 1 {
		t.Fatalf("expected 1 rule after reload, got %d", len(got))
	}
	if got[0].LastTriggered != nil {
		t.Error("expected a new rule ID to start with no LastTriggered")
	}
}

func TestEvaluateDeFi_MatchesByChainTokenAndField(t *testing.T) {
	engine := NewDecisionEngine()
	engine.AddDeFiRule(&DeFiAlertRule{
		ID: "defi-1", OwnerUuid: "owner-1", Protocol: "aave", Version: "v3",
		ChainID: "1", MarketTokenContract: "0xUSDC", Field: "TVL",
		Threshold: 1000, Direction: DirectionGreaterThanOrEqual, Enabled: true,
	})

	// Wrong field — no match
	none := engine.EvaluateDeFi("1", "0xUSDC", "APY", 2000, "Ethereum")
	if len(none) != 0 {
		t.Errorf("expected no match for mismatched field, got %d", len(none))
	}

	// Matching chain/token/field, above threshold
	got := engine.EvaluateDeFi("1", "0xUSDC", "TVL", 2000, "Ethereum")
	if len(got) != 1 {
		t.Errorf("expected 1 decision for matching DeFi rule, got %d", len(got))
	}
}

func TestEvaluatePredictMarket_MidpointThreshold(t *testing.T) {
	engine := NewDecisionEngine()
	engine.AddPredictMarketRule(&PredictMarketAlertRule{
		ID: "pm-1", OwnerUuid: "owner-1", PredictMarket: "polymarket", TokenID: "tok-1",
		Field: "MIDPOINT", Threshold: 0.7, Direction: DirectionGreaterThanOrEqual, Enabled: true,
	})

	below := engine.EvaluatePredictMarket("tok-1", 0.5, 0.49, 0.51)
	if len(below) != 0 {
		t.Errorf("expected no trigger below threshold, got %d", len(below))
	}

	above := engine.EvaluatePredictMarket("tok-1", 0.75, 0.74, 0.76)
	if len(above) != 1 {
		t.Errorf("expected 1 trigger above threshold, got %d", len(above))
	}
}
