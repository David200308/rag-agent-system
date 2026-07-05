package worker

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/hibiken/asynq"
	"scheduler/config"
	"scheduler/store"
)

const TypeRagTrigger = "rag:trigger"
const Queue = "rag-scheduler"

// TriggerPayload is serialised into the Asynq task at enqueue time.
// BackendURL and ServiceKey are injected at execution time from live config.
// RunID is deliberately not part of this payload: this exact payload is
// re-enqueued unchanged on every cron tick of a recurring schedule, so a
// RunID baked in here would be shared by every execution. NewHandler derives
// a fresh per-execution ID from the Asynq task ID at run time instead.
// Either ConversationID+Message or WorkflowID+WorkflowInput must be set.
type TriggerPayload struct {
	ScheduleID       string `json:"scheduleId"`
	UserUuid         string `json:"userUuid"`
	ConversationID   string `json:"conversationId"`
	Message          string `json:"message"`
	WorkflowID       string `json:"workflowId,omitempty"`
	WorkflowInput    string `json:"workflowInput,omitempty"`
	TopK             int    `json:"topK"`
	UseKnowledgeBase bool   `json:"useKnowledgeBase"`
	UseWebFetch      bool   `json:"useWebFetch"`
}

func NewTriggerTask(p TriggerPayload) (*asynq.Task, error) {
	payload, err := json.Marshal(p)
	if err != nil {
		return nil, err
	}
	return asynq.NewTask(TypeRagTrigger, payload,
		asynq.MaxRetry(3),
		asynq.Timeout(5*time.Minute),
		asynq.Queue(Queue),
	), nil
}

var httpClient = &http.Client{Timeout: 60 * time.Second}

// NewHandler returns an Asynq handler that calls the Spring Boot trigger endpoint.
// BackendURL and ServiceKey are read from cfg at execution time so they stay current
// across config rotations without needing to re-register tasks.
func NewHandler(cfg *config.Config, st *store.Store) asynq.HandlerFunc {
	return func(ctx context.Context, t *asynq.Task) error {
		var p TriggerPayload
		if err := json.Unmarshal(t.Payload(), &p); err != nil {
			return fmt.Errorf("unmarshal payload: %w", err)
		}

		// asynq.GetTaskID is unique per enqueue (i.e. per cron tick) but stable
		// across that task's own retries — exactly the identifier this run needs:
		// distinct run-history rows per execution, and a stable idempotency key
		// the backend can use to dedupe retries of one execution.
		runID, ok := asynq.GetTaskID(ctx)
		if !ok {
			runID = uuid.New().String()
		}

		startTime := time.Now().UTC()
		_ = st.InsertRun(p.ScheduleID, runID, "RUNNING", startTime)

		var err error
		if p.WorkflowID != "" {
			err = callWorkflowBackend(ctx, cfg.BackendURL, cfg.ServiceKey, runID, p)
		} else {
			err = callChatBackend(ctx, cfg.BackendURL, cfg.ServiceKey, runID, p)
		}

		status := "COMPLETED"
		if err != nil {
			status = "FAILED"
		}
		_ = st.CompleteRun(runID, status, time.Now().UTC())
		return err
	}
}

func callChatBackend(ctx context.Context, backendURL, serviceKey, idempotencyKey string, p TriggerPayload) error {
	body, err := json.Marshal(struct {
		UserUuid         string `json:"userUuid"`
		ConversationID   string `json:"conversationId"`
		Message          string `json:"message"`
		TopK             int    `json:"topK"`
		UseKnowledgeBase bool   `json:"useKnowledgeBase"`
		UseWebFetch      bool   `json:"useWebFetch"`
	}{p.UserUuid, p.ConversationID, p.Message, p.TopK, p.UseKnowledgeBase, p.UseWebFetch})
	if err != nil {
		return fmt.Errorf("marshal: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		backendURL+"/api/v1/scheduler/trigger", bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Scheduler-Key", serviceKey)
	req.Header.Set("X-Idempotency-Key", idempotencyKey)

	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("call backend: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("backend returned %d", resp.StatusCode)
	}
	return nil
}

func callWorkflowBackend(ctx context.Context, backendURL, serviceKey, idempotencyKey string, p TriggerPayload) error {
	body, err := json.Marshal(struct {
		UserUuid      string `json:"userUuid"`
		WorkflowID    string `json:"workflowId"`
		WorkflowInput string `json:"workflowInput"`
	}{p.UserUuid, p.WorkflowID, p.WorkflowInput})
	if err != nil {
		return fmt.Errorf("marshal: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		backendURL+"/api/v1/scheduler/workflow-trigger", bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Scheduler-Key", serviceKey)
	req.Header.Set("X-Idempotency-Key", idempotencyKey)

	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("call backend: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("backend returned %d", resp.StatusCode)
	}
	return nil
}
