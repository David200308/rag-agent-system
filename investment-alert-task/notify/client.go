package notify

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/segmentio/kafka-go"

	"investment-alert-task/internal/config"
)

// TopicAlertTriggered must stay in sync with agent-system-notification-consumer's
// EmailEventListener.TOPIC_ALERT_TRIGGERED.
const TopicAlertTriggered = "notifications.alert-triggered"

// typeIDAlertTriggered is written to the __TypeId__ header so Spring's JsonDeserializer
// resolves it via spring.json.type.mapping on the consumer side (see that module's
// application.yml) — mirrors the key agent-system-rest would have used had it produced
// this event itself.
const typeIDAlertTriggered = "alertTriggered"

// TriggerPayload describes a fired alert rule. OwnerUuid is resolved to an email address
// via EmailResolver before publishing — this service never receives raw contact info from
// the DB rows it monitors.
type TriggerPayload struct {
	OwnerUuid string `json:"ownerUuid"`
	OrgId     string `json:"orgId,omitempty"`
	RuleType  string `json:"ruleType"` // "price", "defi", "predict-market"
	RuleID    string `json:"ruleId"`
	Symbol    string `json:"symbol,omitempty"`
	Message   string `json:"message"`
}

// alertTriggeredEvent mirrors agent-system-notification-consumer's
// EmailEventListener.AlertTriggeredEvent record field-for-field (to, ruleType,
// symbolOrProtocol, message).
type alertTriggeredEvent struct {
	To               string `json:"to"`
	RuleType         string `json:"ruleType"`
	SymbolOrProtocol string `json:"symbolOrProtocol"`
	Message          string `json:"message"`
}

// EmailResolver resolves an owner's UUID to their email address.
type EmailResolver interface {
	GetEmailByOwnerUUID(ownerUUID string) (string, error)
}

// kafkaWriter is the subset of *kafka.Writer used here, so tests can substitute a fake.
type kafkaWriter interface {
	WriteMessages(ctx context.Context, msgs ...kafka.Message) error
	Close() error
}

type Client struct {
	writer kafkaWriter
	emails EmailResolver
}

func NewClient(cfg *config.Config, emails EmailResolver) *Client {
	return &Client{
		writer: &kafka.Writer{
			Addr:                   kafka.TCP(strings.Split(cfg.KafkaBootstrapServers, ",")...),
			Topic:                  TopicAlertTriggered,
			Balancer:               &kafka.LeastBytes{},
			AllowAutoTopicCreation: true,
		},
		emails: emails,
	}
}

// Notify resolves the rule owner's email and publishes a fired-alert event directly to
// Kafka for agent-system-notification-consumer to deliver.
func (c *Client) Notify(ctx context.Context, p TriggerPayload) error {
	email, err := c.emails.GetEmailByOwnerUUID(p.OwnerUuid)
	if err != nil {
		return fmt.Errorf("resolve owner email: %w", err)
	}
	if email == "" {
		return fmt.Errorf("no email found for owner %s", p.OwnerUuid)
	}

	value, err := json.Marshal(alertTriggeredEvent{
		To:               email,
		RuleType:         p.RuleType,
		SymbolOrProtocol: p.Symbol,
		Message:          p.Message,
	})
	if err != nil {
		return fmt.Errorf("marshal event: %w", err)
	}

	return c.writer.WriteMessages(ctx, kafka.Message{
		Key:   []byte(email),
		Value: value,
		Headers: []kafka.Header{
			{Key: "__TypeId__", Value: []byte(typeIDAlertTriggered)},
		},
	})
}

// Close releases the underlying Kafka writer's connections.
func (c *Client) Close() error {
	return c.writer.Close()
}
