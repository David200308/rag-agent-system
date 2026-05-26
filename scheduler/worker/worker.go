package worker

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/hibiken/asynq"
	"scheduler/config"
	"scheduler/store"
)

const TypeRagTrigger = "rag:trigger"
const Queue = "rag-scheduler"

// TriggerPayload is serialised into the Asynq task at enqueue time.
// BackendURL and ServiceKey are injected at execution time from live config.
// Either ConversationID+Message or WorkflowID+WorkflowInput must be set.
type TriggerPayload struct {
	ScheduleID       string `json:"scheduleId"`
	RunID            string `json:"runId"`
	UserEmail        string `json:"userEmail"`
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

		startTime := time.Now().UTC()
		_ = st.InsertRun(p.ScheduleID, p.RunID, "RUNNING", startTime)

		var err error
		if p.WorkflowID != "" {
			err = callWorkflowBackend(ctx, cfg.BackendURL, cfg.ServiceKey, p)
		} else {
			err = callChatBackend(ctx, cfg.BackendURL, cfg.ServiceKey, p)
		}

		status := "COMPLETED"
		if err != nil {
			status = "FAILED"
		}
		_ = st.CompleteRun(p.RunID, status, time.Now().UTC())
		return err
	}
}

func callChatBackend(ctx context.Context, backendURL, serviceKey string, p TriggerPayload) error {
	body, err := json.Marshal(struct {
		UserEmail        string `json:"userEmail"`
		ConversationID   string `json:"conversationId"`
		Message          string `json:"message"`
		TopK             int    `json:"topK"`
		UseKnowledgeBase bool   `json:"useKnowledgeBase"`
		UseWebFetch      bool   `json:"useWebFetch"`
	}{p.UserEmail, p.ConversationID, p.Message, p.TopK, p.UseKnowledgeBase, p.UseWebFetch})
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

func callWorkflowBackend(ctx context.Context, backendURL, serviceKey string, p TriggerPayload) error {
	body, err := json.Marshal(struct {
		UserEmail     string `json:"userEmail"`
		WorkflowID    string `json:"workflowId"`
		WorkflowInput string `json:"workflowInput"`
	}{p.UserEmail, p.WorkflowID, p.WorkflowInput})
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
