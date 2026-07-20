package store

import (
	"database/sql"
	"encoding/json"
	"fmt"

	"investment-alert-task/internal/config"
	"investment-alert-task/internal/core"

	_ "github.com/go-sql-driver/mysql"
)

const (
	tokenTable         = "alert_rule_token_config"
	defiTable          = "alert_rule_defi_config"
	predictMarketTable = "alert_rule_predict_market_config"
)

type Store struct {
	db *sql.DB
}

func New(dsn string) (*Store, error) {
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, fmt.Errorf("open db: %w", err)
	}
	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("ping db: %w", err)
	}
	return &Store{db: db}, nil
}

func nullStr(s string) sql.NullString {
	if s == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: s, Valid: true}
}

func frequencyToJSON(f *core.Frequency) ([]byte, error) {
	if f == nil {
		return nil, nil
	}
	return json.Marshal(config.FrequencyConfig{Number: numberPtr(f.Number), Unit: f.Unit})
}

func numberPtr(n int) *int {
	if n == 0 {
		return nil
	}
	return &n
}

func frequencyFromJSON(raw []byte) (*config.FrequencyConfig, error) {
	if len(raw) == 0 {
		return nil, nil
	}
	var fc config.FrequencyConfig
	if err := json.Unmarshal(raw, &fc); err != nil {
		return nil, fmt.Errorf("invalid frequency JSON: %w", err)
	}
	return &fc, nil
}

// GetEmailByOwnerUUID resolves a rule owner's email address from the shared `users` table
// (owned by agent-system-rest's schema) — used by notify.Client to address fired-alert
// Kafka events without this service persisting contact info itself.
func (s *Store) GetEmailByOwnerUUID(ownerUUID string) (string, error) {
	var email string
	err := s.db.QueryRow(`SELECT email FROM users WHERE uuid = ?`, ownerUUID).Scan(&email)
	if err == sql.ErrNoRows {
		return "", nil
	}
	if err != nil {
		return "", fmt.Errorf("query email for owner %s: %w", ownerUUID, err)
	}
	return email, nil
}

// ── Price (token) rules ─────────────────────────────────────────────────────

func (s *Store) CreatePriceRule(rule *core.AlertRule) error {
	freqJSON, err := frequencyToJSON(rule.Frequency)
	if err != nil {
		return err
	}
	_, err = s.db.Exec(`
		INSERT INTO alert_rule_token_config
			(id, owner_uuid, org_id, symbol, price_feed_id, asset_type, threshold, direction, enabled, frequency)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		rule.ID, rule.OwnerUuid, nullStr(rule.OrgId), rule.Symbol, rule.PriceFeedID,
		string(rule.AssetType), rule.Threshold, string(rule.Direction), rule.Enabled, freqJSON)
	return err
}

func (s *Store) GetPriceRuleByID(id string) (*core.AlertRule, error) {
	row := s.db.QueryRow(`
		SELECT id, owner_uuid, COALESCE(org_id, ''), symbol, price_feed_id, asset_type, threshold, direction, enabled, frequency
		FROM alert_rule_token_config WHERE id = ?`, id)
	return scanPriceRow(row)
}

func (s *Store) ListPriceRulesByOwner(ownerUuid string) ([]*core.AlertRule, error) {
	rows, err := s.db.Query(`
		SELECT id, owner_uuid, COALESCE(org_id, ''), symbol, price_feed_id, asset_type, threshold, direction, enabled, frequency
		FROM alert_rule_token_config WHERE owner_uuid = ? ORDER BY created_at DESC`, ownerUuid)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanPriceRows(rows)
}

// ListAllEnabledPriceRules returns every enabled price rule across all owners — used by the
// monitor loop's hot-reload to rebuild the in-process decision engine.
func (s *Store) ListAllEnabledPriceRules() ([]*core.AlertRule, error) {
	rows, err := s.db.Query(`
		SELECT id, owner_uuid, COALESCE(org_id, ''), symbol, price_feed_id, asset_type, threshold, direction, enabled, frequency
		FROM alert_rule_token_config WHERE enabled = TRUE`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanPriceRows(rows)
}

func (s *Store) UpdatePriceRule(rule *core.AlertRule) error {
	freqJSON, err := frequencyToJSON(rule.Frequency)
	if err != nil {
		return err
	}
	_, err = s.db.Exec(`
		UPDATE alert_rule_token_config
		SET threshold=?, direction=?, enabled=?, frequency=?
		WHERE id=?`,
		rule.Threshold, string(rule.Direction), rule.Enabled, freqJSON, rule.ID)
	return err
}

func (s *Store) DeletePriceRule(id, ownerUuid string) error {
	_, err := s.db.Exec(`DELETE FROM alert_rule_token_config WHERE id = ? AND owner_uuid = ?`, id, ownerUuid)
	return err
}

func scanPriceRow(row *sql.Row) (*core.AlertRule, error) {
	var id, ownerUuid, orgId, symbol, priceFeedID, assetType, direction string
	var threshold float64
	var enabled bool
	var freqJSON []byte
	if err := row.Scan(&id, &ownerUuid, &orgId, &symbol, &priceFeedID, &assetType, &threshold, &direction, &enabled, &freqJSON); err != nil {
		return nil, err
	}
	return buildPriceRule(id, ownerUuid, orgId, symbol, priceFeedID, assetType, threshold, direction, enabled, freqJSON)
}

func scanPriceRows(rows *sql.Rows) ([]*core.AlertRule, error) {
	var out []*core.AlertRule
	for rows.Next() {
		var id, ownerUuid, orgId, symbol, priceFeedID, assetType, direction string
		var threshold float64
		var enabled bool
		var freqJSON []byte
		if err := rows.Scan(&id, &ownerUuid, &orgId, &symbol, &priceFeedID, &assetType, &threshold, &direction, &enabled, &freqJSON); err != nil {
			return nil, err
		}
		rule, err := buildPriceRule(id, ownerUuid, orgId, symbol, priceFeedID, assetType, threshold, direction, enabled, freqJSON)
		if err != nil {
			return nil, fmt.Errorf("price rule id %s: %w", id, err)
		}
		out = append(out, rule)
	}
	return out, rows.Err()
}

func buildPriceRule(id, ownerUuid, orgId, symbol, priceFeedID, assetType string, threshold float64, direction string, enabled bool, freqJSON []byte) (*core.AlertRule, error) {
	fc, err := frequencyFromJSON(freqJSON)
	if err != nil {
		return nil, err
	}
	rule, err := config.ParsePriceRule(config.AlertRuleConfig{
		ID:          id,
		OwnerUuid:   ownerUuid,
		OrgId:       orgId,
		Symbol:      symbol,
		PriceFeedID: priceFeedID,
		AssetType:   assetType,
		Threshold:   threshold,
		Direction:   direction,
		Enabled:     enabled,
		Frequency:   fc,
	})
	return rule, err
}

// ── DeFi rules ────────────────────────────────────────────────────────────────

func (s *Store) CreateDeFiRule(rule *core.DeFiAlertRule) error {
	freqJSON, err := frequencyToJSON(rule.Frequency)
	if err != nil {
		return err
	}
	paramsJSON, err := json.Marshal(defiParamsFromRule(rule))
	if err != nil {
		return err
	}
	_, err = s.db.Exec(`
		INSERT INTO alert_rule_defi_config
			(id, owner_uuid, org_id, protocol, version, chain_id, params, field, threshold, direction, enabled, frequency)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		rule.ID, rule.OwnerUuid, nullStr(rule.OrgId), rule.Protocol, rule.Version, rule.ChainID,
		paramsJSON, rule.Field, rule.Threshold, string(rule.Direction), rule.Enabled, freqJSON)
	return err
}

func (s *Store) GetDeFiRuleByID(id string) (*core.DeFiAlertRule, error) {
	row := s.db.QueryRow(`
		SELECT id, owner_uuid, COALESCE(org_id, ''), protocol, version, chain_id, params, field, threshold, direction, enabled, frequency
		FROM alert_rule_defi_config WHERE id = ?`, id)
	return scanDeFiRow(row)
}

func (s *Store) ListDeFiRulesByOwner(ownerUuid string) ([]*core.DeFiAlertRule, error) {
	rows, err := s.db.Query(`
		SELECT id, owner_uuid, COALESCE(org_id, ''), protocol, version, chain_id, params, field, threshold, direction, enabled, frequency
		FROM alert_rule_defi_config WHERE owner_uuid = ? ORDER BY created_at DESC`, ownerUuid)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanDeFiRows(rows)
}

func (s *Store) ListAllEnabledDeFiRules() ([]*core.DeFiAlertRule, error) {
	rows, err := s.db.Query(`
		SELECT id, owner_uuid, COALESCE(org_id, ''), protocol, version, chain_id, params, field, threshold, direction, enabled, frequency
		FROM alert_rule_defi_config WHERE enabled = TRUE`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanDeFiRows(rows)
}

func (s *Store) UpdateDeFiRule(rule *core.DeFiAlertRule) error {
	freqJSON, err := frequencyToJSON(rule.Frequency)
	if err != nil {
		return err
	}
	_, err = s.db.Exec(`
		UPDATE alert_rule_defi_config
		SET threshold=?, direction=?, enabled=?, frequency=?
		WHERE id=?`,
		rule.Threshold, string(rule.Direction), rule.Enabled, freqJSON, rule.ID)
	return err
}

func (s *Store) DeleteDeFiRule(id, ownerUuid string) error {
	_, err := s.db.Exec(`DELETE FROM alert_rule_defi_config WHERE id = ? AND owner_uuid = ?`, id, ownerUuid)
	return err
}

func defiParamsFromRule(rule *core.DeFiAlertRule) config.DeFiAlertRuleParams {
	return config.DeFiAlertRuleParams{
		Category:                rule.Category,
		MarketTokenContract:     rule.MarketTokenContract,
		MarketTokenName:         rule.MarketTokenName,
		MarketTokenPair:         rule.MarketTokenPair,
		VaultName:               rule.VaultName,
		BorrowTokenContract:     rule.BorrowTokenContract,
		CollateralTokenContract: rule.CollateralTokenContract,
		OracleAddress:           rule.OracleAddress,
		IRMAddress:              rule.IRMAddress,
		LLTV:                    rule.LLTV,
		MarketContractAddress:   rule.MarketContractAddress,
		VaultTokenAddress:       rule.VaultTokenAddress,
		DepositTokenContract:    rule.DepositTokenContract,
		LedgerAddress:           rule.LedgerAddress,
	}
}

func scanDeFiRow(row *sql.Row) (*core.DeFiAlertRule, error) {
	var id, ownerUuid, orgId, protocol, version, chainID, field, direction string
	var threshold float64
	var enabled bool
	var paramsJSON, freqJSON []byte
	if err := row.Scan(&id, &ownerUuid, &orgId, &protocol, &version, &chainID, &paramsJSON, &field, &threshold, &direction, &enabled, &freqJSON); err != nil {
		return nil, err
	}
	return buildDeFiRule(id, ownerUuid, orgId, protocol, version, chainID, paramsJSON, field, threshold, direction, enabled, freqJSON)
}

func scanDeFiRows(rows *sql.Rows) ([]*core.DeFiAlertRule, error) {
	var out []*core.DeFiAlertRule
	for rows.Next() {
		var id, ownerUuid, orgId, protocol, version, chainID, field, direction string
		var threshold float64
		var enabled bool
		var paramsJSON, freqJSON []byte
		if err := rows.Scan(&id, &ownerUuid, &orgId, &protocol, &version, &chainID, &paramsJSON, &field, &threshold, &direction, &enabled, &freqJSON); err != nil {
			return nil, err
		}
		rule, err := buildDeFiRule(id, ownerUuid, orgId, protocol, version, chainID, paramsJSON, field, threshold, direction, enabled, freqJSON)
		if err != nil {
			return nil, fmt.Errorf("defi rule id %s: %w", id, err)
		}
		out = append(out, rule)
	}
	return out, rows.Err()
}

func buildDeFiRule(id, ownerUuid, orgId, protocol, version, chainID string, paramsJSON []byte, field string, threshold float64, direction string, enabled bool, freqJSON []byte) (*core.DeFiAlertRule, error) {
	var params config.DeFiAlertRuleParams
	if len(paramsJSON) > 0 {
		if err := json.Unmarshal(paramsJSON, &params); err != nil {
			return nil, fmt.Errorf("invalid params JSON: %w", err)
		}
	}
	fc, err := frequencyFromJSON(freqJSON)
	if err != nil {
		return nil, err
	}
	return config.ParseDeFiRule(config.DeFiAlertRuleConfig{
		ID:        id,
		OwnerUuid: ownerUuid,
		OrgId:     orgId,
		Protocol:  protocol,
		Category:  params.Category,
		Version:   version,
		ChainID:   chainID,
		Field:     field,
		Threshold: threshold,
		Direction: direction,
		Enabled:   enabled,
		Frequency: fc,
		Params:    params,
	})
}

// ── Prediction market rules ──────────────────────────────────────────────────

func (s *Store) CreatePredictMarketRule(rule *core.PredictMarketAlertRule) error {
	freqJSON, err := frequencyToJSON(rule.Frequency)
	if err != nil {
		return err
	}
	paramsJSON, err := json.Marshal(config.PredictMarketAlertRuleParams{
		NegRisk: rule.NegRisk, QuestionID: rule.QuestionID, Question: rule.Question,
		ConditionID: rule.ConditionID, Outcome: rule.Outcome, TokenID: rule.TokenID,
	})
	if err != nil {
		return err
	}
	_, err = s.db.Exec(`
		INSERT INTO alert_rule_predict_market_config
			(id, owner_uuid, org_id, predict_market, params, field, threshold, direction, enabled, frequency)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		rule.ID, rule.OwnerUuid, nullStr(rule.OrgId), rule.PredictMarket, paramsJSON,
		rule.Field, rule.Threshold, string(rule.Direction), rule.Enabled, freqJSON)
	return err
}

func (s *Store) GetPredictMarketRuleByID(id string) (*core.PredictMarketAlertRule, error) {
	row := s.db.QueryRow(`
		SELECT id, owner_uuid, COALESCE(org_id, ''), predict_market, params, field, threshold, direction, enabled, frequency
		FROM alert_rule_predict_market_config WHERE id = ?`, id)
	return scanPredictMarketRow(row)
}

func (s *Store) ListPredictMarketRulesByOwner(ownerUuid string) ([]*core.PredictMarketAlertRule, error) {
	rows, err := s.db.Query(`
		SELECT id, owner_uuid, COALESCE(org_id, ''), predict_market, params, field, threshold, direction, enabled, frequency
		FROM alert_rule_predict_market_config WHERE owner_uuid = ? ORDER BY created_at DESC`, ownerUuid)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanPredictMarketRows(rows)
}

func (s *Store) ListAllEnabledPredictMarketRules() ([]*core.PredictMarketAlertRule, error) {
	rows, err := s.db.Query(`
		SELECT id, owner_uuid, COALESCE(org_id, ''), predict_market, params, field, threshold, direction, enabled, frequency
		FROM alert_rule_predict_market_config WHERE enabled = TRUE`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanPredictMarketRows(rows)
}

func (s *Store) UpdatePredictMarketRule(rule *core.PredictMarketAlertRule) error {
	freqJSON, err := frequencyToJSON(rule.Frequency)
	if err != nil {
		return err
	}
	_, err = s.db.Exec(`
		UPDATE alert_rule_predict_market_config
		SET threshold=?, direction=?, enabled=?, frequency=?
		WHERE id=?`,
		rule.Threshold, string(rule.Direction), rule.Enabled, freqJSON, rule.ID)
	return err
}

func (s *Store) DeletePredictMarketRule(id, ownerUuid string) error {
	_, err := s.db.Exec(`DELETE FROM alert_rule_predict_market_config WHERE id = ? AND owner_uuid = ?`, id, ownerUuid)
	return err
}

func scanPredictMarketRow(row *sql.Row) (*core.PredictMarketAlertRule, error) {
	var id, ownerUuid, orgId, predictMarket, field, direction string
	var threshold float64
	var enabled bool
	var paramsJSON, freqJSON []byte
	if err := row.Scan(&id, &ownerUuid, &orgId, &predictMarket, &paramsJSON, &field, &threshold, &direction, &enabled, &freqJSON); err != nil {
		return nil, err
	}
	return buildPredictMarketRule(id, ownerUuid, orgId, predictMarket, paramsJSON, field, threshold, direction, enabled, freqJSON)
}

func scanPredictMarketRows(rows *sql.Rows) ([]*core.PredictMarketAlertRule, error) {
	var out []*core.PredictMarketAlertRule
	for rows.Next() {
		var id, ownerUuid, orgId, predictMarket, field, direction string
		var threshold float64
		var enabled bool
		var paramsJSON, freqJSON []byte
		if err := rows.Scan(&id, &ownerUuid, &orgId, &predictMarket, &paramsJSON, &field, &threshold, &direction, &enabled, &freqJSON); err != nil {
			return nil, err
		}
		rule, err := buildPredictMarketRule(id, ownerUuid, orgId, predictMarket, paramsJSON, field, threshold, direction, enabled, freqJSON)
		if err != nil {
			return nil, fmt.Errorf("predict market rule id %s: %w", id, err)
		}
		out = append(out, rule)
	}
	return out, rows.Err()
}

func buildPredictMarketRule(id, ownerUuid, orgId, predictMarket string, paramsJSON []byte, field string, threshold float64, direction string, enabled bool, freqJSON []byte) (*core.PredictMarketAlertRule, error) {
	var params config.PredictMarketAlertRuleParams
	if len(paramsJSON) > 0 {
		if err := json.Unmarshal(paramsJSON, &params); err != nil {
			return nil, fmt.Errorf("invalid params JSON: %w", err)
		}
	}
	fc, err := frequencyFromJSON(freqJSON)
	if err != nil {
		return nil, err
	}
	return config.ParsePredictMarketRule(config.PredictMarketAlertRuleConfig{
		ID:            id,
		OwnerUuid:     ownerUuid,
		OrgId:         orgId,
		PredictMarket: predictMarket,
		Params:        params,
		Field:         field,
		Threshold:     threshold,
		Direction:     direction,
		Enabled:       enabled,
		Frequency:     fc,
	})
}
