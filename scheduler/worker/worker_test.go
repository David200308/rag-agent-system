package worker

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestNewTriggerTask_TypeAndPayload(t *testing.T) {
	p := TriggerPayload{
		ScheduleID:       "sched-1",
		UserUuid:        "user-uuid-1",
		ConversationID:   "conv-1",
		Message:          "daily report",
		TopK:             5,
		UseKnowledgeBase: true,
		UseWebFetch:      false,
	}

	task, err := NewTriggerTask(p)
	if err != nil {
		t.Fatalf("NewTriggerTask error: %v", err)
	}

	if task.Type() != TypeRagTrigger {
		t.Errorf("task.Type() = %q, want %q", task.Type(), TypeRagTrigger)
	}

	var decoded TriggerPayload
	if err := json.Unmarshal(task.Payload(), &decoded); err != nil {
		t.Fatalf("unmarshal payload: %v", err)
	}
	if decoded.ScheduleID != p.ScheduleID {
		t.Errorf("ScheduleID = %q, want %q", decoded.ScheduleID, p.ScheduleID)
	}
	if decoded.UserUuid != p.UserUuid {
		t.Errorf("UserUuid = %q, want %q", decoded.UserUuid, p.UserUuid)
	}
	if decoded.TopK != p.TopK {
		t.Errorf("TopK = %d, want %d", decoded.TopK, p.TopK)
	}
	if decoded.UseKnowledgeBase != p.UseKnowledgeBase {
		t.Errorf("UseKnowledgeBase = %v, want %v", decoded.UseKnowledgeBase, p.UseKnowledgeBase)
	}
}

func TestNewTriggerTask_WorkflowPayload(t *testing.T) {
	p := TriggerPayload{
		ScheduleID:    "sched-2",
		UserUuid:     "user-uuid-1",
		WorkflowID:    "wf-42",
		WorkflowInput: `{"key":"value"}`,
	}

	task, err := NewTriggerTask(p)
	if err != nil {
		t.Fatalf("NewTriggerTask error: %v", err)
	}

	var decoded TriggerPayload
	if err := json.Unmarshal(task.Payload(), &decoded); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	if decoded.WorkflowID != p.WorkflowID {
		t.Errorf("WorkflowID = %q, want %q", decoded.WorkflowID, p.WorkflowID)
	}
	if decoded.WorkflowInput != p.WorkflowInput {
		t.Errorf("WorkflowInput = %q, want %q", decoded.WorkflowInput, p.WorkflowInput)
	}
}

func TestCallChatBackend_SendsIdempotencyKeyHeader(t *testing.T) {
	var gotIdempotencyKey, gotServiceKey string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotIdempotencyKey = r.Header.Get("X-Idempotency-Key")
		gotServiceKey = r.Header.Get("X-Scheduler-Key")
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	p := TriggerPayload{ScheduleID: "sched-1", UserUuid: "user-uuid-1", Message: "hi"}
	if err := callChatBackend(context.Background(), server.URL, "service-key", "run-abc-123", p); err != nil {
		t.Fatalf("callChatBackend error: %v", err)
	}

	if gotIdempotencyKey != "run-abc-123" {
		t.Errorf("X-Idempotency-Key = %q, want %q", gotIdempotencyKey, "run-abc-123")
	}
	if gotServiceKey != "service-key" {
		t.Errorf("X-Scheduler-Key = %q, want %q", gotServiceKey, "service-key")
	}
}

func TestCallWorkflowBackend_SendsIdempotencyKeyHeader(t *testing.T) {
	var gotIdempotencyKey string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotIdempotencyKey = r.Header.Get("X-Idempotency-Key")
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	p := TriggerPayload{ScheduleID: "sched-2", UserUuid: "user-uuid-1", WorkflowID: "wf-1"}
	if err := callWorkflowBackend(context.Background(), server.URL, "service-key", "run-xyz-789", p); err != nil {
		t.Fatalf("callWorkflowBackend error: %v", err)
	}

	if gotIdempotencyKey != "run-xyz-789" {
		t.Errorf("X-Idempotency-Key = %q, want %q", gotIdempotencyKey, "run-xyz-789")
	}
}

func TestCallChatBackend_DifferentRunIDsProduceDifferentHeaders(t *testing.T) {
	var seen []string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		seen = append(seen, r.Header.Get("X-Idempotency-Key"))
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	p := TriggerPayload{ScheduleID: "sched-1", UserUuid: "user-uuid-1", Message: "hi"}
	_ = callChatBackend(context.Background(), server.URL, "key", "run-1", p)
	_ = callChatBackend(context.Background(), server.URL, "key", "run-2", p)

	if len(seen) != 2 || seen[0] == seen[1] {
		t.Errorf("expected two distinct idempotency keys across separate executions, got %v", seen)
	}
}
