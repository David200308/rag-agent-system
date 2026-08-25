package notify

import (
	"context"
	"encoding/json"
	"errors"
	"testing"

	"github.com/segmentio/kafka-go"
)

type fakeWriter struct {
	messages []kafka.Message
	err      error
}

func (f *fakeWriter) WriteMessages(_ context.Context, msgs ...kafka.Message) error {
	if f.err != nil {
		return f.err
	}
	f.messages = append(f.messages, msgs...)
	return nil
}

func (f *fakeWriter) Close() error { return nil }

type fakeResolver struct {
	emails map[string]string
	err    error
}

func (f *fakeResolver) GetEmailByOwnerUUID(ownerUUID string) (string, error) {
	if f.err != nil {
		return "", f.err
	}
	return f.emails[ownerUUID], nil
}

func TestNotify_PublishesEventWithResolvedEmail(t *testing.T) {
	writer := &fakeWriter{}
	resolver := &fakeResolver{emails: map[string]string{"owner-1": "owner1@example.com"}}
	client := &Client{writer: writer, emails: resolver}

	p := TriggerPayload{
		OwnerUuid: "owner-1", OrgId: "org-1", RuleType: "price", RuleID: "rule-1",
		Symbol: "BTC/USD", Message: "price crossed threshold",
	}
	if err := client.Notify(context.Background(), p); err != nil {
		t.Fatalf("Notify error: %v", err)
	}

	if len(writer.messages) != 1 {
		t.Fatalf("expected 1 message, got %d", len(writer.messages))
	}
	msg := writer.messages[0]

	if string(msg.Key) != "owner1@example.com" {
		t.Errorf("Key = %q, want owner1@example.com", msg.Key)
	}

	var gotHeader string
	for _, h := range msg.Headers {
		if h.Key == "__TypeId__" {
			gotHeader = string(h.Value)
		}
	}
	if gotHeader != "alertTriggered" {
		t.Errorf("__TypeId__ header = %q, want alertTriggered", gotHeader)
	}

	var event alertTriggeredEvent
	if err := json.Unmarshal(msg.Value, &event); err != nil {
		t.Fatalf("unmarshal value: %v", err)
	}
	if event.To != "owner1@example.com" || event.RuleType != "price" ||
		event.SymbolOrProtocol != "BTC/USD" || event.Message != "price crossed threshold" {
		t.Errorf("unexpected event: %+v", event)
	}
}

func TestNotify_ResolverErrorPropagates(t *testing.T) {
	writer := &fakeWriter{}
	resolver := &fakeResolver{err: errors.New("db unavailable")}
	client := &Client{writer: writer, emails: resolver}

	err := client.Notify(context.Background(), TriggerPayload{OwnerUuid: "owner-1", RuleID: "rule-1"})
	if err == nil {
		t.Fatal("expected error when resolver fails")
	}
	if len(writer.messages) != 0 {
		t.Error("expected no message written when resolver fails")
	}
}

func TestNotify_NoEmailFoundReturnsError(t *testing.T) {
	writer := &fakeWriter{}
	resolver := &fakeResolver{emails: map[string]string{}}
	client := &Client{writer: writer, emails: resolver}

	err := client.Notify(context.Background(), TriggerPayload{OwnerUuid: "owner-missing", RuleID: "rule-1"})
	if err == nil {
		t.Fatal("expected error when no email is found for owner")
	}
	if len(writer.messages) != 0 {
		t.Error("expected no message written when email is missing")
	}
}

func TestNotify_WriterErrorPropagates(t *testing.T) {
	writer := &fakeWriter{err: errors.New("broker unreachable")}
	resolver := &fakeResolver{emails: map[string]string{"owner-1": "owner1@example.com"}}
	client := &Client{writer: writer, emails: resolver}

	err := client.Notify(context.Background(), TriggerPayload{OwnerUuid: "owner-1", RuleID: "rule-1"})
	if err == nil {
		t.Fatal("expected error when writer fails")
	}
}
