package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"investment-alert-task/internal/core"
)

// Config holds all configuration for the application
type Config struct {
	Port string

	// MySQL (shared with agent-system-rest)
	DSN string

	ServiceKey string // X-Alert-Key, shared secret with agent-system-rest (inbound CRUD auth)

	// Kafka (shared with agent-system-rest / agent-system-notification-consumer) — fired
	// alerts are published directly to notifications.alert-triggered, see notify.Client.
	KafkaBootstrapServers string

	// Pyth Oracle Configuration (crypto AND equity feeds share the same API shape)
	PythAPIURL string
	PythAPIKey string

	CheckInterval      int // seconds between price/DeFi/prediction-market polls
	RuleReloadInterval int // seconds between MySQL rule re-reads (0 = disabled)
}

// Load loads configuration from environment variables (with Docker-secrets support via _FILE suffix).
func Load() *Config {
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?parseTime=true",
		getEnv("MYSQL_USER", "ragagent"),
		getSecret("MYSQL_PASSWORD", "ragagent"),
		getEnv("MYSQL_HOST", "localhost"),
		getEnv("MYSQL_PORT", "3306"),
		getEnv("MYSQL_DB", "ragagent"),
	)

	return &Config{
		Port:                  getEnv("PORT", "8085"),
		DSN:                   dsn,
		ServiceKey:            getSecret("ALERT_SERVICE_KEY", "alert-secret-key"),
		KafkaBootstrapServers: getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
		PythAPIURL:            getEnv("PYTH_API_URL", "https://hermes.pyth.network"),
		PythAPIKey:            getSecret("PYTH_API_KEY", ""),
		CheckInterval:         getEnvInt("CHECK_INTERVAL", 60),
		RuleReloadInterval:    getEnvInt("RULE_RELOAD_INTERVAL", 60),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

// getSecret supports Docker secrets: if {KEY}_FILE is set, reads the value from that file.
func getSecret(key, fallback string) string {
	if path := os.Getenv(key + "_FILE"); path != "" {
		data, err := os.ReadFile(path)
		if err == nil {
			return strings.TrimSpace(string(data))
		}
	}
	return getEnv(key, fallback)
}

func getEnvInt(key string, fallback int) int {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	if n, err := strconv.Atoi(v); err == nil {
		return n
	}
	return fallback
}

// ── Rule config shapes (validated JSON request/DB shapes) ──────────────────────

// FrequencyConfig represents the frequency configuration for an alert rule
type FrequencyConfig struct {
	Number *int               `json:"number,omitempty"` // Required for DAY and HOUR, not needed for ONCE
	Unit   core.FrequencyUnit `json:"unit"`             // DAY, HOUR, ONCE, or NEVER
}

// AlertRuleConfig represents a price alert rule (crypto or stock) in JSON format
type AlertRuleConfig struct {
	ID          string           `json:"id,omitempty"`
	OwnerUuid   string           `json:"ownerUuid"`
	OrgId       string           `json:"orgId,omitempty"`
	Symbol      string           `json:"symbol"`
	PriceFeedID string           `json:"priceFeedId"`
	AssetType   string           `json:"assetType,omitempty"` // CRYPTO or STOCK, defaults to CRYPTO
	Threshold   float64          `json:"threshold"`
	Direction   string           `json:"direction"` // ">=", ">", "=", "<=", "<"
	Enabled     bool             `json:"enabled"`
	Frequency   *FrequencyConfig `json:"frequency,omitempty"`
}

// DeFiAlertRuleParams holds protocol-specific parameters nested under "params" in JSON
type DeFiAlertRuleParams struct {
	Category string `json:"category,omitempty"`
	// Common
	MarketTokenContract string `json:"market_token_contract,omitempty"`
	MarketTokenName     string `json:"market_token_name,omitempty"`
	MarketTokenPair     string `json:"market_token_pair,omitempty"`
	VaultName           string `json:"vault_name,omitempty"`
	// Morpho-specific fields
	MarketID                string `json:"market_id,omitempty"`
	BorrowTokenContract     string `json:"borrow_token_contract,omitempty"`
	CollateralTokenContract string `json:"collateral_token_contract,omitempty"`
	OracleAddress           string `json:"oracle_address,omitempty"`
	IRMAddress              string `json:"irm_address,omitempty"`
	LLTV                    string `json:"lltv,omitempty"`
	MarketContractAddress   string `json:"market_contract_address,omitempty"`
	VaultTokenAddress       string `json:"vault_token_address,omitempty"`
	DepositTokenContract    string `json:"deposit_token_contract,omitempty"`
	// Hyperliquid-specific
	LedgerAddress string `json:"ledger_address,omitempty"`
}

// DeFiAlertRuleConfig represents a DeFi protocol alert rule in JSON format
type DeFiAlertRuleConfig struct {
	ID        string              `json:"id,omitempty"`
	OwnerUuid string              `json:"ownerUuid"`
	OrgId     string              `json:"orgId,omitempty"`
	Protocol  string              `json:"protocol"`
	Category  string              `json:"category,omitempty"`
	Version   string              `json:"version"`
	ChainID   string              `json:"chainId"`
	Field     string              `json:"field"` // TVL, APY, UTILIZATION, LIQUIDITY
	Threshold float64             `json:"threshold"`
	Direction string              `json:"direction"`
	Enabled   bool                `json:"enabled"`
	Frequency *FrequencyConfig    `json:"frequency,omitempty"`
	Params    DeFiAlertRuleParams `json:"params"`
}

// PredictMarketAlertRuleParams holds prediction-market-specific parameters (params JSON column).
type PredictMarketAlertRuleParams struct {
	NegRisk     bool   `json:"negRisk,omitempty"`
	QuestionID  string `json:"question_id,omitempty"`
	Question    string `json:"question,omitempty"`
	ConditionID string `json:"condition_id,omitempty"`
	Outcome     string `json:"outcome,omitempty"` // "YES" or "NO"
	TokenID     string `json:"token_id,omitempty"`
}

// PredictMarketAlertRuleConfig represents a prediction market alert rule.
type PredictMarketAlertRuleConfig struct {
	ID            string                       `json:"id,omitempty"`
	OwnerUuid     string                       `json:"ownerUuid"`
	OrgId         string                       `json:"orgId,omitempty"`
	PredictMarket string                       `json:"predictMarket"`
	Params        PredictMarketAlertRuleParams `json:"params"`
	Field         string                       `json:"field"` // "MIDPOINT"
	Threshold     float64                      `json:"threshold"`
	Direction     string                       `json:"direction"`
	Enabled       bool                         `json:"enabled"`
	Frequency     *FrequencyConfig             `json:"frequency,omitempty"`
}

func parseDirection(d string) (core.Direction, error) {
	switch d {
	case ">=":
		return core.DirectionGreaterThanOrEqual, nil
	case ">":
		return core.DirectionGreaterThan, nil
	case "=":
		return core.DirectionEqual, nil
	case "<=":
		return core.DirectionLessThanOrEqual, nil
	case "<":
		return core.DirectionLessThan, nil
	default:
		return "", fmt.Errorf("invalid direction '%s', must be one of: >=, >, =, <=, <", d)
	}
}

func parseFrequency(fc *FrequencyConfig) (*core.Frequency, error) {
	if fc == nil {
		return nil, nil
	}
	switch fc.Unit {
	case core.FrequencyUnitDay, core.FrequencyUnitHour:
		if fc.Number == nil || *fc.Number <= 0 {
			return nil, fmt.Errorf("frequency.number is required and must be positive for unit %s", fc.Unit)
		}
		return &core.Frequency{Number: *fc.Number, Unit: fc.Unit}, nil
	case core.FrequencyUnitOnce, core.FrequencyUnitNever:
		return &core.Frequency{Unit: fc.Unit}, nil
	default:
		return nil, fmt.Errorf("invalid frequency.unit '%s', must be one of: DAY, HOUR, ONCE, NEVER", fc.Unit)
	}
}

// ParsePriceRule converts AlertRuleConfig to core.AlertRule.
func ParsePriceRule(rc AlertRuleConfig) (*core.AlertRule, error) {
	direction, err := parseDirection(rc.Direction)
	if err != nil {
		return nil, fmt.Errorf("%w (symbol %s)", err, rc.Symbol)
	}
	if rc.Symbol == "" {
		return nil, fmt.Errorf("symbol cannot be empty in alert rule")
	}
	if rc.Threshold <= 0 {
		return nil, fmt.Errorf("threshold must be positive for symbol %s", rc.Symbol)
	}
	if rc.PriceFeedID == "" {
		return nil, fmt.Errorf("price_feed_id is required for symbol %s", rc.Symbol)
	}
	if rc.OwnerUuid == "" {
		return nil, fmt.Errorf("ownerUuid is required for symbol %s", rc.Symbol)
	}

	assetType := core.AssetType(rc.AssetType)
	if assetType == "" {
		assetType = core.AssetTypeCrypto
	}
	if assetType != core.AssetTypeCrypto && assetType != core.AssetTypeStock {
		return nil, fmt.Errorf("invalid assetType '%s', must be CRYPTO or STOCK", rc.AssetType)
	}

	frequency, err := parseFrequency(rc.Frequency)
	if err != nil {
		return nil, fmt.Errorf("%w (symbol %s)", err, rc.Symbol)
	}

	return &core.AlertRule{
		ID:          rc.ID,
		OwnerUuid:   rc.OwnerUuid,
		OrgId:       rc.OrgId,
		Symbol:      rc.Symbol,
		PriceFeedID: rc.PriceFeedID,
		AssetType:   assetType,
		Threshold:   rc.Threshold,
		Direction:   direction,
		Enabled:     rc.Enabled,
		Frequency:   frequency,
	}, nil
}

// ParseDeFiRule converts DeFiAlertRuleConfig to core.DeFiAlertRule.
func ParseDeFiRule(rc DeFiAlertRuleConfig) (*core.DeFiAlertRule, error) {
	direction, err := parseDirection(rc.Direction)
	if err != nil {
		return nil, fmt.Errorf("%w (protocol %s %s)", err, rc.Protocol, rc.Version)
	}
	if rc.Protocol == "" {
		return nil, fmt.Errorf("protocol cannot be empty in DeFi alert rule")
	}
	if rc.Version == "" {
		return nil, fmt.Errorf("version cannot be empty in DeFi alert rule")
	}
	if rc.ChainID == "" {
		return nil, fmt.Errorf("chain_id cannot be empty in DeFi alert rule")
	}
	if rc.OwnerUuid == "" {
		return nil, fmt.Errorf("ownerUuid is required for DeFi alert rule")
	}

	category := rc.Category
	if category == "" {
		category = rc.Params.Category
	}

	if rc.Protocol == "morpho" {
		if category != "market" && category != "vault" {
			return nil, fmt.Errorf("category must be 'market' or 'vault' for Morpho protocol")
		}
		if category == "market" {
			if rc.Params.MarketID == "" && rc.Params.MarketTokenContract == "" {
				return nil, fmt.Errorf("market_id or market_token_contract is required for Morpho market (in params)")
			}
			if rc.Params.MarketID != "" && rc.Params.MarketTokenContract == "" {
				rc.Params.MarketTokenContract = rc.Params.MarketID
			}
		} else if category == "vault" {
			if rc.Params.VaultTokenAddress == "" {
				return nil, fmt.Errorf("vault_token_address is required for Morpho vault (in params)")
			}
			if rc.Params.MarketTokenContract == "" {
				rc.Params.MarketTokenContract = rc.Params.VaultTokenAddress
			}
		}
	} else if rc.Protocol == "kamino" {
		if category != "vault" {
			return nil, fmt.Errorf("category must be 'vault' for Kamino protocol")
		}
		if rc.Params.VaultTokenAddress == "" {
			return nil, fmt.Errorf("vault_token_address is required for Kamino vault (in params)")
		}
		if rc.Params.MarketTokenContract == "" {
			rc.Params.MarketTokenContract = rc.Params.VaultTokenAddress
		}
		if rc.Params.DepositTokenContract == "" {
			return nil, fmt.Errorf("deposit_token_contract is required for Kamino vault (in params)")
		}
	} else if rc.Protocol == "pendle" {
		if category != "pt" {
			return nil, fmt.Errorf("category must be 'pt' for Pendle protocol")
		}
		if rc.Params.MarketTokenContract == "" {
			return nil, fmt.Errorf("market_token_contract is required for Pendle PT market (in params)")
		}
	} else if rc.Protocol == "hyperliquid" {
		if category != "vault" {
			return nil, fmt.Errorf("category must be 'vault' for Hyperliquid protocol")
		}
		if rc.Params.LedgerAddress == "" {
			return nil, fmt.Errorf("ledger_address is required for Hyperliquid vault (in params)")
		}
		if rc.Params.MarketTokenContract == "" {
			rc.Params.MarketTokenContract = rc.Params.LedgerAddress
		}
	} else {
		if rc.Params.MarketTokenContract == "" {
			return nil, fmt.Errorf("market_token_contract cannot be empty in DeFi alert rule (in params)")
		}
	}

	if rc.Protocol == "pendle" || rc.Protocol == "hyperliquid" {
		if rc.Field != "APY" && rc.Field != "TVL" {
			return nil, fmt.Errorf("invalid field '%s' for %s protocol, must be one of: APY, TVL", rc.Field, rc.Protocol)
		}
	} else if rc.Field != "TVL" && rc.Field != "APY" && rc.Field != "UTILIZATION" && rc.Field != "LIQUIDITY" {
		return nil, fmt.Errorf("invalid field '%s' for protocol %s %s, must be one of: TVL, APY, UTILIZATION, LIQUIDITY", rc.Field, rc.Protocol, rc.Version)
	}

	if rc.Threshold < 0 {
		return nil, fmt.Errorf("threshold must be non-negative for protocol %s %s", rc.Protocol, rc.Version)
	}

	frequency, err := parseFrequency(rc.Frequency)
	if err != nil {
		return nil, fmt.Errorf("%w (protocol %s %s)", err, rc.Protocol, rc.Version)
	}

	rule := &core.DeFiAlertRule{
		ID:                  rc.ID,
		OwnerUuid:           rc.OwnerUuid,
		OrgId:               rc.OrgId,
		Protocol:            rc.Protocol,
		Category:            category,
		Version:             rc.Version,
		ChainID:             rc.ChainID,
		MarketTokenContract: rc.Params.MarketTokenContract,
		Field:               rc.Field,
		Threshold:           rc.Threshold,
		Direction:           direction,
		Enabled:             rc.Enabled,
		Frequency:           frequency,
		MarketTokenName:     rc.Params.MarketTokenName,
		MarketTokenPair:     rc.Params.MarketTokenPair,
		VaultName:           rc.Params.VaultName,
	}

	if rc.Protocol == "morpho" {
		rule.BorrowTokenContract = rc.Params.BorrowTokenContract
		rule.CollateralTokenContract = rc.Params.CollateralTokenContract
		rule.OracleAddress = rc.Params.OracleAddress
		rule.IRMAddress = rc.Params.IRMAddress
		rule.LLTV = rc.Params.LLTV
		rule.MarketContractAddress = rc.Params.MarketContractAddress
		rule.VaultTokenAddress = rc.Params.VaultTokenAddress
		rule.DepositTokenContract = rc.Params.DepositTokenContract
	}
	if rc.Protocol == "kamino" {
		rule.VaultTokenAddress = rc.Params.VaultTokenAddress
		rule.DepositTokenContract = rc.Params.DepositTokenContract
	}
	if rc.Protocol == "hyperliquid" {
		rule.LedgerAddress = rc.Params.LedgerAddress
	}

	return rule, nil
}

// ParsePredictMarketRule converts PredictMarketAlertRuleConfig to core.PredictMarketAlertRule.
func ParsePredictMarketRule(rc PredictMarketAlertRuleConfig) (*core.PredictMarketAlertRule, error) {
	direction, err := parseDirection(rc.Direction)
	if err != nil {
		return nil, err
	}
	if rc.PredictMarket == "" {
		return nil, fmt.Errorf("predict_market cannot be empty")
	}
	if rc.Params.TokenID == "" {
		return nil, fmt.Errorf("params.token_id cannot be empty for predict market rule")
	}
	if rc.Field != "MIDPOINT" {
		return nil, fmt.Errorf("invalid field '%s' for predict market rule, must be: MIDPOINT", rc.Field)
	}
	if rc.Threshold < 0 {
		return nil, fmt.Errorf("threshold must be non-negative for predict market rule")
	}
	if rc.OwnerUuid == "" {
		return nil, fmt.Errorf("ownerUuid is required for predict market rule")
	}

	frequency, err := parseFrequency(rc.Frequency)
	if err != nil {
		return nil, err
	}

	return &core.PredictMarketAlertRule{
		ID:            rc.ID,
		OwnerUuid:     rc.OwnerUuid,
		OrgId:         rc.OrgId,
		PredictMarket: rc.PredictMarket,
		TokenID:       rc.Params.TokenID,
		Field:         rc.Field,
		Threshold:     rc.Threshold,
		Direction:     direction,
		Enabled:       rc.Enabled,
		Frequency:     frequency,
		NegRisk:       rc.Params.NegRisk,
		QuestionID:    rc.Params.QuestionID,
		Question:      rc.Params.Question,
		ConditionID:   rc.Params.ConditionID,
		Outcome:       rc.Params.Outcome,
	}, nil
}
