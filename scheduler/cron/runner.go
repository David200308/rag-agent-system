package cron

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"scheduler/config"
	"scheduler/model"
	"scheduler/store"
	"sync"

	cronlib "github.com/robfig/cron/v3"
)

// Runner manages all active cron schedules.
type Runner struct {
	cfg     *config.Config
	store   *store.Store
	cron    *cronlib.Cron
	mu      sync.Mutex
	entries map[int64]cronlib.EntryID // schedule DB ID → cron entry ID
}

// NewRunner creates a Runner but does not start it.
func NewRunner(cfg *config.Config, st *store.Store) *Runner {
	return &Runner{
		cfg:     cfg,
		store:   st,
		cron:    cronlib.New(),
		entries: make(map[int64]cronlib.EntryID),
	}
}

// LoadAndStart loads all enabled schedules from the DB and starts the cron daemon.
func (r *Runner) LoadAndStart() {
	schedules, err := r.store.ListEnabled()
	if err != nil {
		log.Printf("[runner] failed to load schedules on startup: %v", err)
	} else {
		for _, sc := range schedules {
			if err := r.Register(sc); err != nil {
				log.Printf("[runner] failed to register schedule id=%d: %v", sc.ID, err)
			}
		}
		log.Printf("[runner] loaded %d active schedule(s)", len(schedules))
	}
	r.cron.Start()
}

// Register adds a new cron job for the given schedule.
func (r *Runner) Register(sc model.Schedule) error {
	entryID, err := r.cron.AddFunc(sc.CronExpr, r.makeJob(sc))
	if err != nil {
		return fmt.Errorf("invalid cron expression %q: %w", sc.CronExpr, err)
	}
	r.mu.Lock()
	r.entries[sc.ID] = entryID
	r.mu.Unlock()
	log.Printf("[runner] registered schedule id=%d cron=%q conv=%s", sc.ID, sc.CronExpr, sc.ConversationID)
	return nil
}

// Unregister removes a cron job by schedule DB ID.
func (r *Runner) Unregister(scheduleID int64) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if entryID, ok := r.entries[scheduleID]; ok {
		r.cron.Remove(entryID)
		delete(r.entries, scheduleID)
		log.Printf("[runner] unregistered schedule id=%d", scheduleID)
	}
}

// makeJob returns the function executed when the cron fires.
func (r *Runner) makeJob(sc model.Schedule) func() {
	return func() {
		log.Printf("[runner] firing schedule id=%d conv=%s", sc.ID, sc.ConversationID)
		if err := r.triggerQuery(sc); err != nil {
			log.Printf("[runner] trigger failed schedule id=%d: %v", sc.ID, err)
		}
	}
}

type triggerPayload struct {
	UserEmail        string `json:"userEmail"`
	ConversationID   string `json:"conversationId"`
	Message          string `json:"message"`
	TopK             int    `json:"topK"`
	UseKnowledgeBase bool   `json:"useKnowledgeBase"`
	UseWebFetch      bool   `json:"useWebFetch"`
}

func (r *Runner) triggerQuery(sc model.Schedule) error {
	payload := triggerPayload{
		UserEmail:        sc.OwnerEmail,
		ConversationID:   sc.ConversationID,
		Message:          sc.Message,
		TopK:             sc.TopK,
		UseKnowledgeBase: sc.UseKnowledgeBase,
		UseWebFetch:      sc.UseWebFetch,
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("marshal trigger payload: %w", err)
	}

	url := r.cfg.BackendURL + "/api/v1/scheduler/trigger"
	req, err := http.NewRequest(http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("create trigger request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Scheduler-Key", r.cfg.ServiceKey)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return fmt.Errorf("call backend trigger: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("backend trigger returned status %d", resp.StatusCode)
	}

	log.Printf("[runner] schedule id=%d trigger OK status=%d", sc.ID, resp.StatusCode)
	return nil
}
