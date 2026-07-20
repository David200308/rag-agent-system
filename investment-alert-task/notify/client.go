package notify

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/google/uuid"
	"investment-alert-task/internal/config"
)

// TriggerPayload is POSTed to agent-system-rest when an alert rule fires.
// The Java side resolves the owner's email (Resend, via Kafka) and Telegram
// chat (already-connected per-user bot) from OwnerUuid/OrgId — this service
// never stores or sends raw contact info.
type TriggerPayload struct {
	OwnerUuid string `json:"ownerUuid"`
	OrgId     string `json:"orgId,omitempty"`
	RuleType  string `json:"ruleType"` // "price", "defi", "predict-market"
	RuleID    string `json:"ruleId"`
	Symbol    string `json:"symbol,omitempty"`
	Message   string `json:"message"`
}

type Client struct {
	backendURL string
	serviceKey string
	httpClient *http.Client
}

func NewClient(cfg *config.Config) *Client {
	return &Client{
		backendURL: cfg.BackendURL,
		serviceKey: cfg.ServiceKey,
		httpClient: &http.Client{Timeout: 15 * time.Second},
	}
}

// Notify POSTs a fired-alert trigger to agent-system-rest's internal callback endpoint.
func (c *Client) Notify(ctx context.Context, p TriggerPayload) error {
	body, err := json.Marshal(p)
	if err != nil {
		return fmt.Errorf("marshal trigger payload: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		c.backendURL+"/api/v1/alerts/trigger", bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Alert-Key", c.serviceKey)
	req.Header.Set("X-Idempotency-Key", fmt.Sprintf("%s-%s", p.RuleID, uuid.New().String()))

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("call backend: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("backend returned %d", resp.StatusCode)
	}
	return nil
}
