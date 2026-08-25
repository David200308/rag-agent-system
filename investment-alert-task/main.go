package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"investment-alert-task/handler"
	"investment-alert-task/internal/config"
	"investment-alert-task/internal/core"
	"investment-alert-task/internal/data/price"
	"investment-alert-task/monitor"
	"investment-alert-task/notify"
	"investment-alert-task/store"
)

func main() {
	cfg := config.Load()

	st, err := store.New(cfg.DSN)
	if err != nil {
		log.Fatalf("[investment-alert-task] cannot connect to MySQL: %v", err)
	}

	pythClient := price.NewPythClient(cfg.PythAPIURL, cfg.PythAPIKey)
	engine := core.NewDecisionEngine()
	notifier := notify.NewClient(cfg, st)
	defer notifier.Close()

	// Load all enabled rules from MySQL at startup
	priceRules, err := st.ListAllEnabledPriceRules()
	if err != nil {
		log.Fatalf("[investment-alert-task] failed to load price rules: %v", err)
	}
	for _, r := range priceRules {
		engine.AddRule(r)
	}
	defiRules, err := st.ListAllEnabledDeFiRules()
	if err != nil {
		log.Fatalf("[investment-alert-task] failed to load DeFi rules: %v", err)
	}
	for _, r := range defiRules {
		engine.AddDeFiRule(r)
	}
	predictRules, err := st.ListAllEnabledPredictMarketRules()
	if err != nil {
		log.Fatalf("[investment-alert-task] failed to load predict-market rules: %v", err)
	}
	for _, r := range predictRules {
		engine.AddPredictMarketRule(r)
	}
	log.Printf("✅ Loaded %d price, %d DeFi, %d predict-market rule(s) from MySQL",
		len(priceRules), len(defiRules), len(predictRules))

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)

	go monitor.Prices(ctx, pythClient, engine, notifier, st, cfg)
	go monitor.DeFi(ctx, engine, notifier, st, cfg)
	go monitor.PredictMarkets(ctx, engine, notifier, st, cfg)
	if cfg.RuleReloadInterval > 0 {
		go monitor.ReloadRulesLoop(ctx, engine, st, cfg)
	}

	h := handler.New(cfg, st)
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", h.Health)
	mux.HandleFunc("POST /internal/alerts/price", h.CreatePrice)
	mux.HandleFunc("POST /internal/alerts/defi", h.CreateDeFi)
	mux.HandleFunc("POST /internal/alerts/predict-market", h.CreatePredictMarket)
	mux.HandleFunc("GET /internal/alerts", h.List)
	mux.HandleFunc("PATCH /internal/alerts/{type}/{id}", h.Update)
	mux.HandleFunc("DELETE /internal/alerts/{type}/{id}", h.Delete)

	go func() {
		log.Printf("🚀 investment-alert-task listening on :%s (check interval %ds, reload interval %ds)",
			cfg.Port, cfg.CheckInterval, cfg.RuleReloadInterval)
		if err := http.ListenAndServe(":"+cfg.Port, mux); err != nil {
			log.Fatalf("[investment-alert-task] server error: %v", err)
		}
	}()

	<-sigChan
	log.Println("🛑 Shutting down...")
	cancel()
	time.Sleep(1 * time.Second)
	log.Println("✅ Shutdown complete")
}
