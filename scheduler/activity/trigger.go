package activity

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"scheduler/model"
)

var httpClient = &http.Client{Timeout: 60 * time.Second}

// TriggerActivity calls the Spring Boot /api/v1/scheduler/trigger endpoint.
// Temporal retries this automatically according to the workflow's RetryPolicy.
func TriggerActivity(ctx context.Context, payload model.TriggerPayload) error {
	body, err := json.Marshal(struct {
		UserEmail        string `json:"userEmail"`
		ConversationID   string `json:"conversationId"`
		Message          string `json:"message"`
		TopK             int    `json:"topK"`
		UseKnowledgeBase bool   `json:"useKnowledgeBase"`
		UseWebFetch      bool   `json:"useWebFetch"`
	}{
		UserEmail:        payload.UserEmail,
		ConversationID:   payload.ConversationID,
		Message:          payload.Message,
		TopK:             payload.TopK,
		UseKnowledgeBase: payload.UseKnowledgeBase,
		UseWebFetch:      payload.UseWebFetch,
	})
	if err != nil {
		return fmt.Errorf("marshal trigger payload: %w", err)
	}

	url := payload.BackendURL + "/api/v1/scheduler/trigger"
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("build trigger request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Scheduler-Key", payload.ServiceKey)

	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("call backend trigger: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("backend trigger returned %d", resp.StatusCode)
	}
	return nil
}
