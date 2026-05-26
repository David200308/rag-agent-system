package handler

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	"crypto/rand"

	"github.com/robfig/cron/v3"
	"scheduler/config"
	"scheduler/cronmgr"
	"scheduler/model"
	"scheduler/store"
)

type Handler struct {
	cfg *config.Config
	st  *store.Store
	mgr *cronmgr.Manager
}

func New(cfg *config.Config, st *store.Store, mgr *cronmgr.Manager) *Handler {
	return &Handler{cfg: cfg, st: st, mgr: mgr}
}

// ── Auth ──────────────────────────────────────────────────────────────────────

type ctxKey string

const emailKey ctxKey = "userEmail"

func (h *Handler) withAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" || !strings.HasPrefix(authHeader, "Bearer ") {
			writeError(w, http.StatusUnauthorized, "missing Authorization header")
			return
		}
		email, err := h.validateToken(authHeader)
		if err != nil || email == "" {
			writeError(w, http.StatusUnauthorized, "invalid or expired token")
			return
		}
		next(w, r.WithContext(context.WithValue(r.Context(), emailKey, email)))
	}
}

func (h *Handler) validateToken(authHeader string) (string, error) {
	req, err := http.NewRequest(http.MethodGet, h.cfg.ValidateURL, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Authorization", authHeader)

	resp, err := (&http.Client{Timeout: 5 * time.Second}).Do(req)
	if err != nil {
		return "", fmt.Errorf("validate request: %w", err)
	}
	defer resp.Body.Close()

	var result struct {
		Valid bool   `json:"valid"`
		Email string `json:"email"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", err
	}
	if !result.Valid {
		return "", fmt.Errorf("token invalid")
	}
	return result.Email, nil
}

func (h *Handler) withServiceAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Scheduler-Key") != h.cfg.ServiceKey {
			writeError(w, http.StatusUnauthorized, "invalid service key")
			return
		}
		next(w, r)
	}
}

// ── Health ────────────────────────────────────────────────────────────────────

func (h *Handler) Health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// ── List ──────────────────────────────────────────────────────────────────────

// GET /schedules?conversationId={id}
func (h *Handler) List(w http.ResponseWriter, r *http.Request) {
	h.withAuth(func(w http.ResponseWriter, r *http.Request) {
		convID := r.URL.Query().Get("conversationId")
		if convID == "" {
			writeError(w, http.StatusBadRequest, "conversationId is required")
			return
		}
		email := r.Context().Value(emailKey).(string)

		schedules, err := h.st.ListByConversation(email, convID)
		if err != nil {
			log.Printf("[handler] list error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to list schedules")
			return
		}

		out := make([]model.Schedule, 0, len(schedules))
		for _, sc := range schedules {
			enrichSchedule(sc, h.st)
			out = append(out, *sc)
		}
		writeJSON(w, http.StatusOK, out)
	})(w, r)
}

// ── Create ────────────────────────────────────────────────────────────────────

// POST /schedules
func (h *Handler) Create(w http.ResponseWriter, r *http.Request) {
	h.withAuth(func(w http.ResponseWriter, r *http.Request) {
		email := r.Context().Value(emailKey).(string)

		body, err := io.ReadAll(io.LimitReader(r.Body, 32*1024))
		if err != nil {
			writeError(w, http.StatusBadRequest, "cannot read body")
			return
		}
		var req model.CreateRequest
		if err := json.Unmarshal(body, &req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid JSON")
			return
		}
		if req.ConversationID == "" || req.Message == "" {
			writeError(w, http.StatusBadRequest, "conversationId and message are required")
			return
		}
		if req.TopK <= 0 {
			req.TopK = 5
		}
		if req.Timezone == "" {
			req.Timezone = "UTC"
		}

		sc := &model.Schedule{
			ID:               newID(),
			ConversationID:   req.ConversationID,
			OwnerEmail:       email,
			Message:          req.Message,
			CronExpr:         model.BuildCronExpr(req.CronMinute, req.CronHour, req.CronDay, req.CronMonth, req.CronWeekday),
			Timezone:         req.Timezone,
			TopK:             req.TopK,
			UseKnowledgeBase: req.UseKnowledgeBase,
			UseWebFetch:      req.UseWebFetch,
			Enabled:          true,
			CreatedAt:        time.Now().UTC(),
		}

		if err := h.st.Create(sc); err != nil {
			log.Printf("[handler] create db error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to create schedule")
			return
		}
		if err := h.mgr.Add(sc); err != nil {
			log.Printf("[handler] register cron error: %v", err)
			_ = h.st.Delete(sc.ID) // rollback
			writeError(w, http.StatusInternalServerError, "failed to register schedule")
			return
		}

		sc.NextRunAt = nextRunTime(sc.CronExpr, sc.Timezone)
		writeJSON(w, http.StatusCreated, sc)
	})(w, r)
}

// ── Update ────────────────────────────────────────────────────────────────────

// PATCH /schedules/{id}
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	h.withAuth(func(w http.ResponseWriter, r *http.Request) {
		email := r.Context().Value(emailKey).(string)
		id := r.PathValue("id")

		sc, err := h.st.GetByID(id)
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "schedule not found")
			return
		}
		if err != nil {
			writeError(w, http.StatusInternalServerError, "failed to fetch schedule")
			return
		}
		if sc.OwnerEmail != email {
			writeError(w, http.StatusForbidden, "not the owner")
			return
		}

		body, _ := io.ReadAll(io.LimitReader(r.Body, 32*1024))
		var req model.UpdateRequest
		if err := json.Unmarshal(body, &req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid JSON")
			return
		}

		if req.Message != nil {
			sc.Message = *req.Message
		}
		if req.TopK != nil {
			sc.TopK = *req.TopK
		}
		if req.UseKnowledgeBase != nil {
			sc.UseKnowledgeBase = *req.UseKnowledgeBase
		}
		if req.UseWebFetch != nil {
			sc.UseWebFetch = *req.UseWebFetch
		}
		if req.Enabled != nil {
			sc.Enabled = *req.Enabled
		}
		if req.Timezone != nil {
			sc.Timezone = *req.Timezone
		}

		// Merge cron fields into the existing expression
		parts := strings.Fields(sc.CronExpr)
		for len(parts) < 5 {
			parts = append(parts, "*")
		}
		if req.CronMinute != nil {
			parts[0] = *req.CronMinute
		}
		if req.CronHour != nil {
			parts[1] = *req.CronHour
		}
		if req.CronDay != nil {
			parts[2] = *req.CronDay
		}
		if req.CronMonth != nil {
			parts[3] = *req.CronMonth
		}
		if req.CronWeekday != nil {
			parts[4] = *req.CronWeekday
		}
		sc.CronExpr = strings.Join(parts, " ")

		if err := h.st.Update(sc); err != nil {
			log.Printf("[handler] update db error id=%s: %v", id, err)
			writeError(w, http.StatusInternalServerError, "failed to update schedule")
			return
		}
		if err := h.mgr.Update(sc); err != nil {
			log.Printf("[handler] update cron error id=%s: %v", id, err)
		}

		sc.NextRunAt = nextRunTime(sc.CronExpr, sc.Timezone)
		sc.LastRunAt = h.st.LastRunTime(sc.ID)
		writeJSON(w, http.StatusOK, sc)
	})(w, r)
}

// ── Delete ────────────────────────────────────────────────────────────────────

// DELETE /schedules/{id}
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	h.withAuth(func(w http.ResponseWriter, r *http.Request) {
		email := r.Context().Value(emailKey).(string)
		id := r.PathValue("id")

		sc, err := h.st.GetByID(id)
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "schedule not found")
			return
		}
		if err != nil {
			writeError(w, http.StatusInternalServerError, "failed to fetch schedule")
			return
		}
		if sc.OwnerEmail != email {
			writeError(w, http.StatusForbidden, "not the owner")
			return
		}

		h.mgr.Remove(id)
		if err := h.st.Delete(id); err != nil {
			log.Printf("[handler] delete db error id=%s: %v", id, err)
			writeError(w, http.StatusInternalServerError, "failed to delete schedule")
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})(w, r)
}

// ── Runs ──────────────────────────────────────────────────────────────────────

// GET /schedules/{id}/runs
func (h *Handler) ListRuns(w http.ResponseWriter, r *http.Request) {
	h.withAuth(func(w http.ResponseWriter, r *http.Request) {
		email := r.Context().Value(emailKey).(string)
		id := r.PathValue("id")

		sc, err := h.st.GetByID(id)
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "schedule not found")
			return
		}
		if err != nil {
			writeError(w, http.StatusInternalServerError, "failed to fetch schedule")
			return
		}
		if sc.OwnerEmail != email {
			writeError(w, http.StatusForbidden, "not the owner")
			return
		}

		runs, err := h.st.ListRuns(id)
		if err != nil {
			log.Printf("[handler] list runs error id=%s: %v", id, err)
			writeError(w, http.StatusInternalServerError, "failed to list runs")
			return
		}
		if runs == nil {
			runs = []model.ScheduleRun{}
		}
		writeJSON(w, http.StatusOK, runs)
	})(w, r)
}

// ── Internal endpoints (service-key auth, used by Spring Boot workflow engine) ─

// POST /internal/schedules
func (h *Handler) InternalCreate(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		body, err := io.ReadAll(io.LimitReader(r.Body, 32*1024))
		if err != nil {
			writeError(w, http.StatusBadRequest, "cannot read body")
			return
		}
		var req model.InternalCreateRequest
		if err := json.Unmarshal(body, &req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid JSON")
			return
		}
		if req.OwnerEmail == "" || req.ConversationID == "" || req.Message == "" {
			writeError(w, http.StatusBadRequest, "ownerEmail, conversationId, and message are required")
			return
		}
		if req.TopK <= 0 {
			req.TopK = 5
		}
		if req.Timezone == "" {
			req.Timezone = "UTC"
		}
		if req.CronExpr == "" {
			req.CronExpr = "0 9 * * *"
		}

		sc := &model.Schedule{
			ID:               newID(),
			ConversationID:   req.ConversationID,
			OwnerEmail:       req.OwnerEmail,
			Message:          req.Message,
			CronExpr:         req.CronExpr,
			Timezone:         req.Timezone,
			TopK:             req.TopK,
			UseKnowledgeBase: req.UseKnowledgeBase,
			UseWebFetch:      req.UseWebFetch,
			Enabled:          true,
			CreatedAt:        time.Now().UTC(),
		}

		if err := h.st.Create(sc); err != nil {
			log.Printf("[handler] internal create db error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to create schedule")
			return
		}
		if err := h.mgr.Add(sc); err != nil {
			log.Printf("[handler] internal register cron error: %v", err)
			_ = h.st.Delete(sc.ID)
			writeError(w, http.StatusInternalServerError, "failed to register schedule")
			return
		}

		sc.NextRunAt = nextRunTime(sc.CronExpr, sc.Timezone)
		writeJSON(w, http.StatusCreated, sc)
	})(w, r)
}

// GET /internal/schedules?conversationId={id}&ownerEmail={email}
func (h *Handler) InternalList(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		convID := r.URL.Query().Get("conversationId")
		ownerEmail := r.URL.Query().Get("ownerEmail")
		if convID == "" {
			writeError(w, http.StatusBadRequest, "conversationId is required")
			return
		}

		schedules, err := h.st.ListByOwner(ownerEmail, convID)
		if err != nil {
			log.Printf("[handler] internal list error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to list schedules")
			return
		}

		out := make([]model.Schedule, 0, len(schedules))
		for _, sc := range schedules {
			enrichSchedule(sc, h.st)
			out = append(out, *sc)
		}
		writeJSON(w, http.StatusOK, out)
	})(w, r)
}

// DELETE /internal/schedules/{id}?ownerEmail={email}
func (h *Handler) InternalDelete(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		ownerEmail := r.URL.Query().Get("ownerEmail")

		sc, err := h.st.GetByID(id)
		if err == sql.ErrNoRows {
			writeError(w, http.StatusNotFound, "schedule not found")
			return
		}
		if err != nil {
			writeError(w, http.StatusInternalServerError, "failed to fetch schedule")
			return
		}
		if ownerEmail != "" && sc.OwnerEmail != ownerEmail {
			writeError(w, http.StatusForbidden, "not the owner")
			return
		}

		h.mgr.Remove(id)
		if err := h.st.Delete(id); err != nil {
			log.Printf("[handler] internal delete error id=%s: %v", id, err)
			writeError(w, http.StatusInternalServerError, "failed to delete schedule")
			return
		}
		w.WriteHeader(http.StatusNoContent)
	})(w, r)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

// enrichSchedule populates NextRunAt and LastRunAt on a schedule in-place.
func enrichSchedule(sc *model.Schedule, st *store.Store) {
	sc.NextRunAt = nextRunTime(sc.CronExpr, sc.Timezone)
	sc.LastRunAt = st.LastRunTime(sc.ID)
}

// nextRunTime computes the next cron firing time using the 5-field cron expression.
func nextRunTime(cronExpr, timezone string) *time.Time {
	loc := time.UTC
	if timezone != "" && timezone != "UTC" {
		if l, err := time.LoadLocation(timezone); err == nil {
			loc = l
		}
	}
	parser := cron.NewParser(cron.Minute | cron.Hour | cron.Dom | cron.Month | cron.Dow)
	schedule, err := parser.Parse(cronExpr)
	if err != nil {
		return nil
	}
	next := schedule.Next(time.Now().In(loc))
	return &next
}

// newID generates a random UUID v4 string.
func newID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		b[0:4], b[4:6], b[6:8], b[8:10], b[10:16])
}
