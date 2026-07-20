package notify

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"investment-alert-task/internal/config"
)

func TestNotify_SendsHeadersAndBody(t *testing.T) {
	var gotAlertKey, gotIdempotencyKey string
	var gotBody TriggerPayload
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotAlertKey = r.Header.Get("X-Alert-Key")
		gotIdempotencyKey = r.Header.Get("X-Idempotency-Key")
		if r.URL.Path != "/api/v1/alerts/trigger" {
			t.Errorf("unexpected path: %s", r.URL.Path)
		}
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	cfg := &config.Config{BackendURL: server.URL, ServiceKey: "alert-secret"}
	client := NewClient(cfg)

	p := TriggerPayload{
		OwnerUuid: "owner-1", OrgId: "org-1", RuleType: "price", RuleID: "rule-1",
		Symbol: "BTC/USD", Message: "price crossed threshold",
	}
	if err := client.Notify(context.Background(), p); err != nil {
		t.Fatalf("Notify error: %v", err)
	}

	if gotAlertKey != "alert-secret" {
		t.Errorf("X-Alert-Key = %q, want %q", gotAlertKey, "alert-secret")
	}
	if gotIdempotencyKey == "" {
		t.Error("expected a non-empty X-Idempotency-Key")
	}
	if gotBody.OwnerUuid != "owner-1" || gotBody.Message != "price crossed threshold" {
		t.Errorf("unexpected body received: %+v", gotBody)
	}
}

func TestNotify_DifferentCallsProduceDifferentIdempotencyKeys(t *testing.T) {
	var seen []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		seen = append(seen, r.Header.Get("X-Idempotency-Key"))
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	cfg := &config.Config{BackendURL: server.URL, ServiceKey: "key"}
	client := NewClient(cfg)

	p := TriggerPayload{OwnerUuid: "owner-1", RuleType: "price", RuleID: "rule-1"}
	_ = client.Notify(context.Background(), p)
	_ = client.Notify(context.Background(), p)

	if len(seen) != 2 || seen[0] == seen[1] {
		t.Errorf("expected two distinct idempotency keys, got %v", seen)
	}
}

func TestNotify_NonSuccessStatusReturnsError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	cfg := &config.Config{BackendURL: server.URL, ServiceKey: "key"}
	client := NewClient(cfg)

	err := client.Notify(context.Background(), TriggerPayload{OwnerUuid: "owner-1", RuleID: "rule-1"})
	if err == nil {
		t.Fatal("expected error on non-2xx response")
	}
}
