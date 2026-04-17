package handler

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"scheduler/config"
	"scheduler/cron"
	"scheduler/model"
	"scheduler/store"
	"strconv"
	"strings"
)

type Handler struct {
	cfg    *config.Config
	store  *store.Store
	runner *cron.Runner
}

func New(cfg *config.Config, st *store.Store, runner *cron.Runner) *Handler {
	return &Handler{cfg: cfg, store: st, runner: runner}
}

// ── Middleware ────────────────────────────────────────────────────────────────

type ctxKey string

const emailKey ctxKey = "userEmail"

// withAuth validates the Bearer JWT via Spring Boot and injects the email.
func (h *Handler) withAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" || !strings.HasPrefix(authHeader, "Bearer ") {
			writeError(w, http.StatusUnauthorized, "missing or invalid Authorization header")
			return
		}

		email, err := h.validateToken(authHeader)
		if err != nil || email == "" {
			writeError(w, http.StatusUnauthorized, "invalid or expired token")
			return
		}

		ctx := context.WithValue(r.Context(), emailKey, email)
		next(w, r.WithContext(ctx))
	}
}

// validateToken calls Spring Boot /api/v1/auth/validate and returns the email.
func (h *Handler) validateToken(authHeader string) (string, error) {
	req, err := http.NewRequest(http.MethodGet, h.cfg.ValidateURL, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Authorization", authHeader)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("validate request failed: %w", err)
	}
	defer resp.Body.Close()

	var result struct {
		Valid  bool   `json:"valid"`
		Email  string `json:"email"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", err
	}
	if !result.Valid {
		return "", fmt.Errorf("token invalid")
	}
	return result.Email, nil
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

		schedules, err := h.store.ListByConversation(convID)
		if err != nil {
			log.Printf("[handler] list error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to list schedules")
			return
		}
		if schedules == nil {
			schedules = []model.Schedule{}
		}
		writeJSON(w, http.StatusOK, schedules)
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

		cronExpr := model.BuildCronExpr(req.CronMinute, req.CronHour, req.CronDay, req.CronMonth, req.CronWeekday)

		sc := &model.Schedule{
			ConversationID:   req.ConversationID,
			OwnerEmail:       email,
			Message:          req.Message,
			CronExpr:         cronExpr,
			TopK:             req.TopK,
			UseKnowledgeBase: req.UseKnowledgeBase,
			UseWebFetch:      req.UseWebFetch,
			Enabled:          true,
		}

		if err := h.store.Create(sc); err != nil {
			log.Printf("[handler] create error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to create schedule")
			return
		}

		if err := h.runner.Register(*sc); err != nil {
			log.Printf("[handler] cron register error id=%d: %v", sc.ID, err)
			writeError(w, http.StatusBadRequest, "invalid cron expression: "+err.Error())
			// Roll back DB entry on invalid cron
			h.store.Delete(sc.ID, email)
			return
		}

		writeJSON(w, http.StatusCreated, sc)
	})(w, r)
}

// ── Update ────────────────────────────────────────────────────────────────────

// PATCH /schedules/{id}
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	h.withAuth(func(w http.ResponseWriter, r *http.Request) {
		email := r.Context().Value(emailKey).(string)

		id, err := pathID(r)
		if err != nil {
			writeError(w, http.StatusBadRequest, "invalid id")
			return
		}

		existing, err := h.store.GetByID(id)
		if err != nil || existing == nil {
			writeError(w, http.StatusNotFound, "schedule not found")
			return
		}
		if existing.OwnerEmail != email {
			writeError(w, http.StatusForbidden, "not the owner")
			return
		}

		body, _ := io.ReadAll(io.LimitReader(r.Body, 32*1024))
		var req model.UpdateRequest
		if err := json.Unmarshal(body, &req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid JSON")
			return
		}

		// Apply partial updates
		if req.Message != nil {
			existing.Message = *req.Message
		}
		if req.TopK != nil {
			existing.TopK = *req.TopK
		}
		if req.UseKnowledgeBase != nil {
			existing.UseKnowledgeBase = *req.UseKnowledgeBase
		}
		if req.UseWebFetch != nil {
			existing.UseWebFetch = *req.UseWebFetch
		}
		if req.Enabled != nil {
			existing.Enabled = *req.Enabled
		}

		// Rebuild cron expression if any cron field provided
		if req.CronMinute != nil || req.CronHour != nil || req.CronDay != nil ||
			req.CronMonth != nil || req.CronWeekday != nil {
			parts := strings.Fields(existing.CronExpr)
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
			existing.CronExpr = strings.Join(parts, " ")
		}

		if err := h.store.Update(existing); err != nil {
			log.Printf("[handler] update error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to update schedule")
			return
		}

		// Re-register cron job
		h.runner.Unregister(existing.ID)
		if existing.Enabled {
			if err := h.runner.Register(*existing); err != nil {
				log.Printf("[handler] re-register error id=%d: %v", existing.ID, err)
				writeError(w, http.StatusBadRequest, "invalid cron expression: "+err.Error())
				return
			}
		}

		writeJSON(w, http.StatusOK, existing)
	})(w, r)
}

// ── Delete ────────────────────────────────────────────────────────────────────

// DELETE /schedules/{id}
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	h.withAuth(func(w http.ResponseWriter, r *http.Request) {
		email := r.Context().Value(emailKey).(string)

		id, err := pathID(r)
		if err != nil {
			writeError(w, http.StatusBadRequest, "invalid id")
			return
		}

		deleted, err := h.store.Delete(id, email)
		if err != nil {
			log.Printf("[handler] delete error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to delete schedule")
			return
		}
		if !deleted {
			writeError(w, http.StatusNotFound, "schedule not found or not owner")
			return
		}

		h.runner.Unregister(id)
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

func pathID(r *http.Request) (int64, error) {
	seg := r.PathValue("id")
	return strconv.ParseInt(seg, 10, 64)
}
