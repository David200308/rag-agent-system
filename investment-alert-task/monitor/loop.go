package monitor

import (
	"context"
	"fmt"
	"log"
	"time"

	"investment-alert-task/internal/config"
	"investment-alert-task/internal/core"
	"investment-alert-task/internal/data/defi"
	"investment-alert-task/internal/data/prediction/polymarket"
	"investment-alert-task/internal/data/price"
	"investment-alert-task/notify"
	"investment-alert-task/store"
)

// Prices continuously monitors crypto/stock prices via Pyth and fires alerts.
func Prices(ctx context.Context, pythClient *price.PythClient, engine *core.DecisionEngine, notifier *notify.Client, cfg *config.Config) {
	ticker := time.NewTicker(time.Duration(cfg.CheckInterval) * time.Second)
	defer ticker.Stop()

	checkPrices(ctx, pythClient, engine, notifier)
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			checkPrices(ctx, pythClient, engine, notifier)
		}
	}
}

func checkPrices(ctx context.Context, pythClient *price.PythClient, engine *core.DecisionEngine, notifier *notify.Client) {
	rules := engine.GetRules()
	symbolToFeedID := make(map[string]string)
	for _, rule := range rules {
		if rule.Enabled {
			symbolToFeedID[rule.Symbol] = rule.PriceFeedID
		}
	}
	if len(symbolToFeedID) == 0 {
		return
	}

	log.Printf("🔍 Checking prices for %d symbol(s)...", len(symbolToFeedID))
	prices, err := pythClient.GetMultiplePrices(ctx, symbolToFeedID)
	if err != nil {
		log.Printf("⚠️  Failed to fetch prices: %v", err)
		return
	}

	for symbol, priceData := range prices {
		if err := priceData.Validate(); err != nil {
			log.Printf("⚠️  Invalid price data for %s: %v", symbol, err)
			continue
		}
		log.Printf("💰 %s: $%g", symbol, priceData.Price)
	}

	decisions := engine.EvaluateAll(prices)
	for _, decision := range decisions {
		if !decision.ShouldAlert {
			continue
		}
		log.Printf("🚨 Alert triggered: %s", decision.Message)
		err := notifier.Notify(ctx, notify.TriggerPayload{
			OwnerUuid: decision.Rule.OwnerUuid,
			OrgId:     decision.Rule.OrgId,
			RuleType:  "price",
			RuleID:    decision.Rule.ID,
			Symbol:    decision.CurrentPrice.Symbol,
			Message:   decision.Message,
		})
		if err != nil {
			log.Printf("❌ Failed to notify owner %s: %v", decision.Rule.OwnerUuid, err)
		} else {
			log.Printf("✅ Alert notified for %s (owner %s)", decision.CurrentPrice.Symbol, decision.Rule.OwnerUuid)
		}
	}
}

// DeFi continuously monitors DeFi protocol values and fires alerts.
func DeFi(ctx context.Context, engine *core.DecisionEngine, notifier *notify.Client, cfg *config.Config) {
	ticker := time.NewTicker(time.Duration(cfg.CheckInterval) * time.Second)
	defer ticker.Stop()

	checkDeFi(ctx, engine, notifier)
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			checkDeFi(ctx, engine, notifier)
		}
	}
}

func checkDeFi(ctx context.Context, engine *core.DecisionEngine, notifier *notify.Client) {
	defiRules := engine.GetDeFiRules()
	if len(defiRules) == 0 {
		return
	}

	clientManager := defi.NewClientManager()
	defer clientManager.Close()

	log.Printf("🔍 Checking DeFi protocols for %d rule(s)...", len(defiRules))
	for _, rule := range defiRules {
		if !rule.Enabled {
			continue
		}

		value, chainName, err := clientManager.GetFieldValue(ctx, rule)
		if err != nil {
			log.Printf("⚠️  %v", err)
			continue
		}

		categoryStr := defi.GetCategoryString(rule)
		displayName := defi.GetDisplayName(rule)
		log.Printf("💰 %s%s %s on %s - %s%s: %g", rule.Protocol, categoryStr, rule.Version, chainName, rule.Field, displayName, value)

		identifier := defi.GetIdentifier(rule)
		decisions := engine.EvaluateDeFi(rule.ChainID, identifier, rule.Field, value, chainName)
		for _, decision := range decisions {
			if !decision.ShouldAlert {
				continue
			}
			log.Printf("🚨 Alert triggered: %s", decision.Message)
			err := notifier.Notify(ctx, notify.TriggerPayload{
				OwnerUuid: decision.Rule.OwnerUuid,
				OrgId:     decision.Rule.OrgId,
				RuleType:  "defi",
				RuleID:    decision.Rule.ID,
				Symbol:    fmt.Sprintf("%s %s", decision.Rule.Protocol, decision.Rule.Field),
				Message:   decision.Message,
			})
			if err != nil {
				log.Printf("❌ Failed to notify owner %s: %v", decision.Rule.OwnerUuid, err)
			} else {
				log.Printf("✅ DeFi alert notified for %s %s (owner %s)", decision.Rule.Protocol, decision.Rule.Field, decision.Rule.OwnerUuid)
			}
		}
	}
}

// PredictMarkets continuously monitors Polymarket midpoints and fires alerts.
func PredictMarkets(ctx context.Context, engine *core.DecisionEngine, notifier *notify.Client, cfg *config.Config) {
	ticker := time.NewTicker(time.Duration(cfg.CheckInterval) * time.Second)
	defer ticker.Stop()

	checkPredictMarkets(ctx, engine, notifier)
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			checkPredictMarkets(ctx, engine, notifier)
		}
	}
}

func checkPredictMarkets(ctx context.Context, engine *core.DecisionEngine, notifier *notify.Client) {
	rules := engine.GetPredictMarketRules()
	if len(rules) == 0 {
		return
	}

	tokenIDSet := make(map[string]struct{})
	for _, rule := range rules {
		if rule.Enabled {
			tokenIDSet[rule.TokenID] = struct{}{}
		}
	}
	if len(tokenIDSet) == 0 {
		return
	}
	tokenIDs := make([]string, 0, len(tokenIDSet))
	for id := range tokenIDSet {
		tokenIDs = append(tokenIDs, id)
	}

	log.Printf("🔍 Checking Polymarket prices for %d token(s)...", len(tokenIDs))
	client := polymarket.NewClient()
	prices, err := client.GetTokenPrices(ctx, tokenIDs)
	if err != nil {
		log.Printf("⚠️  Failed to fetch Polymarket prices: %v", err)
		return
	}

	for _, rule := range rules {
		if !rule.Enabled {
			continue
		}
		tp, ok := prices[rule.TokenID]
		if !ok {
			log.Printf("⚠️  No price data for Polymarket token %s", rule.TokenID)
			continue
		}

		log.Printf("💰 [%s] [%s] %s - midpoint=%.4f buy=%.4f sell=%.4f",
			rule.PredictMarket, rule.Outcome, rule.Question, tp.Midpoint, tp.BuyPrice, tp.SellPrice)

		decisions := engine.EvaluatePredictMarket(rule.TokenID, tp.Midpoint, tp.BuyPrice, tp.SellPrice)
		for _, decision := range decisions {
			if !decision.ShouldAlert {
				continue
			}
			log.Printf("🚨 Alert triggered: %s", decision.Message)
			err := notifier.Notify(ctx, notify.TriggerPayload{
				OwnerUuid: decision.Rule.OwnerUuid,
				OrgId:     decision.Rule.OrgId,
				RuleType:  "predict-market",
				RuleID:    decision.Rule.ID,
				Symbol:    decision.Rule.Question,
				Message:   decision.Message,
			})
			if err != nil {
				log.Printf("❌ Failed to notify owner %s: %v", decision.Rule.OwnerUuid, err)
			} else {
				log.Printf("✅ Predict-market alert notified for %s (owner %s)", decision.Rule.Question, decision.Rule.OwnerUuid)
			}
		}
	}
}

// ReloadRulesLoop periodically re-reads all enabled rules from MySQL and hot-swaps them
// into the engine, preserving LastTriggered so frequency suppression survives.
func ReloadRulesLoop(ctx context.Context, engine *core.DecisionEngine, st *store.Store, cfg *config.Config) {
	ticker := time.NewTicker(time.Duration(cfg.RuleReloadInterval) * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			reloadRules(engine, st)
		}
	}
}

func reloadRules(engine *core.DecisionEngine, st *store.Store) {
	priceRules, err := st.ListAllEnabledPriceRules()
	if err != nil {
		log.Printf("⚠️  Hot-reload: failed to load price rules: %v", err)
		return
	}
	defiRules, err := st.ListAllEnabledDeFiRules()
	if err != nil {
		log.Printf("⚠️  Hot-reload: failed to load DeFi rules: %v", err)
		return
	}
	predictRules, err := st.ListAllEnabledPredictMarketRules()
	if err != nil {
		log.Printf("⚠️  Hot-reload: failed to load predict-market rules: %v", err)
		return
	}
	engine.ReplaceRules(priceRules, defiRules, predictRules)
	log.Printf("🔄 Hot-reload: %d price, %d DeFi, %d predict-market rule(s) active",
		len(priceRules), len(defiRules), len(predictRules))
}
