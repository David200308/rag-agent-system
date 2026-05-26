package main

import (
	"log"
	"net/http"
	"time"

	"github.com/hibiken/asynq"
	"scheduler/config"
	"scheduler/cronmgr"
	"scheduler/handler"
	"scheduler/store"
	"scheduler/worker"
)

func main() {
	cfg := config.Load()

	st, err := store.New(cfg.DSN)
	if err != nil {
		log.Fatalf("[scheduler] cannot connect to MySQL: %v", err)
	}

	redisOpt := asynq.RedisClientOpt{
		Addr:     cfg.RedisAddr,
		Password: cfg.RedisPassword,
	}

	asynqScheduler := asynq.NewScheduler(redisOpt, &asynq.SchedulerOpts{
		Location: time.UTC,
	})

	mgr := cronmgr.New(asynqScheduler)

	// Reload all active schedules from MySQL on startup
	schedules, err := st.ListAll()
	if err != nil {
		log.Fatalf("[scheduler] failed to load schedules from DB: %v", err)
	}
	mgr.LoadAll(schedules)
	log.Printf("[scheduler] loaded %d schedule(s) from DB", len(schedules))

	// Start cron scheduler in background
	go func() {
		if err := asynqScheduler.Run(); err != nil {
			log.Fatalf("[scheduler] cron scheduler error: %v", err)
		}
	}()

	// Start Asynq task worker in background
	asynqServer := asynq.NewServer(redisOpt, asynq.Config{
		Queues:      map[string]int{worker.Queue: 1},
		Concurrency: 5,
	})
	mux := asynq.NewServeMux()
	mux.HandleFunc(worker.TypeRagTrigger, worker.NewHandler(cfg, st))

	go func() {
		if err := asynqServer.Run(mux); err != nil {
			log.Fatalf("[scheduler] task worker error: %v", err)
		}
	}()

	// HTTP REST API
	h := handler.New(cfg, st, mgr)
	httpMux := http.NewServeMux()
	httpMux.HandleFunc("GET /health", h.Health)
	httpMux.HandleFunc("GET /schedules", h.List)
	httpMux.HandleFunc("POST /schedules", h.Create)
	httpMux.HandleFunc("PATCH /schedules/{id}", h.Update)
	httpMux.HandleFunc("DELETE /schedules/{id}", h.Delete)
	httpMux.HandleFunc("GET /schedules/{id}/runs", h.ListRuns)

	// Internal endpoints — service-key auth, used by Spring Boot workflow engine
	httpMux.HandleFunc("POST /internal/schedules", h.InternalCreate)
	httpMux.HandleFunc("GET /internal/schedules", h.InternalList)
	httpMux.HandleFunc("DELETE /internal/schedules/{id}", h.InternalDelete)

	log.Printf("[scheduler] listening on :%s", cfg.Port)
	if err := http.ListenAndServe(":"+cfg.Port, httpMux); err != nil {
		log.Fatalf("[scheduler] server error: %v", err)
	}
}
