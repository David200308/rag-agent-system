package handler

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"time"

	commonpb "go.temporal.io/api/common/v1"
	"go.temporal.io/api/enums/v1"
	workflowpb "go.temporal.io/api/workflow/v1"
	"go.temporal.io/api/workflowservice/v1"
	"go.temporal.io/sdk/client"
	"go.temporal.io/sdk/converter"

	"scheduler/config"
	"scheduler/model"
	ragworkflow "scheduler/workflow"
)

type Handler struct {
	cfg *config.Config
	tc  client.Client
}

func New(cfg *config.Config, tc client.Client) *Handler {
	return &Handler{cfg: cfg, tc: tc}
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

		iter, err := h.tc.ScheduleClient().List(r.Context(), client.ScheduleListOptions{PageSize: 1000})
		if err != nil {
			log.Printf("[handler] list error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to list schedules")
			return
		}
		dc := converter.GetDefaultDataConverter()
		var schedules []model.Schedule

		for iter.HasNext() {
			entry, err := iter.Next()
			if err != nil {
				log.Printf("[handler] list iterate error: %v", err)
				break
			}

			memo, ok := decodeMemo(dc, entry.Memo)
			if !ok || memo.ConversationID != convID {
				continue
			}

			// Describe to get workflow args (message, topK, etc.)
			handle := h.tc.ScheduleClient().GetHandle(r.Context(), entry.ID)
			desc, err := handle.Describe(r.Context())
			if err != nil {
				log.Printf("[handler] describe error id=%s: %v", entry.ID, err)
				continue
			}

			payload := decodePayload(desc.Schedule.Action)
			sc := buildSchedule(entry.ID, memo, entry.Spec, !entry.Paused, payload,
				entry.NextActionTimes, entry.RecentActions)
			schedules = append(schedules, sc)
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
		if req.Timezone == "" {
			req.Timezone = "UTC"
		}

		cronExpr := model.BuildCronExpr(req.CronMinute, req.CronHour, req.CronDay, req.CronMonth, req.CronWeekday)
		scheduleID := newID()

		memo := model.ScheduleMemo{
			OwnerEmail:     email,
			ConversationID: req.ConversationID,
			CreatedAt:      time.Now().UTC().Format(time.RFC3339),
		}
		memoJSON, _ := json.Marshal(memo)

		payload := model.TriggerPayload{
			UserEmail:        email,
			ConversationID:   req.ConversationID,
			Message:          req.Message,
			TopK:             req.TopK,
			UseKnowledgeBase: req.UseKnowledgeBase,
			UseWebFetch:      req.UseWebFetch,
			BackendURL:       h.cfg.BackendURL,
			ServiceKey:       h.cfg.ServiceKey,
		}

		handle, err := h.tc.ScheduleClient().Create(r.Context(), client.ScheduleOptions{
			ID: scheduleID,
			Spec: client.ScheduleSpec{
				CronExpressions: []string{cronExpr},
				TimeZoneName:    req.Timezone,
			},
			Action: &client.ScheduleWorkflowAction{
				ID:        scheduleID + "-run",
				Workflow:  ragworkflow.RagQueryWorkflow,
				Args:      []interface{}{payload},
				TaskQueue: ragworkflow.TaskQueue,
			},
			Memo: map[string]interface{}{
				"data": string(memoJSON),
			},
			Overlap: enums.SCHEDULE_OVERLAP_POLICY_SKIP,
		})
		if err != nil {
			log.Printf("[handler] create schedule error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to create schedule")
			return
		}

		desc, _ := handle.Describe(r.Context())
		var nextTimes []time.Time
		if desc != nil {
			nextTimes = desc.Info.NextActionTimes
		}

		createdAt, _ := time.Parse(time.RFC3339, memo.CreatedAt)
		sc := buildSchedule(scheduleID, memo,
			&client.ScheduleSpec{CronExpressions: []string{cronExpr}, TimeZoneName: req.Timezone},
			true, payload, nextTimes, nil)
		sc.CreatedAt = createdAt

		writeJSON(w, http.StatusCreated, sc)
	})(w, r)
}

// ── Update ────────────────────────────────────────────────────────────────────

// PATCH /schedules/{id}
func (h *Handler) Update(w http.ResponseWriter, r *http.Request) {
	h.withAuth(func(w http.ResponseWriter, r *http.Request) {
		email := r.Context().Value(emailKey).(string)
		id := r.PathValue("id")

		body, _ := io.ReadAll(io.LimitReader(r.Body, 32*1024))
		var req model.UpdateRequest
		if err := json.Unmarshal(body, &req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid JSON")
			return
		}

		handle := h.tc.ScheduleClient().GetHandle(r.Context(), id)
		desc, err := handle.Describe(r.Context())
		if err != nil {
			writeError(w, http.StatusNotFound, "schedule not found")
			return
		}

		dc := converter.GetDefaultDataConverter()
		memo, ok := decodeMemo(dc, desc.Memo)
		if !ok || memo.OwnerEmail != email {
			writeError(w, http.StatusForbidden, "not the owner")
			return
		}

		// Start from existing payload and apply partial updates
		current := decodePayload(desc.Schedule.Action)
		if req.Message != nil {
			current.Message = *req.Message
		}
		if req.TopK != nil {
			current.TopK = *req.TopK
		}
		if req.UseKnowledgeBase != nil {
			current.UseKnowledgeBase = *req.UseKnowledgeBase
		}
		if req.UseWebFetch != nil {
			current.UseWebFetch = *req.UseWebFetch
		}
		// Always refresh secrets in case they rotated
		current.BackendURL = h.cfg.BackendURL
		current.ServiceKey = h.cfg.ServiceKey

		// Build updated cron expression by merging into the existing spec
		cronExpr := ""
		timezone := "UTC"
		if desc.Schedule.Spec != nil {
			if len(desc.Schedule.Spec.CronExpressions) > 0 {
				cronExpr = desc.Schedule.Spec.CronExpressions[0]
			}
			if desc.Schedule.Spec.TimeZoneName != "" {
				timezone = desc.Schedule.Spec.TimeZoneName
			}
		}
		if req.CronMinute != nil || req.CronHour != nil || req.CronDay != nil ||
			req.CronMonth != nil || req.CronWeekday != nil {
			parts := strings.Fields(cronExpr)
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
			cronExpr = strings.Join(parts, " ")
		}
		if req.Timezone != nil {
			timezone = *req.Timezone
		}

		updatedPayload := current
		err = handle.Update(r.Context(), client.ScheduleUpdateOptions{
			DoUpdate: func(input client.ScheduleUpdateInput) (*client.ScheduleUpdate, error) {
				sched := &input.Description.Schedule
				sched.Spec = &client.ScheduleSpec{
					CronExpressions: []string{cronExpr},
					TimeZoneName:    timezone,
				}
				if req.Enabled != nil {
					sched.State = &client.ScheduleState{Paused: !*req.Enabled}
				}
				if action, ok := sched.Action.(*client.ScheduleWorkflowAction); ok {
					action.Args = []interface{}{updatedPayload}
				}
				return &client.ScheduleUpdate{Schedule: sched}, nil
			},
		})
		if err != nil {
			log.Printf("[handler] update schedule error id=%s: %v", id, err)
			writeError(w, http.StatusInternalServerError, "failed to update schedule")
			return
		}

		desc2, _ := handle.Describe(r.Context())
		enabled := true
		var nextTimes []time.Time
		var recentActions []client.ScheduleActionResult
		if desc2 != nil {
			if desc2.Schedule.State != nil {
				enabled = !desc2.Schedule.State.Paused
			}
			nextTimes = desc2.Info.NextActionTimes
			recentActions = desc2.Info.RecentActions
		}

		sc := buildSchedule(id, memo,
			&client.ScheduleSpec{CronExpressions: []string{cronExpr}, TimeZoneName: timezone},
			enabled, updatedPayload, nextTimes, recentActions)
		writeJSON(w, http.StatusOK, sc)
	})(w, r)
}

// ── Delete ────────────────────────────────────────────────────────────────────

// DELETE /schedules/{id}
func (h *Handler) Delete(w http.ResponseWriter, r *http.Request) {
	h.withAuth(func(w http.ResponseWriter, r *http.Request) {
		email := r.Context().Value(emailKey).(string)
		id := r.PathValue("id")

		handle := h.tc.ScheduleClient().GetHandle(r.Context(), id)
		desc, err := handle.Describe(r.Context())
		if err != nil {
			writeError(w, http.StatusNotFound, "schedule not found")
			return
		}

		dc := converter.GetDefaultDataConverter()
		memo, ok := decodeMemo(dc, desc.Memo)
		if !ok || memo.OwnerEmail != email {
			writeError(w, http.StatusForbidden, "not the owner")
			return
		}

		if err := handle.Delete(r.Context()); err != nil {
			log.Printf("[handler] delete schedule error id=%s: %v", id, err)
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
		id := r.PathValue("id")

		resp, err := h.tc.ListWorkflow(r.Context(), &workflowservice.ListWorkflowExecutionsRequest{
			Namespace: h.cfg.TemporalNamespace,
			Query:     fmt.Sprintf(`TemporalScheduledById = "%s"`, id),
			PageSize:  20,
		})
		if err != nil {
			log.Printf("[handler] list runs error id=%s: %v", id, err)
			writeError(w, http.StatusInternalServerError, "failed to list runs")
			return
		}

		runs := make([]model.ScheduleRun, 0, len(resp.Executions))
		for _, exec := range resp.Executions {
			runs = append(runs, executionToRun(exec))
		}
		writeJSON(w, http.StatusOK, runs)
	})(w, r)
}

// ── Internal endpoints (service-key auth, used by Spring Boot workflow engine) ─

func (h *Handler) withServiceAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Scheduler-Key") != h.cfg.ServiceKey {
			writeError(w, http.StatusUnauthorized, "invalid service key")
			return
		}
		next(w, r)
	}
}

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

		scheduleID := newID()
		memo := model.ScheduleMemo{
			OwnerEmail:     req.OwnerEmail,
			ConversationID: req.ConversationID,
			CreatedAt:      time.Now().UTC().Format(time.RFC3339),
		}
		memoJSON, _ := json.Marshal(memo)

		payload := model.TriggerPayload{
			UserEmail:        req.OwnerEmail,
			ConversationID:   req.ConversationID,
			Message:          req.Message,
			TopK:             req.TopK,
			UseKnowledgeBase: req.UseKnowledgeBase,
			UseWebFetch:      req.UseWebFetch,
			BackendURL:       h.cfg.BackendURL,
			ServiceKey:       h.cfg.ServiceKey,
		}

		_, err = h.tc.ScheduleClient().Create(r.Context(), client.ScheduleOptions{
			ID: scheduleID,
			Spec: client.ScheduleSpec{
				CronExpressions: []string{req.CronExpr},
				TimeZoneName:    req.Timezone,
			},
			Action: &client.ScheduleWorkflowAction{
				ID:        scheduleID + "-run",
				Workflow:  ragworkflow.RagQueryWorkflow,
				Args:      []interface{}{payload},
				TaskQueue: ragworkflow.TaskQueue,
			},
			Memo:    map[string]interface{}{"data": string(memoJSON)},
			Overlap: enums.SCHEDULE_OVERLAP_POLICY_SKIP,
		})
		if err != nil {
			log.Printf("[handler] internal create error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to create schedule")
			return
		}

		createdAt, _ := time.Parse(time.RFC3339, memo.CreatedAt)
		writeJSON(w, http.StatusCreated, model.Schedule{
			ID:               scheduleID,
			ConversationID:   req.ConversationID,
			OwnerEmail:       req.OwnerEmail,
			Message:          req.Message,
			CronExpr:         req.CronExpr,
			Timezone:         req.Timezone,
			TopK:             req.TopK,
			UseKnowledgeBase: req.UseKnowledgeBase,
			UseWebFetch:      req.UseWebFetch,
			Enabled:          true,
			CreatedAt:        createdAt,
		})
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

		iter, err := h.tc.ScheduleClient().List(r.Context(), client.ScheduleListOptions{PageSize: 1000})
		if err != nil {
			log.Printf("[handler] internal list error: %v", err)
			writeError(w, http.StatusInternalServerError, "failed to list schedules")
			return
		}
		dc := converter.GetDefaultDataConverter()
		var schedules []model.Schedule

		for iter.HasNext() {
			entry, err := iter.Next()
			if err != nil {
				break
			}
			memo, ok := decodeMemo(dc, entry.Memo)
			if !ok || memo.ConversationID != convID {
				continue
			}
			if ownerEmail != "" && memo.OwnerEmail != ownerEmail {
				continue
			}
			sc := buildSchedule(entry.ID, memo, entry.Spec, !entry.Paused,
				model.TriggerPayload{}, entry.NextActionTimes, entry.RecentActions)
			schedules = append(schedules, sc)
		}
		if schedules == nil {
			schedules = []model.Schedule{}
		}
		writeJSON(w, http.StatusOK, schedules)
	})(w, r)
}

// DELETE /internal/schedules/{id}?ownerEmail={email}
func (h *Handler) InternalDelete(w http.ResponseWriter, r *http.Request) {
	h.withServiceAuth(func(w http.ResponseWriter, r *http.Request) {
		id := r.PathValue("id")
		ownerEmail := r.URL.Query().Get("ownerEmail")

		handle := h.tc.ScheduleClient().GetHandle(r.Context(), id)
		desc, err := handle.Describe(r.Context())
		if err != nil {
			writeError(w, http.StatusNotFound, "schedule not found")
			return
		}

		if ownerEmail != "" {
			dc := converter.GetDefaultDataConverter()
			memo, ok := decodeMemo(dc, desc.Memo)
			if ok && memo.OwnerEmail != ownerEmail {
				writeError(w, http.StatusForbidden, "not the owner")
				return
			}
		}

		if err := handle.Delete(r.Context()); err != nil {
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

// decodeMemo extracts a ScheduleMemo from a Temporal Memo proto field.
// The memo was stored as a single JSON string under the "data" key.
func decodeMemo(dc converter.DataConverter, rawMemo *commonpb.Memo) (model.ScheduleMemo, bool) {
	if rawMemo == nil {
		return model.ScheduleMemo{}, false
	}
	payload, ok := rawMemo.Fields["data"]
	if !ok {
		return model.ScheduleMemo{}, false
	}
	var dataStr string
	if err := dc.FromPayload(payload, &dataStr); err != nil {
		return model.ScheduleMemo{}, false
	}
	var memo model.ScheduleMemo
	if err := json.Unmarshal([]byte(dataStr), &memo); err != nil {
		return model.ScheduleMemo{}, false
	}
	return memo, true
}

// decodePayload extracts TriggerPayload from the workflow action's Args.
// Args come back from Temporal as map[string]interface{} (JSON-decoded), so we
// round-trip through JSON to get a properly typed TriggerPayload.
func decodePayload(action client.ScheduleAction) model.TriggerPayload {
	wa, ok := action.(*client.ScheduleWorkflowAction)
	if !ok || len(wa.Args) == 0 {
		return model.TriggerPayload{}
	}
	raw, err := json.Marshal(wa.Args[0])
	if err != nil {
		return model.TriggerPayload{}
	}
	var p model.TriggerPayload
	_ = json.Unmarshal(raw, &p)
	return p
}

func buildSchedule(
	id string,
	memo model.ScheduleMemo,
	spec *client.ScheduleSpec,
	enabled bool,
	payload model.TriggerPayload,
	nextTimes []time.Time,
	recentActions []client.ScheduleActionResult,
) model.Schedule {
	cronExpr, timezone := "", "UTC"
	if spec != nil {
		if len(spec.CronExpressions) > 0 {
			cronExpr = spec.CronExpressions[0]
		}
		if spec.TimeZoneName != "" {
			timezone = spec.TimeZoneName
		}
	}
	createdAt, _ := time.Parse(time.RFC3339, memo.CreatedAt)
	sc := model.Schedule{
		ID:               id,
		ConversationID:   memo.ConversationID,
		OwnerEmail:       memo.OwnerEmail,
		Message:          payload.Message,
		CronExpr:         cronExpr,
		Timezone:         timezone,
		TopK:             payload.TopK,
		UseKnowledgeBase: payload.UseKnowledgeBase,
		UseWebFetch:      payload.UseWebFetch,
		Enabled:          enabled,
		CreatedAt:        createdAt,
	}
	if len(nextTimes) > 0 {
		t := nextTimes[0]
		sc.NextRunAt = &t
	}
	if len(recentActions) > 0 {
		t := recentActions[len(recentActions)-1].ActualTime
		sc.LastRunAt = &t
	}
	return sc
}

func executionToRun(exec *workflowpb.WorkflowExecutionInfo) model.ScheduleRun {
	run := model.ScheduleRun{
		WorkflowID: exec.GetExecution().GetWorkflowId(),
		Status:     strings.TrimPrefix(exec.GetStatus().String(), "WORKFLOW_EXECUTION_STATUS_"),
	}
	if t := exec.GetStartTime(); t != nil {
		ts := t.AsTime()
		run.StartTime = &ts
	}
	if t := exec.GetCloseTime(); t != nil {
		ts := t.AsTime()
		run.CloseTime = &ts
	}
	return run
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
