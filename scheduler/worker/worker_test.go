package worker

import (
	"encoding/json"
	"testing"
)

func TestNewTriggerTask_TypeAndPayload(t *testing.T) {
	p := TriggerPayload{
		ScheduleID:       "sched-1",
		RunID:            "run-1",
		UserEmail:        "user@test.com",
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
	if decoded.UserEmail != p.UserEmail {
		t.Errorf("UserEmail = %q, want %q", decoded.UserEmail, p.UserEmail)
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
		RunID:         "run-2",
		UserEmail:     "user@test.com",
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
