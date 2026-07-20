package core

import (
	"fmt"
	"sync"
	"time"

	"investment-alert-task/internal/data/price"
)

// Direction indicates the comparison operator for price threshold
type Direction string

const (
	DirectionGreaterThanOrEqual Direction = ">="
	DirectionGreaterThan        Direction = ">"
	DirectionEqual              Direction = "="
	DirectionLessThanOrEqual    Direction = "<="
	DirectionLessThan           Direction = "<"
)

// FrequencyUnit represents the unit for frequency
type FrequencyUnit string

const (
	FrequencyUnitDay   FrequencyUnit = "DAY"
	FrequencyUnitHour  FrequencyUnit = "HOUR"
	FrequencyUnitOnce  FrequencyUnit = "ONCE"
	FrequencyUnitNever FrequencyUnit = "NEVER"
)

// Frequency represents the frequency configuration for an alert rule
type Frequency struct {
	Number int           `json:"number,omitempty"` // Number of units (required for DAY/HOUR, ignored for ONCE and NEVER)
	Unit   FrequencyUnit `json:"unit"`             // DAY, HOUR, ONCE, NEVER
}

// AssetType distinguishes a crypto price feed from an equity (stock) price feed.
// Both are fetched identically via Pyth — this is a display/filter label only.
type AssetType string

const (
	AssetTypeCrypto AssetType = "CRYPTO"
	AssetTypeStock  AssetType = "STOCK"
)

// AlertRule defines a price alert rule (crypto or stock, both via Pyth).
type AlertRule struct {
	ID            string     `json:"id"` // UUID — used for hot-swap matching
	OwnerUuid     string     `json:"ownerUuid"`
	OrgId         string     `json:"orgId,omitempty"`
	Symbol        string     `json:"symbol"`
	PriceFeedID   string     `json:"priceFeedId"` // Pyth price feed ID for this symbol
	AssetType     AssetType  `json:"assetType"`
	Threshold     float64    `json:"threshold"`
	Direction     Direction  `json:"direction"` // >=, >, =, <=, <
	Enabled       bool       `json:"enabled"`
	LastTriggered *time.Time `json:"lastTriggered,omitempty"`
	Frequency     *Frequency `json:"frequency,omitempty"` // Optional frequency configuration
}

// DeFiAlertRule defines a DeFi protocol alert rule
type DeFiAlertRule struct {
	ID                  string     `json:"id"` // UUID — used for hot-swap matching
	OwnerUuid           string     `json:"ownerUuid"`
	OrgId               string     `json:"orgId,omitempty"`
	Protocol            string     `json:"protocol"`
	Category            string     `json:"category,omitempty"` // "market" or "vault" (for Morpho), empty for others
	Version             string     `json:"version"`
	ChainID             string     `json:"chainId"`
	MarketTokenContract string     `json:"marketTokenContract"` // For Aave: token contract, For Morpho market: market_id, For Morpho vault: vault_token_address
	Field               string     `json:"field"`               // "TVL", "APY", "UTILIZATION", "LIQUIDITY"
	Threshold           float64    `json:"threshold"`
	Direction           Direction  `json:"direction"` // >=, >, =, <=, <
	Enabled             bool       `json:"enabled"`
	LastTriggered       *time.Time `json:"lastTriggered,omitempty"`
	Frequency           *Frequency `json:"frequency,omitempty"`
	// Display names (optional, for better logging/alert messages)
	MarketTokenName string `json:"marketTokenName,omitempty"` // For Aave: display name of the token (e.g., "USDC")
	MarketTokenPair string `json:"marketTokenPair,omitempty"` // For Morpho market: display pair (e.g., "USDC/WETH")
	VaultName       string `json:"vaultName,omitempty"`       // For Morpho vault: display name of the vault
	// Morpho-specific fields
	BorrowTokenContract     string `json:"borrowTokenContract,omitempty"`
	CollateralTokenContract string `json:"collateralTokenContract,omitempty"`
	OracleAddress           string `json:"oracleAddress,omitempty"`
	IRMAddress              string `json:"irmAddress,omitempty"`
	LLTV                    string `json:"lltv,omitempty"`
	MarketContractAddress   string `json:"marketContractAddress,omitempty"`
	VaultTokenAddress       string `json:"vaultTokenAddress,omitempty"`
	DepositTokenContract    string `json:"depositTokenContract,omitempty"`
	// Hyperliquid-specific fields
	LedgerAddress string `json:"ledgerAddress,omitempty"` // For Hyperliquid vault: the vault ledger address
}

// AlertDecision represents the result of evaluating an alert rule
type AlertDecision struct {
	ShouldAlert  bool
	Rule         *AlertRule
	CurrentPrice *price.PriceData
	Message      string
}

// DeFiAlertDecision represents the result of evaluating a DeFi alert rule
type DeFiAlertDecision struct {
	ShouldAlert  bool
	Rule         *DeFiAlertRule
	CurrentValue float64
	ChainName    string
	Message      string
}

// PredictMarketAlertRule defines a prediction market alert rule.
// Threshold comparison is performed against the midpoint price.
type PredictMarketAlertRule struct {
	ID            string     `json:"id"` // UUID — used for hot-swap matching
	OwnerUuid     string     `json:"ownerUuid"`
	OrgId         string     `json:"orgId,omitempty"`
	PredictMarket string     `json:"predictMarket"` // e.g., "polymarket"
	TokenID       string     `json:"tokenId"`       // CLOB token ID to monitor
	Field         string     `json:"field"`         // "MIDPOINT"
	Threshold     float64    `json:"threshold"`
	Direction     Direction  `json:"direction"`
	Enabled       bool       `json:"enabled"`
	LastTriggered *time.Time `json:"lastTriggered,omitempty"`
	Frequency     *Frequency `json:"frequency,omitempty"`
	// Display context (populated from params)
	NegRisk     bool   `json:"negRisk,omitempty"`
	QuestionID  string `json:"questionId,omitempty"`
	Question    string `json:"question,omitempty"`
	ConditionID string `json:"conditionId,omitempty"`
	Outcome     string `json:"outcome,omitempty"` // "YES" or "NO"
}

// PredictMarketAlertDecision represents the result of evaluating a prediction market alert rule.
type PredictMarketAlertDecision struct {
	ShouldAlert      bool
	Rule             *PredictMarketAlertRule
	CurrentMidpoint  float64
	CurrentBuyPrice  float64
	CurrentSellPrice float64
	Message          string
}

// DecisionEngine handles price comparison and alert decisions.
// All exported methods are thread-safe.
type DecisionEngine struct {
	mu                 sync.Mutex
	rules              []*AlertRule
	defiRules          []*DeFiAlertRule
	predictMarketRules []*PredictMarketAlertRule
}

// NewDecisionEngine creates a new decision engine
func NewDecisionEngine() *DecisionEngine {
	return &DecisionEngine{
		rules:              make([]*AlertRule, 0),
		defiRules:          make([]*DeFiAlertRule, 0),
		predictMarketRules: make([]*PredictMarketAlertRule, 0),
	}
}

// AddRule adds an alert rule to the engine
func (e *DecisionEngine) AddRule(rule *AlertRule) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.rules = append(e.rules, rule)
}

// AddDeFiRule adds a DeFi alert rule to the engine
func (e *DecisionEngine) AddDeFiRule(rule *DeFiAlertRule) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.defiRules = append(e.defiRules, rule)
}

// AddPredictMarketRule adds a prediction market alert rule to the engine
func (e *DecisionEngine) AddPredictMarketRule(rule *PredictMarketAlertRule) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.predictMarketRules = append(e.predictMarketRules, rule)
}

// GetRules returns a snapshot of all alert rules
func (e *DecisionEngine) GetRules() []*AlertRule {
	e.mu.Lock()
	defer e.mu.Unlock()
	cp := make([]*AlertRule, len(e.rules))
	copy(cp, e.rules)
	return cp
}

// GetDeFiRules returns a snapshot of all DeFi alert rules
func (e *DecisionEngine) GetDeFiRules() []*DeFiAlertRule {
	e.mu.Lock()
	defer e.mu.Unlock()
	cp := make([]*DeFiAlertRule, len(e.defiRules))
	copy(cp, e.defiRules)
	return cp
}

// GetPredictMarketRules returns a snapshot of all prediction market alert rules
func (e *DecisionEngine) GetPredictMarketRules() []*PredictMarketAlertRule {
	e.mu.Lock()
	defer e.mu.Unlock()
	cp := make([]*PredictMarketAlertRule, len(e.predictMarketRules))
	copy(cp, e.predictMarketRules)
	return cp
}

// ReplaceRules atomically swaps all rule sets, preserving LastTriggered from
// existing rules that share the same rule ID. Call this to hot-reload rules
// from the database without restarting the process.
func (e *DecisionEngine) ReplaceRules(price []*AlertRule, defi []*DeFiAlertRule, predict []*PredictMarketAlertRule) {
	e.mu.Lock()
	defer e.mu.Unlock()

	// Build lookup maps keyed by rule ID to carry over in-memory state.
	oldPrice := make(map[string]*AlertRule, len(e.rules))
	for _, r := range e.rules {
		if r.ID != "" {
			oldPrice[r.ID] = r
		}
	}
	oldDefi := make(map[string]*DeFiAlertRule, len(e.defiRules))
	for _, r := range e.defiRules {
		if r.ID != "" {
			oldDefi[r.ID] = r
		}
	}
	oldPredict := make(map[string]*PredictMarketAlertRule, len(e.predictMarketRules))
	for _, r := range e.predictMarketRules {
		if r.ID != "" {
			oldPredict[r.ID] = r
		}
	}

	// Carry LastTriggered forward so frequency suppression survives a reload.
	for _, r := range price {
		if old, ok := oldPrice[r.ID]; ok {
			r.LastTriggered = old.LastTriggered
		}
	}
	for _, r := range defi {
		if old, ok := oldDefi[r.ID]; ok {
			r.LastTriggered = old.LastTriggered
		}
	}
	for _, r := range predict {
		if old, ok := oldPredict[r.ID]; ok {
			r.LastTriggered = old.LastTriggered
		}
	}

	e.rules = price
	e.defiRules = defi
	e.predictMarketRules = predict
}

// shouldSuppress applies frequency-based alert suppression. Returns true if the
// alert should be suppressed (not fired) given the rule's LastTriggered state.
// Mutates rule.Enabled for ONCE semantics as a side effect, matching the
// original crypto-alert behavior.
func shouldSuppress(enabled *bool, lastTriggered *time.Time, freq *Frequency) bool {
	if freq != nil {
		switch freq.Unit {
		case FrequencyUnitOnce:
			if lastTriggered != nil {
				*enabled = false
				return true
			}
		case FrequencyUnitNever:
			return true
		case FrequencyUnitDay:
			if lastTriggered != nil {
				requiredDuration := time.Duration(freq.Number) * 24 * time.Hour
				if time.Since(*lastTriggered) < requiredDuration {
					return true
				}
			}
		case FrequencyUnitHour:
			if lastTriggered != nil {
				requiredDuration := time.Duration(freq.Number) * time.Hour
				if time.Since(*lastTriggered) < requiredDuration {
					return true
				}
			}
		}
		return false
	}
	// Default behavior: suppress duplicate alerts within 1 hour if no frequency is specified
	if lastTriggered != nil && time.Since(*lastTriggered) < time.Hour {
		return true
	}
	return false
}

// Evaluate checks if a price should trigger an alert based on rules.
func (e *DecisionEngine) Evaluate(priceData *price.PriceData) []*AlertDecision {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.evaluateLocked(priceData)
}

// evaluateLocked runs evaluation for a single price; caller must hold e.mu.
func (e *DecisionEngine) evaluateLocked(priceData *price.PriceData) []*AlertDecision {
	decisions := make([]*AlertDecision, 0)

	for _, rule := range e.rules {
		if !rule.Enabled {
			continue
		}

		if rule.Symbol != priceData.Symbol {
			continue
		}

		shouldAlert := false
		message := ""

		switch rule.Direction {
		case DirectionGreaterThanOrEqual:
			if priceData.Price >= rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s price is %g, which is >= threshold of %g",
					priceData.Symbol, priceData.Price, rule.Threshold)
			}
		case DirectionGreaterThan:
			if priceData.Price > rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s price is %g, which is > threshold of %g",
					priceData.Symbol, priceData.Price, rule.Threshold)
			}
		case DirectionEqual:
			epsilon := 0.01
			if priceData.Price >= rule.Threshold-epsilon && priceData.Price <= rule.Threshold+epsilon {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s price is %g, which equals threshold of %g",
					priceData.Symbol, priceData.Price, rule.Threshold)
			}
		case DirectionLessThanOrEqual:
			if priceData.Price <= rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s price is %g, which is <= threshold of %g",
					priceData.Symbol, priceData.Price, rule.Threshold)
			}
		case DirectionLessThan:
			if priceData.Price < rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s price is %g, which is < threshold of %g",
					priceData.Symbol, priceData.Price, rule.Threshold)
			}
		}

		if shouldAlert {
			if shouldSuppress(&rule.Enabled, rule.LastTriggered, rule.Frequency) {
				continue
			}

			decisions = append(decisions, &AlertDecision{
				ShouldAlert:  true,
				Rule:         rule,
				CurrentPrice: priceData,
				Message:      message,
			})

			now := time.Now()
			rule.LastTriggered = &now
		}
	}

	return decisions
}

// EvaluateAll evaluates all rules against multiple price data points
func (e *DecisionEngine) EvaluateAll(prices map[string]*price.PriceData) []*AlertDecision {
	e.mu.Lock()
	defer e.mu.Unlock()

	allDecisions := make([]*AlertDecision, 0)
	for _, priceData := range prices {
		decisions := e.evaluateLocked(priceData)
		allDecisions = append(allDecisions, decisions...)
	}

	return allDecisions
}

// EvaluatePredictMarket checks if a prediction market midpoint should trigger an alert.
// buyPrice and sellPrice are passed through to the decision for inclusion in alert messages.
func (e *DecisionEngine) EvaluatePredictMarket(tokenID string, midpoint, buyPrice, sellPrice float64) []*PredictMarketAlertDecision {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.evaluatePredictMarketLocked(tokenID, midpoint, buyPrice, sellPrice)
}

// evaluatePredictMarketLocked is the lock-free implementation; caller must hold e.mu.
func (e *DecisionEngine) evaluatePredictMarketLocked(tokenID string, midpoint, buyPrice, sellPrice float64) []*PredictMarketAlertDecision {
	decisions := make([]*PredictMarketAlertDecision, 0)

	for _, rule := range e.predictMarketRules {
		if !rule.Enabled {
			continue
		}
		if rule.TokenID != tokenID {
			continue
		}

		shouldAlert := false
		message := ""

		switch rule.Direction {
		case DirectionGreaterThanOrEqual:
			if midpoint >= rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: Polymarket token %s midpoint is %.4f, which is >= threshold of %g",
					tokenID, midpoint, rule.Threshold)
			}
		case DirectionGreaterThan:
			if midpoint > rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: Polymarket token %s midpoint is %.4f, which is > threshold of %g",
					tokenID, midpoint, rule.Threshold)
			}
		case DirectionEqual:
			epsilon := 0.0001
			if midpoint >= rule.Threshold-epsilon && midpoint <= rule.Threshold+epsilon {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: Polymarket token %s midpoint is %.4f, which equals threshold of %g",
					tokenID, midpoint, rule.Threshold)
			}
		case DirectionLessThanOrEqual:
			if midpoint <= rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: Polymarket token %s midpoint is %.4f, which is <= threshold of %g",
					tokenID, midpoint, rule.Threshold)
			}
		case DirectionLessThan:
			if midpoint < rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: Polymarket token %s midpoint is %.4f, which is < threshold of %g",
					tokenID, midpoint, rule.Threshold)
			}
		}

		if shouldAlert {
			if shouldSuppress(&rule.Enabled, rule.LastTriggered, rule.Frequency) {
				continue
			}

			decisions = append(decisions, &PredictMarketAlertDecision{
				ShouldAlert:      true,
				Rule:             rule,
				CurrentMidpoint:  midpoint,
				CurrentBuyPrice:  buyPrice,
				CurrentSellPrice: sellPrice,
				Message:          message,
			})

			now := time.Now()
			rule.LastTriggered = &now
		}
	}

	return decisions
}

// EvaluateDeFi checks if a DeFi value should trigger an alert based on rules
func (e *DecisionEngine) EvaluateDeFi(chainID, tokenAddress, field string, currentValue float64, chainName string) []*DeFiAlertDecision {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.evaluateDeFiLocked(chainID, tokenAddress, field, currentValue, chainName)
}

// evaluateDeFiLocked is the lock-free implementation; caller must hold e.mu.
func (e *DecisionEngine) evaluateDeFiLocked(chainID, tokenAddress, field string, currentValue float64, chainName string) []*DeFiAlertDecision {
	decisions := make([]*DeFiAlertDecision, 0)

	for _, rule := range e.defiRules {
		if !rule.Enabled {
			continue
		}

		// Match rule by chain ID, token address, and field
		if rule.ChainID != chainID || rule.MarketTokenContract != tokenAddress || rule.Field != field {
			continue
		}

		shouldAlert := false
		message := ""

		switch rule.Direction {
		case DirectionGreaterThanOrEqual:
			if currentValue >= rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s %s %s on %s - %s is %g, which is >= threshold of %g",
					rule.Protocol, rule.Version, rule.Field, chainName, rule.Field, currentValue, rule.Threshold)
			}
		case DirectionGreaterThan:
			if currentValue > rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s %s %s on %s - %s is %g, which is > threshold of %g",
					rule.Protocol, rule.Version, rule.Field, chainName, rule.Field, currentValue, rule.Threshold)
			}
		case DirectionEqual:
			epsilon := 0.01
			if currentValue >= rule.Threshold-epsilon && currentValue <= rule.Threshold+epsilon {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s %s %s on %s - %s is %g, which equals threshold of %g",
					rule.Protocol, rule.Version, rule.Field, chainName, rule.Field, currentValue, rule.Threshold)
			}
		case DirectionLessThanOrEqual:
			if currentValue <= rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s %s %s on %s - %s is %g, which is <= threshold of %g",
					rule.Protocol, rule.Version, rule.Field, chainName, rule.Field, currentValue, rule.Threshold)
			}
		case DirectionLessThan:
			if currentValue < rule.Threshold {
				shouldAlert = true
				message = fmt.Sprintf("🚨 Alert: %s %s %s on %s - %s is %g, which is < threshold of %g",
					rule.Protocol, rule.Version, rule.Field, chainName, rule.Field, currentValue, rule.Threshold)
			}
		}

		if shouldAlert {
			if shouldSuppress(&rule.Enabled, rule.LastTriggered, rule.Frequency) {
				continue
			}

			decisions = append(decisions, &DeFiAlertDecision{
				ShouldAlert:  true,
				Rule:         rule,
				CurrentValue: currentValue,
				ChainName:    chainName,
				Message:      message,
			})

			now := time.Now()
			rule.LastTriggered = &now
		}
	}

	return decisions
}
