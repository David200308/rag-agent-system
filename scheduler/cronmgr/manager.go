package cronmgr

import (
	"fmt"
	"log"
	"sync"

	"github.com/google/uuid"
	"github.com/hibiken/asynq"
	"scheduler/model"
	"scheduler/worker"
)

// Manager wraps an asynq.Scheduler to provide goroutine-safe dynamic
// registration and removal of per-schedule cron entries.
type Manager struct {
	scheduler *asynq.Scheduler
	entries   map[string]string // scheduleID → asynq entryID
	mu        sync.RWMutex
}

func New(scheduler *asynq.Scheduler) *Manager {
	return &Manager{
		scheduler: scheduler,
		entries:   make(map[string]string),
	}
}

// Add registers a new cron entry for the given schedule.
func (m *Manager) Add(sc *model.Schedule) error {
	task, err := buildTask(sc)
	if err != nil {
		return fmt.Errorf("build task: %w", err)
	}
	cronSpec := formatCron(sc.CronExpr, sc.Timezone)
	entryID, err := m.scheduler.Register(cronSpec, task)
	if err != nil {
		return fmt.Errorf("register cron %q: %w", cronSpec, err)
	}
	m.mu.Lock()
	m.entries[sc.ID] = entryID
	m.mu.Unlock()
	return nil
}

// Remove unregisters the cron entry for the given schedule ID.
func (m *Manager) Remove(scheduleID string) {
	m.mu.Lock()
	entryID, ok := m.entries[scheduleID]
	delete(m.entries, scheduleID)
	m.mu.Unlock()
	if ok {
		if err := m.scheduler.Unregister(entryID); err != nil {
			log.Printf("[cronmgr] unregister entry %s (schedule %s): %v", entryID, scheduleID, err)
		}
	}
}

// Update replaces an existing cron entry with updated schedule settings.
// If the schedule is disabled, the entry is only removed.
func (m *Manager) Update(sc *model.Schedule) error {
	m.Remove(sc.ID)
	if !sc.Enabled {
		return nil
	}
	return m.Add(sc)
}

// LoadAll registers all active schedules; called once at startup.
func (m *Manager) LoadAll(schedules []*model.Schedule) {
	for _, sc := range schedules {
		if sc.Enabled {
			if err := m.Add(sc); err != nil {
				log.Printf("[cronmgr] failed to load schedule %s: %v", sc.ID, err)
			}
		}
	}
}

// ── Helpers ───────────────────────────────────────────────────────────────────

func buildTask(sc *model.Schedule) (*asynq.Task, error) {
	return worker.NewTriggerTask(worker.TriggerPayload{
		ScheduleID:       sc.ID,
		RunID:            uuid.New().String(),
		UserEmail:        sc.OwnerEmail,
		ConversationID:   sc.ConversationID,
		Message:          sc.Message,
		TopK:             sc.TopK,
		UseKnowledgeBase: sc.UseKnowledgeBase,
		UseWebFetch:      sc.UseWebFetch,
	})
}

// formatCron prefixes the cron expression with the IANA timezone when non-UTC.
// Asynq supports "CRON_TZ=America/New_York 0 9 * * *" syntax.
func formatCron(cronExpr, timezone string) string {
	if timezone == "" || timezone == "UTC" {
		return cronExpr
	}
	return "CRON_TZ=" + timezone + " " + cronExpr
}
